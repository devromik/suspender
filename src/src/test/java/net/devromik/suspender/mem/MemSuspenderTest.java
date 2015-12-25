package net.devromik.suspender.mem;

import java.util.Collection;
import org.junit.*;
import static com.google.common.collect.Lists.newArrayList;
import static java.lang.System.currentTimeMillis;
import static java.time.Duration.*;
import static net.devromik.suspender.Suspender.*;
import net.devromik.suspender.RestoredObjectListener;
import static net.devromik.suspender.mem.MemSuspender.*;
import net.devromik.suspender.utils.Path;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Shulnyaev Roman
 */
public class MemSuspenderTest {

    @Before
    public void beforeTest() {
        suspender = new MemSuspender();
        suspender.start();
    }

    @After
    public void afterTest() {
        suspender.stop();
    }

    // ****************************** //

    @Test
    public void canSuspendObject() {
        // We check that there are no suspended objects.
        assertFalse(suspender.hasObjectsSuspendedBy(new Path("A")));
        assertFalse(suspender.hasObjectsSuspendedBy(new Path("A", "B")));
        assertFalse(suspender.hasObjectsSuspendedBy(new Path("A", "B", "C1")));
        assertFalse(suspender.hasObjectsSuspendedBy(new Path("A", "B", "C2")));
        assertFalse(suspender.hasObjectsSuspendedBy(new Path("A", "B", "C3")));
        assertFalse(suspender.hasObjectsSuspendedBy(new Path("A", "B", "C3", "D1")));
        assertFalse(suspender.hasObjectsSuspendedBy(new Path("A", "B", "C3", "D2")));

        // We suspend objects.
        // Each object will be suspended two times to check that repeated suspension works correctly:
        // the first object should be replaced by the second.
        String object_A_B = "object_A_B";
        suspender.suspend(new Path("A", "B"), new Object(), MIN_SUSPENSION_DURATION.minus(ofMillis(1L)));
        suspender.suspend(new Path("A", "B"), object_A_B, MIN_SUSPENSION_DURATION.minus(ofMillis(1L)));

        String object_A_B_C1 = "object_A_B_C1";
        suspender.suspend(new Path("A", "B", "C1"), new Object(), MIN_SUSPENSION_DURATION);
        suspender.suspend(new Path("A", "B", "C1"), object_A_B_C1, MIN_SUSPENSION_DURATION);

        String object_A_B_C2 = "object_A_B_C2";
        suspender.suspend(new Path("A", "B", "C2"), new Object(), MIN_SUSPENSION_DURATION.plus(ofMillis(1L)));
        suspender.suspend(new Path("A", "B", "C2"), object_A_B_C2, MIN_SUSPENSION_DURATION.plus(ofMillis(1L)));

        String object_A_B_C3 = "object_A_B_C3";
        suspender.suspend(new Path("A", "B", "C3"), new Object(), MIN_SUSPENSION_DURATION.plus(ofMillis(1L)));
        suspender.suspend(new Path("A", "B", "C3"), object_A_B_C3, MIN_SUSPENSION_DURATION.plus(ofMillis(1L)));

        String object_A_B_C3_D1 = "object_A_B_C3_D1";
        suspender.suspend(new Path("A", "B", "C3", "D1"), new Object(), MAX_SUSPENSION_DURATION);
        suspender.suspend(new Path("A", "B", "C3", "D1"), object_A_B_C3_D1, MAX_SUSPENSION_DURATION);

        String object_A_B_C3_D2 = "object_A_B_C3_D2";
        suspender.suspend(new Path("A", "B", "C3", "D2"), new Object(), MAX_SUSPENSION_DURATION.plus(ofMillis(1L)));
        suspender.suspend(new Path("A", "B", "C3", "D2"), object_A_B_C3_D2, MAX_SUSPENSION_DURATION.plus(ofMillis(1L)));

        // We check that there are expected suspended objects.
        assertTrue(suspender.hasObjectsSuspendedBy(new Path("A")));

        assertTrue(suspender.hasObjectsSuspendedBy(new Path("A", "B")));
        assertThat(
            suspender.divisionFor(new Path("A", "B")).suspendedObjectTreeRoot.
                getChild("A").suspendedObjects.get("B").suspendedObject,
            is(object_A_B));

        assertTrue(suspender.hasObjectsSuspendedBy(new Path("A", "B", "C1")));
        assertThat(
            suspender.divisionFor(new Path("A", "B")).suspendedObjectTreeRoot.
                getChild("A").getChild("B").suspendedObjects.get("C1").suspendedObject,
            is(object_A_B_C1));

        assertTrue(suspender.hasObjectsSuspendedBy(new Path("A", "B", "C2")));
        assertThat(
            suspender.divisionFor(new Path("A", "B")).suspendedObjectTreeRoot.
                getChild("A").getChild("B").suspendedObjects.get("C2").suspendedObject,
            is(object_A_B_C2));

        assertTrue(suspender.hasObjectsSuspendedBy(new Path("A", "B", "C3")));
        assertThat(
            suspender.divisionFor(new Path("A", "B")).suspendedObjectTreeRoot.
                getChild("A").getChild("B").suspendedObjects.get("C3").suspendedObject,
            is(object_A_B_C3));

        assertTrue(suspender.hasObjectsSuspendedBy(new Path("A", "B", "C3", "D1")));
        assertThat(
            suspender.divisionFor(new Path("A", "B")).suspendedObjectTreeRoot.
                getChild("A").getChild("B").getChild("C3").suspendedObjects.get("D1").suspendedObject,
            is(object_A_B_C3_D1));

        assertTrue(suspender.hasObjectsSuspendedBy(new Path("A", "B", "C3", "D2")));
        assertThat(
            suspender.divisionFor(new Path("A", "B")).suspendedObjectTreeRoot.
                getChild("A").getChild("B").getChild("C3").suspendedObjects.get("D2").suspendedObject,
            is(object_A_B_C3_D2));

        // We check that the restoration queue contains expected elements.
        assertThat(suspender.divisionFor(new Path("A", "B")).restorationQueue.size(), is(6));

        assertThatRestorationQueueContains("A", "B");
        assertThatRestorationQueueContains("A", "B", "C1");
        assertThatRestorationQueueContains("A", "B", "C2");
        assertThatRestorationQueueContains("A", "B", "C3");
        assertThatRestorationQueueContains("A", "B", "C3", "D1");
        assertThatRestorationQueueContains("A", "B", "C3", "D2");
    }

    @Test
    public void canRestoreSuspendedObjects() throws Exception {
        /* We suspend objects.
           As a result we will get the following tree of suspended objects:

           root
             A1
               B1: path_A1_B1
                 C1: path_A1_B1_C1
                 C2: path_A1_B1_C2
               B2
                 C1: path_A1_B2_C1
                 C2: path_A1_B2_C2
                 C3: path_A1_B2_C3
             A2
               B1: path_A2_B1
                 C1: path_A2_B1_C1
                 C2: path_A2_B1_C2
               B2
                 C1: path_A2_B2_C1
                 C2: path_A2_B2_C2
                 C3: path_A2_B2_C3
        */

        // A1_X.
        Path path_A1_B1 = new Path("A1", "B1");
        suspender.suspend(path_A1_B1, path_A1_B1, MAX_SUSPENSION_DURATION);

        // A1_B1_X.
        Path path_A1_B1_C1 = new Path("A1", "B1", "C1");
        suspender.suspend(path_A1_B1_C1, path_A1_B1_C1, MAX_SUSPENSION_DURATION);

        Path path_A1_B1_C2 = new Path("A1", "B1", "C2");
        suspender.suspend(path_A1_B1_C2, path_A1_B1_C2, MAX_SUSPENSION_DURATION);

        // A1_B2_X.
        Path path_A1_B2_C1 = new Path("A1", "B2", "C1");
        suspender.suspend(path_A1_B2_C1, path_A1_B2_C1, MAX_SUSPENSION_DURATION);

        Path path_A1_B2_C2 = new Path("A1", "B2", "C2");
        suspender.suspend(path_A1_B2_C2, path_A1_B2_C2, MAX_SUSPENSION_DURATION);

        Path path_A1_B2_C3 = new Path("A1", "B2", "C3");
        suspender.suspend(path_A1_B2_C3, path_A1_B2_C3, MAX_SUSPENSION_DURATION);

        // A2_X.
        Path path_A2_B1 = new Path("A2", "B1");
        suspender.suspend(path_A2_B1, path_A2_B1, MAX_SUSPENSION_DURATION);

        // A2_B1_X.
        Path path_A2_B1_C1 = new Path("A2", "B1", "C1");
        suspender.suspend(path_A2_B1_C1, path_A2_B1_C1, MAX_SUSPENSION_DURATION);

        Path path_A2_B1_C2 = new Path("A2", "B1", "C2");
        suspender.suspend(path_A2_B1_C2, path_A2_B1_C2, MAX_SUSPENSION_DURATION);

        // A1_B2_X.
        Path path_A2_B2_C1 = new Path("A2", "B2", "C1");
        suspender.suspend(path_A2_B2_C1, path_A2_B2_C1, MAX_SUSPENSION_DURATION);

        Path path_A2_B2_C2 = new Path("A2", "B2", "C2");
        suspender.suspend(path_A2_B2_C2, path_A2_B2_C2, MAX_SUSPENSION_DURATION);

        Path path_A2_B2_C3 = new Path("A2", "B2", "C3");
        suspender.suspend(path_A2_B2_C3, path_A2_B2_C3, MAX_SUSPENSION_DURATION);

        // We create two listeners.
        Collection<RestoredObjectListener> listeners = newArrayList(
            mock(RestoredObjectListener.class),
            mock(RestoredObjectListener.class));

        // We "restore" objects that were not suspended first.
        // Later we will check that the listeners are notified about the restoration of the expected objects.
        // We will also check that the listeners are not notified about
        // restoration of objects that are not suspended.
        suspender.restore(new Path("absent"), listeners);
        suspender.restore(new Path("absent", "absent"), listeners);
        suspender.restore(new Path("absent", "absent", "absent"), listeners);

        /* We restore path_A1_B1_C1.
           As a result we will get the following tree of suspended objects:

           root
             A1
               B1: path_A1_B1
                 C2: path_A1_B1_C2
               B2
                 C1: path_A1_B2_C1
                 C2: path_A1_B2_C2
                 C3: path_A1_B2_C3
             A2
               B1: path_A2_B1
                 C1: path_A2_B1_C1
                 C2: path_A2_B1_C2
               B2
                 C1: path_A2_B2_C1
                 C2: path_A2_B2_C2
                 C3: path_A2_B2_C3
        */
        suspender.restore(path_A1_B1_C1, listeners);

        checkNotification(
            listeners,
            path_A1_B1_C1);

        /* We restore path_A1_B1.
           As a result we will get the following tree of suspended objects:

           root
             A1
               B2
                 C1: path_A1_B2_C1
                 C2: path_A1_B2_C2
                 C3: path_A1_B2_C3
             A2
               B1: path_A2_B1
                 C1: path_A2_B1_C1
                 C2: path_A2_B1_C2
               B2
                 C1: path_A2_B2_C1
                 C2: path_A2_B2_C2
                 C3: path_A2_B2_C3
        */
        suspender.restore(path_A1_B1, listeners);

        checkNotification(
            listeners,
            path_A1_B1,
            path_A1_B1_C1,
            path_A1_B1_C2);

        /* We restore A2.
           As a result we will get the following tree of suspended objects:

           root
             A1
               B2
                 C1: path_A1_B2_C1
                 C2: path_A1_B2_C2
                 C3: path_A1_B2_C3
        */
        suspender.restore(new Path("A2"), listeners);

        checkNotification(
            listeners,

            path_A1_B1,
            path_A1_B1_C1,
            path_A1_B1_C2,

            path_A2_B1,
            path_A2_B1_C1,
            path_A2_B1_C2,
            path_A2_B2_C1,
            path_A2_B2_C2,
            path_A2_B2_C3);

        /* We restore A1.
           As a result we will get the following tree of suspended objects:

           root
        */
        suspender.restore(new Path("A1"), listeners);

        checkNotification(
            listeners,

            path_A1_B1,
            path_A1_B1_C1,
            path_A1_B1_C2,
            path_A1_B2_C1,
            path_A1_B2_C2,
            path_A1_B2_C3,

            path_A2_B1,
            path_A2_B1_C1,
            path_A2_B1_C2,
            path_A2_B2_C1,
            path_A2_B2_C2,
            path_A2_B2_C3);

        assertFalse(suspender.hasObjectsSuspendedBy(new Path("A1")));
        assertFalse(suspender.hasObjectsSuspendedBy(new Path("A2")));
    }

    @Test
    public void canRestoreObjectWithMinRestorationTime() throws Exception {
        /* We suspend objects.
           As a result we will get the following tree of suspended objects:

           root
             A1
               B1: path_A1_B1 for 1 hour // there is a suspended object (<), there is a subtree
                 C1: path_A1_B1_C1 for 3 hours
                 C2: path_A1_B1_C2 for 4 hours
               B2
                 C1: path_A1_B2_C1 на 2 час // there is a suspended object, there is no subtree
                 C2: path_A1_B2_C2 for 3 hours
                 C3: path_A1_B2_C3 for 4 hours
             A2
               B1: path_A2_B1 for 3 hours // there is a suspended object (>), there is a subtree
                 C1: path_A2_B1_C1 for 1 hour
                 C2: path_A2_B1_C2 for 2 hours
               B2 // there is no suspended object, there is a subtree
                 C1: path_A2_B2_C1 for 1 hour
                 C2: path_A2_B2_C2 for 2 hours
                 C3: path_A2_B2_C3 for 3 hours

           // *** - a description of a test case.
        */

        // A1_X.
        Path path_A1_B1 = new Path("A1", "B1");
        suspender.suspend(path_A1_B1, path_A1_B1, ofHours(1L));

        // A1_B1_X.
        Path path_A1_B1_C1 = new Path("A1", "B1", "C1");
        suspender.suspend(path_A1_B1_C1, path_A1_B1_C1, ofHours(3L));

        Path path_A1_B1_C2 = new Path("A1", "B1", "C2");
        suspender.suspend(path_A1_B1_C2, path_A1_B1_C2, ofHours(4L));

        // A1_B2_X.
        Path path_A1_B2_C1 = new Path("A1", "B2", "C1");
        suspender.suspend(path_A1_B2_C1, path_A1_B2_C1, ofHours(1L));

        Path path_A1_B2_C2 = new Path("A1", "B2", "C2");
        suspender.suspend(path_A1_B2_C2, path_A1_B2_C2, ofHours(3L));

        Path path_A1_B2_C3 = new Path("A1", "B2", "C3");
        suspender.suspend(path_A1_B2_C3, path_A1_B2_C3, ofHours(4L));

        // A2_X.
        Path path_A2_B1 = new Path("A2", "B1");
        suspender.suspend(path_A2_B1, path_A2_B1, ofHours(3L));

        // A2_B1_X.
        Path path_A2_B1_C1 = new Path("A2", "B1", "C1");
        suspender.suspend(path_A2_B1_C1, path_A2_B1_C1, ofHours(1L));

        Path path_A2_B1_C2 = new Path("A2", "B1", "C2");
        suspender.suspend(path_A2_B1_C2, path_A2_B1_C2, ofHours(2L));

        // A1_B2_X.
        Path path_A2_B2_C1 = new Path("A2", "B2", "C1");
        suspender.suspend(path_A2_B2_C1, path_A2_B2_C1, ofHours(1L));

        Path path_A2_B2_C2 = new Path("A2", "B2", "C2");
        suspender.suspend(path_A2_B2_C2, path_A2_B2_C2, ofHours(2L));

        Path path_A2_B2_C3 = new Path("A2", "B2", "C3");
        suspender.suspend(path_A2_B2_C3, path_A2_B2_C3, ofHours(3L));

        // We create two listeners.
        Collection<RestoredObjectListener> listeners = newArrayList(
            mock(RestoredObjectListener.class),
            mock(RestoredObjectListener.class));

        // We "restore" objects that were not suspended first.
        // Later we will check that the listeners are notified about the restoration of the expected objects.
        // We will also check that the listeners are not notified about
        // restoration of objects that are not suspended.
        assertNull(
            suspender.divisionFor(new Path("A1", "absent")).
                findMinRestorationTime(new Path("A1", "absent")));
        suspender.restoreObjectWithMinRestorationTime(new Path("A1", "absent"), listeners);

        assertNull(
            suspender.divisionFor(new Path("A2", "absent", "absent")).
                findMinRestorationTime(new Path("A2", "absent", "absent")));
        suspender.restoreObjectWithMinRestorationTime(new Path("A2", "absent", "absent"), listeners);

        assertNull(
            suspender.divisionFor(new Path("A1", "absent", "absent")).
                findMinRestorationTime(new Path("A1", "absent", "absent")));
        suspender.restoreObjectWithMinRestorationTime(new Path("absent", "absent", "absent"), listeners);

        /* We restore path_A1_B1: there is a suspended object (<), there is a subtree.
           As a result we will get the following tree of suspended objects:

           root
             A1
               B1
                 C1: path_A1_B1_C1 for 2 hours
                 C2: path_A1_B1_C2 for 3 hours
               B2
                 C1: path_A1_B2_C1 for 1 hour // there is a suspended object, there is no subtree
                 C2: path_A1_B2_C2 for 2 hours
                 C3: path_A1_B2_C3 for 3 hours
             A2
               B1: path_A2_B1 for 3 hours // there is a suspended object (>), there is a subtree
                 C1: path_A2_B1_C1 for 1 hour
                 C2: path_A2_B1_C2 for 2 hours
               B2 // there is no suspended object, there is a subtree
                 C1: path_A2_B2_C1 for 1 hour
                 C2: path_A2_B2_C2 for 2 hours
                 C3: path_A2_B2_C3 for 3 hours
        */
        assertTrue(
            suspender.divisionFor(path_A1_B1).suspendedObjectTreeRoot.
                getChild("A1").hasSuspendedObject("B1"));

        assertThat(
            suspender.divisionFor(path_A1_B1).findMinRestorationTime(path_A1_B1),
            is(suspender.divisionFor(path_A1_B1).suspendedObjectTreeRoot.
                   getChild("A1").getRestorationTime("B1")));

        suspender.restoreObjectWithMinRestorationTime(new Path("A1"), listeners);

        checkNotification(
            listeners,
            path_A1_B1);

        assertFalse(
            suspender.divisionFor(path_A1_B1).suspendedObjectTreeRoot.
                getChild("A1").hasSuspendedObject("B1"));

        /* We restore path_A1_B2_C1: there is a suspended object, there is no subtree.
           As a result we will get the following tree of suspended objects:

           root
             A1
               B1
                 C1: path_A1_B1_C1 for 2 hours
                 C2: path_A1_B1_C2 for 3 hours
               B2
                 C2: path_A1_B2_C2 for 2 hours
                 C3: path_A1_B2_C3 for 3 hours
             A2
               B1: path_A2_B1 for 3 hours // there is a suspended object (>), there is a subtree
                 C1: path_A2_B1_C1 for 1 hour
                 C2: path_A2_B1_C2 for 2 hours
               B2 // there is no suspended object, there is a subtree
                 C1: path_A2_B2_C1 for 1 hour
                 C2: path_A2_B2_C2 for 2 hours
                 C3: path_A2_B2_C3 for 3 hours
        */
        assertThat(
            suspender.divisionFor(path_A1_B2_C1).findMinRestorationTime(path_A1_B2_C1),
            is(suspender.divisionFor(path_A1_B2_C1).suspendedObjectTreeRoot.
                   getChild("A1").getChild("B2").getRestorationTime("C1")));

        suspender.restoreObjectWithMinRestorationTime(path_A1_B2_C1, listeners);

        checkNotification(
            listeners,
            path_A1_B1,
            path_A1_B2_C1);

        /* We restore path_A2_B1: there is a suspended object (>), there is a subtree.
           As a result we will get the following tree of suspended objects:

           root
             A1
               B1
                 C1: path_A1_B1_C1 for 2 hours
                 C2: path_A1_B1_C2 for 3 hours
               B2
                 C2: path_A1_B2_C2 for 2 hours
                 C3: path_A1_B2_C3 for 3 hours
             A2
               B1: path_A2_B1 for 3 hours
                 C2: path_A2_B1_C2 for 2 hours
               B2 // there is no suspended object, there is a subtree
                 C1: path_A2_B2_C1 for 1 hour
                 C2: path_A2_B2_C2 for 2 hours
                 C3: path_A2_B2_C3 for 3 hours
        */
        assertThat(
            suspender.divisionFor(path_A2_B1).findMinRestorationTime(path_A2_B1),
            is(suspender.divisionFor(path_A2_B1).suspendedObjectTreeRoot.
                   getChild("A2").getChild("B1").getRestorationTime("C1")));

        suspender.restoreObjectWithMinRestorationTime(path_A2_B1, listeners);

        checkNotification(
            listeners,
            path_A1_B1,
            path_A1_B2_C1,
            path_A2_B1_C1);

        /* We restore path_A2_B2: there is no suspended object, there is a subtree.
           As a result we will get the following tree of suspended objects:

           root
             A1
               B1
                 C1: path_A1_B1_C1 for 2 hours
                 C2: path_A1_B1_C2 for 3 hours
               B2
                 C2: path_A1_B2_C2 for 2 hours
                 C3: path_A1_B2_C3 for 3 hours
             A2
               B1: path_A2_B1 for 3 hours
                 C2: path_A2_B1_C2 for 2 hours
               B2
                 C2: path_A2_B2_C2 for 2 hours
                 C3: path_A2_B2_C3 for 3 hours
        */
        assertThat(
            suspender.divisionFor(new Path("A2", "B2")).findMinRestorationTime(new Path("A2", "B2")),
            is(suspender.divisionFor(new Path("A2", "B2")).suspendedObjectTreeRoot.
                   getChild("A2").getChild("B2").getRestorationTime("C1")));

        suspender.restoreObjectWithMinRestorationTime(new Path("A2", "B2"), listeners);

        checkNotification(
            listeners,
            path_A1_B1,
            path_A1_B2_C1,
            path_A2_B1_C1,
            path_A1_B2_C1);

        /* We restore all from A1.
           As a result we will get the following tree of suspended objects:

           root
             A2
               B1: path_A2_B1 for 3 hours
                 C2: path_A2_B1_C2 for 2 hours
               B2
                 C2: path_A2_B2_C2 for 2 hours
                 C3: path_A2_B2_C3 for 3 hours
        */
        suspender.restoreObjectWithMinRestorationTime(path_A1_B1, listeners);
        suspender.restoreObjectWithMinRestorationTime(path_A1_B1, listeners);

        suspender.restoreObjectWithMinRestorationTime(new Path("A1", "B2"), listeners);
        suspender.restoreObjectWithMinRestorationTime(new Path("A1", "B2"), listeners);

        checkNotification(
            listeners,
            path_A1_B1,
            path_A1_B2_C1,
            path_A2_B1_C1,
            path_A1_B2_C1,
            path_A1_B1_C1,
            path_A1_B1_C2,
            path_A1_B2_C1,
            path_A1_B2_C2);

        assertFalse(suspender.hasObjectsSuspendedBy(new Path("A1")));

        /* We restore all from A2.
           As a result we will get the following tree of suspended objects:

           root
        */
        suspender.restoreObjectWithMinRestorationTime(path_A2_B1, listeners);
        suspender.restoreObjectWithMinRestorationTime(path_A2_B1, listeners);

        suspender.restoreObjectWithMinRestorationTime(new Path("A2", "B2"), listeners);
        suspender.restoreObjectWithMinRestorationTime(new Path("A2", "B2"), listeners);

        checkNotification(
            listeners,
            path_A1_B1,
            path_A1_B2_C1,
            path_A2_B1_C1,
            path_A1_B2_C1,
            path_A1_B1_C1,
            path_A1_B1_C2,
            path_A1_B2_C1,
            path_A1_B2_C2,
            path_A2_B1_C2,
            path_A2_B1,
            path_A2_B2_C2,
            path_A2_B2_C3);

        assertFalse(suspender.hasObjectsSuspendedBy(new Path("A2")));
    }

    @Test
    public void canRestoreExpiredSuspendedObjects() throws Exception {
        /* We suspend objects.
           As a result we will get the following tree of suspended objects:

           root
             A1
               B1: path_A1_B1 for 1 hour
                 C1: path_A1_B1_C1 for 2 hours
                 C2: path_A1_B1_C2 for 3 hours
               B2
                 C1: path_A1_B2_C1 for 1 hour
                 C2: path_A1_B2_C2 for 2 hours
                 C3: path_A1_B2_C3 for 3 hours
             A2
               B1: path_A2_B1 for 3 hours
                 C1: path_A2_B1_C1 for 1 hour
                 C2: path_A2_B1_C2 for 2 hours
               B2
                 C1: path_A2_B2_C1 for 1 hour
                 C2: path_A2_B2_C2 for 2 hours
                 C3: path_A2_B2_C3 for 3 hours
        */

        // A1_X.
        Path path_A1_B1 = new Path("A1", "B1");
        suspender.suspend(path_A1_B1, path_A1_B1, ofHours(1L));

        // A1_B1_X.
        Path path_A1_B1_C1 = new Path("A1", "B1", "C1");
        suspender.suspend(path_A1_B1_C1, path_A1_B1_C1, ofHours(2L));

        Path path_A1_B1_C2 = new Path("A1", "B1", "C2");
        suspender.suspend(path_A1_B1_C2, path_A1_B1_C2, ofHours(3L));

        // A1_B2_X.
        Path path_A1_B2_C1 = new Path("A1", "B2", "C1");
        suspender.suspend(path_A1_B2_C1, path_A1_B2_C1, ofHours(1L));

        Path path_A1_B2_C2 = new Path("A1", "B2", "C2");
        suspender.suspend(path_A1_B2_C2, path_A1_B2_C2, ofHours(2L));

        Path path_A1_B2_C3 = new Path("A1", "B2", "C3");
        suspender.suspend(path_A1_B2_C3, path_A1_B2_C3, ofHours(3L));

        // A2_X.
        Path path_A2_B1 = new Path("A2", "B1");
        suspender.suspend(path_A2_B1, path_A2_B1, ofHours(3L));

        // A2_B1_X.
        Path path_A2_B1_C1 = new Path("A2", "B1", "C1");
        suspender.suspend(path_A2_B1_C1, path_A2_B1_C1, ofHours(1L));

        Path path_A2_B1_C2 = new Path("A2", "B1", "C2");
        suspender.suspend(path_A2_B1_C2, path_A2_B1_C2, ofHours(2L));

        // A1_B2_X.
        Path path_A2_B2_C1 = new Path("A2", "B2", "C1");
        suspender.suspend(path_A2_B2_C1, path_A2_B2_C1, ofHours(1L));

        Path path_A2_B2_C2 = new Path("A2", "B2", "C2");
        suspender.suspend(path_A2_B2_C2, path_A2_B2_C2, ofHours(2L));

        Path path_A2_B2_C3 = new Path("A2", "B2", "C3");
        suspender.suspend(path_A2_B2_C3, path_A2_B2_C3, ofHours(3L));

        // We create two listeners.
        Collection<RestoredObjectListener> listeners = newArrayList(
            mock(RestoredObjectListener.class),
            mock(RestoredObjectListener.class));
        listeners.forEach(suspender::addRestoredObjectListener);

        /* We restore objects that have restoration time which is not greater than currentTimeMillis() + [1 hour].
           As a result we will get the following tree of suspended objects:

           root
             A1
               B1
                 C1: path_A1_B1_C1 for 2 hours
                 C2: path_A1_B1_C2 for 3 hours
               B2
                 C2: path_A1_B2_C2 for 2 hours
                 C3: path_A1_B2_C3 for 3 hours
             A2
               B1: path_A2_B1 for 3 hours
                 C2: path_A2_B1_C2 for 2 hours
               B2
                 C2: path_A2_B2_C2 for 2 hours
                 C3: path_A2_B2_C3 for 3 hours
        */
        suspender.restoreExpired(currentTimeMillis() + ofHours(1L).toMillis() + MIN_DURATION_HALF /* see MemSuspender.calcRestorationTime() */);

        checkNotification(
            listeners,
            path_A1_B1,
            path_A1_B2_C1,
            path_A2_B1_C1,
            path_A2_B2_C1);

        /* We restore objects that have restoration time which is not greater than currentTimeMillis() + [2 hours].
           As a result we will get the following tree of suspended objects:

           root
             A1
               B1
                 C2: path_A1_B1_C2 for 3 hours
               B2
                 C3: path_A1_B2_C3 for 3 hours
             A2
               B1: path_A2_B1 for 3 hours
               B2
                 C3: path_A2_B2_C3 for 3 hours
        */
        suspender.restoreExpired(currentTimeMillis() + ofHours(2L).toMillis() + MIN_DURATION_HALF);

        checkNotification(
            listeners,
            path_A1_B1,
            path_A1_B2_C1,
            path_A2_B1_C1,
            path_A2_B2_C1,
            path_A1_B1_C1,
            path_A1_B2_C2,
            path_A2_B1_C2,
            path_A2_B2_C2);

        /* We restore objects that have restoration time which is not greater than currentTimeMillis() + [3 hours].
           As a result we will get the following tree of suspended objects:

           root
        */
        suspender.restoreExpired(currentTimeMillis() + ofHours(3L).toMillis() + MIN_DURATION_HALF);

        checkNotification(
            listeners,
            path_A1_B1,
            path_A1_B2_C1,
            path_A2_B1_C1,
            path_A2_B2_C1,
            path_A1_B1_C1,
            path_A1_B2_C2,
            path_A2_B1_C2,
            path_A2_B2_C2,
            path_A1_B1_C2,
            path_A1_B2_C3,
            path_A2_B1,
            path_A2_B2_C3);
    }

    @Test
    public void testMinDurationHalf() {
        assertThat(MIN_DURATION_HALF, is(MIN_SUSPENSION_DURATION.toMillis() / 2L));
    }

    @Test
    public void testCalcRestorationTime() throws Exception {
        long currentTime = MIN_DURATION_HALF;
        Long restorationTime = calcRestorationTime(currentTime, ZERO);
        assertThat(restorationTime, is(currentTime));

        currentTime = MIN_DURATION_HALF;
        restorationTime = calcRestorationTime(currentTime, ofMillis(1L));
        assertThat(restorationTime, is(currentTime + MIN_DURATION_HALF));

        currentTime = MIN_DURATION_HALF - 2L;
        restorationTime = calcRestorationTime(currentTime, ofMillis(1L));
        assertThat(restorationTime, is(currentTime + 2L));
    }

    // ****************************** //

    private void assertThatRestorationQueueContains(String... suspendedObjectPathSegments) {
        Path suspendedObjectPath = new Path(suspendedObjectPathSegments);
        SuspendedObjectTreeNode suspendedObjectParentNode = suspender.divisionFor(suspendedObjectPath).findParentNodeFor(suspendedObjectPath);
        RestorationQueueElement restorationQueueElement = new RestorationQueueElement(suspendedObjectParentNode, suspendedObjectPath.getLastSegment());

        assertTrue(
            suspender.divisionFor(suspendedObjectPath).restorationQueue.containsValue(
                restorationQueueElement));
    }

    /**
     * Checks that the given listeners have received notifications about restoration of the given objects.
     * Checks that the listeners haven't gotten other notifications.
     *
     * The paths by which the objects were suspended are passed as {@code paths}.
     * It is assumed that the path itself was suspended: {@code suspend(path, path, duration)}.
     */
    static void checkNotification(Collection<RestoredObjectListener> listeners, Path... paths) throws Exception {
        for (RestoredObjectListener listener : listeners) {
            verify(listener, times(paths.length)).onObjectRestored(any(), any());

            for (Path path : paths) {
                verify(listener).onObjectRestored(path, path);
            }
        }
    }

    // ****************************** //

    private MemSuspender suspender;
}
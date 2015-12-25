package net.devromik.suspender.mem;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.Test;
import static com.google.common.collect.Lists.newArrayList;
import static java.lang.System.currentTimeMillis;
import static java.time.Duration.*;
import static net.devromik.suspender.Suspender.*;
import net.devromik.suspender.RestoredObjectListener;
import static net.devromik.suspender.mem.MemSuspender.*;
import net.devromik.suspender.utils.Path;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Shulnyaev Roman
 */
public class SuspendedObjectDivisionTest {

    @Test
    public void canSuspendObject() {
        SuspendedObjectDivision div = makeDivision();

        // We check that there are no suspended objects.
        assertTrue(div.pathFirstSegmentToDivCount.isEmpty());

        assertFalse(div.hasObjectsSuspendedBy(new Path("A")));
        assertFalse(div.hasObjectsSuspendedBy(new Path("A", "B")));
        assertFalse(div.hasObjectsSuspendedBy(new Path("A", "B", "C1")));
        assertFalse(div.hasObjectsSuspendedBy(new Path("A", "B", "C2")));
        assertFalse(div.hasObjectsSuspendedBy(new Path("A", "B", "C3")));
        assertFalse(div.hasObjectsSuspendedBy(new Path("A", "B", "C3", "D1")));
        assertFalse(div.hasObjectsSuspendedBy(new Path("A", "B", "C3", "D2")));

        // We suspend objects.
        // Each object will be suspended two times to check that repeated suspension works correctly:
        // the first object should be replaced by the second.
        String object_A_B = "object_A_B";
        div.suspend(new Path("A", "B"), new Object(), MIN_SUSPENSION_DURATION.minus(ofMillis(1L)));
        div.suspend(new Path("A", "B"), object_A_B, MIN_SUSPENSION_DURATION.minus(ofMillis(1L)));

        String object_A_B_C1 = "object_A_B_C1";
        div.suspend(new Path("A", "B", "C1"), new Object(), MIN_SUSPENSION_DURATION);
        div.suspend(new Path("A", "B", "C1"), object_A_B_C1, MIN_SUSPENSION_DURATION);

        String object_A_B_C2 = "object_A_B_C2";
        div.suspend(new Path("A", "B", "C2"), new Object(), MIN_SUSPENSION_DURATION.plus(ofMillis(1L)));
        div.suspend(new Path("A", "B", "C2"), object_A_B_C2, MIN_SUSPENSION_DURATION.plus(ofMillis(1L)));

        String object_A_B_C3 = "object_A_B_C3";
        div.suspend(new Path("A", "B", "C3"), new Object(), MIN_SUSPENSION_DURATION.plus(ofMillis(1L)));
        div.suspend(new Path("A", "B", "C3"), object_A_B_C3, MIN_SUSPENSION_DURATION.plus(ofMillis(1L)));

        String object_A_B_C3_D1 = "object_A_B_C3_D1";
        div.suspend(new Path("A", "B", "C3", "D1"), new Object(), MAX_SUSPENSION_DURATION);
        div.suspend(new Path("A", "B", "C3", "D1"), object_A_B_C3_D1, MAX_SUSPENSION_DURATION);

        String object_A_B_C3_D2 = "object_A_B_C3_D2";
        div.suspend(new Path("A", "B", "C3", "D2"), new Object(), MAX_SUSPENSION_DURATION.plus(ofMillis(1L)));
        div.suspend(new Path("A", "B", "C3", "D2"), object_A_B_C3_D2, MAX_SUSPENSION_DURATION.plus(ofMillis(1L)));

        // We check that there are expected suspended objects.
        assertTrue(div.hasObjectsSuspendedBy(new Path("A")));

        assertTrue(div.hasObjectsSuspendedBy(new Path("A", "B")));
        assertThat(
            div.suspendedObjectTreeRoot.getChild("A").suspendedObjects.get("B").suspendedObject,
            is(object_A_B));
        assertThat(div.pathFirstSegmentToDivCount.get("A").get(), is(1));

        assertTrue(div.hasObjectsSuspendedBy(new Path("A", "B", "C1")));
        assertThat(
            div.suspendedObjectTreeRoot.getChild("A").getChild("B").suspendedObjects.get("C1").suspendedObject,
            is(object_A_B_C1));
        assertThat(div.pathFirstSegmentToDivCount.get("A").get(), is(1));

        assertTrue(div.hasObjectsSuspendedBy(new Path("A", "B", "C2")));
        assertThat(
            div.suspendedObjectTreeRoot.getChild("A").getChild("B").suspendedObjects.get("C2").suspendedObject,
            is(object_A_B_C2));
        assertThat(div.pathFirstSegmentToDivCount.get("A").get(), is(1));

        assertTrue(div.hasObjectsSuspendedBy(new Path("A", "B", "C3")));
        assertThat(
            div.suspendedObjectTreeRoot.getChild("A").getChild("B").suspendedObjects.get("C3").suspendedObject,
            is(object_A_B_C3));
        assertThat(div.pathFirstSegmentToDivCount.get("A").get(), is(1));

        assertTrue(div.hasObjectsSuspendedBy(new Path("A", "B", "C3", "D1")));
        assertThat(
            div.suspendedObjectTreeRoot.getChild("A").getChild("B").getChild("C3").suspendedObjects.get("D1").suspendedObject,
            is(object_A_B_C3_D1));
        assertThat(div.pathFirstSegmentToDivCount.get("A").get(), is(1));

        assertTrue(div.hasObjectsSuspendedBy(new Path("A", "B", "C3", "D2")));
        assertThat(
            div.suspendedObjectTreeRoot.getChild("A").getChild("B").getChild("C3").suspendedObjects.get("D2").suspendedObject,
            is(object_A_B_C3_D2));
        assertThat(div.pathFirstSegmentToDivCount.get("A").get(), is(1));

        // We check that the restoration queue contains expected elements.
        assertThat(div.restorationQueue.size(), is(6));

        assertThatRestorationQueueContains(div, "A", "B");
        assertThatRestorationQueueContains(div, "A", "B", "C1");
        assertThatRestorationQueueContains(div, "A", "B", "C2");
        assertThatRestorationQueueContains(div, "A", "B", "C3");
        assertThatRestorationQueueContains(div, "A", "B", "C3", "D1");
        assertThatRestorationQueueContains(div, "A", "B", "C3", "D2");

        div.restore(new Path("A"), newArrayList());
        assertThat(div.pathFirstSegmentToDivCount.get("A").get(), is(0));
    }

    private SuspendedObjectDivision makeDivision() {
        return new SuspendedObjectDivision(new ConcurrentHashMap<>());
    }

    @Test
    public void canRestoreSuspendedObjects() throws Exception {
        SuspendedObjectDivision div = makeDivision();

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
        div.suspend(path_A1_B1, path_A1_B1, MAX_SUSPENSION_DURATION);

        // A1_B1_X.
        Path path_A1_B1_C1 = new Path("A1", "B1", "C1");
        div.suspend(path_A1_B1_C1, path_A1_B1_C1, MAX_SUSPENSION_DURATION);

        Path path_A1_B1_C2 = new Path("A1", "B1", "C2");
        div.suspend(path_A1_B1_C2, path_A1_B1_C2, MAX_SUSPENSION_DURATION);

        // A1_B2_X.
        Path path_A1_B2_C1 = new Path("A1", "B2", "C1");
        div.suspend(path_A1_B2_C1, path_A1_B2_C1, MAX_SUSPENSION_DURATION);

        Path path_A1_B2_C2 = new Path("A1", "B2", "C2");
        div.suspend(path_A1_B2_C2, path_A1_B2_C2, MAX_SUSPENSION_DURATION);

        Path path_A1_B2_C3 = new Path("A1", "B2", "C3");
        div.suspend(path_A1_B2_C3, path_A1_B2_C3, MAX_SUSPENSION_DURATION);

        // A2_X.
        Path path_A2_B1 = new Path("A2", "B1");
        div.suspend(path_A2_B1, path_A2_B1, MAX_SUSPENSION_DURATION);

        // A2_B1_X.
        Path path_A2_B1_C1 = new Path("A2", "B1", "C1");
        div.suspend(path_A2_B1_C1, path_A2_B1_C1, MAX_SUSPENSION_DURATION);

        Path path_A2_B1_C2 = new Path("A2", "B1", "C2");
        div.suspend(path_A2_B1_C2, path_A2_B1_C2, MAX_SUSPENSION_DURATION);

        // A1_B2_X.
        Path path_A2_B2_C1 = new Path("A2", "B2", "C1");
        div.suspend(path_A2_B2_C1, path_A2_B2_C1, MAX_SUSPENSION_DURATION);

        Path path_A2_B2_C2 = new Path("A2", "B2", "C2");
        div.suspend(path_A2_B2_C2, path_A2_B2_C2, MAX_SUSPENSION_DURATION);

        Path path_A2_B2_C3 = new Path("A2", "B2", "C3");
        div.suspend(path_A2_B2_C3, path_A2_B2_C3, MAX_SUSPENSION_DURATION);

        // We create two listeners.
        Collection<RestoredObjectListener> listeners = newArrayList(
            mock(RestoredObjectListener.class),
            mock(RestoredObjectListener.class));

        // We "restore" objects that were not suspended first.
        // Later we will check that the listeners are notified about the restoration of the expected objects.
        // We will also check that the listeners are not notified about
        // restoration of objects that are not suspended.
        div.restore(new Path("absent"), listeners);
        div.restore(new Path("absent", "absent"), listeners);
        div.restore(new Path("absent", "absent", "absent"), listeners);

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
        div.restore(path_A1_B1_C1, listeners);

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
        div.restore(path_A1_B1, listeners);

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
        div.restore(new Path("A2"), listeners);

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
        div.restore(new Path("A1"), listeners);

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

        assertFalse(div.hasObjectsSuspendedBy(new Path("A1")));
        assertFalse(div.hasObjectsSuspendedBy(new Path("A2")));
    }

    @Test
    public void canRestoreObjectWithMinRestorationTime() throws Exception {
        SuspendedObjectDivision div = makeDivision();

        /* We suspend objects.
           As a result we will get the following tree of suspended objects:

           root
             A1
               B1: path_A1_B1 for 1 hour // there is a suspended object (<), there is a subtree
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

           // *** - a description of a test case.
        */

        // A1_X.
        Path path_A1_B1 = new Path("A1", "B1");
        div.suspend(path_A1_B1, path_A1_B1, ofHours(1L));

        // A1_B1_X.
        Path path_A1_B1_C1 = new Path("A1", "B1", "C1");
        div.suspend(path_A1_B1_C1, path_A1_B1_C1, ofHours(2L));

        Path path_A1_B1_C2 = new Path("A1", "B1", "C2");
        div.suspend(path_A1_B1_C2, path_A1_B1_C2, ofHours(3L));

        // A1_B2_X.
        Path path_A1_B2_C1 = new Path("A1", "B2", "C1");
        div.suspend(path_A1_B2_C1, path_A1_B2_C1, ofHours(1L));

        Path path_A1_B2_C2 = new Path("A1", "B2", "C2");
        div.suspend(path_A1_B2_C2, path_A1_B2_C2, ofHours(2L));

        Path path_A1_B2_C3 = new Path("A1", "B2", "C3");
        div.suspend(path_A1_B2_C3, path_A1_B2_C3, ofHours(3L));

        // A2_X.
        Path path_A2_B1 = new Path("A2", "B1");
        div.suspend(path_A2_B1, path_A2_B1, ofHours(3L));

        // A2_B1_X.
        Path path_A2_B1_C1 = new Path("A2", "B1", "C1");
        div.suspend(path_A2_B1_C1, path_A2_B1_C1, ofHours(1L));

        Path path_A2_B1_C2 = new Path("A2", "B1", "C2");
        div.suspend(path_A2_B1_C2, path_A2_B1_C2, ofHours(2L));

        // A1_B2_X.
        Path path_A2_B2_C1 = new Path("A2", "B2", "C1");
        div.suspend(path_A2_B2_C1, path_A2_B2_C1, ofHours(1L));

        Path path_A2_B2_C2 = new Path("A2", "B2", "C2");
        div.suspend(path_A2_B2_C2, path_A2_B2_C2, ofHours(2L));

        Path path_A2_B2_C3 = new Path("A2", "B2", "C3");
        div.suspend(path_A2_B2_C3, path_A2_B2_C3, ofHours(3L));

        // We create two listeners.
        Collection<RestoredObjectListener> listeners = newArrayList(
            mock(RestoredObjectListener.class),
            mock(RestoredObjectListener.class));

        // We "restore" objects that were not suspended first.
        // Later we will check that the listeners are notified about the restoration of the expected objects.
        // We will also check that the listeners are not notified about
        // restoration of objects that are not suspended.
        assertNull(div.findMinRestorationTime(new Path("A1", "absent")));
        div.restoreObjectWithMinRestorationTime(new Path("A1", "absent"), listeners);

        assertNull(div.findMinRestorationTime(new Path("A2", "absent", "absent")));
        div.restoreObjectWithMinRestorationTime(new Path("A2", "absent", "absent"), listeners);

        assertNull(div.findMinRestorationTime(new Path("absent", "absent", "absent")));
        div.restoreObjectWithMinRestorationTime(new Path("absent", "absent", "absent"), listeners);

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
        assertTrue(div.suspendedObjectTreeRoot.getChild("A1").hasSuspendedObject("B1"));

        assertThat(
            div.findMinRestorationTime(path_A1_B1),
            is(div.suspendedObjectTreeRoot.getChild("A1").getRestorationTime("B1")));

        div.restoreObjectWithMinRestorationTime(path_A1_B1, listeners);

        checkNotification(
            listeners,
            path_A1_B1);

        assertFalse(div.suspendedObjectTreeRoot.getChild("A1").hasSuspendedObject("B1"));

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
            div.findMinRestorationTime(path_A1_B2_C1),
            is(div.suspendedObjectTreeRoot.getChild("A1").getChild("B2").getRestorationTime("C1")));

        div.restoreObjectWithMinRestorationTime(path_A1_B2_C1, listeners);

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
            div.findMinRestorationTime(path_A2_B1),
            is(div.suspendedObjectTreeRoot.getChild("A2").getChild("B1").getRestorationTime("C1")));

        div.restoreObjectWithMinRestorationTime(path_A2_B1, listeners);

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
            div.findMinRestorationTime(new Path("A2", "B2")),
            is(div.suspendedObjectTreeRoot.getChild("A2").getChild("B2").getRestorationTime("C1")));

        div.restoreObjectWithMinRestorationTime(new Path("A2", "B2"), listeners);

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
        div.restoreObjectWithMinRestorationTime(path_A1_B1, listeners);
        div.restoreObjectWithMinRestorationTime(path_A1_B1, listeners);

        div.restoreObjectWithMinRestorationTime(new Path("A1", "B2"), listeners);
        div.restoreObjectWithMinRestorationTime(new Path("A1", "B2"), listeners);

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

        assertFalse(div.hasObjectsSuspendedBy(new Path("A1")));

        /* We restore all from A2.
           As a result we will get the following tree of suspended objects:

           root
        */
        div.restoreObjectWithMinRestorationTime(path_A2_B1, listeners);
        div.restoreObjectWithMinRestorationTime(path_A2_B1, listeners);

        div.restoreObjectWithMinRestorationTime(new Path("A2", "B2"), listeners);
        div.restoreObjectWithMinRestorationTime(new Path("A2", "B2"), listeners);

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

        assertFalse(div.hasObjectsSuspendedBy(new Path("A2")));
    }

    @Test
    public void canRestoreExpiredSuspendedObjects() throws Exception {
        SuspendedObjectDivision div = makeDivision();

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
        div.suspend(path_A1_B1, path_A1_B1, ofHours(1L));

        // A1_B1_X.
        Path path_A1_B1_C1 = new Path("A1", "B1", "C1");
        div.suspend(path_A1_B1_C1, path_A1_B1_C1, ofHours(2L));

        Path path_A1_B1_C2 = new Path("A1", "B1", "C2");
        div.suspend(path_A1_B1_C2, path_A1_B1_C2, ofHours(3L));

        // A1_B2_X.
        Path path_A1_B2_C1 = new Path("A1", "B2", "C1");
        div.suspend(path_A1_B2_C1, path_A1_B2_C1, ofHours(1L));

        Path path_A1_B2_C2 = new Path("A1", "B2", "C2");
        div.suspend(path_A1_B2_C2, path_A1_B2_C2, ofHours(2L));

        Path path_A1_B2_C3 = new Path("A1", "B2", "C3");
        div.suspend(path_A1_B2_C3, path_A1_B2_C3, ofHours(3L));

        // A2_X.
        Path path_A2_B1 = new Path("A2", "B1");
        div.suspend(path_A2_B1, path_A2_B1, ofHours(3L));

        // A2_B1_X.
        Path path_A2_B1_C1 = new Path("A2", "B1", "C1");
        div.suspend(path_A2_B1_C1, path_A2_B1_C1, ofHours(1L));

        Path path_A2_B1_C2 = new Path("A2", "B1", "C2");
        div.suspend(path_A2_B1_C2, path_A2_B1_C2, ofHours(2L));

        // A1_B2_X.
        Path path_A2_B2_C1 = new Path("A2", "B2", "C1");
        div.suspend(path_A2_B2_C1, path_A2_B2_C1, ofHours(1L));

        Path path_A2_B2_C2 = new Path("A2", "B2", "C2");
        div.suspend(path_A2_B2_C2, path_A2_B2_C2, ofHours(2L));

        Path path_A2_B2_C3 = new Path("A2", "B2", "C3");
        div.suspend(path_A2_B2_C3, path_A2_B2_C3, ofHours(3L));

        // We create two listeners.
        Collection<RestoredObjectListener> listeners = newArrayList(
            mock(RestoredObjectListener.class),
            mock(RestoredObjectListener.class));

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
        div.restoreExpired(
            listeners,
            currentTimeMillis() + ofHours(1L).toMillis() + MIN_DURATION_HALF /* see MemSuspender.calcRestorationTime() */);

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
        div.restoreExpired(
            listeners,
            currentTimeMillis() + ofHours(2L).toMillis() + MIN_DURATION_HALF);

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
        div.restoreExpired(
            listeners,
            currentTimeMillis() + ofHours(3L).toMillis() + MIN_DURATION_HALF);

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

    // ****************************** //

    private void assertThatRestorationQueueContains(SuspendedObjectDivision div, String... suspendedObjectPathSegments) {
        Path suspendedObjectPath = new Path(suspendedObjectPathSegments);
        SuspendedObjectTreeNode suspendedObjectParentNode = div.findParentNodeFor(suspendedObjectPath);
        RestorationQueueElement restorationQueueElement = new RestorationQueueElement(suspendedObjectParentNode, suspendedObjectPath.getLastSegment());

        assertTrue(div.restorationQueue.containsValue(restorationQueueElement));
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
}
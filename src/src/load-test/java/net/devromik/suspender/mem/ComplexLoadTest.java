package net.devromik.suspender.mem;

import java.util.*;
import java.util.concurrent.*;
import org.junit.*;
import static java.lang.Math.abs;
import static java.lang.System.currentTimeMillis;
import static java.time.Duration.*;
import static java.util.Collections.newSetFromMap;
import static java.util.concurrent.ForkJoinTask.invokeAll;
import net.devromik.suspender.utils.Path;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * @author Shulnyaev Roman
 */
public class ComplexLoadTest {

    @Before
    public void beforeTest() throws Exception {
        suspender = new MemSuspender();
        suspender.start();

        suspender.addRestoredObjectListener(
            (suspensionPath, restoredObject) -> {
                restoredObjects.add(suspensionPath);

                if (isLeaf(suspensionPath)) {
                    long deviationOfLeafRestorationTimeFromIdeal = abs(currentTimeMillis() - (leafsSuspensionStartTime + LEAF_SUSPENSION_DURATION));

                    synchronized (this) {
                        if (deviationOfLeafRestorationTimeFromIdeal > maxDeviationOfLeafRestorationTimeFromIdeal) {
                            maxDeviationOfLeafRestorationTimeFromIdeal = deviationOfLeafRestorationTimeFromIdeal;
                        }
                    }
                }
            });
    }

    private boolean isLeaf(Path suspensionPath) {
        return suspensionPath.getSegmentCount() == 5;
    }

    @After
    public void afterTest() {
        suspender.stop();
    }

    // ****************************** //

    @Test
    public void canWorkUnderConcurrentLoad() throws Exception {
        /* We suspend objects (actually, paths: suspend(path, path, duration)) concurrently.
           As a result we will get the following tree of suspended objects (format: "node: set of suspended objects"):

           root
             A1:
               B1: Path("A1", "B1") for 2 hours
                 C1: Path("A1", "B1", "C1") for 1 hour
                   D1:
                     E1: Path("A1", "B1", "C1", "D1", "E1") for 10 sec
                     ...
                     E256: Path("A1", "B1", "C1", "D1", "E256") for 10 sec
                   D2:
                     E1: Path("A1", "B1", "C1", "D2", "E1") for 10 sec
                     ...
                     E256: Path("A1", "B1", "C1", "D2", "E256") for 10 sec
                 C2: ...
               B2: ...
             ...
             A<suspendedObjectDivCount>: ...
        */
        Collection<RecursiveAction> suspensionTasks = new ArrayList<>();

        for (int i = 1; i <= 10; ++i) {
            suspensionTasks.add(new RecursiveAction() {

                @Override
                protected void compute() {
                    for (int a = 1; a <= suspender.suspendedObjectDivCount; ++a) {
                        for (int b = 1; b <= 2; ++b) {
                            Path path_AI_BJ = new Path("A" + a, "B" + b);

                            if (!suspender.hasObjectsSuspendedBy(path_AI_BJ)) {
                                suspender.suspend(
                                    path_AI_BJ,
                                    path_AI_BJ,
                                    ofHours(2L));
                            }

                            for (int c = 1; c <= 2; ++c) {
                                Path path_AI_BJ_CK = new Path("A" + a, "B" + b, "C" + c);

                                if (!suspender.hasObjectsSuspendedBy(path_AI_BJ_CK)) {
                                    suspender.suspend(
                                        path_AI_BJ_CK,
                                        path_AI_BJ_CK,
                                        ofHours(1L));
                                }

                                for (int d = 1; d <= 2; ++d) {
                                    for (int e = 1; e <= 256; ++e) {
                                        Path path_AI_BJ_CK_DL_EM = new Path(
                                            "A" + a,
                                            "B" + b,
                                            "C" + c,
                                            "D" + d,
                                            "E" + e);

                                        if (!suspender.hasObjectsSuspendedBy(path_AI_BJ_CK_DL_EM)) {
                                            suspender.suspend(
                                                path_AI_BJ_CK_DL_EM,
                                                path_AI_BJ_CK_DL_EM,
                                                ofSeconds(10L));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            });
        }

        invokeAll(suspensionTasks);
        leafsSuspensionStartTime = currentTimeMillis();
        Set<Path> expectedRestoredObjects = new HashSet<>();

        // We check that the tree was built correctly.
        for (int a = 1; a <= suspender.suspendedObjectDivCount; ++a) {
            assertTrue(suspender.hasObjectsSuspendedBy(new Path("A" + a)));

            for (int b = 1; b <= 2; ++b) {
                Path path_AI_BJ = new Path("A" + a, "B" + b);
                assertTrue(suspender.hasObjectsSuspendedBy(path_AI_BJ));

                for (int c = 1; c <= 2; ++c) {
                    Path path_AI_BJ_CK = new Path("A" + a, "B" + b, "C" + c);
                    assertTrue(suspender.hasObjectsSuspendedBy(path_AI_BJ_CK));

                    for (int d = 1; d <= 2; ++d) {
                        for (int e = 1; e <= 256; ++e) {
                            Path path_AI_BJ_CK_DL_EM = new Path(
                                "A" + a,
                                "B" + b,
                                "C" + c,
                                "D" + d,
                                "E" + e);

                            expectedRestoredObjects.add(path_AI_BJ_CK_DL_EM);
                            assertTrue(suspender.hasObjectsSuspendedBy(path_AI_BJ_CK_DL_EM));
                        }
                    }
                }
            }
        }

        // We wait for the leafs to be restored.
        Thread.sleep(15000L);

        assertTrue(maxDeviationOfLeafRestorationTimeFromIdeal < 3000L);
        checkNotification(expectedRestoredObjects);

        /* Now we have the following tree of suspended objects:

           root
             A1:
               B1: Path("A1", "B1") for 2 hours
                 C1: Path("A1", "B1", "C1") for 1 hour
                 C2: Path("A1", "B1", "C2") for 1 hour
               B2: ...
             ...
             A<suspendedObjectDivCount>: ...
        */
        for (int a = 1; a <= suspender.suspendedObjectDivCount; ++a) {
            assertTrue(suspender.hasObjectsSuspendedBy(new Path("A" + a)));

            for (int b = 1; b <= 2; ++b) {
                Path path_AI_BJ = new Path("A" + a, "B" + b);
                assertTrue(suspender.hasObjectsSuspendedBy(path_AI_BJ));

                for (int c = 1; c <= 2; ++c) {
                    Path path_AI_BJ_CK = new Path("A" + a, "B" + b, "C" + c);
                    assertTrue(suspender.hasObjectsSuspendedBy(path_AI_BJ_CK));
                    assertFalse(
                        suspender.divisionFor(path_AI_BJ_CK).suspendedObjectTreeRoot.
                            getChild("A" + a).
                            getChild("B" + b).hasChildren());
                }
            }
        }

        // We concurrently restore the remaining objects divided in two branches.
        Collection<RecursiveAction> restorationTasks = new ArrayList<>();

        for (int i = 1; i <= 5; ++i) {
            restorationTasks.add(new RecursiveAction() {

                @Override
                protected void compute() {
                    for (int a = 1; a <= suspender.suspendedObjectDivCount / 2; ++a) {
                        for (int b = 1; b <= 2; ++b) {
                            Path path_AI_BJ = new Path("A" + a, "B" + b);

                            if (suspender.hasObjectsSuspendedBy(path_AI_BJ)) {
                                suspender.restore(path_AI_BJ);
                            }
                        }
                    }
                }
            });
        }

        for (int i = 1; i <= 5; ++i) {
            restorationTasks.add(
                new RecursiveAction() {

                    @Override
                    protected void compute() {

                        for (int a = suspender.suspendedObjectDivCount / 2 + 1; a <= suspender.suspendedObjectDivCount; ++a) {
                            for (int b = 1; b <= 2; ++b) {
                                Path path_AI_BJ = new Path("A" + a, "B" + b);

                                if (suspender.hasObjectsSuspendedBy(path_AI_BJ)) {
                                    suspender.restoreObjectWithMinRestorationTime(path_AI_BJ);
                                }
                            }
                        }
                    }
                });
        }

        invokeAll(restorationTasks);

        for (int a = 1; a <= suspender.suspendedObjectDivCount; ++a) {
            assertFalse(suspender.hasObjectsSuspendedBy(new Path("A" + a)));

            for (int b = 1; b <= 2; ++b) {
                Path path_AI_BJ = new Path("A" + a, "B" + b);
                expectedRestoredObjects.add(path_AI_BJ);

                for (int c = 1; c <= 2; ++c) {
                    Path path_AI_BJ_CK = new Path("A" + a, "B" + b, "C" + c);
                    expectedRestoredObjects.add(path_AI_BJ_CK);
                }
            }
        }

        checkNotification(expectedRestoredObjects);
        assertThat(suspender.pathFirstSegmentToDivCount.size(), is(suspender.suspendedObjectDivCount));

        for (int i = 1; i <= suspender.suspendedObjectDivCount; ++i) {
            assertThat(suspender.pathFirstSegmentToDivCount.get("A" + i).get(), is(0));
        }
    }

    void checkNotification(Set<Path> paths) throws Exception {
        assertThat(paths, is(restoredObjects));
    }

    // ****************************** //

    private MemSuspender suspender;

    private Set<Path> restoredObjects = newSetFromMap(new ConcurrentHashMap<>());

    private static final int LEAF_SUSPENSION_DURATION = 10000;
    private long leafsSuspensionStartTime;
    private long maxDeviationOfLeafRestorationTimeFromIdeal = Long.MIN_VALUE;
}
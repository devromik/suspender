package net.devromik.suspender.mem;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import static com.google.common.base.Preconditions.*;
import static java.lang.Math.abs;
import static java.lang.System.currentTimeMillis;
import static java.lang.Thread.*;
import static java.time.Duration.*;
import net.devromik.suspender.*;
import net.devromik.suspender.utils.*;
import static net.devromik.slf4jUtils.Slf4jUtils.logException;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * All suspended objects are distributed by divisions (net.devromik.suspender.mem.SuspendedObjectDivision).
 * Ideally, the distribution will be even. ;)
 *
 * There is a special thread that restores all the suspended objects with an expired restoration time.
 * The name of that thread is restorer.
 *
 * The restorer periodically iterates over all the divisions and
 * restores all objects from the current division with an expired restoration time.
 *
 * Let expired(division) denote a set of such objects.
 * If expired(division) is not empty then
 * the restorer creates a separated task that restores objects from the expired(division).
 * The task is passed to the system Fork/Join pool to be executed.
 *
 * @author Shulnyaev Roman
 */
public final class MemSuspender implements Suspender {

    public static final String RESTORER_THREAD_NAME = "Suspended Object Restorer";

    /**
     * Number of suspended object divisions.
     */
    public static final int MIN_SUSPENDED_OBJECT_DIVISION_COUNT = 4;
    public static final int MAX_SUSPENDED_OBJECT_DIVISION_COUNT = 256;
    public static final int DEFAULT_SUSPENDED_OBJECT_DIVISION_COUNT = 64;

    /**
     * Time-out after an iteration over the divisions with at least one restored object.
     */
    public static final Duration MIN_RESTORER_SLEEP_TIME_AFTER_USEFUL_WORK = ZERO;
    public static final Duration MAX_RESTORER_SLEEP_TIME_AFTER_USEFUL_WORK = ofSeconds(10L);
    public static final Duration DEFAULT_RESTORER_SLEEP_TIME_AFTER_USEFUL_WORK = MIN_RESTORER_SLEEP_TIME_AFTER_USEFUL_WORK;

    /**
     * Time-out after an iteration over the divisions with no restored objects.
     */
    public static final Duration MIN_RESTORER_SLEEP_TIME_AFTER_USELESS_WORK = ZERO;
    public static final Duration MAX_RESTORER_SLEEP_TIME_AFTER_USELESS_WORK = ofMinutes(1L);
    public static final Duration DEFAULT_RESTORER_SLEEP_TIME_AFTER_USELESS_WORK = ofSeconds(1L);

    // ****************************** //

    public MemSuspender() {
        this(
            DEFAULT_SUSPENDED_OBJECT_DIVISION_COUNT,
            DEFAULT_RESTORER_SLEEP_TIME_AFTER_USEFUL_WORK,
            DEFAULT_RESTORER_SLEEP_TIME_AFTER_USELESS_WORK);
    }

    public MemSuspender(
        int suspendedObjectDivCount,
        Duration restorerSleepTimeAfterUsefulWork,
        Duration restorerSleepTimeAfterUselessWork) {

        suspendedObjectDivCount = Ints.adjust(
            suspendedObjectDivCount,
            MIN_SUSPENDED_OBJECT_DIVISION_COUNT,
            MAX_SUSPENDED_OBJECT_DIVISION_COUNT);
        this.suspendedObjectDivCount = suspendedObjectDivCount;

        suspendedObjectDivs = new SuspendedObjectDivision[suspendedObjectDivCount];
        pathFirstSegmentToDivCount = new ConcurrentHashMap<>(suspendedObjectDivCount);

        for (int i = 0; i < suspendedObjectDivCount; ++i) {
            suspendedObjectDivs[i] = new SuspendedObjectDivision(pathFirstSegmentToDivCount);
        }

        setRestorerSleepTimeAfterUsefulWork(restorerSleepTimeAfterUsefulWork);
        setRestorerSleepTimeAfterUselessWork(restorerSleepTimeAfterUselessWork);
    }

    public void setRestorerSleepTimeAfterUsefulWork(Duration restorerSleepTimeAfterUsefulWork) {
        this.restorerSleepTimeAfterUsefulWork = Durations.adjust(
            restorerSleepTimeAfterUsefulWork,
            MIN_RESTORER_SLEEP_TIME_AFTER_USEFUL_WORK,
            MAX_RESTORER_SLEEP_TIME_AFTER_USEFUL_WORK);
    }

    public void setRestorerSleepTimeAfterUselessWork(Duration restorerSleepTimeAfterUselessWork) {
        this.restorerSleepTimeAfterUselessWork = Durations.adjust(
            restorerSleepTimeAfterUselessWork,
            MIN_RESTORER_SLEEP_TIME_AFTER_USELESS_WORK,
            MAX_RESTORER_SLEEP_TIME_AFTER_USELESS_WORK);
    }

    @Override
    public void start() {
        synchronized (lifeCycleLock) {
            checkState(!started);

            // Creating and starting the restorer.
            makeAndStartRestorer();

            started = true;
        }
    }

    private void makeAndStartRestorer() {
        // Creating the restorer.
        restorer = new Thread(
            () -> {
                while (!currentThread().isInterrupted()) {
                    try {
                        if (restoreExpired()) {
                            if (restorerSleepTimeAfterUsefulWork != ZERO) {
                                sleep(restorerSleepTimeAfterUsefulWork.toMillis());
                            }
                        }
                        else {
                            if (restorerSleepTimeAfterUselessWork != ZERO) {
                                sleep(restorerSleepTimeAfterUselessWork.toMillis());
                            }
                        }
                    }
                    catch (InterruptedException exception) {
                        currentThread().interrupt();
                    }
                    catch (Exception exception) {
                        logException(logger, exception);
                    }
                }
            },
            RESTORER_THREAD_NAME);

        // Starting the restorer.
        restorer.start();
    }

    boolean restoreExpired() {
        return restoreExpired(currentTimeMillis());
    }

    boolean restoreExpired(long expirationTime) {
        boolean atLeastOneObjectWasRestored = false;

        for (int i = 0; i < suspendedObjectDivCount; ++i) {
            if (suspendedObjectDivs[i].restoreExpired(listeners, expirationTime)) {
                atLeastOneObjectWasRestored = true;
            }
        }

        return atLeastOneObjectWasRestored;
    }

    @Override
    public void stop() {
        synchronized (lifeCycleLock) {
            checkState(started);
            restorer.interrupt();

            try {
                logger.info("Waiting for \"{}\" to be stopped...", RESTORER_THREAD_NAME);
                restorer.join();
                logger.info("\"{}\" has been stopped", RESTORER_THREAD_NAME);
            }
            catch (InterruptedException exception) {
                logger.error("Interrupted while waiting for \"{}\" to be stopped", RESTORER_THREAD_NAME);
            }

            started = false;
        }
    }

    @Override
    public void addRestoredObjectListener(RestoredObjectListener listener) {
        listeners.add(checkNotNull(listener));
    }

    @Override
    public void removeRestoredObjectListener(RestoredObjectListener listener) {
        listeners.remove(checkNotNull(listener));
    }

    @Override
    public boolean hasObjectsSuspendedBy(Path path) {
        checkArgument(path.getSegmentCount() >= MIN_SUSPENSION_PATH_SEGMENT_COUNT - 1);
        String pathFirstSegment = path.getFirstSegment();

        return
            path.getSegmentCount() == 1 ?
            hasObjectsSuspendedBy(pathFirstSegment) :
            divisionFor(path).hasObjectsSuspendedBy(path);
    }

    boolean hasObjectsSuspendedBy(String pathFirstSegment) {
        return
            pathFirstSegmentToDivCount.containsKey(pathFirstSegment) &&
            pathFirstSegmentToDivCount.get(pathFirstSegment).get() > 0;
    }

    @Override
    public void suspend(Path path, Object object, Duration duration) {
        checkArgument(path.getSegmentCount() >= MIN_SUSPENSION_PATH_SEGMENT_COUNT);
        duration = Durations.adjust(duration, MIN_SUSPENSION_DURATION, MAX_SUSPENSION_DURATION);
        divisionFor(path).suspend(path, object, duration);
    }

    @Override
    public void restore(Path path) {
        restore(path, listeners);
    }

    @Override
    public void restore(Path path, Collection<RestoredObjectListener> listeners) {
        checkArgument(path.getSegmentCount() >= MIN_SUSPENSION_PATH_SEGMENT_COUNT - 1);

        // This case is a rare one because
        // the first segment of a path is usually a top-level logical container of suspended objects.
        // Top-level containers are usually restored last.
        // So one iteration over the divisions won't hurt,
        // and it is still a very efficient and concurrent implementation.
        if (path.getSegmentCount() == 1) {
            String pathFirstSegment = path.getFirstSegment();

            if (hasObjectsSuspendedBy(pathFirstSegment)) {
                for (int i = 0; i < suspendedObjectDivCount; ++i) {
                    SuspendedObjectDivision div = suspendedObjectDivs[i];

                    if (div.hasObjectsSuspendedBy(pathFirstSegment)) {
                        div.restore(path, listeners);
                    }
                }
            }
        }
        else {
            divisionFor(path).restore(path, listeners);
        }
    }

    @Override
    public void restoreObjectWithMinRestorationTime(Path path) {
        restoreObjectWithMinRestorationTime(path, listeners);
    }

    @Override
    public void restoreObjectWithMinRestorationTime(Path path, Collection<RestoredObjectListener> listeners) {
        checkArgument(path.getSegmentCount() >= MIN_SUSPENSION_PATH_SEGMENT_COUNT - 1);

        if (path.getSegmentCount() == 1) {
            String pathFirstSegment = path.getFirstSegment();

            if (hasObjectsSuspendedBy(pathFirstSegment)) {
                Long min = Long.MAX_VALUE;
                SuspendedObjectDivision divHavingMin = null;

                for (int i = 0; i < suspendedObjectDivCount; ++i) {
                    SuspendedObjectDivision div = suspendedObjectDivs[i];

                    if (div.hasObjectsSuspendedBy(pathFirstSegment)) {
                        Long divMin = div.findMinRestorationTime(path);

                        if (divMin != null && divMin < min) {
                            min = divMin;
                            divHavingMin = div;
                        }
                    }
                }

                if (divHavingMin != null) {
                    // This is not a bug. ;)
                    // If the "found" object was restored within another operation then all is well - the source problem was solved.
                    // And we still have the right to restore another object with minimal restoration time because
                    // in essence the contract of the method is
                    // "some object with the minimal restoration time should be restored".
                    // But we cannot influence other restoration processes and we do not have to.
                    divHavingMin.restoreObjectWithMinRestorationTime(path, listeners);
                }
            }
        }
        else {
            divisionFor(path).restoreObjectWithMinRestorationTime(path, listeners);
        }
    }

    // ****************************** //

    SuspendedObjectDivision divisionFor(Path path) {
        int pathFirstSegmentHashCode = path.getSegment(0).hashCode();
        int pathSecondSegmentHashCode = path.getSegment(1).hashCode();
        int combinedHashCode = (17 * 37 + pathFirstSegmentHashCode) * 37 + pathSecondSegmentHashCode;

        return suspendedObjectDivs[abs(combinedHashCode) % suspendedObjectDivCount];
    }

    // ****************************** //

    final static long MIN_DURATION_HALF = MIN_SUSPENSION_DURATION.toMillis() / 2L;

    static long calcRestorationTime(Duration duration) {
        return calcRestorationTime(currentTimeMillis(), duration);
    }

    /**
     * Calculates and returns the object restoration time by
     * the suspension time {@code suspensionTime} and the suspension duration {@code duration}.
     *
     * The restoration queue maps restoration time to the corresponding set of suspended objects.
     * The less different restoration times there are, the better:
     *     - there are less hash tables for storing Multimap values,
     *     - iteration over Multimap keys is faster.
     *
     * So the "ideal" restoration time is calculated first
     * as a sum {@code suspensionTime + duration} and then
     * the sum is transformed so that the result can be divided
     * to {@code SuspendedObjectDivision.MIN_DURATION_HALF} without a remainder.
     * The value {@code SuspendedObjectDivision.MIN_DURATION_HALF} was chosen
     * in order for the resulting restoration time not to differ greatly from the ideal.
     */
    static long calcRestorationTime(long suspensionTime, Duration duration) {
        long restorationTime = suspensionTime + duration.toMillis();
        long remainder = restorationTime % MIN_DURATION_HALF;

        return
            remainder == 0 ?
            restorationTime :
            (restorationTime - remainder) + MIN_DURATION_HALF;
    }

    // ****************************** //

    // Life-cycle.
    private boolean started;
    private final Object lifeCycleLock = new Object();

    // Divisions between which suspended objects are distributed.
    private final SuspendedObjectDivision[] suspendedObjectDivs;
    final int suspendedObjectDivCount;

    // The value of the map is the number of divisions that contain objects
    // suspended by paths that have the first segment equal to the key of the map.
    final Map<String, AtomicInteger> pathFirstSegmentToDivCount;

    // The thread that restores suspended objects.
    private Thread restorer;

    // Time-out after an iteration over the divisions with at least one restored object.
    private volatile Duration restorerSleepTimeAfterUsefulWork;

    // Time-out after an iteration over the divisions with no restored objects.
    private volatile Duration restorerSleepTimeAfterUselessWork;

    // An object restoration event listeners.
    final CopyOnWriteArraySet<RestoredObjectListener> listeners = new CopyOnWriteArraySet<>();

    private final static Logger logger = getLogger(MemSuspender.class);
}

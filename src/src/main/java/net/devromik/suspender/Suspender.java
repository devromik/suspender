package net.devromik.suspender;

import java.time.Duration;
import java.util.Collection;
import static java.time.Duration.*;
import net.devromik.suspender.utils.Path;

/**
 * It allows suspending arbitrary objects for a certain amount of time.
 * After the time expires all the listeners subscribed to the object restoration event will be notified.
 * You can always restore the object explicitly using {@code restore(Path)} before the time expires.
 *
 * Each object is suspended by a certain unique path (net.devromik.suspender.Path).
 * A path has the following format: /segment_1/segment_2/.../segment_N.
 * A path must have at least two segments.
 * Paths are intended for performing hierarchical grouping of suspended objects.
 * Grouping allows you to work with the suspended objects on a group level.
 * For example, it is possible to explicitly restore a whole group of suspended objects.
 *
 * Use the {@code hasObjectsSuspendedBy(Path)} method to check if there are any objects
 * suspended by a path with the '/A/B/C' prefix (for example: '/A/B/C', '/A/B/C/D/E').
 * To restore all these objects, use the method {@code restore(Path)}.
 *
 * You can get notifications on object restoration by registering a listener:
 * {@code addRestoredObjectListener(RestoredObjectListener)}.
 *
 * @author Shulnyaev Roman
 */
public interface Suspender {

    int MIN_SUSPENSION_PATH_SEGMENT_COUNT = 2;

    Duration MIN_SUSPENSION_DURATION = ofMillis(100L);
    Duration MAX_SUSPENSION_DURATION = ofDays(365L * 10L);

    // ****************************** //

    /**
     * Call this method before working with a suspender.
     */
    void start();

    /**
     * Call this method after working with a suspender.
     */
    void stop();

    /**
     * Adds an object restoration event listener.
     */
    void addRestoredObjectListener(RestoredObjectListener listener);

    /**
     * Removes an object restoration event listener.
     */
    void removeRestoredObjectListener(RestoredObjectListener listener);

    /**
     * @return {@code true} iff there are any objects suspended by paths with the prefix {@code path}.
     *
     * @throws IllegalArgumentException when {@code path.getSegmentCount() < Suspender.MIN_SUSPENSION_PATH_SEGMENT_COUNT - 1}.
     */
    boolean hasObjectsSuspendedBy(Path path);

    /**
     * Suspends the object {@code object}
     * by the path {@code path}
     * for {@code duration}.
     *
     * If there is already an object suspended by the {@code path} then it will be overwritten.
     *
     * @throws IllegalArgumentException when {@code path.getSegmentCount() < Suspender.MIN_SUSPENSION_PATH_SEGMENT_COUNT}.
     */
    void suspend(Path path, Object object, Duration duration);

    /**
     * If there are any objects suspended by the paths with the prefix {@code path},
     * restores them and notifies all the registered listeners about it.
     *
     * @throws IllegalArgumentException when {@code path.getSegmentCount() < Suspender.MIN_SUSPENSION_PATH_SEGMENT_COUNT - 1}.
     */
    void restore(Path path);

    /**
     * If there are any objects suspended by the paths with the prefix {@code path},
     * restores them and notifies only the specified {@code listeners} about it.
     *
     * @throws IllegalArgumentException when {@code path.getSegmentCount() < Suspender.MIN_SUSPENSION_PATH_SEGMENT_COUNT - 1}.
     */
    void restore(Path path, Collection<RestoredObjectListener> listeners);

    /**
     * If there are any objects suspended by the {@code path},
     * restores the one with the closest restoration time
     * and notifies all the registered listeners about it.
     *
     * @throws IllegalArgumentException when {@code path.getSegmentCount() < Suspender.MIN_SUSPENSION_PATH_SEGMENT_COUNT - 1}.
     */
    void restoreObjectWithMinRestorationTime(Path path);

    /**
     * If there are any objects suspended by the {@code path},
     * restores the one with the closest restoration time
     * and notifies only the specified {@code listeners} about it.
     *
     * @throws IllegalArgumentException when {@code path.getSegmentCount() < Suspender.MIN_SUSPENSION_PATH_SEGMENT_COUNT - 1}.
     */
    void restoreObjectWithMinRestorationTime(Path path, Collection<RestoredObjectListener> listeners);
}
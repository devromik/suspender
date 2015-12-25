package net.devromik.suspender.mem;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.*;
import org.slf4j.Logger;
import com.google.common.collect.*;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.MultimapBuilder.treeKeys;
import static java.lang.Thread.*;
import net.devromik.suspender.*;
import static net.devromik.suspender.Suspender.*;
import static net.devromik.suspender.mem.MemSuspender.calcRestorationTime;
import static net.devromik.suspender.mem.SuspendedObjectTreeNode.makeRoot;
import net.devromik.suspender.utils.Path;
import static net.devromik.slf4jUtils.Slf4jUtils.logException;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * A division of suspended objects.
 *
 * It has almost the same interface as net.devromik.suspender.mem.MemSuspender.
 * Most of the interface methods take a path or a path prefix as a parameter.
 * For example:
 *     - to suspend an object by a path,
 *     - to check if there are any objects suspended by paths with a given prefix,
 *     - ...
 *
 * Before net.devromik.suspender.mem.MemSuspender performs its operation
 * it usually determines which division the passed path belongs to and then
 * delegates performing of the operation to the determined division.
 *
 * @author Shulnyaev Roman
 */
final class SuspendedObjectDivision {

    SuspendedObjectDivision(Map<String, AtomicInteger> pathFirstSegmentToDivCount) {
        this.pathFirstSegmentToDivCount = pathFirstSegmentToDivCount;
    }

    /**
     * @return {@code true} iff there are objects suspended by paths with the prefix {@code path}.
     *
     * @throws IllegalArgumentException when {@code path.getSegmentCount() < Suspender.MIN_SUSPENSION_PATH_SEGMENT_COUNT - 1}.
     */
    boolean hasObjectsSuspendedBy(Path path) {
        checkArgument(path.getSegmentCount() >= MIN_SUSPENSION_PATH_SEGMENT_COUNT - 1);
        suspendedObjectTreeLock.lock();

        try {
            SuspendedObjectTreeNode parent = findParentNodeFor(path);

            return
                (parent != null) &&
                (parent.hasSuspendedObject(path.getLastSegment()) || parent.hasChild(path.getLastSegment()));
        }
        finally {
            suspendedObjectTreeLock.unlock();
        }
    }

    SuspendedObjectTreeNode findParentNodeFor(Path path) {
        SuspendedObjectTreeNode parent = suspendedObjectTreeRoot;

        for (int i = 0; i < path.getSegmentCount() - 1; ++i) {
            String pathSegment = path.getSegment(i);

            if (parent.hasChild(pathSegment)) {
                parent = parent.getChild(pathSegment);
            }
            else {
                return null;
            }
        }

        return parent;
    }

    boolean hasObjectsSuspendedBy(String pathFirstSegment) {
        return
            suspendedObjectTreeRoot.suspendedObjects.containsKey(pathFirstSegment) ||
            suspendedObjectTreeRoot.hasChild(pathFirstSegment);
    }

    /**
     * Suspends the object {@code object}
     * by the path {@code path}
     * for {@code duration}.
     *
     * If there is already an object suspended by the {@code path} then it will be overwritten.
     *
     * @throws IllegalArgumentException when {@code path.getSegmentCount() < Suspender.MIN_SUSPENSION_PATH_SEGMENT_COUNT}.
     */
    void suspend(Path path, Object object, Duration duration) {
        checkArgument(path.getSegmentCount() >= MIN_SUSPENSION_PATH_SEGMENT_COUNT);
        String pathFirstSegment = path.getFirstSegment();
        suspendedObjectTreeLock.lock();

        try {
            boolean hadObjectsSuspendedByPathFirstSegment = hasObjectsSuspendedBy(pathFirstSegment);
            SuspendedObjectTreeNode parent = suspendedObjectTreeRoot;

            for (int i = 0; i < path.getSegmentCount() - 1; ++i) {
                parent = parent.ensureChild(path.getSegment(i));
            }

            String pathLastSegment = path.getLastSegment();

            if (parent.hasSuspendedObject(pathLastSegment)) {
                removeFromRestorationQueue(parent, pathLastSegment);
            }

            long restorationTime = calcRestorationTime(duration);
            restorationQueue.put(restorationTime, new RestorationQueueElement(parent, pathLastSegment));
            parent.suspend(pathLastSegment, object, restorationTime);

            if (!hadObjectsSuspendedByPathFirstSegment) {
                if (!pathFirstSegmentToDivCount.containsKey(pathFirstSegment)) {
                    pathFirstSegmentToDivCount.putIfAbsent(pathFirstSegment, new AtomicInteger());
                }

                pathFirstSegmentToDivCount.get(pathFirstSegment).incrementAndGet();
            }
        }
        finally {
            suspendedObjectTreeLock.unlock();
        }
    }

    /**
     * If there are any objects suspended by the paths with the prefix {@code path},
     * restores them and notifies only the specified {@code listeners} about it.
     *
     * @throws IllegalArgumentException when {@code path.getSegmentCount() < Suspender.MIN_SUSPENSION_PATH_SEGMENT_COUNT - 1}.
     */
    void restore(Path path, Collection<RestoredObjectListener> listeners) {
        checkArgument(path.getSegmentCount() >= MIN_SUSPENSION_PATH_SEGMENT_COUNT - 1);

        String pathFirstSegment = path.getFirstSegment();
        String pathLastSegment = path.getLastSegment();

        Object objectExactlyMatchedForPath = null;
        SuspendedObjectTreeNode pathSubtreeRoot = null;

        suspendedObjectTreeLock.lock();

        try {
            boolean hadObjectsSuspendedByPathFirstSegment = hasObjectsSuspendedBy(pathFirstSegment);
            SuspendedObjectTreeNode parent = findParentNodeFor(path);

            if (parent == null) {
                return;
            }

            if (parent.hasSuspendedObject(pathLastSegment)) {
                removeFromRestorationQueue(parent, pathLastSegment);
                objectExactlyMatchedForPath = parent.removeSuspendedObject(pathLastSegment);
                parent.detachRecursivelyUpIfEmpty();
            }

            // Here we are only detaching the subtree from the tree (very fast operation).
            // The "heavy" notification operation will be performed out of the critical section.
            if (parent.hasChild(pathLastSegment)) {
                pathSubtreeRoot = parent.getChild(pathLastSegment);
                pathSubtreeRoot.detach();
                parent.detachRecursivelyUpIfEmpty();
            }

            if (!hasObjectsSuspendedBy(pathFirstSegment) && hadObjectsSuspendedByPathFirstSegment) {
                pathFirstSegmentToDivCount.get(pathFirstSegment).decrementAndGet();
            }
        }
        finally {
            suspendedObjectTreeLock.unlock();
        }

        if (objectExactlyMatchedForPath != null) {
            notifyAboutObjectRestored(path, objectExactlyMatchedForPath, listeners);
        }

        if (pathSubtreeRoot != null) {
            pathSubtreeRoot.traverse(
                (restoredSubtreeNode) -> {
                    removeFromRestorationQueue(restoredSubtreeNode);
                    notifyAboutNodeRestored(restoredSubtreeNode, listeners);
                });
        }
    }

    void removeFromRestorationQueue(SuspendedObjectTreeNode parent) {
        suspendedObjectTreeLock.lock();

        try {
            parent.suspendedObjects.keySet().forEach(
                (pathLastSegment) ->
                    removeFromRestorationQueue(parent, pathLastSegment));
        }
        finally {
            suspendedObjectTreeLock.unlock();
        }
    }

    boolean removeFromRestorationQueue(SuspendedObjectTreeNode parent, String pathLastSegment) {
        return restorationQueue.remove(
            parent.getRestorationTime(pathLastSegment),
            new RestorationQueueElement(parent, pathLastSegment));
    }

    /**
     * If there are any objects suspended by the {@code path},
     * restores the one with the closest restoration time
     * and notifies only the specified {@code listeners} about it.
     *
     * @throws IllegalArgumentException when {@code path.getSegmentCount() < Suspender.MIN_SUSPENSION_PATH_SEGMENT_COUNT - 1}.
     */
    void restoreObjectWithMinRestorationTime(Path path, Collection<RestoredObjectListener> listeners) {
        checkArgument(path.getSegmentCount() >= MIN_SUSPENSION_PATH_SEGMENT_COUNT - 1);

        String pathFirstSegment = path.getFirstSegment();
        String pathLastSegment = path.getLastSegment();

        Object objectExactlyMatchedForPath = null;
        Long restorationTimeOfObjectExactlyMatchedForPath = null;

        SuspendedObjectTreeNode pathSubtreeRoot = null;
        SuspendedObjectInfo pathSubtreeMinInfo = null;
        PathAndSuspendedObject pathSubtreeMinPathAndObject = null;

        suspendedObjectTreeLock.lock();

        try {
            boolean hadObjectsSuspendedByPathFirstSegment = hasObjectsSuspendedBy(pathFirstSegment);
            SuspendedObjectTreeNode parent = findParentNodeFor(path);

            if (parent == null) {
                return;
            }

            if (parent.hasSuspendedObject(pathLastSegment)) {
                objectExactlyMatchedForPath = parent.getSuspendedObject(pathLastSegment);
                restorationTimeOfObjectExactlyMatchedForPath = parent.getRestorationTime(pathLastSegment);
            }

            if (parent.hasChild(pathLastSegment)) {
                pathSubtreeRoot = parent.getChild(pathLastSegment);
                pathSubtreeMinInfo = pathSubtreeRoot.findObjectWithMinRestorationTimeInSubtree();
            }

            if (objectExactlyMatchedForPath != null && pathSubtreeRoot != null) {
                if (restorationTimeOfObjectExactlyMatchedForPath < pathSubtreeMinInfo.restorationTime) {
                    removeFromRestorationQueue(parent, pathLastSegment);
                    parent.removeSuspendedObject(pathLastSegment);
                    parent.detachRecursivelyUpIfEmpty();
                }
                // Here we are only detaching the subtree from the tree (very fast operation).
                // The "heavy" notification operation will be performed out of the critical section.
                else {
                    removeFromRestorationQueue(pathSubtreeMinInfo.parentNode, pathSubtreeMinInfo.pathLastSegment);
                    pathSubtreeMinPathAndObject = parent.removeObjectWithMinRestorationTimeFromSubtree(pathSubtreeMinInfo);
                }
            }
            else if (objectExactlyMatchedForPath != null) {
                removeFromRestorationQueue(parent, pathLastSegment);
                parent.removeSuspendedObject(pathLastSegment);
                parent.detachRecursivelyUpIfEmpty();
            }
            // Here we are only detaching the subtree from the tree (very fast operation).
            // The "heavy" notification operation will be performed out of the critical section.
            else if (pathSubtreeMinInfo != null) {
                removeFromRestorationQueue(pathSubtreeMinInfo.parentNode, pathSubtreeMinInfo.pathLastSegment);
                pathSubtreeMinPathAndObject = parent.removeObjectWithMinRestorationTimeFromSubtree(pathSubtreeMinInfo);
            }

            if (!hasObjectsSuspendedBy(pathFirstSegment) && hadObjectsSuspendedByPathFirstSegment) {
                pathFirstSegmentToDivCount.get(pathFirstSegment).decrementAndGet();
            }
        }
        finally {
            suspendedObjectTreeLock.unlock();
        }

        if (pathSubtreeMinPathAndObject != null) {
            notifyAboutObjectRestored(
                pathSubtreeMinPathAndObject.path,
                pathSubtreeMinPathAndObject.suspendedObject,
                listeners);
        }
        else if (objectExactlyMatchedForPath != null) {
            notifyAboutObjectRestored(path, objectExactlyMatchedForPath, listeners);
        }
    }

    Long findMinRestorationTime(Path path) {
        checkArgument(path.getSegmentCount() >= MIN_SUSPENSION_PATH_SEGMENT_COUNT - 1);
        String pathLastSegment = path.getLastSegment();

        Long restorationTimeOfObjectExactlyMatchedForPath = null;

        SuspendedObjectTreeNode pathSubtreeRoot = null;
        SuspendedObjectInfo pathSubtreeMinInfo = null;

        suspendedObjectTreeLock.lock();

        try {
            SuspendedObjectTreeNode parent = findParentNodeFor(path);

            if (parent == null) {
                return null;
            }

            if (parent.hasSuspendedObject(pathLastSegment)) {
                restorationTimeOfObjectExactlyMatchedForPath = parent.getRestorationTime(pathLastSegment);
            }

            if (parent.hasChild(pathLastSegment)) {
                pathSubtreeRoot = parent.getChild(pathLastSegment);
                pathSubtreeMinInfo = pathSubtreeRoot.findObjectWithMinRestorationTimeInSubtree();
            }

            if (restorationTimeOfObjectExactlyMatchedForPath != null && pathSubtreeRoot != null) {
                if (restorationTimeOfObjectExactlyMatchedForPath < pathSubtreeMinInfo.restorationTime) {
                    return restorationTimeOfObjectExactlyMatchedForPath;
                }
                else {
                    return pathSubtreeMinInfo.restorationTime;
                }
            }
            else if (restorationTimeOfObjectExactlyMatchedForPath != null) {
                return restorationTimeOfObjectExactlyMatchedForPath;
            }
            else if (pathSubtreeMinInfo != null) {
                return pathSubtreeMinInfo.restorationTime;
            }
            else {
                return null;
            }
        }
        finally {
            suspendedObjectTreeLock.unlock();
        }
    }

    /**
     * Restores objects with expired restoration time.
     *
     * @return {@code true} iff there were any objects restored (objects with expired restoration time).
     */
    boolean restoreExpired(Collection<RestoredObjectListener> listeners, long expirationTime) {
        return new RestoreExpiredTask(listeners, expirationTime).invoke();
    }

    private class RestoreExpiredTask extends RecursiveTask<Boolean> {

        private RestoreExpiredTask(Collection<RestoredObjectListener> listeners, long expirationTime) {
            this.listeners = listeners;
            this.expirationTime = expirationTime;
        }

        @Override
        @SuppressWarnings("Convert2streamapi")
        protected Boolean compute() {
            boolean atLeastOneObjectWasRestored = false;

            while (/* there are suspended objects and */ !currentThread().isInterrupted()) {
                Collection<PathAndSuspendedObject> restoredPathAndObjects = null;
                suspendedObjectTreeLock.lock();

                try {
                    if (restorationQueue.isEmpty()) {
                        return atLeastOneObjectWasRestored;
                    }

                    long minRestorationTime = restorationQueue.keySet().iterator().next();

                    if (minRestorationTime > expirationTime) {
                        return atLeastOneObjectWasRestored;
                    }

                    for (RestorationQueueElement restoredQueueElement : restorationQueue.removeAll(minRestorationTime)) {
                        if (!restoredQueueElement.suspendedObjectParentNode.isDetached()) {
                            SuspendedObjectTreeNode restoredObjectParentNode = restoredQueueElement.suspendedObjectParentNode;
                            Path restoredObjectPath = restoredObjectParentNode.path.withAppendedSegment(restoredQueueElement.suspendedObjectPathLastSegment);
                            boolean hadObjectsSuspendedByPathFirstSegment = hasObjectsSuspendedBy(restoredObjectPath.getFirstSegment());
                            Object restoredObject = restoredObjectParentNode.removeSuspendedObject(restoredQueueElement.suspendedObjectPathLastSegment);
                            restoredObjectParentNode.detachRecursivelyUpIfEmpty();

                            if (!hasObjectsSuspendedBy(restoredObjectPath.getFirstSegment()) && hadObjectsSuspendedByPathFirstSegment) {
                                pathFirstSegmentToDivCount.get(restoredObjectPath.getFirstSegment()).decrementAndGet();
                            }

                            if (restoredPathAndObjects == null) {
                                restoredPathAndObjects = new ArrayList<>();
                            }

                            restoredPathAndObjects.add(new PathAndSuspendedObject(restoredObjectPath, restoredObject));
                        }
                    }
                }
                finally {
                    suspendedObjectTreeLock.unlock();
                }

                if (restoredPathAndObjects != null) {
                    for (PathAndSuspendedObject restoredPathAndObject : restoredPathAndObjects) {
                        notifyAboutObjectRestored(
                            restoredPathAndObject.path,
                            restoredPathAndObject.suspendedObject,
                            listeners);

                        atLeastOneObjectWasRestored = true;
                    }
                }
            }

            return atLeastOneObjectWasRestored;
        }

        // ****************************** //

        final Collection<RestoredObjectListener> listeners;
        final long expirationTime;
    }

    // ****************************** //

    private void notifyAboutObjectRestored(Path path, Object restoredObject, Collection<RestoredObjectListener> listeners) {
        for (RestoredObjectListener listener : listeners) {
            notifyAboutObjectRestored(path, restoredObject, listener);
        }
    }

    private void notifyAboutObjectRestored(Path path, Object restoredObject, RestoredObjectListener listener) {
        try {
            listener.onObjectRestored(path, restoredObject);
        }
        catch (Exception exception) {
            logException(logger, exception);
        }
    }

    private void notifyAboutNodeRestored(SuspendedObjectTreeNode node, Collection<RestoredObjectListener> listeners) {
        node.suspendedObjects.forEach(
            (pathLastSegment, suspendedObjectAndRestorationTime) ->
                notifyAboutObjectRestored(
                    node.path.withAppendedSegment(pathLastSegment),
                    suspendedObjectAndRestorationTime.suspendedObject,
                    listeners));
    }

    // ****************************** //

    // The tree of suspended objects.
    final SuspendedObjectTreeNode suspendedObjectTreeRoot = makeRoot();
    final Lock suspendedObjectTreeLock = new ReentrantLock();

    // The restoration queue of suspended objects.
    final Multimap<Long, RestorationQueueElement> restorationQueue = treeKeys().hashSetValues().build();

    // The value of the map is the number of divisions that contain objects
    // suspended by paths that have the first segment equal to the key of the map.
    final Map<String, AtomicInteger> pathFirstSegmentToDivCount;

    final static Logger logger = getLogger(SuspendedObjectDivision.class);
}

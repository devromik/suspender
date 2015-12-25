package net.devromik.suspender.mem;

import java.util.*;
import java.util.function.Consumer;
import com.google.common.collect.*;
import static com.google.common.base.Preconditions.*;
import static com.google.common.collect.MultimapBuilder.treeKeys;
import net.devromik.suspender.utils.Path;

/**
 * A node of a tree of suspended objects.
 *
 * @author Shulnyaev Roman
 */
final class SuspendedObjectTreeNode {

    SuspendedObjectTreeNode(Path path) {
        this(null, path);
    }

    SuspendedObjectTreeNode(SuspendedObjectTreeNode parent, Path path) {
        this.parent = parent;
        this.path = path;
    }

    /**
     * Creates and returns a tree root - a node without parent and path.
     */
    static SuspendedObjectTreeNode makeRoot() {
        return new SuspendedObjectTreeNode(null);
    }

    /**
     * @return {@code true} iff the node has suspended objects
     *         (there are objects suspended by paths having the format {@code this.path}/<some segment>).
     */
    boolean hasSuspendedObjects() {
        return !suspendedObjects.isEmpty();
    }

    /**
     * @return {@code true} iff there is an object suspended by the path {@code this.path}/{@code pathLastSegment}.
     */
    boolean hasSuspendedObject(String pathLastSegment) {
        return suspendedObjects.containsKey(pathLastSegment);
    }

    /**
     * Suspends the object {@code object}
     * by the path {@code this.path}/{@code pathLastSegment}
     * until the restoration time {@code restorationTime}.
     *
     * The node must not be a root.
     *
     * @throws IllegalStateException when the node is a root.
     */
    void suspend(String pathLastSegment, Object object, Long restorationTime) {
        checkState(!isRoot());
        removeSuspendedObject(pathLastSegment);

        suspendedObjects.put(
            pathLastSegment,
            new SuspendedObjectAndRestorationTime(object, restorationTime));

        restorationQueue.put(restorationTime, pathLastSegment);
    }

    Object getSuspendedObject(String pathLastSegment) {
        return suspendedObjects.get(pathLastSegment).suspendedObject;
    }

    Long getRestorationTime(String pathLastSegment) {
        return suspendedObjects.get(pathLastSegment).restorationTime;
    }

    /**
     * Removes and returns the object suspended by the path {@code this.path}/{@code pathLastSegment}.
     *
     * @return the removed object or null if there was no object
     *         suspended by the path {@code this.path}/{@code pathLastSegment}.
     */
    Object removeSuspendedObject(String pathLastSegment) {
        if (!hasSuspendedObject(pathLastSegment)) {
            return null;
        }

        Long restorationTime = getRestorationTime(pathLastSegment);
        restorationQueue.remove(restorationTime, pathLastSegment);

        return suspendedObjects.remove(pathLastSegment).suspendedObject;
    }

    boolean isEmpty() {
        return !hasSuspendedObjects() && !hasChildren();
    }

    /**
     * @return {@code true} iff the node is a root.
     */
    boolean isRoot() {
        // Only a root has a path equal to null.
        // Both a root and a detached node can have a parent equal to null.
        return path == null;
    }

    boolean hasChildren() {
        return !children.isEmpty();
    }

    /**
     * @return {@code true} iff there is a child node with the path {@code this.path}/{@code pathLastSegment}.
     */
    boolean hasChild(String pathLastSegment) {
        return children.containsKey(pathLastSegment);
    }

    /**
     * If there is a child node with the path {@code this.path}/{@code pathLastSegment} then returns it.
     * Else creates and returns such a child node.
     */
    SuspendedObjectTreeNode ensureChild(String pathLastSegment) {
        if (hasChild(pathLastSegment)) {
            return getChild(pathLastSegment);
        }
        else {
            Path newChildPath = isRoot() ? new Path(pathLastSegment) : path.withAppendedSegment(pathLastSegment);
            SuspendedObjectTreeNode newChild = new SuspendedObjectTreeNode(this, newChildPath);
            children.put(pathLastSegment, newChild);

            return newChild;
        }
    }

    SuspendedObjectTreeNode getChild(String pathLastSegment) {
        return children.get(pathLastSegment);
    }

    /**
     * @return number of children.
     */
    int getChildCount() {
        return children.size();
    }

    /**
     * @return {@code true} iff the subtree having this node as a root
     *         was previously disconnected from the tree by calling the {@code detach()}.
     */
    boolean isDetached() {
        SuspendedObjectTreeNode node = this;

        while (node.parent != null) {
            node = node.parent;
        }

        return !node.isRoot();
    }

    /**
     * Disconnects the subtree having this node as a root from the tree.
     * If this node is a root then disconnecting is not performed: {@code this.isDetached() == false}.
     */
    void detach() {
        if (parent != null) {
            parent.children.remove(path.getLastSegment());
            parent = null;
        }
    }

    void detachRecursivelyUpIfEmpty() {
        if (parent != null && isEmpty()) {
            SuspendedObjectTreeNode parent = this.parent;
            detach();
            parent.detachRecursivelyUpIfEmpty();
        }
    }

    /**
     * Traverses the subtree having this node as a root
     * passing each traversed node to the {@code nodeConsumer.accept(SuspendedObjectTreeNode)}.
     */
    void traverse(Consumer<SuspendedObjectTreeNode> nodeConsumer) {
        Queue<SuspendedObjectTreeNode> queue = new ArrayDeque<>();
        queue.add(this);

        while (!queue.isEmpty()) {
            SuspendedObjectTreeNode node = queue.remove();
            nodeConsumer.accept(node);
            node.children.forEach((childPathLastSegment, child) -> queue.add(child));
        }
    }

    /**
     * If the subtree having this node as a root is not empty then finds one of the objects
     * having the minimal restoration time among all such objects,
     * removes it from the subtree and returns it.
     *
     * If the subtree is empty then returns {@code null}.
     */
    PathAndSuspendedObject removeObjectWithMinRestorationTimeFromSubtree() {
        return removeObjectWithMinRestorationTimeFromSubtree(findObjectWithMinRestorationTimeInSubtree());
    }

    PathAndSuspendedObject removeObjectWithMinRestorationTimeFromSubtree(SuspendedObjectInfo subtreeMin) {
        if (subtreeMin.isInitialized()) {
            Object suspendedObject = subtreeMin.parentNode.removeSuspendedObject(subtreeMin.pathLastSegment);
            subtreeMin.parentNode.detachRecursivelyUpIfEmpty();

            return new PathAndSuspendedObject(
                subtreeMin.getPath(),
                suspendedObject);
        }
        else {
            return null;
        }
    }

    /**
     * If the subtree having this node as a root is not empty then
     * finds one of the objects having the minimal restoration time among all such objects
     * and returns it.
     *
     * If the subtree is empty then returns {@code null}.
     */
    SuspendedObjectInfo findObjectWithMinRestorationTimeInSubtree() {
        SuspendedObjectInfo subtreeMin = new SuspendedObjectInfo();

        traverse(
            node -> {
                if (!node.hasSuspendedObjects()) {
                    return;
                }

                Map.Entry<Long, String> nodeMin = node.restorationQueue.entries().iterator().next();
                String nodeMinPathLastSegment = nodeMin.getValue();
                Long nodeMinRestorationTime = nodeMin.getKey();

                if (subtreeMin.isInitialized()) {
                    if (nodeMinRestorationTime < subtreeMin.restorationTime) {
                        subtreeMin.parentNode = node;
                        subtreeMin.pathLastSegment = nodeMinPathLastSegment;
                        subtreeMin.restorationTime = nodeMinRestorationTime;
                    }
                }
                else {
                    subtreeMin.parentNode = node;
                    subtreeMin.pathLastSegment = nodeMinPathLastSegment;
                    subtreeMin.restorationTime = nodeMinRestorationTime;
                }
            });

        return subtreeMin;
    }

    // ****************************** //

    // Path of the node in the tree.
    final Path path;

    // Parent node.
    SuspendedObjectTreeNode parent;

    // Child nodes.
    //
    // The last segment of a child node path is mapped to the child node.
    // The path of a child node: this.path/<the last segment of the child node>.
    final Map<String, SuspendedObjectTreeNode> children = new HashMap<>();

    // Suspended objects.
    //
    // suspendedObjects maps the last segment of an object suspension path to
    //                  the suspended object.
    //
    // restorationQueue maps common planned restoration time of a set of suspended objects to
    //                  the set of the last segments of the suspended object paths.
    final Map<String, SuspendedObjectAndRestorationTime> suspendedObjects = new HashMap<>();
    final Multimap<Long, String> restorationQueue = treeKeys().hashSetValues().build();
}
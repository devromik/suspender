package net.devromik.suspender.mem;

import com.google.common.base.MoreObjects;

/**
 * An element of a restoration queue associated with a suspended object division
 * (net.devromik.suspender.SuspendedObjectDivision).
 *
 * There is a one-to-one correspondence between
 * restoration queue elements and suspended objects.
 *
 * The element includes:
 *     - the node of the suspended object tree corresponding to
 *       all the suspension path segments except for the last one;
 *     - the last segment of the suspension path.
 *
 * The element is used while restoring the suspended object
 * for faster deletion (since we already have the node)
 * from the suspended object tree.
 *
 * @author Shulnyaev Roman
 */
final class RestorationQueueElement {

    RestorationQueueElement(
        SuspendedObjectTreeNode suspendedObjectParentNode,
        String suspendedObjectPathLastSegment) {

        this.suspendedObjectParentNode = suspendedObjectParentNode;
        this.suspendedObjectPathLastSegment = suspendedObjectPathLastSegment;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }

        if (other == null) {
            return false;
        }

        if (other.getClass() != getClass()) {
            return false;
        }

        RestorationQueueElement otherRestorationQueueElement = (RestorationQueueElement)other;

        return
            suspendedObjectParentNode.path.equals(otherRestorationQueueElement.suspendedObjectParentNode.path) &&
            suspendedObjectPathLastSegment.equals(otherRestorationQueueElement.suspendedObjectPathLastSegment);
    }

    @Override
    public int hashCode() {
        int hashCode = 17;
        hashCode = hashCode * 37 + suspendedObjectParentNode.path.hashCode();
        hashCode = hashCode * 37 + suspendedObjectPathLastSegment.hashCode();

        return hashCode;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("suspendedObjectParentNode.path", suspendedObjectParentNode.path)
            .add("suspendedObjectPathLastSegment", suspendedObjectPathLastSegment)
            .toString();
    }

    // ****************************** //

    final SuspendedObjectTreeNode suspendedObjectParentNode;
    final String suspendedObjectPathLastSegment;
}

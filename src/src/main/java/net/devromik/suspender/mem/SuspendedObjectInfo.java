package net.devromik.suspender.mem;

import net.devromik.suspender.utils.Path;

/**
 * Full information on suspended object: location in a tree and restoration time.
 *
 * @author Shulnyaev Roman
 */
final class SuspendedObjectInfo {

    boolean isInitialized() {
        return parentNode != null;
    }

    Path getPath() {
        return parentNode.path.withAppendedSegment(pathLastSegment);
    }

    // ****************************** //

    SuspendedObjectTreeNode parentNode;
    String pathLastSegment;
    Long restorationTime;
}

package net.devromik.suspender.mem;

import net.devromik.suspender.utils.Path;

/**
 * @author Shulnyaev Roman
 */
final class PathAndSuspendedObject {

    PathAndSuspendedObject(Path path, Object suspendedObject) {
        this.path = path;
        this.suspendedObject = suspendedObject;
    }

    // ****************************** //

    final Path path;
    final Object suspendedObject;
}

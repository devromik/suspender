package net.devromik.suspender.mem;

/**
 * @author Shulnyaev Roman
 */
final class SuspendedObjectAndRestorationTime {

    SuspendedObjectAndRestorationTime(Object suspendedObject, Long restorationTime) {
        this.suspendedObject = suspendedObject;
        this.restorationTime = restorationTime;
    }

    // ****************************** //

    final Object suspendedObject;
    final Long restorationTime;
}

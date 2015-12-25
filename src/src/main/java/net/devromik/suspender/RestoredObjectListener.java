package net.devromik.suspender;

import net.devromik.suspender.utils.Path;

/**
 * An object restoration event listener.
 *
 * @author Shulnyaev Roman
 */
public interface RestoredObjectListener {

    /**
     * @param suspensionPath path which was used for suspension of the restoredObject.
     * @param restoredObject restored object.
     */
    void onObjectRestored(Path suspensionPath, Object restoredObject) throws Exception;
}

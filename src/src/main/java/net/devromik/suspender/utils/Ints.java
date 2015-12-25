package net.devromik.suspender.utils;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.*;

/**
 * @author Shulnyaev Roman
 */
public final class Ints {

    /**
     * @throws IllegalArgumentException when {@code min} > {@code max}.
     */
    public static int adjust(int value, int min, int max) {
        checkArgument(min <= max);
        return min(max(min, value), max);
    }
}

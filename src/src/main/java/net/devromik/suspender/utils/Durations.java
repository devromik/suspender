package net.devromik.suspender.utils;

import java.time.Duration;
import static com.google.common.base.Preconditions.*;

/**
 * @author Shulnyaev Roman
 */
public final class Durations {

    /**
     * @throws IllegalArgumentException when {@code min} > {@code max}.
     */
    public static Duration adjust(Duration value, Duration min, Duration max) {
        checkArgument(min.compareTo(max) < 1);

        if (value.compareTo(min) < 0)  {
            value = min;
        }

        if (value.compareTo(max) > 0)  {
            value = max;
        }

        return value;
    }
}
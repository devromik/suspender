package net.devromik.suspender.utils;

import org.junit.Test;
import static java.time.Duration.*;
import static net.devromik.suspender.utils.Durations.adjust;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * @author Shulnyaev Roman
 */
public class DurationsTest {

    @Test
    public void canAdjustValue() throws Exception {
        assertThat(adjust(ofMillis(-1L), ZERO, ofMillis(2L)), is(ZERO));
        assertThat(adjust(ZERO, ZERO, ofMillis(2L)), is(ZERO));
        assertThat(adjust(ofMillis(1L), ZERO, ofMillis(2L)), is(ofMillis(1L)));
        assertThat(adjust(ofMillis(2L), ZERO, ofMillis(2L)), is(ofMillis(2L)));
        assertThat(adjust(ofMillis(3L), ZERO, ofMillis(2L)), is(ofMillis(2L)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void whenAdjustingMinShouldBeLessOrEqualThanMax() throws Exception {
        adjust(ZERO, ofMillis(2L), ofMillis(1L));
    }
}
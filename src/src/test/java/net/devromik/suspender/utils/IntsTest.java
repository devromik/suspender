package net.devromik.suspender.utils;

import org.junit.Test;
import static net.devromik.suspender.utils.Ints.adjust;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * @author Shulnyaev Roman
 */
public class IntsTest {

    @Test
    public void canAdjustValue() throws Exception {
        assertThat(adjust(-1, 0, 2), is(0));
        assertThat(adjust(0, 0, 2), is(0));
        assertThat(adjust(1, 0, 2), is(1));
        assertThat(adjust(2, 0, 2), is(2));
        assertThat(adjust(3, 0, 2), is(2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void whenAdjustingMinShouldBeLessOrEqualThanMax() throws Exception {
        adjust(0, 2, 1);
    }
}
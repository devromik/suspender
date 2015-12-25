package net.devromik.suspender.mem;

import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.junit.Test;
import com.google.common.base.MoreObjects;
import net.devromik.suspender.utils.Path;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

/**
 * @author Shulnyaev Roman
 */
public class RestorationQueueElementTest {

    @Test
    public void testEquals() throws Exception {
        RestorationQueueElement element = new RestorationQueueElement(
            new SuspendedObjectTreeNode(new Path("segment_1", "segment_2")),
            "segment_3");

        RestorationQueueElement restorationQueueElementCopy = new RestorationQueueElement(
            new SuspendedObjectTreeNode(new Path("segment_1", "segment_2")),
            "segment_3");

        assertThat(element, is(equalTo(element)));
        assertThat(element, is(not(equalTo(null))));
        assertThat(element, is(not(equalTo(new Object()))));
        assertThat(element, is(equalTo(restorationQueueElementCopy)));
        assertThat(element, is(not(equalTo(new RestorationQueueElement(new SuspendedObjectTreeNode(new Path("segment_1", "~~~NOT EQUAL~~~")), "segment_3")))));
        assertThat(element, is(not(equalTo(new RestorationQueueElement(new SuspendedObjectTreeNode(new Path("segment_1", "segment_2")), "~~~NOT EQUAL~~~")))));
    }

    @Test
    public void testHashCode() throws Exception {
        RestorationQueueElement element = new RestorationQueueElement(
            new SuspendedObjectTreeNode(new Path("segment_1", "segment_2")),
            "segment_3");

        int expected = new HashCodeBuilder().
            append(element.suspendedObjectParentNode.path).
            append(element.suspendedObjectPathLastSegment).toHashCode();

        assertThat(element.hashCode(), is(expected));
    }

    @Test
    public void testToString() throws Exception {
        RestorationQueueElement element = new RestorationQueueElement(
            new SuspendedObjectTreeNode(new Path("segment_1", "segment_2")),
            "segment_3");

        String expected = MoreObjects.toStringHelper(element).
            add("suspendedObjectParentNode.path", element.suspendedObjectParentNode.path).
            add("suspendedObjectPathLastSegment", element.suspendedObjectPathLastSegment).toString();

        assertThat(element.toString(), is(expected));
    }
}
package net.devromik.suspender.utils;

import java.util.Iterator;
import org.junit.Test;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

/**
 * @author Shulnyaev Roman
 */
public class PathTest {

    @Test
    public void consistsOfSegments() throws Exception {
        Path path = new Path();

        assertThat(path.getSegmentCount(), is(0));
        assertFalse(path.iterator().hasNext());
        assertTrue(path.isRoot());

        // ****************************** //

        path = new Path("segment_1", "segment_2");

        assertThat(path.getSegmentCount(), is(2));
        assertThat(path.getSegment(0), is("segment_1"));
        assertThat(path.getSegment(1), is("segment_2"));

        Iterator<String> iterOverSegments = path.iterator();
        assertThat(iterOverSegments.next(), is("segment_1"));
        assertThat(iterOverSegments.next(), is("segment_2"));
        assertFalse(iterOverSegments.hasNext());

        assertThat(path.getFirstSegment(), is("segment_1"));
        assertThat(path.getLastSegment(), is("segment_2"));

        assertFalse(path.isRoot());

        // ****************************** //

        path = new Path("segment_1", "segment_2", "segment_3");

        assertThat(path.getSegmentCount(), is(3));
        assertThat(path.getSegment(0), is("segment_1"));
        assertThat(path.getSegment(1), is("segment_2"));
        assertThat(path.getSegment(2), is("segment_3"));

        iterOverSegments = path.iterator();
        assertThat(iterOverSegments.next(), is("segment_1"));
        assertThat(iterOverSegments.next(), is("segment_2"));
        assertThat(iterOverSegments.next(), is("segment_3"));
        assertFalse(iterOverSegments.hasNext());

        assertThat(path.getFirstSegment(), is("segment_1"));
        assertThat(path.getLastSegment(), is("segment_3"));

        assertFalse(path.isRoot());
    }

    @Test(expected = NullPointerException.class)
    public void segmentArrayShouldNotBeNull() throws Exception {
        new Path((String[])null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void segmentShouldNotBeNull() throws Exception {
        new Path("segment_1", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void segmentShouldNotBeEmpty() throws Exception {
        new Path("segment_1", "");
    }

    @Test(expected = IllegalArgumentException.class)
    public void segmentShouldNotContainLeadingSlash() throws Exception {
        new Path("/segment_1", "segment_2");
    }

    @Test(expected = IllegalArgumentException.class)
    public void segmentShouldNotContainNotLeadingSlash() throws Exception {
        new Path("segment/_1", "segment_2");
    }

    @Test
    public void canBuildNewPathByAppendingSegment() {
        Path path = new Path();
        assertThat(
            path.withAppendedSegment("segment_1"),
            is(new Path("segment_1")));

        path = new Path("segment_1", "segment_2");
        assertThat(
            path.withAppendedSegment("segment_3"),
            is(new Path("segment_1", "segment_2", "segment_3")));
    }

    @Test
    public void canTestForPrefix() {
        Path path = new Path();

        assertTrue(path.hasPrefix(new Path()));
        assertFalse(path.hasPrefix(new Path("segment_1")));

        // ****************************** //

        path = new Path("segment_1", "segment_2", "segment_3");

        assertTrue(path.hasPrefix(new Path("segment_1", "segment_2")));
        assertTrue(path.hasPrefix(new Path("segment_1", "segment_2", "segment_3")));

        assertFalse(path.hasPrefix(new Path("segment_2", "segment_3")));
        assertFalse(path.hasPrefix(new Path("segment_1", "segment_2", "segment_3", "segment_4")));
    }

    @Test
    public void canTestForSuffix() {
        Path path = new Path();

        assertTrue(path.hasSuffix(new Path()));
        assertFalse(path.hasSuffix(new Path("segment_1")));

        // ****************************** //

        path = new Path("segment_1", "segment_2", "segment_3");

        assertTrue(path.hasSuffix(new Path("segment_2", "segment_3")));
        assertTrue(path.hasSuffix(new Path("segment_1", "segment_2", "segment_3")));

        assertFalse(path.hasSuffix(new Path("segment_1", "segment_2")));
        assertFalse(path.hasSuffix(new Path("segment_0", "segment_1", "segment_2", "segment_3")));
    }

    @Test
    @SuppressWarnings("ObjectEqualsNull")
    public void pathsAreEqualThenAndOnlyThenWhenTheirSegmentSequencesAreEqual() {
        Path path = new Path("segment_1", "segment_2");
        Path pathCopy = new Path("segment_1", "segment_2");

        assertThat(path, is(equalTo(path)));
        assertThat(path, is(equalTo(pathCopy)));
        assertThat(path.hashCode(), is(pathCopy.hashCode()));
        assertThat(path, is(not(equalTo(null))));
        assertThat(path, is(not(equalTo(new Object()))));
        assertThat(path, is(not(equalTo(new Path("segment_1", "segment_2", "segment_3")))));

        // ****************************** //

        path = new Path("segment_1", "segment_2", "segment_3");
        pathCopy = new Path("segment_1", "segment_2", "segment_3");

        assertThat(path, is(equalTo(path)));
        assertThat(path, is(equalTo(pathCopy)));
        assertThat(path.hashCode(), is(pathCopy.hashCode()));
        assertThat(path, is(not(equalTo(new Path("segment_1", "segment_2")))));

        // ****************************** //

        path = new Path();
        pathCopy = new Path();

        assertThat(path, is(equalTo(path)));
        assertThat(path, is(equalTo(pathCopy)));
        assertThat(path.hashCode(), is(pathCopy.hashCode()));
        assertThat(path, is(not(equalTo(new Path("segment_1")))));
    }

    @Test
    public void toStringReturnsSegmentsDividedBySlashWithLeadingSlash() {
        Path path = new Path();
        assertThat(path.toString(), is("/"));

        path = new Path("segment_1", "segment_2");
        assertThat(path.toString(), is("/segment_1/segment_2"));

        path = new Path("segment_1", "segment_2", "segment_3");
        assertThat(path.toString(), is("/segment_1/segment_2/segment_3"));
    }
}
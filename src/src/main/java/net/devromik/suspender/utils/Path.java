package net.devromik.suspender.utils;

import java.util.*;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterators;
import static com.google.common.base.Preconditions.*;
import static com.google.common.base.Strings.isNullOrEmpty;
import static org.apache.commons.lang3.ArrayUtils.add;

/**
 * Path is a sequence of strings named segments.
 * Serves as a key for object suspension.
 *
 * A segment cannot be null or empty.
 * A segment cannot contain '/'.
 *
 * This class is thread-safe.
 *
 * @author Shulnyaev Roman
 */
public final class Path implements Iterable<String> {

    /**
     * @throws NullPointerException when {@code segments} is null.
     * @throws IllegalArgumentException when at least one element of the {@code segments} is null or empty.
     * @throws IllegalArgumentException when at least one element of the {@code segments} contains '/'.
     */
    public Path(String... segments) {
        for (String segment : segments) {
            checkArgument(!isNullOrEmpty(segment) && !segment.contains("/"));
        }

        this.segments = segments;
    }

    /**
     * @return iterator over the path segments.
     */
    @Override
    public Iterator<String> iterator() {
        return Iterators.forArray(segments);
    }

    /**
     * @return the {@code i}-th segment of the path, {@code i} = 0..{@code getSegmentCount()} - 1.
     *
     * @throws ArrayIndexOutOfBoundsException when {@code i} is less than zero
     *         or the number of the path segments is less than {@code i} + 1;
     */
    public String getSegment(int i) {
        return segments[i];
    }

    /**
     * @return the first segment of the path.
     * @throws ArrayIndexOutOfBoundsException when the path does not have any segments.
     */
    public String getFirstSegment() {
        return segments[0];
    }

    /**
     * @return the last segment of the path.
     * @throws ArrayIndexOutOfBoundsException when the path does not have any segments.
     */
    public String getLastSegment() {
        return segments[getSegmentCount() - 1];
    }

    /**
     * @return the number of path segments.
     */
    public int getSegmentCount() {
        return segments.length;
    }

    /**
     * @return a new path consisting of this path segments with appended @{segment}.
     */
    public Path withAppendedSegment(String segment) {
        return new Path(add(segments, segment));
    }

    /**
     * @return {@code true} iff the i-th segment of the path
     *         equals the i-th segment of the {@code prefix},
     *         i = 0..{@code prefix.getSegmentCount()} - 1.
     */
    public boolean hasPrefix(Path prefix) {
        int prefixSegmentCount = prefix.getSegmentCount();

        if (prefixSegmentCount > getSegmentCount()) {
            return false;
        }

        for (int i = 0; i < prefixSegmentCount; ++i) {
            if (!prefix.getSegment(i).equals(getSegment(i))) {
                return false;
            }
        }

        return true;
    }

    /**
     * @return {@code true} iff the {@code getSegmentCount() - suffix.getSegmentCount() + i}-th segment of the path
     *         equals the i-th segment of the {@code suffix},
     *         i = 0..{@code suffix.getSegmentCount()} - 1.
     */
    public boolean hasSuffix(Path suffix) {
        int suffixSegmentCount = suffix.getSegmentCount();
        int segmentCount = getSegmentCount();

        if (suffixSegmentCount > segmentCount) {
            return false;
        }

        int suffixStartPos = segmentCount - suffixSegmentCount;

        for (int i = 0; i < suffixSegmentCount; ++i) {
            if (!suffix.getSegment(i).equals(getSegment(suffixStartPos + i))) {
                return false;
            }
        }

        return true;
    }

    /**
     * @return {@code true} iff the path does not have any segments.
     */
    public boolean isRoot() {
        return getSegmentCount() == 0;
    }

    // ****************************** //

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }

        if (other == null) {
            return false;
        }

        if (other.getClass() != getClass()) {
            return false;
        }

        Path otherPath = (Path)other;
        return Arrays.equals(segments, otherPath.segments);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(segments);
    }

    @Override
    public String toString() {
        return "/" + Joiner.on("/").join(segments);
    }

    // ****************************** //

    private final String[] segments;
}

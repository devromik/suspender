package net.devromik.suspender.mem;

import java.util.Set;
import org.junit.Test;
import static net.devromik.suspender.mem.SuspendedObjectTreeNode.makeRoot;
import net.devromik.suspender.utils.Path;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.mockito.internal.util.collections.Sets.newSet;

/**
 * @author Shulnyaev Roman
 */
public class SuspendedObjectTreeNodeTest {

    @Test
    public void hasParentAndPath() throws Exception {
        SuspendedObjectTreeNode root = makeRoot();

        assertNull(root.parent);
        assertNull(root.path);
        assertTrue(root.isRoot());
        assertFalse(root.hasSuspendedObjects());
        assertTrue(root.children.isEmpty());

        // ****************************** //

        Path nodePath = new Path("segment_1", "segment_2", "segment_3");
        SuspendedObjectTreeNode node = new SuspendedObjectTreeNode(root, nodePath);

        assertThat(node.parent, is(root));
        assertThat(node.path, is(nodePath));
        assertFalse(node.isRoot());
        assertFalse(node.hasSuspendedObjects());
        assertTrue(node.children.isEmpty());
    }

    @Test
    public void canEnsureChild() {
        SuspendedObjectTreeNode root = makeRoot();

        root.ensureChild("rootChild").ensureChild("rootChild_Child_1");
        root.ensureChild("rootChild").ensureChild("rootChild_Child_2");

        assertThat(root.getChildCount(), is(1));
        assertTrue(root.hasChild("rootChild"));

        // ****************************** //

        SuspendedObjectTreeNode rootChild = root.ensureChild("rootChild");
        assertThat(root.getChildCount(), is(1));
        assertThat(rootChild.parent, is(root));
        assertThat(rootChild.getChildCount(), is(2));
        assertTrue(rootChild.hasChild("rootChild_Child_1"));
        assertTrue(rootChild.hasChild("rootChild_Child_2"));

        // ****************************** //

        SuspendedObjectTreeNode rootChild_Child_1 = rootChild.ensureChild("rootChild_Child_1");
        assertThat(rootChild.getChildCount(), is(2));
        assertThat(rootChild_Child_1.parent, is(rootChild));
        assertThat(rootChild_Child_1.getChildCount(), is(0));

        // ****************************** //

        SuspendedObjectTreeNode rootChild_Child_2 = rootChild.ensureChild("rootChild_Child_2");
        assertThat(rootChild.getChildCount(), is(2));
        assertThat(rootChild_Child_2.parent, is(rootChild));
        assertThat(rootChild_Child_2.getChildCount(), is(0));
    }

    @Test
    public void canSuspendObject() {
        SuspendedObjectTreeNode node = new SuspendedObjectTreeNode(new Path("segment_1", "segment_2"));
        assertFalse(node.hasSuspendedObjects());

        // ****************************** //

        Object segment_3_object = new Object();
        Long segment_3_restorationTime = 3L;

        node.suspend("segment_3", segment_3_object, segment_3_restorationTime);

        assertTrue(node.hasSuspendedObjects());
        assertTrue(node.hasSuspendedObject("segment_3"));

        assertThat(node.suspendedObjects.size(), is(1));
        assertTrue(node.suspendedObjects.containsKey("segment_3"));

        assertThat(node.suspendedObjects.get("segment_3").suspendedObject, is(segment_3_object));
        assertThat(node.suspendedObjects.get("segment_3").restorationTime, is(segment_3_restorationTime));

        assertThat(node.getRestorationTime("segment_3"), is(segment_3_restorationTime));
        assertThat(node.restorationQueue.size(), is(1));
        assertTrue(node.restorationQueue.containsEntry(segment_3_restorationTime, "segment_3"));

        assertFalse(node.hasSuspendedObject("segment_4"));
        assertFalse(node.hasSuspendedObject("segment_5"));

        // We check the case when two different suspended objects have different restoration times.
        Object segment_4_object = new Object();
        Long segment_4_restorationTime = 4L;

        node.suspend("segment_4", segment_4_object, segment_4_restorationTime);

        assertThat(node.suspendedObjects.size(), is(2));
        assertThat(node.restorationQueue.size(), is(2));

        assertTrue(node.hasSuspendedObjects());
        assertTrue(node.hasSuspendedObject("segment_3"));

        assertTrue(node.suspendedObjects.containsKey("segment_3"));
        assertThat(node.suspendedObjects.get("segment_3").suspendedObject, is(segment_3_object));
        assertThat(node.getSuspendedObject("segment_3"), is(segment_3_object));

        assertThat(node.suspendedObjects.get("segment_3").restorationTime, is(segment_3_restorationTime));
        assertThat(node.getRestorationTime("segment_3"), is(segment_3_restorationTime));
        assertTrue(node.restorationQueue.containsEntry(segment_3_restorationTime, "segment_3"));

        assertTrue(node.hasSuspendedObjects());
        assertTrue(node.hasSuspendedObject("segment_4"));

        assertTrue(node.suspendedObjects.containsKey("segment_4"));
        assertThat(node.suspendedObjects.get("segment_4").suspendedObject, is(segment_4_object));
        assertThat(node.getSuspendedObject("segment_4"), is(segment_4_object));

        assertThat(node.suspendedObjects.get("segment_4").restorationTime, is(segment_4_restorationTime));
        assertThat(node.getRestorationTime("segment_4"), is(segment_4_restorationTime));
        assertTrue(node.restorationQueue.containsEntry(segment_4_restorationTime, "segment_4"));

        // We check the case when two different suspended objects have the same restoration time.
        Object segment_5_object = new Object();
        Long segment_5_restorationTime = 4L;

        node.suspend("segment_5", segment_5_object, segment_5_restorationTime);

        assertThat(node.suspendedObjects.size(), is(3));
        assertThat(node.restorationQueue.size(), is(3));

        assertTrue(node.hasSuspendedObjects());
        assertTrue(node.hasSuspendedObject("segment_3"));

        assertTrue(node.suspendedObjects.containsKey("segment_3"));
        assertThat(node.suspendedObjects.get("segment_3").suspendedObject, is(segment_3_object));
        assertThat(node.getSuspendedObject("segment_3"), is(segment_3_object));

        assertThat(node.suspendedObjects.get("segment_3").restorationTime, is(segment_3_restorationTime));
        assertThat(node.getRestorationTime("segment_3"), is(segment_3_restorationTime));
        assertTrue(node.restorationQueue.containsEntry(segment_3_restorationTime, "segment_3"));

        assertTrue(node.hasSuspendedObjects());
        assertTrue(node.hasSuspendedObject("segment_4"));

        assertTrue(node.suspendedObjects.containsKey("segment_4"));
        assertThat(node.suspendedObjects.get("segment_4").suspendedObject, is(segment_4_object));
        assertThat(node.getSuspendedObject("segment_4"), is(segment_4_object));

        assertThat(node.suspendedObjects.get("segment_4").restorationTime, is(segment_4_restorationTime));
        assertThat(node.getRestorationTime("segment_4"), is(segment_4_restorationTime));
        assertTrue(node.restorationQueue.containsEntry(segment_4_restorationTime, "segment_4"));

        assertTrue(node.hasSuspendedObjects());
        assertTrue(node.hasSuspendedObject("segment_5"));

        assertTrue(node.suspendedObjects.containsKey("segment_5"));
        assertThat(node.suspendedObjects.get("segment_5").suspendedObject, is(segment_5_object));
        assertThat(node.getSuspendedObject("segment_5"), is(segment_5_object));

        assertThat(node.suspendedObjects.get("segment_5").restorationTime, is(segment_5_restorationTime));
        assertThat(node.getRestorationTime("segment_5"), is(segment_5_restorationTime));
        assertTrue(node.restorationQueue.containsEntry(segment_4_restorationTime, "segment_5"));

        // ****************************** //

        node.removeSuspendedObject("segment_3");
        node.removeSuspendedObject("segment_4");
        node.removeSuspendedObject("segment_5");
        node.detachRecursivelyUpIfEmpty();

        assertTrue(node.isEmpty());
        assertFalse(node.hasSuspendedObjects());
        assertTrue(node.suspendedObjects.isEmpty());
        assertTrue(node.restorationQueue.isEmpty());
    }

    @Test(expected = IllegalStateException.class)
    public void cannotSuspendObjectWhenNodeIsRoot() {
        makeRoot().suspend("pathLastSegment", new Object(), 0L);
    }

    @Test
    public void canDetachSubtreeFromTree() {
        SuspendedObjectTreeNode root = makeRoot();
        assertFalse(root.isDetached());

        SuspendedObjectTreeNode rootChild = root.ensureChild("rootChild");
        rootChild.ensureChild("rootChild_Child_1");
        rootChild.ensureChild("rootChild_Child_2");

        assertFalse(rootChild.isDetached());
        assertFalse(rootChild.ensureChild("rootChild_Child_1").isDetached());
        assertFalse(rootChild.ensureChild("rootChild_Child_2").isDetached());

        SuspendedObjectTreeNode rootChild_Child_2 = rootChild.ensureChild("rootChild_Child_2");
        rootChild_Child_2.detach();

        assertTrue(rootChild_Child_2.isDetached());
        assertNull(rootChild_Child_2.parent);
        assertFalse(rootChild.hasChild("rootChild_Child_2"));

        root.detach();
        assertFalse(root.isDetached());
    }

    @Test
    public void canTraverseSubtree() {
        SuspendedObjectTreeNode root = makeRoot();

        SuspendedObjectTreeNode rootChild = root.ensureChild("rootChild");
        SuspendedObjectTreeNode rootChild_Child_1 = rootChild.ensureChild("rootChild_Child_1");
        SuspendedObjectTreeNode rootChild_Child_2 = rootChild.ensureChild("rootChild_Child_2");

        Set<SuspendedObjectTreeNode> traversedNodes = newSet();
        root.traverse(traversedNodes::add);
        assertThat(traversedNodes, is(newSet(root, rootChild, rootChild_Child_1, rootChild_Child_2)));

        traversedNodes = newSet();
        rootChild.traverse(traversedNodes::add);
        assertThat(traversedNodes, is(newSet(rootChild, rootChild_Child_1, rootChild_Child_2)));

        traversedNodes = newSet();
        rootChild_Child_1.traverse(traversedNodes::add);
        assertThat(traversedNodes, is(newSet(rootChild_Child_1)));

        traversedNodes = newSet();
        rootChild_Child_2.traverse(traversedNodes::add);
        assertThat(traversedNodes, is(newSet(rootChild_Child_2)));
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    public void canRemoveObjectWithMinRestorationTimeFromSubtree() {
        SuspendedObjectTreeNode root = makeRoot();
        assertNull(root.removeObjectWithMinRestorationTimeFromSubtree());

        // ****************************** //

        SuspendedObjectTreeNode rootChild = root.ensureChild("rootChild");
        assertNull(rootChild.removeObjectWithMinRestorationTimeFromSubtree());

        String rootChild_1_object = "rootChild_1_object";
        Long rootChild_1_restorationTime = 3L;
        rootChild.suspend("rootChild_1_object", rootChild_1_object, rootChild_1_restorationTime);

        // ****************************** //

        SuspendedObjectTreeNode rootChild_Child_1 = rootChild.ensureChild("rootChild_Child_1");
        assertNull(rootChild_Child_1.removeObjectWithMinRestorationTimeFromSubtree());

        String rootChild_Child_1_1_object = "rootChild_Child_1_1_object";
        Long rootChild_Child_1_1_restorationTime = 1L;
        rootChild_Child_1.suspend(
            "rootChild_Child_1_1_object",
            rootChild_Child_1_1_object,
            rootChild_Child_1_1_restorationTime);

        String rootChild_Child_1_2_object = "rootChild_Child_1_2_object";
        Long rootChild_Child_1_2_restorationTime = 7L;
        rootChild_Child_1.suspend(
            "rootChild_Child_1_2_object",
            rootChild_Child_1_2_object,
            rootChild_Child_1_2_restorationTime);

        // ****************************** //

        SuspendedObjectTreeNode rootChild_Child_2 = rootChild.ensureChild("rootChild_Child_2");
        assertNull(rootChild_Child_2.removeObjectWithMinRestorationTimeFromSubtree());

        String rootChild_Child_2_1_object = "rootChild_Child_2_1_object";
        Long rootChild_Child_2_1_restorationTime = 2L;
        rootChild_Child_2.suspend(
            "rootChild_Child_2_1_object",
            rootChild_Child_2_1_object,
            rootChild_Child_2_1_restorationTime);

        String rootChild_Child_2_2_object = "rootChild_Child_2_2_object";
        Long rootChild_Child_2_2_restorationTime = 7L;
        rootChild_Child_2.suspend(
            "rootChild_Child_2_2_object",
            rootChild_Child_2_2_object,
            rootChild_Child_2_2_restorationTime);

        // ****************************** //

        assertThat(root.removeObjectWithMinRestorationTimeFromSubtree().suspendedObject, is(rootChild_Child_1_1_object));
        assertThat(rootChild.removeObjectWithMinRestorationTimeFromSubtree().suspendedObject, is(rootChild_Child_2_1_object));
        assertThat(root.removeObjectWithMinRestorationTimeFromSubtree().suspendedObject, is(rootChild_1_object));
        assertThat(rootChild_Child_1.removeObjectWithMinRestorationTimeFromSubtree().suspendedObject, is(rootChild_Child_1_2_object));
        assertThat(rootChild_Child_2.removeObjectWithMinRestorationTimeFromSubtree().suspendedObject, is(rootChild_Child_2_2_object));

        assertFalse(root.hasSuspendedObjects());
        assertNull(root.removeObjectWithMinRestorationTimeFromSubtree());

        assertFalse(rootChild.hasSuspendedObjects());
        assertNull(rootChild.removeObjectWithMinRestorationTimeFromSubtree());

        assertFalse(rootChild_Child_1.hasSuspendedObjects());
        assertNull(rootChild_Child_1.removeObjectWithMinRestorationTimeFromSubtree());

        assertFalse(rootChild_Child_2.hasSuspendedObjects());
        assertNull(rootChild_Child_2.removeObjectWithMinRestorationTimeFromSubtree());

        assertTrue(root.isEmpty());
    }
}
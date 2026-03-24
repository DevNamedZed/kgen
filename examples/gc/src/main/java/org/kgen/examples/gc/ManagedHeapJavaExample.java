package org.kgen.examples.gc;

import org.kgen.runtime.gc.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Java version of the managed heap GC example.
 *
 * Demonstrates allocating objects on the managed heap, registering types,
 * and collecting dead objects with finalization — all from Java.
 */
public class ManagedHeapJavaExample {

    /**
     * Allocate objects, keep one as root, collect the dead one with finalization.
     */
    public static long basicCollection() {
        var heap = new BumpHeap(4096);
        var registry = new TypeRegistry();
        var gc = new MarkSweepGC(heap, registry);

        // Define a layout: 8 bytes for a data pointer, 4 bytes for length
        var fields = List.of(
            new FieldDescriptor("data", 0, 8, false),
            new FieldDescriptor("length", 8, 4, false)
        );
        var layout = new ObjectLayout("Buffer", 16, fields, 0, 8);
        int typeId = registry.register(layout);
        var registeredLayout = registry.lookup(typeId);

        // Allocate two objects
        long liveObject = heap.allocate(registeredLayout);
        heap.writeField(liveObject, 0, 0xAAAAL);
        heap.writeField(liveObject, 1, 100L);

        long deadObject = heap.allocate(registeredLayout);
        heap.writeField(deadObject, 0, 0xBBBBL);
        heap.writeField(deadObject, 1, 200L);

        // Register a finalizer
        var finalized = new ArrayList<Long>();
        gc.registerFinalizer(typeId, finalized::add);

        // Only the first object is a root
        gc.addRootProvider(visitor -> visitor.visitRoot(liveObject));

        // Collect — dead object gets finalized
        gc.collect();

        return gc.bytesReclaimed();
    }

    /**
     * GC traces through reference fields to keep a chain alive.
     */
    public static int referenceTracing() {
        var heap = new BumpHeap(4096);
        var registry = new TypeRegistry();
        var gc = new MarkSweepGC(heap, registry);

        // Node layout: next (reference) + value (primitive)
        var nodeFields = List.of(
            new FieldDescriptor("next", 0, 8, true),
            new FieldDescriptor("value", 8, 8, false)
        );
        var nodeLayout = new ObjectLayout("Node", 16, nodeFields, 0, 8);
        int nodeTypeId = registry.register(nodeLayout);
        var registered = registry.lookup(nodeTypeId);

        // Build a chain: head → middle → tail
        long tail = heap.allocate(registered);
        heap.writeField(tail, 0, 0L); // next = null
        heap.writeField(tail, 1, 3L);

        long middle = heap.allocate(registered);
        heap.writeField(middle, 0, tail); // next = tail
        heap.writeField(middle, 1, 2L);

        long head = heap.allocate(registered);
        heap.writeField(head, 0, middle); // next = middle
        heap.writeField(head, 1, 1L);

        // Detached node — not reachable from head
        long detached = heap.allocate(registered);
        heap.writeField(detached, 0, 0L);
        heap.writeField(detached, 1, 99L);

        var finalized = new ArrayList<Long>();
        gc.registerFinalizer(nodeTypeId, finalized::add);

        // Only head is a root — GC traces through next fields
        gc.addRootProvider(visitor -> visitor.visitRoot(head));

        gc.collect();

        // Only detached should be finalized (chain is all live)
        return finalized.size();
    }
}

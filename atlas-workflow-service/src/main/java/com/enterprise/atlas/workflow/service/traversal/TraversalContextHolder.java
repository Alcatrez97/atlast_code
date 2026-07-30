package com.enterprise.atlas.workflow.service.traversal;

/**
 * Holds the per-thread {@link TraversalContext} for the duration of a single
 * workflow traversal pass.
 *
 * <p>All traversal collaborators ({@link TaskRecorder}, {@link RuntimeGraphManager},
 * {@link ResumeRouter}, …) read from this holder instead of being passed the context
 * as a parameter on every call.
 *
 * <p>The context is set by {@code GraphTraversalEngine#traverse()} at the start of
 * each pass and removed in the {@code finally} block at the end. It is also available
 * to the static {@link ResumeRouter#trySynchronousResumption} path.
 */
public final class TraversalContextHolder {

    private static final ThreadLocal<TraversalContext> HOLDER = new ThreadLocal<>();

    private TraversalContextHolder() {}

    /** Sets the traversal context for the current thread. */
    public static void set(TraversalContext ctx) {
        HOLDER.set(ctx);
    }

    /** Returns the traversal context for the current thread, or {@code null}. */
    public static TraversalContext get() {
        return HOLDER.get();
    }

    /** Clears the traversal context for the current thread. */
    public static void remove() {
        HOLDER.remove();
    }
}

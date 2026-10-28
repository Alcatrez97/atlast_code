package com.vi.atlas.workflow.service.traversal;

/**
 * Holds the per-thread {@link TraversalContext} for the duration of a single
 * workflow traversal pass.
 *
 * <p>All traversal collaborators ({@link TaskRecorder}, {@link RuntimeGraphManager},
 * {@link ResumeRouter}, â€¦) read from this holder instead of being passed the context
 * as a parameter on every call.
 *
 * <p>The context is set by {@code GraphTraversalEngine#traverse()} at the start of
 * each pass and removed in the {@code finally} block at the end. It is also available
 * to the static {@link ResumeRouter#trySynchronousResumption} path.
 */
import java.util.ArrayDeque;
import java.util.Deque;

public final class TraversalContextHolder {

    private static final InheritableThreadLocal<Deque<TraversalContext>> HOLDER =
            new InheritableThreadLocal<>() {
                @Override
                protected Deque<TraversalContext> initialValue() {
                    return new ArrayDeque<>();
                }

                @Override
                protected Deque<TraversalContext> childValue(Deque<TraversalContext> parentValue) {
                    return parentValue == null ? new ArrayDeque<>() : new ArrayDeque<>(parentValue);
                }
            };

    private TraversalContextHolder() {}

    /** Sets (pushes) the traversal context for the current thread. */
    public static void set(TraversalContext ctx) {
        if (ctx != null) {
            HOLDER.get().push(ctx);
        }
    }

    /** Returns the current traversal context for the current thread, or {@code null}. */
    public static TraversalContext get() {
        Deque<TraversalContext> stack = HOLDER.get();
        return (stack == null || stack.isEmpty()) ? null : stack.peek();
    }

    /** Clears (pops) the traversal context for the current thread. */
    public static void remove() {
        Deque<TraversalContext> stack = HOLDER.get();
        if (stack != null && !stack.isEmpty()) {
            stack.pop();
            if (stack.isEmpty()) {
                HOLDER.remove();
            }
        }
    }
}

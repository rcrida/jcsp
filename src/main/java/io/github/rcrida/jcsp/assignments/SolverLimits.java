package io.github.rcrida.jcsp.assignments;

import io.github.rcrida.jcsp.solver.Cancellation;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Caps the amount of search work performed by a solver.
 *
 * <ul>
 *   <li>{@link #nodeLimit} — maximum variable-assignment nodes to explore ({@code 0} = unlimited)</li>
 *   <li>{@link #timeLimit} — maximum wall-clock duration ({@code null} = unlimited)</li>
 * </ul>
 *
 * When either limit is exceeded {@link io.github.rcrida.jcsp.solver.BoundSolver#getSolutions()} truncates the stream silently;
 * {@link io.github.rcrida.jcsp.solver.BoundSolver#getSolution()} throws {@link io.github.rcrida.jcsp.solver.LimitExceededException}.
 *
 * <p>This class only tracks <em>whether</em> a limit was hit, not the {@link Statistics} at that
 * point — search methods now write into a caller-supplied {@link Statistics} instance directly
 * (see {@code io.github.rcrida.jcsp.solver.SolverConfig.getStatistics()}), so the caller already
 * holds a live reference to it regardless of outcome and no separate captured snapshot is needed here.
 */
@Value
public class SolverLimits {
    long nodeLimit;
    @Nullable Duration timeLimit;

    /**
     * Mutable runtime flag: set by search methods the first time a limit is detected.
     * Excluded from equals/hashCode/toString because it is ephemeral search state, not configuration.
     */
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Getter(AccessLevel.NONE)
    AtomicBoolean limitReached = new AtomicBoolean(false);

    /**
     * The absolute deadline {@link #deadlineNanos} captured for the solve in progress, or {@code
     * null} before the first call of a solve. Same ephemeral-state treatment as {@link
     * #limitReached}, and cleared by the same {@link #resetLimitReached}.
     */
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Getter(AccessLevel.NONE)
    AtomicReference<Long> deadline = new AtomicReference<>();

    private SolverLimits(long nodeLimit, @Nullable Duration timeLimit) {
        if (nodeLimit < 0) throw new IllegalArgumentException("nodeLimit must be non-negative, got: " + nodeLimit);
        this.nodeLimit = nodeLimit;
        this.timeLimit = timeLimit;
    }

    public static SolverLimits unlimited() {
        return new SolverLimits(0, null);
    }

    public static SolverLimits ofNodes(long nodeLimit) {
        return new SolverLimits(nodeLimit, null);
    }

    public static SolverLimits ofTime(Duration timeLimit) {
        return new SolverLimits(0, timeLimit);
    }

    public static SolverLimits of(long nodeLimit, Duration timeLimit) {
        return new SolverLimits(nodeLimit, timeLimit);
    }

    /**
     * The absolute deadline as a {@link System#nanoTime()} value, or {@link Long#MAX_VALUE} if
     * unlimited. Captured on the first call and the same for every later one, until {@link
     * #resetLimitReached} clears it.
     * <p>
     * {@link #timeLimit} bounds a <em>solve</em>, not each search within it, and one solve runs
     * several: an optimization solve seeds an incumbent through its own feasibility search and a
     * series of bounded probes, each of which reaches {@code DomWdegLubySearch} and asks for a
     * deadline of its own. Recomputing {@code now + timeLimit} per search re-granted the whole
     * budget to each of them, so a solve given a minute could spend nine before returning -- with
     * the caller's own deadline long expired by then, which left branch-and-bound's search filtered
     * out entirely in favour of the seed.
     */
    public long deadlineNanos() {
        if (timeLimit == null) {
            return Long.MAX_VALUE;
        }
        Long captured = deadline.get();
        if (captured != null) {
            return captured;
        }
        // Not set(): a concurrent capture (IndependentSubproblemSolver solves subproblems in
        // parallel against one instance) must leave both callers reading the same deadline.
        deadline.compareAndSet(null, System.nanoTime() + timeLimit.toNanos());
        return deadline.get();
    }

    /** True when {@code nodesExplored} has reached or exceeded the node limit (and a limit is set). */
    public boolean isNodeLimitExceeded(long nodesExplored) {
        return nodeLimit > 0 && nodesExplored >= nodeLimit;
    }

    /** True when the current time is at or past {@code deadlineNanos} (and a time limit is set). */
    public boolean isTimeLimitExceeded(long deadlineNanos) {
        // Use subtraction per nanoTime contract to handle potential long wrapping correctly.
        return deadlineNanos != Long.MAX_VALUE && System.nanoTime() - deadlineNanos >= 0;
    }

    /** Called by search methods the first time a limit is detected. */
    public void markLimitReached() {
        limitReached.set(true);
    }

    /** True after any search method has called {@link #markLimitReached}. */
    public boolean isLimitReached() {
        return limitReached.get();
    }

    /**
     * Clears the limit-hit flag and the captured {@link #deadlineNanos} so this instance can be
     * reused for a new solve. Called by {@link io.github.rcrida.jcsp.solver.BoundSolver}'s entry
     * points, which is where one solve ends and the next begins.
     */
    public void resetLimitReached() {
        limitReached.set(false);
        deadline.set(null);
    }

    /** Why {@link #checkStop} says a search should stop, or {@link #NONE} if it shouldn't. */
    public enum StopReason { NONE, CANCELLED, LIMIT_EXCEEDED }

    /**
     * Combines a {@link Cancellation} check with this instance's own node/time limit check, in the
     * order a caller should react to them: cancellation first (an active external stop signal), then
     * node/time limits ({@link #markLimitReached()} is called as a side effect exactly when {@link
     * StopReason#LIMIT_EXCEEDED} is returned, matching {@link #isNodeLimitExceeded}/{@link
     * #isTimeLimitExceeded}'s own documented contract). Shared by every terminal solver/branching
     * decorator that needs both checks at the same site ({@code DomWdegLubySearch}, {@code
     * BranchAndBoundSolver}, {@code SetBranchingSolver}) so the check and its ordering can't silently
     * drift between them.
     */
    public StopReason checkStop(@NonNull Cancellation cancellation, long nodesExplored, long deadlineNanos) {
        if (cancellation.isCancelled()) return StopReason.CANCELLED;
        if (isNodeLimitExceeded(nodesExplored) || isTimeLimitExceeded(deadlineNanos)) {
            markLimitReached();
            return StopReason.LIMIT_EXCEEDED;
        }
        return StopReason.NONE;
    }
}

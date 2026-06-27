package io.github.rcrida.jcsp.assignments;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Caps the amount of search work performed by a solver.
 *
 * <ul>
 *   <li>{@code nodeLimit} — maximum variable-assignment nodes to explore ({@code 0} = unlimited)</li>
 *   <li>{@code timeLimit} — maximum wall-clock duration ({@code null} = unlimited)</li>
 * </ul>
 *
 * When either limit is exceeded {@link BoundSolver#getSolutions()} truncates the stream silently;
 * {@link BoundSolver#getSolution()} throws {@link io.github.rcrida.jcsp.solver.LimitExceededException}.
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
    AtomicReference<Statistics> limitHitStats = new AtomicReference<>(null);

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

    /** Returns the absolute deadline as a {@link System#nanoTime()} value, or {@link Long#MAX_VALUE} if unlimited. */
    public long deadlineNanos() {
        return timeLimit == null ? Long.MAX_VALUE : System.nanoTime() + timeLimit.toNanos();
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

    /** Called by search methods the first time a limit is detected. Thread-safe via CAS. */
    public void markLimitReached(Statistics statistics) {
        limitHitStats.compareAndSet(null, statistics);
    }

    /** True after any search method has called {@link #markLimitReached}. */
    public boolean isLimitReached() {
        return limitHitStats.get() != null;
    }

    /**
     * Returns the {@link Statistics} captured when the limit was first hit.
     * Only valid to call after {@link #isLimitReached()} returns true.
     */
    public Statistics getLimitHitStatistics() {
        return limitHitStats.get();
    }

    /** Clears the limit-hit flag so this instance can be reused for a new search. */
    public void resetLimitReached() {
        limitHitStats.set(null);
    }
}

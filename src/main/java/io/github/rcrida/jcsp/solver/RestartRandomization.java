package io.github.rcrida.jcsp.solver;

import org.jspecify.annotations.Nullable;

import java.util.Random;

/**
 * Supplies a fresh, per-restart {@link Random} for {@link DomWdegLubySearch}'s Luby restarts to
 * seed {@link io.github.rcrida.jcsp.solver.backtrackingsearch.selector.DomWdegVariableSelector}'s
 * tie-breaking with, turning restart-to-restart diversification into something controlled and
 * reproducible instead of relying on an unrelated JDK implementation detail (salted {@code
 * Set.of()}/{@code Collectors.toUnmodifiableSet()} iteration order, seeded once per JVM process
 * from {@code System.nanoTime()}) to accidentally vary search behaviour launch-to-launch. See
 * {@link DomWdegLubySearch}'s own Javadoc for how the returned {@link Random} is used.
 * <p>
 * {@link #NONE} always returns {@code null}, disabling this mechanism entirely and leaving
 * tie-breaking exactly as it was before it existed (first-encountered candidate wins,
 * deterministically, with zero {@link Random} overhead). Unlike {@link
 * Cancellation#NEVER}/{@link io.github.rcrida.jcsp.solver.listener.SolverListener#NONE}, this is
 * <em>not</em> what an unconfigured solve gets by default -- {@code SolverConfig.getRestartRandomization()}
 * defaults to {@link #seeded} with a fresh random base seed instead, since restart diversification
 * is a genuine improvement over both a frozen deterministic tie-break and the accidental
 * launch-to-launch salted-collection variance it replaces (see above). For <em>reproducible</em>
 * randomized results -- e.g. a benchmark comparing runs -- pass {@link #seeded} with a fixed,
 * caller-chosen seed rather than {@link #NONE}: {@link #NONE} gives repeatable results too, but
 * only by turning diversification off altogether.
 */
@FunctionalInterface
public interface RestartRandomization {

    RestartRandomization NONE = restartIndex -> null;

    /**
     * Returns a fresh {@link Random} to seed the given restart's tie-breaking with, or {@code null}
     * to disable randomized tie-breaking for this restart (e.g. {@link #NONE} always returns {@code
     * null}). {@code restartIndex} is the same 1-indexed Luby restart counter {@link
     * DomWdegLubySearch#getSolution} already iterates.
     */
    @Nullable Random randomFor(int restartIndex);

    /**
     * Derives a distinct {@link Random} per restart from one internal driver {@link Random} seeded
     * with {@code baseSeed}, advanced once per call via {@link Random#nextLong()} rather than
     * combining {@code baseSeed} with {@code restartIndex} directly (e.g. {@code baseSeed +
     * restartIndex}), to avoid the adjacent-seed correlation a simple offset can produce.
     * <p>
     * {@link Random#nextLong()} is safe to call concurrently on one shared instance (its seed is
     * updated via CAS), so one {@link RestartRandomization} returned from this method can safely be
     * shared across {@link IndependentSubproblemSolver}'s concurrently-solved subproblems, each
     * running its own {@link DomWdegLubySearch} restart loop -- though the *order* in which
     * concurrent subproblems draw from the shared driver isn't deterministic, the same caveat that
     * already applies to those subproblems sharing one {@link Cancellation}/{@link
     * io.github.rcrida.jcsp.assignments.Statistics}.
     */
    static RestartRandomization seeded(long baseSeed) {
        Random driver = new Random(baseSeed);
        return restartIndex -> new Random(driver.nextLong());
    }
}

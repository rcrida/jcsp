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
     * Returns the randomization for the {@code searchIndex}-th search of one solve, so that searches
     * run in sequence do not draw from each other's streams.
     * <p>
     * A solve is not always one search. Every optimization solve runs several — one for the first
     * solution and one per bounded probe ({@link BoundedFirstSolution}, ADR-0041, ADR-0044) — and
     * each is a fresh {@link DomWdegLubySearch} whose restart counter starts again at 1. Without a
     * search identity, {@link #seeded}'s stream position at the moment a search starts is what
     * decides its tie-breaking, so the same search is a different search depending on what ran
     * before it. That is measurable: one instance solved three times from a single {@code seeded}
     * driver took 8,770, then 20,873, then over 56,000 nodes.
     * <p>
     * The default returns {@code this}, which is the behaviour every implementation had before this
     * method existed — a caller's own lambda keeps working and keeps sharing one stream across
     * searches. {@link #NONE} inherits it and stays inert.
     *
     * @param searchIndex 0 for a solve's first (or only) search, then 1 upwards per later search
     */
    default RestartRandomization forSearch(int searchIndex) {
        return this;
    }

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
     * <p>
     * <b>{@code restartIndex} is deliberately ignored, and the consequence is sharper than it looks.</b>
     * Because the driver is stateful, what a caller gets back depends on how many times this has
     * already been called, not on which restart is asking. A solve that runs several searches in
     * sequence -- which every optimization solve now does, one for the first solution and one per
     * bounded probe (ADR-0041, ADR-0044) -- therefore hands each later search a different tie-break
     * stream than it would have got running first. On a problem whose difficulty is sensitive to that
     * stream this dominates: solving one fixed instance three times from a single {@code seeded}
     * driver took 8,770, 20,873 and then more than 56,000 nodes. A whole solve stays reproducible,
     * since the total sequence of draws is fixed, but two searches within it are not comparable to
     * each other or to the same search run alone. Making this a pure function of {@code (baseSeed,
     * restartIndex)} via a mixer would keep the anti-correlation property above without that effect.
     */
    static RestartRandomization seeded(long baseSeed) {
        Random driver = new Random(baseSeed);
        return new RestartRandomization() {
            @Override
            public Random randomFor(int restartIndex) {
                return new Random(driver.nextLong());
            }

            /**
             * Search 0 keeps this instance, so a solve that runs one search -- the whole satisfaction
             * chain, and branch-and-bound's own first-solution search -- is unchanged. Each later
             * search gets its <em>own</em> driver, seeded from {@code (baseSeed, searchIndex)}, so it
             * draws the same stream wherever it runs in the sequence while still differing from its
             * siblings. Deriving a whole driver rather than one seed per restart is deliberate: the
             * statefulness inside a search is what varies its restarts, and replacing it with a pure
             * per-restart function was measured as a regression (ADR-0015).
             */
            @Override
            public RestartRandomization forSearch(int searchIndex) {
                return searchIndex == 0 ? this : seeded(mix(baseSeed, searchIndex));
            }
        };
    }

    /**
     * SplitMix64's gamma step followed by its finalizer (Steele, Lea &amp; Flood 2014), the standard
     * way to turn a counter into a well-distributed seed. The multiplier is odd, so stepping by it
     * visits every {@code long} before repeating, and the shift/multiply finalizer avalanches each
     * step — which is what makes consecutive searches unrelated rather than merely distinct.
     */
    private static long mix(long baseSeed, int searchIndex) {
        long z = baseSeed + searchIndex * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}

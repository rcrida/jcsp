package io.github.rcrida.jcsp.benchmark;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.NogoodStore;
import io.github.rcrida.jcsp.assignments.SolverLimits;
import io.github.rcrida.jcsp.assignments.Statistics;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.BooleanDomain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.DomWdegLubySearch;
import io.github.rcrida.jcsp.solver.LimitExceededException;
import io.github.rcrida.jcsp.solver.NodeConsistentSolver;
import io.github.rcrida.jcsp.solver.PropagationFixpointSolver;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.solver.backtrackingsearch.order.LeastConstrainingValueOrderer;
import io.github.rcrida.jcsp.variables.Variable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Standalone (non-JUnit, not run by surefire/jacoco) harness measuring how much of a hard search's
 * wall-clock cost is attributable to {@code NogoodStore}. Originally built to isolate {@code
 * NogoodConstraint} propagation cost specifically, but that turned out to be a dead end: two
 * separate propagation-count optimizations (dirty-variable-index round-2+ filtering, then
 * cross-node round-1 seeding) each demonstrably reduced how many nogoods got {@code .propagate()}-
 * checked, yet neither moved this benchmark's numbers at all. The actual cost was one layer
 * earlier: {@code NogoodStore.apply()} did {@code Set.copyOf(nogoods)} and {@code
 * ConstraintSatisfactionProblem}'s constructor rebuilt the whole structural-constraints-plus-
 * nogoods {@code HashSet} from scratch, unconditionally, on every single node, regardless of how
 * many of those nogoods anyone actually checked afterward. Caching both steps (see {@code
 * NogoodStore#snapshot} and {@code ConstraintSatisfactionProblem}'s {@code nogoodMergeCache}) is
 * what actually closed the gap -- see {@code project_jcsp_nogood_dirty_index_result} in project
 * memory for the full trail.
 *
 * <p>All three variants explore the exact same fixed node budget ({@link #NODE_LIMIT}) on the exact
 * same hard CSP, wired identically except for one thing:
 * <ul>
 *   <li>{@code default} uses {@link NogoodStore#forProblem} (production sizing) with nogood
 *       learning enabled -- the production configuration.</li>
 *   <li>{@code capped} uses a capacity-1 {@link NogoodStore}, which still learns but immediately
 *       evicts, so it approximates "no accumulated nogood-store cost" -- but {@code
 *       Solver.Factory#FULL_PROPAGATION_INFERENCE}'s {@code applyWithReason} still derives a
 *       reason on every domain wipeout before it's immediately discarded. This isolates the
 *       copy/merge cost specifically (see the trail above), not the cost of nogood <em>learning</em>
 *       in general.</li>
 *   <li>{@code disabled} sets {@code nogoodLearningEnabled(false)} instead, so only {@code
 *       Inference#apply} is ever called, never {@code applyWithReason} -- {@code NogoodStore.record}
 *       is then never called at all, so this is CDCL fully switched off: no explanation
 *       computation, no accumulation, no copy/merge. Comparing {@code capped} against {@code
 *       disabled} isolates the reason-derivation cost itself, which {@code capped} alone can't
 *       separate out.</li>
 * </ul>
 * A wall-clock gap between {@code default} and {@code capped} is attributable to nogood-store
 * copy/merge overhead; a gap between {@code capped} and {@code disabled} is attributable to the
 * explanation computation itself -- neither is about different search decisions, since dom/wdeg
 * weighting and value ordering are unaffected by either knob.
 *
 * <p>Covers four different shapes deliberately: {@link #golombRuler} is dominated by one large
 * {@code AllDiffConstraint} whose GAC pruning touches many variables per round (broad propagation),
 * and is UNSAT (a full search-space proof); {@link #randomBinaryCsp} is built entirely from
 * pairwise {@code biPredicateConstraint}s, so only {@code AC3} (pure per-arc revision) and nogoods
 * ever propagate (narrow propagation), and is also UNSAT; {@link #quasigroupCompletion} is a random
 * Latin-square completion (QCP) instance near the classic Gomes/Selman phase-transition region, and
 * is SAT -- the search finds a solution rather than proving none exists, exercising nogood learning
 * as a pruning aid across restarts rather than as an UNSAT-proof accelerant. Unlike the SAT/binary-CSP
 * encodings QCP hardness is usually studied with, this library's {@code allDiffConstraint} is full
 * Regin GAC, which turned out strong enough that small/medium orders (n&lt;=16) solve in
 * milliseconds regardless of hole fraction -- order 20 at a 50% hole fraction was the smallest
 * instance found (via a throwaway tuning sweep, not committed) that lands in the same few-seconds
 * range as the other two scenarios. {@link #pigeonhole} is the classic boolean-encoded pigeonhole
 * principle (n pigeons, n-1 holes via {@code exactlyOneConstraint}/{@code atMostOneConstraint}, not
 * {@code allDiffConstraint} -- the latter would let Regin's GAC catch the Hall violation in one
 * propagation step with zero search, making it useless for stressing nogood learning). UNSAT, and
 * famously hard: Haken (1985) proved the shortest resolution refutation of PHP(n) grows
 * exponentially in n, which transfers directly to any CDCL-style solver's worst case since CDCL
 * proofs are themselves resolution proofs. Confirmed empirically via a throwaway tuning sweep (not
 * committed): n=5/6/7 solved in 115/554/7492ms, but n=8 and n=9 both blew the 300k-node budget
 * (62506ms and 91200ms respectively, neither finishing) -- growth far steeper than any other
 * scenario here. n=7 (6 holes) is used as the full-proof instance.
 *
 * <p><b>{@code pigeonhole}'s three variants originally came out statistically identical</b> (~8.3s
 * each, identical backtrack counts at forced truncation) -- nogood learning had literally zero
 * effect. Root cause: {@code DomWdegLubySearch} only calls {@code selector.incrementWeights}/{@code
 * conflictExplainer.explain} inside the branch where {@code inference.apply} (propagation) returns
 * empty, and neither {@code ExactlyOneConstraint} nor {@code AtMostOneConstraint} was registered as
 * a {@link io.github.rcrida.jcsp.consistency.Propagatable} in {@code
 * PropagationFixpointSolver.PROPAGATORS} -- only their pairwise-NAND {@code BinaryDecomposable}
 * decomposition fed {@code AC3}, which can force individual variables but can never detect "zero
 * holes assigned true" as a domain wipeout (an inherently global/counting condition). So every
 * pigeonhole failure was caught by {@code Assignment#isConsistent}'s direct {@code isSatisfiedBy}
 * check instead, silently bypassing both learning mechanisms regardless of configuration.
 *
 * <p>Two attempts to fix this by changing {@code DomWdegLubySearch}'s failure-handling (calling the
 * same weight/nogood-learning path from the {@code isConsistent} branch too) were both empirically
 * reverted: unconditional weight+nogood learning regressed {@code CryptarithmeticTest} ~30x in
 * wall-clock from nogood-store bloat (low-value full-assignment fallback nogoods costing real
 * ongoing checks); weight-only still regressed it ~4x <em>and</em> made pigeonhole itself worse.
 * <b>The fix that actually worked (2026-07-17)</b> was architecturally different: giving {@code
 * AtMostOneConstraint} (and, via inheritance, {@code ExactlyOneConstraint}) real {@code Propagatable}
 * implementations -- definite/possible counting exactly like {@code AtLeastNConstraint}/{@code
 * AtMostNConstraint} already do, with {@code ExactlyOneConstraint} additionally forcing the sole
 * remaining open variable {@code true} once every other candidate is excluded, propagation the
 * pairwise-NAND decomposition alone could never provide. Violations now surface as genuine domain
 * wipeouts through {@code inference.apply}, so {@code DomWdegLubySearch} needed no changes at all --
 * dom/wdeg weighting and nogood learning both engage naturally. Result: pigeonhole's full UNSAT
 * proof dropped from ~8.3s (all three variants identical) to 2.45-3.19s, with the variants now
 * properly diverging ({@code default} 3185ms &gt; {@code capped} 3071ms &gt; {@code disabled}
 * 2452ms) exactly as the other UNSAT scenarios do. See {@code project_jcsp_isconsistent_learning_gap}
 * in project memory for the full trail, including why the two reverted attempts made things worse.
 *
 * <p>Run via {@code mvn test-compile} then
 * {@code java -cp target/classes:target/test-classes:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout) io.github.rcrida.jcsp.benchmark.NogoodPropagationBenchmark}.
 */
public final class NogoodPropagationBenchmark {

    private static final long NODE_LIMIT = 300_000L;
    private static final long FORCED_NODE_LIMIT = 500L;
    private static final int TRIALS = 3;

    private NogoodPropagationBenchmark() {
    }

    public static void main(String[] args) {
        record Scenario(String name, ConstraintSatisfactionProblem csp) {
        }
        List<Scenario> scenarios = List.of(
                new Scenario("Golomb ruler order=6 length=16 (UNSAT, one below optimal 17)", golombRuler(6, 16)),
                new Scenario("Golomb ruler order=7 length=24 (UNSAT, one below optimal 25)", golombRuler(7, 24)),
                new Scenario("Random binary CSP n=26 d=6 t=0.13 seed=42 (UNSAT, narrow AC3-only propagation)",
                        randomBinaryCsp(26, 6, 0.13, 42L)),
                new Scenario("Quasigroup completion n=20 holes=0.5 seed=42 (SAT, phase-transition region)",
                        quasigroupCompletion(20, 0.5, 42L)),
                new Scenario("Pigeonhole n=7 pigeons, 6 holes (UNSAT, boolean encoding, resolution-hard)",
                        pigeonhole(7))
        );

        for (Scenario scenario : scenarios) {
            System.out.println();
            System.out.println("=== " + scenario.name() + " ===");
            runAllVariants(scenario.csp(), NODE_LIMIT);
        }

        // Forced-truncation comparisons: a node budget small enough that all variants are
        // guaranteed to hit it (rather than complete the proof), so nodesExplored -- and therefore
        // a nodes/sec regression metric -- is directly comparable rather than "n/a (finished under
        // limit)".
        ConstraintSatisfactionProblem order7 = golombRuler(7, 24);
        System.out.println();
        System.out.println("=== Golomb ruler order=7 length=24, forced truncation at " + FORCED_NODE_LIMIT + " nodes ===");
        runAllVariants(order7, FORCED_NODE_LIMIT);

        ConstraintSatisfactionProblem randomBinary = randomBinaryCsp(26, 6, 0.13, 42L);
        System.out.println();
        System.out.println("=== Random binary CSP n=26 d=6 t=0.13 seed=42, forced truncation at " + FORCED_NODE_LIMIT + " nodes ===");
        runAllVariants(randomBinary, FORCED_NODE_LIMIT);

        ConstraintSatisfactionProblem pigeonhole7 = pigeonhole(7);
        System.out.println();
        System.out.println("=== Pigeonhole n=7 pigeons, 6 holes, forced truncation at " + FORCED_NODE_LIMIT + " nodes ===");
        runAllVariants(pigeonhole7, FORCED_NODE_LIMIT);
    }

    private static void runAllVariants(ConstraintSatisfactionProblem csp, long nodeLimit) {
        run("default (NogoodStore.forProblem)", csp, nodeLimit,
                () -> NogoodStore.forProblem(csp), true);
        run("capped (NogoodStore capacity=1)", csp, nodeLimit,
                () -> new NogoodStore(1), true);
        run("disabled (nogoodLearningEnabled=false)", csp, nodeLimit,
                () -> NogoodStore.forProblem(csp), false);
    }

    private static void run(String label, ConstraintSatisfactionProblem csp, long nodeLimit,
                            java.util.function.Supplier<NogoodStore> storeFactory, boolean nogoodLearningEnabled) {
        List<Long> millis = new ArrayList<>();
        List<Statistics> stats = new ArrayList<>();
        String outcome = "?";
        for (int trial = 0; trial < TRIALS; trial++) {
            SolverLimits limits = SolverLimits.ofNodes(nodeLimit);
            // Statistics is now a shared token seeded into DomWdegLubySearch (see buildChain below)
            // rather than something only reachable via a returned Assignment -- so it's readable
            // here regardless of how the search below terminates: SAT, genuine UNSAT, or a limit hit.
            Statistics statistics = new Statistics();
            Solver chain = buildChain(storeFactory.get(), limits, nogoodLearningEnabled, statistics);
            long start = System.nanoTime();
            try {
                var result = chain.getSolution(csp);
                millis.add((System.nanoTime() - start) / 1_000_000);
                outcome = limits.isLimitReached() ? "LIMIT" : result.isPresent() ? "SAT" : "UNSAT";
                stats.add(statistics);
            } catch (LimitExceededException e) {
                stats.add(statistics);
                millis.add((System.nanoTime() - start) / 1_000_000);
                outcome = "LIMIT";
            }
        }
        double avgMillis = millis.stream().mapToLong(Long::longValue).average().orElse(0);
        Statistics first = stats.isEmpty() ? null : stats.get(0);
        String nodes = first == null ? "n/a (" + outcome + ")" : String.valueOf(first.getNodesExplored().get());
        String nodesPerSec = (first == null || avgMillis == 0) ? "n/a"
                : String.format("%.0f", first.getNodesExplored().get() / (avgMillis / 1000));
        String backtracks = first == null ? "n/a" : String.valueOf(first.getBacktracks().get());
        String nogoodsLearned = first == null ? "n/a" : String.valueOf(first.getNogoodsLearned().get());
        System.out.printf("%-35s avg=%.0fms trials=%s nodesExplored=%-8s nodes/sec=%-6s backtracks=%-6s nogoodsLearned=%s%n",
                label, avgMillis, millis, nodes, nodesPerSec, backtracks, nogoodsLearned);
    }

    /** Mirrors {@code Solver.Factory}'s satisfaction chain, minus the decomposition decorators
     * (not needed here: these Golomb ruler instances are a single dense connected component with
     * treewidth above the tree-decomposition threshold, so those decorators would be pure passthrough). */
    private static Solver buildChain(NogoodStore nogoodStore, SolverLimits limits, boolean nogoodLearningEnabled,
                                     Statistics statistics) {
        io.github.rcrida.jcsp.consistency.Inference inference = nogoodLearningEnabled
                ? Solver.Factory.FULL_PROPAGATION_INFERENCE
                : io.github.rcrida.jcsp.consistency.Inference.withoutReasonTracking(Solver.Factory.FULL_PROPAGATION_INFERENCE);
        DomWdegLubySearch domWdegLubySearch = DomWdegLubySearch.builder()
                .domainValuesOrderer(LeastConstrainingValueOrderer.INSTANCE)
                .inference(inference)
                .limits(limits)
                .nogoodStore(nogoodStore)
                .statistics(statistics)
                .maxRestarts(Integer.MAX_VALUE)
                .build();
        Solver propagationFixpointSolver = PropagationFixpointSolver.builder()
                .inner(domWdegLubySearch)
                .snap(true)
                .build();
        return NodeConsistentSolver.builder().inner(propagationFixpointSolver).build();
    }

    /** Same construction as {@code GolombRulerTest}, parameterized so different orders can be compared. */
    private static ConstraintSatisfactionProblem golombRuler(int n, int maxLength) {
        Variable.Factory f = Variable.Factory.INSTANCE;
        List<Variable<Integer>> marks = new ArrayList<>();
        for (int i = 0; i < n; i++) marks.add(f.create("m" + i));

        var builder = ConstraintSatisfactionProblem.builder();
        marks.forEach(m -> builder.variableDomain(m, IntRangeDomain.of(0, maxLength)));
        builder.equalsConstraint(marks.get(0), 0);
        for (int i = 0; i < n - 1; i++) {
            builder.comparatorConstraint(marks.get(i), Operator.LT, marks.get(i + 1));
        }

        List<Variable<Integer>> diffs = new ArrayList<>();
        Variable<Integer> firstGap = null;
        Variable<Integer> lastGap = null;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                Variable<Integer> d = f.create("d" + i + j);
                builder.variableDomain(d, IntRangeDomain.of(1, maxLength));
                builder.linearConstraint(Map.of(marks.get(j), 1, marks.get(i), -1, d, -1), Operator.EQ, 0);
                diffs.add(d);
                if (i == 0 && j == 1) firstGap = d;
                if (i == n - 2 && j == n - 1) lastGap = d;
            }
        }
        builder.allDiffConstraint(Set.copyOf(diffs));
        builder.comparatorConstraint(firstGap, Operator.LT, lastGap);
        return builder.build();
    }

    /**
     * Random binary CSP (Model B): {@code n} variables of domain size {@code domainSize}, a
     * constraint on every pair (full density -- keeps it one connected component, same reasoning
     * as {@link #golombRuler}), each with a fixed, seeded-random compatibility matrix of the given
     * {@code tightness} (fraction of value pairs forbidden). Deliberately built from only
     * {@code biPredicateConstraint} -- no {@code AllDiffConstraint}/{@code LinearConstraint}/etc
     * -- so the only propagators active are {@code AC3} (pure per-arc, per-value revision) and
     * {@code NogoodFixpointConsistency}, unlike {@link #golombRuler}'s single large
     * {@code AllDiffConstraint} whose GAC pruning touches many variables per round. Propagation
     * here narrows one variable's domain against one neighbour at a time, so the dirty-variable
     * set after a round should stay much smaller relative to the whole variable set -- the shape
     * the dirty-index optimization was actually designed for.
     */
    private static ConstraintSatisfactionProblem randomBinaryCsp(int n, int domainSize, double tightness, long seed) {
        Variable.Factory f = Variable.Factory.INSTANCE;
        List<Variable<Integer>> vars = new ArrayList<>();
        for (int i = 0; i < n; i++) vars.add(f.create("rv" + i));

        var builder = ConstraintSatisfactionProblem.builder();
        vars.forEach(v -> builder.variableDomain(v, IntRangeDomain.of(0, domainSize - 1)));

        Random rnd = new Random(seed);
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                boolean[][] compatible = new boolean[domainSize][domainSize];
                for (int a = 0; a < domainSize; a++) {
                    for (int b = 0; b < domainSize; b++) {
                        compatible[a][b] = rnd.nextDouble() >= tightness;
                    }
                }
                builder.biPredicateConstraint(vars.get(i), vars.get(j), (x, y) -> compatible[x][y]);
            }
        }
        return builder.build();
    }

    /**
     * Quasigroup completion problem (QCP): generates a random order-{@code n} Latin square, clears a
     * {@code holesFraction} of its cells (seeded), then poses completing the rest as a CSP -- one
     * variable per cell, domain {@code [0, n-1]}, one {@code allDiffConstraint} per row and per
     * column, plus an {@code equalsConstraint} fixing every still-filled (non-hole) cell. Always
     * SAT (the original square is always a valid completion), unlike {@link #golombRuler} and
     * {@link #randomBinaryCsp}, which are both UNSAT.
     */
    private static ConstraintSatisfactionProblem quasigroupCompletion(int n, double holesFraction, long seed) {
        int[][] latinSquare = randomLatinSquare(n, new Random(seed));
        Random holeRnd = new Random(seed + 1);
        boolean[][] hole = new boolean[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                hole[i][j] = holeRnd.nextDouble() < holesFraction;
            }
        }

        Variable.Factory f = Variable.Factory.INSTANCE;
        @SuppressWarnings("unchecked")
        Variable<Integer>[][] cells = new Variable[n][n];
        var builder = ConstraintSatisfactionProblem.builder();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                cells[i][j] = f.create("qcp" + i + "-" + j);
                builder.variableDomain(cells[i][j], IntRangeDomain.of(0, n - 1));
            }
        }
        for (int i = 0; i < n; i++) {
            Set<Variable<Integer>> row = new HashSet<>();
            for (int j = 0; j < n; j++) row.add(cells[i][j]);
            builder.allDiffConstraint(row);
        }
        for (int j = 0; j < n; j++) {
            Set<Variable<Integer>> column = new HashSet<>();
            for (int i = 0; i < n; i++) column.add(cells[i][j]);
            builder.allDiffConstraint(column);
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (!hole[i][j]) {
                    builder.equalsConstraint(cells[i][j], latinSquare[i][j]);
                }
            }
        }
        return builder.build();
    }

    /**
     * Builds a random Latin square by permuting the rows, columns, and symbols of the canonical
     * cyclic square {@code (i + j) mod n} -- permuting any of the three always yields another valid
     * Latin square, so this is guaranteed correct by construction without needing to search for one.
     */
    private static int[][] randomLatinSquare(int n, Random rnd) {
        int[][] square = new int[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                square[i][j] = (i + j) % n;
            }
        }
        shuffleRows(square, rnd);
        shuffleColumns(square, rnd);
        int[] symbolPermutation = randomPermutation(n, rnd);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                square[i][j] = symbolPermutation[square[i][j]];
            }
        }
        return square;
    }

    private static void shuffleRows(int[][] square, Random rnd) {
        for (int i = square.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int[] tmp = square[i];
            square[i] = square[j];
            square[j] = tmp;
        }
    }

    private static void shuffleColumns(int[][] square, Random rnd) {
        for (int j = square.length - 1; j > 0; j--) {
            int k = rnd.nextInt(j + 1);
            for (int[] row : square) {
                int tmp = row[j];
                row[j] = row[k];
                row[k] = tmp;
            }
        }
    }

    private static int[] randomPermutation(int n, Random rnd) {
        int[] permutation = new int[n];
        for (int i = 0; i < n; i++) permutation[i] = i;
        for (int i = n - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int tmp = permutation[i];
            permutation[i] = permutation[j];
            permutation[j] = tmp;
        }
        return permutation;
    }

    /**
     * Boolean-encoded pigeonhole principle: {@code n} pigeons into {@code n-1} holes, UNSAT.
     * Deliberately avoids {@code allDiffConstraint} -- Regin's GAC would catch the Hall violation
     * in one propagation step with zero search, defeating the point of a nogood-learning
     * stress-test. Instead uses one boolean variable per (pigeon, hole) pair: {@code
     * exactlyOneConstraint} forces each pigeon into exactly one hole, {@code atMostOneConstraint}
     * forbids two pigeons sharing a hole. Famously hard for resolution-based reasoning (Haken 1985)
     * -- see the class javadoc for measured growth.
     */
    private static ConstraintSatisfactionProblem pigeonhole(int n) {
        int holes = n - 1;
        Variable.Factory f = Variable.Factory.INSTANCE;
        @SuppressWarnings("unchecked")
        Variable<Boolean>[][] inHole = new Variable[n][holes];
        var builder = ConstraintSatisfactionProblem.builder();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < holes; j++) {
                inHole[i][j] = f.create("ph" + i + "-" + j);
                builder.variableDomain(inHole[i][j], BooleanDomain.INSTANCE);
            }
        }
        for (int i = 0; i < n; i++) {
            Set<Variable<Boolean>> pigeon = new HashSet<>();
            for (int j = 0; j < holes; j++) pigeon.add(inHole[i][j]);
            builder.exactlyOneConstraint(pigeon);
        }
        for (int j = 0; j < holes; j++) {
            Set<Variable<Boolean>> hole = new HashSet<>();
            for (int i = 0; i < n; i++) hole.add(inHole[i][j]);
            builder.atMostOneConstraint(hole);
        }
        return builder.build();
    }
}

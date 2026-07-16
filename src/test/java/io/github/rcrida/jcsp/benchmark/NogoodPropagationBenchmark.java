package io.github.rcrida.jcsp.benchmark;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.NogoodStore;
import io.github.rcrida.jcsp.assignments.SolverLimits;
import io.github.rcrida.jcsp.assignments.Statistics;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.ConflictExplainer;
import io.github.rcrida.jcsp.solver.DomWdegLubySearch;
import io.github.rcrida.jcsp.solver.LimitExceededException;
import io.github.rcrida.jcsp.solver.MacAndFixpointConflictExplainer;
import io.github.rcrida.jcsp.solver.NodeConsistentSolver;
import io.github.rcrida.jcsp.solver.NullConflictExplainer;
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
 *   <li>{@code default} uses {@link NogoodStore#forProblem} (production sizing) with {@link
 *       MacAndFixpointConflictExplainer} -- the production configuration.</li>
 *   <li>{@code capped} uses a capacity-1 {@link NogoodStore}, which still learns but immediately
 *       evicts, so it approximates "no accumulated nogood-store cost" -- but it still calls {@link
 *       MacAndFixpointConflictExplainer} on every domain wipeout, re-running MAC and the
 *       propagation fixpoint with reason tracking, before immediately discarding the result. This
 *       isolates the copy/merge cost specifically (see the trail above), not the cost of nogood
 *       <em>learning</em> in general.</li>
 *   <li>{@code disabled} uses {@link NullConflictExplainer} instead, which returns {@code
 *       Optional.empty()} unconditionally -- {@code NogoodStore.record} is then never called at
 *       all, so this is CDCL fully switched off: no explanation computation, no accumulation, no
 *       copy/merge. Comparing {@code capped} against {@code disabled} isolates {@code
 *       MacAndFixpointConflictExplainer}'s own per-wipeout cost, which {@code capped} alone can't
 *       separate out (a distinction first raised, then resolved by adding {@code
 *       NullConflictExplainer} to the library, in the same investigation this benchmark
 *       documents).</li>
 * </ul>
 * A wall-clock gap between {@code default} and {@code capped} is attributable to nogood-store
 * copy/merge overhead; a gap between {@code capped} and {@code disabled} is attributable to the
 * explanation computation itself -- neither is about different search decisions, since dom/wdeg
 * weighting and value ordering are unaffected by either knob.
 *
 * <p>Covers three different shapes deliberately: {@link #golombRuler} is dominated by one large
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
 * range as the other two scenarios.
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
                        quasigroupCompletion(20, 0.5, 42L))
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
    }

    private static void runAllVariants(ConstraintSatisfactionProblem csp, long nodeLimit) {
        run("default (NogoodStore.forProblem)", csp, nodeLimit,
                () -> NogoodStore.forProblem(csp), MacAndFixpointConflictExplainer.INSTANCE);
        run("capped (NogoodStore capacity=1)", csp, nodeLimit,
                () -> new NogoodStore(1), MacAndFixpointConflictExplainer.INSTANCE);
        run("disabled (NullConflictExplainer)", csp, nodeLimit,
                () -> NogoodStore.forProblem(csp), NullConflictExplainer.INSTANCE);
    }

    private static void run(String label, ConstraintSatisfactionProblem csp, long nodeLimit,
                            java.util.function.Supplier<NogoodStore> storeFactory, ConflictExplainer conflictExplainer) {
        List<Long> millis = new ArrayList<>();
        List<Statistics> stats = new ArrayList<>();
        String outcome = "?";
        for (int trial = 0; trial < TRIALS; trial++) {
            SolverLimits limits = SolverLimits.ofNodes(nodeLimit);
            Solver chain = buildChain(storeFactory.get(), limits, conflictExplainer);
            long start = System.nanoTime();
            try {
                var result = chain.getSolution(csp);
                millis.add((System.nanoTime() - start) / 1_000_000);
                if (limits.isLimitReached()) {
                    stats.add(limits.getLimitHitStatistics());
                    outcome = "LIMIT";
                } else if (result.isPresent()) {
                    // SAT: the returned Assignment's Statistics reflects the node count of whichever
                    // Luby restart succeeded, not a cumulative count across earlier failed restarts
                    // (see DomWdegLubySearch#getSolution) -- fine here since these scenarios succeed
                    // on the first restart (getStatistics().getRestarts() == 0).
                    stats.add(result.get().getStatistics());
                    outcome = "SAT";
                } else {
                    // Genuinely UNSAT: DomWdegLubySearch#getSolution returns Optional.empty() with no
                    // Statistics attached anywhere reachable from this API -- a real gap, not something
                    // this benchmark can work around without a production-code change.
                    stats.add(null);
                    outcome = "UNSAT (no stats exposed by getSolution() on this path)";
                }
            } catch (LimitExceededException e) {
                stats.add(e.getStatistics());
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
    private static Solver buildChain(NogoodStore nogoodStore, SolverLimits limits, ConflictExplainer conflictExplainer) {
        DomWdegLubySearch domWdegLubySearch = DomWdegLubySearch.builder()
                .domainValuesOrderer(LeastConstrainingValueOrderer.INSTANCE)
                .inference(Solver.Factory.FULL_PROPAGATION_INFERENCE)
                .limits(limits)
                .nogoodStore(nogoodStore)
                .conflictExplainer(conflictExplainer)
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
}

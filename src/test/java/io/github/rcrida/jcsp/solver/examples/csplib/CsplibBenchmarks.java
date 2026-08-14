package io.github.rcrida.jcsp.solver.examples.csplib;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.SolverLimits;
import io.github.rcrida.jcsp.parser.xcsp3.Xcsp3Parser;
import io.github.rcrida.jcsp.solver.LimitExceededException;
import io.github.rcrida.jcsp.solver.RestartRandomization;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.solver.SolverConfig;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * JMH benchmark suite covering the full solver chain end-to-end across the CSPLib problems in
 * this package, complementing {@link io.github.rcrida.jcsp.benchmark.NogoodPropagationBenchmark}'s
 * narrower focus on CDCL/nogood-store overhead specifically. Every CSP here is built by directly
 * reusing each {@code ProbNNN} test class's own builder method/static field (and, for optimization
 * problems, its objective method reference) rather than duplicating construction logic — see each
 * builder's own javadoc for how the fixed-instance and parameterized forms relate.
 *
 * <p>Every {@code @Benchmark} method passes {@link #deterministicConfig()} rather than a bare
 * {@code createSolver(csp)}/{@code createSolver(csp, objective)} call: {@code SolverConfig}
 * defaults to {@link RestartRandomization#seeded} with a fresh random base seed per construction
 * (see {@link io.github.rcrida.jcsp.solver.DomWdegLubySearch}'s own Javadoc), which would otherwise
 * make each JMH iteration search a genuinely different workload (different node counts, not just
 * timing noise around the same one) — exactly the kind of variance this benchmark's own
 * warmup/measurement iterations exist to filter *out* of a before/after comparison, not reintroduce
 * as a second, uncontrolled source alongside it. {@link #deterministicConfig()} pins a fixed literal
 * seed ({@link #RESTART_RANDOMIZATION_SEED}) via {@link RestartRandomization#seeded} rather than
 * {@link RestartRandomization#NONE} — same code plus the same seed always reproduces the same
 * restart-tie-break sequence, exercising the real production default path (seeded randomization),
 * not a special-cased "feature disabled" path most real callers won't run. A fixed seed does
 * <em>not</em> by itself make a before/after comparison across two separate JMH invocations (two
 * different {@code java} process launches, e.g. one per code version being compared) fully
 * reproducible -- {@code AC3}'s own arc-processing order is independently salted once per JVM
 * process (see {@link io.github.rcrida.jcsp.solver.RestartRandomization}'s own Javadoc) and
 * untouched by this pin -- but it does eliminate {@code RestartRandomization} specifically as a
 * source of noise both within one run and across separate runs of this class.
 *
 * <p>Not run by surefire (this class name doesn't match {@code *Test.java}, the same mechanism
 * that already excludes {@code NogoodPropagationBenchmark}). Run via:
 * <pre>
 * mvn test-compile
 * java -cp target/classes:target/test-classes:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout) org.openjdk.jmh.Main CsplibBenchmarks
 * </pre>
 *
 * <p>Every fixed real-world instance (Killer Sudoku, Car Sequencing, Warehouse Location, Steel
 * Mill Slab Design, Job Shop Scheduling, Product Matrix TSP, Knapsack) is benchmarked unscaled, at
 * its CSPLib-transcribed size — JMH's warmup/measurement iterations make sub-10ms operations just
 * as measurable as slower ones, so there's no need to inflate these with fabricated instance data.
 * Five of the six naturally-parametric problem families (Golomb Ruler, Social Golfers, Magic
 * Square, Number Partitioning, N-Queens) are additionally benchmarked at a larger, hand-picked size
 * (see the {@code SCALED_*} constants below) to actually stress search/CDCL rather than resolve
 * entirely during propagation; those scaled sizes are a reasonable starting point for stress
 * purposes, not claimed as official CSPLib instances, and may need re-tuning once actually measured
 * (the same "throwaway tuning sweep" practice documented in
 * {@link io.github.rcrida.jcsp.benchmark.NogoodPropagationBenchmark}'s own javadoc).
 *
 * <p>Steiner Triple System has no scaled variant: valid orders are ≡ 1 or 3 (mod 6), so the only
 * step up from the test's order 7 is order 9 — and an empirical check found order 9 still hadn't
 * found a first solution after 280+ seconds (this model's pairwise {@code
 * intersectionCardinalityConstraint}s plus lexicographic symmetry breaking apparently don't scale
 * from 7 to 9 triples the way the other five families' scaled sizes do), so it's excluded here
 * rather than risking a benchmark run that hangs for minutes on one method.
 *
 * <p>{@link #magicSquareXcsp3LargeNodeBudget} is a deliberately different shape from every other
 * method here: sourced from the real, unmodified {@code MagicSquare-6-sum.xml.lzma} competition
 * instance bundled at {@code src/test/resources/xcsp3/competition/} (the same corpus {@link
 * io.github.rcrida.jcsp.parser.xcsp3.Xcsp3CompetitionRunner} drives) rather than a {@code ProbNNN}
 * test class's own builder, and bounded by {@link SolverLimits#ofNodes} rather than solved to
 * completion, since this specific instance doesn't finish within any benchmark-reasonable time (a
 * 60-second budget still reports {@code UNKNOWN} — see {@code Xcsp3CompetitionRunner}'s own bundled
 * run). A node budget rather than a time budget keeps the amount of search work measured constant
 * across JMH iterations regardless of a run's own speed, which is the property a benchmark
 * comparing two code versions actually needs: a time budget would silently do <em>less</em> search
 * on a slower version and call that a fair comparison. Added specifically to give profiling-driven
 * propagation/search changes (e.g. the {@code AC3.revise} materialisation fix motivated by JFR
 * profiling of this exact instance) a disciplined, reusable harness: JMH's warmup/measurement
 * iterations and reported error margins are what actually distinguish a genuine improvement from
 * this kind of long-running sandboxed environment's own run-to-run timing noise, which a single
 * before/after timing comparison cannot.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(1)
public class CsplibBenchmarks {

    // Order-8 Golomb ruler at its known-optimal length (OEIS A003022): satisfiable, meaningfully
    // harder to search than the test's order-5 instance.
    private static final int SCALED_GOLOMB_ORDER = 8;
    private static final int SCALED_GOLOMB_LENGTH = 34;

    // 3 groups of 2 golfers over 3 rounds (6 golfers) -- larger than the test's 2x2x3 (4 golfers)
    // instance. Social Golfers is a notoriously hard combinatorial problem even at small sizes: an
    // empirical sizing pass found 3 groups x 3 per group x 4 rounds (9 golfers) took over a minute
    // to solve, and even 3x2x4 (6 golfers) took ~67s, so this is deliberately a modest step up
    // rather than the first larger instance tried.
    private static final int SCALED_GOLFER_GROUPS = 3;
    private static final int SCALED_GOLFER_PER_GROUP = 2;
    private static final int SCALED_GOLFER_ROUNDS = 3;

    private static final int SCALED_MAGIC_SQUARE_ORDER = 5;

    // Same mod-4 residue class as the test's proven-satisfiable N=8, scaled up 2x.
    private static final int SCALED_PARTITION_N = 16;

    private static final int SCALED_NQUEENS_N = 16;

    // Node budget for magicSquareXcsp3LargeNodeBudget -- see that benchmark's own Javadoc for why
    // a node budget rather than a time budget. 5000 was the value used, and validated as giving a
    // good signal-to-noise ratio in this sandboxed environment, during the manual investigation
    // that motivated adding this benchmark in the first place.
    private static final long MAGIC_SQUARE_XCSP3_NODE_BUDGET = 5000;

    // See deterministicConfig()'s own comment for why a fixed literal seed, not RestartRandomization.NONE.
    private static final long RESTART_RANDOMIZATION_SEED = 42L;

    // Fixed real-world instances, unscaled.
    private ConstraintSatisfactionProblem carSequencing;
    private ConstraintSatisfactionProblem bibd;
    private ConstraintSatisfactionProblem warehouseLocation;
    private ConstraintSatisfactionProblem steelMillSlabDesign;
    private ConstraintSatisfactionProblem killerSudoku;
    private ConstraintSatisfactionProblem jobShopScheduling;
    private ConstraintSatisfactionProblem productMatrixTsp;
    private ConstraintSatisfactionProblem knapsack;

    // Parametric families at their fixed/test size.
    private ConstraintSatisfactionProblem golombRuler;
    private ConstraintSatisfactionProblem socialGolfers;
    private ConstraintSatisfactionProblem magicSquare;
    private ConstraintSatisfactionProblem steinerTripleSystem;
    private ConstraintSatisfactionProblem numberPartitioning;
    private ConstraintSatisfactionProblem nQueens;

    // Parametric families, scaled up (Steiner Triple System has no scaled variant -- see class javadoc).
    private ConstraintSatisfactionProblem golombRulerScaled;
    private ConstraintSatisfactionProblem socialGolfersScaled;
    private ConstraintSatisfactionProblem magicSquareScaled;
    private ConstraintSatisfactionProblem numberPartitioningScaled;
    private ConstraintSatisfactionProblem nQueensScaled;

    // See magicSquareXcsp3LargeNodeBudget's own Javadoc.
    private ConstraintSatisfactionProblem magicSquareXcsp3Large;

    @Setup(Level.Trial)
    public void setup() throws IOException, URISyntaxException {
        carSequencing = Prob001CarSequencingTest.CSP;
        bibd = Prob028BalancedIncompleteBlockDesignTest.PROBLEM.csp();
        warehouseLocation = Prob034WarehouseLocationTest.CSP;
        steelMillSlabDesign = Prob038SteelMillSlabDesignTest.CSP;
        killerSudoku = Prob057KillerSudokuTest.killerSudoku();
        jobShopScheduling = Prob061JobShopSchedulingTest.CSP;
        productMatrixTsp = Prob075ProductMatrixTspTest.TSP;
        knapsack = Prob133KnapsackTest.problem();

        golombRuler = Prob006GolombRulerTest.buildRuler(Prob006GolombRulerTest.N, Prob006GolombRulerTest.OPTIMAL_LENGTH).csp();
        socialGolfers = Prob010SocialGolfersTest.CSP;
        magicSquare = Prob019MagicSquareTest.square().csp();
        steinerTripleSystem = Prob044SteinerTripleSystemTest.CSP;
        numberPartitioning = Prob049NumberPartitioningTest.CSP;
        nQueens = Prob054NQueensTest.nQueens();

        golombRulerScaled = Prob006GolombRulerTest.buildRuler(SCALED_GOLOMB_ORDER, SCALED_GOLOMB_LENGTH).csp();
        socialGolfersScaled = Prob010SocialGolfersTest.buildCsp(
                SCALED_GOLFER_GROUPS, SCALED_GOLFER_PER_GROUP, SCALED_GOLFER_ROUNDS, scaledGolfers()).csp();
        magicSquareScaled = Prob019MagicSquareTest.square(SCALED_MAGIC_SQUARE_ORDER).csp();
        numberPartitioningScaled = Prob049NumberPartitioningTest.buildCsp(SCALED_PARTITION_N).csp();
        nQueensScaled = Prob054NQueensTest.nQueens(SCALED_NQUEENS_N).csp();

        magicSquareXcsp3Large = loadMagicSquareXcsp3Large();
    }

    private static ConstraintSatisfactionProblem loadMagicSquareXcsp3Large() throws IOException, URISyntaxException {
        URL resource = CsplibBenchmarks.class.getResource("/xcsp3/competition/MagicSquare-6-sum.xml.lzma");
        if (resource == null) {
            throw new IllegalStateException(
                    "Bundled instance /xcsp3/competition/MagicSquare-6-sum.xml.lzma is missing from the classpath "
                            + "(expected src/test/resources/xcsp3/competition on target/test-classes)");
        }
        Path instancePath = Paths.get(resource.toURI());
        return Xcsp3Parser.parse(instancePath).csp();
    }

    private static Set<String> scaledGolfers() {
        return IntStream.range(0, SCALED_GOLFER_GROUPS * SCALED_GOLFER_PER_GROUP)
                .mapToObj(i -> "g" + i)
                .collect(Collectors.toSet());
    }

    /**
     * Fresh per call (not a shared constant) so each benchmark invocation still gets its own
     * {@code Statistics} token, per {@link SolverConfig}'s own construction contract -- only
     * {@code restartRandomization} is pinned away from its own fresh-random-per-construction
     * default, to {@link RestartRandomization#seeded} with the fixed {@link
     * #RESTART_RANDOMIZATION_SEED} rather than {@link RestartRandomization#NONE}: a fixed seed
     * reproduces the same restart-tie-break sequence every call (same as {@code NONE} would for
     * its own, different, always-deterministic behaviour), but does so via the real production
     * code path -- {@code NONE} would instead measure a special-cased "diversification disabled"
     * path most real callers never take.
     */
    private static SolverConfig deterministicConfig() {
        return SolverConfig.builder().restartRandomization(RestartRandomization.seeded(RESTART_RANDOMIZATION_SEED)).build();
    }

    // --- Fixed real-world instances, unscaled full-chain regression baselines ---

    @Benchmark
    public void carSequencing(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(carSequencing, deterministicConfig()).getSolution());
    }

    @Benchmark
    public void balancedIncompleteBlockDesign(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(bibd, deterministicConfig()).getSolution());
    }

    @Benchmark
    public void warehouseLocation(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(warehouseLocation, Prob034WarehouseLocationTest::totalCost, deterministicConfig()).getSolution());
    }

    @Benchmark
    public void steelMillSlabDesign(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(steelMillSlabDesign, Prob038SteelMillSlabDesignTest::totalLoss, deterministicConfig()).getSolution());
    }

    @Benchmark
    public void killerSudoku(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(killerSudoku, deterministicConfig()).getSolution());
    }

    @Benchmark
    public void jobShopScheduling(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(jobShopScheduling, Prob061JobShopSchedulingTest::makespan, deterministicConfig()).getSolution());
    }

    @Benchmark
    public void productMatrixTsp(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(productMatrixTsp, Prob075ProductMatrixTspTest::tourCost, deterministicConfig()).getSolution());
    }

    @Benchmark
    public void knapsack(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(knapsack, Prob133KnapsackTest::negatedValue, deterministicConfig()).getSolution());
    }

    // --- Parametric families at their fixed/test size: first solution and full enumeration ---

    @Benchmark
    public void golombRuler(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(golombRuler, deterministicConfig()).getSolution());
    }

    @Benchmark
    public void golombRulerAllSolutions(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(golombRuler, deterministicConfig()).getSolutions().count());
    }

    @Benchmark
    public void socialGolfers(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(socialGolfers, deterministicConfig()).getSolution());
    }

    @Benchmark
    public void socialGolfersAllSolutions(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(socialGolfers, deterministicConfig()).getSolutions().count());
    }

    @Benchmark
    public void magicSquare(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(magicSquare, deterministicConfig()).getSolution());
    }

    @Benchmark
    public void magicSquareAllSolutions(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(magicSquare, deterministicConfig()).getSolutions().count());
    }

    @Benchmark
    public void steinerTripleSystem(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(steinerTripleSystem, deterministicConfig()).getSolution());
    }

    @Benchmark
    public void steinerTripleSystemAllSolutions(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(steinerTripleSystem, deterministicConfig()).getSolutions().count());
    }

    @Benchmark
    public void numberPartitioning(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(numberPartitioning, deterministicConfig()).getSolution());
    }

    @Benchmark
    public void numberPartitioningAllSolutions(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(numberPartitioning, deterministicConfig()).getSolutions().count());
    }

    @Benchmark
    public void nQueens(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(nQueens, deterministicConfig()).getSolution());
    }

    @Benchmark
    public void nQueensAllSolutions(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(nQueens, deterministicConfig()).getSolutions().count());
    }

    // --- Parametric families, scaled up: first solution only (full enumeration would be unbounded) ---

    @Benchmark
    public void golombRulerScaled(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(golombRulerScaled, deterministicConfig()).getSolution());
    }

    @Benchmark
    public void socialGolfersScaled(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(socialGolfersScaled, deterministicConfig()).getSolution());
    }

    @Benchmark
    public void magicSquareScaled(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(magicSquareScaled, deterministicConfig()).getSolution());
    }

    @Benchmark
    public void numberPartitioningScaled(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(numberPartitioningScaled, deterministicConfig()).getSolution());
    }

    @Benchmark
    public void nQueensScaled(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(nQueensScaled, deterministicConfig()).getSolution());
    }

    // --- Real XCSP3 competition instance, bounded by node budget rather than solved to completion ---

    /**
     * See this class's own Javadoc for why this benchmark is bounded by a node budget rather than
     * solved to completion, and why it's sourced from a bundled XCSP3 competition instance rather
     * than a {@code ProbNNN} test class. A fresh {@link SolverLimits} is built per invocation
     * (rather than once in {@link #setup}) since it carries mutable runtime state ({@code
     * limitHitStats}) that must not leak between JMH iterations. {@link LimitExceededException} is
     * the expected, common-case outcome here (the whole point of the node budget is to stop search
     * before it would otherwise finish) and is consumed by the {@link Blackhole} like any other
     * result, not treated as a benchmark failure.
     */
    @Benchmark
    public void magicSquareXcsp3LargeNodeBudget(Blackhole bh) {
        SolverConfig config = SolverConfig.builder()
                .limits(SolverLimits.ofNodes(MAGIC_SQUARE_XCSP3_NODE_BUDGET))
                .restartRandomization(RestartRandomization.seeded(RESTART_RANDOMIZATION_SEED))
                .build();
        try {
            bh.consume(Solver.Factory.INSTANCE.createSolver(magicSquareXcsp3Large, config).getSolution());
        } catch (LimitExceededException e) {
            bh.consume(e);
        }
    }
}

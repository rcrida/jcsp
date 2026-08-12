package io.github.rcrida.jcsp.solver.examples.csplib;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.solver.Solver;
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

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
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

    // Fixed real-world instances, unscaled.
    private ConstraintSatisfactionProblem carSequencing;
    private ConstraintSatisfactionProblem bibd;
    private ConstraintSatisfactionProblem warehouseLocation;
    private ConstraintSatisfactionProblem steelMillSlabDesign;
    private ConstraintSatisfactionProblem killerSudoku;
    private ConstraintSatisfactionProblem jobShopScheduling;
    private ConstraintSatisfactionProblem productMatrixTsp;
    private ConstraintSatisfactionProblem knapsack;
    private ToDoubleFunction<Assignment> knapsackObjective;

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

    @Setup(Level.Trial)
    public void setup() {
        carSequencing = Prob001CarSequencingTest.CSP;
        bibd = Prob028BalancedIncompleteBlockDesignTest.PROBLEM.csp();
        warehouseLocation = Prob034WarehouseLocationTest.CSP;
        steelMillSlabDesign = Prob038SteelMillSlabDesignTest.CSP;
        killerSudoku = Prob057KillerSudokuTest.killerSudoku();
        jobShopScheduling = Prob061JobShopSchedulingTest.CSP;
        productMatrixTsp = Prob075ProductMatrixTspTest.TSP;
        var knapsackInstance = Prob133KnapsackTest.xcsp3Instance();
        knapsack = knapsackInstance.csp();
        knapsackObjective = knapsackInstance.objective();

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
    }

    private static Set<String> scaledGolfers() {
        return IntStream.range(0, SCALED_GOLFER_GROUPS * SCALED_GOLFER_PER_GROUP)
                .mapToObj(i -> "g" + i)
                .collect(Collectors.toSet());
    }

    // --- Fixed real-world instances, unscaled full-chain regression baselines ---

    @Benchmark
    public void carSequencing(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(carSequencing).getSolution());
    }

    @Benchmark
    public void balancedIncompleteBlockDesign(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(bibd).getSolution());
    }

    @Benchmark
    public void warehouseLocation(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(warehouseLocation, Prob034WarehouseLocationTest::totalCost).getSolution());
    }

    @Benchmark
    public void steelMillSlabDesign(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(steelMillSlabDesign, Prob038SteelMillSlabDesignTest::totalLoss).getSolution());
    }

    @Benchmark
    public void killerSudoku(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(killerSudoku).getSolution());
    }

    @Benchmark
    public void jobShopScheduling(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(jobShopScheduling, Prob061JobShopSchedulingTest::makespan).getSolution());
    }

    @Benchmark
    public void productMatrixTsp(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(productMatrixTsp, Prob075ProductMatrixTspTest::tourCost).getSolution());
    }

    @Benchmark
    public void knapsack(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(knapsack, knapsackObjective).getSolution());
    }

    // --- Parametric families at their fixed/test size: first solution and full enumeration ---

    @Benchmark
    public void golombRuler(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(golombRuler).getSolution());
    }

    @Benchmark
    public void golombRulerAllSolutions(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(golombRuler).getSolutions().count());
    }

    @Benchmark
    public void socialGolfers(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(socialGolfers).getSolution());
    }

    @Benchmark
    public void socialGolfersAllSolutions(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(socialGolfers).getSolutions().count());
    }

    @Benchmark
    public void magicSquare(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(magicSquare).getSolution());
    }

    @Benchmark
    public void magicSquareAllSolutions(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(magicSquare).getSolutions().count());
    }

    @Benchmark
    public void steinerTripleSystem(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(steinerTripleSystem).getSolution());
    }

    @Benchmark
    public void steinerTripleSystemAllSolutions(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(steinerTripleSystem).getSolutions().count());
    }

    @Benchmark
    public void numberPartitioning(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(numberPartitioning).getSolution());
    }

    @Benchmark
    public void numberPartitioningAllSolutions(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(numberPartitioning).getSolutions().count());
    }

    @Benchmark
    public void nQueens(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(nQueens).getSolution());
    }

    @Benchmark
    public void nQueensAllSolutions(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(nQueens).getSolutions().count());
    }

    // --- Parametric families, scaled up: first solution only (full enumeration would be unbounded) ---

    @Benchmark
    public void golombRulerScaled(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(golombRulerScaled).getSolution());
    }

    @Benchmark
    public void socialGolfersScaled(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(socialGolfersScaled).getSolution());
    }

    @Benchmark
    public void magicSquareScaled(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(magicSquareScaled).getSolution());
    }

    @Benchmark
    public void numberPartitioningScaled(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(numberPartitioningScaled).getSolution());
    }

    @Benchmark
    public void nQueensScaled(Blackhole bh) {
        bh.consume(Solver.Factory.INSTANCE.createSolver(nQueensScaled).getSolution());
    }
}

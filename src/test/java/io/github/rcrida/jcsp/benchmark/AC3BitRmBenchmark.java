package io.github.rcrida.jcsp.benchmark;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.assignments.NogoodStore;
import io.github.rcrida.jcsp.assignments.SolverLimits;
import io.github.rcrida.jcsp.assignments.Statistics;
import io.github.rcrida.jcsp.constraints.binary.BinaryConstraint;
import io.github.rcrida.jcsp.constraints.nary.GroundNogoodConstraint;
import io.github.rcrida.jcsp.consistency.ConsistencyResult;
import io.github.rcrida.jcsp.consistency.ConstraintConsistency;
import io.github.rcrida.jcsp.consistency.Inference;
import io.github.rcrida.jcsp.consistency.arc.AC3;
import io.github.rcrida.jcsp.consistency.arc.AC3BitRm;
import io.github.rcrida.jcsp.consistency.arc.Arc;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.ObjectSingletonDomain;
import io.github.rcrida.jcsp.parser.xcsp3.Xcsp3Parser;
import io.github.rcrida.jcsp.solver.DomWdegLubySearch;
import io.github.rcrida.jcsp.solver.FixpointPropagation;
import io.github.rcrida.jcsp.solver.LimitExceededException;
import io.github.rcrida.jcsp.solver.NodeConsistentSolver;
import io.github.rcrida.jcsp.solver.PropagationFixpointSolver;
import io.github.rcrida.jcsp.solver.RestartRandomization;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.solver.backtrackingsearch.order.LeastConstrainingValueOrderer;
import io.github.rcrida.jcsp.variables.Variable;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * JMH benchmark comparing {@link AC3} against {@link AC3BitRm} wall-clock, same discipline {@code
 * CsplibBenchmarks#magicSquareXcsp3LargeNodeBudget} uses (a fixed {@link SolverLimits#ofNodes} node
 * budget, not a time budget, so both variants do the same amount of search work per invocation
 * regardless of how fast either one runs -- the property a before/after comparison needs; see that
 * method's own Javadoc) plus a fixed seeded {@link RestartRandomization} so both variants follow the
 * identical search trajectory.
 *
 * <p>Neither {@code MAC} nor {@code Solver.Factory} exposes a seam to swap the arc-consistency
 * algorithm they use (both hardcode {@link AC3BitRm} in production), so {@link #buildChain} builds
 * its own chain: {@link #macThenFixpoint} replicates {@code MAC}'s own arc-filtering logic verbatim,
 * parameterized by a {@link Variant}'s {@code applyQueue}/{@code applyQueueWithReason} instead of
 * calling {@code MAC.INSTANCE} directly, and substitutes the same variant's {@link
 * ConstraintConsistency} for whichever {@link FixpointPropagation#PROPAGATORS} entry is {@link
 * AC3BitRm#INSTANCE} -- covering both call sites {@link AC3BitRm} is actually wired into in
 * production. {@link #variantName} is a {@code @Param}, so JMH runs every {@code @Benchmark} method
 * once per variant and reports them side by side.
 *
 * <p>Six scenarios: four synthetic dense-random binary CSPs ({@link #denseBinaryNarrowDomain}/
 * {@link #denseBinaryMediumDomain}/{@link #denseBinaryWideDomain}/{@link #denseBinaryVeryWideDomain},
 * same construction as {@code NogoodPropagationBenchmark#randomBinaryCsp} -- deliberately built from
 * only {@code biPredicateConstraint}, so {@code AC3}/{@code AC3BitRm} and nogood learning are the
 * only active propagators) at increasing domain size, since {@link AC3BitRm}'s precomputed support
 * bitset costs {@code O(|D_i|*|D_j|)} up front per (arc, constraint) -- whether that amortizes
 * against {@code AC3}'s cheaper-per-call-but-repeated-every-time scan plausibly depends on how wide
 * the domains are (specifically, whether a domain spans more than one 64-bit {@link java.util.BitSet}
 * word), not just how many search nodes reuse the graph; {@link #expensiveConstraintCheck}, the same
 * shape as {@link #denseBinaryMediumDomain} but with a deliberately non-trivial per-pair check (see
 * its own Javadoc) -- the other factor, besides domain width, the literature attributes AC3bit+rm's
 * advantage to; and {@link #roomMateXcsp3}, the real, unmodified {@code RoomMate-sr0050-int.xml.lzma}
 * competition instance bundled at {@code src/test/resources/xcsp3/competition/} (50 variables, 4900
 * constraints, binary-preference-heavy -- the same corpus {@code Xcsp3CompetitionRunner} drives) for
 * external validity beyond synthetic CSPs.
 *
 * <p>Run via {@code mvn test-compile} then
 * {@code java -cp target/classes:target/test-classes:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout) org.openjdk.jmh.Main AC3BitRmBenchmark}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(1)
public class AC3BitRmBenchmark {

    // Modest budgets, not NogoodPropagationBenchmark's 300k: JMH's default iteration window is only
    // ~10s and re-invokes the @Benchmark method repeatedly within it, so each single invocation needs
    // to stay fast, especially for AC3's slower per-node cost on the wide-domain scenario -- same
    // "keep it JMH-iteration-fast" reasoning as CsplibBenchmarks#MAGIC_SQUARE_XCSP3_NODE_BUDGET.
    private static final long DENSE_BINARY_NODE_LIMIT = 5_000L;
    // Smaller still for the real XCSP3 instance: RoomMate-sr0050-int's 4900 constraints make each
    // node itself expensive (Xcsp3CompetitionRunner's own bundled run explores ~688k nodes in a full
    // 60s budget), so a few thousand nodes is already a representative, JMH-iteration-fast sample.
    private static final long XCSP3_NODE_LIMIT = 2_000L;
    // Smaller than DENSE_BINARY_NODE_LIMIT: AC3's own naive scan is O(|D_i|*|D_j|) per revise, and
    // at domain size 250 that's up to 62,500 checks per (arc, constraint) -- keeping the node budget
    // modest here is what keeps this JMH-iteration-fast despite the much larger domain.
    private static final long VERY_WIDE_NODE_LIMIT = 1_000L;
    private static final long RESTART_RANDOMIZATION_SEED = 42L;

    @Param({"AC3", "AC3BitRm"})
    public String variantName;

    private Variant variant;
    private ConstraintSatisfactionProblem denseBinaryNarrow;
    private ConstraintSatisfactionProblem denseBinaryMedium;
    private ConstraintSatisfactionProblem denseBinaryWide;
    private ConstraintSatisfactionProblem denseBinaryVeryWide;
    private ConstraintSatisfactionProblem expensiveConstraintCheck;
    private ConstraintSatisfactionProblem roomMate;

    @FunctionalInterface
    private interface ArcConsistencyQueue {
        Optional<ConstraintSatisfactionProblem> applyQueue(ConstraintSatisfactionProblem problem, Queue<Arc> queue);
    }

    @FunctionalInterface
    private interface ArcConsistencyQueueWithReason {
        ConsistencyResult applyQueueWithReason(ConstraintSatisfactionProblem problem, Queue<Arc> queue);
    }

    private record Variant(ConstraintConsistency propagator,
                            ArcConsistencyQueue applyQueue, ArcConsistencyQueueWithReason applyQueueWithReason) {
    }

    private static final Variant AC3_VARIANT = new Variant(
            AC3.INSTANCE, AC3.INSTANCE::applyQueue, AC3.INSTANCE::applyQueueWithReason);
    private static final Variant AC3_BIT_RM_VARIANT = new Variant(
            AC3BitRm.INSTANCE, AC3BitRm.INSTANCE::applyQueue, AC3BitRm.INSTANCE::applyQueueWithReason);

    @Setup(Level.Trial)
    public void setup() throws IOException, URISyntaxException {
        variant = "AC3".equals(variantName) ? AC3_VARIANT : AC3_BIT_RM_VARIANT;
        denseBinaryNarrow = randomBinaryCsp(26, 6, 0.13, 42L);
        denseBinaryMedium = randomBinaryCsp(18, 25, 0.13, 42L);
        denseBinaryWide = randomBinaryCsp(14, 60, 0.10, 7L);
        denseBinaryVeryWide = randomBinaryCsp(10, 250, 0.15, 42L);
        expensiveConstraintCheck = randomBinaryCspExpensiveCheck(18, 20, 0.13, 42L);
        roomMate = loadBundledXcsp3("RoomMate-sr0050-int.xml.lzma");
    }

    @Benchmark
    public void denseBinaryNarrowDomain(Blackhole bh) {
        bh.consume(solve(denseBinaryNarrow, DENSE_BINARY_NODE_LIMIT));
    }

    @Benchmark
    public void denseBinaryMediumDomain(Blackhole bh) {
        bh.consume(solve(denseBinaryMedium, DENSE_BINARY_NODE_LIMIT));
    }

    @Benchmark
    public void denseBinaryWideDomain(Blackhole bh) {
        bh.consume(solve(denseBinaryWide, DENSE_BINARY_NODE_LIMIT));
    }

    /**
     * Domain size 250 -- spans multiple 64-bit {@link java.util.BitSet} words (unlike {@link
     * #denseBinaryWideDomain}'s 60, which still fits in one), so {@link AC3BitRm}'s word-parallel
     * {@link java.util.BitSet#intersects} gets genuine parallelism to exploit that a single-word
     * domain never exercises.
     */
    @Benchmark
    public void denseBinaryVeryWideDomain(Blackhole bh) {
        bh.consume(solve(denseBinaryVeryWide, VERY_WIDE_NODE_LIMIT));
    }

    /**
     * Same shape as {@link #denseBinaryMediumDomain} but each pair check does deliberate extra work
     * ({@link Blackhole#consumeCPU}) before returning the same precomputed answer -- simulating a
     * constraint whose {@code isSatisfiedByArcValues} isn't a trivial lambda, the other factor (besides
     * domain width) the literature attributes AC3bit+rm's advantage to: {@link AC3}'s naive scan pays
     * that cost on every pair on every revise call, while {@link AC3BitRm} pays it once per pair at
     * table-build time and only ever does cheap bit tests afterward.
     */
    @Benchmark
    public void expensiveConstraintCheck(Blackhole bh) {
        bh.consume(solve(expensiveConstraintCheck, DENSE_BINARY_NODE_LIMIT));
    }

    @Benchmark
    public void roomMateXcsp3(Blackhole bh) {
        bh.consume(solve(roomMate, XCSP3_NODE_LIMIT));
    }

    private Optional<Assignment> solve(ConstraintSatisfactionProblem csp, long nodeLimit) {
        Solver chain = buildChain(variant, csp, SolverLimits.ofNodes(nodeLimit), new Statistics(),
                RestartRandomization.seeded(RESTART_RANDOMIZATION_SEED));
        try {
            return chain.getSolution(csp);
        } catch (LimitExceededException e) {
            return Optional.empty();
        }
    }

    /**
     * Mirrors {@code Solver.Factory}'s satisfaction chain (see {@code
     * NogoodPropagationBenchmark#buildChain}), except both places {@link AC3BitRm} is hardcoded in
     * production -- {@code MAC}'s own per-node call and {@link FixpointPropagation#PROPAGATORS}'
     * arc-consistency entry -- are swapped in for whichever {@link Variant} is passed in.
     */
    private static Solver buildChain(Variant variant, ConstraintSatisfactionProblem csp, SolverLimits limits,
                                     Statistics statistics, RestartRandomization restartRandomization) {
        List<ConstraintConsistency> propagators = FixpointPropagation.PROPAGATORS.stream()
                .map(p -> p == AC3BitRm.INSTANCE ? variant.propagator() : p)
                .toList();
        FixpointPropagation fixpointPropagation = FixpointPropagation.builder().propagators(propagators).build();

        DomWdegLubySearch domWdegLubySearch = DomWdegLubySearch.builder()
                .domainValuesOrderer(LeastConstrainingValueOrderer.INSTANCE)
                .inference(macThenFixpoint(variant, fixpointPropagation))
                .limits(limits)
                .nogoodStore(NogoodStore.forProblem(csp))
                .statistics(statistics)
                .maxRestarts(Integer.MAX_VALUE)
                .restartRandomization(restartRandomization)
                .build();
        Solver propagationFixpointSolver = PropagationFixpointSolver.builder()
                .inner(domWdegLubySearch)
                .fixpointPropagation(fixpointPropagation)
                .snap(true)
                .build();
        return NodeConsistentSolver.builder().inner(propagationFixpointSolver).build();
    }

    /** Verbatim structure of {@code Solver.Factory#propagationInference}, parameterized by {@code variant}. */
    private static Inference macThenFixpoint(Variant variant, FixpointPropagation fixpointPropagation) {
        return new Inference() {
            @Override
            public Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem problem,
                                                                  Variable<?> variable, Assignment assignment) {
                return macApply(variant, problem, variable, assignment)
                        .flatMap(afterMac -> fixpointPropagation.applyFixpoint(afterMac,
                                FixpointPropagation.changedVariables(problem.getVariableDomains(), afterMac.getVariableDomains()),
                                assignment.listener(), assignment.getStatistics(), assignment.cancellation()));
            }

            @Override
            public ConsistencyResult applyWithReason(ConstraintSatisfactionProblem problem,
                                                      Variable<?> variable, Assignment assignment) {
                ConsistencyResult macResult = macApplyWithReason(variant, problem, variable, assignment);
                if (macResult.isInfeasible()) {
                    return macResult.reason() != null ? macResult
                            : ConsistencyResult.infeasible(GroundNogoodConstraint.of(assignment.getValues()));
                }
                ConstraintSatisfactionProblem afterMac = macResult.problem();
                ConsistencyResult fixpointResult = fixpointPropagation.applyFixpointWithReason(afterMac,
                        FixpointPropagation.changedVariables(problem.getVariableDomains(), afterMac.getVariableDomains()),
                        assignment.listener(), assignment.getStatistics(), assignment.cancellation());
                if (fixpointResult.isInfeasible() && fixpointResult.reason() == null) {
                    return ConsistencyResult.infeasible(GroundNogoodConstraint.of(assignment.getValues()));
                }
                return fixpointResult;
            }
        };
    }

    /** Replicates {@code MAC#apply}'s own arc-filtering logic, calling {@code variant} instead of {@code AC3BitRm.INSTANCE}. */
    @SuppressWarnings("unchecked")
    private static Optional<ConstraintSatisfactionProblem> macApply(Variant variant, ConstraintSatisfactionProblem problem,
                                                                     Variable<?> variable, Assignment assignment) {
        Object value = assignment.getValue(variable).orElseThrow();
        Queue<Arc> queue = new ArrayDeque<>(macQueue(problem, variable, assignment));
        return variant.applyQueue().applyQueue(
                problem.withDomain((Variable<Object>) variable, new ObjectSingletonDomain<>(value)), queue);
    }

    /** Replicates {@code MAC#applyWithReason}'s own arc-filtering logic, calling {@code variant} instead of {@code AC3BitRm.INSTANCE}. */
    @SuppressWarnings("unchecked")
    private static ConsistencyResult macApplyWithReason(Variant variant, ConstraintSatisfactionProblem problem,
                                                         Variable<?> variable, Assignment assignment) {
        Object value = assignment.getValue(variable).orElseThrow();
        Queue<Arc> queue = new ArrayDeque<>(macQueue(problem, variable, assignment));
        return variant.applyQueueWithReason().applyQueueWithReason(
                problem.withDomain((Variable<Object>) variable, new ObjectSingletonDomain<>(value)), queue);
    }

    private static Set<Arc> macQueue(ConstraintSatisfactionProblem problem, Variable<?> variable, Assignment assignment) {
        return problem.getAllBinaryConstraints().stream()
                .flatMap(BinaryConstraint::getArcs)
                .filter(arc -> arc.getTo().equals(variable))
                .filter(arc -> assignment.getValue(arc.getFrom()).isEmpty())
                .collect(Collectors.toSet());
    }

    private static ConstraintSatisfactionProblem loadBundledXcsp3(String fileName) throws IOException, URISyntaxException {
        URL resource = AC3BitRmBenchmark.class.getResource("/xcsp3/competition/" + fileName);
        if (resource == null) {
            throw new IllegalStateException("Bundled instance /xcsp3/competition/" + fileName
                    + " is missing from the classpath (expected src/test/resources/xcsp3/competition on target/test-classes)");
        }
        Path instancePath = Paths.get(resource.toURI());
        return Xcsp3Parser.parse(instancePath).csp();
    }

    /** Same construction as {@code NogoodPropagationBenchmark#randomBinaryCsp} -- see its own Javadoc. */
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
     * Same construction as {@link #randomBinaryCsp}, except each pair check burns {@link
     * Blackhole#consumeCPU} tokens before returning {@code compatible[x][y]} -- the returned
     * boolean is unaffected, only the cost of computing it, so {@link AC3}/{@link AC3BitRm} still
     * have to agree on an identical result exactly as they do for the cheap-check scenarios.
     * {@link Blackhole#consumeCPU} (not a hand-rolled busy loop) specifically because JMH's own
     * implementation is written to survive JIT dead-code elimination; a hand-rolled loop whose
     * result is otherwise discarded risks being optimized away entirely, silently turning this back
     * into the cheap-check scenario it's meant to be distinct from.
     */
    private static ConstraintSatisfactionProblem randomBinaryCspExpensiveCheck(int n, int domainSize, double tightness, long seed) {
        Variable.Factory f = Variable.Factory.INSTANCE;
        List<Variable<Integer>> vars = new ArrayList<>();
        for (int i = 0; i < n; i++) vars.add(f.create("ev" + i));

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
                builder.biPredicateConstraint(vars.get(i), vars.get(j), (x, y) -> {
                    Blackhole.consumeCPU(50);
                    return compatible[x][y];
                });
            }
        }
        return builder.build();
    }
}

package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.ConstraintConsistency;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.assignmentfactory.InitialAssignmentFactory;
import io.github.rcrida.jcsp.solver.assignmentfactory.RandomAssignmentFactory;
import io.github.rcrida.jcsp.solver.listener.LocalSolverListener;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.LongAdder;

import static org.assertj.core.api.Assertions.assertThat;

class LocalSolverConfigTest {
    private static final Variable.Factory VF = Variable.Factory.INSTANCE;

    @Test
    void listener_defaultsToNone() {
        assertThat(LocalSolverConfig.builder().build().getListener()).isSameAs(LocalSolverListener.NONE);
    }

    @Test
    void listenerReceivesLocalSearchStepAndSolutionFoundEvents() {
        // A ConcurrentLinkedQueue/LongAdder-backed recorder, not a plain List/int: MinConflictsSolver
        // and TabuSearchSolver race each other via IndependentSubproblemLocalSolver/RaceLocalSolver,
        // both running IntStream.parallel() attempts, so the listener is genuinely invoked
        // concurrently from multiple threads -- this is the thread-safety contract in practice, not
        // just documented.
        //
        // Deliberately not RandomAssignmentFactory: for a two-variable notEquals CSP this loose, a
        // random initial assignment already satisfies it often enough (~80% per attempt) that no
        // repair step -- and therefore no onLocalSearchStep -- would ever fire. This factory always
        // starts both variables at the domain's max value, guaranteeing an initial violation so at
        // least one real repair step is forced deterministically.
        Variable<Integer> x = VF.create("lscx");
        Variable<Integer> y = VF.create("lscy");
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 5))
                .variableDomain(y, IntRangeDomain.of(1, 5))
                .notEqualsConstraint(x, y)
                .build();
        InitialAssignmentFactory alwaysMaxFactory = c -> {
            var builder = Assignment.builder();
            c.getVariableDomains().forEach((v, d) -> {
                var values = ((DiscreteDomain<?>) d).toList();
                builder.value(v, values.get(values.size() - 1));
            });
            return builder.build();
        };

        LongAdder steps = new LongAdder();
        ConcurrentLinkedQueue<Assignment> solutionsFound = new ConcurrentLinkedQueue<>();
        LocalSolverListener recorder = new LocalSolverListener() {
            @Override
            public void onLocalSearchStep(int attempt, int step, Assignment current) {
                steps.increment();
            }

            @Override
            public void onSolutionFound(Assignment solution) {
                solutionsFound.add(solution);
            }
        };

        var solver = LocalSolver.Factory.INSTANCE.createLocalSolver(10, 100, alwaysMaxFactory,
                LocalSolverConfig.builder().listener(recorder).build());

        var solution = solver.getLocalSolution(csp);

        assertThat(solution).isPresent();
        assertThat(steps.sum()).isGreaterThan(0);
        assertThat(solutionsFound).isNotEmpty();
    }

    @Test
    void listenerReceivesOnPropagatorProgressDuringPreprocessing() {
        // x={1}, y={1,2}, x!=y -- AC3 (part of LocalSolver.Factory#PREPROCESSORS) removes 1 from
        // y's domain before repair search ever starts, narrowing the domain-sum from 2 to 1.
        Variable<Integer> x = VF.create("ppx");
        Variable<Integer> y = VF.create("ppy");
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 1))
                .variableDomain(y, IntRangeDomain.of(1, 2))
                .notEqualsConstraint(x, y)
                .build();

        record Progress(ConstraintConsistency propagator, double beforeSum, double afterSum) {}
        ConcurrentLinkedQueue<Progress> progress = new ConcurrentLinkedQueue<>();
        LocalSolverListener recorder = new LocalSolverListener() {
            @Override
            public void onPropagatorProgress(ConstraintConsistency propagator, Map<Variable<?>, Domain<?>> domainsBefore,
                                              Map<Variable<?>, Domain<?>> domainsAfter, double domainSumBefore, double domainSumAfter) {
                progress.add(new Progress(propagator, domainSumBefore, domainSumAfter));
            }
        };

        var solver = LocalSolver.Factory.INSTANCE.createLocalSolver(1, 10, RandomAssignmentFactory.INSTANCE,
                LocalSolverConfig.builder().listener(recorder).build());

        assertThat(solver.getLocalSolution(csp)).isPresent();
        assertThat(progress).isNotEmpty();
        assertThat(progress.stream().mapToDouble(Progress::afterSum).min().orElseThrow())
                .isLessThan(progress.stream().mapToDouble(Progress::beforeSum).max().orElseThrow());
    }

    @Test
    void applyPreprocessorsShortCircuitsOnInfeasibleCsp() {
        // x={1}, y={1}, x!=y -- AC3 (part of PREPROCESSORS) wipes y's domain to empty during
        // preprocessing itself, before repair search ever starts. Every other test here uses a
        // satisfiable CSP, so this is the only one that exercises applyPreprocessors' early-return
        // once a propagator reports infeasibility mid-pass.
        Variable<Integer> x = VF.create("infx");
        Variable<Integer> y = VF.create("infy");
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 1))
                .variableDomain(y, IntRangeDomain.of(1, 1))
                .notEqualsConstraint(x, y)
                .build();

        var solver = LocalSolver.Factory.INSTANCE.createLocalSolver(1, 10, RandomAssignmentFactory.INSTANCE,
                LocalSolverConfig.builder().build());

        assertThat(solver.getLocalSolution(csp)).isEmpty();
    }
}

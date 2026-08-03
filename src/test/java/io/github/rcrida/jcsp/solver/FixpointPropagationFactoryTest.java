package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Statistics;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.binary.BinaryComparatorConstraint;
import io.github.rcrida.jcsp.constraints.nary.GroundNogoodConstraint;
import io.github.rcrida.jcsp.constraints.nary.SumBoundConstraint;
import io.github.rcrida.jcsp.consistency.ConstraintConsistency;
import io.github.rcrida.jcsp.consistency.arc.AC3;
import io.github.rcrida.jcsp.consistency.fixpoint.NogoodFixpointConsistency;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.listener.SolverListener;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@link FixpointPropagation.Factory#forProblem}'s filtering: a {@code
 * FixpointConsistency.of(...)} entry is included only if its constraint type is present, {@link
 * AC3#INSTANCE} only if the problem has binary/binary-decomposable constraints, and {@link
 * NogoodFixpointConsistency#INSTANCE} only when nogood learning is enabled.
 */
public class FixpointPropagationFactoryTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    @Test
    void forProblem_includesOnlyThePropagatorForThePresentConstraintType() {
        Variable<Integer> v1 = F.create("sumv1"), v2 = F.create("sumv2"), v3 = F.create("sumv3");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(v1, IntRangeDomain.of(1, 5))
                .variableDomain(v2, IntRangeDomain.of(1, 5))
                .variableDomain(v3, IntRangeDomain.of(1, 5))
                .constraint(SumBoundConstraint.of(Set.of(v1, v2, v3), Operator.EQ, 10))
                .build();

        var filtered = FixpointPropagation.Factory.INSTANCE.forProblem(csp, false);

        assertThat(filtered.getPropagators())
                .extracting(Object::toString)
                .containsExactly("FixpointConsistency(SumBoundConstraint)");
    }

    @Test
    void forProblem_includesAC3WhenABinaryConstraintIsPresent() {
        Variable<Integer> x = F.create("binaryx"), y = F.create("binaryy");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 5))
                .variableDomain(y, IntRangeDomain.of(1, 5))
                .constraint(BinaryComparatorConstraint.of(x, Operator.LT, y))
                .build();

        var filtered = FixpointPropagation.Factory.INSTANCE.forProblem(csp, false);

        assertThat(filtered.getPropagators()).contains(AC3.INSTANCE);
    }

    @Test
    void forProblem_excludesAC3WhenNoBinaryOrDecomposableConstraintIsPresent() {
        Variable<Integer> v1 = F.create("nobinaryv1"), v2 = F.create("nobinaryv2");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(v1, IntRangeDomain.of(1, 5))
                .variableDomain(v2, IntRangeDomain.of(1, 5))
                .constraint(SumBoundConstraint.of(Set.of(v1, v2), Operator.EQ, 6))
                .build();

        var filtered = FixpointPropagation.Factory.INSTANCE.forProblem(csp, false);

        assertThat(filtered.getPropagators()).doesNotContain(AC3.INSTANCE);
    }

    @Test
    void forProblem_includesNogoodFixpointConsistencyOnlyWhenLearningEnabled() {
        Variable<Integer> x = F.create("nogoodflagx");
        var csp = ConstraintSatisfactionProblem.builder().variableDomain(x, IntRangeDomain.of(1, 5)).build();

        assertThat(FixpointPropagation.Factory.INSTANCE.forProblem(csp, true).getPropagators())
                .containsExactly(NogoodFixpointConsistency.INSTANCE);
        assertThat(FixpointPropagation.Factory.INSTANCE.forProblem(csp, false).getPropagators())
                .isEmpty();
    }

    @Test
    void forProblem_includesNogoodFixpointConsistencyWhenNogoodsAlreadyPresentEvenIfLearningDisabled() {
        // SolverConfig#isNogoodLearningEnabled() only documents disabling CDCL (no explanation
        // computation, no accumulation of newly-learned nogoods) -- it never meant "stop
        // propagating nogoods the caller already put on the problem" via .nogood(...)/withNogoods().
        Variable<Integer> x = F.create("preseedx"), y = F.create("preseedy");
        var nogood = GroundNogoodConstraint.of(Map.of(x, 1, y, 2));
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 5))
                .variableDomain(y, IntRangeDomain.of(1, 5))
                .nogood(nogood)
                .build();

        assertThat(FixpointPropagation.Factory.INSTANCE.forProblem(csp, false).getPropagators())
                .containsExactly(NogoodFixpointConsistency.INSTANCE);
    }

    @Test
    void forProblem_includesBinaryComparatorPropagatorForADecomposedOrderingConstraint() {
        // IncreasingConstraint has no direct BinaryComparatorConstraint in csp.getConstraints(),
        // only in its BinaryDecomposable decomposition (csp.getAllBinaryConstraints()). A cutset/tree
        // sub-CSP built via withVariableSubset promotes exactly that decomposition into real
        // structural constraints, so the filter must see this constraint type as present too --
        // otherwise a top-level CSP shaped like this one would silently lose propagation on whatever
        // sub-CSP the ordering constraint gets split into.
        Variable<Integer> v1 = F.create("orderv1"), v2 = F.create("orderv2"), v3 = F.create("orderv3");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(v1, IntRangeDomain.of(1, 5))
                .variableDomain(v2, IntRangeDomain.of(1, 5))
                .variableDomain(v3, IntRangeDomain.of(1, 5))
                .increasingConstraint(List.of(v1, v2, v3))
                .build();

        var filtered = FixpointPropagation.Factory.INSTANCE.forProblem(csp, false);

        assertThat(filtered.getPropagators())
                .extracting(Object::toString)
                .contains("FixpointConsistency(IncreasingConstraint)", "FixpointConsistency(BinaryComparatorConstraint)");
        assertThat(filtered.getPropagators()).contains(AC3.INSTANCE);
    }

    @Test
    void isApplicable_failsOpenForAPropagatorThatIsNeitherSingletonNorFixpointConsistency() {
        // Every real PROPAGATORS entry today is AC3.INSTANCE, NogoodFixpointConsistency.INSTANCE, or
        // a FixpointConsistency -- this branch only matters for a hypothetical future PROPAGATORS
        // entry of some other ConstraintConsistency kind, which must fail open (assumed applicable)
        // rather than crash. Exercised directly with a fabricated ConstraintConsistency since
        // PROPAGATORS itself can't be mutated just to reach this through forProblem.
        Variable<Integer> x = F.create("failopenx");
        var csp = ConstraintSatisfactionProblem.builder().variableDomain(x, IntRangeDomain.of(1, 5)).build();
        ConstraintConsistency notAKnownKind = Optional::of;

        assertThat(FixpointPropagation.isApplicable(notAKnownKind, csp, false)).isTrue();
    }

    @Test
    void forProblem_filteredInstancePropagatesTheSameAsTheFullCatalog() {
        Variable<Integer> v1 = F.create("equivv1"), v2 = F.create("equivv2"), v3 = F.create("equivv3");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(v1, IntRangeDomain.of(1, 5))
                .variableDomain(v2, IntRangeDomain.of(1, 5))
                .variableDomain(v3, IntRangeDomain.of(8, 8))
                .constraint(SumBoundConstraint.of(Set.of(v1, v2, v3), Operator.EQ, 10))
                .build();

        var filtered = FixpointPropagation.Factory.INSTANCE.forProblem(csp, true);

        var viaFiltered = filtered.applyFixpoint(csp, null, SolverListener.NONE, new Statistics(), Cancellation.NEVER);
        var viaFull = FixpointPropagation.FULL.applyFixpoint(csp, null, SolverListener.NONE, new Statistics(), Cancellation.NEVER);

        assertThat(viaFiltered).isEqualTo(viaFull);
    }
}

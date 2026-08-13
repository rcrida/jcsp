package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.binary.BinaryOffsetConstraint;
import io.github.rcrida.jcsp.constraints.unary.UnaryComparatorConstraint;
import io.github.rcrida.jcsp.constraints.unary.UnaryValueConstraint;
import io.github.rcrida.jcsp.domains.BooleanDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class AndConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Integer> A = F.create("a");
    static final Variable<Integer> C = F.create("c");
    static final Variable<Double> X = F.create("x");
    static final Variable<Double> Y = F.create("y");

    static final UnaryValueConstraint<Integer> A_EQ_3 = UnaryValueConstraint.of(A, 3);
    static final UnaryValueConstraint<Integer> C_EQ_4 = UnaryValueConstraint.of(C, 4);

    /** Minimal {@link Propagatable} conjunct whose {@link #isNecessarilySatisfied} always reports true. */
    private record AlwaysNecessary(Variable<?> variable) implements Constraint, Propagatable {
        @Override
        public boolean isSatisfiedBy(Assignment a) { return true; }

        @Override
        public String getRelation() { return "always"; }

        @Override
        public Set<Variable<?>> getVariables() { return Set.of(variable); }

        @Override
        public Optional<Map<Variable<?>, Domain<?>>> propagate(Map<Variable<?>, Domain<?>> domains) {
            return Optional.of(Map.of());
        }

        @Override
        public boolean isNecessarilySatisfied(Map<Variable<?>, Domain<?>> domains) { return true; }
    }

    /**
     * Minimal {@link Propagatable} conjunct that always reports {@code variable} narrowed to
     * {@code domain} even when that's exactly the domain already in place — exercises {@code
     * runFixpoint}'s defensive "did this entry actually change" guard, which no real propagator in
     * this codebase ever needs (each already omits a variable from its own returned map rather than
     * report a no-op entry).
     */
    private record UnchangedEntryReporter(Variable<Double> variable, Domain<Double> domain)
            implements Constraint, Propagatable {
        @Override
        public boolean isSatisfiedBy(Assignment a) { return true; }

        @Override
        public String getRelation() { return "unchanged"; }

        @Override
        public Set<Variable<?>> getVariables() { return Set.of(variable); }

        @Override
        public Optional<Map<Variable<?>, Domain<?>>> propagate(Map<Variable<?>, Domain<?>> domains) {
            return Optional.of(Map.of(variable, domain));
        }
    }

    // --- isSatisfiedBy / structure ---

    @Test
    void isSatisfiedBy_allConjunctsSatisfied_true() {
        val and = AndConstraint.of(Set.of(A_EQ_3, C_EQ_4));
        assertThat(and.isSatisfiedBy(Assignment.builder().value(A, 3).value(C, 4).build())).isTrue();
    }

    @Test
    void isSatisfiedBy_oneConjunctViolated_false() {
        val and = AndConstraint.of(Set.of(A_EQ_3, C_EQ_4));
        assertThat(and.isSatisfiedBy(Assignment.builder().value(A, 3).value(C, 5).build())).isFalse();
    }

    @Test
    void isSatisfiedBy_partialAssignment_optimisticallyTrue() {
        val and = AndConstraint.of(Set.of(A_EQ_3, C_EQ_4));
        assertThat(and.isSatisfiedBy(Assignment.builder().value(A, 3).build())).isTrue();
    }

    @Test
    void of_computesVariableUnion() {
        val and = AndConstraint.of(Set.of(A_EQ_3, C_EQ_4));
        assertThat(and.getVariables()).containsExactlyInAnyOrder(A, C);
    }

    @Test
    void getRelationJoinsConjunctsSortedWithAnd() {
        val and = AndConstraint.of(Set.of(A_EQ_3, C_EQ_4));
        assertThat(and.getRelation()).isEqualTo("(a == 3 AND c == 4)");
    }

    @Test
    void testToString() {
        val and = AndConstraint.of(Set.of(A_EQ_3, C_EQ_4));
        assertThat(and.toString()).isEqualTo("<(a, c), (a == 3 AND c == 4)>");
    }

    // --- propagate() ---

    @Test
    void propagate_noNarrowing_emptyDiff() {
        val and = AndConstraint.of(Set.of(UnaryComparatorConstraint.of(X, Operator.GEQ, 0.0)));
        Map<Variable<?>, Domain<?>> domains = Map.of(X, IntervalDomain.of(1, 10));
        val result = and.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_singleConjunctNarrows() {
        val and = AndConstraint.of(Set.of(UnaryComparatorConstraint.of(X, Operator.GEQ, 3.0)));
        Map<Variable<?>, Domain<?>> domains = Map.of(X, IntervalDomain.of(1, 10));
        val result = and.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).containsEntry(X, IntervalDomain.of(3, 10));
    }

    @Test
    void propagate_crossConjunctFixpointRequiresMultipleRounds() {
        val geqThree = UnaryComparatorConstraint.of(X, Operator.GEQ, 3.0);
        val xEqualsY = BinaryOffsetConstraint.of(X, 0.0, Operator.EQ, Y);
        // Deliberately built via the builder (not #of, which routes through Set.copyOf and loses
        // order) with a LinkedHashSet so xEqualsY is visited before geqThree on every round: a
        // single left-to-right pass in this order narrows X but not Y, so this test only passes if
        // AndConstraint actually loops to a fixpoint rather than running each conjunct once.
        Set<Constraint> orderedConjuncts = new LinkedHashSet<>();
        orderedConjuncts.add(xEqualsY);
        orderedConjuncts.add(geqThree);
        val and = AndConstraint.builder().variables(Set.of(X, Y)).conjuncts(orderedConjuncts).build();
        Map<Variable<?>, Domain<?>> domains = Map.of(X, IntervalDomain.of(1, 10), Y, IntervalDomain.of(1, 10));
        val result = and.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get())
                .containsEntry(X, IntervalDomain.of(3, 10))
                .containsEntry(Y, IntervalDomain.of(3, 10));
    }

    @Test
    void propagate_conjunctInfeasible_empty() {
        val and = AndConstraint.of(Set.of(UnaryComparatorConstraint.of(X, Operator.GEQ, 20.0)));
        Map<Variable<?>, Domain<?>> domains = Map.of(X, IntervalDomain.of(1, 10));
        assertThat(and.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_conjunctReportsUnchangedEntry_notTreatedAsChange() {
        Domain<Double> unchanged = IntervalDomain.of(1, 10);
        val and = AndConstraint.of(Set.of(new UnchangedEntryReporter(X, unchanged)));
        Map<Variable<?>, Domain<?>> domains = Map.of(X, unchanged);
        val result = and.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_nonPropagatableConjunct_skippedWithoutError() {
        val and = AndConstraint.of(Set.of(A_EQ_3));
        Map<Variable<?>, Domain<?>> domains = Map.of(A, IntRangeDomain.of(1, 5));
        val result = and.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- explainInfeasible() ---

    @Test
    void explainInfeasible_feasible_empty() {
        val and = AndConstraint.of(Set.of(UnaryComparatorConstraint.of(X, Operator.GEQ, 3.0)));
        Map<Variable<?>, Domain<?>> domains = Map.of(X, IntervalDomain.of(1, 10));
        assertThat(and.explainInfeasible(domains)).isEmpty();
    }

    @Test
    void explainInfeasible_delegatesToFailingConjunctsOwnReason() {
        val offset = BinaryOffsetConstraint.of(X, 0.0, Operator.EQ, Y);
        val and = AndConstraint.of(Set.of(offset));
        Map<Variable<?>, Domain<?>> domains = Map.of(X, IntervalDomain.of(5, 5), Y, IntervalDomain.of(1, 1));
        assertThat(and.explainInfeasible(domains))
                .contains(GroundNogoodConstraint.of(Map.of(X, 5.0, Y, 1.0)));
    }

    @Test
    void explainInfeasible_fallsBackToAllSingletonReasonWhenConjunctUnexplained() {
        val and = AndConstraint.of(Set.of(UnaryComparatorConstraint.of(X, Operator.GEQ, 20.0)));
        Map<Variable<?>, Domain<?>> domains = Map.of(X, IntervalDomain.of(5, 5));
        assertThat(and.explainInfeasible(domains))
                .contains(GroundNogoodConstraint.of(Map.of(X, 5.0)));
    }

    // --- isNecessarilySatisfied() ---

    @Test
    void isNecessarilySatisfied_propagatableConjunctReportsTrue_overallTrue() {
        val and = AndConstraint.of(Set.of(new AlwaysNecessary(X)));
        assertThat(and.isNecessarilySatisfied(Map.of(X, IntervalDomain.of(1, 10)))).isTrue();
    }

    @Test
    void isNecessarilySatisfied_propagatableConjunctDefaultFalse_overallFalse() {
        val and = AndConstraint.of(Set.of(UnaryComparatorConstraint.of(X, Operator.GEQ, 3.0)));
        assertThat(and.isNecessarilySatisfied(Map.of(X, IntervalDomain.of(1, 10)))).isFalse();
    }

    @Test
    void isNecessarilySatisfied_nonPropagatableFullyDeterminedSatisfied_true() {
        val and = AndConstraint.of(Set.of(A_EQ_3));
        assertThat(and.isNecessarilySatisfied(Map.of(A, IntRangeDomain.of(3, 3)))).isTrue();
    }

    @Test
    void isNecessarilySatisfied_nonPropagatableNotFullyDetermined_false() {
        val and = AndConstraint.of(Set.of(A_EQ_3));
        assertThat(and.isNecessarilySatisfied(Map.of(A, IntRangeDomain.of(1, 5)))).isFalse();
    }

    @Test
    void isNecessarilySatisfied_nonPropagatableFullyDeterminedButViolated_false() {
        val and = AndConstraint.of(Set.of(A_EQ_3));
        assertThat(and.isNecessarilySatisfied(Map.of(A, IntRangeDomain.of(4, 4)))).isFalse();
    }

    // --- solver integration: the motivating use case, AndConstraint as a reification body ---

    @Test
    void reifiedAndConstraint_forcesBothConjunctsWhenIndicatorTrue() {
        Variable<Boolean> indicator = F.create("ind");
        Variable<Integer> p = F.create("p");
        Variable<Integer> q = F.create("q");
        val and = AndConstraint.of(Set.of(UnaryValueConstraint.of(p, 3), UnaryValueConstraint.of(q, 4)));
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(indicator, BooleanDomain.INSTANCE)
                .variableDomain(p, IntRangeDomain.of(1, 5))
                .variableDomain(q, IntRangeDomain.of(1, 5))
                .reifyConstraint(indicator, and)
                .equalsConstraint(indicator, true)
                .build();
        val solutions = Solver.Factory.INSTANCE.createSolver(csp).getSolutions().toList();
        assertThat(solutions).hasSize(1);
        assertThat(solutions.get(0).getValue(p)).hasValue(3);
        assertThat(solutions.get(0).getValue(q)).hasValue(4);
    }
}

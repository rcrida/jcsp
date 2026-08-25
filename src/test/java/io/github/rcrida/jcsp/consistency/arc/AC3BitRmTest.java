package io.github.rcrida.jcsp.consistency.arc;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.binary.BinaryComparatorConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryNotEqualsConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryTuplesConstraint;
import io.github.rcrida.jcsp.constraints.nary.GroundNogoodConstraint;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.EnumDomain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.constraints.binary.BinaryTuple;
import io.github.rcrida.jcsp.solver.examples.AustraliaMapColouringTest;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static io.github.rcrida.jcsp.solver.examples.AustraliaMapColouringTest.Colour.GREEN;
import static io.github.rcrida.jcsp.solver.examples.AustraliaMapColouringTest.Colour.RED;
import static io.github.rcrida.jcsp.solver.examples.AustraliaMapColouringTest.NT;
import static io.github.rcrida.jcsp.solver.examples.AustraliaMapColouringTest.WA;

/**
 * Mirrors {@link AC3Test}'s own scenarios against {@link AC3BitRm} (same assertions, same
 * expected outcomes -- {@link AC3BitRm} must be behaviourally identical to {@link AC3}, only
 * computed differently), plus bit+rm-specific coverage for multi-constraint arcs and residue
 * reuse/invalidation within a single {@link AC3BitRm#applyQueue} call.
 */
public class AC3BitRmTest {
    @Test
    void toString_isReadable() {
        assertThat(AC3BitRm.INSTANCE).hasToString("AC3bit+rm");
    }

    @Test
    void applyYEqualsX2() {
        val domain = IntRangeDomain.of(0, 10);
        val tuples = List.of(
                BinaryTuple.of(0, 0),
                BinaryTuple.of(1, 1),
                BinaryTuple.of(2, 4),
                BinaryTuple.of(3, 9)
        );
        val builder = ConstraintSatisfactionProblem.builder();
        Variable<Object> left = Variable.Factory.INSTANCE.create("left");
        Variable<Object> right = Variable.Factory.INSTANCE.create("right");
        builder.variableDomainEntry(left, domain);
        builder.variableDomainEntry(right, domain);
        builder.constraint(BinaryTuplesConstraint.of(left, right, Set.copyOf(tuples)));
        val problem = builder.build();
        val arcConstrainedProblem = AC3BitRm.INSTANCE.apply(problem).get();
        assertThat(arcConstrainedProblem.getVariableDomains().get(left)).isEqualTo(DiscreteDomain.of(0, 1, 2, 3));
        assertThat(arcConstrainedProblem.getVariableDomains().get(right)).isEqualTo(DiscreteDomain.of(0, 1, 4, 9));
    }

    @Test
    void emptyProblem() {
        val builder = ConstraintSatisfactionProblem.builder();
        val problem = builder.build();
        val arcConstrainedProblem = AC3BitRm.INSTANCE.apply(problem).get();
        assertThat(arcConstrainedProblem.getVariableDomains()).isEmpty();
    }

    @Test
    void singleVariableNoConstraints() {
        val domain = IntRangeDomain.of(0, 5);
        val builder = ConstraintSatisfactionProblem.builder();
        val variable = builder.createVariable("x", domain);
        builder.variableDomain(variable, domain);
        val problem = builder.build();
        val arcConstrainedProblem = AC3BitRm.INSTANCE.apply(problem).get();
        assertThat(arcConstrainedProblem.getVariableDomains().get(variable)).isEqualTo(domain);
    }

    @Test
    void applyAustraliaMapColouring() {
        val problem = AustraliaMapColouringTest.problem();
        val arcConstrainedProblem = AC3BitRm.INSTANCE.apply(problem).get();
        arcConstrainedProblem.getVariableDomains().keySet().forEach(state ->
                assertThat(arcConstrainedProblem.getVariableDomains().get(state)).isEqualTo(AustraliaMapColouringTest.DOMAIN));
    }

    @Test
    void applyAustraliaMapColouring_matchesAC3() {
        // Differential check: AC3BitRm's precomputed-bitset revise must reach byte-identical
        // domains to AC3's own naive per-pair scan for the same problem.
        val problem = AustraliaMapColouringTest.problem();
        val ac3Result = AC3.INSTANCE.apply(problem).get();
        val bitRmResult = AC3BitRm.INSTANCE.apply(problem).get();
        assertThat(bitRmResult.getVariableDomains()).isEqualTo(ac3Result.getVariableDomains());
    }

    @Test
    void reviseArc_revisedDomain() {
        val twoColours = new EnumDomain<>(EnumSet.of(RED, GREEN));
        val redOnly = new EnumDomain<>(EnumSet.of(RED));
        val problem = ConstraintSatisfactionProblem.builder()
                .variableDomain(WA, twoColours)
                .variableDomain(NT, redOnly)
                .notEqualsConstraint(WA, NT)
                .build();
        val result = AC3BitRm.INSTANCE.revise(problem, Arc.of(WA, NT));
        assertThat(((DiscreteDomain<?>) result.get().getDomain(WA)).toList()).isEqualTo(List.of(GREEN));
    }

    @Test
    void reviseArc_noRevisionNeeded() {
        val twoColours = new EnumDomain<>(EnumSet.of(RED, GREEN));
        val problem = ConstraintSatisfactionProblem.builder()
                .variableDomain(WA, twoColours)
                .variableDomain(NT, twoColours)
                .notEqualsConstraint(WA, NT)
                .build();
        val result = AC3BitRm.INSTANCE.revise(problem, Arc.of(WA, NT));
        assertThat(result.get().getDomain(WA)).isEqualTo(twoColours);
    }

    @Test
    void reviseArc_emptyDomain() {
        val redOnly = new EnumDomain<>(EnumSet.of(RED));
        val problem = ConstraintSatisfactionProblem.builder()
                .variableDomain(WA, redOnly)
                .variableDomain(NT, redOnly)
                .notEqualsConstraint(WA, NT)
                .build();
        assertThat(AC3BitRm.INSTANCE.revise(problem, Arc.of(WA, NT))).isEmpty();
    }

    @Test
    void intervalDomain_fromNotDiscrete_fallsBackToAC3NaiveRevise() {
        // Both IntervalDomain: no ValueIndex is built for either endpoint, so buildBitIndex records
        // a null support entry and revise() falls straight through to AC3's own naive scan.
        Variable<Double> x = Variable.Factory.INSTANCE.create("x_bitrm_a");
        Variable<Double> y = Variable.Factory.INSTANCE.create("y_bitrm_a");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(0.0, 10.0))
                .variableDomain(y, IntervalDomain.of(0.0, 10.0))
                .comparatorConstraint(x, Operator.LEQ, y)
                .build();
        var result = AC3BitRm.INSTANCE.apply(csp);
        assertThat(result).isPresent();
        assertThat(result.get().getDomain(x)).isEqualTo(IntervalDomain.of(0.0, 10.0));
        assertThat(result.get().getDomain(y)).isEqualTo(IntervalDomain.of(0.0, 10.0));
    }

    @Test
    void intervalDomain_toNotDiscrete_fallsBackToAC3NaiveRevise() {
        // Mixed: discrete Double from-variable, IntervalDomain to-variable -- again no support
        // entry for either arc direction, so both fall back to AC3's own naive scan.
        Variable<Double> d = Variable.Factory.INSTANCE.create("d_bitrm_b");
        Variable<Double> c = Variable.Factory.INSTANCE.create("c_bitrm_b");
        var discreteDoubleDomain = DiscreteDomain.of(2.0, 5.0);
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(d, discreteDoubleDomain)
                .variableDomain(c, IntervalDomain.of(0.0, 10.0))
                .comparatorConstraint(d, Operator.LEQ, c)
                .build();
        var result = AC3BitRm.INSTANCE.apply(csp);
        assertThat(result).isPresent();
        assertThat(result.get().getDomain(d)).isEqualTo(discreteDoubleDomain);
        assertThat(result.get().getDomain(c)).isEqualTo(IntervalDomain.of(0.0, 10.0));
    }

    @Test
    void explainConflict_bothSidesSingleton_returnsSoundReason() {
        val redOnly = new EnumDomain<>(EnumSet.of(RED));
        val problem = ConstraintSatisfactionProblem.builder()
                .variableDomain(WA, redOnly)
                .variableDomain(NT, redOnly)
                .notEqualsConstraint(WA, NT)
                .build();
        assertThat(AC3BitRm.INSTANCE.explainConflict(problem)).contains(GroundNogoodConstraint.of(Map.of(WA, RED, NT, RED)));
    }

    @Test
    void explainConflict_neitherSideSingleton_returnsEmptyOptional() {
        val domain = IntRangeDomain.of(0, 10);
        val tuples = List.of(BinaryTuple.of(0, 11));
        val builder = ConstraintSatisfactionProblem.builder();
        Variable<Object> left = Variable.Factory.INSTANCE.create("left_ec_bitrm");
        Variable<Object> right = Variable.Factory.INSTANCE.create("right_ec_bitrm");
        builder.variableDomainEntry(left, domain);
        builder.variableDomainEntry(right, domain);
        builder.constraint(BinaryTuplesConstraint.of(left, right, Set.copyOf(tuples)));
        val problem = builder.build();
        assertThat(AC3BitRm.INSTANCE.explainConflict(problem)).isEmpty();
    }

    @Test
    void explainConflict_noWipeout_returnsEmptyOptional() {
        val twoColours = new EnumDomain<>(EnumSet.of(RED, GREEN));
        val problem = ConstraintSatisfactionProblem.builder()
                .variableDomain(WA, twoColours)
                .variableDomain(NT, twoColours)
                .notEqualsConstraint(WA, NT)
                .build();
        assertThat(AC3BitRm.INSTANCE.explainConflict(problem)).isEmpty();
    }

    @Test
    void inconsistent() {
        val domain = IntRangeDomain.of(0, 10);
        val tuples = List.of(BinaryTuple.of(0, 11));
        val builder = ConstraintSatisfactionProblem.builder();
        Variable<Object> left = Variable.Factory.INSTANCE.create("left_bitrm");
        Variable<Object> right = Variable.Factory.INSTANCE.create("right_bitrm");
        builder.variableDomainEntry(left, domain);
        builder.variableDomainEntry(right, domain);
        builder.constraint(BinaryTuplesConstraint.of(left, right, Set.copyOf(tuples)));
        val problem = builder.build();
        assertThat(AC3BitRm.INSTANCE.apply(problem)).isEmpty();
    }

    @Test
    void applyWithReason_delegatesToApplyQueueWithReason() {
        val redOnly = new EnumDomain<>(EnumSet.of(RED));
        val problem = ConstraintSatisfactionProblem.builder()
                .variableDomain(WA, redOnly)
                .variableDomain(NT, redOnly)
                .notEqualsConstraint(WA, NT)
                .build();
        assertThat(AC3BitRm.INSTANCE.applyWithReason(problem, null).isInfeasible()).isTrue();
    }

    @Test
    void twoConstraintsOnSameArc_bothEnforced() {
        // Arc(x, y) carries two separate BinaryConstraint objects, exercising the per-constraint-
        // indexed support/residue arrays (arcConstraints.get(arc).size() == 2). Each constraint is
        // checked independently -- a value survives if it has *some* support under each constraint
        // separately, not necessarily the same support value for both -- so x=3 is only prunable
        // here because y's domain is narrow enough that the x<=y constraint alone has no support for
        // it (y's max is 2), not because the two constraints are reasoned about jointly.
        Variable<Integer> x = Variable.Factory.INSTANCE.create("x_two_bitrm");
        Variable<Integer> y = Variable.Factory.INSTANCE.create("y_two_bitrm");
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 3))
                .variableDomain(y, IntRangeDomain.of(1, 2))
                .notEqualsConstraint(x, y)
                .comparatorConstraint(x, Operator.LEQ, y)
                .build();
        val ac3Result = AC3.INSTANCE.apply(csp).get();
        val bitRmResult = AC3BitRm.INSTANCE.apply(csp).get();
        assertThat(bitRmResult.getVariableDomains()).isEqualTo(ac3Result.getVariableDomains());
        assertThat(((DiscreteDomain<Integer>) bitRmResult.getDomain(x)).toList()).containsExactlyInAnyOrder(1, 2);
        assertThat(bitRmResult.getDomain(y)).isEqualTo(IntRangeDomain.of(1, 2));
    }

    @Test
    void cyclicGraph_residueInvalidatedAcrossRepeatedRevisions_matchesAC3() {
        // X<=Y, X!=Y, Y<=Z: revising Arc(X,Y) can record a residue pointing at Y's current max; once
        // Arc(Y,Z)'s own revision later removes that same value from Y, Arc(X,Y) gets requeued and
        // must detect the stale residue and recompute -- both the residue-hit and residue-miss
        // branches are exercised within one applyQueue call. Correctness is checked differentially
        // against AC3 (the real oracle) rather than by hand-deriving expected domains: with each
        // constraint checked independently (see twoConstraintsOnSameArc_bothEnforced above), plain
        // arc consistency here only prunes x=3/y=3 (X<=Y has no support once Y<=Z caps Y at 2), not
        // the full x=1/y=2 a joint/search-level deduction would reach.
        Variable<Integer> x = Variable.Factory.INSTANCE.create("x_cyclic_bitrm");
        Variable<Integer> y = Variable.Factory.INSTANCE.create("y_cyclic_bitrm");
        Variable<Integer> z = Variable.Factory.INSTANCE.create("z_cyclic_bitrm");
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 3))
                .variableDomain(y, IntRangeDomain.of(1, 3))
                .variableDomain(z, IntRangeDomain.of(1, 2))
                .notEqualsConstraint(x, y)
                .comparatorConstraint(x, Operator.LEQ, y)
                .comparatorConstraint(y, Operator.LEQ, z)
                .build();
        val ac3Result = AC3.INSTANCE.apply(csp).get();
        val bitRmResult = AC3BitRm.INSTANCE.apply(csp).get();
        assertThat(bitRmResult.getVariableDomains()).isEqualTo(ac3Result.getVariableDomains());
        assertThat(((DiscreteDomain<Integer>) bitRmResult.getDomain(x)).toList()).containsExactlyInAnyOrder(1, 2);
        assertThat(((DiscreteDomain<Integer>) bitRmResult.getDomain(y)).toList()).containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void applyQueueWithReason_narrowsWithoutWipeout_requeuesOtherNeighbours() {
        // Mirrors AC3Test's identically-named test: a-b-c chain where revising arc(b, c) narrows b
        // (to {1}, not wiped), which must requeue b's other neighbour arc(a, b) -- exercises
        // applyQueueWithReason's own "narrowed, not wiped" branch specifically (applyQueue's parallel
        // branch is already covered elsewhere, e.g. cyclicGraph_... above, but applyQueueWithReason
        // is a separate traversal).
        Variable<Integer> a = Variable.Factory.INSTANCE.create("a_requeue_bitrm");
        Variable<Integer> b = Variable.Factory.INSTANCE.create("b_requeue_bitrm");
        Variable<Integer> c = Variable.Factory.INSTANCE.create("c_requeue_bitrm");
        val problem = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, IntRangeDomain.of(1, 3))
                .variableDomain(b, IntRangeDomain.of(1, 3))
                .variableDomain(c, IntRangeDomain.of(1, 1))
                .notEqualsConstraint(a, b)
                .comparatorConstraint(b, Operator.LEQ, c)
                .build();
        assertThat(AC3BitRm.INSTANCE.explainConflict(problem)).isEmpty();
        val result = AC3BitRm.INSTANCE.apply(problem).get();
        assertThat(((DiscreteDomain<Integer>) result.getDomain(b)).toList()).containsExactly(1);
        assertThat(((DiscreteDomain<Integer>) result.getDomain(a)).toList()).containsExactlyInAnyOrder(2, 3);
    }

    @Test
    void residue_hitOnASecondCallToTheSameUnchangedGraph() {
        // Residues are persisted per constraint graph (see AC3BitRm's own top-level Javadoc), not
        // rebuilt per call -- so a second apply() against the exact same, unchanged csp reuses
        // whatever residue the first call recorded, a genuine residue-hit rather than the
        // residue-miss-then-found path every other test above exercises. x != y never wipes out
        // (every value has some support), so the first call is guaranteed to populate a residue for
        // every from-index without ever deleting anything.
        Variable<Integer> x = Variable.Factory.INSTANCE.create("x_residuehit_bitrm");
        Variable<Integer> y = Variable.Factory.INSTANCE.create("y_residuehit_bitrm");
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 5))
                .variableDomain(y, IntRangeDomain.of(1, 5))
                .notEqualsConstraint(x, y)
                .build();
        val first = AC3BitRm.INSTANCE.apply(csp).get();
        val second = AC3BitRm.INSTANCE.apply(csp).get();
        assertThat(second.getVariableDomains()).isEqualTo(first.getVariableDomains());
    }

    @Test
    void supportScan_skipsPastANonLiveBitToALaterLiveOne() {
        // x's declared support for x=1 (under x != y) spans y-index 1 (value 2) and y-index 2 (value
        // 3), in that index order. Narrowing y's *current* domain to {1, 3} (excluding 2, but only
        // after declaredDomains already captured the full {1, 2, 3}) removes the first of those two
        // support bits from liveJ, forcing support[i]'s own set-bit scan to advance past it to find
        // the second -- support.nextSetBit(0) alone would return the non-live index-1 bit first.
        // Revises only the (x, y) direction directly (not apply(), which also revises (y, x) and
        // could pre-populate x's residue via that reverse pass' own multidirectional write, in
        // whichever order the queue happens to process arcs in -- reaching this exact scan only
        // when x's residue is still unset).
        Variable<Integer> x = Variable.Factory.INSTANCE.create("x_scan_bitrm");
        Variable<Integer> y = Variable.Factory.INSTANCE.create("y_scan_bitrm");
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 3))
                .variableDomain(y, IntRangeDomain.of(1, 3))
                .notEqualsConstraint(x, y)
                .build();
        val narrowed = csp.withDomain(y, DiscreteDomain.of(1, 3));
        val ac3Result = AC3.INSTANCE.revise(narrowed, Arc.of(x, y)).get();
        val bitRmResult = AC3BitRm.INSTANCE.revise(narrowed, Arc.of(x, y)).get();
        assertThat(bitRmResult.getVariableDomains()).isEqualTo(ac3Result.getVariableDomains());
    }
}

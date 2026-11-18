package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.NumericSetDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

class ExtremumPropagationTest {

    private static final Variable.Factory F = new Variable.Factory();
    private static final Variable<Integer> A = F.create("a_ep");
    private static final Variable<Integer> B = F.create("b_ep");
    private static final Variable<Integer> C = F.create("c_ep");
    private static final Variable<Integer> T = F.create("t_ep");

    @Test void eqCoverageEligible_allContiguousIntegerRanges_isFalse() {
        Map<Variable<?>, Domain<?>> domains = Map.of(A, IntRangeDomain.of(0, 3), B, IntRangeDomain.of(2, 5), T, IntRangeDomain.of(-1, 9));
        assertThat(ExtremumPropagation.eqCoverageEligible(Operator.EQ, List.of(A, B), T, domains)).isFalse();
    }

    @Test void eqCoverageEligible_gapInTarget_isTrue() {
        Map<Variable<?>, Domain<?>> domains = Map.of(A, IntRangeDomain.of(0, 3), B, IntRangeDomain.of(2, 5), T, DiscreteDomain.of(1, 4));
        assertThat(ExtremumPropagation.eqCoverageEligible(Operator.EQ, List.of(A, B), T, domains)).isTrue();
    }

    @Test void eqCoverageEligible_gapInAnOperand_isTrue() {
        Map<Variable<?>, Domain<?>> domains = Map.of(A, IntRangeDomain.of(0, 3), B, DiscreteDomain.of(2, 5), T, IntRangeDomain.of(0, 5));
        assertThat(ExtremumPropagation.eqCoverageEligible(Operator.EQ, List.of(A, B), T, domains)).isTrue();
    }

    @Test void eqCoverageEligible_nonIntegerValues_isTrue() {
        // {0.0, 1.0} has as many values as its integer span, but its values aren't integers, so
        // "no gaps" means nothing for it.
        Variable<Double> x = F.create("x_ep_double"), y = F.create("y_ep_double"), t = F.create("t_ep_double");
        Map<Variable<?>, Domain<?>> domains = Map.of(x, DiscreteDomain.of(0.0, 1.0), y, DiscreteDomain.of(0.0, 1.0), t, DiscreteDomain.of(0.0, 1.0));
        assertThat(ExtremumPropagation.eqCoverageEligible(Operator.EQ, List.of(x, y), t, domains)).isTrue();
    }

    @Test void narrowMaxEqCoverage_gappedDomains_keepsOnlySupportedValues() {
        // a={1,3,5}, b={0,3}: mLo = 1, so target's -1 is below every achievable maximum and its 2
        // is no operand's value; 3 and 5 remain, and a alone covers 5. b also covers 3, so a's own
        // threshold is 3 and its 1 survives under b's 3.
        var result = ExtremumPropagation.narrowMaxEqCoverage(
                List.of(new double[]{1, 3, 5}, new double[]{0, 3}), new double[]{-1, 2, 3, 5}).orElseThrow();
        assertThat(result.targetKept()).containsExactlyInAnyOrder(3.0, 5.0);
        assertThat(result.varsKept().get(0)).containsExactlyInAnyOrder(1.0, 3.0, 5.0);
        assertThat(result.varsKept().get(1)).containsExactlyInAnyOrder(0.0, 3.0);
    }

    /**
     * On contiguous integer ranges the bounds pass, run to its fixpoint, leaves exactly the values
     * that have a support: the same result the value-level coverage pass would reach.
     */
    @Test void boundsFixpointOnContiguousRanges_isGeneralizedArcConsistent() {
        Random random = new Random(12345);
        for (int round = 0; round < 2000; round++) {
            List<Variable<Integer>> operands = round % 2 == 0 ? List.of(A, B) : List.of(A, B, C);
            Map<Variable<?>, Domain<?>> domains = new HashMap<>();
            for (Variable<Integer> v : operands) domains.put(v, randomRange(random));
            domains.put(T, randomRange(random));
            boolean max = random.nextBoolean();
            Set<Variable<Integer>> operandSet = new LinkedHashSet<>(operands);
            Propagatable constraint = max
                    ? MaxVariableConstraint.of(operandSet, Operator.EQ, T)
                    : MinVariableConstraint.of(operandSet, Operator.EQ, T);

            Map<Variable<?>, Set<Integer>> expected = supportedValues(operands, domains, max);
            Optional<Map<Variable<?>, Domain<?>>> actual = fixpoint(constraint, domains);

            String description = (max ? "max" : "min") + " over " + domains;
            if (expected.values().stream().anyMatch(Set::isEmpty)) {
                assertThat(actual).as(description).isEmpty();
            } else {
                assertThat(actual).as(description).isPresent();
                for (Variable<?> v : expected.keySet()) {
                    assertThat(valuesOf(actual.get().get(v))).as(description + " at " + v).isEqualTo(expected.get(v));
                }
            }
        }
    }

    private static NumericSetDomain<Integer> randomRange(Random random) {
        int lo = random.nextInt(7) - 3;
        return IntRangeDomain.of(lo, lo + random.nextInt(5));
    }

    private static Optional<Map<Variable<?>, Domain<?>>> fixpoint(Propagatable constraint, Map<Variable<?>, Domain<?>> initial) {
        Map<Variable<?>, Domain<?>> domains = new HashMap<>(initial);
        while (true) {
            Optional<Map<Variable<?>, Domain<?>>> changed = constraint.propagate(domains);
            if (changed.isEmpty() || changed.get().values().stream().anyMatch(Domain::isEmpty)) return Optional.empty();
            if (changed.get().isEmpty()) return Optional.of(domains);
            domains.putAll(changed.get());
        }
    }

    private static Map<Variable<?>, Set<Integer>> supportedValues(List<Variable<Integer>> operands,
                                                                  Map<Variable<?>, Domain<?>> domains, boolean max) {
        Map<Variable<?>, Set<Integer>> supported = new HashMap<>();
        for (Variable<Integer> v : operands) supported.put(v, new TreeSet<>());
        supported.put(T, new TreeSet<>());
        Set<Integer> targetValues = valuesOf(domains.get(T));
        List<List<Integer>> tuples = new ArrayList<>(List.of(List.of()));
        for (Variable<Integer> v : operands) {
            List<List<Integer>> extended = new ArrayList<>();
            for (List<Integer> tuple : tuples) {
                for (int value : valuesOf(domains.get(v))) {
                    List<Integer> next = new ArrayList<>(tuple);
                    next.add(value);
                    extended.add(next);
                }
            }
            tuples = extended;
        }
        for (List<Integer> tuple : tuples) {
            int extremum = max
                    ? tuple.stream().mapToInt(Integer::intValue).max().orElseThrow()
                    : tuple.stream().mapToInt(Integer::intValue).min().orElseThrow();
            if (!targetValues.contains(extremum)) continue;
            supported.get(T).add(extremum);
            for (int i = 0; i < operands.size(); i++) supported.get(operands.get(i)).add(tuple.get(i));
        }
        return supported;
    }

    @SuppressWarnings("unchecked")
    private static Set<Integer> valuesOf(Domain<?> domain) {
        return new TreeSet<>(((DiscreteDomain<Integer>) domain).toList());
    }
}

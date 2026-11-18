package io.github.rcrida.jcsp.constraints.binary;

import io.github.rcrida.jcsp.variables.Variable;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SymmetricBinaryConstraintTest {
    private final Variable.Factory variables = new Variable.Factory();
    private final Variable<Integer> a = variables.create("a");
    private final Variable<Integer> b = variables.create("b");
    private final Variable<Integer> c = variables.create("c");

    /** Inherits the base class's equality untouched, which every Lombok-generated subclass only reaches after its own checks. */
    @SuperBuilder
    static class Plain extends SymmetricBinaryConstraint<Integer> {
        @Override
        public boolean isSatisfiedBy(Integer leftValue, Integer rightValue) {
            return true;
        }

        @Override
        public String getRelation() {
            return "plain";
        }
    }

    private Plain plain(Variable<Integer> left, Variable<Integer> right) {
        return Plain.builder().left(left).right(right).build();
    }

    @Test
    void equals_ignoresWhichVariableIsWhichSide() {
        assertThat(plain(a, b)).isEqualTo(plain(a, b)).isEqualTo(plain(b, a));
        assertThat(plain(a, b).hashCode()).isEqualTo(plain(b, a).hashCode());
    }

    @Test
    void equals_aDifferentPairIsNotEqual() {
        Plain ab = plain(a, b);
        assertThat(ab).isEqualTo(ab);
        assertThat(ab).isNotEqualTo(plain(a, c));  // same left, different right
        assertThat(ab).isNotEqualTo(plain(c, a));  // swapped left matches, swapped right does not
        assertThat(ab).isNotEqualTo(plain(c, c));
    }

    @Test
    void equals_otherTypesAreNotEqual() {
        assertThat(plain(a, b)).isNotEqualTo("ab");
        // A subclass's own canEqual refuses the base type, even over the same variables.
        assertThat(plain(a, b)).isNotEqualTo(BinaryNotEqualsConstraint.of(a, b));
        assertThat(BinaryNotEqualsConstraint.of(a, b)).isNotEqualTo(plain(a, b));
    }

    /**
     * A variable hashes as a multiple of its index, so summing the two hashes gave every pair with the
     * same index sum the same hash: an all-different's pairwise decomposition then crowded into a
     * handful of hash buckets.
     */
    @Test
    void hashCode_pairsWithTheSameIndexSumDoNotCollide() {
        int n = 200;
        Variable.Factory many = new Variable.Factory();
        @SuppressWarnings("unchecked")
        Variable<Integer>[] v = new Variable[n];
        for (int i = 0; i < n; i++) v[i] = many.create("v" + i);
        Set<Integer> hashes = new HashSet<>();
        int pairs = 0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                hashes.add(plain(v[i], v[j]).hashCode());
                pairs++;
            }
        }
        // Summed, these 19,900 pairs shared 397 hashes.
        assertThat(hashes.size()).isGreaterThan(pairs * 99 / 100);
    }
}

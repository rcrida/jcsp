package io.github.rcrida.jcsp.assignments;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NogoodStoreTest {

    private static final Variable.Factory VF = Variable.Factory.INSTANCE;
    private static final Variable<Integer> X = VF.create("x");
    private static final Variable<Integer> Y = VF.create("y");

    private static ConstraintSatisfactionProblem csp() {
        return ConstraintSatisfactionProblem.builder()
                .variableDomain(X, IntRangeDomain.of(1, 3))
                .variableDomain(Y, IntRangeDomain.of(1, 3))
                .build();
    }

    @Test
    void emptyStoreAppliesToCspUnchanged() {
        NogoodStore store = new NogoodStore();
        var csp = csp();
        assertThat(store.apply(csp)).isSameAs(csp);
    }

    @Test
    void sizeReflectsRecordedNogoods() {
        NogoodStore store = new NogoodStore();
        assertThat(store.size()).isZero();
        store.record(Map.of(X, 1));
        assertThat(store.size()).isEqualTo(1);
        store.record(Map.of(Y, 2));
        assertThat(store.size()).isEqualTo(2);
    }

    @Test
    void recordingTheSameNogoodTwiceDoesNotGrowSize() {
        // NogoodConstraint has value-based equality on its forbidden map, and the store is
        // Set-backed, so re-deriving the same nogood (e.g. independently in two branches) collapses
        // into one entry.
        NogoodStore store = new NogoodStore();
        store.record(Map.of(X, 1, Y, 2));
        store.record(Map.of(X, 1, Y, 2));
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void applyAddsNogoodConstraintToCsp() {
        NogoodStore store = new NogoodStore();
        store.record(Map.of(X, 1, Y, 2));
        var augmented = store.apply(csp());
        assertThat(augmented.getConstraints()).contains(NogoodConstraint.of(Map.of(X, 1, Y, 2)));
    }

    @Test
    void applyIsIdempotentAcrossRepeatedCalls() {
        // Simulates re-applying at every search node without needing to track what's already
        // present: applying twice in a row must not throw or duplicate anything visible.
        NogoodStore store = new NogoodStore();
        store.record(Map.of(X, 1));
        var csp = csp();
        var once = store.apply(csp);
        var twice = store.apply(once);
        assertThat(twice.getConstraints()).isEqualTo(once.getConstraints());
    }

    @Test
    void emptyNogoodIsIgnoredNotRecorded() {
        // An empty nogood would vacuously match every assignment (isSatisfiedBy returns false
        // whenever every one of its own entries matches, which is trivially true for zero
        // entries), pruning the entire search tree. record() must ignore it rather than store it.
        NogoodStore store = new NogoodStore();
        store.record(Map.of());
        assertThat(store.size()).isZero();
        var csp = csp();
        assertThat(store.apply(csp)).isSameAs(csp);
    }

    @Test
    void equalsAndHashCodeExcludeMutableSet() {
        NogoodStore a = new NogoodStore();
        NogoodStore b = new NogoodStore();
        a.record(Map.of(X, 1));
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}

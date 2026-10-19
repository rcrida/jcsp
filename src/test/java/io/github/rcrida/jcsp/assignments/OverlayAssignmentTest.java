package io.github.rcrida.jcsp.assignments;

import io.github.rcrida.jcsp.solver.listener.SolverListener;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OverlayAssignmentTest {
    private static final Variable.Factory F = Variable.Factory.INSTANCE;
    private final Variable<Integer> a = F.create("a");
    private final Variable<Integer> b = F.create("b");
    private final Variable<Integer> c = F.create("c");

    /** Two distinct variables that always hash alike, to reach a layer whose hash matches but whose variable does not. */
    private record Colliding(String name) implements Variable<Integer> {
        @Override
        public String getName() {
            return name;
        }

        @Override
        public int hashCode() {
            return 42;
        }
    }

    @Test
    void extendingCopiesNothingAndLeavesTheOriginalAlone() {
        Assignment base = Assignment.of(Map.of(a, 1, b, 2));
        Assignment extended = base.withValue(c, 3);

        assertThat(extended).isInstanceOf(OverlayAssignment.class);
        assertThat(extended.size()).isEqualTo(3);
        assertThat(extended.getValueOrNull(c)).isEqualTo(3);
        assertThat(extended.getValueOrNull(a)).isEqualTo(1);
        assertThat(base.size()).isEqualTo(2);
        assertThat(base.isAssigned(c)).isFalse();
    }

    @Test
    void lookupsFindEveryLayerAndTheBase() {
        Assignment chain = Assignment.of(Map.of(a, 1)).withValue(b, 2).withValue(c, 3);

        assertThat(chain.getValueOrNull(a)).isEqualTo(1);
        assertThat(chain.getValueOrNull(b)).isEqualTo(2);
        assertThat(chain.getValueOrNull(c)).isEqualTo(3);
        assertThat(chain.getValueOrNull(F.create("missing"))).isNull();
        assertThat(chain.isAssigned(b)).isTrue();
        assertThat(chain.isAssigned(F.create("missing"))).isFalse();
        assertThat(chain.getValue(c)).hasValue(3);
    }

    @Test
    void aLayerWhoseHashMatchesButWhoseVariableDoesNotIsSkipped() {
        Variable<Integer> first = new Colliding("first");
        Variable<Integer> second = new Colliding("second");
        Assignment chain = Assignment.empty().withValue(first, 1);

        assertThat(chain.isAssigned(second)).isFalse();
        assertThat(chain.getValueOrNull(second)).isNull();
        assertThat(chain.isAssigned(first)).isTrue();
        assertThat(chain.withValue(second, 2).getValueOrNull(second)).isEqualTo(2);
    }

    @Test
    void reassigningAVariableReplacesItsValueWithoutDuplicatingIt() {
        Assignment chain = Assignment.of(Map.of(a, 1)).withValue(b, 2);

        Assignment replaced = chain.withValue(b, 9);

        assertThat(replaced.size()).isEqualTo(2);
        assertThat(replaced.getValueOrNull(b)).isEqualTo(9);
        assertThat(chain.getValueOrNull(b)).isEqualTo(2);
        assertThat(Assignment.of(Map.of(a, 1)).withValue(a, 5)).isEqualTo(Assignment.of(Map.of(a, 5)));
    }

    @Test
    void aChainIsFlattenedOnceItIsMaxDepthLayersDeep() {
        Assignment chain = Assignment.empty();
        List<Variable<Integer>> variables = new ArrayList<>();
        for (int i = 0; i < OverlayAssignment.MAX_DEPTH; i++) {
            Variable<Integer> v = F.create("v" + i);
            variables.add(v);
            chain = chain.withValue(v, i);
            assertThat(chain).isInstanceOf(OverlayAssignment.class);
        }

        Assignment flattened = chain.withValue(F.create("one-more"), 99);

        assertThat(flattened).isNotInstanceOf(OverlayAssignment.class);
        assertThat(flattened.size()).isEqualTo(OverlayAssignment.MAX_DEPTH + 1);
        for (int i = 0; i < variables.size(); i++) {
            assertThat(flattened.getValueOrNull(variables.get(i))).isEqualTo(i);
        }
    }

    @Test
    void variablesIsASetViewOverLayersThenBase() {
        Assignment chain = Assignment.of(Map.of(a, 1)).withValue(b, 2).withValue(c, 3);
        Set<Variable<?>> variables = chain.variables();

        assertThat(variables).hasSize(3).containsExactlyInAnyOrder(a, b, c);
        assertThat(variables.contains(b)).isTrue();
        assertThat(variables.contains(F.create("missing"))).isFalse();
        assertThat(variables.contains("not a variable")).isFalse();
        assertThat(new HashSet<>(variables)).isEqualTo(Set.of(a, b, c));
    }

    @Test
    void entriesCoverEveryLayerAndTheBaseExactlyOnce() {
        Assignment chain = Assignment.of(Map.of(a, 1)).withValue(b, 2).withValue(c, 3);

        assertThat(chain.entries()).extracting(Map.Entry::getKey).containsExactlyInAnyOrder(a, b, c);
        assertThat(chain.entryStream().mapToInt(e -> (Integer) e.getValue()).sum()).isEqualTo(6);
        assertThatThrownBy(() -> chain.entries().iterator().next().setValue(9))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void iteratorsAreExhaustedAfterTheBase() {
        var iterator = Assignment.of(Map.of(a, 1)).withValue(b, 2).variables().iterator();

        iterator.next();
        iterator.next();

        assertThat(iterator.hasNext()).isFalse();
        assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void equalsAndHashCodeAreByEntriesWhateverTheImplementation() {
        Assignment overlay = Assignment.of(Map.of(a, 1)).withValue(b, 2);
        Assignment map = Assignment.of(Map.of(a, 1, b, 2));

        assertThat(overlay).isEqualTo(map).hasSameHashCodeAs(map);
        assertThat(map).isEqualTo(overlay);
        assertThat(overlay).isNotEqualTo(Assignment.of(Map.of(a, 1, b, 3)));
        assertThat(overlay).isNotEqualTo(Assignment.of(Map.of(a, 1)));
        assertThat(overlay).isNotEqualTo("a string");
        assertThat(overlay).isEqualTo(overlay);
        assertThat(overlay.withValue(c, 3)).isNotEqualTo(overlay);
    }

    @Test
    void toStringIsSortedByVariableName() {
        assertThat(Assignment.of(Map.of(c, 3)).withValue(a, 1).withValue(b, 2)).asString().isEqualTo("{a=1, b=2, c=3}");
    }

    @Test
    void extendingCountsANodeAndFiresTheListenerWithTheNewAssignment() {
        Statistics statistics = new Statistics();
        List<Assignment> seen = new ArrayList<>();
        Assignment base = Assignment.builder().statistics(statistics)
                .listener(new SolverListener() {
                    @Override
                    public void onNodeExplored(Variable<?> variable, Object value, Assignment assignment) {
                        seen.add(assignment);
                    }
                }).build();

        Assignment next = base.withValue(a, 1).withValue(b, 2);

        assertThat(statistics.getNodesExplored()).isEqualTo(2);
        assertThat(seen).hasSize(2);
        assertThat(seen.getLast()).isSameAs(next);
        assertThat(next.getStatistics()).isSameAs(statistics);
        assertThat(next.listener()).isSameAs(base.listener());
        assertThat(next.cancellation()).isSameAs(base.cancellation());
    }

    @Test
    void derivedAssignmentsKeepTheEntriesAndTheSolveContext() {
        Statistics statistics = new Statistics();
        Assignment chain = Assignment.builder().statistics(statistics).build().withValue(a, 1).withValue(b, 2);

        Assignment withMore = chain.withValues(Map.of(c, 3));
        Assignment merged = chain.merge(Assignment.of(Map.of(c, 3)));
        Assignment rebuilt = chain.toBuilder().build();

        assertThat(withMore).isEqualTo(Assignment.of(Map.of(a, 1, b, 2, c, 3)));
        assertThat(merged).isEqualTo(withMore);
        assertThat(rebuilt).isEqualTo(chain);
        assertThat(withMore.getStatistics()).isSameAs(statistics);
        assertThat(rebuilt.getStatistics()).isSameAs(statistics);
    }
}

package io.github.rcrida.jcsp.variables;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VariableMapTest {
    private final Variable.Factory factory = new Variable.Factory();

    private List<Variable<Integer>> variables(int count) {
        List<Variable<Integer>> variables = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            variables.add(factory.create("v" + i));
        }
        return variables;
    }

    @Test
    void anEmptyMapHasNothing() {
        VariableMap<String> map = new VariableMap<>();
        Variable<Integer> v = variables(1).get(0);

        assertThat(map).isEmpty();
        assertThat(map.get(v)).isNull();
        assertThat(map.containsKey(v)).isFalse();
        assertThat(map.entrySet()).isEmpty();
    }

    @Test
    void aKeyThatIsNotAVariableIsAbsent() {
        VariableMap<String> map = new VariableMap<>();
        map.put(variables(1).get(0), "x");

        assertThat(map.get("v0")).isNull();
        assertThat(map.containsKey(7)).isFalse();
    }

    @Test
    void putThenGet_andReplacingReturnsThePreviousValueWithoutGrowing() {
        VariableMap<String> map = new VariableMap<>();
        Variable<Integer> v = variables(1).get(0);

        assertThat(map.put(v, "first")).isNull();
        assertThat(map.put(v, "second")).isEqualTo("first");

        assertThat(map).hasSize(1);
        assertThat(map.get(v)).isEqualTo("second");
        assertThat(map.containsKey(v)).isTrue();
    }

    @Test
    void growsPastItsInitialCapacityKeepingEveryEntryInInsertionOrder() {
        VariableMap<Integer> map = new VariableMap<>();
        List<Variable<Integer>> variables = variables(40);
        for (int i = 0; i < variables.size(); i++) {
            map.put(variables.get(i), i);
        }

        assertThat(map).hasSize(40);
        for (int i = 0; i < variables.size(); i++) {
            assertThat(map.get(variables.get(i))).isEqualTo(i);
        }
        assertThat(List.copyOf(map.keySet())).isEqualTo(variables);
    }

    @Test
    void variablesWhoseIndicesShareASlotAreEachFoundAndAnAbsentOneProbesPastThem() {
        // A fresh map's table has 8 slots, so indices 0, 8 and 16 all start at slot 0.
        List<Variable<Integer>> variables = variables(17);
        Variable<Integer> first = variables.get(0);
        Variable<Integer> second = variables.get(8);
        Variable<Integer> absent = variables.get(16);
        VariableMap<String> map = new VariableMap<>();
        map.put(first, "first");
        map.put(second, "second");

        assertThat(map.get(first)).isEqualTo("first");
        assertThat(map.get(second)).isEqualTo("second");
        assertThat(map.get(absent)).isNull();
    }

    @Test
    void aVariableFromAnotherFactoryWithTheSameIndexIsAbsent() {
        Variable<Integer> own = variables(1).get(0);
        Variable<Integer> foreign = new Variable.Factory().create("v0");
        VariableMap<String> map = new VariableMap<>();
        map.put(own, "own");

        assertThat(foreign.getIndex()).isEqualTo(own.getIndex());
        assertThat(map.get(foreign)).isNull();
        assertThat(map.containsKey(foreign)).isFalse();
    }

    @Test
    void aVariableNoFactoryIssuedIsAbsent() {
        VariableMap<String> map = new VariableMap<>();
        map.put(variables(1).get(0), "own");
        Variable<Integer> unindexed = () -> "unindexed";

        assertThat(map.get(unindexed)).isNull();
    }

    @Test
    void theCopyConstructorHoldsTheEntriesInTheirOrder() {
        for (int count : new int[]{0, 1, 3, 4, 5, 9, 33}) {
            Variable.Factory own = new Variable.Factory();
            Map<Variable<?>, Integer> source = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                source.put(own.create("c" + i), i);
            }

            VariableMap<Integer> copy = new VariableMap<>(source);

            assertThat(copy).isEqualTo(source);
            assertThat(List.copyOf(copy.keySet())).isEqualTo(List.copyOf(source.keySet()));
        }
    }

    @Test
    void putAllTakesAnotherMapAndOverwritesWhatIsAlreadyThere() {
        List<Variable<Integer>> variables = variables(3);
        VariableMap<String> map = new VariableMap<>();
        map.put(variables.get(0), "old");
        Map<Variable<?>, String> more = new LinkedHashMap<>();
        more.put(variables.get(0), "new");
        more.put(variables.get(1), "b");
        VariableMap<String> other = new VariableMap<>();
        other.put(variables.get(2), "c");

        map.putAll(more);
        map.putAll(other);

        assertThat(map).hasSize(3);
        assertThat(map.get(variables.get(0))).isEqualTo("new");
        assertThat(List.copyOf(map.keySet())).isEqualTo(variables);
    }

    @Test
    void forEachVisitsEntriesInInsertionOrder() {
        List<Variable<Integer>> variables = variables(6);
        VariableMap<Integer> map = new VariableMap<>();
        for (int i = variables.size() - 1; i >= 0; i--) {
            map.put(variables.get(i), i);
        }
        List<Integer> seen = new ArrayList<>();
        map.forEach((variable, value) -> seen.add(value));

        assertThat(seen).containsExactly(5, 4, 3, 2, 1, 0);
    }

    @Test
    void theEntryIteratorStopsAtTheEndAndCannotRemove() {
        VariableMap<String> map = new VariableMap<>();
        map.put(variables(1).get(0), "only");
        Iterator<Map.Entry<Variable<?>, String>> entries = map.entrySet().iterator();

        assertThat(entries.hasNext()).isTrue();
        assertThat(entries.next().getValue()).isEqualTo("only");
        assertThat(entries.hasNext()).isFalse();
        assertThatThrownBy(entries::next).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void entriesCannotBeRemoved() {
        List<Variable<Integer>> variables = variables(2);
        VariableMap<String> map = new VariableMap<>();
        map.put(variables.get(0), "a");

        assertThatThrownBy(() -> map.remove(variables.get(0))).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(map::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> map.keySet().remove(variables.get(0))).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void behavesAsTheLinkedHashMapItReplaces() {
        Random rng = new Random(17);
        for (int trial = 0; trial < 200; trial++) {
            Variable.Factory own = new Variable.Factory();
            List<Variable<Integer>> pool = new ArrayList<>();
            for (int i = 0; i < 1 + rng.nextInt(60); i++) {
                pool.add(own.create("t" + i));
            }
            Map<Variable<?>, Integer> oracle = new LinkedHashMap<>();
            VariableMap<Integer> actual = new VariableMap<>();

            for (int step = 0; step < 80; step++) {
                Variable<Integer> variable = pool.get(rng.nextInt(pool.size()));
                int value = rng.nextInt(1000);
                assertThat(actual.put(variable, value)).isEqualTo(oracle.put(variable, value));
                Variable<Integer> probe = pool.get(rng.nextInt(pool.size()));
                assertThat(actual.get(probe)).isEqualTo(oracle.get(probe));
                assertThat(actual.containsKey(probe)).isEqualTo(oracle.containsKey(probe));
            }

            assertThat(actual).hasSize(oracle.size());
            assertThat(List.copyOf(actual.entrySet())).isEqualTo(List.copyOf(oracle.entrySet()));
            assertThat(actual).isEqualTo(oracle);
            assertThat(oracle).isEqualTo(actual);
            assertThat(actual.hashCode()).isEqualTo(oracle.hashCode());
        }
    }
}

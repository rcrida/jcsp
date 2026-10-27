package io.github.rcrida.jcsp;

import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainMapTest {

    private static final Variable.Factory VF = new Variable.Factory();

    private static Domain<?> domain(int upper) {
        return IntRangeDomain.of(0, upper);
    }

    private static List<Variable<?>> variables(String prefix, int count) {
        List<Variable<?>> variables = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            variables.add(VF.create(prefix + i));
        }
        return variables;
    }

    /** {@code entries} as a map that iterates in variable index order, which is how a {@link DomainMap} does. */
    private static Map<Variable<?>, Domain<?>> byIndex(Map<Variable<?>, Domain<?>> entries) {
        Map<Variable<?>, Domain<?>> sorted = new TreeMap<>(Comparator.<Variable<?>>comparingInt(Variable::getIndex));
        sorted.putAll(entries);
        return sorted;
    }

    private static Map<Variable<?>, Domain<?>> declared(List<Variable<?>> variables, Random rng) {
        Map<Variable<?>, Domain<?>> declared = new LinkedHashMap<>();
        for (Variable<?> variable : variables) {
            declared.put(variable, domain(1 + rng.nextInt(9)));
        }
        return byIndex(declared);
    }

    private static void assertSameAs(Map<Variable<?>, Domain<?>> oracle, DomainMap actual, List<Variable<?>> probes) {
        assertThat(actual.size()).isEqualTo(oracle.size());
        assertThat(actual.isEmpty()).isEqualTo(oracle.isEmpty());
        assertThat(List.copyOf(actual.keySet())).isEqualTo(List.copyOf(oracle.keySet()));
        assertThat(List.copyOf(actual.values())).isEqualTo(List.copyOf(oracle.values()));
        assertThat(List.copyOf(actual.entrySet())).isEqualTo(List.copyOf(oracle.entrySet()));
        List<Map.Entry<Variable<?>, Domain<?>>> viaForEach = new ArrayList<>();
        actual.forEach((variable, domain) -> viaForEach.add(Map.entry(variable, domain)));
        assertThat(viaForEach).isEqualTo(List.copyOf(oracle.entrySet()));
        for (Variable<?> probe : probes) {
            assertThat(actual.get(probe)).isEqualTo(oracle.get(probe));
            assertThat(actual.containsKey(probe)).isEqualTo(oracle.containsKey(probe));
            assertThat(actual.keySet().contains(probe)).isEqualTo(oracle.containsKey(probe));
        }
        assertThat(actual).isEqualTo(oracle);
        assertThat(oracle).isEqualTo(actual);
        assertThat(actual.hashCode()).isEqualTo(oracle.hashCode());
    }

    @Test
    void behavesAsTheLinkedHashMapItReplaces() {
        Random rng = new Random(31);
        for (int trial = 0; trial < 300; trial++) {
            List<Variable<?>> known = variables("k" + trial + "_", rng.nextInt(12));
            List<Variable<?>> strangers = variables("s" + trial + "_", 4);
            List<Variable<?>> probes = new ArrayList<>(known);
            probes.addAll(strangers);
            Map<Variable<?>, Domain<?>> oracle = declared(known, rng);
            DomainMap actual = DomainMap.of(oracle);
            assertSameAs(oracle, actual, probes);

            for (int step = 0; step < 12; step++) {
                // Mostly existing variables, sometimes a new one: it takes its place by index.
                Variable<?> target = !probes.isEmpty() && rng.nextInt(5) > 0 && !known.isEmpty()
                        ? known.get(rng.nextInt(known.size())) : strangers.get(rng.nextInt(strangers.size()));
                if (rng.nextBoolean()) {
                    Domain<?> narrowed = domain(rng.nextInt(9));
                    oracle = byIndex(oracle);
                    oracle.put(target, narrowed);
                    actual = actual.with(target, narrowed);
                } else {
                    Map<Variable<?>, Domain<?>> updates = new LinkedHashMap<>();
                    for (int i = 0, count = 1 + rng.nextInt(3); i < count; i++) {
                        updates.put(rng.nextInt(4) == 0 ? strangers.get(rng.nextInt(strangers.size()))
                                : known.isEmpty() ? strangers.get(0) : known.get(rng.nextInt(known.size())), domain(rng.nextInt(9)));
                    }
                    oracle = byIndex(oracle);
                    oracle.putAll(updates);
                    actual = actual.withAll(updates);
                }
                assertSameAs(oracle, actual, probes);
            }
        }
    }

    @Test
    void derivingLeavesTheOriginalUnchanged() {
        Variable<Integer> a = VF.create("keepA");
        Variable<Integer> b = VF.create("keepB");
        DomainMap original = DomainMap.of(new LinkedHashMap<>(Map.of(a, domain(5), b, domain(5))));

        DomainMap derived = original.with(a, domain(1));

        assertThat(original.get(a)).isEqualTo(domain(5));
        assertThat(derived.get(a)).isEqualTo(domain(1));
        assertThat(derived.get(b)).isSameAs(original.get(b));
    }

    @Test
    void aVariableNoFactoryIssuedIsRejected() {
        Variable<Integer> unindexed = () -> "unindexed";

        assertThatThrownBy(() -> DomainMap.of(Map.of(unindexed, domain(1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not issued by a Variable.Factory");
    }

    @Test
    void variablesFromDifferentFactoriesSharingAnIndexAreRejected() {
        Variable<Integer> first = new Variable.Factory().create("first");
        Variable<Integer> second = new Variable.Factory().create("second");

        assertThatThrownBy(() -> DomainMap.of(new LinkedHashMap<>(Map.of(first, domain(1), second, domain(1)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("share index 0");
    }

    @Test
    void aVariableIsFoundOnlyIfItIsExactlyOneOfTheMapsOwn() {
        Variable.Factory factory = new Variable.Factory();
        Variable<Integer> own = factory.create("own");
        Variable<Integer> beyondTheEnd = factory.create("beyond");
        Variable<Integer> foreign = new Variable.Factory().create("foreign");
        DomainMap map = DomainMap.of(Map.of(own, domain(1)));

        assertThat(map.get(own)).isEqualTo(domain(1));
        Variable<Integer> unindexed = () -> "unindexed";

        assertThat(map.containsKey(foreign)).isFalse();
        assertThat(map.containsKey(beyondTheEnd)).isFalse();
        assertThat(map.containsKey(unindexed)).isFalse();
    }

    @Test
    void aMapOverASparseSetOfIndicesSkipsTheGaps() {
        Variable.Factory factory = new Variable.Factory();
        Variable<Integer> first = factory.create("first");
        factory.create("skipped");
        Variable<Integer> third = factory.create("third");
        DomainMap map = DomainMap.of(new LinkedHashMap<>(Map.of(third, domain(3), first, domain(1))));
        List<Variable<?>> seen = new ArrayList<>();
        map.forEach((variable, domain) -> seen.add(variable));

        assertThat(map).hasSize(2);
        assertThat(List.copyOf(map.keySet())).containsExactly(first, third);
        assertThat(seen).containsExactly(first, third);
        assertThat(map.hashCode()).isEqualTo(Map.of(first, domain(1), third, domain(3)).hashCode());
    }

    @Test
    void anAlreadyBuiltMapIsNotCopied() {
        DomainMap map = DomainMap.of(new LinkedHashMap<>(Map.of(VF.create("same"), domain(2))));

        assertThat(DomainMap.of(map)).isSameAs(map);
    }

    @Test
    void mapsDerivedFromTheSameOneCompareTheirDomains() {
        Variable<Integer> a = VF.create("cmpA");
        Variable<Integer> b = VF.create("cmpB");
        DomainMap base = DomainMap.of(new LinkedHashMap<>(Map.of(a, domain(5), b, domain(5))));

        assertThat(base.with(a, domain(1))).isEqualTo(base.with(a, domain(1)));
        assertThat(base.with(a, domain(1))).isNotEqualTo(base.with(a, domain(2)));
        assertThat(base.with(a, domain(1))).isNotEqualTo(base.with(b, domain(1)));
    }

    @Test
    void isNotEqualToAnythingThatIsNotAMapOfTheSameEntries() {
        Variable<Integer> a = VF.create("neqA");
        DomainMap map = DomainMap.of(new LinkedHashMap<>(Map.of(a, domain(5))));

        assertThat(map).isNotEqualTo("not a map");
        assertThat(map).isNotEqualTo(Map.of(a, domain(4)));
        assertThat(map).isNotEqualTo(Map.of());
    }

    @Test
    void anEmptyMapIsEmpty() {
        DomainMap empty = DomainMap.of(new LinkedHashMap<>());

        assertThat(empty).isEmpty();
        assertThat(empty.keySet()).isEmpty();
        assertThat(empty.values()).isEmpty();
        assertThat(empty.entrySet()).isEmpty();
        assertThat(empty.get("anything")).isNull();
        assertThat(empty.containsKey("anything")).isFalse();
    }

    @Test
    void cannotBeModified() {
        Variable<Integer> a = VF.create("roA");
        DomainMap map = DomainMap.of(new LinkedHashMap<>(Map.of(a, domain(5))));

        assertThatThrownBy(() -> map.put(a, domain(1))).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> map.remove(a)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(map::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> map.keySet().remove(a)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> map.values().clear()).isInstanceOf(UnsupportedOperationException.class);
        Iterator<Map.Entry<Variable<?>, Domain<?>>> entries = map.entrySet().iterator();
        entries.next();
        assertThatThrownBy(entries::remove).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void iteratorsStopAtTheEnd() {
        DomainMap map = DomainMap.of(new LinkedHashMap<>(Map.of(VF.create("endA"), domain(5))));

        for (Iterator<?> iterator : List.of(map.keySet().iterator(), map.values().iterator(), map.entrySet().iterator())) {
            iterator.next();
            assertThat(iterator.hasNext()).isFalse();
            assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);
        }
    }
}

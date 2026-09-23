package io.github.rcrida.jcsp.consistency;

import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DomainOverlayTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    private static Map<Variable<?>, Domain<?>> base(Variable<?> a, Domain<?> da, Variable<?> b, Domain<?> db) {
        Map<Variable<?>, Domain<?>> map = new LinkedHashMap<>();
        map.put(a, da);
        map.put(b, db);
        return map;
    }

    @Test
    void get_variableWithAnUpdate_readsTheUpdate() {
        Variable<Integer> x = F.create("x"), y = F.create("y");
        Map<Variable<?>, Domain<?>> updates = new HashMap<>();
        updates.put(x, IntRangeDomain.of(2, 3));
        Map<Variable<?>, Domain<?>> overlay =
                DomainOverlay.of(base(x, IntRangeDomain.of(1, 9), y, IntRangeDomain.of(4, 6)), updates);
        assertThat(overlay.get(x)).isEqualTo(IntRangeDomain.of(2, 3));
    }

    @Test
    void get_variableWithoutAnUpdate_fallsThroughToTheBase() {
        Variable<Integer> x = F.create("x"), y = F.create("y");
        Map<Variable<?>, Domain<?>> updates = new HashMap<>();
        updates.put(x, IntRangeDomain.of(2, 3));
        Map<Variable<?>, Domain<?>> overlay =
                DomainOverlay.of(base(x, IntRangeDomain.of(1, 9), y, IntRangeDomain.of(4, 6)), updates);
        assertThat(overlay.get(y)).isEqualTo(IntRangeDomain.of(4, 6));
    }

    @Test
    void get_variableInNeitherMap_isNull() {
        Variable<Integer> x = F.create("x"), y = F.create("y"), absent = F.create("absent");
        Map<Variable<?>, Domain<?>> overlay =
                DomainOverlay.of(base(x, IntRangeDomain.of(1, 9), y, IntRangeDomain.of(4, 6)), new HashMap<>());
        assertThat(overlay.get(absent)).isNull();
    }

    /** The updates map is read live, so narrowing after construction is visible through the view. */
    @Test
    void get_updateRecordedAfterConstruction_isVisible() {
        Variable<Integer> x = F.create("x"), y = F.create("y");
        Map<Variable<?>, Domain<?>> updates = new HashMap<>();
        Map<Variable<?>, Domain<?>> overlay =
                DomainOverlay.of(base(x, IntRangeDomain.of(1, 9), y, IntRangeDomain.of(4, 6)), updates);
        assertThat(overlay.get(x)).isEqualTo(IntRangeDomain.of(1, 9));
        updates.put(x, IntRangeDomain.of(7, 7));
        assertThat(overlay.get(x)).isEqualTo(IntRangeDomain.of(7, 7));
    }

    @Test
    void entrySet_mergesUpdatesOverTheBase() {
        Variable<Integer> x = F.create("x"), y = F.create("y");
        Map<Variable<?>, Domain<?>> updates = new HashMap<>();
        updates.put(x, IntRangeDomain.of(2, 3));
        Map<Variable<?>, Domain<?>> overlay =
                DomainOverlay.of(base(x, IntRangeDomain.of(1, 9), y, IntRangeDomain.of(4, 6)), updates);
        assertThat(overlay).containsOnly(
                Map.entry(x, IntRangeDomain.of(2, 3)),
                Map.entry(y, IntRangeDomain.of(4, 6)));
    }
}

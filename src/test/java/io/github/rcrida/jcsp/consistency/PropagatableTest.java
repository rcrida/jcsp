package io.github.rcrida.jcsp.consistency;

import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PropagatableTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    // --- addIfSingleton ---

    @Test
    void addIfSingleton_singletonDomain_recordsItsValue() {
        Variable<Integer> x = F.create("x");
        Map<Variable<?>, Object> reason = new HashMap<>();
        Propagatable.addIfSingleton(IntRangeDomain.of(5, 5), x, reason);
        assertThat(reason).containsEntry(x, 5);
    }

    @Test
    void addIfSingleton_nonSingletonDomain_leavesReasonUnchanged() {
        Variable<Integer> x = F.create("x");
        Map<Variable<?>, Object> reason = new HashMap<>();
        Propagatable.addIfSingleton(IntRangeDomain.of(1, 3), x, reason);
        assertThat(reason).isEmpty();
    }

    // --- allSingletonReason ---

    @Test
    void allSingletonReason_everyVariableSingleton_returnsFullReason() {
        Variable<Integer> x = F.create("y"), y = F.create("z");
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                x, IntRangeDomain.of(2, 2), y, IntRangeDomain.of(3, 3));
        assertThat(Propagatable.allSingletonReason(Set.of(x, y), domains))
                .isEqualTo(Map.of(x, 2, y, 3));
    }

    @Test
    void allSingletonReason_oneVariableNotSingleton_returnsEmpty() {
        Variable<Integer> x = F.create("w"), y = F.create("v");
        var domains = Map.<Variable<?>, io.github.rcrida.jcsp.domains.Domain<?>>of(
                x, IntRangeDomain.of(2, 2), y, IntRangeDomain.of(1, 3));
        assertThat(Propagatable.allSingletonReason(Set.of(x, y), domains)).isEmpty();
    }
}

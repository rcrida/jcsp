package io.github.rcrida.jcsp.solver.backtrackingsearch.order;

import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PhaseMemoryTest {
    private static final Variable.Factory F = Variable.Factory.INSTANCE;
    private final Variable<Integer> x = F.create("phase_x");
    private final Variable<Integer> y = F.create("phase_y");

    @Test
    void unknownVariable_leavesOrderUntouched() {
        assertThat(new PhaseMemory().prioritise(x, List.of(1, 2, 3))).containsExactly(1, 2, 3);
    }

    @Test
    void rememberedValue_movesToTheFrontKeepingTheRestInOrder() {
        var memory = new PhaseMemory();
        memory.recordIfDeepest(Map.of(x, 3));
        assertThat(memory.prioritise(x, List.of(1, 2, 3, 4))).containsExactly(3, 1, 2, 4);
    }

    @Test
    void rememberedValueAlreadyFirst_returnsTheSameListWithoutCopying() {
        var memory = new PhaseMemory();
        memory.recordIfDeepest(Map.of(x, 1));
        List<Object> values = List.of(1, 2, 3);
        assertThat(memory.prioritise(x, values)).isSameAs(values);
    }

    @Test
    void rememberedValuePrunedSincePropagation_returnsTheSameList() {
        var memory = new PhaseMemory();
        memory.recordIfDeepest(Map.of(x, 9));
        List<Object> values = List.of(1, 2, 3);
        assertThat(memory.prioritise(x, values)).isSameAs(values);
    }

    @Test
    void shallowerAssignment_doesNotOverwriteTheDeepestPath() {
        var memory = new PhaseMemory();
        memory.recordIfDeepest(Map.of(x, 3, y, 7));
        memory.recordIfDeepest(Map.of(x, 1)); // shallower: ignored outright
        assertThat(memory.prioritise(x, List.of(1, 2, 3))).containsExactly(3, 1, 2);
        assertThat(memory.prioritise(y, List.of(5, 7))).containsExactly(7, 5);
    }

    @Test
    void strictlyDeeperAssignment_replacesTheRememberedValues() {
        var memory = new PhaseMemory();
        memory.recordIfDeepest(Map.of(x, 3));
        memory.recordIfDeepest(Map.of(x, 1, y, 5));
        assertThat(memory.prioritise(x, List.of(1, 2, 3))).containsExactly(1, 2, 3);
        assertThat(memory.prioritise(y, List.of(4, 5))).containsExactly(5, 4);
    }

    @Test
    void equalDepthAssignment_isIgnoredSoOnlyGenuineProgressCounts() {
        var memory = new PhaseMemory();
        memory.recordIfDeepest(Map.of(x, 3));
        memory.recordIfDeepest(Map.of(x, 2));
        assertThat(memory.prioritise(x, List.of(1, 2, 3))).containsExactly(3, 1, 2);
    }

    // --- recordSolution: the optimization counterpart, ordered by objective rather than depth ---

    @Test
    void recordSolution_overwritesAtEqualSize_unlikeRecordIfDeepest() {
        // The distinction that makes a separate method necessary: every solution is complete, so
        // successive ones always tie on size. recordIfDeepest would keep the first one forever,
        // which for branch-and-bound means guiding by the *worst* solution ever found.
        var memory = new PhaseMemory();
        memory.recordSolution(Map.of(x, 3, y, 4));
        memory.recordSolution(Map.of(x, 1, y, 5));
        assertThat(memory.prioritise(x, List.of(1, 2, 3))).containsExactly(1, 2, 3);
        assertThat(memory.prioritise(y, List.of(4, 5))).containsExactly(5, 4);
    }

    @Test
    void recordIfDeepest_atTheSameSize_keepsTheFirst() {
        // The contrast, stated directly: identical calls through the depth-gated entry point keep
        // the original values, which is correct for satisfaction and wrong for optimization.
        var memory = new PhaseMemory();
        memory.recordIfDeepest(Map.of(x, 3, y, 4));
        memory.recordIfDeepest(Map.of(x, 1, y, 5));
        assertThat(memory.prioritise(x, List.of(1, 2, 3))).containsExactly(3, 1, 2);
        assertThat(memory.prioritise(y, List.of(4, 5))).containsExactly(4, 5);
    }
}

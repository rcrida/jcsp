package io.github.rcrida.jcsp.constraints.nary;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TarjanSCCTest {

    /** {@code 0 -> 1 -> ... -> n-1}, plus {@code n-1 -> 0} when {@code closed}. */
    private static List<List<Integer>> chain(int n, boolean closed) {
        List<List<Integer>> graph = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            graph.add(i + 1 < n ? List.of(i + 1) : closed ? List.of(0) : List.of());
        }
        return graph;
    }

    @Test
    void smallGraph_componentsNumberedInTheOrderTheyComplete() {
        // 0 <-> 1 -> 2 <-> 3, and 4 alone: {2,3} completes first, then {0,1}, then {4}.
        List<List<Integer>> graph = List.of(List.of(1), List.of(0, 2), List.of(3), List.of(2), List.of());

        assertThat(TarjanSCC.compute(graph, 5)).containsExactly(1, 1, 0, 0, 2);
    }

    @Test
    void aPathThroughEveryNode_isSearchedWithoutRecursing() {
        // Each node of an open chain is its own component, completed deepest first; closing it
        // makes one. A recursive search overflowed the thread's stack well before this length.
        int n = 100_000;

        assertThat(TarjanSCC.compute(chain(n, false), n)).containsExactly(IntStream.range(0, n).map(i -> n - 1 - i).toArray());
        assertThat(TarjanSCC.compute(chain(n, true), n)).containsOnly(0);
    }
}

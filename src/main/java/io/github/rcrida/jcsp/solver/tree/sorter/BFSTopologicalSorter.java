package io.github.rcrida.jcsp.solver.tree.sorter;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.consistency.arc.Arc;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class BFSTopologicalSorter implements TopologicalSorter {
    public static final BFSTopologicalSorter INSTANCE = new BFSTopologicalSorter();

    private BFSTopologicalSorter() {}

    @Override
    public List<Arc> sort(@NonNull ConstraintSatisfactionProblem tcsp, @NonNull Variable root) {
        assert tcsp.isTree();
        val queue = new ArrayDeque<>(List.of(root));
        val visited = new HashSet<Variable>();
        val result = new ArrayList<Arc>();
        val neighbours = tcsp.getNeighbours();
        while (!queue.isEmpty()) {
            val node = queue.poll();
            visited.add(node);
            val unvisited = neighbours.get(node).stream().filter(v -> !visited.contains(v)).toList();
            queue.addAll(unvisited);
            result.addAll(unvisited.stream().map(v -> Arc.of(node, v)).toList());
        }
        return List.copyOf(result);
    }
}

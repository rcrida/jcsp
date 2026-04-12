package org.jcsp;

import lombok.val;
import org.jcsp.constraints.Constraint;
import org.jcsp.domains.Domain;
import org.jcsp.variables.Variable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TreeConstraintSatisfactionProblem extends ConstraintSatisfactionProblem {
    public TreeConstraintSatisfactionProblem(Map<Variable, Domain> variableDomains, Set<Constraint> constraints) {
        super(variableDomains, constraints);
        validateTree();
    }

    private void validateTree() {
        assert !getVariableDomains().isEmpty() : "Tree must not be empty";
        val visited = new HashSet<Variable>();
        val neighbours = getNeighbours();
        val startingVariable = neighbours.keySet().iterator().next();
        assert !isCyclic(startingVariable, null, neighbours, visited) : "Tree must not contain cycles";
        assert visited.size() == neighbours.size() : "Tree must be fully connected";
    }

    private boolean isCyclic(Variable src, Variable prt, Map<Variable, Set<Variable>> neighbours, Set<Variable> visited) {
        visited.add(src);
        for (Variable neighbour : neighbours.get(src)) {
            if (!visited.contains(neighbour)) {
                if (isCyclic(neighbour, src, neighbours, visited)) {
                    return true;
                }
            } else {
                if (neighbour != prt) {
                    return true;
                }
            }
        }
        return false;
    }
}

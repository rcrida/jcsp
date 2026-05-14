package io.github.rcrida.jcsp.domains;

import io.github.rcrida.jcsp.solver.tree.decomposition.decomposer.TreeDecomposer;
import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A {@link Domain} used for the domains of the nodes produced by {@link TreeDecomposer}.
 * Each potential value in the domain is an {@link Assignment} to all of the clique variables associated with a node of the tree
 * decomposition. The domain consists of all the possible consistent combinations of values of the domains of each of the clique
 * variables.
 */
public class AssignmentDomain extends DomainObjectSet<Assignment> {
    /**
     * Create the domain by iterating over all combinations of the clique variable domains, to create assignments and then
     * filter out any that are not consistent with the constraints of the original problem.
     *
     * @param variableDomains the set of clique variables and their associated domains
     * @param csp the original problem, used for determining which of the combinations of domain values are consistent
     */
    public AssignmentDomain(@NonNull Map<Variable<?>, Domain<?>> variableDomains, @NonNull ConstraintSatisfactionProblem csp) {
        super(populateCombinations(variableDomains, csp));
    }

    private static Set<Assignment> populateCombinations(@NonNull Map<Variable<?>, Domain<?>> variableDomains, @NonNull ConstraintSatisfactionProblem csp) {
        // create a list of single variable assignments for each value of the domain of each variable
        val variableAssignments = variableDomains.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream()
                                .map(v -> Assignment.builder().value(e.getKey(), v).build())
                                .toList()));
        // now merge all the combinations of the variable assignments, as long as they are consistent
        return variableAssignments.values().stream()
                .map(assignments -> (Supplier<Stream<Assignment>>) assignments::stream)
                .reduce((s1, s2) ->
                        () -> s1.get().flatMap(a1 -> s2.get().map(a1::merge)))
                .orElse(Stream::empty).get()
                .filter(a -> a.isConsistent(csp))
                .collect(Collectors.toSet());
    }
}

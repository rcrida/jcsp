package io.github.rcrida.jcsp.domains;

import io.github.rcrida.jcsp.solver.Cancellation;
import io.github.rcrida.jcsp.solver.tree.decomposition.decomposer.TreeDecomposer;
import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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
public record AssignmentDomain(Set<Assignment> values) implements DiscreteSetDomain<Assignment> {
    /**
     * Create the domain by iterating over all combinations of the clique variable domains, to create assignments and then
     * filter out any that are not consistent with the constraints of the original problem.
     *
     * @param variableDomains the set of clique variables and their associated domains
     * @param csp the original problem, used for determining which of the combinations of domain values are consistent
     */
    public AssignmentDomain(@NonNull Map<Variable<?>, Domain<?>> variableDomains, @NonNull ConstraintSatisfactionProblem csp) {
        this(populateCombinations(variableDomains, csp, Cancellation.NEVER));
    }

    /**
     * {@link #AssignmentDomain(Map, ConstraintSatisfactionProblem)}, but abandoned as {@link
     * Optional#empty()} when {@code cancellation} fires part-way through the enumeration.
     * <p>
     * This enumeration is the one genuinely long uninterruptible stretch on the satisfaction chain.
     * {@link io.github.rcrida.jcsp.solver.tree.decomposition.decomposer.TreeDecomposerImpl} bounds
     * each clique's combination count before calling this, but that bound is
     * {@code TreeDecompositionSolver.MAX_DOMAIN_SIZE_CAP} = 1,000,000 <em>per clique</em>, each
     * combination costing a full {@link Assignment#isConsistent} pass over every constraint -- so a
     * bounded enumeration can still run far longer than any caller's time limit. The cancellation
     * check goes once per combination produced, which is negligible beside that consistency pass.
     * <p>
     * A cancellation that arrives only after the enumeration finished also yields {@link
     * Optional#empty()}: a complete domain is of no use to a search that is about to stop anyway,
     * and the caller's fallback path notices the cancellation immediately.
     */
    public static Optional<AssignmentDomain> of(@NonNull Map<Variable<?>, Domain<?>> variableDomains,
                                                 @NonNull ConstraintSatisfactionProblem csp,
                                                 @NonNull Cancellation cancellation) {
        val combinations = populateCombinations(variableDomains, csp, cancellation);
        return cancellation.isCancelled() ? Optional.empty() : Optional.of(new AssignmentDomain(combinations));
    }

    private static Set<Assignment> populateCombinations(@NonNull Map<Variable<?>, Domain<?>> variableDomains,
                                                         @NonNull ConstraintSatisfactionProblem csp,
                                                         @NonNull Cancellation cancellation) {
        // create a list of single variable assignments for each value of the domain of each variable
        val variableAssignments = variableDomains.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> singleVariableAssignments(e.getKey(), e.getValue())));
        // now merge all the combinations of the variable assignments, as long as they are consistent
        return variableAssignments.values().stream()
                .map(assignments -> (Supplier<Stream<Assignment>>) assignments::stream)
                .reduce((s1, s2) ->
                        () -> s1.get().flatMap(a1 -> s2.get().map(a1::merge)))
                .orElse(Stream::empty).get()
                .takeWhile(a -> !cancellation.isCancelled())
                .filter(a -> a.isConsistent(csp))
                .collect(Collectors.toSet());
    }

    /**
     * Enumerates one clique variable's domain into single-variable assignments: full enumeration
     * for a {@link DiscreteDomain}, or the sole point for an already-singleton {@link BoundedDomain}
     * via {@link Domain#singleValue()} — the only shape a {@link BoundedDomain} can ever be by the
     * time a clique reaches tree decomposition. The satisfaction chain's
     * {@code PropagationFixpointSolver(snap=true)} always resolves every bounded domain to a
     * singleton before {@link io.github.rcrida.jcsp.solver.tree.decomposition.TreeDecompositionSolver} runs, and the optimization chain -- which
     * does leave bounded domains open -- reaches tree decomposition only through {@code
     * BranchAndBoundSolver}'s first-solution search, which {@code Solver.Factory} withholds
     * entirely from a problem that has any {@link BoundedDomain} variable for exactly this reason
     * (ADR-0041). A genuinely non-singleton {@link BoundedDomain} can't be enumerated at all, so
     * this deliberately doesn't attempt to handle that case.
     */
    private static List<Assignment> singleVariableAssignments(Variable<?> variable, Domain<?> domain) {
        if (domain instanceof DiscreteDomain<?> discrete) {
            return discrete.stream().map(v -> Assignment.builder().value(variable, v).build()).toList();
        }
        return List.of(Assignment.builder().value(variable, domain.singleValue().orElseThrow()).build());
    }

    @Override
    public String toString() { return DiscreteSetDomain.domainToString(this); }
}

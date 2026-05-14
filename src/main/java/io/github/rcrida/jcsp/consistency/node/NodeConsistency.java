package io.github.rcrida.jcsp.consistency.node;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.unary.UnaryConstraint;
import io.github.rcrida.jcsp.domains.Domain;

import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents a utility class for applying node consistency algorithms to
 * constraint satisfaction problems. This class ensures that all unary
 * constraints in a problem are satisfied by constraining the domains of variables
 * accordingly.
 */
@Slf4j
public class NodeConsistency {
    public static NodeConsistency INSTANCE = new NodeConsistency();

    private NodeConsistency() {}

    /**
     * Applies node consistency algorithm to the given constraint satisfaction problem.
     *
     * @param problem The constraint satisfaction problem to apply node consistency to.
     * @return An Optional containing the updated problem if node consistency was applied successfully, or empty if an inconsistency was found.
     */
    public Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem problem) {
        val builder = problem.toBuilder();
        val unaryConstraints = problem.getConstraints().stream()
                .filter(c -> c instanceof UnaryConstraint)
                .map(c -> (UnaryConstraint<?>) c)
                .toList();
        val variableDomains = new HashMap<>(problem.getVariableDomains());
        for (UnaryConstraint<?> constraint : unaryConstraints) {
            val variable = constraint.getVariable();
            val domain = variableDomains.get(variable);
            val optionalRevisedDomain = revise(domain, constraint);
            if (optionalRevisedDomain.isPresent()) {
                val revisedDomain = optionalRevisedDomain.get();
                if (revisedDomain.isEmpty()) {
                    log.warn("Domain of variable {} is empty after Node consistency", variable);
                    return Optional.empty();
                }
                builder.variableDomainEntry(variable, revisedDomain);
                variableDomains.put(variable, revisedDomain);
            }
        }
        val nodeConsistentProblem = builder.build();
        log.info("Node consistent problem {}", nodeConsistentProblem);
        return Optional.of(nodeConsistentProblem);
    }

    private Optional<Domain<?>> revise(Domain<?> domain, UnaryConstraint<?> constraint) {
        val revised = new AtomicBoolean(false);
        val revisedDomainBuilder = domain.toBuilder();
        domain.stream().forEach(x -> {
            if (!constraint.isSatisfiedByValue(x)) {
                revisedDomainBuilder.delete(x);
                revised.set(true);
            }
        });
        return revised.get() ? Optional.of(revisedDomainBuilder.build()) : Optional.empty();
    }
}

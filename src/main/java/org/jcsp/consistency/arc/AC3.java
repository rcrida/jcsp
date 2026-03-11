package org.jcsp.consistency.arc;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jcsp.constraints.BinaryConstraint;
import org.jcsp.domains.Domain;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Implementation of the Arc-Consistency algorithm (AC-3) for constraint satisfaction problems.
 * This class applies the arc-consistency algorithm to enforce consistency on binary constraints
 * in the given problem. It attempts to reduce the domains of variables while ensuring that
 * each constraint is satisfied.
 * <p>
 * The algorithm works by maintaining a queue of binary constraints (arcs) and iteratively revising
 * the domains of variables associated with those constraints.
 */
@Slf4j
public class AC3 implements ArcConsistency {
    public static AC3 INSTANCE = new AC3();

    private AC3() {}

    @Override
    public Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem problem) {
        final var builder = problem.toBuilder();
        final var binaryConstraints = problem.getConstraints().stream()
                .filter(c -> c instanceof BinaryConstraint)
                .map(c -> (BinaryConstraint) c)
                .toList();
        final var queue = new ArrayDeque<>(binaryConstraints.stream()
                .flatMap(c -> Stream.of(c, c.reversed()))
                .toList());
        while (!queue.isEmpty()) {
            final var arc = queue.poll();
            val X_i = arc.left();
            final var D_i = problem.getVariableDomains().get(X_i);
            final var optionalRevisedDomain1 = revise(problem, D_i, arc);
            if (optionalRevisedDomain1.isPresent()) {
                final var revisedDomain1 = optionalRevisedDomain1.get();
                if (revisedDomain1.isEmpty()) {
                    log.warn("Domain of variable {} is empty after AC3", X_i);
                    return Optional.empty();
                }
                builder.variableDomain(arc.left(), revisedDomain1);
                val X_iNeighbours = binaryConstraints.stream()
                        .filter(c -> c.right().equals(X_i))
                        .filter(c -> !c.right().equals(arc.right()))
                        .toList();
                queue.addAll(X_iNeighbours);
            }
        }
        return Optional.of(builder.build());
    }

    private Optional<Domain> revise(ConstraintSatisfactionProblem problem, Domain domain1, BinaryConstraint constraint) {
        final var revised = new AtomicBoolean(false);
        final var revisedDomain1Builder = domain1.toBuilder();
        domain1.stream().forEach(x -> {
            final var domain2 = problem.getVariableDomains().get(constraint.right());
            if (domain2.stream().noneMatch(y -> constraint.isSatisfied(x, y))) {
                revisedDomain1Builder.delete(x);
                revised.set(true);
            }
        });
        return revised.get() ? Optional.of(revisedDomain1Builder.build()) : Optional.empty();
    }
}

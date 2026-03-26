package org.jcsp.consistency.arc;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.constraints.binary.BinaryConstraint;
import org.jcsp.domains.Domain;
import org.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
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
        val allBinaryConstraints = problem.getAllBinaryConstraints().stream()
                .flatMap(c ->Stream.of(c, c.reversed()))
                .collect(Collectors.toSet());
        val queue = new ArrayDeque<>(allBinaryConstraints);
        return applyQueue(problem, queue, allBinaryConstraints);
    }

    public Optional<ConstraintSatisfactionProblem> applyQueue(ConstraintSatisfactionProblem problem, Queue<BinaryConstraint> queue, Set<BinaryConstraint> allBinaryConstraints) {
        val variableDomains = new HashMap<>(problem.getVariableDomains());
        while (!queue.isEmpty()) {
            val arc = queue.poll();
            val X_i = arc.getLeft();
            val X_j = arc.getRight();
            val D_i = variableDomains.get(X_i);
            val optionalRevisedDomain1 = revise(problem, D_i, arc);
            if (optionalRevisedDomain1.isPresent()) {
                val revisedDomain1 = optionalRevisedDomain1.get();
                if (revisedDomain1.isEmpty()) {
                    log.warn("Domain of variable {} is empty after AC3", X_i);
                    return Optional.empty();
                }
                variableDomains.put(X_i, revisedDomain1);
                val X_iNeighbours = allBinaryConstraints.stream()
                        .filter(c -> !c.getLeft().equals(X_j))
                        .filter(c -> c.getRight().equals(X_i))
                        .toList();
                queue.addAll(X_iNeighbours);
            }
        }
        return Optional.of(problem.toBuilder().variableDomains(variableDomains).build());
    }

    private Optional<Domain> revise(ConstraintSatisfactionProblem problem, Domain domain1, BinaryConstraint constraint) {
        val revised = new AtomicBoolean(false);
        val revisedDomain1Builder = domain1.toBuilder();
        domain1.stream().forEach(x -> {
            val domain2 = problem.getVariableDomains().get(constraint.getRight());
            if (domain2.stream().noneMatch(y -> constraint.isSatisfiedBy(x, y))) {
                revisedDomain1Builder.delete(x);
                revised.set(true);
            }
        });
        return revised.get() ? Optional.of(revisedDomain1Builder.build()) : Optional.empty();
    }
}

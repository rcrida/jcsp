package org.jcsp.consistency.arc;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.constraints.binary.BinaryConstraint;
import org.jcsp.domains.Domain;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

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
        val allArcs = problem.getAllBinaryConstraints().stream()
                .flatMap(BinaryConstraint::getArcs)
                .collect(Collectors.toSet());
        val queue = new ArrayDeque<>(allArcs);
        return applyQueue(problem, queue);
    }

    public Optional<ConstraintSatisfactionProblem> applyQueue(ConstraintSatisfactionProblem problem, Queue<Arc> queue) {
        val allBinaryConstraints = problem.getAllBinaryConstraints();
        val arcConstraints = allBinaryConstraints.stream()
                .flatMap(binaryConstraint -> binaryConstraint.getArcs()
                        .map(arc -> new AbstractMap.SimpleEntry<>(arc, binaryConstraint)))
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        val allArcs = arcConstraints.keySet();
        val variableDomains = new HashMap<>(problem.getVariableDomains());
        while (!queue.isEmpty()) {
            val arc = queue.poll();
            val X_i = arc.getFrom();
            val X_j = arc.getTo();
            for (BinaryConstraint binaryConstraint : arcConstraints.get(arc)) {
                val optionalRevisedD_i = revise(problem, arc, binaryConstraint);
                if (optionalRevisedD_i.isPresent()) {
                    val revisedD_i = optionalRevisedD_i.get();
                    if (revisedD_i.isEmpty()) {
                        log.warn("Domain of variable {} is empty after AC3", X_i);
                        return Optional.empty();
                    }
                    variableDomains.put(X_i, revisedD_i);
                    val X_iNeighbours = allArcs.stream()
                            .filter(c -> !c.getFrom().equals(X_j))
                            .filter(c -> c.getTo().equals(X_i))
                            .toList();
                    queue.addAll(X_iNeighbours);
                }
            }
        }
        return Optional.of(problem.toBuilder().variableDomains(variableDomains).build());
    }

    public Optional<Domain> revise(ConstraintSatisfactionProblem problem, Arc arc, BinaryConstraint constraint) {
        val D_i = problem.getVariableDomains().get(arc.getFrom());
        val revised = new AtomicBoolean(false);
        val revisedD_iBuilder = D_i.toBuilder();
        D_i.stream().forEach(x -> {
            val domain2 = problem.getVariableDomains().get(arc.getTo());
            if (domain2.stream().noneMatch(y -> constraint.isSatisfiedBy(arc.toAssignment(x, y)))) {
                revisedD_iBuilder.delete(x);
                revised.set(true);
            }
        });
        return revised.get() ? Optional.of(revisedD_iBuilder.build()) : Optional.empty();
    }
}

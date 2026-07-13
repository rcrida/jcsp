package io.github.rcrida.jcsp.consistency.arc;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.consistency.ConstraintConsistency;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.binary.BinaryConstraint;
import io.github.rcrida.jcsp.constraints.nary.GroundNogoodConstraint;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.variables.Variable;

import java.util.*;
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
public class AC3 implements ConstraintConsistency {
    public static final AC3 INSTANCE = new AC3();

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
        val arcsByTarget = arcConstraints.keySet().stream()
                .collect(Collectors.groupingBy(Arc::getTo));
        val variableDomains = new HashMap<>(problem.getVariableDomains());
        while (!queue.isEmpty()) {
            val arc = queue.poll();
            val X_i = arc.getFrom();
            val X_j = arc.getTo();
            for (BinaryConstraint<?, ?> binaryConstraint : arcConstraints.get(arc)) {
                val optionalRevisedD_i = revise(problem, arc, binaryConstraint);
                if (optionalRevisedD_i.isPresent()) {
                    val revisedD_i = optionalRevisedD_i.get();
                    if (revisedD_i.isEmpty()) {
                        log.warn("Domain of variable {} is empty after AC3", X_i);
                        return Optional.empty();
                    }
                    variableDomains.put(X_i, revisedD_i);
                    val X_iNeighbours = arcsByTarget.getOrDefault(X_i, List.of()).stream()
                            .filter(c -> !c.getFrom().equals(X_j))
                            .toList();
                    queue.addAll(X_iNeighbours);
                }
            }
        }
        return Optional.of(problem.toBuilder().variableDomains(variableDomains).build());
    }

    /**
     * Re-runs the same arc-consistency traversal as {@link #applyQueue} (same initial queue, same
     * arc/constraint grouping, same processing order), stopping at the same domain wipeout, and
     * attributes it. Deliberately a separate traversal rather than sharing a core loop with
     * {@link #applyQueue} — mirrors {@code FixpointConsistency}'s {@code apply}/{@code explainConflict}
     * pair, which are likewise two parallel implementations rather than one shared one.
     * <p>
     * A wipeout on arc {@code (X_i, X_j)} means: {@code revise} found that, of the values currently
     * in {@code D_i} (as read from {@code problem} — not X_i's declared/original domain, its
     * current, context-dependent one), none satisfy the constraint against {@code D_j} (likewise
     * current). Citing only {@code X_j}'s value would be <em>unsound</em> here even when
     * {@code X_j} is singleton: it would claim the constraint rules out every value {@code X_i}
     * could <em>ever</em> take, when it only ruled out what was in {@code D_i} <em>at this point
     * in the search</em> — a claim that doesn't generalise to branches where X_i's domain differs.
     * The reason is sound only when {@code X_i} is <em>also</em> singleton at this point
     * ({@link Propagatable#allSingletonReason}): then the wipeout reduces to one concrete pair
     * violating the (fixed, structural) constraint, which is unconditionally true regardless of
     * search state. Unlike {@link #applyQueue}, no domain bookkeeping is needed here: {@link #revise}
     * reads only from {@code problem}, never from any progressively-narrowed map, so the wipeout
     * point is found identically either way.
     * <p>
     * Deliberately ground-only, unlike {@link io.github.rcrida.jcsp.consistency.fixpoint.FixpointConsistency#explainConflict},
     * which additionally falls back to a range-based nogood over a whole failing constraint's
     * variables when the ground reason is empty. That fallback's soundness argument rests on
     * {@link Propagatable#propagate} directly reporting infeasibility for a constraint given its
     * current domains; AC3's wipeout is a per-arc support-existence check on {@link #revise}, not a
     * single constraint's own {@code propagate} call, so extending the same argument here would
     * need separate justification not attempted in this pass.
     */
    @Override
    public Optional<NogoodConstraint> explainConflict(ConstraintSatisfactionProblem problem) {
        val allArcs = problem.getAllBinaryConstraints().stream()
                .flatMap(BinaryConstraint::getArcs)
                .collect(Collectors.toSet());
        val queue = new ArrayDeque<>(allArcs);
        val allBinaryConstraints = problem.getAllBinaryConstraints();
        val arcConstraints = allBinaryConstraints.stream()
                .flatMap(binaryConstraint -> binaryConstraint.getArcs()
                        .map(arc -> new AbstractMap.SimpleEntry<>(arc, binaryConstraint)))
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        val arcsByTarget = arcConstraints.keySet().stream()
                .collect(Collectors.groupingBy(Arc::getTo));
        while (!queue.isEmpty()) {
            val arc = queue.poll();
            val X_i = arc.getFrom();
            val X_j = arc.getTo();
            for (BinaryConstraint<?, ?> binaryConstraint : arcConstraints.get(arc)) {
                val optionalRevisedD_i = revise(problem, arc, binaryConstraint);
                if (optionalRevisedD_i.isPresent()) {
                    val revisedD_i = optionalRevisedD_i.get();
                    if (revisedD_i.isEmpty()) {
                        val reason = Propagatable.allSingletonReason(List.of(X_i, X_j), problem.getVariableDomains());
                        return reason.isEmpty() ? Optional.empty() : Optional.of(GroundNogoodConstraint.of(reason));
                    }
                    val X_iNeighbours = arcsByTarget.getOrDefault(X_i, List.of()).stream()
                            .filter(c -> !c.getFrom().equals(X_j))
                            .toList();
                    queue.addAll(X_iNeighbours);
                }
            }
        }
        return Optional.empty();
    }

    public Optional<ConstraintSatisfactionProblem> revise(ConstraintSatisfactionProblem problem, Arc arc) {
        val arcConstraints = problem.getAllBinaryConstraints().stream()
                .filter(bc -> bc.getArcs().anyMatch(arc::equals))
                .toList();
        val variableDomains = new HashMap<>(problem.getVariableDomains());
        for (BinaryConstraint<?, ?> binaryConstraint : arcConstraints) {
            val optionalRevisedD = revise(problem, arc, binaryConstraint);
            if (optionalRevisedD.isPresent()) {
                val revisedD = optionalRevisedD.get();
                if (revisedD.isEmpty()) {
                    log.warn("Domain of variable {} is empty after revising arc {}", arc.getFrom(), arc);
                    return Optional.empty();
                }
                variableDomains.put(arc.getFrom(), revisedD);
            }
        }
        return Optional.of(problem.toBuilder().variableDomains(variableDomains).build());
    }

    public Optional<DiscreteDomain<?>> revise(ConstraintSatisfactionProblem problem, Arc arc, BinaryConstraint<?, ?> constraint) {
        if (!(problem.getVariableDomains().get(arc.getFrom()) instanceof DiscreteDomain<?> D_i)) return Optional.empty();
        if (!(problem.getVariableDomains().get(arc.getTo()) instanceof DiscreteDomain<?> D_j)) return Optional.empty();
        val valuesToDelete = D_i.stream()
                .filter(x -> D_j.stream().noneMatch(y -> constraint.isSatisfiedBy(arc.toAssignment(x, y))))
                .toList();
        if (valuesToDelete.isEmpty()) return Optional.empty();
        val revisedBuilder = D_i.toBuilder();
        valuesToDelete.forEach(revisedBuilder::delete);
        return Optional.of(revisedBuilder.build());
    }
}

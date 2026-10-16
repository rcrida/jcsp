package io.github.rcrida.jcsp.solver.tree;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.consistency.arc.AC3;
import io.github.rcrida.jcsp.consistency.arc.Arc;
import io.github.rcrida.jcsp.solver.backtrackingsearch.order.DomainValuesOrderer;
import io.github.rcrida.jcsp.solver.Cancellation;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.solver.tree.selector.TreeUnassignedVariableSelector;
import io.github.rcrida.jcsp.solver.tree.sorter.TopologicalSorter;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Slf4j
@Value
public class TreeSolver implements Solver {
    @NonNull TopologicalSorter topologicalSorter;
    @NonNull DomainValuesOrderer orderer;
    TreeUnassignedVariableSelector.Factory selectorFactory;
    @NonNull Cancellation cancellation;

    @Override
    public Stream<Assignment> getSolutions(@NonNull ConstraintSatisfactionProblem tcsp) {
        assert tcsp.isTree();
        log.debug("Searching {}", tcsp);
        val root = tcsp.getVariableDomains().entrySet().iterator().next().getKey();
        val X = topologicalSorter.sort(tcsp, root);
        var current = tcsp;
        for (Arc arc : X.reversed()) {
            val result = makeArcConsistent(current, arc.getFrom(), arc.getTo());
            if (result.isEmpty()) return Stream.empty();
            current = result.get();
        }
        val finalTcsp = current;
        val unassignedVariableSelector = selectorFactory.createSelector(X);
        Domain<?> rootDomain = finalTcsp.getDomain(root);
        log.debug("Domain {}", rootDomain);
        val start = Assignment.empty();
        // Pre-filtered once, outside the per-candidate-value loop below, so checking each candidate
        // root value doesn't re-scan the tree's entire constraint list (Assignment#isConsistent's
        // own filter) just to find the handful of constraints that touch root.
        val rootConstraints = finalTcsp.getConstraints().stream()
                .filter(constraint -> constraint.getVariables().contains(root))
                .toList();
        return (rootDomain instanceof DiscreteDomain<?> dd ? dd.stream() : rootDomain.singleValue().stream())
                .<Assignment>map(value -> start.withValue(root, value))
                // populateAssignment only calls isConsistent for variables assigned *after* the root
                // (each recursive step filters its own candidate); a constraint that touches only the
                // root itself -- e.g. a single-variable PredicateConstraint, which NodeConsistency
                // doesn't prune because it isn't a UnaryConstraint -- would otherwise never be checked
                // at all when the tree is exactly one node, since populateAssignment then returns
                // immediately via the isComplete(tcsp) branch below without ever filtering.
                .filter(rootAssignment -> rootAssignment.isConsistentAmong(rootConstraints))
                .flatMap(rootAssignment -> populateAssignment(finalTcsp, rootAssignment, unassignedVariableSelector));
    }

    Optional<ConstraintSatisfactionProblem> makeArcConsistent(@NonNull ConstraintSatisfactionProblem tcsp, @NonNull Variable parent, @NonNull Variable node) {
        return AC3.INSTANCE.revise(tcsp, Arc.of(parent, node));
    }

    Stream<Assignment> populateAssignment(@NonNull ConstraintSatisfactionProblem tcsp, @NonNull Assignment assignment, @NonNull TreeUnassignedVariableSelector selector) {
        return StreamSupport.stream(new Descent(tcsp, assignment, selector), false);
    }

    /**
     * Depth-first search below one assignment, yielding each complete one lazily and in order.
     * <p>
     * The levels live in an explicit stack of candidate iterators rather than in nested streams: a
     * tree is searched one variable per level, so a recursive {@code flatMap} per level overflowed
     * the thread stack at a few thousand variables, where this is bounded by the heap.
     */
    private final class Descent extends Spliterators.AbstractSpliterator<Assignment> {
        private final ConstraintSatisfactionProblem tcsp;
        private final TreeUnassignedVariableSelector selector;
        /** One iterator of still-untried candidates per level, innermost level on top. */
        private final Deque<Iterator<Assignment>> levels = new ArrayDeque<>();

        Descent(ConstraintSatisfactionProblem tcsp, Assignment start, TreeUnassignedVariableSelector selector) {
            super(Long.MAX_VALUE, Spliterator.ORDERED);
            this.tcsp = tcsp;
            this.selector = selector;
            levels.push(List.of(start).iterator());
        }   

        @Override
        public boolean tryAdvance(Consumer<? super Assignment> action) {
            Assignment solution = advance();
            if (solution == null) {
                return false;
            }
            action.accept(solution);
            return true;
        }

        /** The next complete assignment, or {@code null} once the search is exhausted or cancelled. */
        private @Nullable Assignment advance() {
            while (!levels.isEmpty()) {
                Iterator<Assignment> candidates = levels.peek();
                if (!candidates.hasNext()) {
                    levels.pop();
                    continue;
                }
                Assignment assignment = candidates.next();
                log.debug("Searching tree with assignment: {}", assignment);
                // Truncates silently, like every getSolutions() stream; the callers that need to tell a
                // cancelled search from an UNSAT one check the token themselves.
                if (cancellation.isCancelled()) {
                    levels.clear();
                    return null;
                }
                if (assignment.isComplete(tcsp)) {
                    log.debug("Found tree solution {}", assignment);
                    return assignment;
                }
                val variable = selector.select(tcsp, assignment);
                levels.push(orderer.order(tcsp, variable, assignment)
                        .map(value -> assignment.withValue(variable, value))
                        .filter(next -> next.isConsistent(tcsp))
                        .iterator());
            }
            return null;
        }
    }
}

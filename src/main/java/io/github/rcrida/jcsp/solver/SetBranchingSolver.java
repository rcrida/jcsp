package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.SetBoundedDomain;
import io.github.rcrida.jcsp.solver.listener.SolverListener;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

/**
 * Handles {@link SetBoundedDomain} variables that do not collapse to singletons during propagation
 * by recursively branching: picks the most-undetermined set variable, picks one of its undetermined
 * elements, and explores both "force it in" and "exclude it" as separate branches, re-propagating
 * and backtracking on infeasibility. {@code inner} handles any remaining discrete (and, in the
 * optimization chain, continuous) variables once every set variable is fully resolved.
 * <p>
 * Unlike {@link BisectionConditioningSolver}'s numeric midpoint-snapping (safe for the satisfaction
 * chain because propagation-narrowed continuous bounds are typically box-consistent — almost any
 * point in the box also satisfies each individual constraint's own bound requirement), an arbitrary
 * choice among a set variable's undetermined elements is far more likely to violate some other
 * constraint the search hasn't considered yet (set constraints are inherently combinatorial, not
 * smooth). So this class does real backtracking search in <em>both</em> chains, not just
 * optimization — {@link Solver.Factory} wires it in unconditionally whenever any variable's domain
 * is a {@link SetBoundedDomain}, in both {@code createSolver} overloads.
 * <p>
 * {@link #objective} is {@code null} in the satisfaction chain (no incumbent to track — {@link
 * #getSolutions} returns every branch's solutions unfiltered) and non-null in the optimization
 * chain. It exists for the same reason {@link BisectionConditioningSolver} carries one: each branch
 * below independently calls {@code inner.getSolutions(...)}, and {@link BranchAndBoundSolver}'s own
 * "each solution strictly better than the previous" guarantee only holds <em>within</em> one such
 * call — concatenating multiple branches' streams without a shared incumbent would let a later
 * branch yield a worse solution than an earlier one already found, silently breaking that contract
 * for whatever reads the combined stream (in particular, {@link BoundSolver#getSolution}'s {@code
 * reduce((a, b) -> b)}, which assumes the last element is the global optimum).
 */
@Slf4j
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class SetBranchingSolver extends SolverDecorator {

    @Nullable ToDoubleFunction<Assignment> objective;
    @Builder.Default @NonNull SolverListener listener = SolverListener.NONE;

    @Override
    public Stream<Assignment> getSolutions(@NonNull ConstraintSatisfactionProblem csp) {
        if (objective == null) {
            return allFeasible(csp);
        }
        double[] incumbent = {Double.MAX_VALUE};
        return allFeasible(csp).filter(candidate -> {
            double cost = objective.applyAsDouble(candidate);
            if (cost < incumbent[0]) {
                incumbent[0] = cost;
                return true;
            }
            return false;
        });
    }

    /**
     * Explicitly pinned to {@code getSolutions(csp).findFirst()} rather than inheriting {@link
     * SolverDecorator}'s default, for the same reason {@link BisectionConditioningSolver} is: this
     * class's logic lives in {@link #getSolutions}, not in a {@code preprocess}-then-delegate shape
     * the base default assumes.
     */
    @Override
    public Optional<Assignment> getSolution(@NonNull ConstraintSatisfactionProblem csp) {
        return getSolutions(csp).findFirst();
    }

    private Stream<Assignment> allFeasible(@NonNull ConstraintSatisfactionProblem csp) {
        val target = findMostUndeterminedSet(csp);
        if (target == null) {
            return csp.isFullyDetermined() ? forcedSolution(csp).stream() : getInner().getSolutions(csp);
        }
        val domain = (SetBoundedDomain<?>) csp.getDomain(target);
        val element = pickElement(domain);
        log.debug("Branching on {}: force {} in, or exclude it", target, element);
        return Stream.concat(
                forceIn(csp, target, domain, element).stream().flatMap(this::allFeasible),
                excludeFrom(csp, target, domain, element).stream().flatMap(this::allFeasible)
        );
    }

    @Nullable
    static Variable<?> findMostUndeterminedSet(ConstraintSatisfactionProblem csp) {
        return csp.getVariableDomains().entrySet().stream()
                .filter(e -> e.getValue() instanceof SetBoundedDomain<?> sd && !sd.isSingleton())
                .max(Comparator.comparingInt(e -> undeterminedCount((SetBoundedDomain<?>) e.getValue())))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * Picks the least undetermined element by {@link SetBoundedDomain#getComparator()} — every
     * domain carries one unconditionally (see that method's Javadoc), so branch order (and
     * therefore which solution is found first) is reproducible across runs without this method
     * needing to derive its own ordering.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object pickElement(SetBoundedDomain domain) {
        Set undetermined = new HashSet<>(domain.getUpperBound());
        undetermined.removeAll(domain.getLowerBound());
        return undetermined.stream().min(domain.getComparator()).orElseThrow();
    }

    /**
     * Never itself produces an empty domain, so unlike {@code repropagate}'s own check below this
     * has no {@code narrowed.isEmpty()} guard: {@code domain} only ever reaches here selected by
     * {@link #findMostUndeterminedSet} (non-singleton, hence non-empty, hence {@code |lowerBound| <
     * maxCardinality} strictly — otherwise {@link
     * io.github.rcrida.jcsp.domains.SetIntervalDomain}'s own tightening would already have
     * collapsed it to a singleton) with {@code element} freshly drawn from {@code upperBound \
     * lowerBound} by {@link #pickElement}. Adding one more forced-in element therefore always keeps
     * {@code lowerBound.size() <= maxCardinality} and leaves containment/{@code upperBound}'s own
     * cardinality untouched, so none of {@link SetBoundedDomain#isEmpty()}'s four conditions can
     * become true here. This is specific to how <em>this class</em> calls {@code withLowerBound} —
     * unlike {@link io.github.rcrida.jcsp.constraints.binary.DisjointConstraint}/{@link io.github.rcrida.jcsp.constraints.binary.SubsetConstraint}, which narrow arbitrary domain
     * states and do need their own checks.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Optional<ConstraintSatisfactionProblem> forceIn(ConstraintSatisfactionProblem csp, Variable target,
                                                              SetBoundedDomain domain, Object element) {
        SetBoundedDomain narrowed = domain.withLowerBound(Set.of(element));
        return repropagate(csp.withDomain(target, narrowed));
    }

    /** Never produces an empty domain either, for the symmetric reason {@link #forceIn} doesn't. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Optional<ConstraintSatisfactionProblem> excludeFrom(ConstraintSatisfactionProblem csp, Variable target,
                                                                  SetBoundedDomain domain, Object element) {
        Set restricted = new HashSet<>(domain.getUpperBound());
        restricted.remove(element);
        SetBoundedDomain narrowed = domain.withUpperBound(restricted);
        return repropagate(csp.withDomain(target, narrowed));
    }

    /**
     * Delegates to {@link FixpointPropagation#applyFixpoint(ConstraintSatisfactionProblem, Set, SolverListener)}
     * so that every constraint referencing a set variable — not just {@link
     * io.github.rcrida.jcsp.constraints.binary.SubsetConstraint}/{@link
     * io.github.rcrida.jcsp.constraints.binary.DisjointConstraint}/{@link
     * io.github.rcrida.jcsp.constraints.binary.IntersectionCardinalityConstraint}/{@link
     * io.github.rcrida.jcsp.constraints.nary.PartitionConstraint} — gets to prune a branch as soon
     * as it's narrowed, including one only reached via {@link
     * io.github.rcrida.jcsp.constraints.nary.ReifiedConstraint} or a {@link
     * io.github.rcrida.jcsp.constraints.nary.LexConstraint} symmetry-breaking constraint. Instance
     * method (not {@code static}, unlike before {@link #listener} existed) so it can read
     * {@code this.listener} rather than needing it threaded through every caller as a parameter.
     */
    private Optional<ConstraintSatisfactionProblem> repropagate(ConstraintSatisfactionProblem csp) {
        return FixpointPropagation.applyFixpoint(csp, null, listener);
    }

    private static int undeterminedCount(SetBoundedDomain<?> domain) {
        return domain.getUpperBound().size() - domain.getLowerBound().size();
    }
}

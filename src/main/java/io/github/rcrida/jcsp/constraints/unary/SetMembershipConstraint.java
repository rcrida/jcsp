package io.github.rcrida.jcsp.constraints.unary;

import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.constraints.nary.ReifiedConstraint;
import io.github.rcrida.jcsp.constraints.nary.SetBoundsNogoodConstraint;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.SetBoundedDomain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Unary constraint over a set variable: {@code element ∈ variable}. The set-CP analogue of
 * {@link UnaryValueConstraint} — instead of pinning a scalar variable to one ground value, this
 * pins one specific element's membership in a {@link SetBoundedDomain} variable. Its primary
 * purpose is to be reified ({@code indicator <-> element ∈ variable}), which is otherwise
 * impossible for a set variable: {@link io.github.rcrida.jcsp.constraints.binary.SubsetConstraint}/
 * {@link io.github.rcrida.jcsp.constraints.binary.DisjointConstraint} can only ever <em>force</em>
 * hard, unconditional membership/exclusion — neither can produce a genuine boolean output variable
 * usable elsewhere (e.g. in an arithmetic ordering constraint that breaks symmetry between
 * otherwise-interchangeable set variables, which is what motivated adding this class).
 * <p>
 * Propagation only narrows the "must be a member" direction — forcing {@code element} into the
 * domain's lower bound — mirroring {@link ReifiedConstraint}'s own documented, generic limit for
 * an arbitrary body ("no generic way to propagate the negation of an arbitrary constraint"): a
 * reified indicator forced {@code false} isn't proactively excluded from the domain's upper bound.
 * Final-solution correctness is unaffected, since {@link ReifiedConstraint#isSatisfiedBy} always
 * re-checks the ground truth once every variable involved is assigned, regardless of how much
 * propagation ran beforehand — this constraint only needs to be sound, not complete.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class SetMembershipConstraint<E> extends UnaryConstraint<Set<E>> implements Propagatable {
    @NonNull private final E element;

    public static <E> SetMembershipConstraint<E> of(@NonNull Variable<Set<E>> variable, @NonNull E element) {
        return SetMembershipConstraint.<E>builder().variable(variable).element(element).build();
    }

    @Override
    protected boolean checkValue(@NonNull Set<E> value) {
        return value.contains(element);
    }

    @Override
    public String getRelation() {
        return element + " in " + getVariable();
    }

    /**
     * Forces {@code element} into the domain's lower bound — sound whenever {@code propagate} runs
     * at all, since that only happens once the constraint is known to hold (either registered
     * directly, or via {@link ReifiedConstraint} once its indicator has resolved true). Reports
     * infeasible when {@code element} isn't even in the upper bound (can never be a member) —
     * exactly the signal {@link ReifiedConstraint} needs, with no set-specific logic of its own, to
     * force a still-open indicator false.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        if (!(domains.get(getVariable()) instanceof SetBoundedDomain<?>)) {
            return Optional.of(Map.of());
        }
        SetBoundedDomain<E> domain = (SetBoundedDomain<E>) domains.get(getVariable());
        SetBoundedDomain<E> narrowed = domain.withLowerBound(Set.of(element));
        if (narrowed.isEmpty()) return Optional.empty();
        return narrowed.equals(domain) ? Optional.of(Map.of()) : Optional.of(Map.of(getVariable(), narrowed));
    }

    /** Two-tier explanation via {@link SetBoundsNogoodConstraint#explainViaGroundOrBounds}. */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        return SetBoundsNogoodConstraint.explainViaGroundOrBounds(getVariables(), domains);
    }

    /**
     * {@code element} already forced into the domain's lower bound makes this constraint true
     * regardless of how the domain narrows further — propagation only ever adds to a lower bound,
     * never removes from it, so this can't be undone by any later step in the same search branch.
     */
    @Override
    public boolean isNecessarilySatisfied(@NonNull Map<Variable<?>, Domain<?>> domains) {
        return domains.get(getVariable()) instanceof SetBoundedDomain<?> domain && domain.getLowerBound().contains(element);
    }
}

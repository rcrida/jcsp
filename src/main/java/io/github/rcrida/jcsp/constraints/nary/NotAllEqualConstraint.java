package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The negative dual of {@link AllEqualConstraint}: {@code NOT(v1 == v2 == ... == vn)}, satisfied
 * unless every variable ends up with the same value. Equivalent to fixing {@link NValueConstraint}'s
 * {@code count} to "more than 1" — the pattern XCSP3's {@code nValues} construct produces under an
 * XCSP3 {@code (gt,1)} condition (see {@code
 * Xcsp3CallbackHandler#applyNValuesCondition}) — but unlike {@link NValueConstraint}'s general
 * bounds-consistency propagation (GAC-nvalue is NP-hard for an arbitrary target count), "more than
 * one distinct value" is the single easy case: {@link #propagate} is exact generalised arc
 * consistency, and needs neither an auxiliary count variable nor {@link NValueConstraint}'s
 * classify/bound machinery to get it. Added after profiling a real XCSP3 instance ({@code
 * Ramsey-12.xml.lzma}, 220 triangle constraints each modelled as {@code nValues(3 vars) > 1}) whose
 * propagation stayed almost inert until two of a triangle's three edges were already assigned, the
 * structural cost of routing "not all equal" through {@link NValueConstraint}'s general machinery.
 * <p>
 * A value {@code v} can be soundly excluded from one variable's domain only if every other
 * variable is already pinned to {@code v} — with two or more variables still open, either one is
 * always free to pick a different value, so no domain can be narrowed at all until at most one
 * variable remains undetermined (this is a structural property of the constraint itself, not a
 * weakness of this propagator). {@link #propagate} therefore only ever narrows the sole open
 * variable once every other one is already singleton at a shared value; with zero variables open
 * it either finds two singletons that already differ (nothing to do) or all of them equal (the
 * one and only infeasibility case, per {@link #explainInfeasible}).
 * <p>
 * Deliberately does not implement {@link io.github.rcrida.jcsp.constraints.BinaryDecomposable}:
 * unlike {@link AllEqualConstraint}, whose GAC decomposes losslessly into a pairwise-equality
 * chain, "not all equal" has no equivalent decomposition into independent binary constraints — a
 * pairwise {@code !=} on every pair is {@link AllDiffConstraint}, a strictly stronger requirement.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class NotAllEqualConstraint<T> extends UniformNaryConstraint<T> implements Propagatable {

    public static <T> NotAllEqualConstraint<T> of(@NonNull Set<Variable<T>> variables) {
        assert variables.size() >= 2 : "NotAllEqualConstraint requires at least 2 variables";
        return NotAllEqualConstraint.<T>builder().variables(variables).build();
    }

    /**
     * Optimistic until every variable is assigned (a partial assignment can never yet prove "all
     * equal", since an unassigned variable could still differ from the rest); violated only once
     * every value is in and they're all equal.
     */
    @Override
    protected boolean isSatisfiedByValues(@NonNull Collection<T> values) {
        if (values.size() < getVariables().size()) return true;
        Iterator<T> it = values.iterator();
        T first = it.next();
        while (it.hasNext()) {
            if (!Objects.equals(first, it.next())) return true;
        }
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        List<Variable<T>> open = new ArrayList<>();
        T commonValue = null;
        for (Variable<T> v : (Set<Variable<T>>) (Set<?>) getVariables()) {
            DiscreteDomain<T> dom = (DiscreteDomain<T>) domains.get(v);
            if (!dom.isSingleton()) {
                open.add(v);
                continue;
            }
            T value = dom.singleValue().get();
            if (commonValue == null) {
                commonValue = value;
            } else if (!Objects.equals(commonValue, value)) {
                return Optional.of(Map.of());
            }
        }
        if (!open.isEmpty()) {
            if (open.size() > 1) return Optional.of(Map.of());
            Variable<T> lone = open.get(0);
            DiscreteDomain<T> dom = (DiscreteDomain<T>) domains.get(lone);
            if (!dom.contains(commonValue)) return Optional.of(Map.of());
            // dom is non-singleton (open), so removing one value can never empty it.
            return Optional.of(Map.of(lone, dom.toBuilder().delete(commonValue).build()));
        }
        return Optional.empty();
    }

    /**
     * {@link #propagate}'s only infeasibility path is every variable singleton at the same value
     * — the forcing step never wipes a domain, since it only ever deletes one value from a
     * variable that (being classified open) had at least two. So this is always sound to call
     * directly: {@link Propagatable#allSingletonReason} over every variable.
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        return GroundNogoodConstraint.fromReason(Propagatable.allSingletonReason(getVariables(), domains));
    }

    @Override
    public String getRelation() {
        return "NotAllEqual" + getVariables().stream().map(Object::toString).sorted()
                .collect(Collectors.joining(", ", "(", ")"));
    }
}

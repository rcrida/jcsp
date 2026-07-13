package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Represents the "at least N" constraint for boolean variables in a CSP.
 * This constraint ensures that at least {@code n} of the involved variables are {@code true}.
 * Suitable for use with {@link io.github.rcrida.jcsp.domains.BooleanDomain}.
 *
 * <p>For partial assignments, the constraint is satisfied as long as it is still possible
 * to reach {@code n} true values — it only fails when all variables are assigned and fewer
 * than {@code n} are {@code true}.
 */
@SuperBuilder
public class AtLeastNConstraint extends UniformNaryConstraint<Boolean> implements Propagatable {
    private final int n;

    @Override
    protected boolean isSatisfiedByValues(@NonNull Collection<Boolean> values) {
        long trueCount = values.stream().filter(b -> b).count();
        if (trueCount >= n) return true;
        return values.size() < getVariables().size();
    }

    /**
     * When the maximum reachable count of {@code true} variables equals exactly {@code n},
     * forces all possibly-true variables (domain {@code {true, false}}) to {@code true}.
     * Detects infeasibility when the max reachable count falls below {@code n}.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        List<Variable<Boolean>> possiblyTrue = new ArrayList<>();
        int definiteTrue = 0;
        for (Variable<?> var : getVariables()) {
            Domain<Boolean> dom = (Domain<Boolean>) domains.get(var);
            if (!dom.contains(Boolean.TRUE)) continue;
            if (!dom.contains(Boolean.FALSE)) definiteTrue++;
            else possiblyTrue.add((Variable<Boolean>) var);
        }
        int maxCount = definiteTrue + possiblyTrue.size();
        if (maxCount < n) return Optional.empty();

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        if (maxCount == n) {
            for (Variable<Boolean> var : possiblyTrue)
                updated.put(var, ((DiscreteDomain<Boolean>) domains.get(var)).toBuilder().delete(Boolean.FALSE).build());
        }
        return Optional.of(updated);
    }

    @Override
    public String getRelation() {
        return "AtLeast" + n;
    }

    /**
     * On infeasibility (max reachable count below {@code n}), attributes the conflict to every
     * variable already forced {@code false} (domain excludes {@code TRUE}) — collectively a
     * sufficient (not necessarily minimal) explanation for why the reachable count falls short.
     * Empty when no variable is forced false (e.g. {@code n} exceeds the variable count while
     * domains remain open); callers fall back to the full assignment in that case.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        Map<Variable<?>, Object> reason = new HashMap<>();
        for (Variable<?> var : getVariables()) {
            Domain<Boolean> dom = (Domain<Boolean>) domains.get(var);
            if (!dom.contains(Boolean.TRUE)) reason.put(var, Boolean.FALSE);
        }
        return GroundNogoodConstraint.fromReason(reason);
    }
}

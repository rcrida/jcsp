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
 * Represents the "at most N" constraint for boolean variables in a CSP.
 * This constraint ensures that at most {@code n} of the involved variables are {@code true}.
 * Suitable for use with {@link io.github.rcrida.jcsp.domains.BooleanDomain}.
 *
 * <p>For the specific case of N=1, prefer {@link AtMostOneConstraint}, which provides an
 * AC3-compatible binary decomposition into pairwise not-both-true constraints.
 */
@SuperBuilder
public class AtMostNConstraint extends UniformNaryConstraint<Boolean> implements Propagatable {
    private final int n;

    @Override
    protected boolean isSatisfiedByValues(@NonNull Collection<Boolean> values) {
        return values.stream().filter(b -> b).count() <= n;
    }

    /**
     * When the count of definite {@code true} variables reaches {@code n}, forces all
     * remaining possibly-true variables (domain {@code {true, false}}) to {@code false}.
     * Detects infeasibility when the definite count already exceeds {@code n}.
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
        if (definiteTrue > n) return Optional.empty();

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        if (definiteTrue == n) {
            for (Variable<Boolean> var : possiblyTrue)
                updated.put(var, ((DiscreteDomain<Boolean>) domains.get(var)).toBuilder().delete(Boolean.TRUE).build());
        }
        return Optional.of(updated);
    }

    @Override
    public String getRelation() {
        return "AtMost" + n;
    }

    /**
     * On infeasibility (definite {@code true} count above {@code n}), attributes the conflict to
     * every variable already forced {@code true} (domain excludes {@code FALSE}) — collectively a
     * sufficient (not necessarily minimal) explanation for why the count exceeds {@code n}.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Map<Variable<?>, Object> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        Map<Variable<?>, Object> reason = new HashMap<>();
        for (Variable<?> var : getVariables()) {
            Domain<Boolean> dom = (Domain<Boolean>) domains.get(var);
            if (!dom.contains(Boolean.FALSE)) reason.put(var, Boolean.TRUE);
        }
        return reason;
    }
}

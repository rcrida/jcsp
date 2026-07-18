package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Represents the "exactly one" constraint for boolean variables in a CSP.
 * This constraint ensures that exactly one of the involved variables is {@code true}.
 *
 * <p>Extends {@link AtMostOneConstraint}: for partial assignments it behaves identically
 * (at most one true), and only when all variables are assigned does it additionally require
 * that exactly one is {@code true}. The inherited pairwise-NAND binary decomposition
 * provides AC3 propagation for the "at most one" half; {@link #propagate} layers "at least
 * one" reasoning on top via the shared {@link AtMostOneConstraint#classify} helper.
 */
@SuperBuilder
public class ExactlyOneConstraint extends AtMostOneConstraint {

    @Override
    protected boolean isSatisfiedByValues(@NonNull Collection<Boolean> values) {
        long trueCount = values.stream().filter(b -> b).count();
        if (trueCount > 1) return false;
        if (values.size() < getVariables().size()) return true;
        return trueCount == 1;
    }

    @Override
    public String getRelation() {
        return "ExactlyOne";
    }

    /**
     * The inherited pairwise-NAND decomposition only rules out two-or-more-true; it says nothing
     * about zero-true, so it is not a sound stand-in for this constraint's full "exactly one true"
     * semantics — unlike {@link AtMostOneConstraint}, whose own weaker "at most one" semantics the
     * same decomposition does fully capture.
     */
    @Override
    public boolean isDecompositionComplete() {
        return false;
    }

    /**
     * Same "at most one" reasoning as {@link AtMostOneConstraint#propagate} (infeasible above one
     * definite true, force the rest false once exactly one is true) plus the dual "at least one"
     * case once zero are yet definitely true: infeasible once no variable can still become true,
     * and once exactly one candidate remains open, it is forced {@code true} (mirrors {@code
     * AtLeastNConstraint} with {@code n} hardcoded to 1) — propagation the inherited pairwise-NAND
     * decomposition alone can never provide, since "at least one true" is an inherently
     * whole-constraint counting condition, not a pairwise relation.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        Classification c = classify(domains);
        if (c.definiteTrue() > 1) return Optional.empty();
        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        if (c.definiteTrue() == 1) {
            for (Variable<Boolean> var : c.possiblyTrue())
                updated.put(var, ((DiscreteDomain<Boolean>) domains.get(var)).toBuilder().delete(Boolean.TRUE).build());
            return Optional.of(updated);
        }
        if (c.possiblyTrue().isEmpty()) return Optional.empty();
        if (c.possiblyTrue().size() == 1) {
            Variable<Boolean> only = c.possiblyTrue().get(0);
            updated.put(only, ((DiscreteDomain<Boolean>) domains.get(only)).toBuilder().delete(Boolean.FALSE).build());
        }
        return Optional.of(updated);
    }

    /**
     * Two infeasibility points, each needing its own reason: more than one definite true reuses
     * {@link AtMostOneConstraint#explainInfeasible}'s reasoning directly (citing every forced-true
     * variable); zero definite true with no remaining candidate attributes the conflict to every
     * variable instead, all of which are forced {@code false} by construction of that branch
     * (mirrors {@code AtLeastNConstraint}'s equivalent "all forced false" case).
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        Classification c = classify(domains);
        if (c.definiteTrue() > 1) return super.explainInfeasible(domains);
        // Only reachable when definiteTrue == 0 and possiblyTrue is empty (propagate()'s other
        // infeasible case) -- by classify()'s own definition that means every variable's domain
        // already excludes TRUE, so there is no conditional left to check here; every variable is
        // unconditionally part of the reason.
        Map<Variable<?>, Object> reason = new HashMap<>();
        for (Variable<?> var : getVariables()) {
            reason.put(var, Boolean.FALSE);
        }
        return GroundNogoodConstraint.fromReason(reason);
    }
}

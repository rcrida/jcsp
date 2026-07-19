package io.github.rcrida.jcsp.constraints.nary;

import lombok.experimental.SuperBuilder;
import lombok.val;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.BinaryDecomposable;
import io.github.rcrida.jcsp.constraints.LogicOperator;
import io.github.rcrida.jcsp.constraints.binary.BinaryConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryLogicConstraint;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Represents the "at most one" constraint for boolean variables in a constraint satisfaction problem (CSP).
 * This constraint ensures that at most one of the involved variables is assigned {@code true}.
 * Suitable for use with {@link io.github.rcrida.jcsp.domains.BooleanDomain}.
 * <p>
 * Implements {@link Propagatable} (as does {@link ExactlyOneConstraint}) in addition to the
 * inherited pairwise-NAND {@link BinaryDecomposable} decomposition: without it, a violation of
 * this constraint is only ever caught by {@link io.github.rcrida.jcsp.assignments.Assignment#isConsistent}'s
 * direct {@code isSatisfiedBy} check, never by propagation — and search-time dom/wdeg weight
 * updates and nogood learning are both gated on a propagation-detected domain wipeout, so a
 * constraint that never propagates never contributes to either, regardless of solver configuration.
 */
@SuperBuilder
public class AtMostOneConstraint extends UniformNaryConstraint<Boolean> implements BinaryDecomposable, Propagatable {

    @Override
    protected boolean isSatisfiedByValues(@NonNull Collection<Boolean> values) {
        return values.stream().filter(b -> b).count() <= 1;
    }

    @Override
    public String getRelation() {
        return "AtMostOne";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<BinaryConstraint<?, ?>> getAsBinaryConstraints() {
        val variables = new ArrayList<>(getVariables());
        val binaryConstraints = new HashSet<BinaryConstraint<?, ?>>();
        for (int i = 0; i < variables.size(); i++) {
            for (int j = i + 1; j < variables.size(); j++) {
                binaryConstraints.add(BinaryLogicConstraint.of(
                        (Variable<Boolean>) variables.get(i),
                        LogicOperator.NAND,
                        (Variable<Boolean>) variables.get(j)));
            }
        }
        return binaryConstraints;
    }

    /** Classification of this constraint's variables against current domain state: how many are
     *  definitely {@code true} (singleton {@code {true}}), and which are still possibly {@code true}
     *  (open {@code {true, false}}). Variables definitely {@code false} contribute to neither list.
     *  Package-private so {@link ExactlyOneConstraint} can reuse it directly (same classification,
     *  different forcing/infeasibility rules on top). */
    @SuppressWarnings("unchecked")
    final Classification classify(@NonNull Map<Variable<?>, Domain<?>> domains) {
        List<Variable<Boolean>> possiblyTrue = new ArrayList<>();
        int definiteTrue = 0;
        for (Variable<?> var : getVariables()) {
            Domain<Boolean> dom = (Domain<Boolean>) domains.get(var);
            if (!dom.contains(Boolean.TRUE)) continue;
            if (!dom.contains(Boolean.FALSE)) definiteTrue++;
            else possiblyTrue.add((Variable<Boolean>) var);
        }
        return new Classification(definiteTrue, possiblyTrue);
    }

    record Classification(int definiteTrue, List<Variable<Boolean>> possiblyTrue) {
    }

    /**
     * At most one of these variables may be {@code true}: infeasible once two are already
     * definitely {@code true}; once exactly one is, every remaining possibly-true variable is
     * forced {@code false} (mirrors {@code AtMostNConstraint} with {@code n} hardcoded to 1, but
     * kept as its own implementation since {@link ExactlyOneConstraint} needs to layer additional
     * "at least one" reasoning on top via the shared {@link #classify} helper).
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
        }
        return Optional.of(updated);
    }

    /**
     * On infeasibility (two or more definitely {@code true}), attributes the conflict to every
     * variable already forced {@code true} — collectively sufficient, and by pigeonhole always
     * reduces to a simple pairwise collision (at least two share the value), same reasoning as
     * {@code AtMostNConstraint}'s equivalent.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        Map<Variable<?>, Object> reason = new HashMap<>();
        for (Variable<?> var : getVariables()) {
            Domain<Boolean> dom = (Domain<Boolean>) domains.get(var);
            if (!dom.contains(Boolean.FALSE)) reason.put(var, Boolean.TRUE);
        }
        return GroundNogoodConstraint.fromReason(reason);
    }
}

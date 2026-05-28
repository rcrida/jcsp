package io.github.rcrida.jcsp.constraints.nary;

import lombok.experimental.SuperBuilder;
import lombok.val;
import io.github.rcrida.jcsp.constraints.binary.BinaryAtMostOneConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryConstraint;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Represents the "exactly one" constraint for boolean variables in a CSP.
 * This constraint ensures that exactly one of the involved variables is {@code true}.
 *
 * <p>For partial assignments it behaves like {@link AtMostOneConstraint} — only when
 * all variables are assigned does it additionally require that at least one is {@code true}.
 * The binary decomposition (pairwise not-both-true) provides AC3 propagation for the
 * "at most one" half; the "at least one" half is enforced by the full predicate check.
 */
@SuperBuilder
public class ExactlyOneConstraint extends UniformNaryConstraint<Boolean> {

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

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Set<BinaryConstraint<?, ?>>> getAsBinaryConstraints() {
        val variables = new ArrayList<>(getVariables());
        val binaryConstraints = new HashSet<BinaryConstraint<?, ?>>();
        for (int i = 0; i < variables.size(); i++) {
            for (int j = i + 1; j < variables.size(); j++) {
                binaryConstraints.add(BinaryAtMostOneConstraint.of(
                        (Variable<Boolean>) variables.get(i),
                        (Variable<Boolean>) variables.get(j)));
            }
        }
        return Optional.of(binaryConstraints);
    }
}

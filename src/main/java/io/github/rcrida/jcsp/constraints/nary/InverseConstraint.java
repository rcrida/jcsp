package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Enforces that two arrays of integer variables are mutual inverses:
 * {@code f[i] == j ↔ invf[j-1] == i+1} for all {@code i} (0-based index, 1-based values).
 * <p>
 * Both arrays must have the same length {@code n} and each variable's domain should
 * be a subset of {@code {1..n}}. Equivalent to MiniZinc's {@code inverse(f, invf)}.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class InverseConstraint extends NaryConstraint implements Propagatable {
    @NonNull private final List<Variable<Integer>> f;
    @NonNull private final List<Variable<Integer>> invf;

    public static InverseConstraint of(@NonNull List<Variable<Integer>> f,
                                       @NonNull List<Variable<Integer>> invf) {
        assert f.size() == invf.size() : "InverseConstraint requires equal-length arrays";
        return InverseConstraint.builder()
                .variables(f)
                .variables(invf)
                .f(List.copyOf(f))
                .invf(List.copyOf(invf))
                .build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        for (int i = 0; i < f.size(); i++) {
            var fi = assignment.getValue(f.get(i));
            if (fi.isEmpty()) return true;
            int j = fi.get(); // 1-based
            var invfj = assignment.getValue(invf.get(j - 1));
            if (invfj.isEmpty()) return true;
            if (!invfj.get().equals(i + 1)) return false;
        }
        return true;
    }

    /**
     * Arc consistency between the two arrays: {@code j ∈ dom(f[i]) ↔ (i+1) ∈ dom(invf[j-1])}.
     * Runs two passes — first pruning invf based on f domains, then pruning f based on the
     * (possibly updated) invf domains.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        int n = f.size();
        Map<Variable<?>, Domain<?>> updated = new HashMap<>();

        // Pass 1: prune invf[j] — remove val if (j+1) ∉ dom(f[val-1])
        for (int j = 0; j < n; j++) {
            DiscreteDomain<Integer> invfDom = (DiscreteDomain<Integer>) domains.get(invf.get(j));
            DiscreteDomain.Builder<Integer> builder = null;
            for (Integer val : invfDom.toList()) {
                DiscreteDomain<Integer> fDom = (DiscreteDomain<Integer>) domains.get(f.get(val - 1));
                if (!fDom.contains(j + 1)) {
                    if (builder == null) builder = invfDom.toBuilder();
                    builder.delete(val);
                }
            }
            if (builder != null) {
                DiscreteDomain<Integer> pruned = builder.build();
                if (pruned.isEmpty()) return Optional.empty();
                updated.put(invf.get(j), pruned);
            }
        }

        // Pass 2: prune f[i] — remove j if (i+1) ∉ dom(invf[j-1]) (using updated invf domains)
        for (int i = 0; i < n; i++) {
            DiscreteDomain<Integer> fDom = (DiscreteDomain<Integer>) domains.get(f.get(i));
            DiscreteDomain.Builder<Integer> builder = null;
            for (Integer j : fDom.toList()) {
                DiscreteDomain<Integer> invfDom = (DiscreteDomain<Integer>) updated.getOrDefault(invf.get(j - 1),
                        domains.get(invf.get(j - 1)));
                if (!invfDom.contains(i + 1)) {
                    if (builder == null) builder = fDom.toBuilder();
                    builder.delete(j);
                }
            }
            if (builder != null) {
                DiscreteDomain<Integer> pruned = builder.build();
                if (pruned.isEmpty()) return Optional.empty();
                updated.put(f.get(i), pruned);
            }
        }

        return Optional.of(updated);
    }

    @Override
    public String getRelation() {
        return "inverse(f=" + f + ", invf=" + invf + ")";
    }
}

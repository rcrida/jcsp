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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
     * Outcome of one propagation pass, shared by {@link #propagate} and {@link #explainInfeasible}
     * so each pass's narrowing/failure-detection logic lives in exactly one place. {@code updated}
     * (passed into every pass method) is mutated in place with any narrowed domains regardless of
     * outcome; {@link #FEASIBLE} means the pass found no infeasibility, {@code infeasible(reason)}
     * means it did, carrying the sound explanation for {@link #explainInfeasible} to return
     * (ignored by {@link #propagate}, which only needs to know a pass failed).
     */
    private record PassOutcome(boolean infeasible, Map<Variable<?>, Object> reason) {
        static final PassOutcome FEASIBLE = new PassOutcome(false, Map.of());
        static PassOutcome infeasible(Map<Variable<?>, Object> reason) { return new PassOutcome(true, reason); }
    }

    @SuppressWarnings("unchecked")
    private DiscreteDomain<Integer> currentDomain(Variable<Integer> v, Map<Variable<?>, Domain<?>> domains,
                                                  Map<Variable<?>, Domain<?>> updated) {
        return (DiscreteDomain<Integer>) updated.getOrDefault(v, domains.get(v));
    }

    /** Pass 1: prune invf[j] — remove val if (j+1) ∉ dom(f[val-1]). */
    private PassOutcome invfPruningPass(Map<Variable<?>, Domain<?>> domains, Map<Variable<?>, Domain<?>> updated) {
        int n = f.size();
        for (int j = 0; j < n; j++) {
            DiscreteDomain<Integer> invfDom = currentDomain(invf.get(j), domains, updated);
            Set<Variable<?>> culprits = new HashSet<>();
            DiscreteDomain.Builder<Integer> builder = null;
            for (Integer val : invfDom.toList()) {
                DiscreteDomain<Integer> fDom = currentDomain(f.get(val - 1), domains, updated);
                if (!fDom.contains(j + 1)) {
                    if (builder == null) builder = invfDom.toBuilder();
                    builder.delete(val);
                    culprits.add(f.get(val - 1));
                }
            }
            if (builder != null) {
                DiscreteDomain<Integer> pruned = builder.build();
                if (pruned.isEmpty()) {
                    Map<Variable<?>, Domain<?>> merged = new HashMap<>(domains);
                    merged.putAll(updated);
                    return PassOutcome.infeasible(Propagatable.allSingletonReason(culprits, merged));
                }
                updated.put(invf.get(j), pruned);
            }
        }
        return PassOutcome.FEASIBLE;
    }

    /** Pass 2: prune f[i] — remove j if (i+1) ∉ dom(invf[j-1]) (using updated invf domains). */
    private PassOutcome fPruningPass(Map<Variable<?>, Domain<?>> domains, Map<Variable<?>, Domain<?>> updated) {
        int n = f.size();
        for (int i = 0; i < n; i++) {
            DiscreteDomain<Integer> fDom = currentDomain(f.get(i), domains, updated);
            Set<Variable<?>> culprits = new HashSet<>();
            DiscreteDomain.Builder<Integer> builder = null;
            for (Integer j : fDom.toList()) {
                DiscreteDomain<Integer> invfDom = currentDomain(invf.get(j - 1), domains, updated);
                if (!invfDom.contains(i + 1)) {
                    if (builder == null) builder = fDom.toBuilder();
                    builder.delete(j);
                    culprits.add(invf.get(j - 1));
                }
            }
            if (builder != null) {
                DiscreteDomain<Integer> pruned = builder.build();
                if (pruned.isEmpty()) {
                    Map<Variable<?>, Domain<?>> merged = new HashMap<>(domains);
                    merged.putAll(updated);
                    return PassOutcome.infeasible(Propagatable.allSingletonReason(culprits, merged));
                }
                updated.put(f.get(i), pruned);
            }
        }
        return PassOutcome.FEASIBLE;
    }

    /**
     * Arc consistency between the two arrays: {@code j ∈ dom(f[i]) ↔ (i+1) ∈ dom(invf[j-1])}.
     * Runs two passes — first pruning invf based on f domains, then pruning f based on the
     * (possibly updated) invf domains.
     */
    @Override
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        if (invfPruningPass(domains, updated).infeasible()) return Optional.empty();
        if (fPruningPass(domains, updated).infeasible()) return Optional.empty();
        return Optional.of(updated);
    }

    /**
     * Replays {@link #propagate}'s two passes via the same {@link #invfPruningPass}/
     * {@link #fPruningPass} helpers (threading pass 1's updates into pass 2 exactly as
     * {@code propagate} does) to find the same emptied domain. At that point, the emptied
     * variable's every candidate value was excluded by some variable on the opposite array — those
     * "opposite" variables are the culprits. Sound only when every culprit is currently singleton,
     * via {@link Propagatable#allSingletonReason}: a non-singleton excluding variable could still
     * be assigned a value later that resolves the exclusion, so citing it as a nogood requires its
     * value to already be pinned.
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        PassOutcome pass1 = invfPruningPass(domains, updated);
        if (pass1.infeasible()) return GroundNogoodConstraint.fromReason(pass1.reason());
        PassOutcome pass2 = fPruningPass(domains, updated);
        if (pass2.infeasible()) return GroundNogoodConstraint.fromReason(pass2.reason());
        return Optional.empty();
    }

    @Override
    public String getRelation() {
        return "inverse(f=" + f + ", invf=" + invf + ")";
    }
}

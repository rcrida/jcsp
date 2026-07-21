package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * An n-ary constraint that assigns each item to a bin without exceeding any bin's capacity:
 * {@code sum(weights[i] : bin[i] == b) <= capacities[b]} for every bin {@code b}. Equivalent to
 * MiniZinc's {@code bin_packing_capa(capacities, bin, weights)} constraint.
 * <p>
 * {@code bin} holds the decision variables (0-indexed bin number per item); {@code weights} and
 * {@code capacities} are fixed data, not variables — only the assignment is a decision, matching
 * the standard formulation. Discrete-only: not whitelisted for {@code BoundedDomain} variables,
 * same precedent as {@link AllDiffConstraint} and {@link NValueConstraint} (a bin index has no
 * continuous analogue).
 * <p>
 * This constraint only enforces capacity. "Minimise the number of bins used" — the actual
 * optimization goal in most problems that need bin-packing — composes for free by pairing this
 * with {@link NValueConstraint} over the same {@code bin} variables
 * ({@code nValueConstraint(Set.copyOf(bin), count)}, then minimise {@code count}): no separate
 * machinery is needed for that half.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class BinPackingConstraint extends NaryConstraint implements Propagatable {
    @NonNull private final List<Variable<Integer>> bin;
    @NonNull private final List<Integer> weights;
    @NonNull private final List<Integer> capacities;

    public static BinPackingConstraint of(@NonNull List<Variable<Integer>> bin,
                                           @NonNull List<Integer> weights,
                                           @NonNull List<Integer> capacities) {
        assert bin.size() == weights.size() : "bin and weights must have equal length";
        Set<Variable<?>> allVars = new LinkedHashSet<>(bin);
        return BinPackingConstraint.builder()
                .variables(allVars)
                .bin(List.copyOf(bin))
                .weights(List.copyOf(weights))
                .capacities(List.copyOf(capacities))
                .build();
    }

    /**
     * Accumulates each bin's load from assigned items only, failing as soon as any bin's load
     * (which can only grow as more items are assigned) exceeds its capacity — sound as an early
     * failure for partial assignments. Otherwise satisfied: unlike {@link
     * GlobalCardinalityConstraint}/{@link NValueConstraint}, there is no lower-bound half to this
     * constraint, so "not yet fully assigned, no violation found" and "fully assigned, no
     * violation" both simply fall through to {@code true}.
     */
    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        int[] load = new int[capacities.size()];
        for (int i = 0; i < bin.size(); i++) {
            Optional<Integer> b = assignment.getValue(bin.get(i));
            if (b.isPresent()) load[b.get()] += weights.get(i);
        }
        for (int b = 0; b < capacities.size(); b++) {
            if (load[b] > capacities.get(b)) return false;
        }
        return true;
    }

    /**
     * Single pass, no internal fixpoint iteration needed (simpler than {@link NValueConstraint}:
     * bin-packing has no dual-direction bound narrowing, only ever "would this overload the
     * bin"):
     * <ol>
     *   <li>Classify each item as <em>definite</em> (singleton {@code bin} domain) or
     *       <em>open</em>; accumulate {@code definiteLoad[b]} from definite items only.</li>
     *   <li>If any {@code definiteLoad[b] > capacities[b]}: infeasible.</li>
     *   <li>For each open item, remove any candidate bin {@code b} where
     *       {@code definiteLoad[b] + weight > capacities[b]} — the item cannot join a bin without
     *       exceeding what's already committed there. If this empties an item's domain:
     *       infeasible. No separate "force to the remaining bin" step is needed — pruning down to
     *       one candidate naturally leaves a singleton as a side effect of the same loop.</li>
     * </ol>
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        Classification c = classify(domains);
        for (int b = 0; b < capacities.size(); b++) {
            if (c.definiteLoad()[b] > capacities.get(b)) return Optional.empty();
        }

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        for (int i : c.openIndices()) {
            DiscreteDomain<Integer> dom = (DiscreteDomain<Integer>) domains.get(bin.get(i));
            var builder = dom.toBuilder();
            boolean changed = false;
            for (Integer b : dom.toList()) {
                if (c.definiteLoad()[b] + weights.get(i) > capacities.get(b)) {
                    builder.delete(b);
                    changed = true;
                }
            }
            if (changed) {
                DiscreteDomain<Integer> newDom = builder.build();
                if (newDom.isEmpty()) return Optional.empty();
                updated.put(bin.get(i), newDom);
            }
        }
        return Optional.of(updated);
    }

    /**
     * Replays {@link #propagate}'s classification (cheap, side-effect-free — simply repeated
     * rather than shared). Exactly one of the two infeasibility points is explainable:
     * <ul>
     *   <li><b>{@code definiteLoad[b] > capacities[b]}</b>: cites every definite item assigned to
     *       bin {@code b}, at its singleton value. Unlike {@link GlobalCardinalityConstraint}'s
     *       and {@link NValueConstraint}'s analogous branches, this is <em>unconditionally</em>
     *       sound and always succeeds when reached — every cited variable is singleton by
     *       construction (that's what "definite" means), so there is no {@link
     *       Propagatable#allSingletonReason} gating failure possible here. Sound because bin
     *       {@code b}'s capacity is fixed structural data: if these items' ground bin assignments
     *       alone already overload it, no completion of any other variable can ever fix that.</li>
     *   <li><b>An open item's domain emptied by pruning</b>: deliberately not attempted, for the
     *       same structural reason {@link NValueConstraint#explainInfeasible} skips its analogous
     *       case — soundly citing it would require asserting the pruned item's own domain was
     *       exactly its current candidate set, which isn't expressible as cited ground values (a
     *       search-time-narrowed domain isn't part of the constraint's fixed structural data the
     *       way a capacity is). Falls through to {@link Optional#empty()}.</li>
     * </ul>
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        Classification c = classify(domains);
        for (int b = 0; b < capacities.size(); b++) {
            if (c.definiteLoad()[b] > capacities.get(b)) {
                Set<Variable<?>> cited = new HashSet<>(c.definiteItemsPerBin().get(b));
                return GroundNogoodConstraint.fromReason(Propagatable.allSingletonReason(cited, domains));
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private Classification classify(@NonNull Map<Variable<?>, Domain<?>> domains) {
        int[] definiteLoad = new int[capacities.size()];
        List<List<Variable<?>>> definiteItemsPerBin = new ArrayList<>(capacities.size());
        for (int b = 0; b < capacities.size(); b++) definiteItemsPerBin.add(new ArrayList<>());
        List<Integer> openIndices = new ArrayList<>();

        for (int i = 0; i < bin.size(); i++) {
            DiscreteDomain<Integer> dom = (DiscreteDomain<Integer>) domains.get(bin.get(i));
            if (dom.isSingleton()) {
                int b = dom.singleValue().get();
                definiteLoad[b] += weights.get(i);
                definiteItemsPerBin.get(b).add(bin.get(i));
            } else {
                openIndices.add(i);
            }
        }
        return new Classification(definiteLoad, definiteItemsPerBin, openIndices);
    }

    private record Classification(int[] definiteLoad, List<List<Variable<?>>> definiteItemsPerBin,
                                   List<Integer> openIndices) {}

    @Override
    public String getRelation() {
        return "binPacking(bins=" + capacities.size() + ", items=" + bin.size() + ")";
    }
}

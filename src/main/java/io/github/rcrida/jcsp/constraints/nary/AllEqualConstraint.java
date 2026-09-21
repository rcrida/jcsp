package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.BinaryDecomposable;
import io.github.rcrida.jcsp.constraints.binary.BinaryConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryEqualsConstraint;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An n-ary constraint requiring every variable to take the same value: {@code v1 == v2 == ... ==
 * vn}.
 * <p>
 * Unlike {@link AllDiffConstraint}, whose generalized arc consistency requires genuinely joint
 * (Hall-set) reasoning that a pairwise decomposition provably loses, {@code allEqual}'s GAC
 * decomposes losslessly into pairwise equality along any connected chain — every variable ends up
 * narrowed to the same domain intersection either way. This class exists purely for convergence
 * speed, not propagation strength a chain of {@link BinaryEqualsConstraint}s couldn't eventually
 * reach: {@link #propagate} computes the shared value intersection directly in one pass, rather
 * than needing up to {@code n} outer fixpoint rounds for a restriction to propagate end-to-end
 * along an {@code n}-long chain (a real cost for, e.g., a 300-variable {@code allEqual}). Because
 * the decomposition is lossless, {@link #getAsBinaryConstraints} still offers it — {@link
 * io.github.rcrida.jcsp.solver.LocalSolver local search}'s per-pair conflict scoring benefits from
 * the finer granularity a single n-ary check can't give it.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class AllEqualConstraint<T> extends UniformNaryConstraint<T> implements Propagatable, BinaryDecomposable {

    public static <T> AllEqualConstraint<T> of(@NonNull Set<Variable<T>> variables) {
        return AllEqualConstraint.<T>builder().variables(variables).build();
    }

    @Override
    protected boolean isSatisfiedByValues(@NonNull Collection<T> values) {
        Iterator<T> it = values.iterator();
        if (!it.hasNext()) return true;
        T first = it.next();
        while (it.hasNext()) {
            if (!Objects.equals(first, it.next())) return false;
        }
        return true;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Set<BinaryConstraint<?, ?>> getAsBinaryConstraints() {
        List<Variable<T>> vars = new ArrayList<>((Set<Variable<T>>) (Set<?>) getVariables());
        Set<BinaryConstraint<?, ?>> pairs = new HashSet<>();
        for (int i = 0; i + 1 < vars.size(); i++) {
            pairs.add(BinaryEqualsConstraint.of((Variable) vars.get(i), (Variable) vars.get(i + 1)));
        }
        return pairs;
    }

    /**
     * Narrows every variable to {@code shared}, the set of values present in every variable's
     * domain — full generalized arc consistency in one pass. {@code shared} is computed by
     * intersecting domain value-sets directly rather than working through {@link
     * io.github.rcrida.jcsp.constraints.NumericBounds}-style bounds, since {@code T} isn't
     * restricted to {@link Number} (equality is well-defined over any type).
     * <p>
     * Both passes avoid materialising each variable's values, which matters because search re-runs
     * this once per node for as long as any of its variables is still unassigned — including long
     * after propagation has settled the lot. On {@code Domino-300-300.xml.lzma}, a single
     * 300-variable {@code allEqual}, the whole solve takes 304 nodes and 313 constraint checks, yet
     * ~300 of those nodes re-derive an unchanged answer; doing that with a list per variable per
     * pass dominated the solve.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        List<Variable<T>> vars = new ArrayList<>((Set<Variable<T>>) (Set<?>) getVariables());
        DiscreteDomain<T> first = (DiscreteDomain<T>) domains.get(vars.get(0));
        Set<T> shared = new LinkedHashSet<>(first.toList());
        for (int i = 1; i < vars.size() && !shared.isEmpty(); i++) {
            DiscreteDomain<T> dom = (DiscreteDomain<T>) domains.get(vars.get(i));
            // Equivalent to retainAll(dom.toList()), without building that list: shared only ever
            // shrinks, so testing its members against the domain is the same intersection.
            shared.removeIf(value -> !dom.contains(value));
        }
        if (shared.isEmpty()) return Optional.empty();

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        for (Variable<T> var : vars) {
            DiscreteDomain<T> dom = (DiscreteDomain<T>) domains.get(var);
            // shared is a subset of every domain by construction, so equal sizes mean equal sets
            // and there is nothing to delete -- the common case once propagation has settled.
            if (dom.size() == shared.size()) continue;
            // Past that guard the domain is strictly larger than shared, so at least one value goes
            // and the builder is always needed: no point deferring its creation.
            DiscreteDomain.Builder<T> builder = dom.toBuilder();
            for (T val : dom.toList()) {
                if (!shared.contains(val)) {
                    builder.delete(val);
                }
            }
            // Never empty: `shared` is nonempty and, by construction, a subset of every
            // variable's own domain, so at least `shared`'s values always survive.
            updated.put(var, builder.build());
        }
        return Optional.of(updated);
    }

    /**
     * Tries the tightest available explanation first: a pair of variables whose current domains
     * already share no value is a minimal, self-contained violation, regardless of every other
     * variable — the same "smallest sound culprit first" spirit {@link
     * AllDiffConstraint#explainInfeasible} uses for its Hall-violating subset. Most real {@code
     * allEqual} infeasibilities are exactly this (two directly conflicting variables); falls back
     * to the fully collective reason over every variable — mirroring {@link
     * SumBoundConstraint#explainInfeasible} — only for the rarer case where no single pair is
     * disjoint but the joint intersection is still empty (e.g. three domains {@code {1,2}},
     * {@code {2,3}}, {@code {1,3}}: every pair intersects, but no value is common to all three).
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        List<Variable<T>> vars = new ArrayList<>((Set<Variable<T>>) (Set<?>) getVariables());
        for (int i = 0; i < vars.size(); i++) {
            DiscreteDomain<T> di = (DiscreteDomain<T>) domains.get(vars.get(i));
            for (int j = i + 1; j < vars.size(); j++) {
                DiscreteDomain<T> dj = (DiscreteDomain<T>) domains.get(vars.get(j));
                if (di.stream().noneMatch(dj::contains)) {
                    List<Variable<?>> pair = List.of(vars.get(i), vars.get(j));
                    Map<Variable<?>, Object> ground = Propagatable.allSingletonReason(pair, domains);
                    if (!ground.isEmpty()) return GroundNogoodConstraint.fromReason(ground);
                    Optional<NogoodConstraint> range = RangeNogoodConstraint.fromCurrentBounds(pair, domains);
                    if (range.isPresent()) return range;
                }
            }
        }
        return RangeNogoodConstraint.fromCurrentBounds(getVariables(), domains)
                .or(() -> GroundNogoodConstraint.fromReason(Propagatable.allSingletonReason(getVariables(), domains)));
    }

    @Override
    public String getRelation() {
        return getVariables().stream().map(Object::toString).sorted().collect(Collectors.joining(" == "));
    }
}

package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.constraints.BinaryDecomposable;
import io.github.rcrida.jcsp.constraints.binary.BinaryConstraint;
import io.github.rcrida.jcsp.constraints.binary.DisjointConstraint;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.SetBoundedDomain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
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
 * An n-ary constraint requiring {@code parts} to partition a fixed {@code universe}: every element
 * of {@code universe} belongs to exactly one of {@code parts}, and no element of any part lies
 * outside {@code universe}. Equivalent to MiniZinc's {@code partition_set(parts, universe)}.
 * {@code universe} is fixed data, not a variable — only the parts' membership is a decision,
 * matching {@link BinPackingConstraint}'s precedent for fixed capacities alongside variable
 * assignments. {@code parts} is a {@code Set}, not a {@code List} — unlike {@code
 * BinPackingConstraint}'s {@code bin} (positionally paired with a parallel {@code weights} list),
 * there's no ordering dependency between parts, matching {@link AllDiffConstraint}/{@link
 * AtMostOneConstraint}'s precedent; a {@code Set} also rules out the same variable being passed
 * twice as two different "parts" by construction, which a {@code List} wouldn't.
 * <p>
 * Unlike {@link NValueConstraint} (which keeps its own {@code trackedVariables} field distinct
 * from the inherited {@code variables}, since it has one further variable — {@code count} — beyond
 * that set), {@code parts} <em>is</em> exactly {@link #getVariables()} here, so no separate field
 * is kept at all. {@link #propagate}/{@link #classify} — called repeatedly over the course of a
 * solve, unlike {@link #getAsBinaryConstraints}, which runs once at graph-construction time — look
 * up each part's domain directly from the supplied domains map by variable, keeping {@code
 * Variable<?>} untyped rather than casting {@code getVariables()} to an ordered, typed copy first;
 * {@link #getAsBinaryConstraints} still needs the typed view (to pass to {@link
 * DisjointConstraint#of}), obtained via the same cast-back pattern {@link AllDiffConstraint}
 * already uses.
 * <p>
 * Implements {@link BinaryDecomposable} via pairwise {@link DisjointConstraint}s (for AC3/{@code
 * ConstraintGraph} purposes) but {@link #isDecompositionComplete()} is {@code false}: pairwise
 * disjointness alone only rules out an element landing in two parts at once, it says nothing
 * about an element landing in <em>no</em> part — the same shape of gap {@code ExactlyOneConstraint}
 * documents for its own "at least one true" half, which its inherited pairwise-NAND decomposition
 * can't express either. {@link #propagate} layers that "coverage" reasoning on top, the way {@link
 * ExactlyOneConstraint#propagate} layers "at least one" on top of {@link
 * io.github.rcrida.jcsp.constraints.nary.AtMostOneConstraint}'s inherited decomposition.
 * <p>
 * Deliberately doesn't proactively narrow a part's upper bound down to {@code universe} even
 * though a part's own domain including a candidate element outside {@code universe} is meaningless
 * — every realistic construction already bounds each part's domain to (a subset of) {@code
 * universe} directly, the same "unexpected in practice" reasoning {@link DisjointConstraint} and
 * {@link io.github.rcrida.jcsp.constraints.binary.SubsetConstraint} already give for a
 * non-{@link SetBoundedDomain} side. {@link #isSatisfiedByValues} still checks it independently,
 * though, since {@code universe} is passed as a separate constructor argument with nothing
 * enforcing it matches each part's own domain — a real (if unlikely) modelling mismatch, unlike
 * the domain-kind case above.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class PartitionConstraint<E> extends UniformNaryConstraint<Set<E>> implements Propagatable, BinaryDecomposable {
    @NonNull private final Set<E> universe;

    @SuppressWarnings("unchecked")
    public static <E> PartitionConstraint<E> of(@NonNull Set<Variable<Set<E>>> parts, @NonNull Set<E> universe) {
        return PartitionConstraint.<E>builder()
                .variables((Set<Variable<?>>) (Set<?>) Set.copyOf(parts))
                .universe(Set.copyOf(universe))
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<Variable<Set<E>>> orderedParts() {
        return new ArrayList<>((Set<Variable<Set<E>>>) (Set<?>) getVariables());
    }

    /**
     * Every element seen across the currently-assigned parts must be unique (no element claimed
     * by two parts) and must belong to {@code universe}; once every part is assigned, their
     * combined elements must equal {@code universe} exactly (coverage) — checked only once
     * complete, since a partial assignment obviously hasn't covered everything yet and that's not
     * itself a violation.
     */
    @Override
    protected boolean isSatisfiedByValues(@NonNull Collection<Set<E>> values) {
        Set<E> seen = new HashSet<>();
        for (Set<E> part : values) {
            for (E e : part) {
                if (!universe.contains(e)) return false;
                if (!seen.add(e)) return false;
            }
        }
        return values.size() < getVariables().size() || seen.equals(universe);
    }

    @Override
    public String getRelation() {
        return "partition(parts=" + getVariables().size() + ", |universe|=" + universe.size() + ")";
    }

    /**
     * Only {@link SetBoundedDomain}-typed parts are narrowed; a non-{@code SetBoundedDomain} part
     * (unexpected in practice, same reasoning {@link DisjointConstraint} gives) leaves this
     * constraint's propagation as a no-op for the whole call, matching {@link
     * io.github.rcrida.jcsp.constraints.binary.SubsetConstraint}'s all-or-nothing type check.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        for (Variable<?> part : getVariables()) {
            if (!(domains.get(part) instanceof SetBoundedDomain<?>)) return Optional.of(Map.of());
        }

        Classification<E> c = classify(domains);
        if (c.infeasible()) return Optional.empty();

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        for (Variable<?> part : getVariables()) {
            SetBoundedDomain<E> dom = (SetBoundedDomain<E>) domains.get(part);
            SetBoundedDomain<E> narrowed = dom;
            if (c.excluded().containsKey(part)) {
                Set<E> newUpper = new HashSet<>(narrowed.getUpperBound());
                newUpper.removeAll(c.excluded().get(part));
                narrowed = narrowed.withUpperBound(newUpper);
            }
            if (c.forcedIn().containsKey(part)) {
                narrowed = narrowed.withLowerBound(c.forcedIn().get(part));
            }
            if (narrowed.isEmpty()) return Optional.empty();
            if (!narrowed.equals(dom)) updated.put(part, narrowed);
        }
        return Optional.of(updated);
    }

    /**
     * Classifies every element of {@code universe} against each part's current bounds, looked up
     * directly from {@code domains} by variable (no ordered copy of the parts kept around, since
     * {@code propagate} is called repeatedly over the course of a solve), following the same
     * "definite/undetermined" shape {@link
     * io.github.rcrida.jcsp.constraints.binary.IntersectionCardinalityConstraint} already uses,
     * generalised from a pair to {@code n} parts and with a second, dual forcing direction added:
     * <ul>
     *   <li>2+ parts already have {@code e} forced into their lower bound: infeasible — {@code e}
     *       can't belong to two disjoint parts at once.</li>
     *   <li>exactly 1 part has {@code e} forced in: every <em>other</em> part still candidating
     *       {@code e} has it excluded from their upper bound (same forcing rule {@link
     *       DisjointConstraint} already has, generalised to {@code n} parts).</li>
     *   <li>no part has {@code e} forced in, and no part even candidates it: infeasible — {@code
     *       e} has nowhere left to go, violating coverage.</li>
     *   <li>no part has {@code e} forced in, and exactly one part still candidates it: that part
     *       is <em>forced</em> to include {@code e} — the genuinely new direction beyond {@code
     *       Disjoint}, which never needs to widen a lower bound.</li>
     *   <li>no part has {@code e} forced in, and 2+ parts still candidate it: a genuine
     *       disjunctive choice, left unresolved — the same bounds-consistency-not-GAC ceiling
     *       {@code IntersectionCardinalityConstraint}/{@code NValueConstraint} document.</li>
     * </ul>
     */
    private Classification<E> classify(Map<Variable<?>, Domain<?>> domains) {
        Map<Variable<?>, Set<E>> excluded = new HashMap<>();
        Map<Variable<?>, Set<E>> forcedIn = new HashMap<>();

        for (E e : universe) {
            int definiteCount = 0;
            List<Variable<?>> undetermined = new ArrayList<>();
            for (Variable<?> part : getVariables()) {
                SetBoundedDomain<?> dom = (SetBoundedDomain<?>) domains.get(part);
                if (dom.getLowerBound().contains(e)) {
                    definiteCount++;
                } else if (dom.getUpperBound().contains(e)) {
                    undetermined.add(part);
                }
            }
            if (definiteCount > 1) return new Classification<>(true, Map.of(), Map.of());
            if (definiteCount == 1) {
                for (Variable<?> part : undetermined) {
                    excluded.computeIfAbsent(part, k -> new HashSet<>()).add(e);
                }
                continue;
            }
            if (undetermined.isEmpty()) return new Classification<>(true, Map.of(), Map.of());
            if (undetermined.size() == 1) {
                forcedIn.computeIfAbsent(undetermined.get(0), k -> new HashSet<>()).add(e);
            }
        }
        return new Classification<>(false, excluded, forcedIn);
    }

    private record Classification<T>(boolean infeasible, Map<Variable<?>, Set<T>> excluded, Map<Variable<?>, Set<T>> forcedIn) {}

    /**
     * Two infeasibility points, mirroring {@code ExactlyOneConstraint}'s own two-branch reasoning
     * (a targeted subset for "too many", the full collective for "none left"):
     * <ul>
     *   <li><b>2+ parts force the same element {@code e}</b>: those parts alone are already a
     *       sound, tight reason — structurally identical to a {@link DisjointConstraint} violation
     *       between just that pair (or larger clique), regardless of the other parts' states — so
     *       {@link SetBoundsNogoodConstraint#explainViaGroundOrBounds} is called scoped to just
     *       the implicated parts, not every part.</li>
     *   <li><b>{@code e} has no part left that could contain it</b>: every part is implicated
     *       (any one of them could have been the one to still candidate {@code e}, but none do),
     *       so the reason needs the full part set.</li>
     * </ul>
     * Both branches funnel through the same shared helper the other set constraints use, rather
     * than reimplementing the ground-then-bounds fallback here.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        for (E e : universe) {
            List<Variable<Set<E>>> definite = new ArrayList<>();
            boolean anyUndetermined = false;
            for (Variable<?> part : getVariables()) {
                SetBoundedDomain<?> dom = (SetBoundedDomain<?>) domains.get(part);
                if (dom.getLowerBound().contains(e)) definite.add((Variable<Set<E>>) part);
                else if (dom.getUpperBound().contains(e)) anyUndetermined = true;
            }
            if (definite.size() > 1) {
                return SetBoundsNogoodConstraint.explainViaGroundOrBounds(definite, domains);
            }
            if (definite.isEmpty() && !anyUndetermined) {
                return SetBoundsNogoodConstraint.explainViaGroundOrBounds(getVariables(), domains);
            }
        }
        return Optional.empty();
    }

    /**
     * Pairwise {@link DisjointConstraint}s over every part pair — the "at most one part per
     * element" half of this constraint's semantics; see the class Javadoc for why {@link
     * #isDecompositionComplete()} is {@code false}.
     */
    @Override
    public Set<BinaryConstraint<?, ?>> getAsBinaryConstraints() {
        List<Variable<Set<E>>> ordered = orderedParts();
        Set<BinaryConstraint<?, ?>> binaryConstraints = new HashSet<>();
        for (int i = 0; i < ordered.size(); i++) {
            for (int j = i + 1; j < ordered.size(); j++) {
                binaryConstraints.add(DisjointConstraint.of(ordered.get(i), ordered.get(j)));
            }
        }
        return binaryConstraints;
    }

    @Override
    public boolean isDecompositionComplete() {
        return false;
    }
}

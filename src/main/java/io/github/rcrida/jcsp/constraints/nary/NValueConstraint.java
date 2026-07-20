package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An n-ary constraint linking {@code count} to the number of distinct values taken by
 * {@code trackedVariables}: {@code count == |{ v.value : v in trackedVariables }|}.
 * Equivalent to MiniZinc's {@code nvalue(count, trackedVariables)} constraint. Generalises
 * {@link AllDiffConstraint} (which is equivalent to fixing {@code count} to
 * {@code trackedVariables.size()}).
 * <p>
 * {@code count} is a genuine decision variable rather than a fixed constant + {@link
 * io.github.rcrida.jcsp.constraints.Operator}, unlike e.g. {@code SumConstraint}. This lets a
 * caller hand {@code count} directly to an optimization objective ({@code a ->
 * (Integer) a.getValue(count).orElseThrow()}) to minimise the number of distinct values used —
 * the motivating use case (course/template/slab "minimise resources used" problems) for this
 * constraint. Fixing {@code count} to a singleton domain recovers the fixed-bound use case too.
 * <p>
 * Propagation is bounds-consistency, not full generalised arc consistency: GAC-nvalue is
 * NP-hard (Bessiere et al. 2006), so — like every constraint in this codebase except {@link
 * AllDiffConstraint}'s Régin algorithm — no attempt is made at a full GAC filtering algorithm
 * here. See {@link #propagate} for exactly what is and isn't filtered. Discrete-only: not
 * whitelisted for {@code BoundedDomain} variables, same precedent as {@link AllDiffConstraint},
 * {@link CountConstraint}, {@link AmongConstraint}, and {@link GlobalCardinalityConstraint}
 * (distinct-value counting doesn't have a meaningful analogue over a dense/continuous range).
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class NValueConstraint<T> extends NaryConstraint implements Propagatable {
    @NonNull private final Set<Variable<T>> trackedVariables;
    @NonNull private final Variable<Integer> count;

    public static <T> NValueConstraint<T> of(@NonNull Set<Variable<T>> trackedVariables,
                                              @NonNull Variable<Integer> count) {
        Set<Variable<?>> allVars = new LinkedHashSet<>(trackedVariables);
        allVars.add(count);
        return NValueConstraint.<T>builder()
                .variables(allVars)
                .trackedVariables(Set.copyOf(trackedVariables))
                .count(count)
                .build();
    }

    /**
     * Optimistic until every tracked variable is assigned, with one early-failure check: once
     * {@code count} is assigned, the number of distinct values already committed can only grow
     * as more variables are assigned, so exceeding it is a permanent violation (mirrors {@link
     * GlobalCardinalityConstraint}'s early-exceeded check). {@code count} itself unassigned is
     * treated as optimistically satisfied, mirroring {@link NaryElementConstraint}'s "wait for
     * the linking variable" style.
     */
    @Override
    @SuppressWarnings("unchecked")
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        var assigned = assignment.getValues().keySet();
        if (!assigned.contains(count)) return true;

        Set<T> distinct = new HashSet<>();
        int assignedCount = 0;
        for (Variable<T> v : trackedVariables) {
            if (assigned.contains(v)) {
                distinct.add((T) assignment.getValue(v).orElseThrow());
                assignedCount++;
            }
        }
        int target = (Integer) assignment.getValue(count).orElseThrow();
        if (distinct.size() > target) return false;
        if (assignedCount < trackedVariables.size()) return true;
        return distinct.size() == target;
    }

    /**
     * Classifies each tracked variable as <em>definite</em> (singleton domain) or <em>open</em>
     * (not), then narrows {@code count} to {@code [lowerBound, upperBound]}:
     * <ul>
     *   <li>{@code lowerBound = |definiteValues|} — already-guaranteed distinct values.</li>
     *   <li>{@code upperBound = |definiteValues| + min(|openVars|, |openValues|)}, where
     *       {@code openValues} is the union of every open variable's domain minus
     *       {@code definiteValues} — each open variable can contribute at most one new distinct
     *       value, and there can never be more new values than exist in the open variables'
     *       combined domains.</li>
     * </ul>
     * If narrowing {@code count} forces it to a singleton {@code k}, two further (mutually
     * exclusive, since {@code lowerBound <= upperBound} always) forcing steps apply:
     * <ul>
     *   <li>{@code k == lowerBound}: no open variable may introduce a new value, so every open
     *       variable's domain is intersected with {@code definiteValues} ({@link
     *       #narrowOpenToDefinite}).</li>
     *   <li>{@code k == upperBound}, <em>and</em> the bound was capped by variable count rather
     *       than value supply ({@code |openVars| <= |openValues|}): every open variable is
     *       individually required to contribute a genuinely new value, so {@code definiteValues}
     *       is removed from every open variable's domain ({@link #narrowOpenAwayFromDefinite}).
     *       When the bound was instead capped by value supply ({@code |openVars| >
     *       |openValues|}), only <em>some</em> open variables need a new value and the rest may
     *       still repeat a definite one — which ones is a matching/Hall-set question ({@link
     *       AllDiffConstraint}-shaped), deliberately out of scope here, same kind of scope line as
     *       {@code DiffnConstraint}'s compulsory-part-only propagation.</li>
     * </ul>
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        Classification<T> c = classify(domains);
        int definiteCount = c.definiteValues().size();
        int maxNew = Math.min(c.openVars().size(), c.openValues().size());
        int lowerBound = definiteCount;
        int upperBound = definiteCount + maxNew;

        DiscreteDomain<Integer> countDomain = (DiscreteDomain<Integer>) domains.get(count);
        Map<Variable<?>, Domain<?>> updated = new HashMap<>();

        var builder = countDomain.toBuilder();
        boolean countChanged = false;
        for (Integer k : countDomain.toList()) {
            if (k < lowerBound || k > upperBound) {
                builder.delete(k);
                countChanged = true;
            }
        }
        DiscreteDomain<Integer> newCountDomain = countDomain;
        if (countChanged) {
            newCountDomain = (DiscreteDomain<Integer>) builder.build();
            if (newCountDomain.isEmpty()) return Optional.empty();
            updated.put(count, newCountDomain);
        }

        if (newCountDomain.isSingleton()) {
            int k = newCountDomain.singleValue().get();
            if (k == definiteCount) {
                NarrowOutcome<T> outcome = narrowOpenToDefinite(domains, c);
                if (outcome.emptiedVariable() != null) return Optional.empty();
                updated.putAll(outcome.updated());
            } else if (k == upperBound && c.openVars().size() <= c.openValues().size()) {
                NarrowOutcome<T> outcome = narrowOpenAwayFromDefinite(domains, c);
                if (outcome.emptiedVariable() != null) return Optional.empty();
                updated.putAll(outcome.updated());
            }
        }

        return Optional.of(updated);
    }

    /**
     * Replays {@link #propagate}'s classification and bound computation (recomputing this
     * arithmetic is cheap and side-effect-free, so it's simply repeated rather than shared).
     * Only one of {@code propagate}'s two infeasibility points is ever explainable here:
     * <ul>
     *   <li><b>{@code count}'s domain wiped</b> by the {@code [lowerBound, upperBound]} filter:
     *       sound only when every surviving candidate was excluded from the same side (all below
     *       {@code lowerBound}, or all above {@code upperBound}) — a domain with candidates
     *       excluded from <em>both</em> sides (a gap spanning the whole valid range) has no
     *       single-sided reason and returns {@link Optional#empty()}. The below-side reason cites
     *       the definite variables (already singleton by construction) plus {@code count} itself;
     *       the above-side reason additionally cites the open variables, since the upper bound
     *       depends on their domains too. Both go through {@link
     *       Propagatable#allSingletonReason}, so — like {@link
     *       GlobalCardinalityConstraint}'s analogous branch — this legitimately returns empty
     *       whenever the cited open/count variables aren't all singleton; the caller falls back
     *       to the full-assignment nogood in that case.</li>
     *   <li><b>A forcing step ({@link #narrowOpenToDefinite}/{@link
     *       #narrowOpenAwayFromDefinite}) empties an open variable</b>: deliberately <em>not</em>
     *       attempted here, and not merely as a scope cut — it's structurally impossible to
     *       explain as a {@link GroundNogoodConstraint}. Every variable either forcing step could
     *       cite to justify emptying that variable is, by {@link #classify}'s definition of
     *       "open", non-singleton in these exact {@code domains} (that's precisely why it was
     *       classified open rather than definite), so {@link Propagatable#allSingletonReason}
     *       would unconditionally fail for it — there is no ground value to blame, since the
     *       violation comes from the open variable's <em>whole</em> current domain missing
     *       {@code definiteValues} entirely, not from any one value in it. Falls through to
     *       {@link Optional#empty()} for this case, same as the codebase's other "no sound
     *       reason available" fallbacks.</li>
     * </ul>
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        Classification<T> c = classify(domains);
        int definiteCount = c.definiteValues().size();
        int maxNew = Math.min(c.openVars().size(), c.openValues().size());
        int lowerBound = definiteCount;
        int upperBound = definiteCount + maxNew;

        DiscreteDomain<Integer> countDomain = (DiscreteDomain<Integer>) domains.get(count);
        boolean anySurvives = countDomain.stream().anyMatch(k -> k >= lowerBound && k <= upperBound);
        if (anySurvives) return Optional.empty();

        boolean allBelow = countDomain.stream().allMatch(k -> k < lowerBound);
        boolean allAbove = countDomain.stream().allMatch(k -> k > upperBound);
        if (!allBelow && !allAbove) return Optional.empty();
        Set<Variable<?>> cited = new HashSet<>(c.definiteVars());
        cited.add(count);
        if (allAbove) cited.addAll(c.openVars());
        return GroundNogoodConstraint.fromReason(Propagatable.allSingletonReason(cited, domains));
    }

    @SuppressWarnings("unchecked")
    private Classification<T> classify(@NonNull Map<Variable<?>, Domain<?>> domains) {
        Set<T> definiteValues = new HashSet<>();
        List<Variable<T>> definiteVars = new ArrayList<>();
        List<Variable<T>> openVars = new ArrayList<>();
        for (Variable<T> v : trackedVariables) {
            DiscreteDomain<T> dom = (DiscreteDomain<T>) domains.get(v);
            if (dom.isSingleton()) {
                definiteVars.add(v);
                definiteValues.add(dom.singleValue().get());
            } else {
                openVars.add(v);
            }
        }
        Set<T> openValues = new HashSet<>();
        for (Variable<T> v : openVars) {
            DiscreteDomain<T> dom = (DiscreteDomain<T>) domains.get(v);
            dom.stream().forEach(val -> {
                if (!definiteValues.contains(val)) openValues.add(val);
            });
        }
        return new Classification<>(definiteValues, definiteVars, openVars, openValues);
    }

    @SuppressWarnings("unchecked")
    private NarrowOutcome<T> narrowOpenToDefinite(@NonNull Map<Variable<?>, Domain<?>> domains, Classification<T> c) {
        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        for (Variable<T> v : c.openVars()) {
            DiscreteDomain<T> dom = (DiscreteDomain<T>) domains.get(v);
            var builder = dom.toBuilder();
            boolean changed = false;
            for (T val : dom.toList()) {
                if (!c.definiteValues().contains(val)) {
                    builder.delete(val);
                    changed = true;
                }
            }
            if (changed) {
                DiscreteDomain<T> newDom = builder.build();
                if (newDom.isEmpty()) return new NarrowOutcome<>(Map.of(), v);
                updated.put(v, newDom);
            }
        }
        return new NarrowOutcome<>(updated, null);
    }

    @SuppressWarnings("unchecked")
    private NarrowOutcome<T> narrowOpenAwayFromDefinite(@NonNull Map<Variable<?>, Domain<?>> domains, Classification<T> c) {
        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        for (Variable<T> v : c.openVars()) {
            DiscreteDomain<T> dom = (DiscreteDomain<T>) domains.get(v);
            var builder = dom.toBuilder();
            boolean changed = false;
            for (T val : dom.toList()) {
                if (c.definiteValues().contains(val)) {
                    builder.delete(val);
                    changed = true;
                }
            }
            if (changed) {
                DiscreteDomain<T> newDom = builder.build();
                if (newDom.isEmpty()) return new NarrowOutcome<>(Map.of(), v);
                updated.put(v, newDom);
            }
        }
        return new NarrowOutcome<>(updated, null);
    }

    private record Classification<T>(Set<T> definiteValues, List<Variable<T>> definiteVars,
                                      List<Variable<T>> openVars, Set<T> openValues) {}

    private record NarrowOutcome<T>(Map<Variable<?>, Domain<?>> updated, @Nullable Variable<T> emptiedVariable) {}

    @Override
    public String getRelation() {
        return count + " = NValue(" + trackedVariables.stream().map(Object::toString).sorted()
                .collect(Collectors.joining(", ", "{", "}")) + ")";
    }
}

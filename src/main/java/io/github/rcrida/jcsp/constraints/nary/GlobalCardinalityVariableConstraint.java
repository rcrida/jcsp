package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.NumericBounds;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The variable-target sibling of {@link GlobalCardinalityConstraint}: each tracked value's
 * occurrence count is itself a variable rather than a fixed {@code [min,max]} range --
 * {@code count(countedVariables, value) == targets.get(value)} for every tracked value, jointly
 * (not independently -- see below). Mirrors {@link CountVariableConstraint}/{@link
 * MaxVariableConstraint}'s "variable target" shape, generalised the same way {@link
 * GlobalCardinalityConstraint} generalises {@link CountConstraint} from one value to a whole map.
 * <p>
 * Deliberately <em>not</em> a decomposition into one {@link CountVariableConstraint} per tracked
 * value: that would reintroduce exactly the joint-infeasibility blind spot ADR-0016 fixed for the
 * fixed-count case (e.g. three variables each restricted to {@code {a,b}} with targets
 * {@code a=1,b=1} -- infeasible by pigeonhole, but no single value's own count is individually
 * violated). Propagation instead reuses {@link GlobalCardinalityPropagation}'s flow-based GAC
 * machinery directly, re-reading each tracked value's target variable's <em>current</em> bounds as
 * the flow network's {@code [lo,hi]} on every {@link #propagate} call (dynamic, since {@code
 * domains} is fresh each call) -- giving the {@code countedVariables} the same real joint GAC
 * {@link GlobalCardinalityConstraint} gets for a fixed range.
 * <p>
 * Narrowing the target variables themselves (not just {@code countedVariables}) uses a separate,
 * deliberately simpler pass: {@link ClassificationSupport}'s definite/possible/impossible
 * three-way split (the same one {@link CountConstraint}/{@link CountVariableConstraint} already
 * use) bounds each target to {@code [definiteCount, definiteCount + possibleCount]}. This is sound
 * but not maximally tight -- it doesn't determine each target's true flow-achievable range the way
 * full GAC would (that would need incremental per-value flow queries beyond the one feasibility
 * check this class already does), an explicit, documented scope decision rather than an oversight.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class GlobalCardinalityVariableConstraint<T> extends NaryConstraint implements Propagatable {
    @Getter @NonNull private final Set<Variable<T>> countedVariables;
    @Getter @NonNull private final Map<T, Variable<Integer>> targets;

    public static <T> GlobalCardinalityVariableConstraint<T> of(@NonNull Set<Variable<T>> countedVariables,
                                                                  @NonNull Map<T, Variable<Integer>> targets) {
        Set<Variable<?>> allVars = new LinkedHashSet<>(countedVariables);
        allVars.addAll(targets.values());
        return GlobalCardinalityVariableConstraint.<T>builder()
                .variables(allVars)
                .countedVariables(Set.copyOf(countedVariables))
                .targets(Map.copyOf(targets))
                .build();
    }

    /**
     * Optimistically satisfied for a partial assignment -- only checked once every counted
     * variable and every target is assigned, the same convention {@link CountVariableConstraint}
     * uses.
     */
    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        if (!assignment.getValues().keySet().containsAll(getVariables())) return true;
        Map<T, Integer> counts = new HashMap<>();
        for (Variable<T> v : countedVariables) {
            counts.merge(assignment.getValue(v).orElseThrow(), 1, Integer::sum);
        }
        for (var entry : targets.entrySet()) {
            int target = assignment.getValue(entry.getValue()).orElseThrow();
            if (counts.getOrDefault(entry.getKey(), 0) != target) return false;
        }
        return true;
    }

    private List<T> trackedValues() {
        return new ArrayList<>(targets.keySet());
    }

    /**
     * Builds the flow network with each tracked value's {@code [lo,hi]} read fresh from its target
     * variable's current bounds in {@code domains} -- the one thing that differs from {@link
     * GlobalCardinalityConstraint#computeFlow}, which reads a fixed range instead.
     */
    private GlobalCardinalityPropagation.FlowResult<T> computeFlow(
            @NonNull Map<Variable<?>, Domain<?>> domains, List<T> trackedValues) {
        int[] lo = new int[trackedValues.size()];
        int[] hi = new int[trackedValues.size()];
        for (int k = 0; k < trackedValues.size(); k++) {
            Domain<Integer> targetDomain = (Domain<Integer>) domains.get(targets.get(trackedValues.get(k)));
            lo[k] = (int) Math.round(NumericBounds.min(targetDomain));
            hi[k] = (int) Math.round(NumericBounds.max(targetDomain));
        }
        return GlobalCardinalityPropagation.computeFlow(countedVariables, trackedValues, lo, hi, domains);
    }

    /**
     * Two independent, individually-sound passes, both reading {@code domains} as handed in (never
     * each other's partial results within this one call -- the surrounding fixpoint loop re-invokes
     * this method until nothing changes, so each pass only needs to be sound relative to its own
     * starting point, not maximally tight against the other's output yet):
     * <ol>
     *   <li>{@link GlobalCardinalityPropagation#propagateFromFlow} narrows {@link
     *       #countedVariables} via the same flow-based GAC {@link GlobalCardinalityConstraint}
     *       uses, with {@code [lo,hi]} read from the targets' current bounds.</li>
     *   <li>A definite/possible/impossible classification (see this class's own Javadoc) narrows
     *       each target to {@code [definiteCount, definiteCount + possibleCount]}. This can never
     *       detect infeasibility via an <em>inverted</em> range beyond what pass 1 already would
     *       have: a definite variable for value {@code k} has {@code k} as its sole flow candidate,
     *       so {@code definiteCount > hi[k]} would already make pass 1's flow infeasible (more
     *       forced flow than {@code k}'s edge capacity allows); symmetrically {@code maxCount <
     *       lo[k]} means fewer variables can even reach {@code k} than the network's own required
     *       flow demands. Once pass 1 has returned feasible, both are already guaranteed, and from
     *       them {@code max(tLo,definiteCount) <= min(tHi,maxCount)} follows algebraically -- so
     *       the narrowed <em>range</em> is never inverted, the same argument {@link
     *       CountVariableConstraint#propagate}'s own "provably never empty" comment makes. That
     *       argument is necessary but not sufficient, though: {@link NumericBounds#narrow} filters
     *       the target's own <em>current</em> (possibly gappy, e.g. already pruned by an earlier
     *       round) discrete value set through that range, and a valid non-inverted range can still
     *       contain none of a gappy domain's actual values -- confirmed empirically on the bundled
     *       {@code MagicSequence-008-ca} instance before this comment was corrected, not a
     *       hypothetical. So this method checks the narrowed result's own emptiness explicitly
     *       (mirroring every other propagator's obligation to self-detect a domain wipeout and
     *       return {@link Optional#empty()} for the whole call, e.g. {@link
     *       io.github.rcrida.jcsp.consistency.node.NodeConsistency#apply}) rather than assuming the
     *       range argument alone rules it out.</li>
     * </ol>
     * When a variable is both counted <em>and</em> itself a target (the motivating "magic
     * sequence" shape, {@code occurs == countedVariables}), both passes can each propose their own
     * narrowing for that <em>one</em> variable; {@link #intersectInto} combines them rather than
     * letting one silently overwrite the other. Unlike the false start this comment used to make
     * here: the two proposals are <em>not</em> guaranteed to share a witness value just because
     * each is individually non-empty -- pass 1's candidate for the variable is a value in its role
     * as a counted variable (e.g. "what number sits at this position"), pass 2's is a value in its
     * role as a target (e.g. "how many zeros exist"), and those are unrelated quantities that
     * happen to share one variable, not the same fact viewed twice. Their intersection can be
     * genuinely empty (confirmed empirically on {@code MagicSequence-008-ca}: pass 1 narrowed a
     * variable to values valid as a sequence entry, pass 2 separately narrowed it to values valid
     * as a zero-count, and the two ranges didn't overlap), so this method checks {@code
     * updates.get(target)} for emptiness immediately after each {@link #intersectInto} call, not
     * just each pass's own pre-merge result.
     */
    @Override
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        List<T> trackedValues = trackedValues();
        GlobalCardinalityPropagation.FlowResult<T> result = computeFlow(domains, trackedValues);
        if (!result.feasible()) return Optional.empty();

        Map<Variable<?>, Domain<?>> updates = new HashMap<>(
                GlobalCardinalityPropagation.propagateFromFlow(result, targets.keySet(), domains));

        for (T value : trackedValues) {
            Variable<Integer> target = targets.get(value);
            ClassificationSupport.Classification<T> c = ClassificationSupport.classify(countedVariables, value::equals, domains);
            int definiteCount = c.definite().size();
            int maxCount = definiteCount + c.possible().size();

            @SuppressWarnings("unchecked")
            DiscreteDomain<Integer> targetDomain = (DiscreteDomain<Integer>) domains.get(target);
            double tLo = NumericBounds.min(targetDomain), tHi = NumericBounds.max(targetDomain);
            Optional<Domain<Integer>> narrowed = NumericBounds.<Integer>narrow(
                    targetDomain, Math.max(tLo, definiteCount), Math.min(tHi, maxCount));
            if (narrowed.isEmpty()) continue;
            if (narrowed.get().isEmpty()) return Optional.empty();
            intersectInto(updates, target, narrowed.get());
            if (updates.get(target).isEmpty()) return Optional.empty();
        }
        return Optional.of(updates);
    }

    /** Adds {@code narrowed} for {@code variable} into {@code updates}, intersecting with any existing entry. */
    @SuppressWarnings("unchecked")
    private static <U> void intersectInto(Map<Variable<?>, Domain<?>> updates, Variable<U> variable, Domain<U> narrowed) {
        Domain<U> existing = (Domain<U>) updates.get(variable);
        if (existing == null) {
            updates.put(variable, narrowed);
            return;
        }
        DiscreteDomain<U> existingDiscrete = (DiscreteDomain<U>) existing;
        var builder = existingDiscrete.toBuilder();
        for (U value : existingDiscrete.toList()) {
            if (!((DiscreteDomain<U>) narrowed).contains(value)) builder.delete(value);
        }
        updates.put(variable, builder.build());
    }

    /**
     * Attributes infeasibility to {@link GlobalCardinalityPropagation#findViolatingSubset}'s
     * counted-variable subset, plus every target -- unlike {@link
     * GlobalCardinalityConstraint#explainInfeasible}, the {@code [lo,hi]} bounds behind the flow
     * computation aren't a fixed, search-invariant property of this constraint; they came from the
     * targets' own current domains, so a sound explanation must cite them too (over-citing every
     * target rather than pinpointing which one mattered, since a tight bound on one tracked value
     * can cascade through the shared flow network to make a different value's own subset
     * infeasible).
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        List<T> trackedValues = trackedValues();
        return GlobalCardinalityPropagation.findViolatingSubset(computeFlow(domains, trackedValues)).flatMap(zVars -> {
            List<Variable<?>> cited = new ArrayList<>(zVars);
            cited.addAll(targets.values());
            Map<Variable<?>, Object> ground = Propagatable.allSingletonReason(cited, domains);
            if (!ground.isEmpty()) return GroundNogoodConstraint.fromReason(ground);
            return RangeNogoodConstraint.fromCurrentBounds(cited, domains);
        });
    }

    @Override
    public String getRelation() {
        return "GlobalCardinalityVariable(" + targets.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Object::toString)))
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", ", "{", "}")) + ")";
    }
}

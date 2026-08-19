package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.Singular;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The starred-tuple sibling of {@link NaryTuplesConstraint}: each tuple may leave some positions
 * unconstrained via the {@link #STAR} sentinel ("don't care") rather than requiring every
 * position to name a concrete value. Unlike expanding a starred tuple into the full cross-product
 * of its wildcard positions' domains -- combinatorial, and the exact reason plain {@link
 * NaryTuplesConstraint} declines starred input -- this class treats {@link #STAR} natively
 * throughout both {@link #isSatisfiedBy} and {@link #propagate}: a starred position never gets
 * checked against a domain and never constrains what that variable may take.
 * <p>
 * Added for XCSP3's {@code extension} construct's {@code STARRED_TUPLES} case (a {@code *} entry
 * in {@code <supports>}), confirmed via a real competition instance ({@code
 * PrizeCollecting-15-3-5-0.xml.lzma}) linking a 15-element successor array to a 15-element
 * position array through a table with 16 columns where all but 2-3 are starred per row -- exactly
 * the shape a cross-product expansion could never materialise.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class NaryStarredTuplesConstraint extends NaryConstraint implements Propagatable {
    /**
     * Sentinel marking a "don't care" position -- matches any value without constraining it.
     * Reference-compared everywhere (never {@code equals}), and never printed directly -- {@link
     * #getRelation} renders it as a literal {@code "*"} itself rather than delegating to this
     * object's own {@code toString()} -- so a plain {@code Object} is enough; a custom {@code
     * toString()} override here would be unreachable dead code.
     */
    public static final Object STAR = new Object();

    @Singular Set<Map<Variable<?>, Object>> tuples;

    public static NaryStarredTuplesConstraint of(@NonNull Set<Map<Variable<?>, Object>> tuples) {
        assert !tuples.isEmpty() : "tuples must not be empty";
        var variableSets = tuples.stream().map(Map::keySet).collect(Collectors.toSet());
        assert variableSets.size() == 1 : "all tuples must contain exactly the same variables";
        return NaryStarredTuplesConstraint.builder()
                .variables(variableSets.iterator().next())
                .tuples(tuples)
                .build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        if (!assignment.getValues().keySet().containsAll(getVariables())) return true;
        return tuples.stream().anyMatch(t -> getVariables().stream()
                .allMatch(v -> t.get(v) == STAR || t.get(v).equals(assignment.getValue(v).orElseThrow())));
    }

    /**
     * The same generalised-arc-consistency shape as {@link NaryTuplesConstraint#propagate}, with
     * {@link #STAR} treated as automatically satisfied at both steps: a tuple starred at {@code v}
     * is live regardless of {@code v}'s current domain (liveness check), and -- since a tuple that
     * doesn't constrain {@code v} at all supports every value {@code v}'s domain currently holds
     * -- a variable with any live starred-at-{@code v} tuple is fully supported without needing to
     * enumerate its domain at all, skipped rather than pruned to a materialised union.
     */
    @Override
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        var liveTuples = tuples.stream()
                .filter(t -> getVariables().stream().allMatch(v -> {
                    Object cell = t.get(v);
                    return cell == STAR || domains.get(v).contains(cell);
                }))
                .toList();
        if (liveTuples.isEmpty()) return Optional.empty();

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        for (Variable<?> v : getVariables()) {
            if (liveTuples.stream().anyMatch(t -> t.get(v) == STAR)) continue;
            DiscreteDomain<?> dom = (DiscreteDomain<?>) domains.get(v);
            var supportedValues = liveTuples.stream().map(t -> t.get(v)).collect(Collectors.toSet());
            if (supportedValues.size() < dom.size()) {
                var builder = dom.toBuilder();
                for (var value : dom.toList()) if (!supportedValues.contains(value)) builder.delete(value);
                updated.put(v, builder.build());
            }
        }
        return Optional.of(updated);
    }

    /** Mirrors {@link NaryTuplesConstraint#explainInfeasible}'s own reasoning exactly. */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        return RangeNogoodConstraint.fromCurrentBounds(getVariables(), domains)
                .or(() -> GroundNogoodConstraint.fromReason(Propagatable.allSingletonReason(getVariables(), domains)));
    }

    @Override
    public String getRelation() {
        var sortedVars = getVariables().stream()
                .sorted(Comparator.comparing(Object::toString))
                .toList();
        return "{" + tuples.stream()
                .map(t -> sortedVars.stream()
                        .map(v -> t.get(v) == STAR ? "*" : t.get(v).toString())
                        .collect(Collectors.joining(", ", "(", ")")))
                .sorted()
                .collect(Collectors.joining(", ")) + "}";
    }
}

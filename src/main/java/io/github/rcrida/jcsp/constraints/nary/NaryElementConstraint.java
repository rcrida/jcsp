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
import java.util.stream.Collectors;

/**
 * An n-ary constraint implementing array element access over a list of variables:
 * {@code result = vars[index]}, where both {@link #index} and {@link #result} are variables
 * and {@link #vars} is a list of CSP variables (not fixed values).
 * <p>
 * The {@link #index} variable is 1-based. Out-of-bounds indices are treated as a constraint
 * violation. For partial assignments the constraint is optimistically satisfied — only evaluated
 * once {@link #index}, {@link #result}, and {@code vars[index-1]} are all assigned.
 * <p>
 * Propagation performs three passes over discrete domains:
 * <ol>
 *   <li>Prune {@link #index}: remove value {@code i} if {@code vars[i-1].domain ∩ result.domain = ∅},
 *       or if {@code i} is out of bounds.</li>
 *   <li>Prune {@link #result}: intersect with the union of {@code vars[i-1].domain} for all live
 *       index values {@code i}.</li>
 *   <li>Prune {@code vars[i-1]}: when {@code index.domain} is a singleton {@code {i}}, intersect
 *       {@code vars[i-1].domain} with {@code result.domain}.</li>
 * </ol>
 * Returns {@link Optional#empty()} on infeasibility. Non-discrete (bounded) domains are left
 * unchanged (propagation is a no-op).
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class NaryElementConstraint<T> extends NaryConstraint implements Propagatable {
    @NonNull private final Variable<Integer> index;
    @NonNull private final Variable<T> result;
    @NonNull private final List<Variable<T>> vars;

    public static <T> NaryElementConstraint<T> of(@NonNull Variable<Integer> index,
                                                   @NonNull Variable<T> result,
                                                   @NonNull List<Variable<T>> vars) {
        Set<Variable<?>> allVars = new LinkedHashSet<>();
        allVars.add(index);
        allVars.add(result);
        allVars.addAll(vars);
        return NaryElementConstraint.<T>builder()
                .variables(allVars)
                .index(index)
                .result(result)
                .vars(List.copyOf(vars))
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        var assigned = assignment.getValues().keySet();
        if (!assigned.contains(index)) return true;
        Integer i = (Integer) assignment.getValue(index).orElseThrow();
        if (i < 1 || i > vars.size()) return false;
        if (!assigned.contains(result) || !assigned.contains(vars.get(i - 1))) return true;
        T r = (T) assignment.getValue(result).orElseThrow();
        T v = (T) assignment.getValue(vars.get(i - 1)).orElseThrow();
        return r.equals(v);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        Domain<?> rawIndex = domains.get(index);
        Domain<?> rawResult = domains.get(result);

        if (!(rawIndex instanceof DiscreteDomain<?>) || !(rawResult instanceof DiscreteDomain<?>)) {
            return Optional.of(Map.of());
        }

        DiscreteDomain<Integer> indexDomain = (DiscreteDomain<Integer>) rawIndex;
        DiscreteDomain<T> resultDomain = (DiscreteDomain<T>) rawResult;

        List<DiscreteDomain<T>> varDomains = new ArrayList<>(vars.size());
        for (Variable<T> v : vars) {
            Domain<?> d = domains.get(v);
            if (!(d instanceof DiscreteDomain<?>)) return Optional.of(Map.of());
            varDomains.add((DiscreteDomain<T>) d);
        }

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();

        // Pass 1: prune index — remove i if out-of-bounds or vars[i-1].domain ∩ result.domain = ∅
        {
            var builder = indexDomain.toBuilder();
            boolean changed = false;
            for (Integer i : indexDomain.toList()) {
                if (i < 1 || i > vars.size()) {
                    builder.delete(i);
                    changed = true;
                    continue;
                }
                boolean hasSupport = varDomains.get(i - 1).stream().anyMatch(resultDomain::contains);
                if (!hasSupport) {
                    builder.delete(i);
                    changed = true;
                }
            }
            if (changed) {
                DiscreteDomain<Integer> newIndex = (DiscreteDomain<Integer>) builder.build();
                if (newIndex.isEmpty()) return Optional.empty();
                updated.put(index, newIndex);
                indexDomain = newIndex;
            }
        }

        // Pass 2: prune result — intersect with union of vars[i-1].domain for all live i
        {
            Set<T> reachable = new HashSet<>();
            for (Integer i : indexDomain.toList()) {
                varDomains.get(i - 1).stream().forEach(reachable::add);
            }
            var builder = resultDomain.toBuilder();
            boolean changed = false;
            for (T v : resultDomain.toList()) {
                if (!reachable.contains(v)) {
                    builder.delete(v);
                    changed = true;
                }
            }
            if (changed) {
                DiscreteDomain<T> newResult = (DiscreteDomain<T>) builder.build();
                updated.put(result, newResult);
                resultDomain = newResult;
            }
        }

        // Pass 3: if index is singleton {i}, prune vars[i-1] ∩ result.domain.
        // Pass 1 guarantees i is in [1, vars.size()], so no bounds check needed here.
        if (indexDomain.isSingleton()) {
            Integer i = indexDomain.singleValue().get();
            Variable<T> selectedVar = vars.get(i - 1);
            DiscreteDomain<T> varDom = updated.containsKey(selectedVar)
                    ? (DiscreteDomain<T>) updated.get(selectedVar)
                    : varDomains.get(i - 1);
            var builder = varDom.toBuilder();
            boolean changed = false;
            for (T v : varDom.toList()) {
                if (!resultDomain.contains(v)) {
                    builder.delete(v);
                    changed = true;
                }
            }
            if (changed) {
                updated.put(selectedVar, builder.build());
            }
        }

        return Optional.of(updated);
    }

    /**
     * The sole infeasibility point is pass 1 emptying {@link #index}'s domain: every candidate
     * {@code i} was excluded either because it's out of bounds (unconditional — no variable
     * involved) or because {@code vars[i-1]}'s domain doesn't overlap {@link #result}'s (depends on
     * both). Since {@code propagate} guarantees {@link #index}, {@link #result}, and every
     * {@code vars[i-1]} were {@link DiscreteDomain} before ever reaching pass 1 (the type guards
     * upstream return {@code Optional.of(Map.of())} otherwise, never {@code Optional.empty()}, and
     * {@code explainInfeasible} is only ever invoked with the same {@code domains} that made
     * {@code propagate} return infeasible), no redundant type check is needed here.
     * <p>
     * Attributes the wipeout to {@link #result}, every {@code vars[i-1]} for in-bounds candidates,
     * <em>and</em> {@link #index} itself — citing {@link #index}'s own current value set is what
     * makes this sound: {@link #index}'s domain can be narrowed by a <em>different</em> constraint
     * sharing that variable (e.g. an {@link AllDiffConstraint}, or a {@code BinaryOffsetConstraint}
     * shifting it for a 1-based encoding) before pass 1 ever runs, so the candidate set pass 1
     * iterates over can already be narrower than {@code [1, vars.size()]}. An earlier version of
     * this method omitted {@link #index} from the citation entirely, silently assuming none of the
     * externally-excluded values could ever provide support in a different branch — unsound, and
     * confirmed via a real regression: {@code QuasiGroup-7-09.xml.lzma} intermittently reported a
     * false {@code UNSATISFIABLE} under CDCL search before this fix (a verified solution existed,
     * but a learned nogood built from the old citation wrongly forbade part of the solution space).
     * <p>
     * Delegates to {@link ValueSetNogoodConstraint#fromCurrentState}, which tries a tighter {@link
     * GroundNogoodConstraint} first (when every cited variable happens to be singleton) and falls
     * back to a {@link ValueSetNogoodConstraint} citing each variable's exact current value set
     * otherwise — sound regardless of whether {@link #index}'s domain has gaps, unlike a {@link
     * RangeNogoodConstraint#fromCurrentBounds} citation would be.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        DiscreteDomain<Integer> indexDomain = (DiscreteDomain<Integer>) domains.get(index);

        Set<Variable<?>> cited = new HashSet<>();
        for (Integer i : indexDomain.toList()) {
            if (i >= 1 && i <= vars.size()) {
                cited.add(vars.get(i - 1));
            }
        }
        if (cited.isEmpty()) return Optional.empty();
        cited.add(result);
        cited.add(index);
        return ValueSetNogoodConstraint.fromCurrentState(cited, domains);
    }

    @Override
    public String getRelation() {
        String varList = vars.stream().map(Object::toString).collect(Collectors.joining(", ", "[", "]"));
        return result + " = " + varList + "[" + index + "]";
    }
}

package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.NumericBounds;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * An n-ary constraint that compares the number of {@link #countedVariables} taking {@link #value}
 * to a variable target, rather than a fixed bound: {@code count(countedVariables, value) <op>
 * target}. The variable-target sibling of {@link CountConstraint} — mirrors {@link
 * SumVariableConstraint}/{@link MaxVariableConstraint}'s own shape (a real {@link #operator}
 * field, since {@link CountConstraint} already has one to generalise) rather than {@link
 * NValueConstraint}'s always-equality one, which never had an {@link Operator} to begin with.
 * Extends {@link NaryConstraint} directly rather than {@link UniformNaryConstraint}, whose {@code
 * isSatisfiedBy} is {@code final}.
 * <p>
 * For partial assignments the constraint is optimistically satisfied — only evaluated once every
 * counted variable and {@link #target} are assigned.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class CountVariableConstraint<T> extends NaryConstraint implements Propagatable {
    @Getter @NonNull private final Set<Variable<T>> countedVariables;
    @Getter @NonNull private final T value;
    @Getter @NonNull private final Operator operator;
    @Getter @NonNull private final Variable<Integer> target;

    public static <T> CountVariableConstraint<T> of(@NonNull Set<Variable<T>> countedVariables,
                                                      @NonNull T value,
                                                      @NonNull Operator operator,
                                                      @NonNull Variable<Integer> target) {
        Set<Variable<?>> allVars = new LinkedHashSet<>(countedVariables);
        allVars.add(target);
        return CountVariableConstraint.<T>builder()
                .variables(allVars)
                .countedVariables(Set.copyOf(countedVariables))
                .value(value)
                .operator(operator)
                .target(target)
                .build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        if (!assignment.getValues().keySet().containsAll(getVariables())) return true;
        int count = (int) countedVariables.stream()
                .filter(v -> value.equals(assignment.getValue(v).orElseThrow())).count();
        int targetValue = assignment.getValue(target).orElseThrow();
        return operator.compare(count, targetValue);
    }

    /**
     * Bounds-consistency propagation for {@code count(countedVariables, value) <op> target}. Only
     * {@link Operator#EQ}, {@link Operator#LEQ}, and {@link Operator#GEQ} propagate (matching
     * {@link MaxVariableConstraint#propagate}'s own operator set); {@link Operator#LT}/{@link
     * Operator#GT}/{@link Operator#NEQ} are checked only via {@link #isSatisfiedBy} at solution
     * time.
     * <p>
     * The achievable count over the current box is {@code [definiteCount, maxCount]}. From there,
     * mirroring {@link MaxVariableConstraint#propagate}'s own structure:
     * <ul>
     *   <li><b>{@link #target} narrowing</b>: a target value below {@code definiteCount} can never
     *       satisfy {@code count <= target} (LEQ/EQ), so {@link #target}'s lower bound rises to
     *       {@code definiteCount}; a target value above {@code maxCount} can never satisfy {@code
     *       count >= target} (GEQ/EQ), so its upper bound falls to {@code maxCount}.</li>
     *   <li><b>Possible-variable narrowing</b>: once {@code definiteCount} reaches {@link
     *       #target}'s current maximum (LEQ/EQ), no possible variable may still take {@link
     *       #value} (it would push the count past {@link #target}'s ceiling) — {@link #value}
     *       is removed from every possible domain, mirroring {@link CountConstraint#propagate}'s
     *       own upper-quota-filled branch against a fixed bound. Once {@code maxCount} reaches
     *       {@link #target}'s current minimum (GEQ/EQ), every possible variable is required to
     *       reach the quota — forced to {@code {value}} — mirroring {@link
     *       CountConstraint#propagate}'s own lower-quota branch.</li>
     * </ul>
     * As in {@link MaxVariableConstraint#propagate}, each step is independently sound from the
     * domains this call started with regardless of the others, so using {@link #target}'s bounds
     * <em>before</em> this call's own target-narrowing step doesn't cost soundness — the
     * surrounding fixpoint loop calls this again until nothing changes. Target narrowing is
     * provably never empty given the earlier feasibility checks pass (same cross-bound argument as
     * {@link MaxVariableConstraint#propagate}); the possible-variable passes are provably never
     * empty too, since a "possible" variable's domain (by {@link ClassificationSupport#classify}'s own definition)
     * always has at least one value other than {@link #value} (so removing {@link #value} can't
     * empty it) and always contains {@link #value} itself (so forcing down to just {@link #value}
     * can't either) — matching why {@link CountConstraint#propagate}'s analogous branches need no
     * defensive emptiness check either.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        if (operator != Operator.EQ && operator != Operator.LEQ && operator != Operator.GEQ) {
            return Optional.of(Map.of());
        }
        boolean leqLike = operator == Operator.EQ || operator == Operator.LEQ;
        boolean geqLike = operator == Operator.EQ || operator == Operator.GEQ;

        ClassificationSupport.Classification<T> c = ClassificationSupport.classify(countedVariables, value::equals, domains);
        int definiteCount = c.definite().size();
        int maxCount = definiteCount + c.possible().size();

        DiscreteDomain<Integer> targetDomain = (DiscreteDomain<Integer>) domains.get(target);
        double tLo = NumericBounds.min(targetDomain), tHi = NumericBounds.max(targetDomain);

        if (leqLike && definiteCount > tHi) return Optional.empty();
        if (geqLike && maxCount < tLo) return Optional.empty();

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();

        double newTLo = leqLike ? Math.max(tLo, definiteCount) : tLo;
        double newTHi = geqLike ? Math.min(tHi, maxCount) : tHi;
        NumericBounds.<Integer>narrow(targetDomain, newTLo, newTHi).ifPresent(narrowed -> updated.put(target, narrowed));

        if (leqLike && definiteCount == tHi) {
            for (Variable<T> v : c.possible()) {
                DiscreteDomain<T> dom = (DiscreteDomain<T>) domains.get(v);
                updated.put(v, dom.toBuilder().delete(value).build());
            }
        }

        if (geqLike && maxCount == tLo) {
            for (Variable<T> v : c.possible()) {
                DiscreteDomain<T> dom = (DiscreteDomain<T>) domains.get(v);
                var builder = dom.toBuilder();
                for (T val : dom.toList()) {
                    if (!value.equals(val)) builder.delete(val);
                }
                updated.put(v, builder.build());
            }
        }

        return Optional.of(updated);
    }

    /**
     * Mirrors {@link CountConstraint#explainInfeasible}, generalised to cite {@link #target} too
     * (a genuine variable now, not a fixed constant): the below-side (LEQ/EQ) reason cites the
     * definite variables (already singleton by construction) plus {@link #target}; the above-side
     * (GEQ/EQ) reason cites the impossible variables plus {@link #target} — both routed through
     * {@link Propagatable#allSingletonReason}, so this returns {@link Optional#empty()} whenever
     * {@link #target} (or a cited counted variable) isn't singleton, the same "no sound ground
     * reason available" fallback {@link CountConstraint} already uses.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        boolean leqLike = operator == Operator.EQ || operator == Operator.LEQ;
        boolean geqLike = operator == Operator.EQ || operator == Operator.GEQ;

        ClassificationSupport.Classification<T> c = ClassificationSupport.classify(countedVariables, value::equals, domains);
        int definiteCount = c.definite().size();
        int maxCount = definiteCount + c.possible().size();

        DiscreteDomain<Integer> targetDomain = (DiscreteDomain<Integer>) domains.get(target);
        double tLo = NumericBounds.min(targetDomain), tHi = NumericBounds.max(targetDomain);

        if (leqLike && definiteCount > tHi) {
            List<Variable<?>> cited = new ArrayList<>(c.definite());
            cited.add(target);
            Map<Variable<?>, Object> reason = Propagatable.allSingletonReason(cited, domains);
            if (!reason.isEmpty()) return GroundNogoodConstraint.fromReason(reason);
        }

        if (geqLike && maxCount < tLo) {
            List<Variable<?>> cited = new ArrayList<>(c.impossible());
            cited.add(target);
            Map<Variable<?>, Object> reason = Propagatable.allSingletonReason(cited, domains);
            if (!reason.isEmpty()) return GroundNogoodConstraint.fromReason(reason);
        }

        return Optional.empty();
    }

    @Override
    public String getRelation() {
        return "count(" + value + ") " + operator.symbol + " " + target;
    }
}

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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An n-ary constraint that compares the number of {@link #countedVariables} taking a value from
 * {@link #values} to a variable target, rather than a fixed bound: {@code
 * among(countedVariables, values) <op> target}. The variable-target sibling of {@link
 * AmongConstraint}, generalising {@link CountVariableConstraint} from a single target value to a
 * set — see that class's own Javadoc for why this carries a real {@link #operator} field
 * (mirroring {@link SumVariableConstraint}/{@link MaxVariableConstraint}) rather than {@link
 * NValueConstraint}'s always-equality shape.
 * <p>
 * For partial assignments the constraint is optimistically satisfied — only evaluated once every
 * counted variable and {@link #target} are assigned.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class AmongVariableConstraint<T> extends NaryConstraint implements Propagatable {
    @Getter @NonNull private final Set<Variable<T>> countedVariables;
    @Getter @NonNull private final Set<T> values;
    @Getter @NonNull private final Operator operator;
    @Getter @NonNull private final Variable<Integer> target;

    public static <T> AmongVariableConstraint<T> of(@NonNull Set<Variable<T>> countedVariables,
                                                      @NonNull Set<T> values,
                                                      @NonNull Operator operator,
                                                      @NonNull Variable<Integer> target) {
        Set<Variable<?>> allVars = new LinkedHashSet<>(countedVariables);
        allVars.add(target);
        return AmongVariableConstraint.<T>builder()
                .variables(allVars)
                .countedVariables(Set.copyOf(countedVariables))
                .values(Set.copyOf(values))
                .operator(operator)
                .target(target)
                .build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        if (!assignment.getValues().keySet().containsAll(getVariables())) return true;
        int count = (int) countedVariables.stream()
                .filter(v -> values.contains(assignment.getValue(v).orElseThrow())).count();
        int targetValue = assignment.getValue(target).orElseThrow();
        return operator.compare(count, targetValue);
    }

    /**
     * Mirrors {@link CountVariableConstraint#propagate} generalised from a single value to a set
     * — see that class's own Javadoc for the full derivation and non-emptiness argument (which
     * transfers directly: a "possible" variable here always has at least one value inside {@link
     * #values} and at least one outside it, by {@link ClassificationSupport#classify}'s own
     * definition, so neither narrowing direction below can empty it).
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        if (operator != Operator.EQ && operator != Operator.LEQ && operator != Operator.GEQ) {
            return Optional.of(Map.of());
        }
        boolean leqLike = operator == Operator.EQ || operator == Operator.LEQ;
        boolean geqLike = operator == Operator.EQ || operator == Operator.GEQ;

        ClassificationSupport.Classification<T> c = ClassificationSupport.classify(countedVariables, values::contains, domains);
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
                var builder = dom.toBuilder();
                for (T val : dom.toList()) {
                    if (values.contains(val)) builder.delete(val);
                }
                updated.put(v, builder.build());
            }
        }

        if (geqLike && maxCount == tLo) {
            for (Variable<T> v : c.possible()) {
                DiscreteDomain<T> dom = (DiscreteDomain<T>) domains.get(v);
                var builder = dom.toBuilder();
                for (T val : dom.toList()) {
                    if (!values.contains(val)) builder.delete(val);
                }
                updated.put(v, builder.build());
            }
        }

        return Optional.of(updated);
    }

    /**
     * Mirrors {@link CountVariableConstraint#explainInfeasible} generalised to a value set — see
     * that class's own Javadoc for the shared reasoning.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        boolean leqLike = operator == Operator.EQ || operator == Operator.LEQ;
        boolean geqLike = operator == Operator.EQ || operator == Operator.GEQ;

        ClassificationSupport.Classification<T> c = ClassificationSupport.classify(countedVariables, values::contains, domains);
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
        return "among(" + values.stream().map(Objects::toString).sorted().collect(Collectors.joining(", ")) + ") " + operator.symbol + " " + target;
    }
}

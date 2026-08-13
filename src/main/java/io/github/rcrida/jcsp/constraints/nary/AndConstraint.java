package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Conjunction of an arbitrary set of constraints: {@code AND(c1, c2, ..., cn)}, satisfied exactly
 * when every conjunct is. Exists to give a conjunction a single {@link Constraint} identity where
 * one is needed as a unit — chiefly as the {@code body} of {@link ReifiedConstraint}/{@link
 * ImplicationConstraint} (reifying "all of these hold" as one indicator) — since without it, a group
 * of constraints can only ever be added to a CSP directly (already an implicit conjunction) and never
 * referenced as one object. Deliberately has no negated counterpart: {@code NOT(AND(...))} is
 * {@code OR} of each conjunct's own negation, and generically negating an arbitrary {@link
 * Constraint} isn't something this codebase provides (the same limitation documented for
 * {@code Xcsp3CallbackHandler}'s {@code HALF_TO} reification gap).
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class AndConstraint extends NaryConstraint implements Propagatable {
    @Getter @NonNull private final Set<Constraint> conjuncts;

    public static AndConstraint of(@NonNull Set<Constraint> conjuncts) {
        Set<Variable<?>> vars = conjuncts.stream()
                .flatMap(c -> c.getVariables().stream())
                .collect(Collectors.toSet());
        return AndConstraint.builder().variables(vars).conjuncts(Set.copyOf(conjuncts)).build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment a) {
        return conjuncts.stream().allMatch(c -> c.isSatisfiedBy(a));
    }

    @Override
    public String getRelation() {
        return conjuncts.stream().map(Constraint::getRelation).sorted()
                .collect(Collectors.joining(" AND ", "(", ")"));
    }

    /**
     * True only when every conjunct is guaranteed satisfied: a {@link Propagatable} conjunct via its
     * own override, a non-{@link Propagatable} conjunct only once every one of its variables is
     * singleton (via {@link Propagatable#allSingletonReason}) and the resulting ground assignment
     * satisfies it. Worth implementing (rather than the inherited default {@code false}) specifically
     * because {@link ReifiedConstraint#propagate} already calls a {@link Propagatable} body's
     * {@code isNecessarilySatisfied} to force its indicator {@code true} early — the situation an
     * {@link AndConstraint} sitting inside a reification directly benefits from.
     */
    @Override
    public boolean isNecessarilySatisfied(@NonNull Map<Variable<?>, Domain<?>> domains) {
        return conjuncts.stream().allMatch(c -> necessarilySatisfied(c, domains));
    }

    private static boolean necessarilySatisfied(Constraint c, Map<Variable<?>, Domain<?>> domains) {
        if (c instanceof Propagatable p) return p.isNecessarilySatisfied(domains);
        Map<Variable<?>, Object> singletons = Propagatable.allSingletonReason(c.getVariables(), domains);
        return singletons.size() == c.getVariables().size() && c.isSatisfiedBy(Assignment.of(singletons));
    }

    /**
     * Outcome of running every {@link Propagatable} conjunct's own {@link Propagatable#propagate} to
     * a shared fixpoint — one conjunct narrowing a variable can unlock further narrowing in a sibling
     * conjunct over the same variable, so a single left-to-right pass isn't enough. Shared by {@link
     * #propagate} (only needs {@link #diff}/{@link #infeasible}) and {@link #explainInfeasible}
     * (additionally needs {@link #failingConjunct}/{@link #domainsAtFailure} to ask the conjunct that
     * actually failed for its own reason), mirroring the shared-outcome pattern {@link
     * InverseConstraint} already uses for its own two-pass propagation.
     */
    private record FixpointResult(boolean infeasible, Map<Variable<?>, Domain<?>> diff,
                                   @Nullable Propagatable failingConjunct,
                                   @Nullable Map<Variable<?>, Domain<?>> domainsAtFailure) {
        static FixpointResult feasible(Map<Variable<?>, Domain<?>> diff) {
            return new FixpointResult(false, diff, null, null);
        }

        static FixpointResult infeasible(Propagatable failingConjunct, Map<Variable<?>, Domain<?>> domainsAtFailure) {
            return new FixpointResult(true, Map.of(), failingConjunct, domainsAtFailure);
        }
    }

    private FixpointResult runFixpoint(Map<Variable<?>, Domain<?>> domains) {
        Map<Variable<?>, Domain<?>> current = new HashMap<>(domains);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Constraint c : conjuncts) {
                if (!(c instanceof Propagatable p)) continue;
                Optional<Map<Variable<?>, Domain<?>>> result = p.propagate(current);
                if (result.isEmpty()) return FixpointResult.infeasible(p, Map.copyOf(current));
                for (var entry : result.get().entrySet()) {
                    if (!entry.getValue().equals(current.get(entry.getKey()))) {
                        current.put(entry.getKey(), entry.getValue());
                        changed = true;
                    }
                }
            }
        }
        Map<Variable<?>, Domain<?>> diff = new HashMap<>();
        for (var entry : current.entrySet()) {
            if (!entry.getValue().equals(domains.get(entry.getKey()))) diff.put(entry.getKey(), entry.getValue());
        }
        return FixpointResult.feasible(diff);
    }

    @Override
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        FixpointResult result = runFixpoint(domains);
        return result.infeasible() ? Optional.empty() : Optional.of(result.diff());
    }

    /**
     * Delegates to whichever conjunct's own {@link Propagatable#propagate} actually emptied a domain
     * during {@link #runFixpoint}'s replay, asking that conjunct for its own reason against the
     * domains it saw at that point. Falls back to {@link Propagatable#allSingletonReason} over this
     * constraint's own {@link #getVariables()} — mirroring {@link ReifiedConstraint}/{@link
     * ImplicationConstraint}'s identical fallback — when the failing conjunct doesn't override {@link
     * Propagatable#explainInfeasible} (default: unexplained).
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        FixpointResult result = runFixpoint(domains);
        if (!result.infeasible()) return Optional.empty();
        Optional<NogoodConstraint> reason = result.failingConjunct().explainInfeasible(result.domainsAtFailure());
        return reason.isPresent() ? reason
                : GroundNogoodConstraint.fromReason(Propagatable.allSingletonReason(getVariables(), domains));
    }
}

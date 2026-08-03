package io.github.rcrida.jcsp.consistency.fixpoint;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Collects domain narrowings across a whole {@link FixpointConsistency}/{@link
 * NogoodFixpointConsistency} fixpoint call so {@link ConstraintSatisfactionProblem#withDomains}
 * — an O(variable count) copy — runs at most once per call via {@link #finish}, instead of once
 * per narrowing constraint. Allocates its backing maps lazily, on the first call to {@link
 * #record}: a fixpoint call where nothing narrows (the common case once a round is near its own
 * fixpoint) must stay exactly as cheap as before, or the O(N) copy this class exists to avoid
 * ends up paid unconditionally instead — measured as a real ~7-10% regression on a set-CP
 * branch-heavy benchmark before this class existed, when the copy was built eagerly up front.
 */
final class DomainAccumulator {
    private final Map<Variable<?>, Domain<?>> initial;
    private Map<Variable<?>, Domain<?>> working;
    private Map<Variable<?>, Domain<?>> allUpdates;

    DomainAccumulator(Map<Variable<?>, Domain<?>> initial) {
        this.initial = initial;
    }

    Map<Variable<?>, Domain<?>> view() {
        return working != null ? working : initial;
    }

    void record(Map<Variable<?>, Domain<?>> updates) {
        if (working == null) {
            working = new LinkedHashMap<>(initial);
            allUpdates = new LinkedHashMap<>();
        }
        working.putAll(updates);
        allUpdates.putAll(updates);
    }

    ConstraintSatisfactionProblem finish(ConstraintSatisfactionProblem csp) {
        return allUpdates == null ? csp : csp.withDomains(allUpdates);
    }
}

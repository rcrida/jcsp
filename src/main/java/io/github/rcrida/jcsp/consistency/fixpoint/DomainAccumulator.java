package io.github.rcrida.jcsp.consistency.fixpoint;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.consistency.DomainOverlay;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import io.github.rcrida.jcsp.variables.VariableMap;

import java.util.Map;

/**
 * Collects domain narrowings across a whole {@link FixpointConsistency}/{@link
 * NogoodFixpointConsistency} fixpoint call so {@link ConstraintSatisfactionProblem#withDomains}
 * — an O(variable count) copy — runs at most once per call via {@link #finish}, instead of once
 * per narrowing constraint. What a propagator sees in between is a {@link DomainOverlay} of the
 * narrowings so far over the original domains, so no copy is made for that either. The update
 * map is allocated lazily, on the first call to {@link #record}: a fixpoint call where nothing
 * narrows (the common case once a round is near its own fixpoint) must stay exactly as cheap as
 * before, or the O(N) copy this class exists to avoid ends up paid unconditionally instead —
 * measured as a real ~7-10% regression on a set-CP branch-heavy benchmark before this class
 * existed, when the copy was built eagerly up front.
 */
final class DomainAccumulator {
    private final Map<Variable<?>, Domain<?>> initial;
    private Map<Variable<?>, Domain<?>> allUpdates;
    private Map<Variable<?>, Domain<?>> view;

    DomainAccumulator(Map<Variable<?>, Domain<?>> initial) {
        this.initial = initial;
        this.view = initial;
    }

    /** The domains so far: {@code initial} with every recorded narrowing layered over it, never copied. */
    Map<Variable<?>, Domain<?>> view() {
        return view;
    }

    void record(Map<Variable<?>, Domain<?>> updates) {
        if (allUpdates == null) {
            allUpdates = new VariableMap<>();
            view = DomainOverlay.of(initial, allUpdates);
        }
        allUpdates.putAll(updates);
    }

    ConstraintSatisfactionProblem finish(ConstraintSatisfactionProblem csp) {
        return allUpdates == null ? csp : csp.withDomains(allUpdates);
    }
}

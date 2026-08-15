package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.variables.Variable;

import java.util.Map;

/**
 * The result of one narrowing/failure-detection pass in a multi-pass {@code propagate}/{@code
 * explainInfeasible} implementation, shared by {@link CircuitConstraint} and {@link
 * InverseConstraint}: {@link #FEASIBLE} means the pass found no infeasibility; {@link
 * #infeasible} means it did, carrying the sound explanation for {@code explainInfeasible} to
 * return (ignored by {@code propagate}, which only needs to know a pass failed). Domains narrowed
 * by a pass are mutated directly into a shared {@code updated} map passed alongside this outcome,
 * not carried by it.
 */
record PassOutcome(boolean infeasible, Map<Variable<?>, Object> reason) {
    static final PassOutcome FEASIBLE = new PassOutcome(false, Map.of());
    static PassOutcome infeasible(Map<Variable<?>, Object> reason) { return new PassOutcome(true, reason); }
}

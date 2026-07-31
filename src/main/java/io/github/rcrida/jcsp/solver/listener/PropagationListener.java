package io.github.rcrida.jcsp.solver.listener;

import io.github.rcrida.jcsp.consistency.ConstraintConsistency;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.solver.FixpointPropagation;
import io.github.rcrida.jcsp.solver.LocalSolver;
import io.github.rcrida.jcsp.variables.Variable;

import java.util.Map;

/**
 * Propagator-level fixpoint progress -- shared between {@link SolverListener} (the main chain's
 * one-time preprocessing pass, per-node propagation, and set-branching re-propagation, all via
 * {@link FixpointPropagation}) and {@link LocalSolverListener} ({@link LocalSolver.Factory#PREPROCESSORS}'s
 * single, non-fixpoint preprocessing pass, which also narrows domains before repair search starts).
 */
public interface PropagationListener {
    /**
     * A propagator narrowed at least one domain. {@code domainsBefore}/{@code domainsAfter} are the
     * full per-variable domain snapshots (not just the variables {@code propagator} itself
     * touched), already immutable and reference-cheap to hand out -- {@code domainSumBefore}/
     * {@code domainSumAfter} are the same cheap {@code double} totals the caller already computes
     * to decide whether to fire this event at all.
     */
    default void onPropagatorProgress(ConstraintConsistency propagator,
                                       Map<Variable<?>, Domain<?>> domainsBefore,
                                       Map<Variable<?>, Domain<?>> domainsAfter,
                                       double domainSumBefore, double domainSumAfter) {}
}

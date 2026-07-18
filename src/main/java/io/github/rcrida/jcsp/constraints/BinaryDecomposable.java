package io.github.rcrida.jcsp.constraints;

import io.github.rcrida.jcsp.constraints.binary.BinaryConstraint;

import java.util.Set;

/**
 * A constraint that can be decomposed into an equivalent set of binary constraints.
 * Implemented by n-ary constraints that support AC3 arc propagation via binary decomposition.
 */
public interface BinaryDecomposable {
    Set<BinaryConstraint<?, ?>> getAsBinaryConstraints();

    /**
     * Whether {@link #getAsBinaryConstraints()} alone is a sound and complete stand-in for this
     * constraint's own {@code isSatisfiedBy} — i.e. an assignment satisfies every decomposed
     * binary constraint if and only if it satisfies this one. True for constraints whose n-ary
     * semantics reduce exactly to a set of pairwise conditions (e.g. all-different, adjacent-pair
     * ordering); false when the decomposition only captures part of the semantics, missing a
     * whole-constraint condition it can't express as pairwise relations (e.g. "at least one true",
     * "single Hamiltonian cycle"). Callers that use the decomposition as a substitute for this
     * constraint — rather than purely as extra propagation alongside it — must check this first.
     */
    default boolean isDecompositionComplete() {
        return true;
    }
}

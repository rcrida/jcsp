package io.github.rcrida.jcsp.constraints;

import io.github.rcrida.jcsp.constraints.binary.BinaryConstraint;

import java.util.Set;

/**
 * A constraint that can be decomposed into an equivalent set of binary constraints.
 * Implemented by n-ary constraints that support AC3 arc propagation via binary decomposition.
 */
public interface BinaryDecomposable {
    Set<BinaryConstraint<?, ?>> getAsBinaryConstraints();
}

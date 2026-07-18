package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.consistency.Propagatable;

/**
 * A learned nogood: a constraint recording that some combination of variable states, discovered
 * during search, is jointly infeasible. Different implementations may represent that combination
 * differently — {@link GroundNogoodConstraint} forbids one specific value per variable
 * ({@code OR(x1 != v1, ..., xk != vk)}); a future generalized/interval implementation could forbid
 * a whole sub-domain per variable instead. {@link io.github.rcrida.jcsp.assignments.NogoodStore},
 * {@link io.github.rcrida.jcsp.consistency.Inference}, and
 * {@link io.github.rcrida.jcsp.ConstraintSatisfactionProblem} all operate on this interface rather
 * than any single implementation, so new nogood shapes can be added without touching them.
 */
public interface NogoodConstraint extends Constraint, Propagatable {
}

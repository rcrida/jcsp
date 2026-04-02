package org.jcsp.constraints.nary;

import lombok.Singular;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;
import org.jcsp.constraints.Constraint;
import org.jcsp.constraints.binary.BinaryConstraint;
import org.jcsp.variables.Variable;

import java.util.Optional;
import java.util.Set;

/**
 * Represents an abstract n-ary constraint in a constraint satisfaction problem (CSP).
 * An n-ary constraint applies to a set of variables and defines a condition or restriction
 * on the values that these variables can simultaneously take.
 */
@Value
@NonFinal
@SuperBuilder
public abstract class NaryConstraint implements Constraint {
    @Singular
    Set<Variable> variables;

    public abstract String getRelation();

    public Optional<Set<BinaryConstraint>> getAsBinaryConstraints() {
        return Optional.empty();
    }

    @Override
    public String toString() {
        return "<(" + String.join(", ", variables.stream().map(Object::toString).sorted().toList()) + "), " + getRelation() + ">";
    }
}

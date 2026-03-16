package org.jcsp.constraints.nary;

import lombok.Singular;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;
import org.jcsp.constraints.Constraint;
import org.jcsp.variables.Variable;

import java.util.Set;

@Value
@NonFinal
@SuperBuilder
public abstract class NaryConstraint implements Constraint {
    @Singular
    Set<Variable> variables;

    public abstract String getRelation();

    @Override
    public String toString() {
        return "<(" + String.join(", ", variables.stream().map(Object::toString).sorted().toList()) + "), " + getRelation() + ">";
    }
}

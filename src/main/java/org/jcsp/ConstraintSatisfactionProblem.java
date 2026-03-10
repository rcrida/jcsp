package org.jcsp;

import org.jcsp.assignments.Assignment;
import org.jcsp.constraints.Constraint;
import org.jcsp.domains.Domain;
import org.jcsp.variables.Variable;

import java.util.Optional;
import java.util.Set;

public interface ConstraintSatisfactionProblem {
    Set<Variable> getVariables();
    Set<Domain> getDomains();
    Set<Constraint> getConstraints();
    Optional<Assignment> getSolution();
    interface Factory {
        Variable createVariable(String name, Domain domain);
        void addVariable(Variable variable);
        void addConstraint(Constraint constraint);
        ConstraintSatisfactionProblem create();
    }
}

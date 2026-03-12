package org.jcsp;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jcsp.constraints.Constraint;
import org.jcsp.domains.Domain;
import org.jcsp.variables.Variable;

import java.util.Map;
import java.util.Set;

@Value
@Builder(toBuilder = true)
public class ConstraintSatisfactionProblem {
    @Singular
    Map<Variable, Domain> variableDomains;
    @Singular
    Set<Constraint> constraints;

    public static class ConstraintSatisfactionProblemBuilder {
        private final Variable.Factory variableFactory = new Variable.Factory() {};

        public Variable createVariable(String name, Domain domain) {
            final var variable = variableFactory.create(name, domain);
            variableDomain(variable, domain);
            return variable;
        };
    }
}

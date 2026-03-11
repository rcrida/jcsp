package org.jcsp;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jcsp.assignments.Assignment;
import org.jcsp.constraints.Constraint;
import org.jcsp.domains.Domain;
import org.jcsp.variables.Variable;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Value
@Builder(toBuilder = true)
public class ConstraintSatisfactionProblem {
    @Singular
    Map<Variable, Domain> variableDomains;
    @Singular
    Set<Constraint> constraints;

    @Deprecated
    public Set<Variable> getVariables() {
        return variableDomains.keySet();
    }

    @Deprecated
    public Set<Domain> getDomains() {
        return Set.copyOf(variableDomains.values());
    }

    public Optional<Assignment> getSolution() {
        return Optional.empty();
    }

    public static class ConstraintSatisfactionProblemBuilder {
        private final Variable.Factory variableFactory = new Variable.Factory() {};

        public Variable createVariable(String name, Domain domain) {
            final var variable = variableFactory.create(name, domain);
            variableDomain(variable, domain);
            return variable;
        };
    }
}

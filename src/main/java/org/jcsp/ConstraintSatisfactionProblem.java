package org.jcsp;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jcsp.assignments.Assignment;
import org.jcsp.consistency.arc.AC3;
import org.jcsp.consistency.arc.ArcConsistency;
import org.jcsp.consistency.node.NodeConsistency;
import org.jcsp.constraints.Constraint;
import org.jcsp.domains.Domain;
import org.jcsp.variables.Variable;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Value
@Builder(toBuilder = true)
public class ConstraintSatisfactionProblem {
    @Singular
    Map<Variable, Domain> variableDomains;
    @Singular
    Set<Constraint> constraints;

    public Optional<Assignment> getSolution() {
        val solution = NodeConsistency.INSTANCE.apply(this)
                .flatMap(nodeConsistent -> {
                    log.info("Applying AC3 to node consistent problem {}", nodeConsistent);
                    return AC3.INSTANCE.apply(nodeConsistent);
                })
                .map(arcConsistent -> {
                    log.info("Searching arc-consistent problem {}", arcConsistent);
                    return null;
                });
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

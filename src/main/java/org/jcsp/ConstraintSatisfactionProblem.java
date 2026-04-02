package org.jcsp;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.val;
import org.jcsp.constraints.Constraint;
import org.jcsp.constraints.binary.BinaryConstraint;
import org.jcsp.constraints.nary.NaryConstraint;
import org.jcsp.domains.Domain;
import org.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents a constraint satisfaction problem (CSP), which consists of a set of variables,
 * each associated with a domain of values, and a set of constraints that must be satisfied.
 * This class provides methods for constructing and analyzing the problem.
 * It supports both binary and n-ary constraints and allows for the generation
 * of equivalent binary constraints from n-ary ones.
 */
@Value
@Builder(toBuilder = true)
public class ConstraintSatisfactionProblem {
    @Singular
    Map<Variable, Domain> variableDomains;
    @Singular
    Set<Constraint> constraints;

    public Set<BinaryConstraint> getAllBinaryConstraints() {
        val binaryConstraints = getConstraints().stream()
                .filter(c -> c instanceof BinaryConstraint)
                .map(c -> (BinaryConstraint) c)
                .toList();
        val inferredBinaryConstraints = getConstraints().stream()
                .filter(c -> c instanceof NaryConstraint)
                .map(c -> (NaryConstraint) c)
                .flatMap(c -> c.getAsBinaryConstraints().stream())
                .flatMap(Collection::stream)
                .toList();
        return Stream.concat(binaryConstraints.stream(), inferredBinaryConstraints.stream()).collect(Collectors.toSet());
    }

    public BigInteger getSearchSpace() {
        return getVariableDomains().values().stream().map(Domain::size).map(BigInteger::valueOf).reduce(BigInteger.ONE, BigInteger::multiply);
    }

    public static class ConstraintSatisfactionProblemBuilder {
        private final Variable.Factory variableFactory = new Variable.Factory() {};

        public Variable createVariable(String name, Domain domain) {
            final var variable = variableFactory.create(name, domain);
            variableDomain(variable, domain);
            return variable;
        }

        public Variable[] create1dVariableArray(@NonNull String[] labels, @NonNull String namePrefix, @NonNull Domain domain) {
            final var variables = new Variable[labels.length];
            for (int i = 0; i < labels.length; i++) {
                variables[i] = createVariable(String.format("%s%s", namePrefix, labels[i]), domain);
            }
            return variables;
        }

        public Variable[][] create2dVariableArray(@NonNull String[] rows, @NonNull String[] columns, @NonNull String namePrefix, @NonNull Domain domain) {
            final var variables = new Variable[rows.length][columns.length];
            for (int i = 0; i < rows.length; i++) {
                for (int j = 0; j < columns.length; j++) {
                    variables[i][j] = createVariable(String.format("%s%s%s", namePrefix, rows[i], columns[j]), domain);
                }
            }
            return variables;
        }

        public ConstraintSatisfactionProblemBuilder variables(Collection<Variable> variables) {
            for (Variable variable : variables) {
                variable(variable);
            }
            return this;
        }

        public ConstraintSatisfactionProblemBuilder variable(Variable variable) {
            variableDomain(variable, variable.getDomain());
            return this;
        }
    }
}

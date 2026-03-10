package org.jcsp.impl;

import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jcsp.constraints.Constraint;
import org.jcsp.domains.Domain;
import org.jcsp.variables.Variable;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ConstraintSatisfactionProblemImpl implements ConstraintSatisfactionProblem {
    private final Set<Variable> variables;
    private final Set<Domain> domains;
    private final Set<Constraint> constraints;

    private ConstraintSatisfactionProblemImpl(Set<Variable> variables, Set<Constraint> constraints) {
        this.variables = variables;
        this.domains = variables.stream().map(Variable::getDomain).collect(Collectors.toSet());
        this.constraints = constraints;
    }

    @Override
    public Set<Variable> getVariables() {
        return variables;
    }

    @Override
    public Set<Domain> getDomains() {
        return domains;
    }

    @Override
    public Set<Constraint> getConstraints() {
        return constraints;
    }

    @Override
    public Optional<Assignment> getSolution() {
        return Optional.empty();
    }

    public static class Factory implements ConstraintSatisfactionProblem.Factory {
        private final Variable.Factory variableFactory = new Variable.Factory() {};
        private final Set<Variable> variables = new HashSet<>();
        private final Set<Constraint> constraints = new HashSet<>();

        @Override
        public Variable createVariable(String name, Domain domain) {
            final var variable = variableFactory.create(name, domain);
            variables.add(variable);
            return variable;
        }

        @Override
        public void addVariable(Variable variable) {
            variables.add(variable);
        }

        @Override
        public void addConstraint(Constraint constraint) {
            constraints.add(constraint);
        }

        @Override
        public ConstraintSatisfactionProblem create() {
            return new ConstraintSatisfactionProblemImpl(variables, constraints);
        }
    }
}

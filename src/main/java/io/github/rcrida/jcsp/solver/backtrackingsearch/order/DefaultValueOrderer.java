package io.github.rcrida.jcsp.solver.backtrackingsearch.order;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.stream.Stream;

public class DefaultValueOrderer implements DomainValuesOrderer {
    public static final DefaultValueOrderer INSTANCE = new DefaultValueOrderer();

    private DefaultValueOrderer() {}

    @Override
    public Stream<?> order(@NonNull ConstraintSatisfactionProblem csp, @NonNull Variable<?> variable, @NonNull Assignment assignment) {
        return csp.findDomain(variable).stream().flatMap(Domain::stream);
    }
}

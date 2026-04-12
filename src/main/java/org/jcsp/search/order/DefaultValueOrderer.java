package org.jcsp.search.order;

import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jcsp.domains.Domain;
import org.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.stream.Stream;

public class DefaultValueOrderer implements DomainValuesOrderer {
    public static final DefaultValueOrderer INSTANCE = new DefaultValueOrderer();

    private DefaultValueOrderer() {}

    @Override
    public Stream<?> order(@NonNull ConstraintSatisfactionProblem csp, @NonNull Variable variable, @NonNull Assignment assignment) {
        return csp.getDomain(variable).stream().flatMap(Domain::stream);
    }
}

package org.jcsp.search;

import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;

import java.util.Optional;
import java.util.stream.Stream;

public interface Search {
    Optional<Assignment> search(ConstraintSatisfactionProblem csp);
    Stream<Assignment> searchStream(ConstraintSatisfactionProblem csp);
}

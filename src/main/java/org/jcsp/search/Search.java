package org.jcsp.search;

import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;

import java.util.Optional;

public interface Search {
    Optional<Assignment> search(ConstraintSatisfactionProblem csp);
}

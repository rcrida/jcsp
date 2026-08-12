package io.github.rcrida.jcsp.parser.xcsp3;

/**
 * Thrown when an XCSP3 instance uses a constraint, constraint variant, or expression operator
 * that {@link Xcsp3Parser} does not map onto jcsp's constraint API. Deliberately unchecked and
 * thrown immediately during parsing rather than silently skipping the construct, since silently
 * dropping a constraint would leave the resulting {@link
 * io.github.rcrida.jcsp.ConstraintSatisfactionProblem} under-constrained and able to report
 * solutions that violate the original instance.
 */
public class UnsupportedXcsp3ConstraintException extends RuntimeException {
    public UnsupportedXcsp3ConstraintException(String message) {
        super(message);
    }
}

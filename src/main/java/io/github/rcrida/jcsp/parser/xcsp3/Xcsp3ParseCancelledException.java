package io.github.rcrida.jcsp.parser.xcsp3;

/**
 * Thrown by {@link Xcsp3Parser#parse(java.nio.file.Path, io.github.rcrida.jcsp.solver.Cancellation)}
 * when its {@link io.github.rcrida.jcsp.solver.Cancellation} is cancelled before the instance is
 * fully read: the parse was abandoned, and says nothing about the instance.
 */
public class Xcsp3ParseCancelledException extends RuntimeException {
    public Xcsp3ParseCancelledException() {
        super("XCSP3 parse cancelled");
    }
}

package io.github.rcrida.jcsp.parser.xcsp3;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Entry point for reading an <a href="https://xcsp.org">XCSP3</a> instance file into a jcsp
 * {@link io.github.rcrida.jcsp.ConstraintSatisfactionProblem}, via {@code xcsp3-tools}' XML
 * parser and {@link Xcsp3CallbackHandler}'s callback-driven mapping onto jcsp's constraint
 * builder API.
 * <p>
 * Covers variables/domains, {@code intension} (including the {@code dist} operator), {@code
 * extension} (support/positive tables only), {@code allDifferent}, {@code sum}, {@code count},
 * {@code element}, {@code ordered}, {@code lex}, {@code cumulative}, {@code circuit}, {@code
 * binPacking}, and single {@code minimize}/{@code maximize} objectives. A {@code group} of any of
 * these is also covered "for free": {@code xcsp3-tools} expands a {@code group}'s template against
 * each {@code args} line before this class's callbacks ever see it, so a group is really just
 * repeated ordinary constraints of whichever type it wraps. Any other construct -- {@code
 * regular}/{@code mdd}, {@code nValues}, {@code cardinality}, {@code channel}, {@code
 * diffn}/{@code noOverlap}, conflict-mode {@code extension}, {@code slide}, multi-objective COP,
 * {@code instantiation} -- throws either {@link UnsupportedXcsp3ConstraintException} (a
 * recognised-but-unmappable variant) or a plain {@link RuntimeException} from the underlying
 * library (an entirely unrecognised construct); either way parsing fails immediately rather than
 * silently returning an under-constrained model.
 */
public final class Xcsp3Parser {

    private Xcsp3Parser() {
    }

    /**
     * Parses the XCSP3 file at {@code xcsp3File} into an {@link Xcsp3Instance}.
     *
     * @throws IOException if the file cannot be read or is not well-formed XCSP3
     * @throws UnsupportedXcsp3ConstraintException if the instance uses a recognised-but-unmappable construct
     */
    public static Xcsp3Instance parse(Path xcsp3File) throws IOException {
        Xcsp3CallbackHandler handler = new Xcsp3CallbackHandler();
        try {
            handler.loadInstance(xcsp3File.toString());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to parse XCSP3 instance: " + xcsp3File, e);
        }
        return handler.toInstance();
    }
}

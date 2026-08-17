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
 * extension} (support/positive tables only), {@code allDifferent} (plain list, {@code matrix}, and
 * multi-list "distinct vectors" forms), {@code allEqual}, {@code sum},
 * {@code count}, {@code nValues}, {@code cardinality} (fixed-value form, both fixed-occurrence --
 * see {@link
 * Xcsp3CallbackHandler#buildCtrCardinality(String, org.xcsp.parser.entries.XVariables.XVarInteger[], boolean, int[], int[])}
 * -- and min/max-occurrence-range),
 * {@code element}, {@code minimum}/{@code maximum}, {@code ordered}, {@code lex}, {@code
 * cumulative}, {@code circuit}, {@code binPacking}, {@code regular}, {@code mdd} (mapped onto the
 * same {@link io.github.rcrida.jcsp.constraints.nary.RegularConstraint} {@code regular} uses -- see
 * {@link Xcsp3CallbackHandler#buildRegularOrMdd}), {@code channel} (self-inverse and two-array
 * forms), {@code noOverlap} (1D and 2D), {@code instantiation}, and single, sum-type, or unweighted
 * maximum-type {@code minimize}/{@code maximize} objectives. A {@code group} or {@code slide}
 * wrapping any of these is also covered "for free": {@code xcsp3-tools} expands both back into
 * repeated ordinary constraints of whichever type they wrap (a {@code group} against each {@code
 * args} line, a {@code slide} against each precomputed window) before this class's callbacks ever
 * see them.
 * <p>
 * Every constraint type above except the variable-target form of {@code sum} supports {@code
 * FULL} ({@code reifiedBy}) and {@code HALF_FROM} ({@code hreifiedFrom}, {@code indicator ->
 * body}) reification -- {@code HALF_TO} ({@code hreifiedTo}, {@code body -> indicator}) always
 * throws, since it has no jcsp builder counterpart. {@code ordered} routes through a new {@link
 * io.github.rcrida.jcsp.constraints.nary.OrderedConstraint} (generalising {@link
 * io.github.rcrida.jcsp.constraints.nary.IncreasingConstraint}/{@link
 * io.github.rcrida.jcsp.constraints.nary.DecreasingConstraint} to a runtime-chosen operator)
 * rather than jcsp's own pairwise decomposition, since reifying a chain needs one {@link
 * io.github.rcrida.jcsp.constraints.Constraint} object for the whole thing, not {@code N-1}
 * separate ones. {@code nValues}' reification applies to its condition comparison only, not the
 * underlying {@code count}-to-distinct-values definition (never itself a proposition with a
 * truth value). Any other construct -- the variable-valued forms of {@code cardinality}, the
 * single-value "hot index" form of {@code channel}, {@code noOverlap} with dimensionality other
 * than 1 or 2, conflict-mode {@code extension}, multi-objective COP, {@code minimum}/{@code
 * maximum}/{@code product}/{@code nValues}-type or general expression objectives -- throws either
 * {@link UnsupportedXcsp3ConstraintException} (a recognised-but-unmappable variant) or a plain
 * {@link RuntimeException} from the underlying library (an entirely unrecognised construct); either
 * way parsing fails immediately rather than silently returning an under-constrained model.
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

package io.github.rcrida.jcsp.parser.xcsp3;

import org.xcsp.common.Types.TypeVar;
import org.xcsp.common.domains.Domains;
import org.xcsp.parser.entries.XVariables.XVar;
import org.xcsp.parser.entries.XVariables.XVarInteger;

/**
 * Builds the {@code xcsp3-tools} objects a recognizer needs when it is exercised directly rather
 * than through a parsed file. Shared by the recognizer unit tests, which construct their trees by
 * hand so they can control what {@code dispatch} returns and how wide a scope is -- neither of
 * which an {@code <intension>} fixture expresses directly.
 */
final class XcspTestNodes {

    private XcspTestNodes() {
    }

    /** An integer variable over {@code [min, max]}, as the parser would have produced it. */
    static XVarInteger var(String name, int min, int max) {
        return (XVarInteger) XVar.build(name, TypeVar.integer, new Domains.Dom(min, max));
    }

    /** A handler with {@code variables} registered, so name lookups and domains resolve. */
    static Xcsp3CallbackHandler handlerWith(XVarInteger... variables) {
        Xcsp3CallbackHandler handler = new Xcsp3CallbackHandler();
        for (XVarInteger v : variables) {
            handler.buildVarInteger(v, (int) v.firstValue(), (int) v.lastValue());
        }
        return handler;
    }
}

package io.github.rcrida.jcsp.solver.examples.csplib;

import io.github.rcrida.jcsp.parser.xcsp3.Xcsp3Instance;
import io.github.rcrida.jcsp.parser.xcsp3.Xcsp3Parser;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Resolves and parses an XCSP3 instance file under {@code src/test/resources/xcsp3/csplib/}. */
final class Xcsp3CsplibResource {

    private Xcsp3CsplibResource() {
    }

    static Path resource(String name) throws URISyntaxException {
        return Paths.get(Xcsp3CsplibResource.class.getResource("/xcsp3/csplib/" + name).toURI());
    }

    /** Wraps {@link Xcsp3Parser#parse}'s checked exceptions as unchecked, since every caller here treats a
     * missing/malformed instance file as a program error rather than something to recover from. */
    static Xcsp3Instance parse(String name) {
        try {
            return Xcsp3Parser.parse(resource(name));
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException("Failed to load XCSP3 instance: " + name, e);
        }
    }
}

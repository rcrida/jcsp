package io.github.rcrida.jcsp.solver.examples.csplib;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Resolves an XCSP3 instance file under {@code src/test/resources/xcsp3/csplib/} to a {@link Path}. */
final class Xcsp3CsplibResource {

    private Xcsp3CsplibResource() {
    }

    static Path resource(String name) throws URISyntaxException {
        return Paths.get(Xcsp3CsplibResource.class.getResource("/xcsp3/csplib/" + name).toURI());
    }
}

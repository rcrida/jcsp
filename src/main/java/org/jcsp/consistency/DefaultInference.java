package org.jcsp.consistency;

import lombok.extern.slf4j.Slf4j;
import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.consistency.arc.AC3;
import org.jcsp.consistency.node.NodeConsistency;

import java.util.Optional;

@Slf4j
public class DefaultInference implements Inference {
    public static final Inference INSTANCE = new DefaultInference();

    private DefaultInference() {}

    @Override
    public Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem csp) {
        return NodeConsistency.INSTANCE.apply(csp)
                .flatMap(nodeConsistent -> {
                    log.debug("Applying AC3 to node consistent problem {}", nodeConsistent);
                    return AC3.INSTANCE.apply(nodeConsistent);
                });
    }
}

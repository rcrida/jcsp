package io.github.rcrida.jcsp.solver.examples.csplib;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Car Sequencing (CSPLib prob001): a production line assembles {@code NUM_CARS} cars, each an
 * instance of one of {@code NUM_CLASSES} classes; each class requires a fixed subset of
 * {@code NUM_OPTIONS} options (air-conditioning, sun-roof, etc.). Each option {@code o} has a
 * capacity constraint "at most {@code MAX_PER_BLOCK[o]} cars in any {@code BLOCK_SIZE[o]}
 * consecutive slots may require it", and the exact number of cars of each class to build is
 * fixed in advance. The task is to find a sequencing (permutation with repetition) of classes
 * across the slots that respects both.
 * <p>
 * Instance data is the example given in CSPLib prob001's own specification
 * ({@code Problems/prob001/specification.md} on GitHub), originally from Dincbas et al., ECAI88 —
 * 10 cars, 5 options, 6 classes. The specification also gives one known-valid sequence, which
 * {@link #dincbasEtAlReferenceSequence_isASolutionOfThisModel()} checks directly against this
 * model via {@link Assignment#isSolution}, confirming the transcription is faithful.
 * <p>
 * {@link #CSP} is parsed from a hand-authored XCSP3 instance file (see its own comment for
 * provenance) rather than built via jcsp's constraint builder API: {@code cardinality} pins each
 * class's exact production count, {@code element} links each slot's per-option 0/1 requirement to
 * its class (a fixed per-option column of which classes need it), and a {@code slide} of {@code
 * sum} per option expresses "at most {@code MAX_PER_BLOCK[o]} requiring cars per {@code
 * BLOCK_SIZE[o]}-wide window" -- both {@code cardinality} and {@code slide} were added to {@code
 * Xcsp3CallbackHandler} specifically to make this migration possible (the latter needed no new
 * code at all, since {@code xcsp3-tools} auto-expands a slide's template the same way it does a
 * group's). {@code requires[i][o]} is therefore a plain 0/1 {@code Variable<Integer>} here, not
 * the {@code Variable<Boolean>} the previous hand-built model used -- XCSP3-core has no boolean
 * variable type of its own.
 */
public class Prob001CarSequencingTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final int NUM_CARS = 10;
    static final int NUM_OPTIONS = 5;
    static final int NUM_CLASSES = 6;

    static final int[] MAX_PER_BLOCK = {1, 2, 1, 2, 1};
    static final int[] BLOCK_SIZE = {2, 3, 3, 5, 5};
    static final int[] CLASS_DEMAND = {1, 1, 2, 2, 2, 2};

    // CLASS_OPTIONS[class - 1][option]: whether that class requires that option.
    static final boolean[][] CLASS_OPTIONS = {
            {true, false, true, true, false},
            {false, false, false, true, false},
            {false, true, false, false, true},
            {false, true, false, true, false},
            {true, false, true, false, false},
            {true, true, false, false, false},
    };

    // The valid sequence given in the specification (0-indexed classes 0,1,5,2,4,3,3,4,2,5),
    // converted to this model's 1-indexed classes.
    static final int[] REFERENCE_SEQUENCE = {1, 2, 6, 3, 5, 4, 4, 5, 3, 6};

    static final List<Variable<Integer>> SLOT = IntStream.range(0, NUM_CARS)
            .mapToObj(i -> F.<Integer>create("slot[" + i + "]"))
            .toList();
    static final List<List<Variable<Integer>>> REQUIRES = IntStream.range(0, NUM_CARS)
            .mapToObj(i -> IntStream.range(0, NUM_OPTIONS)
                    .mapToObj(o -> F.<Integer>create("requires[" + i + "][" + o + "]"))
                    .toList())
            .toList();

    static final ConstraintSatisfactionProblem CSP = Xcsp3CsplibResource.parse("car-sequencing-10x5x6.xml").csp();

    @Test
    void solutionExists_andRespectsClassDemand() {
        val result = Solver.Factory.INSTANCE.createSolver(CSP).getSolution();
        assertThat(result).isPresent();

        int[] counts = new int[NUM_CLASSES + 1];
        for (var s : SLOT) counts[result.get().getValue(s).orElseThrow()]++;
        for (int c = 1; c <= NUM_CLASSES; c++) {
            assertThat(counts[c]).as("demand for class %d", c).isEqualTo(CLASS_DEMAND[c - 1]);
        }
    }

    @Test
    void solutionRespectsOptionCapacityInEveryWindow() {
        val result = Solver.Factory.INSTANCE.createSolver(CSP).getSolution().orElseThrow();
        int[] sequence = SLOT.stream().mapToInt(s -> result.getValue(s).orElseThrow()).toArray();

        for (int o = 0; o < NUM_OPTIONS; o++) {
            int block = BLOCK_SIZE[o];
            for (int p = 0; p + block <= NUM_CARS; p++) {
                int required = 0;
                for (int i = p; i < p + block; i++) {
                    if (CLASS_OPTIONS[sequence[i] - 1][o]) required++;
                }
                assertThat(required).as("option %d, window starting at %d", o, p).isLessThanOrEqualTo(MAX_PER_BLOCK[o]);
            }
        }
    }

    @Test
    void dincbasEtAlReferenceSequence_isASolutionOfThisModel() {
        Map<Variable<?>, Object> values = new HashMap<>();
        for (int i = 0; i < NUM_CARS; i++) {
            values.put(SLOT.get(i), REFERENCE_SEQUENCE[i]);
            for (int o = 0; o < NUM_OPTIONS; o++) {
                values.put(REQUIRES.get(i).get(o), CLASS_OPTIONS[REFERENCE_SEQUENCE[i] - 1][o] ? 1 : 0);
            }
        }

        val assignment = Assignment.of(values);
        assertThat(assignment.isSolution(CSP)).isTrue();
    }
}

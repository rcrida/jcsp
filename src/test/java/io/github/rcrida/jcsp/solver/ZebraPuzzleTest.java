package io.github.rcrida.jcsp.solver;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.binary.Operator;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Einstein's Zebra Puzzle modelled as a CSP.
 *
 * <p>Each of the 25 attribute values (e.g. BRIT, RED, MILK, DOG, PALL_MALL) is a variable
 * whose domain is a house position 1–5. Clues become unary fixed-value constraints,
 * binary equality/offset constraints, or "next-to" binary predicate constraints.
 *
 * <pre>
 * Solution:
 *   House 1: Norwegian  | Yellow | Water  | Cat   | Dunhill
 *   House 2: Dane       | Blue   | Tea    | Horse | Blends
 *   House 3: Brit       | Red    | Milk   | Bird  | Pall Mall
 *   House 4: German     | Green  | Coffee | Fish  | Prince
 *   House 5: Swede      | White  | Beer   | Dog   | Blue Master
 *
 * Answer: The German owns the fish.
 * </pre>
 */
public class ZebraPuzzleTest {
    static final Domain<Integer> HOUSES = IntRangeDomain.of(1, 5);
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    // Nationalities
    static final Variable<Integer> BRIT      = F.create("BRIT");
    static final Variable<Integer> SWEDE     = F.create("SWEDE");
    static final Variable<Integer> DANE      = F.create("DANE");
    static final Variable<Integer> NORWEGIAN = F.create("NORWEGIAN");
    static final Variable<Integer> GERMAN    = F.create("GERMAN");

    // House colours
    static final Variable<Integer> RED    = F.create("RED");
    static final Variable<Integer> GREEN  = F.create("GREEN");
    static final Variable<Integer> WHITE  = F.create("WHITE");
    static final Variable<Integer> YELLOW = F.create("YELLOW");
    static final Variable<Integer> BLUE   = F.create("BLUE");

    // Drinks
    static final Variable<Integer> TEA    = F.create("TEA");
    static final Variable<Integer> COFFEE = F.create("COFFEE");
    static final Variable<Integer> MILK   = F.create("MILK");
    static final Variable<Integer> BEER   = F.create("BEER");
    static final Variable<Integer> WATER  = F.create("WATER");

    // Pets
    static final Variable<Integer> DOG   = F.create("DOG");
    static final Variable<Integer> BIRD  = F.create("BIRD");
    static final Variable<Integer> CAT   = F.create("CAT");
    static final Variable<Integer> HORSE = F.create("HORSE");
    static final Variable<Integer> FISH  = F.create("FISH");

    // Cigarettes
    static final Variable<Integer> PALL_MALL   = F.create("PALL_MALL");
    static final Variable<Integer> DUNHILL     = F.create("DUNHILL");
    static final Variable<Integer> BLENDS      = F.create("BLENDS");
    static final Variable<Integer> BLUE_MASTER = F.create("BLUE_MASTER");
    static final Variable<Integer> PRINCE      = F.create("PRINCE");

    static final BiPredicate<Integer, Integer> NEXT_TO =
            (a, b) -> Math.abs(a - b) == 1;

    static ConstraintSatisfactionProblem puzzle() {
        return ConstraintSatisfactionProblem.builder()
                // Domains
                .variableDomain(BRIT,      HOUSES).variableDomain(SWEDE,       HOUSES)
                .variableDomain(DANE,      HOUSES).variableDomain(NORWEGIAN,   HOUSES)
                .variableDomain(GERMAN,    HOUSES)
                .variableDomain(RED,       HOUSES).variableDomain(GREEN,       HOUSES)
                .variableDomain(WHITE,     HOUSES).variableDomain(YELLOW,      HOUSES)
                .variableDomain(BLUE,      HOUSES)
                .variableDomain(TEA,       HOUSES).variableDomain(COFFEE,      HOUSES)
                .variableDomain(MILK,      HOUSES).variableDomain(BEER,        HOUSES)
                .variableDomain(WATER,     HOUSES)
                .variableDomain(DOG,       HOUSES).variableDomain(BIRD,        HOUSES)
                .variableDomain(CAT,       HOUSES).variableDomain(HORSE,       HOUSES)
                .variableDomain(FISH,      HOUSES)
                .variableDomain(PALL_MALL, HOUSES).variableDomain(DUNHILL,     HOUSES)
                .variableDomain(BLENDS,    HOUSES).variableDomain(BLUE_MASTER, HOUSES)
                .variableDomain(PRINCE,    HOUSES)
                // All-diff within each category
                .allDiffConstraint(Set.of(BRIT, SWEDE, DANE, NORWEGIAN, GERMAN))
                .allDiffConstraint(Set.of(RED, GREEN, WHITE, YELLOW, BLUE))
                .allDiffConstraint(Set.of(TEA, COFFEE, MILK, BEER, WATER))
                .allDiffConstraint(Set.of(DOG, BIRD, CAT, HORSE, FISH))
                .allDiffConstraint(Set.of(PALL_MALL, DUNHILL, BLENDS, BLUE_MASTER, PRINCE))
                // Clue 1: The Brit lives in the red house.
                .equalsConstraint(BRIT, RED)
                // Clue 2: The Swede keeps dogs.
                .equalsConstraint(SWEDE, DOG)
                // Clue 3: The Dane drinks tea.
                .equalsConstraint(DANE, TEA)
                // Clue 4: The green house is immediately to the left of the white house.
                .offsetConstraint(GREEN, 1, Operator.EQ, WHITE)
                // Clue 5: The green house owner drinks coffee.
                .equalsConstraint(GREEN, COFFEE)
                // Clue 6: The Pall Mall smoker keeps birds.
                .equalsConstraint(PALL_MALL, BIRD)
                // Clue 7: The yellow house owner smokes Dunhill.
                .equalsConstraint(YELLOW, DUNHILL)
                // Clue 8: The man in the center house drinks milk.
                .equalsConstraint(MILK, 3)
                // Clue 9: The Norwegian lives in the first house.
                .equalsConstraint(NORWEGIAN, 1)
                // Clue 10: The Blends smoker lives next to the cat owner.
                .biPredicateConstraint(BLENDS, CAT, NEXT_TO)
                // Clue 11: The horse owner lives next to the Dunhill smoker.
                .biPredicateConstraint(HORSE, DUNHILL, NEXT_TO)
                // Clue 12: The Blue Master smoker drinks beer.
                .equalsConstraint(BLUE_MASTER, BEER)
                // Clue 13: The German smokes Prince.
                .equalsConstraint(GERMAN, PRINCE)
                // Clue 14: The Norwegian lives next to the blue house.
                .biPredicateConstraint(NORWEGIAN, BLUE, NEXT_TO)
                // Clue 15: The Blends smoker has a neighbor who drinks water.
                .biPredicateConstraint(BLENDS, WATER, NEXT_TO)
                .build();
    }

    @Test
    void solution() {
        val csp = puzzle();
        val result = Solver.Factory.INSTANCE.createSolver().getSolution(csp);
        assertThat(result).hasValueSatisfying(assignment -> {
            assertThat(assignment.isSolution(csp)).isTrue();
            assertThat(assignment).isEqualTo(Assignment.of(Map.ofEntries(
                    Map.entry(NORWEGIAN, 1), Map.entry(DANE,  2), Map.entry(BRIT,   3), Map.entry(GERMAN, 4), Map.entry(SWEDE,       5),
                    Map.entry(YELLOW,    1), Map.entry(BLUE,  2), Map.entry(RED,    3), Map.entry(GREEN,  4), Map.entry(WHITE,       5),
                    Map.entry(WATER,     1), Map.entry(TEA,   2), Map.entry(MILK,   3), Map.entry(COFFEE, 4), Map.entry(BEER,        5),
                    Map.entry(CAT,       1), Map.entry(HORSE, 2), Map.entry(BIRD,   3), Map.entry(FISH,   4), Map.entry(DOG,         5),
                    Map.entry(DUNHILL,   1), Map.entry(BLENDS,2), Map.entry(PALL_MALL, 3), Map.entry(PRINCE, 4), Map.entry(BLUE_MASTER, 5)
            )));
            System.out.println("Solution:   " + assignment);
            System.out.println("Statistics: " + assignment.getStatistics());
        });
    }

    @Test
    void uniqueSolution() {
        val csp = puzzle();
        assertThat(Solver.Factory.INSTANCE.createSolver().getSolutions(csp)).hasSize(1);
    }
}

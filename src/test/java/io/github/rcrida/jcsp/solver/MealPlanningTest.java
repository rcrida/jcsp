package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.EnumDomain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demonstrates {@code AmongConstraint} for category-bounded meal planning.
 *
 * <p>Five meal slots (breakfast, morning snack, lunch, afternoon snack, dinner) are each
 * assigned one food type from:
 * <pre>
 *   PROTEIN, CARBS, FAT, VEGETABLES, FRUITS
 * </pre>
 * The "produce" category is {@code {VEGETABLES, FRUITS}}. Among constraints enforce
 * how many meals fall into specific categories across the day.
 *
 * <p>With exactly 2 produce meals: C(5,2) × 2² × 3³ = 10 × 4 × 27 = 1080 solutions.
 */
public class MealPlanningTest {
    enum Food { PROTEIN, CARBS, FAT, VEGETABLES, FRUITS }

    static final Set<Food> PRODUCE    = Set.of(Food.VEGETABLES, Food.FRUITS);
    static final Set<Food> NON_PRODUCE = Set.of(Food.PROTEIN, Food.CARBS, Food.FAT);

    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Food> BREAKFAST        = F.create("breakfast");
    static final Variable<Food> MORNING_SNACK    = F.create("morning_snack");
    static final Variable<Food> LUNCH            = F.create("lunch");
    static final Variable<Food> AFTERNOON_SNACK  = F.create("afternoon_snack");
    static final Variable<Food> DINNER           = F.create("dinner");

    static final Set<Variable<Food>> ALL_MEALS = Set.of(
            BREAKFAST, MORNING_SNACK, LUNCH, AFTERNOON_SNACK, DINNER);

    static ConstraintSatisfactionProblem basicPlan(Operator op, int n) {
        val domain = EnumDomain.allOf(Food.class);
        return ConstraintSatisfactionProblem.builder()
                .variableDomain(BREAKFAST,       domain)
                .variableDomain(MORNING_SNACK,   domain)
                .variableDomain(LUNCH,           domain)
                .variableDomain(AFTERNOON_SNACK, domain)
                .variableDomain(DINNER,          domain)
                .amongConstraint(ALL_MEALS, PRODUCE, op, n)
                .build();
    }

    @Test
    void exactlyTwoProduce_correctSolutionCount() {
        // C(5,2) × 2^2 × 3^3 = 10 × 4 × 27 = 1080
        val solutions = Solver.Factory.INSTANCE.createSolver()
                .getSolutions(basicPlan(Operator.EQ, 2)).toList();
        assertThat(solutions).hasSize(1080);
    }

    @Test
    void exactlyTwoProduce_allSolutionsSatisfyConstraint() {
        val solutions = Solver.Factory.INSTANCE.createSolver()
                .getSolutions(basicPlan(Operator.EQ, 2)).toList();
        assertThat(solutions).allSatisfy(sol -> {
            long produceCount = ALL_MEALS.stream()
                    .map(v -> sol.getValue(v).orElseThrow())
                    .filter(PRODUCE::contains)
                    .count();
            assertThat(produceCount).isEqualTo(2);
        });
    }

    @Test
    void atLeastThreeProduce_correctSolutionCount() {
        // Choose 3, 4, or 5 slots for produce.
        // k=3: C(5,3)×2^3×3^2 = 10×8×9 = 720
        // k=4: C(5,4)×2^4×3^1 = 5×16×3  = 240
        // k=5: C(5,5)×2^5×3^0 = 1×32×1  = 32
        // Total: 720 + 240 + 32 = 992
        val solutions = Solver.Factory.INSTANCE.createSolver()
                .getSolutions(basicPlan(Operator.GEQ, 3)).toList();
        assertThat(solutions).hasSize(992);
    }

    @Test
    void atMostOneProduce_correctSolutionCount() {
        // k=0: 3^5 = 243
        // k=1: C(5,1)×2×3^4 = 5×2×81 = 810
        // Total: 243 + 810 = 1053
        val solutions = Solver.Factory.INSTANCE.createSolver()
                .getSolutions(basicPlan(Operator.LEQ, 1)).toList();
        assertThat(solutions).hasSize(1053);
    }

    @Test
    void twoIndependentAmongConstraints_combinedCount() {
        // Exactly 2 produce (VEGETABLES or FRUITS) AND exactly 1 protein (PROTEIN).
        // PRODUCE and {PROTEIN} are disjoint so the constraints are independent.
        // Count: C(5,2) × C(3,1) × 2^2 × 1 × 2^2 = 10 × 3 × 4 × 1 × 4 = 480
        //   - C(5,2): choose which 2 slots are produce
        //   - C(3,1): choose which of the remaining 3 slots is protein
        //   - 2^2: each produce slot is VEGETABLES or FRUITS
        //   - 1:   the protein slot must be PROTEIN
        //   - 2^2: the remaining 2 slots are CARBS or FAT
        val domain = EnumDomain.allOf(Food.class);
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(BREAKFAST,       domain)
                .variableDomain(MORNING_SNACK,   domain)
                .variableDomain(LUNCH,           domain)
                .variableDomain(AFTERNOON_SNACK, domain)
                .variableDomain(DINNER,          domain)
                .amongConstraint(ALL_MEALS, PRODUCE,                Operator.EQ, 2)
                .amongConstraint(ALL_MEALS, Set.of(Food.PROTEIN),   Operator.EQ, 1)
                .build();
        val solutions = Solver.Factory.INSTANCE.createSolver().getSolutions(csp).toList();
        assertThat(solutions).hasSize(480);
        assertThat(solutions).allSatisfy(sol -> {
            long produceMeals  = ALL_MEALS.stream().map(v -> sol.getValue(v).orElseThrow()).filter(PRODUCE::contains).count();
            long proteinMeals  = ALL_MEALS.stream().map(v -> sol.getValue(v).orElseThrow()).filter(f -> f == Food.PROTEIN).count();
            assertThat(produceMeals).isEqualTo(2);
            assertThat(proteinMeals).isEqualTo(1);
        });
    }

    @Test
    void propagation_forcesRemainingSlots_whenQuotaBarelyMet() {
        // Only morning_snack and afternoon_snack have produce-possible domains.
        // Constraint: exactly 2 produce. maxCount=2==n → both must be produce.
        // This exercises the EQ/GEQ "force inclusion" propagation path.
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(BREAKFAST,       EnumDomain.of(Food.PROTEIN, Food.CARBS, Food.FAT))
                .variableDomain(MORNING_SNACK,   EnumDomain.allOf(Food.class))
                .variableDomain(LUNCH,           EnumDomain.of(Food.PROTEIN, Food.CARBS, Food.FAT))
                .variableDomain(AFTERNOON_SNACK, EnumDomain.allOf(Food.class))
                .variableDomain(DINNER,          EnumDomain.of(Food.PROTEIN, Food.CARBS, Food.FAT))
                .amongConstraint(ALL_MEALS, PRODUCE, Operator.EQ, 2)
                .build();
        val solutions = Solver.Factory.INSTANCE.createSolver().getSolutions(csp).toList();
        // morning_snack and afternoon_snack both forced to produce (2 choices each).
        // Other 3 slots: 3 non-produce choices each. 2×2×3^3 = 4×27 = 108 solutions.
        assertThat(solutions).hasSize(108);
        assertThat(solutions).allSatisfy(sol -> {
            assertThat(sol.getValue(MORNING_SNACK).orElseThrow()).isIn(Food.VEGETABLES, Food.FRUITS);
            assertThat(sol.getValue(AFTERNOON_SNACK).orElseThrow()).isIn(Food.VEGETABLES, Food.FRUITS);
        });
    }

    @Test
    void infeasible_whenNotEnoughProducePossible() {
        // All slots restricted to non-produce; can't reach exactly 2 produce.
        val nonProduceDomain = EnumDomain.of(Food.PROTEIN, Food.CARBS, Food.FAT);
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(BREAKFAST,       nonProduceDomain)
                .variableDomain(MORNING_SNACK,   nonProduceDomain)
                .variableDomain(LUNCH,           nonProduceDomain)
                .variableDomain(AFTERNOON_SNACK, nonProduceDomain)
                .variableDomain(DINNER,          nonProduceDomain)
                .amongConstraint(ALL_MEALS, PRODUCE, Operator.EQ, 2)
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver().getSolutions(csp)).isEmpty();
    }

    @Test
    void sequentialMealsWithNutritionBalance() {
        // A practical balanced-day plan: among all 5 meals,
        // at least 1 must be PROTEIN-based, at least 1 must be produce.
        // Verify at least one valid plan exists and all plans satisfy the nutrition constraints.
        val domain = EnumDomain.allOf(Food.class);
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(BREAKFAST,       domain)
                .variableDomain(MORNING_SNACK,   domain)
                .variableDomain(LUNCH,           domain)
                .variableDomain(AFTERNOON_SNACK, domain)
                .variableDomain(DINNER,          domain)
                .amongConstraint(ALL_MEALS, Set.of(Food.PROTEIN), Operator.GEQ, 1)
                .amongConstraint(ALL_MEALS, PRODUCE,              Operator.GEQ, 1)
                .increasingConstraint(List.of(BREAKFAST, MORNING_SNACK, LUNCH, AFTERNOON_SNACK, DINNER))
                .build();
        val solution = Solver.Factory.INSTANCE.createSolver().getSolution(csp);
        assertThat(solution).isPresent();
        assertThat(solution).hasValueSatisfying(sol -> {
            long proteinMeals = ALL_MEALS.stream()
                    .map(v -> sol.getValue(v).orElseThrow())
                    .filter(f -> f == Food.PROTEIN).count();
            long produceMeals = ALL_MEALS.stream()
                    .map(v -> sol.getValue(v).orElseThrow())
                    .filter(PRODUCE::contains).count();
            assertThat(proteinMeals).isGreaterThanOrEqualTo(1);
            assertThat(produceMeals).isGreaterThanOrEqualTo(1);
        });
    }
}

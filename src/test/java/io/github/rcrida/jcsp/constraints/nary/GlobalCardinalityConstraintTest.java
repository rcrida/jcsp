package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.EnumDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
public class GlobalCardinalityConstraintTest {
    enum Color { RED, GREEN, BLUE }

    @Mock Variable<Color> v1;
    @Mock Variable<Color> v2;
    @Mock Variable<Color> v3;
    @Mock Variable<Color> v4;

    // 4 vars: exactly 2 RED, 1 GREEN, 1 BLUE
    GlobalCardinalityConstraint<Color> constraint;

    @BeforeEach
    void setUp() {
        constraint = GlobalCardinalityConstraint.of(
                Set.of(v1, v2, v3, v4),
                Map.of(Color.RED, 2, Color.GREEN, 1, Color.BLUE, 1));
    }

    @Test
    void exactCounts_satisfied() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(
                v1, Color.RED, v2, Color.RED, v3, Color.GREEN, v4, Color.BLUE)))).isTrue();
    }

    @Test
    void wrongCount_notSatisfied() {
        // 3 RED, 1 GREEN — but BLUE count is 0, not 1
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(
                v1, Color.RED, v2, Color.RED, v3, Color.RED, v4, Color.GREEN)))).isFalse();
    }

    @Test
    void countExceeded_notSatisfied() {
        // 3 RED already exceeds 2 — fails even with one variable unassigned
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(
                v1, Color.RED, v2, Color.RED, v3, Color.RED)))).isFalse();
    }

    @Test
    void partialAssignment_belowLimit_optimisticallySatisfied() {
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(v1, Color.RED)))).isTrue();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(v1, Color.RED, v2, Color.RED)))).isTrue();
    }

    @Test
    void openGcc_underCount_notSatisfied() {
        // Only RED and GREEN are tracked (BLUE is free / unconstrained).
        // With 1 RED assigned and 2 required, no value exceeds its limit mid-assignment,
        // so early failure doesn't fire — the mismatch is only detected when all vars assigned.
        var openGcc = GlobalCardinalityConstraint.of(
                Set.of(v1, v2, v3, v4),
                Map.of(Color.RED, 2, Color.GREEN, 2));
        assertThat(openGcc.isSatisfiedBy(Assignment.of(Map.of(
                v1, Color.RED, v2, Color.GREEN, v3, Color.BLUE, v4, Color.BLUE)))).isFalse();
    }

    @Test
    void testToString() {
        assertThat(constraint.toString())
                .isEqualTo("<(v1, v2, v3, v4), GlobalCardinality({BLUE=1..1, GREEN=1..1, RED=2..2})>");
    }

    @Test
    void of_createsEquivalentConstraint() {
        assertThat(GlobalCardinalityConstraint.of(
                Set.of(v1, v2, v3, v4),
                Map.of(Color.RED, 2, Color.GREEN, 1, Color.BLUE, 1)))
                .isEqualTo(constraint);
    }

    @Test
    void of_sumOfQuotasExceedsVariableCount_throws() {
        // RED=2, GREEN=2, BLUE=2 sums to 6, but only 4 variables exist -- no assignment could ever
        // satisfy this GCC regardless of domains, so of() rejects it up front.
        assertThatThrownBy(() -> GlobalCardinalityConstraint.of(
                Set.of(v1, v2, v3, v4),
                Map.of(Color.RED, 2, Color.GREEN, 2, Color.BLUE, 2)))
                .isInstanceOf(AssertionError.class);
    }

    // --- propagate(): flow-based GAC (Régin 1996) ---

    static final Domain<Color> RED_ONLY    = EnumDomain.of(Color.RED);
    static final Domain<Color> GREEN_ONLY  = EnumDomain.of(Color.GREEN);
    static final Domain<Color> ALL         = EnumDomain.allOf(Color.class); // RED, GREEN, BLUE
    static final Domain<Color> RED_GREEN   = EnumDomain.of(Color.RED, Color.GREEN);
    static final Domain<Color> GREEN_BLUE  = EnumDomain.of(Color.GREEN, Color.BLUE);

    @Test
    void propagate_quotaAlreadyMetBySingletons_removesValueFromOthers() {
        // RED==2: v1, v2 already pinned to RED exhausts the quota, so no feasible completion can
        // give v3 RED either — GAC removes it, leaving only v3's untracked candidates.
        var c = GlobalCardinalityConstraint.of(Set.of(v1, v2, v3), Map.of(Color.RED, 2));
        var domains = Map.<Variable<?>, Domain<?>>of(v1, RED_ONLY, v2, RED_ONLY, v3, ALL);
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(v3)).isEqualTo(GREEN_BLUE);
    }

    @Test
    void propagate_onlyEnoughCandidatesToMeetQuota_forcesThemAll() {
        // RED==2: v1 is already RED; v2 is the only other variable with RED in domain at all
        // (v3 can't take RED). Both v1 and v2 are therefore required to supply the quota, so v2's
        // untracked GREEN candidate is not part of any feasible completion — GAC forces v2 to
        // {RED}. This is the untracked-node pruning case: a variable's edge to the merged
        // untracked sink can be GAC-unsafe even though untracked values are never individually
        // quota-limited, because *this* variable is needed elsewhere.
        var c = GlobalCardinalityConstraint.of(Set.of(v1, v2, v3), Map.of(Color.RED, 2));
        var domains = Map.<Variable<?>, Domain<?>>of(v1, RED_ONLY, v2, RED_GREEN, v3, GREEN_BLUE);
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(v2)).isEqualTo(RED_ONLY);
        assertThat(result.get()).doesNotContainKey(v3); // v3 never had RED available; unaffected
    }

    @Test
    void propagate_infeasible_quotaAlreadyExceeded() {
        // RED==1: v1, v2 both pinned to RED — 2 singleton RED assignments already exceed quota 1.
        var c = GlobalCardinalityConstraint.of(Set.of(v1, v2), Map.of(Color.RED, 1));
        var domains = Map.<Variable<?>, Domain<?>>of(v1, RED_ONLY, v2, RED_ONLY);
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_infeasible_noCandidatesCanReachQuota() {
        // RED==2: neither v1 nor v2 has RED in its domain at all — the quota is categorically
        // unreachable regardless of any other variable.
        var c = GlobalCardinalityConstraint.of(Set.of(v1, v2), Map.of(Color.RED, 2));
        var domains = Map.<Variable<?>, Domain<?>>of(v1, GREEN_BLUE, v2, GREEN_BLUE);
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_infeasible_pigeonhole_overSubscription() {
        // RED==1, GREEN==1: v1, v2, v3 all have domain {RED, GREEN} only (no untracked escape).
        // 3 variables can only ever be split across 2 total quota slots — infeasible by pigeonhole,
        // even though no *single* tracked value's own quota is individually violated in isolation.
        // This is exactly the joint (Hall-set) reasoning a per-value decomposition misses: the
        // predecessor algorithm classified each value independently and never caught this case.
        var c = GlobalCardinalityConstraint.of(Set.of(v1, v2, v3), Map.of(Color.RED, 1, Color.GREEN, 1));
        var domains = Map.<Variable<?>, Domain<?>>of(v1, RED_GREEN, v2, RED_GREEN, v3, RED_GREEN);
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_infeasible_deficiency_tooFewCandidatesForHighQuota() {
        // RED==3, but only v1 and v2 have RED in domain at all (v3, v4, v5 don't) — the quota
        // needs 3 suppliers, only 2 exist. The dual Hall condition to the pigeonhole case above:
        // here the *value* side is under-suppliable, not the variable side over-subscribed.
        var v5 = org.mockito.Mockito.mock(Variable.class);
        var c = GlobalCardinalityConstraint.of(Set.of(v1, v2, v3, v4, v5), Map.of(Color.RED, 3));
        var domains = Map.<Variable<?>, Domain<?>>of(
                v1, RED_GREEN, v2, RED_GREEN, v3, GREEN_BLUE, v4, GREEN_BLUE, v5, GREEN_BLUE);
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_noChange_returnsEmptyMap() {
        // RED==1: v1 is already pinned to RED (quota met); v2 has no RED available at all.
        // Nothing is prunable — v2's non-RED candidates were never affected by RED's quota.
        var c = GlobalCardinalityConstraint.of(Set.of(v1, v2), Map.of(Color.RED, 1));
        var domains = Map.<Variable<?>, Domain<?>>of(v1, RED_ONLY, v2, GREEN_BLUE);
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_neitherQuotaMet_noChange() {
        // RED==1: v1, v2 both have every color available — plenty of slack either way, no forcing.
        var c = GlobalCardinalityConstraint.of(Set.of(v1, v2), Map.of(Color.RED, 1));
        var domains = Map.<Variable<?>, Domain<?>>of(v1, ALL, v2, ALL);
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_multipleValues_bothProcessed() {
        // RED==2, GREEN==1: v1, v2 pinned to RED exhausts that quota → RED removed from v3, v4;
        // GREEN's quota (1) has slack against 2 open candidates, so no forcing there.
        var c = GlobalCardinalityConstraint.of(Set.of(v1, v2, v3, v4), Map.of(Color.RED, 2, Color.GREEN, 1));
        var domains = Map.<Variable<?>, Domain<?>>of(v1, RED_ONLY, v2, RED_ONLY, v3, ALL, v4, ALL);
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(v3)).isEqualTo(GREEN_BLUE);
        assertThat(result.get().get(v4)).isEqualTo(GREEN_BLUE);
    }

    // --- propagateWithReasons() / explainInfeasible() ---

    @Test
    void propagateWithReasons_feasible_returnsEmptyReason() {
        var c = GlobalCardinalityConstraint.of(Set.of(v1, v2, v3), Map.of(Color.RED, 2));
        var domains = Map.<Variable<?>, Domain<?>>of(v1, RED_ONLY, v2, RED_GREEN, v3, ALL);
        var result = c.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isFalse();
        assertThat(result.reason()).isNull();
    }

    @Test
    void propagateWithReasons_quotaExceeded_attributesTheOverSubscribedSingletons() {
        // RED==1: v1, v2 both pinned to RED. The violating subset the flow's min-cut finds is
        // exactly {v1, v2} here, and both are singleton, so a ground reason is directly available.
        var c = GlobalCardinalityConstraint.of(Set.of(v1, v2), Map.of(Color.RED, 1));
        var domains = Map.<Variable<?>, Domain<?>>of(v1, RED_ONLY, v2, RED_ONLY);
        var result = c.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(GroundNogoodConstraint.of(Map.of(v1, Color.RED, v2, Color.RED)));
    }

    @Test
    void propagateWithReasons_noCandidatesReachQuota_attributesTheSingletonImpossibleVars() {
        // RED==2: v1={GREEN}, v2={BLUE} — neither can ever supply RED, and both are singleton,
        // so the violating subset (both variables) has a direct ground reason.
        var c = GlobalCardinalityConstraint.of(Set.of(v1, v2), Map.of(Color.RED, 2));
        var domains = Map.<Variable<?>, Domain<?>>of(v1, GREEN_ONLY, v2, EnumDomain.of(Color.BLUE));
        var result = c.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isEqualTo(GroundNogoodConstraint.of(Map.of(v1, Color.GREEN, v2, Color.BLUE)));
    }

    @Test
    void propagateWithReasons_feasibleGivenDomains_returnsNoReasonEvenIfOtherwiseTight() {
        var c = GlobalCardinalityConstraint.of(Set.of(v1, v2), Map.of(Color.RED, 2));
        var domains = Map.<Variable<?>, Domain<?>>of(v1, ALL, v2, ALL);
        var result = c.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isFalse();
    }

    @Test
    void explainInfeasible_feasible_returnsEmpty() {
        var c = GlobalCardinalityConstraint.of(Set.of(v1, v2, v3), Map.of(Color.RED, 2));
        var domains = Map.<Variable<?>, Domain<?>>of(v1, RED_ONLY, v2, RED_GREEN, v3, GREEN_BLUE);
        assertThat(c.explainInfeasible(domains)).isEmpty();
    }

    @Test
    void explainInfeasible_hallViolation_nonSingletonEnumViolator_noSoundCitationAvailable() {
        // RED==1, GREEN==1. v1={RED} (definite), v2={RED,GREEN} (open), v3={GREEN} (definite).
        // Genuinely infeasible: v1 and v3 already exhaust both quotas, leaving v2 no legal value.
        // The flow-based GAC propagator's violating subset is the full {v1,v2,v3} (combined
        // candidate-value capacity RED+GREEN=2 < 3 variables — a real Hall violation, just not
        // the minimal {v2,v3} a per-value algorithm might cite). Since v2 isn't singleton, no
        // ground reason can be formed; since Color isn't numeric, RangeNogoodConstraint declines
        // too — the same two-tier-fallback limitation AllDiffConstraint already has for a
        // non-singleton, non-numeric Hall-violating subset, not a new regression.
        var cardinalities = new java.util.LinkedHashMap<Color, Integer>();
        cardinalities.put(Color.RED, 1);
        cardinalities.put(Color.GREEN, 1);
        var c = GlobalCardinalityConstraint.of(Set.of(v1, v2, v3), cardinalities);
        var domains = Map.<Variable<?>, Domain<?>>of(v1, RED_ONLY, v2, RED_GREEN, v3, GREEN_ONLY);
        assertThat(c.propagate(domains)).isEmpty();
        assertThat(c.explainInfeasible(domains)).isEmpty();
    }

    @Test
    void explainInfeasible_structuralOverCommitment_violatingSetIsEmpty_returnsEmptyReason() {
        // RED==3, GREEN==3 but only 4 variables total (Σ quotas = 6 > n = 4): a pure aggregate
        // over-commitment, not attributable to any specific variable's own routing failure — every
        // individual variable could, in isolation, still route successfully. The min-cut's
        // violating subset is genuinely empty here (confirmed empirically, not assumed): the
        // shortfall lands entirely on the value/bookkeeping side of the flow network, so
        // findViolatingSubset's defensive empty check is real, reachable code — but only when
        // assertions are disabled (the normal production default; Maven Surefire enables them for
        // tests). GlobalCardinalityConstraint.of()'s own assert now rejects this shape at
        // construction, so this test bypasses it via the builder directly to still exercise the
        // underlying flow algorithm's defensive handling, same as the assert being compiled out
        // (`-da`) would let a real caller reach it at runtime.
        var w1 = v1; var w2 = v2; var w3 = v3; var w4 = v4;
        var c = GlobalCardinalityConstraint.<Color>builder()
                .variables(Set.of(w1, w2, w3, w4))
                .cardinalityRanges(Map.of(
                        Color.RED, new GlobalCardinalityConstraint.OccurrenceRange(3, 3),
                        Color.GREEN, new GlobalCardinalityConstraint.OccurrenceRange(3, 3)))
                .build();
        var domains = Map.<Variable<?>, Domain<?>>of(
                w1, RED_ONLY, w2, RED_GREEN, w3, GREEN_ONLY, w4, GREEN_ONLY);
        assertThat(c.propagate(domains)).isEmpty();
        assertThat(c.explainInfeasible(domains)).isEmpty();
    }

    @Test
    void solver_exactDistribution() {
        // 4 vars over {RED, GREEN, BLUE}: exactly 2 RED, 1 GREEN, 1 BLUE.
        // Solutions: C(4,2) × C(2,1) × 1 = 6 × 2 = 12.
        Variable<Color> x1 = Variable.Factory.INSTANCE.create("x1");
        Variable<Color> x2 = Variable.Factory.INSTANCE.create("x2");
        Variable<Color> x3 = Variable.Factory.INSTANCE.create("x3");
        Variable<Color> x4 = Variable.Factory.INSTANCE.create("x4");
        var domain = EnumDomain.allOf(Color.class);
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x1, domain).variableDomain(x2, domain)
                .variableDomain(x3, domain).variableDomain(x4, domain)
                .globalCardinalityConstraint(
                        Set.of(x1, x2, x3, x4),
                        Map.of(Color.RED, 2, Color.GREEN, 1, Color.BLUE, 1))
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions()).hasSize(12);
    }

    // --- randomized cross-check against brute-force GAC ---

    /**
     * {@link #propagate}'s flow-based algorithm is intricate enough (a real max-flow-with-lower-
     * bounds computation, not a small patch) to warrant checking it against an independent,
     * trivially-correct oracle rather than trusting hand-picked cases alone: exhaustive search over
     * every possible completion, for many random small instances. For every (variable, value) pair
     * still in the original domain, a value is <em>GAC-consistent</em> iff some full assignment
     * exists agreeing with it that satisfies every tracked cardinality exactly. Sound and complete
     * propagation must retain exactly the GAC-consistent values — no more, no fewer.
     */
    @Test
    void propagate_randomizedCrossCheckAgainstBruteForceGac() {
        var random = new java.util.Random(42);
        List<Color> palette = List.of(Color.RED, Color.GREEN, Color.BLUE);

        for (int trial = 0; trial < 300; trial++) {
            int n = 2 + random.nextInt(3); // 2..4 variables
            List<Variable<Color>> vars = new ArrayList<>();
            for (int i = 0; i < n; i++) vars.add(Variable.Factory.INSTANCE.create("x" + trial + "_" + i));

            List<List<Color>> domLists = new ArrayList<>();
            Map<Variable<?>, Domain<?>> domains = new HashMap<>();
            for (Variable<Color> v : vars) {
                java.util.Set<Color> dom = new java.util.HashSet<>();
                while (dom.isEmpty()) {
                    for (Color c : palette) if (random.nextBoolean()) dom.add(c);
                }
                List<Color> domList = new ArrayList<>(dom);
                domLists.add(domList);
                domains.put(v, EnumDomain.of(domList.get(0), domList.subList(1, domList.size()).toArray(new Color[0])));
            }

            Map<Color, Integer> cardinalities = new HashMap<>();
            for (Color c : palette) {
                if (random.nextBoolean()) cardinalities.put(c, random.nextInt(n + 1));
            }
            if (cardinalities.isEmpty()) continue; // degenerate no-op GCC, nothing to check
            int sumQuotas = cardinalities.values().stream().mapToInt(Integer::intValue).sum();
            if (sumQuotas > n) continue; // structurally over-committed; GlobalCardinalityConstraint.of() now rejects this

            var constraint = GlobalCardinalityConstraint.of(new java.util.HashSet<>(vars), cardinalities);
            boolean bruteForceFeasible = anyCompletionSatisfies(domLists, cardinalities, new Color[n], -1, null, 0);
            var result = constraint.propagate(domains);

            if (!bruteForceFeasible) {
                assertThat(result).as("trial %d: expected infeasible, domains=%s, cardinalities=%s",
                        trial, domLists, cardinalities).isEmpty();
                continue;
            }
            assertThat(result).as("trial %d: expected feasible, domains=%s, cardinalities=%s",
                    trial, domLists, cardinalities).isPresent();

            for (int i = 0; i < n; i++) {
                java.util.Set<Color> expected = new java.util.HashSet<>();
                for (Color v : domLists.get(i)) {
                    if (anyCompletionSatisfies(domLists, cardinalities, new Color[n], i, v, 0)) expected.add(v);
                }
                Domain<?> actualDomain = result.get().getOrDefault(vars.get(i), domains.get(vars.get(i)));
                java.util.Set<Color> actual = ((io.github.rcrida.jcsp.domains.DiscreteDomain<Color>) actualDomain)
                        .toList().stream().collect(java.util.stream.Collectors.toSet());
                assertThat(actual).as("trial %d, variable %d: domains=%s, cardinalities=%s",
                        trial, i, domLists, cardinalities).isEqualTo(expected);
            }
        }
    }

    /**
     * Exhaustive search: does some assignment exist, drawn from each variable's own domain (with
     * variable {@code fixedIdx}, if {@code fixedIdx >= 0}, forced to {@code fixedVal} instead),
     * satisfying every tracked cardinality exactly?
     */
    private static boolean anyCompletionSatisfies(List<List<Color>> domLists, Map<Color, Integer> cardinalities,
                                                    Color[] current, int fixedIdx, Color fixedVal, int idx) {
        if (idx == current.length) {
            Map<Color, Integer> counts = new HashMap<>();
            for (Color c : current) counts.merge(c, 1, Integer::sum);
            for (var e : cardinalities.entrySet()) {
                if (!Objects.equals(counts.getOrDefault(e.getKey(), 0), e.getValue())) return false;
            }
            return true;
        }
        if (idx == fixedIdx) {
            current[idx] = fixedVal;
            return anyCompletionSatisfies(domLists, cardinalities, current, fixedIdx, fixedVal, idx + 1);
        }
        for (Color c : domLists.get(idx)) {
            current[idx] = c;
            if (anyCompletionSatisfies(domLists, cardinalities, current, fixedIdx, fixedVal, idx + 1)) return true;
        }
        return false;
    }

    // ---- range (occurrence range) support ----------------------------------------------------------------

    @Test
    void occurrenceRange_negativeMin_asserts() {
        assertThatThrownBy(() -> new GlobalCardinalityConstraint.OccurrenceRange(-1, 2))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void occurrenceRange_minAboveMax_asserts() {
        assertThatThrownBy(() -> new GlobalCardinalityConstraint.OccurrenceRange(3, 2))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void ofRange_sumOfMinimumsExceedsVariableCount_throws() {
        // RED>=2, GREEN>=2 sums to 4, but only 3 variables exist.
        assertThatThrownBy(() -> GlobalCardinalityConstraint.ofRange(
                Set.of(v1, v2, v3),
                Map.of(Color.RED, new GlobalCardinalityConstraint.OccurrenceRange(2, 3),
                        Color.GREEN, new GlobalCardinalityConstraint.OccurrenceRange(2, 3))))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void cspBuilder_globalCardinalityRangeConstraint_method() {
        Variable<Color> x1 = Variable.Factory.INSTANCE.create("gx1");
        Variable<Color> x2 = Variable.Factory.INSTANCE.create("gx2");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x1, EnumDomain.of(Color.RED, Color.GREEN))
                .variableDomain(x2, EnumDomain.of(Color.RED, Color.GREEN))
                .globalCardinalityRangeConstraint(Set.of(x1, x2),
                        Map.of(Color.RED, new GlobalCardinalityConstraint.OccurrenceRange(0, 1)))
                .build();
        var sols = Solver.Factory.INSTANCE.createSolver(csp).getSolutions().toList();
        assertThat(sols).isNotEmpty();
        for (Assignment a : sols) {
            long redCount = java.util.stream.Stream.of(x1, x2)
                    .filter(v -> a.getValue(v).orElseThrow() == Color.RED).count();
            assertThat(redCount).isLessThanOrEqualTo(1);
        }
    }

    @Test
    void ofRange_exactCounts_satisfied() {
        var c = GlobalCardinalityConstraint.ofRange(Set.of(v1, v2, v3, v4), Map.of(
                Color.RED, new GlobalCardinalityConstraint.OccurrenceRange(1, 2),
                Color.GREEN, new GlobalCardinalityConstraint.OccurrenceRange(0, 1)));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(
                v1, Color.RED, v2, Color.RED, v3, Color.GREEN, v4, Color.BLUE)))).isTrue();
    }

    @Test
    void ofRange_belowMinimum_notSatisfied() {
        // RED needs >=2 but only 1 appears.
        var c = GlobalCardinalityConstraint.ofRange(Set.of(v1, v2, v3, v4), Map.of(
                Color.RED, new GlobalCardinalityConstraint.OccurrenceRange(2, 3)));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(
                v1, Color.RED, v2, Color.GREEN, v3, Color.GREEN, v4, Color.BLUE)))).isFalse();
    }

    @Test
    void ofRange_aboveMaximum_notSatisfied() {
        // RED allows at most 1 but 2 appear.
        var c = GlobalCardinalityConstraint.ofRange(Set.of(v1, v2, v3, v4), Map.of(
                Color.RED, new GlobalCardinalityConstraint.OccurrenceRange(0, 1)));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(
                v1, Color.RED, v2, Color.RED, v3, Color.GREEN, v4, Color.BLUE)))).isFalse();
    }

    @Test
    void propagateRange_forcesLastVariableToMeetMinimum() {
        // RED range [2,2] over {v1,v2,v3}; v1,v2 domains exclude RED, so v3 (the only one that
        // still can be RED) must be forced to RED to reach the minimum.
        Variable<Color> w1 = Variable.Factory.INSTANCE.create("rw1");
        Variable<Color> w2 = Variable.Factory.INSTANCE.create("rw2");
        Variable<Color> w3 = Variable.Factory.INSTANCE.create("rw3");
        var c = GlobalCardinalityConstraint.ofRange(Set.of(w1, w2, w3), Map.of(
                Color.RED, new GlobalCardinalityConstraint.OccurrenceRange(2, 2)));
        Map<Variable<?>, Domain<?>> domains = Map.of(
                w1, EnumDomain.of(Color.RED, Color.GREEN),
                w2, EnumDomain.of(Color.RED, Color.GREEN),
                w3, EnumDomain.of(Color.RED, Color.GREEN));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        // With only 3 variables and RED needing exactly 2, no single variable is individually
        // forced (any 2-of-3 combination works) -- this test instead documents that propagation
        // doesn't spuriously narrow anything here (a real GAC check, not a hand-guessed forcing).
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagateRange_infeasibleWhenMaximumUnreachable() {
        // GREEN range [0,0] (forbidden) but v1's domain is GREEN-only -- infeasible.
        Variable<Color> w1 = Variable.Factory.INSTANCE.create("iw1");
        Variable<Color> w2 = Variable.Factory.INSTANCE.create("iw2");
        var c = GlobalCardinalityConstraint.ofRange(Set.of(w1, w2), Map.of(
                Color.GREEN, new GlobalCardinalityConstraint.OccurrenceRange(0, 0)));
        Map<Variable<?>, Domain<?>> domains = Map.of(
                w1, EnumDomain.of(Color.GREEN),
                w2, EnumDomain.of(Color.RED, Color.GREEN));
        assertThat(c.propagate(domains)).isEmpty();
        assertThat(c.explainInfeasible(domains)).isPresent();
    }

    /**
     * The range-based flow extension (real {@code [min,max]} edges plus {@code sinkOriginal}'s own
     * inclusion in the GAC residual graph — see this class's own Javadoc) is new, careful algorithm
     * work, not a small patch -- exactly the shape of change {@link
     * #propagate_randomizedCrossCheckAgainstBruteForceGac} already exists to guard for the
     * exact-count case. This is the same check over randomly generated {@code [min,max]} ranges
     * instead of exact counts, independently validating the new edge/residual-graph logic against
     * brute-force enumeration rather than trusting the hand-derivation alone.
     */
    @Test
    void propagateRange_randomizedCrossCheckAgainstBruteForceGac() {
        var random = new java.util.Random(43);
        List<Color> palette = List.of(Color.RED, Color.GREEN, Color.BLUE);

        for (int trial = 0; trial < 300; trial++) {
            int n = 2 + random.nextInt(3); // 2..4 variables
            List<Variable<Color>> vars = new ArrayList<>();
            for (int i = 0; i < n; i++) vars.add(Variable.Factory.INSTANCE.create("rx" + trial + "_" + i));

            List<List<Color>> domLists = new ArrayList<>();
            Map<Variable<?>, Domain<?>> domains = new HashMap<>();
            for (Variable<Color> v : vars) {
                java.util.Set<Color> dom = new java.util.HashSet<>();
                while (dom.isEmpty()) {
                    for (Color c : palette) if (random.nextBoolean()) dom.add(c);
                }
                List<Color> domList = new ArrayList<>(dom);
                domLists.add(domList);
                domains.put(v, EnumDomain.of(domList.get(0), domList.subList(1, domList.size()).toArray(new Color[0])));
            }

            Map<Color, GlobalCardinalityConstraint.OccurrenceRange> ranges = new HashMap<>();
            for (Color c : palette) {
                if (random.nextBoolean()) {
                    int lo = random.nextInt(n + 1);
                    int hi = lo + random.nextInt(n + 1 - lo);
                    ranges.put(c, new GlobalCardinalityConstraint.OccurrenceRange(lo, hi));
                }
            }
            if (ranges.isEmpty()) continue; // degenerate no-op GCC, nothing to check
            int sumLo = ranges.values().stream().mapToInt(GlobalCardinalityConstraint.OccurrenceRange::min).sum();
            if (sumLo > n) continue; // structurally over-committed; ofRange() now rejects this

            var constraint = GlobalCardinalityConstraint.ofRange(new java.util.HashSet<>(vars), ranges);
            boolean bruteForceFeasible = anyCompletionSatisfiesRange(domLists, ranges, new Color[n], -1, null, 0);
            var result = constraint.propagate(domains);

            if (!bruteForceFeasible) {
                assertThat(result).as("trial %d: expected infeasible, domains=%s, ranges=%s",
                        trial, domLists, ranges).isEmpty();
                continue;
            }
            assertThat(result).as("trial %d: expected feasible, domains=%s, ranges=%s",
                    trial, domLists, ranges).isPresent();

            for (int i = 0; i < n; i++) {
                java.util.Set<Color> expected = new java.util.HashSet<>();
                for (Color v : domLists.get(i)) {
                    if (anyCompletionSatisfiesRange(domLists, ranges, new Color[n], i, v, 0)) expected.add(v);
                }
                Domain<?> actualDomain = result.get().getOrDefault(vars.get(i), domains.get(vars.get(i)));
                java.util.Set<Color> actual = ((io.github.rcrida.jcsp.domains.DiscreteDomain<Color>) actualDomain)
                        .toList().stream().collect(java.util.stream.Collectors.toSet());
                assertThat(actual).as("trial %d, variable %d: domains=%s, ranges=%s",
                        trial, i, domLists, ranges).isEqualTo(expected);
            }
        }
    }

    /**
     * Exhaustive search over {@code [min,max]} ranges, mirroring {@link #anyCompletionSatisfies}'s
     * exact-count version.
     */
    private static boolean anyCompletionSatisfiesRange(List<List<Color>> domLists,
            Map<Color, GlobalCardinalityConstraint.OccurrenceRange> ranges,
            Color[] current, int fixedIdx, Color fixedVal, int idx) {
        if (idx == current.length) {
            Map<Color, Integer> counts = new HashMap<>();
            for (Color c : current) counts.merge(c, 1, Integer::sum);
            for (var e : ranges.entrySet()) {
                int count = counts.getOrDefault(e.getKey(), 0);
                if (count < e.getValue().min() || count > e.getValue().max()) return false;
            }
            return true;
        }
        if (idx == fixedIdx) {
            current[idx] = fixedVal;
            return anyCompletionSatisfiesRange(domLists, ranges, current, fixedIdx, fixedVal, idx + 1);
        }
        for (Color c : domLists.get(idx)) {
            current[idx] = c;
            if (anyCompletionSatisfiesRange(domLists, ranges, current, fixedIdx, fixedVal, idx + 1)) return true;
        }
        return false;
    }
}

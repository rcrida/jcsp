package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.nary.LinearBoundConstraint;
import io.github.rcrida.jcsp.domains.BoundedDomain;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A bound on a {@link LinearObjective}, as a real constraint that can be propagated into a problem's
 * domains: "no solution costing {@code bound} or more".
 *
 * <p>Expressing a bound as a <em>constraint</em> rather than only as a predicate to test nodes
 * against is the whole point. A predicate rejects one node; narrowing a domain is information every
 * other propagator then compounds with, through the ordinary fixpoint. Without it a bound is
 * invisible to {@link io.github.rcrida.jcsp.constraints.nary.AllDiffConstraint}, {@link
 * io.github.rcrida.jcsp.constraints.nary.GlobalCardinalityConstraint} and every other propagator, no
 * matter how good the bound gets. On a job shop, bounding the makespan tightens every operation's
 * latest start through the precedence chain, which is why asking a <em>tighter</em> question can be
 * easier than asking a loose one.
 *
 * <p>Shared by the two callers that need it, which apply the same cut two different ways: {@link
 * BranchAndBoundSolver} re-{@link #narrow}s its incumbent into the domains at every node
 * ([ADR-0029]), while {@link BoundedFirstSolution} {@link #enforce}s a probe's bound as a constraint
 * once per probe ([ADR-0044]). See {@link #enforce} for why one-per-node and one-per-search want
 * different mechanisms. An instance carries a single-slot cache, since the bound changes rarely
 * relative to the rate it is consulted at, and rebuilding the constraint copies a variable set.
 */
public final class ObjectiveCut {
    /**
     * The constraint built for one objective and bound, {@code null} when that pair has no exact
     * cut. Both halves of the key are kept: a caller is free to hold one instance and ask it about
     * more than one objective, and answering from a cache keyed on the bound alone would hand it the
     * first objective's cut -- a wrong cut discards real solutions rather than failing, which is the
     * outcome {@link #build} declines a non-integral objective to avoid.
     */
    private record Cached(@NonNull LinearObjective objective, double strictlyBetterThan,
                          @Nullable LinearBoundConstraint<Integer> constraint) {}

    private final AtomicReference<Cached> cache = new AtomicReference<>();

    /**
     * The cut requiring a cost strictly better than {@code strictlyBetterThan}, or {@code null} when
     * this objective cannot be cut exactly. Cached against the objective and bound it was built for;
     * a {@code null} result is cached too, so an objective that can never be cut is diagnosed once
     * rather than re-examined on every call.
     *
     * <p>{@code csp} is deliberately not part of the cache key, although {@link #build} reads it: a
     * caller asks once per search <em>node</em>, each with its own narrowed domains, so keying on it
     * would mean never hitting the cache at all. Safe because the only thing read of it is whether
     * each objective variable's domain is discrete with integral values, and narrowing can only
     * remove values from a domain -- it can neither introduce a {@link BoundedDomain} nor make an
     * integral domain fractional. So an answer computed against a parent node's wider domains still
     * holds for every descendant.
     */
    public @Nullable LinearBoundConstraint<Integer> constraintFor(@NonNull LinearObjective objective,
                                                                  double strictlyBetterThan,
                                                                  @NonNull ConstraintSatisfactionProblem csp) {
        Cached cached = cache.get();
        // Double.compare, not ==, so a NaN bound (an objective that returned one) hits the cache
        // instead of rebuilding a declined cut at every node.
        if (cached != null && cached.objective().equals(objective)
                && Double.compare(cached.strictlyBetterThan(), strictlyBetterThan) == 0) {
            return cached.constraint();
        }
        LinearBoundConstraint<Integer> built = build(objective, strictlyBetterThan, csp);
        cache.set(new Cached(objective, strictlyBetterThan, built));
        return built;
    }

    /**
     * {@link #narrow}'s result: the bounded problem, plus the variables whose domains the bound
     * actually narrowed -- empty when it narrowed nothing, or when there was no cut to apply.
     *
     * <p>The caller needs the second half because the narrowing happens outside any propagation
     * pass, so it appears in no diff the next one can take for itself: these are the variables it
     * must declare to {@link io.github.rcrida.jcsp.consistency.Inference#apply(ConstraintSatisfactionProblem,
     * Variable, io.github.rcrida.jcsp.assignments.Assignment, Set)} as already changed, so the
     * propagators touching them are woken. That is where the compounding this whole class exists for
     * actually happens.
     */
    public record Narrowed(@NonNull ConstraintSatisfactionProblem csp, @NonNull Set<Variable<?>> variables) {}

    /**
     * {@code csp} with its domains narrowed so that no remaining assignment costs
     * {@code strictlyBetterThan} or more: {@code null} when that already empties a domain (nothing
     * better exists, so a caller may prune outright), and {@code csp} itself when there is no
     * expressible cut or the cut changed nothing. A caller that must distinguish "no cut possible"
     * from "cut changed nothing" asks {@link #constraintFor} first.
     *
     * <p>Sound because it removes only assignments at or above the given cost. An infinite bound is
     * treated as no bound at all, which is what an as-yet-unknown incumbent looks like.
     */
    public @Nullable Narrowed narrow(@NonNull ConstraintSatisfactionProblem csp,
                                     @NonNull LinearObjective objective,
                                     double strictlyBetterThan) {
        if (strictlyBetterThan == Double.MAX_VALUE) {
            return new Narrowed(csp, Set.of());
        }
        LinearBoundConstraint<Integer> constraint = constraintFor(objective, strictlyBetterThan, csp);
        if (constraint == null) {
            return new Narrowed(csp, Set.of());
        }
        Optional<Map<Variable<?>, Domain<?>>> narrowed = constraint.propagate(csp.getVariableDomains());
        if (narrowed.isEmpty()) {
            return null;
        }
        // propagate returns only the domains it changed, so its key set is the narrowed set --
        // wrapped rather than copied, since this is on the per-node path.
        return narrowed.get().isEmpty()
                ? new Narrowed(csp, Set.of())
                : new Narrowed(csp.withDomains(narrowed.get()), Collections.unmodifiableSet(narrowed.get().keySet()));
    }

    /**
     * {@code csp} with this bound added as a propagated <em>constraint</em>, so every later fixpoint
     * re-derives it; {@code csp} itself when the bound is infinite or has no expressible cut.
     *
     * <p>For the caller that applies a bound once per search rather than once per node, where {@link
     * #narrow} is not enough. {@link #narrow} is a single propagation pass, so it leaves behind only
     * what the bound narrowed <em>at that moment</em>; a bound spread thinly across many variables
     * narrows nothing at all, since twenty domains of width thirty under a sum bound of 286 each
     * still reach 267. The bound is then invisible from the first node onwards, and the "bounded"
     * search re-asks the unbounded question. As a constraint it is re-propagated at every node
     * against the domains that node has narrowed, which is where a thin bound eventually bites.
     *
     * <p>The constraint set changes, so the {@link io.github.rcrida.jcsp.ConstraintGraph} is rebuilt
     * and a caller must hand the result to a {@link FixpointPropagation} built for <em>it</em> rather
     * than for {@code csp} -- {@link FixpointPropagation.Factory#forProblem} filters on the constraint
     * types present, so a {@link LinearBoundConstraint} the original problem didn't have would
     * otherwise never be propagated at all. {@link BranchAndBoundSolver} uses {@link #narrow} for
     * exactly these two reasons: it applies its bound at every node, where rebuilding the graph and
     * the propagator list per node would cost far more than the thin-bound passes it gives up.
     */
    public @NonNull ConstraintSatisfactionProblem enforce(@NonNull ConstraintSatisfactionProblem csp,
                                                          @NonNull LinearObjective objective,
                                                          double strictlyBetterThan) {
        if (strictlyBetterThan == Double.MAX_VALUE) {
            return csp;
        }
        LinearBoundConstraint<Integer> constraint = constraintFor(objective, strictlyBetterThan, csp);
        return constraint == null ? csp : csp.toBuilder().constraint(constraint).build();
    }

    /**
     * The cut as a {@link LinearBoundConstraint}, or {@code null} when this objective can't be
     * expressed as one exactly. The bound is {@code strictlyBetterThan - constant - 1}: one better
     * than the given cost, which for a wholly integral objective is exactly representable rather than
     * an epsilon away. A non-integral objective is left alone rather than approximated, because the
     * cut would have to be loosened by an epsilon to stay sound and a wrong one here silently
     * discards the true optimum instead of failing.
     *
     * <p>Every objective variable's <em>domain</em> has to be integral too, which {@link
     * #isIntegerValued} decides. That subsumes the {@link BoundedDomain} case -- {@code -1} is not
     * the next representable improvement over a continuous cost, and an {@code Integer}-bounded
     * {@link LinearBoundConstraint} dispatches to integer propagation, which cannot read a continuous
     * domain at all; the MIPLIB {@code flugpl} instance (see {@code FlugplTest}) is exactly this mixed
     * integer/continuous shape -- but it also covers a <em>discrete</em> domain of fractional values,
     * which is just as wrong and is not continuous: {@link
     * io.github.rcrida.jcsp.constraints.nary.LinearBoundPropagation} reads such a domain through
     * {@link Number#intValue}, so a value of {@code 1.9} contributes {@code 1} both to the bound it is
     * filtered against and to {@link LinearBoundConstraint#isSatisfiedBy}'s own sum. An assignment
     * costing more than the bound then passes for one costing less.
     */
    @SuppressWarnings("unchecked")
    private static @Nullable LinearBoundConstraint<Integer> build(LinearObjective objective, double strictlyBetterThan,
                                                                  ConstraintSatisfactionProblem csp) {
        double bound = strictlyBetterThan - objective.getConstant() - 1;
        if (!isExactInt(bound) || !isExpressible(objective, csp)) {
            return null;
        }
        Map<Variable<Integer>, Integer> coefficients = new HashMap<>();
        for (var entry : objective.getCoefficients().entrySet()) {
            coefficients.put((Variable<Integer>) entry.getKey(), entry.getValue().intValue());
        }
        return LinearBoundConstraint.of(coefficients, Operator.LEQ, (int) bound);
    }

    /**
     * Whether {@code objective} on {@code csp} can be cut at all -- the half of {@link #build}'s
     * verdict that depends only on the coefficients and the domains they are keyed on, and so is the
     * same for every bound. {@code false} means no bound will ever be expressible here, however the
     * incumbent moves.
     *
     * <p>For a caller that relies on the cut to carry its bound and needs to know, once per solve,
     * whether it may stop computing a bound of its own: {@link BranchAndBoundSolver} asks before
     * allowing {@link LpGate} to back off, since with no cut to fall back on an LP bound is the only
     * pruning the node has. Narrowing can only make this more true (see {@link #constraintFor}), so
     * a {@code false} at the root stays a safe answer deeper down.
     */
    public static boolean isExpressible(@NonNull LinearObjective objective,
                                        @NonNull ConstraintSatisfactionProblem csp) {
        for (var entry : objective.getCoefficients().entrySet()) {
            if (!isExactInt(entry.getValue()) || !isIntegerValued(csp.getDomain(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether every value {@code domain} holds survives {@link Number#intValue} unchanged, so that
     * the integer propagation an {@code Integer}-bounded {@link LinearBoundConstraint} dispatches to
     * reads the domain exactly rather than a truncation of it. False for a {@link BoundedDomain},
     * which is not enumerable at all.
     *
     * <p>Enumerates the domain rather than probing its element type, since an integral-valued {@code
     * Double} domain is perfectly cuttable and a type probe would decline it. The cost is one pass
     * per objective variable per <em>distinct bound</em> -- the result is cached with the cut it
     * decided (see {@link #constraintFor}), so it is paid when the incumbent moves, not per node.
     */
    private static boolean isIntegerValued(Domain<?> domain) {
        // Every value is a Number by construction: LinearObjective's coefficients are keyed on
        // Variable<? extends Number>, and a variable's domain holds its own type's values.
        return domain instanceof DiscreteDomain<?> discrete
                && discrete.stream().allMatch(value -> isExactInt(((Number) value).doubleValue()));
    }

    /**
     * Whether {@code value} is a whole number that survives a cast to {@code int} unchanged. The
     * magnitude test is not redundant with the first: it rejects an infinity (which {@link Math#rint}
     * reports as already whole) and a large finite value (which the cast would silently wrap), either
     * of which would otherwise produce a wrong cut rather than no cut.
     */
    private static boolean isExactInt(double value) {
        return value == Math.rint(value) && Math.abs(value) <= Integer.MAX_VALUE;
    }
}

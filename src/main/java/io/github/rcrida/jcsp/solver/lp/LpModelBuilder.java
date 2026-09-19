package io.github.rcrida.jcsp.solver.lp;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.NumericBounds;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.nary.GlobalCardinalityConstraint;
import io.github.rcrida.jcsp.constraints.nary.LinearBoundConstraint;
import io.github.rcrida.jcsp.constraints.nary.LinearVariableConstraint;
import io.github.rcrida.jcsp.constraints.nary.NaryStarredTuplesConstraint;
import io.github.rcrida.jcsp.constraints.nary.NaryTuplesConstraint;
import io.github.rcrida.jcsp.constraints.nary.SumBoundConstraint;
import io.github.rcrida.jcsp.constraints.nary.SumVariableConstraint;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.solver.LinearObjective;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.ojalgo.optimisation.Expression;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Optimisation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Builds and solves a joint LP relaxation over a {@link ConstraintSatisfactionProblem}'s linear
 * structure and a caller-supplied {@link LinearObjective} (see ADR-0009). Only {@link
 * SumBoundConstraint}, {@link SumVariableConstraint}, {@link LinearBoundConstraint}, and {@link
 * LinearVariableConstraint} are translated into rows; every other constraint is invisible to the
 * relaxation. That's sound rather than merely approximate: dropping a constraint only enlarges a
 * minimization's feasible region, so {@link #solve}'s bound stays a valid lower bound on the full
 * problem's true optimum regardless of how much of the problem isn't linear -- it's just looser the
 * less linear the problem is. A variable's domain bounds become an ojAlgo box constraint whether the
 * variable is continuous ({@link io.github.rcrida.jcsp.domains.BoundedDomain}) or discrete ({@link
 * io.github.rcrida.jcsp.domains.IntRangeDomain} and friends) -- for a discrete variable, dropping
 * its integrality requirement down to a real-valued range is exactly the "relaxation" in "LP
 * relaxation".
 * <p>
 * Like {@link SumBoundConstraint}/{@link LinearBoundConstraint}'s own propagation, only {@link
 * Operator#EQ}, {@link Operator#LEQ}, and {@link Operator#GEQ} translate into a row; a linear
 * constraint using any other operator is skipped the same way those propagators already treat it as
 * a no-op.
 * <p>
 * A second, narrower pass ({@link #addAssignmentRelaxationRows}) adds a transportation/assignment-style
 * relaxation for the specific shape where a {@link GlobalCardinalityConstraint}'s own variables are
 * each looked up, via a small {@link NaryTuplesConstraint}/{@link NaryStarredTuplesConstraint} table,
 * into an objective variable -- e.g. "each item picks one option from a shared pool (the GCC), and its
 * option determines its contribution to the objective (the table)". A per-variable box bound alone is
 * blind to this: since the table constraint is already fully GAC-propagated before the LP is built,
 * an objective variable's own domain bounds are already as tight as that single table can make them in
 * isolation, so a naive per-table convex-hull row would add nothing a plain box bound doesn't already
 * capture (confirmed empirically against {@code PrizeCollecting-15-3-5-0.xml.lzma}, the motivating
 * instance -- see the note in this class's own history). What a box bound can't see is the *joint*
 * effect of the GCC across every item at once (at most one item may claim each option) -- capturing
 * that needs the GCC's own variables represented by one-hot indicators over their live domain values,
 * shared between the GCC's cardinality rows and the table's lookup rows, turning the pair into a
 * genuine assignment LP (Birkhoff-von Neumann: integral extreme points, not merely a valid relaxation).
 * Applies only when the table's looked-up variable is itself an objective coefficient (so the
 * indicators are guaranteed relevant) and the table is an exact bijection between the GCC variable's
 * current live domain and looked-up values (no gap, no ambiguity, no wildcard on the GCC variable's own
 * column) -- see {@link #functionalLookup}. See ADR-0009 and ADR-0020.
 */
@Slf4j
public final class LpModelBuilder {
    /**
     * Caps how many live tuples a table constraint may have to be considered for {@link
     * #addAssignmentRelaxationRows} -- a per-node, per-table bound on how many one-hot indicator
     * variables/rows the relaxation adds, so a large table (e.g. the wide multi-column tables XCSP3's
     * {@code extension} construct can produce) can't blow up LP model size even if it happened to pass
     * every other applicability check.
     */
    private static final int MAX_TABLE_TUPLES_FOR_ASSIGNMENT_RELAXATION = 64;

    private LpModelBuilder() {}

    /**
     * Solves the LP relaxation of {@code csp} against {@code objective}, returning {@link
     * Optional#empty()} if the relaxation itself is infeasible (in which case {@code csp} is
     * certainly infeasible too, since the relaxation only ever enlarges the feasible region).
     */
    public static Optional<LpBound> solve(@NonNull ConstraintSatisfactionProblem csp, @NonNull LinearObjective objective) {
        return solve(csp, objective, null);
    }

    /**
     * As {@link #solve(ConstraintSatisfactionProblem, LinearObjective)}, reusing one {@link
     * ExpressionsBasedModel} across calls that share {@code cacheKey} instead of rebuilding it from
     * scratch every time.
     * <p>
     * Worth doing because {@link io.github.rcrida.jcsp.solver.BranchAndBoundSolver} calls this at <em>every</em> search node, and
     * almost none of the model changes between them: {@link #addRow} reads only a constraint's
     * coefficients, operator and bound, all structural, and {@link #relevantVariables} collects from
     * the same four linear constraint types -- so the variables and rows are fixed for a given
     * constraint graph. Only the variables' own bounds move as domains narrow, and
     * {@link #boundsOf} is re-read and re-applied on every call. JFR profiling of {@code
     * LowAutocorrelation-015} put 21% of the entire solve inside {@link #build}, against 53% actually
     * solving.
     * <p>
     * {@code cacheKey} is expected to be an identity token owned by one solver instance (see {@code
     * BranchAndBoundSolver}'s own field), and the entry is held in {@code csp}'s per-{@code
     * ConstraintGraph} auxiliary cache. That pair is what makes reuse safe: keying on the graph means
     * learned nogoods -- which change {@link ConstraintSatisfactionProblem#getConstraints()}'s
     * reference on almost every node but contribute no rows, being non-linear -- never invalidate it,
     * while keying additionally on a per-solver token stops two solves of the same problem sharing one
     * mutable model. {@code null} disables reuse entirely, which is what the two-argument overload
     * above passes.
     * <p>
     * Reuse is skipped for a problem with assignment-relaxation rows (see {@link
     * #addAssignmentRelaxationRows} and ADR-0020): those add fresh ojAlgo <em>variables</em> derived
     * from each node's live domains, so they cannot be carried between nodes, and re-adding them to a
     * retained model would accumulate. Such a problem falls back to a full rebuild per node, exactly
     * as before.
     */
    public static Optional<LpBound> solve(@NonNull ConstraintSatisfactionProblem csp,
                                           @NonNull LinearObjective objective,
                                           @Nullable Object cacheKey) {
        List<Variable<?>> variables = List.copyOf(relevantVariables(csp, objective));
        if (variables.isEmpty()) {
            return Optional.of(new LpBound(objective.getConstant(), Map.of()));
        }

        ReusableModel reusable = cacheKey == null ? null : reusableModel(csp, objective, variables, cacheKey);
        Map<Variable<?>, org.ojalgo.optimisation.Variable> ojVariables;
        ExpressionsBasedModel model;
        if (reusable == null) {
            ojVariables = new LinkedHashMap<>();
            model = build(csp, objective, variables, ojVariables);
        } else {
            // A copy per node, not the template itself: ojAlgo retains presolve state on a model it
            // has solved, so mutating bounds and re-solving the same instance returns a valid but
            // far weaker bound (measured: Knapsack-30-100-00 went from 605 nodes to 288,022).
            model = reusable.model().copy();
            ojVariables = new LinkedHashMap<>();
            // The cached list, not the freshly computed one: it is what the retained model's variable
            // order was built from, and the two agree by the invariant documented on reusableModel.
            List<Variable<?>> cachedVariables = reusable.variables();
            for (int i = 0; i < cachedVariables.size(); i++) {
                Variable<?> variable = cachedVariables.get(i);
                double[] bounds = boundsOf(csp, variable);
                var ojVariable = model.getVariable(i).lower(bounds[0]).upper(bounds[1]);
                ojVariables.put(variable, ojVariable);
            }
        }

        Optimisation.Result result = model.minimise();
        if (!result.getState().isFeasible()) {
            log.debug("LP relaxation infeasible: {}", result.getState());
            return Optional.empty();
        }

        Map<Variable<?>, Double> solution = new LinkedHashMap<>();
        for (Variable<?> variable : variables) {
            solution.put(variable, ojVariables.get(variable).getValue().doubleValue());
        }
        return Optional.of(new LpBound(result.getValue() + objective.getConstant(), solution));
    }

    /** A structural model retained across nodes; see {@link #solve(ConstraintSatisfactionProblem, LinearObjective, Object)}. */
    private record ReusableModel(LinearObjective objective, List<Variable<?>> variables,
                                  Map<Variable<?>, org.ojalgo.optimisation.Variable> ojVariables,
                                  ExpressionsBasedModel model) {
    }

    /**
     * The retained model for {@code cacheKey}, or {@code null} when this problem cannot reuse one.
     * A cached entry is reused only when it was built for the same {@code objective}, so a caller
     * that changes objective mid-solve is rebuilt for rather than silently served a stale model.
     * Matching on the objective alone is sufficient to keep {@link ReusableModel#variables} aligned
     * with the retained model's own variable order, which the index-based lookup below depends on:
     * {@link #relevantVariables} is a function of the objective's coefficient keys and the linear
     * constraints, and the latter are fixed for the constraint graph this entry is already keyed on.
     */
    private static @Nullable ReusableModel reusableModel(ConstraintSatisfactionProblem csp,
                                                          LinearObjective objective,
                                                          List<Variable<?>> variables,
                                                          Object cacheKey) {
        AtomicReference<ReusableModel> holder =
                csp.computeAuxiliaryCacheIfAbsent(cacheKey, ignored -> new AtomicReference<>());
        ReusableModel cached = holder.get();
        if (cached != null) {
            return cached.objective().equals(objective) ? cached : null;
        }
        if (!findAssignmentLinkages(csp, objective).isEmpty()) {
            return null;
        }
        Map<Variable<?>, org.ojalgo.optimisation.Variable> ojVariables = new LinkedHashMap<>();
        ExpressionsBasedModel model = build(csp, objective, variables, ojVariables);
        ReusableModel fresh = new ReusableModel(objective, variables, ojVariables, model);
        holder.set(fresh);
        return fresh;
    }

    private static ExpressionsBasedModel build(ConstraintSatisfactionProblem csp,
                                                LinearObjective objective,
                                                List<Variable<?>> variables,
                                                Map<Variable<?>, org.ojalgo.optimisation.Variable> ojVariables) {
        ExpressionsBasedModel model = new ExpressionsBasedModel();
        for (Variable<?> variable : variables) {
            double[] bounds = boundsOf(csp, variable);
            var ojVariable = model.addVariable(variable.getName())
                    .lower(bounds[0])
                    .upper(bounds[1])
                    .weight(objective.getCoefficients().getOrDefault(variable, 0.0));
            ojVariables.put(variable, ojVariable);
        }

        int index = 0;
        for (Constraint constraint : csp.getConstraints()) {
            addRow(model, ojVariables, constraint, index++);
        }
        addAssignmentRelaxationRows(csp, objective, model, ojVariables);
        return model;
    }

    private static Set<Variable<?>> relevantVariables(ConstraintSatisfactionProblem csp, LinearObjective objective) {
        Set<Variable<?>> variables = new LinkedHashSet<>(objective.getCoefficients().keySet());
        for (Constraint constraint : csp.getConstraints()) {
            if (isLinear(constraint)) {
                variables.addAll(constraint.getVariables());
            }
        }
        return variables;
    }

    private static boolean isLinear(Constraint constraint) {
        return constraint instanceof SumBoundConstraint<?>
                || constraint instanceof LinearBoundConstraint<?>
                || constraint instanceof SumVariableConstraint<?>
                || constraint instanceof LinearVariableConstraint<?>;
    }

    /**
     * Translates one linear constraint into an ojAlgo {@link Expression} row, skipping it entirely
     * (adding nothing to {@code model}) when it isn't one of the four linear constraint types, or
     * its {@link Operator} isn't propagating -- mirroring {@link SumBoundConstraint}/{@link
     * LinearBoundConstraint}'s own propagators, which treat a non-{@code EQ}/{@code LEQ}/{@code GEQ}
     * operator as a no-op rather than something to model.
     */
    private static void addRow(ExpressionsBasedModel model,
                                Map<Variable<?>, org.ojalgo.optimisation.Variable> ojVariables,
                                Constraint constraint,
                                int index) {
        switch (constraint) {
            case SumBoundConstraint<?> c -> {
                if (isPropagating(c.getOperator())) {
                    Expression expression = newRow(model, index, ojVariables, c.getVariables());
                    applyBound(expression, c.getOperator(), c.getBound().doubleValue());
                }
            }
            case LinearBoundConstraint<?> c -> {
                if (isPropagating(c.getOperator())) {
                    Expression expression = model.addExpression("row" + index);
                    for (var entry : c.getCoefficients().entrySet()) {
                        expression.set(ojVariables.get(entry.getKey()), entry.getValue().doubleValue());
                    }
                    applyBound(expression, c.getOperator(), c.getBound().doubleValue());
                }
            }
            case SumVariableConstraint<?> c -> {
                if (isPropagating(c.getOperator())) {
                    Expression expression = newRow(model, index, ojVariables, c.getSummedVariables());
                    expression.set(ojVariables.get(c.getTarget()), -1.0);
                    applyBound(expression, c.getOperator(), 0.0);
                }
            }
            case LinearVariableConstraint<?> c -> {
                if (isPropagating(c.getOperator())) {
                    Expression expression = model.addExpression("row" + index);
                    for (var entry : c.getCoefficients().entrySet()) {
                        expression.set(ojVariables.get(entry.getKey()), entry.getValue().doubleValue());
                    }
                    expression.set(ojVariables.get(c.getTarget()), -1.0);
                    applyBound(expression, c.getOperator(), 0.0);
                }
            }
            default -> { }
        }
    }

    private static Expression newRow(ExpressionsBasedModel model, int index,
                                      Map<Variable<?>, org.ojalgo.optimisation.Variable> ojVariables,
                                      Set<? extends Variable<?>> unitCoefficientVariables) {
        Expression expression = model.addExpression("row" + index);
        for (Variable<?> variable : unitCoefficientVariables) {
            expression.set(ojVariables.get(variable), 1.0);
        }
        return expression;
    }

    private static boolean isPropagating(Operator operator) {
        return operator == Operator.EQ || operator == Operator.LEQ || operator == Operator.GEQ;
    }

    /** Only ever called once {@link #isPropagating} has confirmed {@code operator} is one of these three. */
    private static void applyBound(Expression expression, Operator operator, double bound) {
        if (operator == Operator.EQ) {
            expression.level(bound);
        } else if (operator == Operator.LEQ) {
            expression.upper(bound);
        } else {
            expression.lower(bound);
        }
    }

    /**
     * One {@link GlobalCardinalityConstraint} paired with every table constraint found to look up
     * one of its variables into a current objective coefficient variable -- see {@link
     * #findAssignmentLinkages}.
     */
    private record AssignmentLinkage(GlobalCardinalityConstraint<?> gcc, List<TableLinkage> tables) {
    }

    /**
     * One table constraint's contribution to an {@link AssignmentLinkage}: {@code key} is the {@link
     * GlobalCardinalityConstraint} variable this table looks up, and {@code lookupByKeyValue} maps
     * each of {@code key}'s current live domain values to the objective coefficient variable(s) --
     * and the value(s) -- that live tuple assigns them. See {@link #functionalLookup}.
     */
    private record TableLinkage(Variable<?> key, Map<Object, Map<Variable<?>, Object>> lookupByKeyValue) {
    }

    /**
     * The purely structural half of {@link #findAssignmentLinkages}: which constraints in a
     * problem's constraint set (a graph-level property, independent of current domains) are
     * {@link GlobalCardinalityConstraint}s versus table constraints. Cached per distinct {@code
     * source} reference via {@link ConstraintSatisfactionProblem#computeAuxiliaryCacheIfAbsent},
     * mirroring {@code FixpointConsistency#filterCache}'s identical reference-equality pattern: a
     * cache hit (the overwhelmingly common case -- every {@link #solve} call within one search node
     * shares the same reference, and so does every node between nogood-learning events) skips
     * re-scanning the whole constraint set from scratch, while any reference change (a genuinely
     * different problem, or a fresh nogood) always falls back to a correct, fresh scan. Found via
     * JFR profiling {@code Fastfood-ff10.xml.lzma} (215 table constraints, zero {@link
     * GlobalCardinalityConstraint}s): the unconditional scan-and-classify was a measurable per-node
     * cost purely to discover, on every single node, that there was nothing to do.
     */
    private record ConstraintClassification(
            Set<Constraint> source, List<GlobalCardinalityConstraint<?>> gccs, List<Constraint> tables) {
    }

    private static ConstraintClassification classifyConstraints(ConstraintSatisfactionProblem csp) {
        AtomicReference<ConstraintClassification> holder =
                csp.computeAuxiliaryCacheIfAbsent(LpModelBuilder.class, ignored -> new AtomicReference<>());
        Set<Constraint> source = csp.getConstraints();
        ConstraintClassification cached = holder.get();
        if (cached != null && cached.source() == source) {
            return cached;
        }
        List<GlobalCardinalityConstraint<?>> gccs = new ArrayList<>();
        List<Constraint> tables = new ArrayList<>();
        for (Constraint constraint : source) {
            if (constraint instanceof GlobalCardinalityConstraint<?> gcc) {
                gccs.add(gcc);
            } else if (constraint instanceof NaryTuplesConstraint || constraint instanceof NaryStarredTuplesConstraint) {
                tables.add(constraint);
            }
        }
        ConstraintClassification fresh = new ConstraintClassification(source, gccs, tables);
        holder.set(fresh);
        return fresh;
    }

    /**
     * Finds every {@link GlobalCardinalityConstraint} in {@code csp} that has at least one qualifying
     * table linkage (see {@link #functionalLookup}) into {@code objective}'s own coefficient
     * variables -- the shape {@link #addAssignmentRelaxationRows} needs to build a real assignment
     * relaxation for. Returns an empty list (cheaply, via the two early-exit checks) for the vast
     * majority of CSPs that have no {@link GlobalCardinalityConstraint} or no table constraint at all.
     */
    private static List<AssignmentLinkage> findAssignmentLinkages(ConstraintSatisfactionProblem csp, LinearObjective objective) {
        ConstraintClassification classified = classifyConstraints(csp);
        if (classified.gccs().isEmpty() || classified.tables().isEmpty()) return List.of();

        List<AssignmentLinkage> linkages = new ArrayList<>();
        for (GlobalCardinalityConstraint<?> gcc : classified.gccs()) {
            List<TableLinkage> tableLinkages = new ArrayList<>();
            for (Constraint table : classified.tables()) {
                Set<Variable<?>> shared = new LinkedHashSet<>(table.getVariables());
                shared.retainAll(gcc.getVariables());
                if (shared.size() != 1) continue;
                Variable<?> key = shared.iterator().next();
                functionalLookup(csp, table, key, objective).ifPresent(
                        lookup -> tableLinkages.add(new TableLinkage(key, lookup)));
            }
            if (!tableLinkages.isEmpty()) linkages.add(new AssignmentLinkage(gcc, tableLinkages));
        }
        return linkages;
    }

    /**
     * Builds {@code key}'s value-to-objective-lookup map from {@code table}'s currently live tuples,
     * or {@link Optional#empty()} if {@code table} doesn't qualify: too many live tuples (see {@link
     * #MAX_TABLE_TUPLES_FOR_ASSIGNMENT_RELAXATION}), {@code key} itself is wildcarded in some tuple or
     * takes the same value in two different live tuples (ambiguous -- not a function of {@code key}),
     * the live tuples don't cover every one of {@code key}'s current domain values exactly once (which
     * would otherwise mean {@code table}'s own GAC propagation hasn't fully run against the current
     * domains, so trusting it here would be unsound), or no objective coefficient variable is
     * determined for <em>every</em> one of {@code key}'s live values. That last condition isn't just
     * "nothing gained" -- it's load-bearing for soundness: an objective variable left undetermined
     * (missing column, or {@link NaryStarredTuplesConstraint#STAR}) for even one live tuple can't be
     * linked at all, since {@link #addAssignmentRelaxationRows}'s linking row is an equality tying the
     * variable to a weighted sum of {@code key}'s indicators -- omitting the undetermined value's term
     * would silently force that variable to {@code 0} whenever that value is selected, cutting off a
     * genuinely feasible point rather than merely under-approximating it.
     */
    private static Optional<Map<Object, Map<Variable<?>, Object>>> functionalLookup(
            ConstraintSatisfactionProblem csp, Constraint table, Variable<?> key, LinearObjective objective) {
        List<Map<Variable<?>, Object>> liveTuples = liveTuplesOf(csp, table);
        if (liveTuples.isEmpty() || liveTuples.size() > MAX_TABLE_TUPLES_FOR_ASSIGNMENT_RELAXATION) {
            return Optional.empty();
        }

        Map<Object, Map<Variable<?>, Object>> byKeyValue = new LinkedHashMap<>();
        Set<Variable<?>> fullyDeterminedOthers = null;
        for (Map<Variable<?>, Object> tuple : liveTuples) {
            // key is always one of table's own variables (it came from table.getVariables() ∩
            // gcc.getVariables()), and both NaryTuplesConstraint/NaryStarredTuplesConstraint's own
            // factories assert every tuple carries a value for every one of the constraint's
            // variables, so tuple.get(key) is never null here.
            Object keyValue = tuple.get(key);
            if (keyValue == NaryStarredTuplesConstraint.STAR || byKeyValue.containsKey(keyValue)) {
                return Optional.empty();
            }
            Map<Variable<?>, Object> relevantOthers = new LinkedHashMap<>();
            for (Variable<? extends Number> otherVar : objective.getCoefficients().keySet()) {
                Object cell = tuple.get(otherVar);
                if (cell != null && cell != NaryStarredTuplesConstraint.STAR) relevantOthers.put(otherVar, cell);
            }
            byKeyValue.put(keyValue, relevantOthers);
            fullyDeterminedOthers = fullyDeterminedOthers == null
                    ? new LinkedHashSet<>(relevantOthers.keySet())
                    : intersect(fullyDeterminedOthers, relevantOthers.keySet());
        }

        DiscreteDomain<?> keyDomain = (DiscreteDomain<?>) csp.getDomain(key);
        if (!byKeyValue.keySet().equals(new LinkedHashSet<>(keyDomain.toList())) || fullyDeterminedOthers.isEmpty()) {
            return Optional.empty();
        }
        Set<Variable<?>> keepOthers = fullyDeterminedOthers;
        Map<Object, Map<Variable<?>, Object>> filtered = new LinkedHashMap<>();
        for (var entry : byKeyValue.entrySet()) {
            Map<Variable<?>, Object> kept = new LinkedHashMap<>();
            for (Variable<?> otherVar : keepOthers) kept.put(otherVar, entry.getValue().get(otherVar));
            filtered.put(entry.getKey(), kept);
        }
        return Optional.of(filtered);
    }

    private static Set<Variable<?>> intersect(Set<Variable<?>> a, Set<Variable<?>> b) {
        Set<Variable<?>> result = new LinkedHashSet<>(a);
        result.retainAll(b);
        return result;
    }

    /**
     * {@code table}'s tuples still consistent with every column's current domain -- the same liveness
     * check {@link NaryTuplesConstraint#propagate}/{@link NaryStarredTuplesConstraint#propagate}
     * already apply, just without materialising a narrowed domain afterward. Normalises both
     * constraint types to a plain {@code Map<Variable<?>, Object>} per tuple so {@link
     * #functionalLookup} doesn't need to know which one it's looking at.
     */
    private static List<Map<Variable<?>, Object>> liveTuplesOf(ConstraintSatisfactionProblem csp, Constraint table) {
        if (table instanceof NaryTuplesConstraint t) {
            return t.getTuples().stream()
                    .filter(tuple -> t.getVariables().stream().allMatch(v -> csp.getDomain(v).contains(tuple.getValue(v).orElseThrow())))
                    .map(Assignment::getValues)
                    .toList();
        }
        NaryStarredTuplesConstraint t = (NaryStarredTuplesConstraint) table;
        return t.getTuples().stream()
                .filter(tuple -> t.getVariables().stream().allMatch(v -> {
                    Object cell = tuple.get(v);
                    return cell == NaryStarredTuplesConstraint.STAR || csp.getDomain(v).contains(cell);
                }))
                .toList();
    }

    /**
     * Adds the assignment-relaxation rows for every linkage {@link #findAssignmentLinkages} found: a
     * one-hot indicator variable per ({@link GlobalCardinalityConstraint} variable, live domain value)
     * pair, a "takes exactly one value" row per variable, a cardinality row per tracked value (summing
     * indicators across every variable in the group, mirroring {@link GlobalCardinalityConstraint}'s
     * own {@code cardinalityRanges}), and a lookup row per objective variable a table linked to those
     * same indicators. See this class's own top-level Javadoc for why this needs to share indicators
     * between the two row kinds rather than modelling each constraint independently.
     */
    private static void addAssignmentRelaxationRows(ConstraintSatisfactionProblem csp,
                                                      LinearObjective objective,
                                                      ExpressionsBasedModel model,
                                                      Map<Variable<?>, org.ojalgo.optimisation.Variable> ojVariables) {
        List<AssignmentLinkage> linkages = findAssignmentLinkages(csp, objective);
        int rowIndex = 0;
        for (AssignmentLinkage linkage : linkages) {
            Map<Variable<?>, Map<Object, org.ojalgo.optimisation.Variable>> indicators = new LinkedHashMap<>();
            for (Variable<?> groupVariable : linkage.gcc().getVariables()) {
                DiscreteDomain<?> domain = (DiscreteDomain<?>) csp.getDomain(groupVariable);
                Map<Object, org.ojalgo.optimisation.Variable> perValue = new LinkedHashMap<>();
                Expression oneHot = model.addExpression("assign-onehot-" + rowIndex++);
                for (Object value : domain.toList()) {
                    var indicator = model.addVariable(groupVariable.getName() + "=" + value).lower(0).upper(1);
                    perValue.put(value, indicator);
                    oneHot.set(indicator, 1.0);
                }
                oneHot.level(1.0);
                indicators.put(groupVariable, perValue);
            }

            for (var trackedEntry : linkage.gcc().getCardinalityRanges().entrySet()) {
                Object trackedValue = trackedEntry.getKey();
                GlobalCardinalityConstraint.OccurrenceRange range = trackedEntry.getValue();
                Expression cardinality = model.addExpression("assign-card-" + rowIndex++);
                for (var perVariable : indicators.values()) {
                    var indicator = perVariable.get(trackedValue);
                    if (indicator != null) cardinality.set(indicator, 1.0);
                }
                cardinality.upper((double) range.max());
                if (range.min() > 0) cardinality.lower((double) range.min());
            }

            for (TableLinkage tableLinkage : linkage.tables()) {
                Map<Object, org.ojalgo.optimisation.Variable> keyIndicators = indicators.get(tableLinkage.key());
                Map<Variable<?>, Map<Object, Object>> coefficientsByOtherVariable = new LinkedHashMap<>();
                for (var lookupEntry : tableLinkage.lookupByKeyValue().entrySet()) {
                    for (var otherEntry : lookupEntry.getValue().entrySet()) {
                        coefficientsByOtherVariable.computeIfAbsent(otherEntry.getKey(), k -> new LinkedHashMap<>())
                                .put(lookupEntry.getKey(), otherEntry.getValue());
                    }
                }
                for (var otherEntry : coefficientsByOtherVariable.entrySet()) {
                    Expression link = model.addExpression("assign-link-" + rowIndex++);
                    link.set(ojVariables.get(otherEntry.getKey()), 1.0);
                    for (var coefficientEntry : otherEntry.getValue().entrySet()) {
                        double coefficient = ((Number) coefficientEntry.getValue()).doubleValue();
                        link.set(keyIndicators.get(coefficientEntry.getKey()), -coefficient);
                    }
                    link.level(0.0);
                }
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static double[] boundsOf(ConstraintSatisfactionProblem csp, Variable<?> variable) {
        Domain domain = csp.getDomain((Variable) variable);
        return new double[]{NumericBounds.min(domain), NumericBounds.max(domain)};
    }
}

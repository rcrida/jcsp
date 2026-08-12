package io.github.rcrida.jcsp.parser.xcsp3;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem.ConstraintSatisfactionProblemBuilder;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.NumericDiscreteDomain;
import io.github.rcrida.jcsp.solver.BranchAndBoundSolver;
import io.github.rcrida.jcsp.solver.LinearObjective;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.Nullable;
import org.xcsp.common.Condition;
import org.xcsp.common.Condition.ConditionVal;
import org.xcsp.common.Condition.ConditionVar;
import org.xcsp.common.IVar;
import org.xcsp.common.Types.TypeConditionOperatorRel;
import org.xcsp.common.Types.TypeFlag;
import org.xcsp.common.Types.TypeObjective;
import org.xcsp.common.Types.TypeOperatorRel;
import org.xcsp.common.Types.TypeRank;
import org.xcsp.common.predicates.XNode;
import org.xcsp.common.predicates.XNodeParent;
import org.xcsp.parser.callbacks.XCallbacks;
import org.xcsp.parser.callbacks.XCallbacks2;
import org.xcsp.parser.entries.XVariables.XVarInteger;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/**
 * Drives {@code xcsp3-tools}' SAX-based parser by implementing its {@link XCallbacks2} callback
 * interface, translating each XCSP3 construct directly into calls on a {@link
 * ConstraintSatisfactionProblemBuilder}. Only the MVP subset of constraints documented in
 * {@link Xcsp3Parser} is overridden; any other construct falls through to {@link XCallbacks2}'s
 * own default implementation, which throws a plain {@link RuntimeException} via {@link
 * XCallbacks2#unimplementedCase} -- both paths fail immediately rather than silently producing an
 * under-constrained model. A constraint variant this class does recognise but cannot map onto
 * jcsp's builder API (e.g. a conflict-mode extension table, or a count/element/cumulative
 * condition shaped in a way jcsp has no equivalent for) throws {@link
 * UnsupportedXcsp3ConstraintException} with a specific reason.
 * <p>
 * XCSP3 core is integer-only, so every variable is built as {@code Variable<Integer>}; {@link
 * #boundsByName} additionally tracks each variable's declared bounds so that {@code element}'s
 * index and {@code circuit}'s successor list -- both 1-indexed in jcsp but not necessarily in a
 * given XCSP3 instance -- can be shifted onto a correctly-bounded auxiliary variable via {@link
 * #shiftVariable}.
 */
final class Xcsp3CallbackHandler implements XCallbacks2 {

    /**
     * Upper bound on a single {@code buildVarInteger(x, min, max)} domain's value count.
     * {@link IntRangeDomain#of} eagerly materializes every value in the range into a {@code
     * LinkedHashSet} (it isn't a lazy/interval-backed domain), so an unbounded XCSP3 range (legal
     * syntax, e.g. {@code 0..1000000}) would otherwise risk severe slowdown or {@link
     * OutOfMemoryError} purely from parsing, before the solver chain ever runs.
     */
    private static final long MAX_MATERIALIZED_DOMAIN_SIZE = 1_000_000;

    private final XCallbacks.Implem implem = new XCallbacks.Implem(this);
    private final ConstraintSatisfactionProblemBuilder builder = ConstraintSatisfactionProblem.builder();
    private final Map<String, Variable<Integer>> variablesByName = new LinkedHashMap<>();
    private final Map<String, int[]> boundsByName = new LinkedHashMap<>();
    private final Map<String, Variable<Integer>> shiftedVariables = new LinkedHashMap<>();
    private @Nullable ToDoubleFunction<Assignment> objective;
    private boolean maximize;

    Xcsp3CallbackHandler() {
        // By default xcsp3-tools "recognizes" simple intension/count/sum/etc. shapes and routes
        // them to more specific callbacks (buildCtrPrimitive, buildCtrExactly, buildCtrAmong, ...)
        // instead of the generic ones this class implements. rawParameters() disables that
        // recognition so every constraint always arrives through the one generic callback per
        // constraint type that this class overrides.
        implem.rawParameters();
    }

    @Override
    public XCallbacks.Implem implem() {
        return implem;
    }

    Xcsp3Instance toInstance() {
        return new Xcsp3Instance(builder.build(), objective, maximize);
    }

    // ---- Variables ----------------------------------------------------------------------------

    @Override
    public void buildVarInteger(XVarInteger x, int minValue, int maxValue) {
        long domainSize = (long) maxValue - minValue + 1;
        if (domainSize > MAX_MATERIALIZED_DOMAIN_SIZE) {
            throw new UnsupportedXcsp3ConstraintException(
                    "Domain too large to materialize (" + domainSize + " values, max " + MAX_MATERIALIZED_DOMAIN_SIZE + "): " + x.id());
        }
        registerVariable(x, IntRangeDomain.of(minValue, maxValue), minValue, maxValue);
    }

    @Override
    public void buildVarInteger(XVarInteger x, int[] values) {
        Integer[] boxed = Arrays.stream(values).boxed().toArray(Integer[]::new);
        int min = Arrays.stream(values).min().orElseThrow();
        int max = Arrays.stream(values).max().orElseThrow();
        registerVariable(x, NumericDiscreteDomain.of(boxed), min, max);
    }

    private void registerVariable(XVarInteger x, Domain<Integer> domain, int min, int max) {
        Variable<Integer> variable = Variable.Factory.INSTANCE.create(x.id());
        variablesByName.put(x.id(), variable);
        boundsByName.put(x.id(), new int[]{min, max});
        builder.variableDomain(variable, domain);
    }

    private Variable<Integer> variableFor(IVar v) {
        return variablesByName.get(((XVarInteger) v).id());
    }

    private List<Variable<Integer>> toVariableList(XVarInteger[] list) {
        return Arrays.stream(list).map(this::variableFor).toList();
    }

    private Set<Variable<Integer>> toVariableSet(XVarInteger[] list) {
        return Arrays.stream(list).map(this::variableFor).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Auxiliary variable {@code offset} away from {@code original}, linked via {@link
     * ConstraintSatisfactionProblemBuilder#offsetConstraint}; identity if {@code offset == 0}.
     * Memoized in {@link #shiftedVariables} keyed by the auxiliary variable's own name (already a
     * unique function of {@code original}/{@code offset}), so the same {@code original}/{@code
     * offset} pair -- e.g. an index variable shared by several {@code element} constraints --
     * reuses one auxiliary variable/constraint instead of building a redundant copy per occurrence.
     */
    private Variable<Integer> shiftVariable(XVarInteger original, int offset) {
        Variable<Integer> base = variableFor(original);
        if (offset == 0) return base;
        String shiftedName = original.id() + "$shift" + offset;
        return shiftedVariables.computeIfAbsent(shiftedName, name -> {
            int[] bounds = boundsByName.get(original.id());
            Variable<Integer> shifted = Variable.Factory.INSTANCE.create(name);
            builder.variableDomain(shifted, IntRangeDomain.of(bounds[0] + offset, bounds[1] + offset));
            builder.offsetConstraint(base, offset, Operator.EQ, shifted);
            return shifted;
        });
    }

    private Variable<Integer> oneBasedIndex(XVarInteger indexVar, int startIndex) {
        return shiftVariable(indexVar, 1 - startIndex);
    }

    // ---- Operator mapping -----------------------------------------------------------------------

    private static Operator mapOperator(TypeConditionOperatorRel operator) {
        return switch (operator) {
            case LT -> Operator.LT;
            case LE -> Operator.LEQ;
            case GE -> Operator.GEQ;
            case GT -> Operator.GT;
            case NE -> Operator.NEQ;
            case EQ -> Operator.EQ;
        };
    }

    private static Operator mapOrderingOperator(TypeOperatorRel operator) {
        return switch (operator) {
            case LT -> Operator.LT;
            case LE -> Operator.LEQ;
            case GE -> Operator.GEQ;
            case GT -> Operator.GT;
        };
    }

    // ---- intension ------------------------------------------------------------------------------

    @Override
    public void buildCtrIntension(String id, XVarInteger[] list, XNodeParent<XVarInteger> tree) {
        builder.predicateConstraint(toVariableSet(list), IntensionExpressionEvaluator.toPredicate(tree, variablesByName));
    }

    // ---- extension (table) ------------------------------------------------------------------------

    @Override
    public void buildCtrExtension(String id, XVarInteger[] list, int[][] tuples, boolean positive, Set<TypeFlag> flags) {
        if (!positive) {
            throw new UnsupportedXcsp3ConstraintException("Negative (conflict) extension tables are not supported: " + id);
        }
        if (!flags.isEmpty()) {
            throw new UnsupportedXcsp3ConstraintException("Starred/smart extension tuples are not supported: " + id);
        }
        List<Variable<Integer>> vars = toVariableList(list);
        Set<Assignment> assignments = new LinkedHashSet<>();
        for (int[] tuple : tuples) {
            Map<Variable<Integer>, Integer> tupleValues = new LinkedHashMap<>();
            for (int i = 0; i < vars.size(); i++) {
                tupleValues.put(vars.get(i), tuple[i]);
            }
            assignments.add(Assignment.of(tupleValues));
        }
        builder.tuplesConstraint(assignments);
    }

    // ---- allDifferent -----------------------------------------------------------------------------

    @Override
    public void buildCtrAllDifferent(String id, XVarInteger[] list) {
        builder.allDiffConstraint(toVariableSet(list));
    }

    // ---- sum ----------------------------------------------------------------------------------------

    @Override
    public void buildCtrSum(String id, XVarInteger[] list, Condition condition) {
        applySumCondition(toVariableSet(list), condition, id);
    }

    @Override
    public void buildCtrSum(String id, XVarInteger[] list, int[] coeffs, Condition condition) {
        applyLinearCondition(toCoefficientMap(list, coeffs), condition, id);
    }

    @Override
    public void buildCtrSum(String id, XVarInteger[] list, XVarInteger[] coeffVars, Condition condition) {
        throw new UnsupportedXcsp3ConstraintException("Sum with variable coefficients is not supported: " + id);
    }

    // applySumCondition/applyLinearCondition's final "else" is unreachable through any real XCSP3
    // sum/linear <condition> -- Condition#buildFrom only ever produces ConditionVal/ConditionVar for
    // the relational operators these constraints use (ConditionIntvl/ConditionSet are for
    // in/notin-style set conditions, which sum/linear don't accept). Package-private (not private)
    // so Xcsp3CallbackHandlerTest can still exercise that branch directly with a hand-built
    // Condition, rather than leaving genuinely dead code uncovered.

    void applySumCondition(Set<Variable<Integer>> vars, Condition condition, String id) {
        if (condition instanceof ConditionVal val) {
            builder.sumConstraint(vars, mapOperator(val.operator), (int) val.k);
        } else if (condition instanceof ConditionVar var) {
            builder.sumConstraint(vars, mapOperator(var.operator), variableFor(var.x));
        } else {
            throw new UnsupportedXcsp3ConstraintException("Unsupported sum condition: " + id);
        }
    }

    void applyLinearCondition(Map<Variable<Integer>, Integer> coefficients, Condition condition, String id) {
        if (condition instanceof ConditionVal val) {
            builder.linearConstraint(coefficients, mapOperator(val.operator), (int) val.k);
        } else if (condition instanceof ConditionVar var) {
            builder.linearConstraint(coefficients, mapOperator(var.operator), variableFor(var.x));
        } else {
            throw new UnsupportedXcsp3ConstraintException("Unsupported linear condition: " + id);
        }
    }

    private Map<Variable<Integer>, Integer> toCoefficientMap(XVarInteger[] list, int[] coeffs) {
        Map<Variable<Integer>, Integer> coefficients = new LinkedHashMap<>();
        for (int i = 0; i < list.length; i++) {
            coefficients.put(variableFor(list[i]), coeffs[i]);
        }
        return coefficients;
    }

    // ---- count / among --------------------------------------------------------------------------------

    @Override
    public void buildCtrCount(String id, XVarInteger[] list, int[] values, Condition condition) {
        if (!(condition instanceof ConditionVal val)) {
            throw new UnsupportedXcsp3ConstraintException("count with a variable target is not supported: " + id);
        }
        Set<Variable<Integer>> vars = toVariableSet(list);
        Operator operator = mapOperator(val.operator);
        int n = (int) val.k;
        if (values.length == 1) {
            builder.countConstraint(vars, values[0], operator, n);
        } else {
            Set<Integer> valueSet = Arrays.stream(values).boxed().collect(Collectors.toCollection(LinkedHashSet::new));
            builder.amongConstraint(vars, valueSet, operator, n);
        }
    }

    // ---- element ------------------------------------------------------------------------------------------

    @Override
    public void buildCtrElement(String id, XVarInteger[] list, int startIndex, XVarInteger index, TypeRank rank, Condition condition) {
        Variable<Integer> result = elementResult(condition, id);
        builder.elementVariableConstraint(oneBasedIndex(index, startIndex), result, toVariableList(list));
        requireAnyRank(rank, id);
    }

    @Override
    public void buildCtrElement(String id, int[] list, int startIndex, XVarInteger index, TypeRank rank, Condition condition) {
        requireAnyRank(rank, id);
        Variable<Integer> result = elementResult(condition, id);
        List<Integer> values = Arrays.stream(list).boxed().toList();
        builder.elementConstraint(oneBasedIndex(index, startIndex), result, values);
    }

    private static void requireAnyRank(TypeRank rank, String id) {
        if (rank != TypeRank.ANY) {
            throw new UnsupportedXcsp3ConstraintException("element with a rank modifier is not supported: " + id);
        }
    }

    private Variable<Integer> elementResult(Condition condition, String id) {
        if (!(condition instanceof ConditionVar var) || var.operator != TypeConditionOperatorRel.EQ) {
            throw new UnsupportedXcsp3ConstraintException("element requires an EQ-to-variable condition: " + id);
        }
        return variableFor(var.x);
    }

    // ---- ordered / lex --------------------------------------------------------------------------------------

    @Override
    public void buildCtrOrdered(String id, XVarInteger[] list, TypeOperatorRel operator) {
        List<Variable<Integer>> vars = toVariableList(list);
        Operator jcspOperator = mapOrderingOperator(operator);
        for (int i = 0; i + 1 < vars.size(); i++) {
            builder.comparatorConstraint(vars.get(i), jcspOperator, vars.get(i + 1));
        }
    }

    @Override
    public void buildCtrLex(String id, XVarInteger[][] lists, TypeOperatorRel operator) {
        if (lists.length != 2) {
            throw new UnsupportedXcsp3ConstraintException("lex with more than two lists is not supported: " + id);
        }
        builder.lexConstraint(toVariableList(lists[0]), mapOrderingOperator(operator), toVariableList(lists[1]));
    }

    // ---- cumulative -------------------------------------------------------------------------------------------

    @Override
    public void buildCtrCumulative(String id, XVarInteger[] origins, int[] lengths, int[] heights, Condition condition) {
        if (!(condition instanceof ConditionVal val) || val.operator != TypeConditionOperatorRel.LE) {
            throw new UnsupportedXcsp3ConstraintException("cumulative requires a <= constant condition: " + id);
        }
        List<Variable<Integer>> starts = toVariableList(origins);
        List<Integer> durations = Arrays.stream(lengths).boxed().toList();
        List<Integer> resources = Arrays.stream(heights).boxed().toList();
        builder.cumulativeConstraint(starts, durations, resources, (int) val.k);
    }

    // ---- circuit ----------------------------------------------------------------------------------------------

    @Override
    public void buildCtrCircuit(String id, XVarInteger[] list, int startIndex) {
        int offset = 1 - startIndex;
        List<Variable<Integer>> successors = Arrays.stream(list).map(v -> shiftVariable(v, offset)).toList();
        builder.circuitConstraint(successors);
    }

    // ---- binPacking -------------------------------------------------------------------------------------------

    @Override
    public void buildCtrBinPacking(String id, XVarInteger[] list, int[] sizes, Condition condition) {
        if (!(condition instanceof ConditionVal val) || val.operator != TypeConditionOperatorRel.LE) {
            throw new UnsupportedXcsp3ConstraintException("binPacking requires a shared <= constant capacity condition: " + id);
        }
        int minBin = Arrays.stream(list).mapToInt(v -> boundsByName.get(v.id())[0]).min().orElseThrow();
        int maxBin = Arrays.stream(list).mapToInt(v -> boundsByName.get(v.id())[1]).max().orElseThrow();
        if (minBin != 0) {
            throw new UnsupportedXcsp3ConstraintException("binPacking requires 0-indexed bin variables: " + id);
        }
        List<Variable<Integer>> bins = toVariableList(list);
        List<Integer> weights = Arrays.stream(sizes).boxed().toList();
        List<Integer> capacities = Collections.nCopies(maxBin + 1, (int) val.k);
        builder.binPackingConstraint(bins, weights, capacities);
    }

    // ---- objectives -----------------------------------------------------------------------------------------------

    @Override
    public void buildObjToMinimize(String id, XVarInteger x) {
        Variable<Integer> variable = variableFor(x);
        // BranchAndBoundSolver calls the objective on *partial* assignments for pruning, so it must
        // return a lower bound on the cost of any completion, not throw when x isn't assigned yet
        // -- the domain minimum is always such a bound.
        double lowerBound = boundsByName.get(x.id())[0];
        objective = assignment -> assignment.getValue(variable).map(Integer::doubleValue).orElse(lowerBound);
        maximize = false;
    }

    @Override
    public void buildObjToMaximize(String id, XVarInteger x) {
        Variable<Integer> variable = variableFor(x);
        // Internally minimizes -x, so the fallback lower bound for an unassigned x is -max(x).
        double lowerBound = -boundsByName.get(x.id())[1];
        objective = assignment -> assignment.getValue(variable).map(v -> (double) -v).orElse(lowerBound);
        maximize = true;
    }

    @Override
    public void buildObjToMinimize(String id, XNodeParent<XVarInteger> tree) {
        // Unlike a single-variable or sum-type objective, a general intension-tree objective has no
        // well-defined lower bound under a partial assignment (would need interval arithmetic over
        // the remaining variables' domains), which BranchAndBoundSolver's pruning requires -- out of
        // MVP scope.
        throw new UnsupportedXcsp3ConstraintException("General expression objectives are not supported: " + id);
    }

    @Override
    public void buildObjToMaximize(String id, XNodeParent<XVarInteger> tree) {
        throw new UnsupportedXcsp3ConstraintException("General expression objectives are not supported: " + id);
    }

    @Override
    public void buildObjToMinimize(String id, TypeObjective type, XVarInteger[] list) {
        objective = buildSumObjective(type, list, null, false);
        maximize = false;
    }

    @Override
    public void buildObjToMaximize(String id, TypeObjective type, XVarInteger[] list) {
        objective = buildSumObjective(type, list, null, true);
        maximize = true;
    }

    @Override
    public void buildObjToMinimize(String id, TypeObjective type, XVarInteger[] list, int[] coeffs) {
        objective = buildSumObjective(type, list, coeffs, false);
        maximize = false;
    }

    @Override
    public void buildObjToMaximize(String id, TypeObjective type, XVarInteger[] list, int[] coeffs) {
        objective = buildSumObjective(type, list, coeffs, true);
        maximize = true;
    }

    /**
     * Builds a {@link BranchAndBoundSolver}-safe objective from a {@code <minimize
     * type="sum">}/{@code <maximize type="sum">} array. Requires every coefficient and every
     * referenced variable's domain minimum to be non-negative.
     * <p>
     * For {@code minimize}, a {@link LinearObjective} is sound directly: its "unassigned
     * contributes 0" convention is a valid lower bound on any completion's cost whenever every
     * coefficient is non-negative (0 is always {@code <=} a non-negative variable's eventual
     * contribution). Negating a {@link LinearObjective} to express {@code maximize} does
     * <em>not</em> produce a sound lower bound even under that same precondition, though: the
     * unassigned fill needed for {@code maximize} is each variable's domain <em>maximum</em> (the
     * best case for what's left to assign), not zero, so the negated total stays {@code <=} every
     * completion's negated cost. That fill isn't expressible as a {@link LinearObjective} (its own
     * "contributes 0" convention is fixed), so {@code maximize} builds a plain {@link
     * ToDoubleFunction} by hand instead, forgoing {@link io.github.rcrida.jcsp.solver.lp.LpModelBuilder}'s
     * LP-relaxation fast path that {@code instanceof LinearObjective} would otherwise unlock.
     */
    private ToDoubleFunction<Assignment> buildSumObjective(TypeObjective type, XVarInteger[] list, int[] coeffs, boolean maximizing) {
        if (type != TypeObjective.SUM) {
            throw new UnsupportedXcsp3ConstraintException("Only sum-type array objectives are supported, got: " + type);
        }
        for (int i = 0; i < list.length; i++) {
            double coefficient = coeffs == null ? 1.0 : coeffs[i];
            if (coefficient < 0 || boundsByName.get(list[i].id())[0] < 0) {
                throw new UnsupportedXcsp3ConstraintException(
                        "Sum-type objectives require non-negative coefficients and domains for a sound partial-assignment bound: " + list[i].id());
            }
        }
        if (!maximizing) {
            LinearObjective.LinearObjectiveBuilder linearObjectiveBuilder = LinearObjective.builder();
            for (int i = 0; i < list.length; i++) {
                double coefficient = coeffs == null ? 1.0 : coeffs[i];
                linearObjectiveBuilder.coefficient(variableFor(list[i]), coefficient);
            }
            return linearObjectiveBuilder.build();
        }
        Map<Variable<Integer>, Double> coefficients = new LinkedHashMap<>();
        Map<Variable<Integer>, Integer> unassignedFill = new LinkedHashMap<>();
        for (int i = 0; i < list.length; i++) {
            Variable<Integer> variable = variableFor(list[i]);
            coefficients.put(variable, coeffs == null ? 1.0 : (double) coeffs[i]);
            unassignedFill.put(variable, boundsByName.get(list[i].id())[1]);
        }
        return assignment -> {
            double total = 0;
            for (var entry : coefficients.entrySet()) {
                Optional<Integer> value = assignment.getValue(entry.getKey());
                total += entry.getValue() * value.orElseGet(() -> unassignedFill.get(entry.getKey()));
            }
            return -total;
        };
    }
}

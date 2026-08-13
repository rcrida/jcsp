package io.github.rcrida.jcsp.parser.xcsp3;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem.ConstraintSatisfactionProblemBuilder;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.binary.BinaryComparatorConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryElementConstraint;
import io.github.rcrida.jcsp.constraints.nary.AllDiffConstraint;
import io.github.rcrida.jcsp.constraints.nary.AmongConstraint;
import io.github.rcrida.jcsp.constraints.nary.BinPackingConstraint;
import io.github.rcrida.jcsp.constraints.nary.CircuitConstraint;
import io.github.rcrida.jcsp.constraints.nary.CountConstraint;
import io.github.rcrida.jcsp.constraints.nary.CumulativeConstraint;
import io.github.rcrida.jcsp.constraints.nary.GlobalCardinalityConstraint;
import io.github.rcrida.jcsp.constraints.nary.LexConstraint;
import io.github.rcrida.jcsp.constraints.nary.MaxVariableConstraint;
import io.github.rcrida.jcsp.constraints.nary.NaryElementConstraint;
import io.github.rcrida.jcsp.constraints.nary.NaryTuplesConstraint;
import io.github.rcrida.jcsp.constraints.nary.OrderedConstraint;
import io.github.rcrida.jcsp.constraints.nary.PredicateConstraint;
import io.github.rcrida.jcsp.constraints.nary.SumBoundConstraint;
import io.github.rcrida.jcsp.constraints.unary.UnaryComparatorConstraint;
import io.github.rcrida.jcsp.constraints.unary.UnaryValueConstraint;
import io.github.rcrida.jcsp.domains.BooleanDomain;
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
import org.xcsp.common.Types.TypeReification;
import org.xcsp.common.predicates.XNode;
import org.xcsp.common.predicates.XNodeParent;
import org.xcsp.parser.callbacks.XCallbacks;
import org.xcsp.parser.callbacks.XCallbacks2;
import org.xcsp.parser.entries.XConstraints.XCtr;
import org.xcsp.parser.entries.XConstraints.XReification;
import org.xcsp.parser.entries.XVariables.XVarInteger;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    private final Map<String, Variable<Boolean>> booleanIndicators = new LinkedHashMap<>();
    private @Nullable ToDoubleFunction<Assignment> objective;
    private boolean maximize;
    private @Nullable XReification currentReification;

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

    // ---- Reification ------------------------------------------------------------------------------------------

    /**
     * {@code buildCtrXxx} callbacks only ever receive a constraint's already-unpacked typed
     * parameters (list, condition, ...), never the {@link XCtr} itself -- so this is the only
     * place {@link XCtr#reification} is visible. Stashing it here for the duration of the
     * (possibly deeply-nested, e.g. inside a {@code group}/{@code slide}) dispatch lets the
     * handful of {@code buildCtrXxx} overrides that support reification (currently just {@link
     * #buildCtrIntension} and the coefficient-less {@link #buildCtrSum(String, XVarInteger[],
     * Condition)}) check {@link #currentReification} and route through {@link #addOrReify} instead
     * of adding directly, without every other {@code buildCtrXxx} method needing to know
     * reification exists at all.
     */
    @Override
    public void loadCtr(XCtr c) {
        XReification previous = currentReification;
        currentReification = c.reification;
        try {
            XCallbacks2.super.loadCtr(c);
        } finally {
            currentReification = previous;
        }
    }

    /**
     * Adds {@code body} directly when the constraint currently being loaded isn't reified;
     * otherwise wraps it per {@link #currentReification}'s {@link TypeReification}: {@code FULL}
     * ({@code indicator <-> body}) maps onto {@code reifyConstraint}, {@code HALF_FROM} ({@code
     * indicator -> body}) onto {@code impliesConstraint}. {@code HALF_TO} ({@code body ->
     * indicator}) has no jcsp counterpart -- unlike the other two, it isn't a builder method jcsp
     * already has, and encoding it generically would need a way to negate an arbitrary {@link
     * Constraint}, which nothing in this codebase provides -- so it throws rather than silently
     * mapping onto the wrong direction. The {@code TypeReification} switch is written as an
     * expression (yielding which builder call to make) rather than a statement -- like {@link
     * #mapOperator}'s switch, this lets the compiler prove it exhaustive over all three enum
     * constants, so it emits no extra "no match" branch that would otherwise sit permanently
     * uncovered (a bare {@code TypeReification} has no fourth constant to exercise it with).
     */
    private void addOrReify(Constraint body, String id) {
        XReification reification = currentReification;
        if (reification == null) {
            builder.constraint(body);
            return;
        }
        Variable<Boolean> indicator = booleanIndicatorFor((XVarInteger) reification.var);
        boolean full = switch (reification.type) {
            case FULL -> true;
            case HALF_FROM -> false;
            case HALF_TO -> throw new UnsupportedXcsp3ConstraintException(
                    "hreifiedTo (constraint -> indicator) reification is not supported: " + id);
        };
        if (full) {
            builder.reifyConstraint(indicator, body);
        } else {
            builder.impliesConstraint(indicator, body);
        }
    }

    /**
     * XCSP3-core is integer-only (see this class's own top-level Javadoc), so a reification
     * target -- like every other variable -- was already registered as a plain 0/1 {@code
     * Variable<Integer>} by {@link #buildVarInteger}. {@code reifyConstraint}/{@code
     * impliesConstraint} both require a genuine {@code Variable<Boolean>} indicator, so this
     * bridges the two: one fresh boolean variable per distinct integer variable (memoized, the
     * same pattern {@link #shiftVariable} uses), tied to it via {@code intVar == 1} reified with
     * {@code FULL} semantics -- built directly against the shared {@link #builder} rather than
     * through {@link #addOrReify}, since this bridge is never itself the constraint being loaded.
     */
    private Variable<Boolean> booleanIndicatorFor(XVarInteger intVar) {
        return booleanIndicators.computeIfAbsent(intVar.id(), name -> {
            Variable<Boolean> boolVar = Variable.Factory.INSTANCE.create(name + "$bool");
            builder.variableDomain(boolVar, BooleanDomain.INSTANCE);
            builder.reifyConstraint(boolVar, UnaryValueConstraint.of(variablesByName.get(name), 1));
            return boolVar;
        });
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
        addOrReify(PredicateConstraint.builder().variables(toVariableSet(list))
                .predicate(IntensionExpressionEvaluator.toPredicate(tree, variablesByName)).build(), id);
    }

    // ---- extension (table) ------------------------------------------------------------------------

    @Override
    public void buildCtrExtension(String id, XVarInteger[] list, int[][] tuples, boolean positive, Set<TypeFlag> flags) {
        if (!positive) {
            throw new UnsupportedXcsp3ConstraintException("Negative (conflict) extension tables are not supported: " + id);
        }
        requireNoUnsupportedFlags(flags, id);
        List<Variable<Integer>> vars = toVariableList(list);
        Set<Assignment> assignments = new LinkedHashSet<>();
        for (int[] tuple : tuples) {
            Map<Variable<Integer>, Integer> tupleValues = new LinkedHashMap<>();
            for (int i = 0; i < vars.size(); i++) {
                tupleValues.put(vars.get(i), tuple[i]);
            }
            assignments.add(Assignment.of(tupleValues));
        }
        addOrReify(NaryTuplesConstraint.of(assignments), id);
    }

    /**
     * The unary form of {@code extension}: a single variable restricted to (or, when {@code
     * !positive}, excluded from) a fixed list of integer values -- functionally a value-membership
     * predicate rather than a tuple table, so it maps onto the same {@link PredicateConstraint}
     * {@link #buildCtrIntension} already uses rather than {@link NaryTuplesConstraint}.
     */
    @Override
    public void buildCtrExtension(String id, XVarInteger x, int[] values, boolean positive, Set<TypeFlag> flags) {
        requireNoUnsupportedFlags(flags, id);
        Variable<Integer> variable = variableFor(x);
        Set<Integer> valueSet = Arrays.stream(values).boxed().collect(Collectors.toCollection(LinkedHashSet::new));
        addOrReify(PredicateConstraint.builder().variables(Set.of(variable))
                .predicate(a -> valueSet.contains(a.getValue(variable).orElseThrow()) == positive)
                .build(), id);
    }

    /**
     * Rejects {@link TypeFlag#STARRED_TUPLES} (wildcard "don't care" entries) and {@link
     * TypeFlag#SMART_TUPLES} (arbitrary expressions in place of literal values) -- neither is
     * representable by the plain {@code int[]}/{@code int[][]} this class works with. {@link
     * TypeFlag#UNCLEAN_TUPLES} is deliberately allowed through unchecked: it only records that the
     * original XML used compact notation (e.g. a {@code 0..4} range) for the value/tuple list --
     * {@code xcsp3-tools} has already expanded that into a plain, fully-materialized array by the
     * time it reaches {@code buildCtrExtension}, so there is nothing left here to reject.
     * Package-private (not private) so {@code Xcsp3CallbackHandlerTest} can exercise the {@link
     * TypeFlag#SMART_TUPLES} branch directly -- real "smart" tuple syntax is obscure enough that no
     * hand-authored XCSP3 fixture in {@code Xcsp3ParserTest} exercises it, the same reasoning
     * {@link #applySumCondition}'s own comment gives for its package-private visibility.
     */
    static void requireNoUnsupportedFlags(Set<TypeFlag> flags, String id) {
        if (flags.contains(TypeFlag.STARRED_TUPLES) || flags.contains(TypeFlag.SMART_TUPLES)) {
            throw new UnsupportedXcsp3ConstraintException("Starred/smart extension tuples are not supported: " + id);
        }
    }

    // ---- allDifferent -----------------------------------------------------------------------------

    @Override
    public void buildCtrAllDifferent(String id, XVarInteger[] list) {
        addOrReify(AllDiffConstraint.builder().variables(toVariableSet(list)).build(), id);
    }

    /**
     * Every row and every column of {@code matrix} is all-different (the standard Latin-square
     * encoding) -- decomposes into one {@link AllDiffConstraint} per row plus one per column, added
     * directly rather than through {@link #addOrReify}: reifying "the whole matrix is all-different"
     * would need a single {@link Constraint} object standing for the conjunction of all of them, and
     * this codebase has no generic AND-of-constraints wrapper (see {@link #addOrReify}'s own Javadoc
     * on why {@code HALF_TO} reification is similarly out of scope for the same reason).
     */
    @Override
    public void buildCtrAllDifferentMatrix(String id, XVarInteger[][] matrix) {
        if (currentReification != null) {
            throw new UnsupportedXcsp3ConstraintException("Reified allDifferentMatrix is not supported: " + id);
        }
        for (XVarInteger[] row : matrix) {
            builder.allDiffConstraint(toVariableSet(row));
        }
        for (int col = 0; col < matrix[0].length; col++) {
            Set<Variable<Integer>> column = new LinkedHashSet<>();
            for (XVarInteger[] row : matrix) {
                column.add(variableFor(row[col]));
            }
            builder.allDiffConstraint(column);
        }
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
            addOrReify(SumBoundConstraint.of(vars, mapOperator(val.operator), (int) val.k), id);
        } else if (condition instanceof ConditionVar var) {
            if (currentReification != null) {
                throw new UnsupportedXcsp3ConstraintException("Reified sum with a variable target is not supported: " + id);
            }
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
            addOrReify(CountConstraint.of(vars, values[0], operator, n), id);
        } else {
            Set<Integer> valueSet = Arrays.stream(values).boxed().collect(Collectors.toCollection(LinkedHashSet::new));
            addOrReify(AmongConstraint.of(vars, valueSet, operator, n), id);
        }
    }

    // ---- nValues --------------------------------------------------------------------------------------------

    @Override
    public void buildCtrNValues(String id, XVarInteger[] list, Condition condition) {
        applyNValuesCondition(toVariableSet(list), condition, id);
    }

    /**
     * {@code nValueConstraint}'s {@code count} parameter is always a genuine {@link
     * Variable}, so a fresh auxiliary variable carries the distinct-value count regardless of
     * whether {@code condition} names one itself; the condition is then applied to that auxiliary
     * via {@link io.github.rcrida.jcsp.constraints.Operator}-based comparison, the same two-step
     * shape {@link #shiftVariable} uses for an index shift. {@code nValueConstraint} itself (the
     * {@code count}-to-distinct-values link) is always added directly, never reified -- only the
     * condition <em>comparing</em> {@code count} is a meaningful thing to reify ({@code b <-> count
     * <op> k}); the link itself is a definition, not a proposition with a truth value of its own.
     */
    void applyNValuesCondition(Set<Variable<Integer>> vars, Condition condition, String id) {
        Variable<Integer> count = Variable.Factory.INSTANCE.create(id + "$nvalues");
        builder.variableDomain(count, IntRangeDomain.of(1, vars.size()));
        builder.nValueConstraint(vars, count);
        if (condition instanceof ConditionVal val) {
            addOrReify(UnaryComparatorConstraint.of(count, mapOperator(val.operator), (int) val.k), id);
        } else if (condition instanceof ConditionVar var) {
            addOrReify(BinaryComparatorConstraint.of(count, mapOperator(var.operator), variableFor(var.x)), id);
        } else {
            throw new UnsupportedXcsp3ConstraintException("Unsupported nValues condition: " + id);
        }
    }

    // ---- cardinality (global cardinality constraint) ---------------------------------------------------------

    @Override
    public void buildCtrCardinality(String id, XVarInteger[] list, boolean closed, int[] values, int[] occurs) {
        if (closed && !closedCoveredByEveryDomain(list, values)) {
            throw new UnsupportedXcsp3ConstraintException(
                    "closed cardinality referencing a value outside some variable's domain is not supported: " + id);
        }
        Map<Integer, Integer> cardinalities = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i++) {
            cardinalities.put(values[i], occurs[i]);
        }
        addOrReify(GlobalCardinalityConstraint.of(toVariableSet(list), cardinalities), id);
    }

    @Override
    public void buildCtrCardinality(String id, XVarInteger[] list, boolean closed, int[] values, XVarInteger[] occurs) {
        throw new UnsupportedXcsp3ConstraintException("cardinality with variable occurrence counts is not supported: " + id);
    }

    @Override
    public void buildCtrCardinality(String id, XVarInteger[] list, boolean closed, int[] values, int[] occursMin, int[] occursMax) {
        throw new UnsupportedXcsp3ConstraintException("cardinality with a min/max occurrence range is not supported: " + id);
    }

    @Override
    public void buildCtrCardinality(String id, XVarInteger[] list, boolean closed, XVarInteger[] values, XVarInteger[] occurs) {
        throw new UnsupportedXcsp3ConstraintException("cardinality with variable-valued values/occurrences is not supported: " + id);
    }

    @Override
    public void buildCtrCardinality(String id, XVarInteger[] list, boolean closed, XVarInteger[] values, int[] occurs) {
        throw new UnsupportedXcsp3ConstraintException("cardinality with variable-valued values is not supported: " + id);
    }

    @Override
    public void buildCtrCardinality(String id, XVarInteger[] list, boolean closed, XVarInteger[] values, int[] occursMin, int[] occursMax) {
        throw new UnsupportedXcsp3ConstraintException("cardinality with variable-valued values is not supported: " + id);
    }

    /**
     * {@code globalCardinalityConstraint} leaves values outside its map unconstrained (open GCC —
     * see that class's own Javadoc), so a {@code closed="true"} cardinality (values outside the
     * map forbidden) is only safe to accept when every {@code list} variable's declared domain is
     * already a subset of {@code values} -- in which case no other value could ever appear anyway,
     * and open vs. closed is moot. Conservative for a non-contiguous ({@link
     * #buildVarInteger(XVarInteger, int[])}) domain: checks every integer in {@code [min, max]}
     * rather than the domain's actual (possibly sparse) value set, so a sparse domain can be
     * rejected even when every value it actually contains is covered.
     */
    private boolean closedCoveredByEveryDomain(XVarInteger[] list, int[] values) {
        Set<Integer> valueSet = Arrays.stream(values).boxed().collect(Collectors.toSet());
        for (XVarInteger v : list) {
            int[] bounds = boundsByName.get(v.id());
            for (int value = bounds[0]; value <= bounds[1]; value++) {
                if (!valueSet.contains(value)) return false;
            }
        }
        return true;
    }

    // ---- element ------------------------------------------------------------------------------------------

    @Override
    public void buildCtrElement(String id, XVarInteger[] list, int startIndex, XVarInteger index, TypeRank rank, Condition condition) {
        requireAnyRank(rank, id);
        Variable<Integer> result = elementResult(condition, id);
        addOrReify(NaryElementConstraint.of(oneBasedIndex(index, startIndex), result, toVariableList(list)), id);
    }

    @Override
    public void buildCtrElement(String id, int[] list, int startIndex, XVarInteger index, TypeRank rank, Condition condition) {
        requireAnyRank(rank, id);
        Variable<Integer> result = elementResult(condition, id);
        List<Integer> values = Arrays.stream(list).boxed().toList();
        addOrReify(BinaryElementConstraint.of(oneBasedIndex(index, startIndex), result, values), id);
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
        addOrReify(OrderedConstraint.of(toVariableList(list), mapOrderingOperator(operator)), id);
    }

    /**
     * For exactly two lists, a single {@link LexConstraint} covers it (and can be reified through
     * {@link #addOrReify} like any other single-object constraint). For more than two, XCSP3
     * applies {@code operator} to every consecutive pair -- {@code lists[0] <op> lists[1] <op> ...
     * <op> lists[n-1]} -- which decomposes cleanly into {@code lists.length - 1} pairwise {@link
     * LexConstraint}s, added directly rather than through {@link #addOrReify}: like {@link
     * #buildCtrAllDifferentMatrix} and {@code instantiation}, reifying "the whole chain holds" as
     * one proposition would need a generic AND-of-constraints wrapper this codebase doesn't have.
     */
    @Override
    public void buildCtrLex(String id, XVarInteger[][] lists, TypeOperatorRel operator) {
        Operator op = mapOrderingOperator(operator);
        if (lists.length == 2) {
            addOrReify(LexConstraint.of(toVariableList(lists[0]), op, toVariableList(lists[1])), id);
            return;
        }
        if (currentReification != null) {
            throw new UnsupportedXcsp3ConstraintException("Reified lex with more than two lists is not supported: " + id);
        }
        for (int i = 0; i + 1 < lists.length; i++) {
            builder.constraint(LexConstraint.of(toVariableList(lists[i]), op, toVariableList(lists[i + 1])));
        }
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
        addOrReify(CumulativeConstraint.of(starts, durations, resources, (int) val.k), id);
    }

    // ---- circuit ----------------------------------------------------------------------------------------------

    @Override
    public void buildCtrCircuit(String id, XVarInteger[] list, int startIndex) {
        int offset = 1 - startIndex;
        List<Variable<Integer>> successors = Arrays.stream(list).map(v -> shiftVariable(v, offset)).toList();
        addOrReify(CircuitConstraint.of(successors), id);
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
        addOrReify(BinPackingConstraint.of(bins, weights, capacities), id);
    }

    // ---- instantiation ----------------------------------------------------------------------------------------

    /**
     * Pins {@code list[i] == values[i]} for every {@code i} -- a fixed (possibly partial) assignment,
     * typically used for a puzzle's "givens". Decomposes into one {@code equalsConstraint} per pair
     * rather than narrowing each variable's domain directly, since {@link #builder} has already
     * accepted that variable's original declared domain by the time this callback runs. Added
     * directly rather than through {@link #addOrReify} -- like {@link
     * #buildCtrAllDifferentMatrix}, reifying "the whole fixed assignment holds" as one proposition
     * would need a generic AND-of-constraints wrapper this codebase doesn't have.
     */
    @Override
    public void buildCtrInstantiation(String id, XVarInteger[] list, int[] values) {
        if (currentReification != null) {
            throw new UnsupportedXcsp3ConstraintException("Reified instantiation is not supported: " + id);
        }
        List<Variable<Integer>> vars = toVariableList(list);
        for (int i = 0; i < vars.size(); i++) {
            builder.equalsConstraint(vars.get(i), values[i]);
        }
    }

    // ---- objectives -----------------------------------------------------------------------------------------------

    @Override
    public void buildObjToMinimize(String id, XVarInteger x) {
        objective = LinearObjective.builder().coefficient(variableFor(x), 1.0).build();
        maximize = false;
    }

    @Override
    public void buildObjToMaximize(String id, XVarInteger x) {
        // Minimizing -x is the same problem as maximizing x; see buildSumObjective's own Javadoc
        // for why negating the coefficient at construction (keeping this a genuine LinearObjective)
        // rather than wrapping it in a negating lambda is what makes this sound and LP-fast-path-eligible.
        objective = LinearObjective.builder().coefficient(variableFor(x), -1.0).build();
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
        objective = buildArrayObjective(id, type, list, null, false);
        maximize = false;
    }

    @Override
    public void buildObjToMaximize(String id, TypeObjective type, XVarInteger[] list) {
        objective = buildArrayObjective(id, type, list, null, true);
        maximize = true;
    }

    @Override
    public void buildObjToMinimize(String id, TypeObjective type, XVarInteger[] list, int[] coeffs) {
        objective = buildArrayObjective(id, type, list, coeffs, false);
        maximize = false;
    }

    @Override
    public void buildObjToMaximize(String id, TypeObjective type, XVarInteger[] list, int[] coeffs) {
        objective = buildArrayObjective(id, type, list, coeffs, true);
        maximize = true;
    }

    /**
     * Dispatches a {@code <minimize>}/{@code <maximize>} array objective by its {@link
     * TypeObjective} aggregation kind: {@link TypeObjective#SUM} via {@link #buildSumObjective},
     * {@link TypeObjective#MAXIMUM} via {@link #buildMaxObjective}. Every other kind ({@code
     * PRODUCT}, {@code MINIMUM}, {@code NVALUES}, {@code LEX}, {@code EXPRESSION}) is out of MVP
     * scope.
     */
    private LinearObjective buildArrayObjective(String id, TypeObjective type, XVarInteger[] list, int[] coeffs, boolean maximizing) {
        return switch (type) {
            case SUM -> buildSumObjective(list, coeffs, maximizing);
            case MAXIMUM -> buildMaxObjective(list, coeffs, maximizing);
            default -> throw new UnsupportedXcsp3ConstraintException(
                    "Only sum/maximum-type array objectives are supported, got: " + type);
        };
    }

    /**
     * Builds a {@link LinearObjective} from a {@code <minimize type="sum">}/{@code <maximize
     * type="sum">} array. For {@code maximize}, negates every coefficient at construction time
     * (minimizing {@code -sum} is the same problem as maximizing {@code sum}) rather than wrapping
     * a positively-signed {@link LinearObjective} in a lambda that negates its <em>result</em>: a
     * lambda is never {@code instanceof LinearObjective}, so {@link BranchAndBoundSolver} would
     * fall back to pruning directly off {@link LinearObjective#applyAsDouble}'s own "unassigned
     * contributes 0" convention -- sound for {@code minimize} under non-negative
     * coefficients/domains, but never sound once negated for {@code maximize} (the fill it would
     * need there is each variable's domain <em>maximum</em>, not zero, to stay a valid bound).
     * Negating the coefficients up front sidesteps that distinction entirely: {@link
     * io.github.rcrida.jcsp.solver.lp.LpModelBuilder}'s LP relaxation (which {@code instanceof
     * LinearObjective} unlocks in {@link BranchAndBoundSolver}) reads each variable's real domain
     * bounds directly from the {@link io.github.rcrida.jcsp.ConstraintSatisfactionProblem} rather
     * than going through {@code applyAsDouble}'s fill convention at all, so it's sound for any
     * coefficient or domain sign -- no non-negativity precondition needed for either direction.
     */
    private LinearObjective buildSumObjective(XVarInteger[] list, int[] coeffs, boolean maximizing) {
        LinearObjective.LinearObjectiveBuilder linearObjectiveBuilder = LinearObjective.builder();
        for (int i = 0; i < list.length; i++) {
            double coefficient = coeffs == null ? 1.0 : coeffs[i];
            linearObjectiveBuilder.coefficient(variableFor(list[i]), maximizing ? -coefficient : coefficient);
        }
        return linearObjectiveBuilder.build();
    }

    /**
     * Builds a {@link LinearObjective} from a {@code <minimize type="maximum">}/{@code <maximize
     * type="maximum">} array: a fresh auxiliary variable {@code max}, linked to {@code list} via
     * {@code maxConstraint(list, EQ, max)} (added directly to {@link #builder}, the same
     * "definition, not a proposition" pattern {@link #applyNValuesCondition} uses for its own
     * {@code count} auxiliary -- never reified), then minimized/maximized the same way a
     * single-variable objective would be. {@code max}'s own domain bounds are the tightest
     * a-priori range {@code max(list)} can actually take -- {@code [max_i(min_i), max_i(max_i)]},
     * i.e. the largest of every listed variable's own minimum through the largest of every listed
     * variable's own maximum; see {@link MaxVariableConstraint#propagate}'s own Javadoc for why
     * that's the exact achievable range rather than just a loose over-approximation. The auxiliary
     * variable's name is a fixed {@code "$max"} rather than {@code id + "$max"} the way {@link
     * #applyNValuesCondition}'s own auxiliary is derived from its constraint's {@code id} --
     * unlike a constraint, an XCSP3 objective doesn't reliably carry one ({@code id} is {@code
     * null} for an objective with no explicit {@code id} attribute), and a fixed name is safe
     * regardless since XCSP3 permits at most one objective per instance.
     */
    private LinearObjective buildMaxObjective(XVarInteger[] list, int[] coeffs, boolean maximizing) {
        if (coeffs != null) {
            throw new UnsupportedXcsp3ConstraintException("Weighted maximum-type array objectives are not supported");
        }
        int maxLo = Arrays.stream(list).mapToInt(v -> boundsByName.get(v.id())[0]).max().orElseThrow();
        int maxHi = Arrays.stream(list).mapToInt(v -> boundsByName.get(v.id())[1]).max().orElseThrow();
        Variable<Integer> max = Variable.Factory.INSTANCE.create("$max");
        builder.variableDomain(max, IntRangeDomain.of(maxLo, maxHi));
        builder.maxConstraint(toVariableSet(list), Operator.EQ, max);
        return LinearObjective.builder().coefficient(max, maximizing ? -1.0 : 1.0).build();
    }
}

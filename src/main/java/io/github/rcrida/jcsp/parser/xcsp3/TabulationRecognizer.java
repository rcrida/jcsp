package io.github.rcrida.jcsp.parser.xcsp3;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.nary.NaryTuplesConstraint;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.xcsp.common.predicates.XNode;
import org.xcsp.parser.entries.XVariables.XVarInteger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Compiles a small-scope {@code <intension>} tree into an extensional {@link
 * NaryTuplesConstraint} by enumerating its scope's Cartesian product and keeping the satisfying
 * tuples, giving it real GAC instead of the auxiliary-heavy decomposition the recognizers after
 * this one would otherwise build.
 * <p>
 * Registered immediately before {@link AndRecognizer}/{@link OrRecognizer}, and that position is
 * the whole design. Every recognizer ahead of it produces a constraint that already propagates
 * ({@code BinaryComparatorConstraint}, {@code SumBoundConstraint}, {@link
 * RelationLogicConstraint}'s two-literal fast path, and so on), so those keep their cheaper,
 * table-free handling. Everything behind it reifies each operand into a fresh boolean indicator and
 * materializes {@code div}/{@code mod}/{@code dist} auxiliaries -- which is where jcsp was losing:
 * {@code KnightTour-06-int}'s 36 knight's-move constraints became 180 variables and 534,935 nodes
 * without a solution in 60s, against Choco compiling the same 36 into tables and solving in 0.52s
 * with 10,017 nodes.
 * <p>
 * Declining is always safe -- the chain simply continues to the decomposing recognizers, which is
 * exactly the behaviour before this class existed. It declines when any scope variable's domain
 * isn't enumerable, when the table would exceed {@link #MAX_INDEX_BITS}, or when no tuple satisfies
 * the predicate (an unsatisfiable constraint, left to the decomposition rather than represented as
 * an empty table, whose own {@code getVariables()} would be empty and so disconnected from the
 * constraint graph).
 * <p>
 * See {@code docs/adr/0034-tabulating-small-scope-intension-constraints.md} for the corpus
 * measurement behind the cap.
 */
final class TabulationRecognizer implements ConstraintRecognizer {

    /**
     * Ceiling on {@link NaryTuplesConstraint}'s support index, which is a bitset per (variable,
     * value) pair over tuple positions and so costs {@code sum(|domain|) * tuples} bits -- the
     * real memory driver, unlike a plain tuple count, which ignores both arity and domain width.
     * <p>
     * 1 MiB, derived from the bundled corpus rather than tuned: every constraint this recognizer
     * is reached for sits at 93,312 bits (the 71 knight's-move constraints of {@code
     * KnightTour-06-int}/{@code QueenAttacking-06}) or below, except one in {@code
     * Domino-300-300} at 54,000,000. Nothing lies between, so any cap in that range selects the
     * same set; this one sits an order of magnitude above what is needed and well below what is
     * refused.
     */
    static final long MAX_INDEX_BITS = 8L * 1024 * 1024;

    /**
     * Ceiling on the tuples this recognizer will materialize across one whole instance, after which
     * it declines everything and the decomposing recognizers take over again.
     * <p>
     * Needed because a per-constraint cap cannot bound an instance: what separated the corpus's
     * affordable case from its unaffordable one was never the size of a constraint but how many
     * there were -- 36 constraints of 1,296 tuples against 4,900 of 2,401, barely twice the size
     * each and 136 times as many. Without this, parsing {@code RoomMate-sr0050-int} exhausted a 4GB
     * heap. {@link NaryTuplesConstraint} holds each tuple as an {@link Assignment}, so tuple count,
     * not the support index, is what actually bounds memory here.
     */
    static final long MAX_TOTAL_TUPLES = 500_000;

    private final Xcsp3CallbackHandler handler;
    private final long totalTupleBudget;

    private long tuplesMaterialized;

    TabulationRecognizer(@NonNull Xcsp3CallbackHandler handler) {
        this(handler, MAX_TOTAL_TUPLES);
    }

    /** Budget-overridable constructor, so a test can exhaust {@link #MAX_TOTAL_TUPLES}'s branch
     *  without materializing half a million tuples to do it. */
    TabulationRecognizer(@NonNull Xcsp3CallbackHandler handler, long totalTupleBudget) {
        this.handler = handler;
        this.totalTupleBudget = totalTupleBudget;
    }

    @Override
    public Optional<Constraint> recognize(XNode<XVarInteger> tree) {
        List<Variable<Integer>> scope = new ArrayList<>();
        List<List<Integer>> values = new ArrayList<>();
        if (!collectScope(tree, scope, values)) return Optional.empty();

        long tuples = 1;
        long domainSum = 0;
        for (List<Integer> domain : values) {
            tuples *= domain.size();
            domainSum += domain.size();
            // Checked as each domain is folded in rather than once at the end: the product only
            // grows, so an early return is equivalent, and it also stops a wide scope overflowing
            // the multiplication before the ceiling is ever consulted.
            if (tuples * domainSum > MAX_INDEX_BITS) return Optional.empty();
        }
        if (tuplesMaterialized + tuples > totalTupleBudget) return Optional.empty();

        Predicate<Assignment> predicate = handler.intensionPredicate(tree);
        Set<Assignment> supports = new LinkedHashSet<>();
        enumerate(scope, values, 0, new LinkedHashMap<>(), predicate, supports);
        if (supports.isEmpty()) return Optional.empty();
        // Charged in candidates rather than kept supports, the same unit the check above uses:
        // enumeration walks every candidate whether it survives or not, and a budget spent in one
        // unit and tested in another reads as a bug even when it happens to be conservative.
        tuplesMaterialized += tuples;
        return Optional.of(NaryTuplesConstraint.of(supports));
    }

    /**
     * Fills {@code scope}/{@code values} with the tree's distinct variables and their enumerable
     * domains, or returns {@code false} when any of them has no enumerable domain -- a variable the
     * handler never registered a {@link DiscreteDomain} for, which a table cannot represent.
     */
    private boolean collectScope(XNode<XVarInteger> tree, List<Variable<Integer>> scope,
                                 List<List<Integer>> values) {
        // vars() is null, not empty, for a tree with no variables at all -- a constants-only
        // and(...)/or(...) reaches here and would otherwise NPE before the emptiness check.
        XVarInteger[] treeVars = tree.vars();
        if (treeVars == null) return false;
        Set<XVarInteger> seen = new LinkedHashSet<>(List.of(treeVars));
        for (XVarInteger x : seen) {
            Variable<Integer> variable = handler.variableForName(x.id());
            // Null only for a name the handler never registered as an integer variable; a
            // registered domain is never empty, so there is no emptiness case to guard here.
            DiscreteDomain<Integer> domain = variable == null ? null : handler.declaredDomain(variable);
            if (domain == null) return false;
            scope.add(variable);
            values.add(domain.toList());
        }
        return true;
    }

    /** Depth-first walk of the Cartesian product, keeping each tuple the predicate accepts. */
    private static void enumerate(List<Variable<Integer>> scope, List<List<Integer>> values, int index,
                                  Map<Variable<?>, Object> current, Predicate<Assignment> predicate,
                                  Set<Assignment> supports) {
        if (index == scope.size()) {
            // Assignment.of copies through the builder, so current stays safe to mutate on return
            // and no second defensive copy is needed here.
            Assignment tuple = Assignment.of(current);
            if (predicate.test(tuple)) supports.add(tuple);
            return;
        }
        Variable<Integer> variable = scope.get(index);
        for (Integer value : values.get(index)) {
            current.put(variable, value);
            enumerate(scope, values, index + 1, current, predicate, supports);
        }
        current.remove(variable);
    }
}

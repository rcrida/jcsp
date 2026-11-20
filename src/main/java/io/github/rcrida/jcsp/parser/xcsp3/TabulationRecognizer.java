package io.github.rcrida.jcsp.parser.xcsp3;

import io.github.rcrida.jcsp.assignments.Valuation;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.nary.NaryTuplesConstraint;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.xcsp.common.Types.TypeExpr;
import org.xcsp.common.predicates.XNode;
import org.xcsp.common.predicates.XNodeLeaf;
import org.xcsp.parser.entries.XVariables.XVarInteger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
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
 * isn't enumerable, when the table would exceed {@link #MAX_INDEX_BITS}, when the instance has spent
 * {@link #MAX_CANDIDATES_WALKED} or {@link #MAX_TUPLES_KEPT}, or when no tuple satisfies
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
     * value) pair over tuple positions and so costs {@code sum(|domain|) * tuples} bits for the
     * tuples it keeps -- the real memory driver, unlike a plain tuple count, which ignores both
     * arity and domain width. Checked after enumeration, since only then is the kept count known:
     * a sparse relation over wide domains, such as a knight's move on a 15x15 board, makes a small
     * table from many candidates.
     * <p>
     * 1 MiB. In the bundled corpus the tables this recognizer builds sit well below it: {@code
     * Knights-015-05}'s knight's-move tables at 655,200 bits each, {@code Domino-300-300}'s at
     * 180,000.
     */
    static final long MAX_INDEX_BITS = 8L * 1024 * 1024;

    /**
     * Ceiling on the tuples this recognizer keeps across one whole instance, after which it
     * declines every table and the decomposing recognizers take over again.
     * <p>
     * Needed because a per-constraint cap cannot bound an instance: what separated the corpus's
     * affordable case from its unaffordable one was never the size of a constraint but how many
     * there were -- 36 constraints of 1,296 tuples against 4,900 of 2,401, barely twice the size
     * each and 136 times as many. Without this, parsing {@code RoomMate-sr0050-int} exhausted a 4GB
     * heap. {@link NaryTuplesConstraint} holds each tuple as a {@link Valuation}, so tuple count,
     * not the support index, is what actually bounds memory here.
     */
    static final long MAX_TUPLES_KEPT = 500_000;

    /**
     * Ceiling on the candidate tuples this recognizer walks across one whole instance, kept or
     * not: the parsing time tabulation costs. Separate from {@link #MAX_TUPLES_KEPT} because a
     * sparse relation walks far more candidates than it keeps -- a knight's move on a 20x20 board
     * walks 160,000 for about 2,700 supports. Walking runs at roughly three million candidates a
     * second, so this is about a third of a second. A relation repeated over many scopes is walked
     * once, so this bounds the distinct relations, not the constraints.
     */
    static final long MAX_CANDIDATES_WALKED = 1_000_000;

    /**
     * Candidates a relation of more than {@link #SAMPLE_THRESHOLD} is tested on, at random, before it
     * is walked, to estimate how many supports the walk would keep.
     */
    static final int SAMPLE_SIZE = 4096;

    /** Relations up to this many candidates are walked without sampling: sampling would save little. */
    static final long SAMPLE_THRESHOLD = 4L * SAMPLE_SIZE;

    /**
     * Hits the sample needs before its estimate can decline a relation. With fewer, the estimate is
     * too noisy to stand on: at 32 hits, one twice the truth is a four-sigma event.
     */
    static final int MIN_SAMPLE_HITS = 32;

    /** Fixed, so a parse makes the same tables every time. */
    private static final long SAMPLE_SEED = 0x5EED;

    private final Xcsp3CallbackHandler handler;
    private final long candidateBudget;
    private final long keptBudget;

    /**
     * The supports of every relation walked so far, by {@link Relation}, values in scope order: an
     * XCSP3 {@code <group>} or {@code <slide>} states one relation over many scopes, which is walked
     * once and renamed onto each. Empty for a relation that makes no table.
     */
    private final Map<Relation, List<Integer[]>> relations = new HashMap<>();

    private long candidatesWalked;
    private long tuplesKept;

    TabulationRecognizer(@NonNull Xcsp3CallbackHandler handler) {
        this(handler, MAX_CANDIDATES_WALKED, MAX_TUPLES_KEPT);
    }

    /** Budget-overridable constructor, so a test can exhaust either budget without walking or
     *  keeping millions of tuples to do it. */
    TabulationRecognizer(@NonNull Xcsp3CallbackHandler handler, long candidateBudget, long keptBudget) {
        this.handler = handler;
        this.candidateBudget = candidateBudget;
        this.keptBudget = keptBudget;
    }

    @Override
    public Optional<Constraint> recognize(XNode<XVarInteger> tree) {
        Map<XVarInteger, Integer> positions = new LinkedHashMap<>();
        List<Variable<Integer>> scope = new ArrayList<>();
        List<List<Integer>> values = new ArrayList<>();
        if (!collectScope(tree, positions, scope, values)) return Optional.empty();

        Relation key = new Relation(shape(tree, positions, new StringBuilder()).toString(), values);
        List<Integer[]> supports = relations.get(key);
        if (supports == null) {
            long tuples = 1;
            for (List<Integer> domain : values) {
                tuples *= domain.size();
                // Checked as each domain is folded in rather than once at the end: the product only
                // grows, so an early return is equivalent, and it also stops a wide scope overflowing
                // the multiplication before the budget is ever consulted.
                if (candidatesWalked + tuples > candidateBudget) return Optional.empty();
            }
            Predicate<Valuation> predicate = handler.intensionPredicate(tree);
            long domainSum = values.stream().mapToLong(List::size).sum();
            if (tuples > SAMPLE_THRESHOLD) {
                candidatesWalked += SAMPLE_SIZE;
                // Room only shrinks as tables are kept, so a relation declined for either reason
                // would be declined again, and is remembered as having no table.
                long room = Math.min(MAX_INDEX_BITS / domainSum, keptBudget - tuplesKept);
                if (sampledSupports(scope, values, predicate, tuples) > 2.0 * room) {
                    relations.put(key, List.of());
                    return Optional.empty();
                }
            }
            supports = new ArrayList<>();
            enumerate(scope, values, 0, new LinkedHashMap<>(), new Integer[scope.size()], predicate, supports);
            // Charged whether or not the table is kept: the walk has been paid for either way.
            candidatesWalked += tuples;
            // The index is sized by the supports kept, not the candidates walked: a sparse relation
            // over wide domains, such as a knight's move, makes a small table. One over the ceiling
            // is remembered as having none, so its supports are not held for the rest of the parse.
            if (supports.size() * domainSum > MAX_INDEX_BITS) supports = List.of();
            relations.put(key, supports);
        }

        if (supports.isEmpty() || tuplesKept + supports.size() > keptBudget) return Optional.empty();
        tuplesKept += supports.size();
        return Optional.of(NaryTuplesConstraint.of(valuations(scope, supports)));
    }

    /**
     * Fills {@code positions}, {@code scope} and {@code values} with the tree's distinct variables,
     * in order, and their enumerable domains, or returns {@code false} when any of them has no
     * enumerable domain -- a variable the handler never registered a {@link DiscreteDomain} for,
     * which a table cannot represent.
     */
    private boolean collectScope(XNode<XVarInteger> tree, Map<XVarInteger, Integer> positions,
                                 List<Variable<Integer>> scope, List<List<Integer>> values) {
        // vars() is null, not empty, for a tree with no variables at all -- a constants-only
        // and(...)/or(...) reaches here and would otherwise NPE before the emptiness check.
        XVarInteger[] treeVars = tree.vars();
        if (treeVars == null) return false;
        for (XVarInteger x : new LinkedHashSet<>(List.of(treeVars))) {
            Variable<Integer> variable = handler.variableForName(x.id());
            // Null only for a name the handler never registered as an integer variable; a
            // registered domain is never empty, so there is no emptiness case to guard here.
            DiscreteDomain<Integer> domain = variable == null ? null : handler.declaredDomain(variable);
            if (domain == null) return false;
            positions.put(x, scope.size());
            scope.add(variable);
            values.add(domain.toList());
        }
        return true;
    }

    /**
     * {@code tree} written out with each variable replaced by its position in the scope, so two
     * trees that differ only in which variables they relate have the same shape.
     */
    private static StringBuilder shape(XNode<XVarInteger> tree, Map<XVarInteger, Integer> positions,
                                       StringBuilder out) {
        out.append(tree.getType()).append('(');
        if (tree instanceof XNodeLeaf<XVarInteger> leaf) {
            out.append(tree.getType() == TypeExpr.VAR ? positions.get((XVarInteger) leaf.value) : leaf.value);
        } else {
            for (XNode<XVarInteger> son : tree.sons) shape(son, positions, out).append(',');
        }
        return out.append(')');
    }

    /**
     * The supports a walk of {@code tuples} candidates would keep, estimated from {@link
     * #SAMPLE_SIZE} of them drawn at random, or zero when fewer than {@link #MIN_SAMPLE_HITS} of
     * those are supports: too few to estimate from, and a sparse relation is the kind worth walking.
     */
    private static double sampledSupports(List<Variable<Integer>> scope, List<List<Integer>> values,
                                          Predicate<Valuation> predicate, long tuples) {
        SplittableRandom random = new SplittableRandom(SAMPLE_SEED);
        Map<Variable<?>, Object> candidate = new LinkedHashMap<>();
        int hits = 0;
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            for (int position = 0; position < scope.size(); position++) {
                List<Integer> domain = values.get(position);
                candidate.put(scope.get(position), domain.get(random.nextInt(domain.size())));
            }
            if (predicate.test(Valuation.of(candidate))) hits++;
        }
        return hits < MIN_SAMPLE_HITS ? 0 : (double) hits / SAMPLE_SIZE * tuples;
    }

    /** Depth-first walk of the Cartesian product, keeping the values of each tuple the predicate accepts. */
    private static void enumerate(List<Variable<Integer>> scope, List<List<Integer>> values, int index,
                                  Map<Variable<?>, Object> current, Integer[] tupleValues,
                                  Predicate<Valuation> predicate, List<Integer[]> supports) {
        if (index == scope.size()) {
            if (predicate.test(Valuation.of(current))) supports.add(tupleValues.clone());
            return;
        }
        Variable<Integer> variable = scope.get(index);
        for (Integer value : values.get(index)) {
            current.put(variable, value);
            tupleValues[index] = value;
            enumerate(scope, values, index + 1, current, tupleValues, predicate, supports);
        }
        current.remove(variable);
    }

    /** {@code supports}, whose values are in {@code scope}'s order, as valuations over {@code scope}. */
    private static Set<Valuation> valuations(List<Variable<Integer>> scope, List<Integer[]> supports) {
        Set<Valuation> tuples = new LinkedHashSet<>();
        Map<Variable<?>, Object> tuple = new LinkedHashMap<>();
        for (Integer[] support : supports) {
            for (int i = 0; i < support.length; i++) tuple.put(scope.get(i), support[i]);
            tuples.add(Valuation.of(tuple));
        }
        return tuples;
    }

    /**
     * A relation as {@link #shape} writes it, over these domains in scope order: what the walk
     * depends on, so two constraints with the same key have the same supports, value for value.
     */
    private record Relation(String shape, List<List<Integer>> domains) {
    }
}

package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.LogicOperator;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.binary.BinaryLogicConstraint;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A {@link LogicOperator} connective (AND/OR/XOR/NAND/NOR/XNOR) applied to two <em>relations</em>
 * over general (typically integer) variables, rather than {@link BinaryLogicConstraint}'s two
 * plain {@code Variable<Boolean>} operands: {@code left <op> right}, where each side is a
 * {@link Literal} --
 * {@code variable == value}/{@code variable != value} ({@link ValueLiteral}), or {@code left ==
 * right}/{@code left != right} between two variables ({@link VariableLiteral}). {@link
 * BinaryLogicConstraint} itself isn't {@link Propagatable} at all (it relies entirely on generic
 * AC3 arc-consistency over its two small boolean domains); this class needs its own propagation
 * since its operands aren't single {@link io.github.rcrida.jcsp.domains.BooleanDomain} variables
 * AC3 can already reach directly.
 * <p>
 * Added for XCSP3's {@code or(...)} intension shape: corpus analysis of the bundled competition
 * instances found every real {@code or} node has exactly two children (never more), and just over
 * half of them have both children in this "literal" shape (a bare {@code eq}/{@code ne} between a
 * variable and a constant, or between two variables) -- previously falling all the way through to
 * the generic, unpropagated {@link PredicateConstraint}, the single largest source of unpropagated
 * intension constraints in that corpus. Modelled as a general {@link LogicOperator} connective
 * rather than an OR-only class since the propagation logic below is no harder to state generically,
 * and doing so gets AND/XOR/NAND/NOR/XNOR recognition "for free" should a future recognizer need
 * them. The remaining {@code or} nodes (at least one child a nested/compound expression, e.g.
 * {@code and(...)} or a {@code dist}-based relation) still fall back to {@link PredicateConstraint}
 * -- recognizing those would need a more general "OR of recursively-recognized sub-constraints"
 * mechanism, out of scope here.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class RelationLogicConstraint extends NaryConstraint implements Propagatable {

    /** A single operand of a {@link RelationLogicConstraint}: a relation with a boolean truth value. */
    public sealed interface Literal permits ValueLiteral, VariableLiteral {
        Set<Variable<?>> variables();

        boolean holds(Assignment assignment);

        Literal negate();
    }

    /** {@code variable == value} ({@link Operator#EQ}) or {@code variable != value} ({@link Operator#NEQ}). */
    public record ValueLiteral(Variable<?> variable, Operator operator, Object value) implements Literal {
        public ValueLiteral {
            assert operator == Operator.EQ || operator == Operator.NEQ : "ValueLiteral only supports EQ/NEQ";
        }

        @Override
        public Set<Variable<?>> variables() {
            return Set.of(variable);
        }

        @Override
        public boolean holds(@NonNull Assignment assignment) {
            boolean equal = assignment.getValue(variable).orElseThrow().equals(value);
            return operator == Operator.EQ ? equal : !equal;
        }

        @Override
        public Literal negate() {
            return new ValueLiteral(variable, operator == Operator.EQ ? Operator.NEQ : Operator.EQ, value);
        }
    }

    /** {@code left == right} ({@link Operator#EQ}) or {@code left != right} ({@link Operator#NEQ}). */
    public record VariableLiteral(Variable<?> left, Operator operator, Variable<?> right) implements Literal {
        public VariableLiteral {
            assert operator == Operator.EQ || operator == Operator.NEQ : "VariableLiteral only supports EQ/NEQ";
        }

        @Override
        public Set<Variable<?>> variables() {
            return Set.of(left, right);
        }

        @Override
        public boolean holds(@NonNull Assignment assignment) {
            boolean equal = assignment.getValue(left).orElseThrow().equals(assignment.getValue(right).orElseThrow());
            return operator == Operator.EQ ? equal : !equal;
        }

        @Override
        public Literal negate() {
            return new VariableLiteral(left, operator == Operator.EQ ? Operator.NEQ : Operator.EQ, right);
        }
    }

    @Getter @NonNull private final Literal left;
    @Getter @NonNull private final LogicOperator operator;
    @Getter @NonNull private final Literal right;

    public static RelationLogicConstraint of(@NonNull Literal left, @NonNull LogicOperator operator, @NonNull Literal right) {
        Set<Variable<?>> allVariables = new LinkedHashSet<>();
        allVariables.addAll(left.variables());
        allVariables.addAll(right.variables());
        return RelationLogicConstraint.builder()
                .variables(allVariables)
                .left(left).operator(operator).right(right)
                .build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        if (!assignment.getValues().keySet().containsAll(getVariables())) return true;
        return operator.apply(left.holds(assignment), right.holds(assignment));
    }

    private enum LiteralStatus { SATISFIED, FALSIFIED, UNDETERMINED }

    /**
     * Classifies a single literal's truth value against its variable(s)' current domains, the same
     * satisfied/falsified/undetermined shape every {@link NogoodConstraint} implementation already
     * uses for unit propagation. Degrades to {@link LiteralStatus#UNDETERMINED} for a non-{@link
     * DiscreteDomain} side (no information, never wrongly resolved), matching {@link
     * ValueSetNogoodConstraint}/{@link RangeNogoodConstraint}'s own graceful-degradation contract.
     * <p>
     * A {@link VariableLiteral} is falsified for {@link Operator#EQ} (resp. satisfied for {@link
     * Operator#NEQ}) exactly when the two domains share no common value at all -- no assignment
     * drawn from the current domains could ever make them equal -- and satisfied for {@code EQ}
     * (resp. falsified for {@code NEQ}) only when both are already singleton at the same value, the
     * one case a plain domain-overlap check can't capture.
     */
    @SuppressWarnings("unchecked")
    private static LiteralStatus classify(Literal literal, Map<Variable<?>, Domain<?>> domains) {
        if (literal instanceof ValueLiteral valueLiteral) {
            Domain<Object> domain = (Domain<Object>) domains.get(valueLiteral.variable());
            if (!(domain instanceof DiscreteDomain<Object> discrete)) return LiteralStatus.UNDETERMINED;
            boolean contains = discrete.contains(valueLiteral.value());
            if (valueLiteral.operator() == Operator.EQ) {
                if (!contains) return LiteralStatus.FALSIFIED;
                return discrete.isSingleton() ? LiteralStatus.SATISFIED : LiteralStatus.UNDETERMINED;
            }
            if (!contains) return LiteralStatus.SATISFIED;
            return discrete.isSingleton() ? LiteralStatus.FALSIFIED : LiteralStatus.UNDETERMINED;
        }

        VariableLiteral variableLiteral = (VariableLiteral) literal;
        Domain<Object> leftDomain = (Domain<Object>) domains.get(variableLiteral.left());
        Domain<Object> rightDomain = (Domain<Object>) domains.get(variableLiteral.right());
        if (!(leftDomain instanceof DiscreteDomain<Object> leftDiscrete)
                || !(rightDomain instanceof DiscreteDomain<Object> rightDiscrete)) {
            return LiteralStatus.UNDETERMINED;
        }
        List<Object> leftValues = leftDiscrete.toList();
        boolean disjoint = rightDiscrete.stream().noneMatch(leftValues::contains);
        boolean bothSingletonEqual = leftDiscrete.isSingleton() && rightDiscrete.isSingleton()
                && leftDiscrete.singleValue().equals(rightDiscrete.singleValue());
        if (variableLiteral.operator() == Operator.EQ) {
            if (disjoint) return LiteralStatus.FALSIFIED;
            return bothSingletonEqual ? LiteralStatus.SATISFIED : LiteralStatus.UNDETERMINED;
        }
        if (disjoint) return LiteralStatus.SATISFIED;
        return bothSingletonEqual ? LiteralStatus.FALSIFIED : LiteralStatus.UNDETERMINED;
    }

    /**
     * Generic truth-table propagation, the same reasoning for all six {@link LogicOperator}
     * variants: once each literal's current status settles it to a known boolean (or leaves it
     * undetermined), {@code operator.apply(...)} says exactly what's required.
     * <ul>
     *   <li>Both literals decided: the connective's actual truth value is now fixed -- infeasible
     *       if it's {@code false}, otherwise a no-op (nothing left to narrow).</li>
     *   <li>Exactly one decided: test the fixed side against both hypothetical values of the
     *       undetermined side. Neither works -- infeasible. Both work -- no-op (the undetermined
     *       side is unconstrained by this connective). Exactly one works -- {@link #force} the
     *       undetermined literal to that value.</li>
     *   <li>Neither decided: nothing to derive from either side alone -- no-op.</li>
     * </ul>
     */
    @Override
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        LiteralStatus leftStatus = classify(left, domains);
        LiteralStatus rightStatus = classify(right, domains);
        boolean leftDecided = leftStatus != LiteralStatus.UNDETERMINED;
        boolean rightDecided = rightStatus != LiteralStatus.UNDETERMINED;

        if (leftDecided && rightDecided) {
            boolean ok = operator.apply(leftStatus == LiteralStatus.SATISFIED, rightStatus == LiteralStatus.SATISFIED);
            return ok ? Optional.of(Map.of()) : Optional.empty();
        }
        if (leftDecided) {
            return forceOrNoop(leftStatus == LiteralStatus.SATISFIED, true, right, domains);
        }
        if (rightDecided) {
            return forceOrNoop(rightStatus == LiteralStatus.SATISFIED, false, left, domains);
        }
        return Optional.of(Map.of());
    }

    /**
     * Tests the undetermined literal's two hypothetical truth values against the already-decided
     * side, and narrows via {@link #force} only when exactly one of them keeps {@link #operator}
     * satisfied.
     */
    private Optional<Map<Variable<?>, Domain<?>>> forceOrNoop(
            boolean fixedValue, boolean fixedIsLeft, Literal undetermined, Map<Variable<?>, Domain<?>> domains) {
        boolean satisfiesTrue = fixedIsLeft ? operator.apply(fixedValue, true) : operator.apply(true, fixedValue);
        boolean satisfiesFalse = fixedIsLeft ? operator.apply(fixedValue, false) : operator.apply(false, fixedValue);
        if (!satisfiesTrue && !satisfiesFalse) return Optional.empty();
        if (satisfiesTrue && satisfiesFalse) return Optional.of(Map.of());
        return Optional.of(force(satisfiesTrue ? undetermined : undetermined.negate(), domains));
    }

    /**
     * Forces {@code literal} to hold, given it's the one side left undetermined once the other is
     * decided. A {@link ValueLiteral} narrows straight to (EQ) or away from (NEQ) its value, the
     * same as {@link ValueDisjunctionConstraint}/{@link GroundNogoodConstraint}'s own forcing. A
     * {@link VariableLiteral} narrows both sides to their intersection for EQ; for NEQ it can only
     * force when one side is already singleton (deleting that value from the other side), the same
     * partial-forcing limit {@link io.github.rcrida.jcsp.constraints.binary.BinaryComparatorConstraint}'s
     * own NEQ handling has -- an inequality between two open domains isn't expressible as a single
     * narrowed domain on either side.
     * <p>
     * Never empties a domain: {@link #classify} having returned {@link LiteralStatus#UNDETERMINED}
     * for {@code literal} (before any negation here) already guarantees every narrowing branch below
     * keeps at least one value on each side it touches -- see this method's own reasoning per
     * branch. {@code literal} may itself be a negation of the one {@link #classify} examined
     * (produced by {@link Literal#negate()}), which flips only {@link ValueLiteral}/{@link
     * VariableLiteral}'s {@code operator} field between {@link Operator#EQ}/{@link Operator#NEQ} --
     * the exact same case-split {@link #classify} already reasons about for whichever operator ends
     * up here, so the emptiness argument holds regardless of which of the two was actually classified.
     */
    @SuppressWarnings("unchecked")
    private static Map<Variable<?>, Domain<?>> force(Literal literal, Map<Variable<?>, Domain<?>> domains) {
        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        if (literal instanceof ValueLiteral valueLiteral) {
            // UNDETERMINED already established the value is present and the domain isn't singleton
            // at it (EQ) or is present and the domain isn't singleton (NEQ) -- either way at least
            // one value is always deleted below, so unconditionally recording the narrowed domain
            // (rather than guarding on a "did anything change" flag) is provably safe, not an
            // approximation, the same reasoning ValueSetNogoodConstraint#propagate uses.
            DiscreteDomain<Object> domain = (DiscreteDomain<Object>) domains.get(valueLiteral.variable());
            DiscreteDomain.Builder<Object> builder = domain.toBuilder();
            for (Object value : domain.toList()) {
                boolean keep = valueLiteral.operator() == Operator.EQ
                        ? value.equals(valueLiteral.value())
                        : !value.equals(valueLiteral.value());
                if (!keep) builder.delete(value);
            }
            updated.put(valueLiteral.variable(), builder.build());
            return updated;
        }

        VariableLiteral variableLiteral = (VariableLiteral) literal;
        DiscreteDomain<Object> leftDomain = (DiscreteDomain<Object>) domains.get(variableLiteral.left());
        DiscreteDomain<Object> rightDomain = (DiscreteDomain<Object>) domains.get(variableLiteral.right());
        if (variableLiteral.operator() == Operator.EQ) {
            // Each side's own narrowing here is a genuine "maybe": UNDETERMINED only guarantees the
            // intersection is non-empty, not that either side already equals it, so a side already
            // at (or inside) the intersection legitimately narrows to nothing.
            Set<Object> rightValues = new HashSet<>(rightDomain.toList());
            DiscreteDomain.Builder<Object> leftBuilder = leftDomain.toBuilder();
            boolean leftChanged = false;
            for (Object value : leftDomain.toList()) {
                if (!rightValues.contains(value)) {
                    leftBuilder.delete(value);
                    leftChanged = true;
                }
            }
            if (leftChanged) updated.put(variableLiteral.left(), leftBuilder.build());

            Set<Object> leftValues = new HashSet<>(leftDomain.toList());
            DiscreteDomain.Builder<Object> rightBuilder = rightDomain.toBuilder();
            boolean rightChanged = false;
            for (Object value : rightDomain.toList()) {
                if (!leftValues.contains(value)) {
                    rightBuilder.delete(value);
                    rightChanged = true;
                }
            }
            if (rightChanged) updated.put(variableLiteral.right(), rightBuilder.build());
            return updated;
        }

        // NEQ: UNDETERMINED rules out "both singleton and equal" and "disjoint", so a singleton
        // side's lone value is always still present in the other (non-singleton) side -- the
        // deletion below always fires, so (unlike the EQ case above) no "did it change" guard is
        // needed, the same reasoning the ValueLiteral branch above already relies on.
        if (leftDomain.isSingleton()) {
            Object value = leftDomain.singleValue().orElseThrow();
            updated.put(variableLiteral.right(), rightDomain.toBuilder().delete(value).build());
        }
        if (rightDomain.isSingleton()) {
            Object value = rightDomain.singleValue().orElseThrow();
            updated.put(variableLiteral.left(), leftDomain.toBuilder().delete(value).build());
        }
        return updated;
    }

    /**
     * Reached only when every literal's current domain(s) already rule out the required connective
     * value -- the same always-sound "cite every involved variable's exact current value set"
     * fallback {@link ValueDisjunctionConstraint#explainInfeasible} uses: whichever combination of
     * classified statuses triggered infeasibility, no assignment drawn from these variables' current
     * domains satisfies {@link #operator} over {@link #left}/{@link #right}, which is exactly what a
     * citation over their current value sets asserts.
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        return ValueSetNogoodConstraint.fromCurrentState(getVariables(), domains);
    }

    @Override
    public String getRelation() {
        return describe(left) + " " + operator.symbol + " " + describe(right);
    }

    private static String describe(Literal literal) {
        if (literal instanceof ValueLiteral v) {
            return v.variable() + " " + v.operator().symbol + " " + v.value();
        }
        VariableLiteral v = (VariableLiteral) literal;
        return v.left() + " " + v.operator().symbol + " " + v.right();
    }
}

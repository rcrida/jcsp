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
 * {@link Literal} -- an equality/ordering comparison between a variable and a value ({@link
 * ValueLiteral}) or between two variables ({@link VariableLiteral}), for any {@link Operator}
 * (EQ/NEQ/LT/LEQ/GT/GEQ). {@link BinaryLogicConstraint} itself isn't {@link Propagatable} at all
 * (it relies entirely on generic AC3 arc-consistency over its two small boolean domains); this
 * class needs its own propagation since its operands aren't single {@link
 * io.github.rcrida.jcsp.domains.BooleanDomain} variables AC3 can already reach directly.
 * <p>
 * Added for XCSP3's {@code or(...)} intension shape: corpus analysis of the bundled competition
 * instances found every real {@code or} node has exactly two children (never more), and just over
 * half of them have both children in this "literal" shape (a bare {@code eq}/{@code ne} between a
 * variable and a constant, or between two variables) -- previously falling all the way through to
 * the generic, unpropagated {@link PredicateConstraint}, the single largest source of unpropagated
 * intension constraints in that corpus. Ordering operators ({@code lt}/{@code le}/{@code ge}/{@code
 * gt}) were added afterwards for the same reason -- a single competition instance
 * ({@code RoomMate-sr0050-int.xml.lzma}) alone has thousands of {@code or}-of-ordering-literals
 * clauses (preference-ranking encodings) that previously fell to {@link PredicateConstraint} too.
 * Modelled as a general {@link LogicOperator} connective rather than an OR-only class since the
 * propagation logic below is no harder to state generically, and doing so gets AND/XOR/NAND/NOR/
 * XNOR recognition "for free" should a future recognizer need them. The remaining {@code or} nodes
 * (at least one child a nested/compound expression, e.g. {@code and(...)} or a {@code dist}-based
 * relation) still fall back to {@link PredicateConstraint} -- recognizing those would need a more
 * general "OR of recursively-recognized sub-constraints" mechanism, out of scope here.
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

    /**
     * {@code variable <op> value} for any {@link Operator} -- {@code EQ}/{@code NEQ} via {@link
     * Object#equals}, the four ordering operators via {@code value}'s natural {@link Comparable}
     * ordering (the same runtime contract {@link Operator#compare} itself relies on: {@code value}
     * must actually implement {@link Comparable} whenever {@code operator} is an ordering one, or
     * {@link #holds}/propagation throw {@link ClassCastException} -- unchecked at construction time,
     * same as every other {@code Object}-typed comparison in this class).
     */
    public record ValueLiteral(Variable<?> variable, Operator operator, Object value) implements Literal {
        @Override
        public Set<Variable<?>> variables() {
            return Set.of(variable);
        }

        @Override
        public boolean holds(@NonNull Assignment assignment) {
            return operator.compare(assignment.getValue(variable).orElseThrow(), value);
        }

        @Override
        public Literal negate() {
            return new ValueLiteral(variable, negatedOperator(operator), value);
        }
    }

    /**
     * {@code left <op> right} for any {@link Operator} -- {@code EQ}/{@code NEQ} via {@link
     * Object#equals}, the four ordering operators via both variables' natural {@link Comparable}
     * ordering (see {@link ValueLiteral}'s own runtime-contract note).
     */
    public record VariableLiteral(Variable<?> left, Operator operator, Variable<?> right) implements Literal {
        @Override
        public Set<Variable<?>> variables() {
            return Set.of(left, right);
        }

        @Override
        public boolean holds(@NonNull Assignment assignment) {
            return operator.compare(assignment.getValue(left).orElseThrow(), assignment.getValue(right).orElseThrow());
        }

        @Override
        public Literal negate() {
            return new VariableLiteral(left, negatedOperator(operator), right);
        }
    }

    /**
     * Logical complement of {@code operator} as a whole relation -- {@code EQ}/{@code NEQ} swap
     * directly (unlike {@link Operator#reversed()}, which deliberately maps both to themselves; see
     * that method's own tests), while the four ordering operators use {@link Operator#reversed()}
     * as-is, since for a total order exactly one of {@code op}/{@code op.reversed()} holds for any
     * pair -- {@code NOT(a < b) == (a >= b)}, etc.
     */
    private static Operator negatedOperator(Operator operator) {
        return switch (operator) {
            case EQ -> Operator.NEQ;
            case NEQ -> Operator.EQ;
            case LT, GT, LEQ, GEQ -> operator.reversed();
        };
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
     */
    private static LiteralStatus classify(Literal literal, Map<Variable<?>, Domain<?>> domains) {
        if (literal instanceof ValueLiteral valueLiteral) {
            return classifyValueLiteral(valueLiteral, domains);
        }
        return classifyVariableLiteral((VariableLiteral) literal, domains);
    }

    @SuppressWarnings("unchecked")
    private static LiteralStatus classifyValueLiteral(ValueLiteral valueLiteral, Map<Variable<?>, Domain<?>> domains) {
        Domain<Object> domain = (Domain<Object>) domains.get(valueLiteral.variable());
        if (!(domain instanceof DiscreteDomain<Object> discrete)) return LiteralStatus.UNDETERMINED;
        Operator operator = valueLiteral.operator();
        Object value = valueLiteral.value();
        if (operator == Operator.EQ || operator == Operator.NEQ) {
            boolean contains = discrete.contains(value);
            if (operator == Operator.EQ) {
                if (!contains) return LiteralStatus.FALSIFIED;
                return discrete.isSingleton() ? LiteralStatus.SATISFIED : LiteralStatus.UNDETERMINED;
            }
            if (!contains) return LiteralStatus.SATISFIED;
            return discrete.isSingleton() ? LiteralStatus.FALSIFIED : LiteralStatus.UNDETERMINED;
        }
        // Ordering operator: the domain-wide extreme most favourable to satisfying (resp.
        // falsifying) the comparison settles SATISFIED (resp. FALSIFIED) whenever it alone already
        // does -- see classifyVariableLiteral's own Javadoc for the downward/upward duality this
        // and that method share.
        Object satisfiedExtreme = isDownward(operator) ? max(discrete) : min(discrete);
        if (operator.compare(satisfiedExtreme, value)) return LiteralStatus.SATISFIED;
        Object falsifiedExtreme = isDownward(operator) ? min(discrete) : max(discrete);
        if (operator.reversed().compare(falsifiedExtreme, value)) return LiteralStatus.FALSIFIED;
        return LiteralStatus.UNDETERMINED;
    }

    /**
     * A {@link VariableLiteral} is falsified for {@link Operator#EQ} (resp. satisfied for {@link
     * Operator#NEQ}) exactly when the two domains share no common value at all -- no assignment
     * drawn from the current domains could ever make them equal -- and satisfied for {@code EQ}
     * (resp. falsified for {@code NEQ}) only when both are already singleton at the same value, the
     * one case a plain domain-overlap check can't capture.
     * <p>
     * For an ordering operator, the analogous "extreme pairing" reasoning applies: the least
     * favourable pairing for satisfying the comparison ({@code (leftMax, rightMin)} for {@code
     * LT}/{@code LEQ}, {@code (leftMin, rightMax)} for {@code GT}/{@code GEQ} -- see {@link
     * #isDownward}) still satisfying it means every other pairing does too (widening either side's
     * contribution only makes the comparison easier), settling {@link LiteralStatus#SATISFIED}; the
     * dual, most favourable pairing failing (checked via {@link Operator#reversed()}, sound for a
     * total order: exactly one of {@code op}/{@code op.reversed()} holds for any pair) settles
     * {@link LiteralStatus#FALSIFIED}.
     */
    @SuppressWarnings("unchecked")
    private static LiteralStatus classifyVariableLiteral(VariableLiteral variableLiteral, Map<Variable<?>, Domain<?>> domains) {
        Domain<Object> leftDomain = (Domain<Object>) domains.get(variableLiteral.left());
        Domain<Object> rightDomain = (Domain<Object>) domains.get(variableLiteral.right());
        if (!(leftDomain instanceof DiscreteDomain<Object> leftDiscrete)
                || !(rightDomain instanceof DiscreteDomain<Object> rightDiscrete)) {
            return LiteralStatus.UNDETERMINED;
        }
        Operator operator = variableLiteral.operator();
        if (operator == Operator.EQ || operator == Operator.NEQ) {
            List<Object> leftValues = leftDiscrete.toList();
            boolean disjoint = rightDiscrete.stream().noneMatch(leftValues::contains);
            boolean bothSingletonEqual = leftDiscrete.isSingleton() && rightDiscrete.isSingleton()
                    && leftDiscrete.singleValue().equals(rightDiscrete.singleValue());
            if (operator == Operator.EQ) {
                if (disjoint) return LiteralStatus.FALSIFIED;
                return bothSingletonEqual ? LiteralStatus.SATISFIED : LiteralStatus.UNDETERMINED;
            }
            if (disjoint) return LiteralStatus.SATISFIED;
            return bothSingletonEqual ? LiteralStatus.FALSIFIED : LiteralStatus.UNDETERMINED;
        }
        boolean downward = isDownward(operator);
        Object leftMin = min(leftDiscrete), leftMax = max(leftDiscrete);
        Object rightMin = min(rightDiscrete), rightMax = max(rightDiscrete);
        Object satisfiedPairLeft = downward ? leftMax : leftMin;
        Object satisfiedPairRight = downward ? rightMin : rightMax;
        if (operator.compare(satisfiedPairLeft, satisfiedPairRight)) return LiteralStatus.SATISFIED;
        Object falsifiedPairLeft = downward ? leftMin : leftMax;
        Object falsifiedPairRight = downward ? rightMax : rightMin;
        if (operator.reversed().compare(falsifiedPairLeft, falsifiedPairRight)) return LiteralStatus.FALSIFIED;
        return LiteralStatus.UNDETERMINED;
    }

    /** {@code LT}/{@code LEQ} narrow towards the domain's maximum being favourable; {@code GT}/{@code GEQ} the minimum. */
    private static boolean isDownward(Operator operator) {
        return operator == Operator.LT || operator == Operator.LEQ;
    }

    @SuppressWarnings("unchecked")
    private static Object min(DiscreteDomain<Object> domain) {
        return domain.stream().min((a, b) -> ((Comparable<Object>) a).compareTo(b)).orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static Object max(DiscreteDomain<Object> domain) {
        return domain.stream().max((a, b) -> ((Comparable<Object>) a).compareTo(b)).orElseThrow();
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
     * decided. A {@link ValueLiteral} narrows to exactly the values satisfying {@code operator}
     * against its {@code value} -- for EQ/NEQ this is straight to/away from that value, the same as
     * {@link ValueDisjunctionConstraint}/{@link GroundNogoodConstraint}'s own forcing; for an
     * ordering operator, a domain-wide threshold cut, the {@code ValueLiteral} analogue of {@link
     * OrderingPropagation}'s bounds consistency. A {@link VariableLiteral} narrows both sides to
     * their intersection for EQ; for NEQ it can only force when one side is already singleton
     * (deleting that value from the other side), the same partial-forcing limit {@link
     * io.github.rcrida.jcsp.constraints.binary.BinaryComparatorConstraint}'s own NEQ handling has --
     * an inequality between two open domains isn't expressible as a single narrowed domain on
     * either side; for an ordering operator, each side is cut against the other's most permissive
     * extreme (see {@link #forceOrderingVariableLiteral}'s own Javadoc) -- real bounds consistency,
     * not full domain consistency, the same strength {@link
     * io.github.rcrida.jcsp.constraints.binary.BinaryComparatorConstraint}'s own numeric propagation
     * gives.
     * <p>
     * Never empties a domain: {@link #classify} having returned {@link LiteralStatus#UNDETERMINED}
     * for {@code literal} (before any negation here) already guarantees every narrowing branch below
     * keeps at least one value on each side it touches -- see this method's own reasoning per
     * branch, and {@link #forceOrderingVariableLiteral}'s for the ordering case specifically.
     * {@code literal} may itself be a negation of the one {@link #classify} examined (produced by
     * {@link Literal#negate()}), which flips only {@link ValueLiteral}/{@link VariableLiteral}'s
     * {@code operator} field to its logical complement (see {@link #negatedOperator}) -- the exact
     * same case-split {@link #classify} already reasons about for whichever operator ends up here,
     * so the emptiness argument holds regardless of which of the two was actually classified.
     */
    @SuppressWarnings("unchecked")
    private static Map<Variable<?>, Domain<?>> force(Literal literal, Map<Variable<?>, Domain<?>> domains) {
        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        if (literal instanceof ValueLiteral valueLiteral) {
            // UNDETERMINED already established at least one domain value satisfies operator (EQ:
            // present and not singleton at it; NEQ: present and not singleton; ordering: the
            // "falsified extreme" always survives -- see classifyValueLiteral) and at least one
            // doesn't (EQ: absent, vacuously true since then every value fails EQ; NEQ: singleton at
            // it; ordering: the "satisfied extreme" always fails), so the generic filter below
            // always deletes at least one value and always keeps at least one -- unconditionally
            // recording the narrowed domain (rather than guarding on a "did anything change" flag)
            // is provably safe, not an approximation, the same reasoning ValueSetNogoodConstraint#propagate uses.
            DiscreteDomain<Object> domain = (DiscreteDomain<Object>) domains.get(valueLiteral.variable());
            DiscreteDomain.Builder<Object> builder = domain.toBuilder();
            for (Object value : domain.toList()) {
                if (!valueLiteral.operator().compare(value, valueLiteral.value())) builder.delete(value);
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

        if (variableLiteral.operator() == Operator.NEQ) {
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

        return forceOrderingVariableLiteral(variableLiteral, leftDomain, rightDomain, updated);
    }

    /**
     * Bounds-consistency narrowing for an ordering {@link VariableLiteral}: a side keeps only the
     * values that could still satisfy {@link #operator} against <em>some</em> value on the other
     * side -- checked against the other side's single most permissive extreme (its maximum for
     * {@code LT}/{@code LEQ}'s right side and {@code GT}/{@code GEQ}'s left side, its minimum for
     * the reverse -- see {@link #isDownward}), mirroring {@link
     * io.github.rcrida.jcsp.constraints.binary.BinaryComparatorConstraint#propagate}'s own numeric
     * bounds narrowing but over any {@link Comparable} type, not just {@link Number}. This is sound
     * but not full domain consistency for a gappy discrete domain (a value between the surviving
     * extreme and an actual gap may have no real support), the same limit every other
     * bounds-consistency-only propagator in this codebase accepts.
     * <p>
     * Never empties a domain: {@link #classify}'s ordering case having returned {@link
     * LiteralStatus#UNDETERMINED} means the least-favourable pairing didn't satisfy {@link
     * #operator} (so the corresponding endpoint -- {@code leftMax} for a downward operator, the side
     * this method narrows via {@code rightExtreme} -- fails and is deleted) and the dual,
     * most-favourable pairing did (via {@link Operator#reversed()}'s total-order duality), which is
     * exactly the pairing each side's own retained extreme ({@code leftMin}/{@code rightMax} for a
     * downward operator) participates in -- so each side always keeps at least that one value.
     */
    private static Map<Variable<?>, Domain<?>> forceOrderingVariableLiteral(
            VariableLiteral variableLiteral, DiscreteDomain<Object> leftDomain, DiscreteDomain<Object> rightDomain,
            Map<Variable<?>, Domain<?>> updated) {
        Operator operator = variableLiteral.operator();
        boolean downward = isDownward(operator);
        Object rightExtreme = downward ? max(rightDomain) : min(rightDomain);
        Object leftExtreme = downward ? min(leftDomain) : max(leftDomain);

        DiscreteDomain.Builder<Object> leftBuilder = leftDomain.toBuilder();
        boolean leftChanged = false;
        for (Object value : leftDomain.toList()) {
            if (!operator.compare(value, rightExtreme)) {
                leftBuilder.delete(value);
                leftChanged = true;
            }
        }
        if (leftChanged) updated.put(variableLiteral.left(), leftBuilder.build());

        DiscreteDomain.Builder<Object> rightBuilder = rightDomain.toBuilder();
        boolean rightChanged = false;
        for (Object value : rightDomain.toList()) {
            if (!operator.compare(leftExtreme, value)) {
                rightBuilder.delete(value);
                rightChanged = true;
            }
        }
        if (rightChanged) updated.put(variableLiteral.right(), rightBuilder.build());
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

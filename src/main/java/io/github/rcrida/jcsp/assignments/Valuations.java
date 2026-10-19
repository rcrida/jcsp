package io.github.rcrida.jcsp.assignments;

import io.github.rcrida.jcsp.variables.Variable;

import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

/** The {@code equals}, {@code hashCode} and {@code toString} every {@link Valuation} shares. */
final class Valuations {
    private Valuations() {
    }

    /** Whether {@code other} is a {@link Valuation} holding exactly {@code valuation}'s entries. */
    static boolean equal(Valuation valuation, Object other) {
        if (valuation == other) return true;
        if (!(other instanceof Valuation that) || that.size() != valuation.size()) return false;
        for (Map.Entry<Variable<?>, Object> entry : valuation.entries()) {
            if (!entry.getValue().equals(that.getValueOrNull(entry.getKey()))) return false;
        }
        return true;
    }

    /**
     * Deliberately not {@code Objects.hash(values)} (equivalently, {@link Map#hashCode()}'s own
     * XOR-sum contract): that collapses badly when many {@code Assignment}s share the same
     * variable set and small-integer domain values -- {@link Integer#hashCode()} is the value
     * itself, so XORing it directly against a per-key hash barely perturbs the low bits, and
     * summing across entries whose values only range over a handful of small integers leaves the
     * whole sum confined to a narrow band. Confirmed empirically parsing a real XCSP3 instance
     * ({@code Steiner3-08.xml.lzma}): 80,640 distinct 6-tuples over domain {@code 1..8} collapsed
     * to just 57 distinct hashcodes, making {@code HashSet<Assignment>} construction (during
     * parsing) and {@link io.github.rcrida.jcsp.constraints.nary.NaryTuplesConstraint}'s own
     * {@code Set#contains} lookups (during search, over any extensional/tuple constraint with a
     * small-integer domain) degrade toward a linear scan instead of the expected O(1) average.
     * Multiplying each value's hashcode by a large odd constant first (a standard integer-avalanche
     * technique) spreads it across the full 32-bit range before combining; the per-entry
     * combination and the sum across entries are both still order-independent, so two equal
     * valuations still produce equal hashcodes whatever their entry order.
     */
    static int hash(Valuation valuation) {
        int hash = 0;
        for (Map.Entry<Variable<?>, Object> entry : valuation.entries()) {
            hash += entry.getKey().hashCode() ^ (entry.getValue().hashCode() * 0x9E3779B1);
        }
        return hash;
    }

    /** {@code {a=1, b=2}}, sorted by variable name so the output does not depend on entry order. */
    static String string(Valuation valuation) {
        return valuation.entryStream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Variable::getName)))
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", ", "{", "}"));
    }
}

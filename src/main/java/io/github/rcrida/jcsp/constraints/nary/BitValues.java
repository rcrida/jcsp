package io.github.rcrida.jcsp.constraints.nary;

/** Reads a boolean variable's value as a bit, whether it is a {@link Boolean} or a 0/1 {@link Integer}. */
final class BitValues {
    private BitValues() {
    }

    /** {@code true} and 1 are 1; {@code false} and 0 are 0. */
    static int bit(Object value) {
        return value instanceof Boolean flag ? (flag ? 1 : 0) : (Integer) value;
    }

    /** {@code bit} as a value of the same type as {@code sample}: a {@link Boolean} or an {@link Integer}. */
    static Object valueLike(Object sample, int bit) {
        return sample instanceof Boolean ? (Object) (bit == 1) : (Object) bit;
    }
}

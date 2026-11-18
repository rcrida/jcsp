package io.github.rcrida.jcsp.constraints.binary;

import lombok.experimental.SuperBuilder;

/**
 * A symmetric binary constraint allows the left and right variables to be swapped.
 * <p>
 * Equality and hashing therefore ignore which variable is which side. The hash mixes the two
 * variables' hashes in a fixed order, smaller first, rather than summing them: a variable hashes as
 * a multiple of its index, so a sum depends only on the two indices' sum, and an all-different over
 * {@code n} variables decomposes into {@code n(n-1)/2} constraints sharing just {@code 2n - 3}
 * hashes.
 */
@SuperBuilder
public abstract class SymmetricBinaryConstraint<T> extends BinaryConstraint<T, T> {

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof SymmetricBinaryConstraint<?> other) || !other.canEqual(this)) return false;
        return getLeft().equals(other.getLeft()) && getRight().equals(other.getRight())
                || getLeft().equals(other.getRight()) && getRight().equals(other.getLeft());
    }

    protected boolean canEqual(Object other) {
        return other instanceof SymmetricBinaryConstraint;
    }

    /** The two variables' hashes, smaller first, through the SplitMix64 finalizer. */
    @Override
    public int hashCode() {
        int a = getLeft().hashCode();
        int b = getRight().hashCode();
        long z = ((long) Math.min(a, b) << 32) | (Math.max(a, b) & 0xFFFF_FFFFL);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (int) (z ^ (z >>> 32));
    }
}

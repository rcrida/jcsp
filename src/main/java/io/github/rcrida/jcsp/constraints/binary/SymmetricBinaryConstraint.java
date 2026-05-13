package io.github.rcrida.jcsp.constraints.binary;

import lombok.Generated;
import lombok.experimental.SuperBuilder;

/**
 * A symmetric binary constraint allows the left and right variables to be swapped.
 */
@SuperBuilder
public abstract class SymmetricBinaryConstraint extends BinaryConstraint {

    @Generated
    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof SymmetricBinaryConstraint other)) {
            return false;
        } else {
            if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$variables = this.getVariables();
                Object other$variables = other.getVariables();
                if (!this$variables.equals(other$variables)) {
                    return false;
                }

                return true;
            }
        }
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof SymmetricBinaryConstraint;
    }

    @Generated
    @Override
    public int hashCode() {
        return 59 + this.getVariables().hashCode();
    }

}

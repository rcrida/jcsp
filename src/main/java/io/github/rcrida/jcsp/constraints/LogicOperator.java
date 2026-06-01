package io.github.rcrida.jcsp.constraints;

/**
 * All six symmetric binary boolean connectives, each with a display symbol and an
 * {@link #apply(boolean, boolean)} method.
 */
public enum LogicOperator {
    AND("&&") {
        @Override public boolean apply(boolean a, boolean b) { return a && b; }
    },
    OR("||") {
        @Override public boolean apply(boolean a, boolean b) { return a || b; }
    },
    XOR("^") {
        @Override public boolean apply(boolean a, boolean b) { return a ^ b; }
    },
    NAND("!&&") {
        @Override public boolean apply(boolean a, boolean b) { return !(a && b); }
    },
    NOR("!||") {
        @Override public boolean apply(boolean a, boolean b) { return !(a || b); }
    },
    XNOR("!^") {
        @Override public boolean apply(boolean a, boolean b) { return !(a ^ b); }
    };

    public final String symbol;

    LogicOperator(String symbol) {
        this.symbol = symbol;
    }

    public abstract boolean apply(boolean a, boolean b);
}

package org.jcsp.constraints.binary;

/**
 * This enum represents a set of binary operators that can be used for comparison.
 * Each operator is associated with a symbol and provides a specific implementation
 * of a comparison between two objects.
 * <p>
 * The operators include:
 * - EQ: Equal to (==)
 * - NEQ: Not equal to (!=)
 * - LT: Less than (<)
 * - GT: Greater than (>)
 * - LEQ: Less than or equal to (<=)
 * - GEQ: Greater than or equal to (>=)
 *<p>
 * Instances of this enum are capable of comparing two objects based on their type.
 * For objects that implement the {@link Comparable} interface, comparisons are done
 * using the natural ordering defined by the {@link Comparable#compareTo(Object)} method.
 * For equality-based operators (EQ and NEQ), comparisons rely on the {@link Object#equals(Object)} method.
 */
public enum Operator {
    EQ("==") {
        @Override
        public boolean compare(Object left, Object right) {
            return left.equals(right);
        }
    },
    NEQ("!=") {
        @Override
        public boolean compare(Object left, Object right) {
            return !left.equals(right);
        }
    },
    LT("<") {
        @Override
        public boolean compare(Object left, Object right) {
            return ((Comparable) left).compareTo(right) < 0;
        }
    },
    GT(">") {
        @Override
        public boolean compare(Object left, Object right) {
            return ((Comparable) left).compareTo(right) > 0;
        }
    },
    LEQ("<=") {
        @Override
        public boolean compare(Object left, Object right) {
            return ((Comparable) left).compareTo(right) <= 0;
        }
    },
    GEQ(">=") {
        @Override
        public boolean compare(Object left, Object right) {
            return ((Comparable) left).compareTo(right) >= 0;
        }
    };

    final String symbol;

    Operator(String symbol) {
        this.symbol = symbol;
    }

    Operator reversed() {
        return switch (this) {
            case EQ -> EQ;
            case NEQ -> NEQ;
            case LT -> GEQ;
            case GT -> LEQ;
            case LEQ -> GT;
            case GEQ -> LT;
        };
    }

    public abstract boolean compare(Object left, Object right);
}

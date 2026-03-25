package org.jcsp.constraints.binary;

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

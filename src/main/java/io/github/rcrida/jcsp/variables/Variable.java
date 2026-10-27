package io.github.rcrida.jcsp.variables;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a variable in a constraint satisfaction problem.
 * The type parameter {@code T} is a phantom type that carries the value type at compile time,
 * enabling type-safe domain and constraint registration at the builder level.
 */
public interface Variable<T> {
    String getName();

    /** What {@link #getIndex()} returns for a variable no {@link Factory} issued. */
    int UNINDEXED = -1;

    /**
     * Position in the order its {@link Factory} issued variables, dense from 0 per factory;
     * {@link #UNINDEXED} for a variable no factory issued.
     */
    default int getIndex() {
        return UNINDEXED;
    }

    /**
     * Equal only to itself: a variable is identified by being the one its factory issued, so two
     * issued with the same name are different variables. It hashes to its index spread across the
     * whole 32-bit range, which agrees with that and, unlike the identity hash, is the same on every
     * run, so the iteration order of a hash set or map of variables does not change from one run to
     * the next. The spreading matters: {@code Valuations#hash} sums a variable's hash code with its
     * value's across entries, and bare small indices collapse that sum into a few distinct values.
     * Multiplying by an odd constant is a bijection, so two indices never share a hash code.
     */
    @Getter
    @RequiredArgsConstructor
    final class Impl<T> implements Variable<T> {
        private final @NonNull String name;
        private final int index;

        @Override
        public int hashCode() {
            return index * 0x9E3779B1;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Issues variables, numbering them from 0 in creation order, and rejects a name it has already
     * issued so that a name identifies one variable. {@link #INSTANCE} is shared by the whole JVM, so
     * it numbers uniquely but not densely per problem and accepts repeated names.
     */
    final class Factory {
        public static final Factory INSTANCE = new Factory(null, null, 0);

        private final @Nullable Set<String> issuedNames;
        private final @Nullable Factory parent;
        private final AtomicInteger next;

        public Factory() {
            this(ConcurrentHashMap.newKeySet(), null, 0);
        }

        private Factory(@Nullable Set<String> issuedNames, @Nullable Factory parent, int next) {
            this.issuedNames = issuedNames;
            this.parent = parent;
            this.next = new AtomicInteger(next);
        }

        /**
         * A factory that carries on from this one: its first variable is numbered where this one's next
         * would be, and the names this one has issued count as issued, without copying them. Variables
         * the fork issues are not seen by this factory, so forks of one factory may reuse each other's
         * indices and names.
         */
        public Factory fork() {
            assert issuedNames != null : "the shared INSTANCE is not a problem's factory";
            return new Factory(ConcurrentHashMap.newKeySet(), this, next.get());
        }

        public <T> Variable<T> create(String name) {
            if (issuedNames != null) {
                if (hasIssued(name)) {
                    throw new IllegalArgumentException("Variable name already issued: " + name);
                }
                issuedNames.add(name);
            }
            return new Impl<>(name, next.getAndIncrement());
        }

        private boolean hasIssued(String name) {
            for (Factory factory = this; factory != null; factory = factory.parent) {
                if (factory.issuedNames.contains(name)) {
                    return true;
                }
            }
            return false;
        }
    }
}

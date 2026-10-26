package io.github.rcrida.jcsp.variables;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.NonNull;

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

    @Value
    class Impl<T> implements Variable<T> {
        @NonNull String name;
        @EqualsAndHashCode.Exclude int index;

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
        public static final Factory INSTANCE = new Factory(null);

        private final AtomicInteger next = new AtomicInteger();
        private final Set<String> issuedNames;

        public Factory() {
            this(ConcurrentHashMap.newKeySet());
        }

        private Factory(Set<String> issuedNames) {
            this.issuedNames = issuedNames;
        }

        public <T> Variable<T> create(String name) {
            if (issuedNames != null && !issuedNames.add(name)) {
                throw new IllegalArgumentException("Variable name already issued: " + name);
            }
            return new Impl<>(name, next.getAndIncrement());
        }
    }
}

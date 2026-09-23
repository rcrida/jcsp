package io.github.rcrida.jcsp.consistency;

import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Read-only {@link Map} view of a base domain map with a small set of narrowings layered over it:
 * {@link #get} returns an {@code updates} entry when one exists, else the {@code base} entry.
 * Lets a propagator that runs sub-propagators to a shared fixpoint hand each one a complete
 * {@code Map<Variable, Domain>} without copying the whole problem's domains per call — the
 * narrowings alone are carried, and they double as the diff to return.
 * <p>
 * {@code updates} is read live rather than snapshotted, so a caller may keep narrowing through the
 * same overlay instance. {@link #entrySet} materializes a merged map on each call and is not on any
 * propagation path (no {@link Propagatable} implementation iterates the domains map it is handed);
 * it exists so the view stays a correct {@link Map} for any caller that does.
 */
public final class DomainOverlay extends AbstractMap<Variable<?>, Domain<?>> {
    private final Map<Variable<?>, Domain<?>> base;
    private final Map<Variable<?>, Domain<?>> updates;

    private DomainOverlay(Map<Variable<?>, Domain<?>> base, Map<Variable<?>, Domain<?>> updates) {
        this.base = base;
        this.updates = updates;
    }

    /** View of {@code base} with {@code updates} layered over it; neither map is copied. */
    public static DomainOverlay of(@NonNull Map<Variable<?>, Domain<?>> base,
                                   @NonNull Map<Variable<?>, Domain<?>> updates) {
        return new DomainOverlay(base, updates);
    }

    @Override
    public @Nullable Domain<?> get(Object key) {
        Domain<?> updated = updates.get(key);
        return updated != null ? updated : base.get(key);
    }

    @Override
    public @NonNull Set<Entry<Variable<?>, Domain<?>>> entrySet() {
        Map<Variable<?>, Domain<?>> merged = new LinkedHashMap<>(base);
        merged.putAll(updates);
        return merged.entrySet();
    }
}

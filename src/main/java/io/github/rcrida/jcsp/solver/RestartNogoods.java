package io.github.rcrida.jcsp.solver;

/**
 * Whether {@link DomWdegLubySearch} remembers, when it abandons a run at its restart cutoff, which
 * branches of that run it had already refuted, so that the next run does not refute them again.
 * <p>
 * Each refuted candidate on the abandoned path becomes a nogood: the decisions above it plus the
 * candidate itself. The candidate the cutoff interrupted proved nothing and is not recorded, and
 * forced values are consequences of the decisions and are not cited. Sound, because each one was
 * refuted for real.
 * <p>
 * It matters for a search that spends its restarts re-proving the same thing, typically an
 * unsatisfiable instance; without it a restart forgets everything but constraint weights and the
 * remembered path. It costs a little on a satisfiable instance that only solves when a restart
 * happens to draw well, since remembering refuted prefixes steers away from some of those draws.
 * <p>
 * So the default is adaptive: record, count how often the recorded nogoods prune a domain or
 * refute a node (their <em>hits</em>) per backtrack, and once {@link #window} backtracks have been
 * spent, keep them only if that rate reached {@link #minHitRate}. Below it the recorded nogoods are
 * forgotten and no more are recorded. See ADR-0057.
 * <p>
 * A short nogood is as useful as a long one for the search it came from, and a long one is rarely
 * met again, so {@link #maxArity} bounds how many decisions one may cite.
 *
 * @param maxArity   the most decisions one recorded nogood may cite; {@code 0} records nothing
 * @param window     backtracks to observe before judging the hit rate; unused when
 *                   {@code minHitRate} is {@code 0}
 * @param minHitRate hits per backtrack below which the recorded nogoods are dropped after
 *                   {@code window} backtracks; {@code 0} keeps them for the whole search
 */
public record RestartNogoods(int maxArity, long window, double minHitRate) {

    /** Records nothing. */
    public static final RestartNogoods NONE = new RestartNogoods(0, 1, 0);

    /**
     * Records nogoods of up to 8 decisions and drops them if, after 10,000 backtracks, they have not
     * done work at least once per 100 backtracks. The default.
     */
    public static final RestartNogoods ADAPTIVE = new RestartNogoods(8, 10_000, 0.01);

    public RestartNogoods {
        if (maxArity < 0) throw new IllegalArgumentException("maxArity must not be negative, got: " + maxArity);
        if (window <= 0) throw new IllegalArgumentException("window must be positive, got: " + window);
        if (minHitRate < 0) throw new IllegalArgumentException("minHitRate must not be negative, got: " + minHitRate);
    }

    /** Records nogoods of up to {@code maxArity} decisions for the whole search. */
    public static RestartNogoods always(int maxArity) {
        return new RestartNogoods(maxArity, 1, 0);
    }

    /** Records nogoods of up to {@code maxArity} decisions, judging them after {@code window} backtracks. */
    public static RestartNogoods adaptive(int maxArity, long window, double minHitRate) {
        return new RestartNogoods(maxArity, window, minHitRate);
    }

    /** Whether any nogood is recorded at all. */
    public boolean isEnabled() {
        return maxArity > 0;
    }

    /** Whether the recorded nogoods are dropped when they are not paying. */
    public boolean isAdaptive() {
        return isEnabled() && minHitRate > 0;
    }
}

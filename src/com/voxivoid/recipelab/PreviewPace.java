package com.voxivoid.recipelab;

import java.util.Locale;

/**
 * When the camera follows the wheel. A look goes to the camera on the UI thread, and on an older body that takes longer
 * than the gap between two clicks, so a fast turn used to queue one camera round trip per click and the app ran behind
 * the wheel. Here the name moves on every click and the camera gets only some of them: a click after a quiet spell at
 * once, so one deliberate click still answers straight away; the last of a burst once the wheel pauses; and one now and
 * then during a long turn, so the view never stands still for the whole of it.
 *
 * A delay of 0 is the old behaviour, every click applied, so the two can be compared on one camera with the line
 * {@link #summary} leaves in LOG.TXT. No android.* import may appear here (tools/test.sh).
 */
final class PreviewPace {
    /** the delays to pick from in the developer menu, in ms; 0 applies every click */
    static final int[] DELAY_MS = { 0, 100, 150, 250 };
    /** the delay a fresh install starts on */
    static final int DEFAULT = 2;
    /** during a long turn the camera catches up after this many delays without an apply */
    static final int CATCH_UP = 4;

    private boolean seen;
    private long lastAsk, lastApplied;
    private int asks, applies, keys;
    private long applyTotal, applyMax, lagTotal, lagMax;

    /** a stored delay index brought back into the table */
    static int clamp(int idx) { return idx >= 0 && idx < DELAY_MS.length ? idx : DEFAULT; }

    /** the next / previous delay, wrapping */
    static int next(int idx, int dir) { return (clamp(idx) + DELAY_MS.length + dir) % DELAY_MS.length; }

    /** the delay of a stored index, in ms */
    static int ms(int idx) { return DELAY_MS[clamp(idx)]; }

    /** a click asks for a preview: true when the camera should get it now, false when it should wait for {@code delay} ms of quiet */
    boolean ask(long now, int delay) {
        if (!seen) lastApplied = now;
        boolean quiet = !seen || now - lastAsk >= delay;
        boolean overdue = seen && now - lastApplied >= delay * CATCH_UP;
        seen = true;
        lastAsk = now;
        asks++;
        return delay <= 0 || quiet || overdue;
    }

    /**
     * The camera finished, at {@code now}, after {@code tookMs}. The clock restarts from the end of the apply, not from the
     * click: clicks that piled up while the camera was busy arrive together, and must not read as a quiet spell.
     */
    void applied(long now, long tookMs) {
        lastApplied = now;
        lastAsk = now;
        applies++;
        applyTotal += tookMs;
        applyMax = Math.max(applyMax, tookMs);
    }

    /** how long a key sat before the app got to it, in ms; anything absurd is a key without a real event time */
    void keyLag(long ms) {
        if (ms < 0 || ms > 60000) return;
        keys++;
        lagTotal += ms;
        lagMax = Math.max(lagMax, ms);
    }

    /** whether a click was counted since the last reset */
    boolean any() { return asks > 0; }

    void reset() {
        seen = false;
        lastAsk = lastApplied = 0;
        asks = applies = keys = 0;
        applyTotal = applyMax = lagTotal = lagMax = 0;
    }

    /** the line a session leaves in LOG.TXT: what the wheel asked for, what the camera got, what each cost */
    String summary(int delay) {
        return String.format(Locale.ROOT, "pace %dms  clicks %d  applied %d  apply avg %dms max %dms  key lag avg %dms max %dms",
                delay, asks, applies, applies == 0 ? 0 : applyTotal / applies, applyMax, keys == 0 ? 0 : lagTotal / keys, lagMax);
    }
}

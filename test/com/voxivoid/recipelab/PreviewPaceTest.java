package com.voxivoid.recipelab;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** When the camera follows the wheel, and a replay of a fast turn against a slow camera on one thread, as on the body. */
class PreviewPaceTest {
    private static final int DELAY = 150;

    @Test void theFirstClickGoesStraightToTheCamera() {
        assertTrue(new PreviewPace().ask(1000, 1000, DELAY));
    }

    @Test void aClickAfterAQuietSpellGoesStraightToTheCamera() {
        PreviewPace p = new PreviewPace();
        p.ask(1000, 1000, DELAY);
        p.applied(1200, 200);
        assertTrue(p.ask(1400, 1400, DELAY));
    }

    @Test void aClickInsideABurstWaits() {
        PreviewPace p = new PreviewPace();
        p.ask(1000, 1000, DELAY);
        p.applied(1200, 200);
        assertFalse(p.ask(1210, 1060, DELAY));
        assertFalse(p.ask(1260, 1120, DELAY));
    }

    @Test void clicksThatPiledUpBehindASlowApplyDoNotReadAsQuiet() {
        PreviewPace p = new PreviewPace();
        p.ask(1000, 1000, DELAY);
        p.applied(1500, 500);
        assertFalse(p.ask(1500, 1050, DELAY), "this click was made at 1050; the app only reached it now");
    }

    @Test void aSlowHandlerDoesNotMakeABurstLookQuiet() {
        PreviewPace p = new PreviewPace();
        p.ask(1000, 1000, DELAY);
        p.applied(1000, 0);
        assertFalse(p.ask(1200, 1050, DELAY), "200 ms apart on arrival, 50 ms apart as made");
        assertFalse(p.ask(1400, 1100, DELAY));
    }

    @Test void anAppBehindTheWheelNeverAppliesEvenWhenOverdue() {
        PreviewPace p = new PreviewPace();
        p.ask(1000, 1000, DELAY);
        p.applied(1000, 0);
        assertFalse(p.ask(3000, 2500, DELAY), "half a second behind: drain first");
    }

    @Test void aLongTurnStillCatchesUp() {
        PreviewPace p = new PreviewPace();
        p.ask(1000, 1000, DELAY);
        p.applied(1000, 0);
        long t = 1000;
        while (t < 1000 + DELAY * PreviewPace.CATCH_UP - 50) { t += 50; assertFalse(p.ask(t, t, DELAY), "at " + t); }
        assertTrue(p.ask(t + 50, t + 50, DELAY));
    }

    @Test void zeroIsTheOldBehaviourEveryClickApplied() {
        PreviewPace p = new PreviewPace();
        for (int i = 0; i < 5; i++) assertTrue(p.ask(1000 + i, 1000, 0));
    }

    @Test void theDelayTableStartsOffAndClampsAndWraps() {
        assertEquals(0, PreviewPace.DELAY_MS[0], "off has to stay reachable for comparing");
        assertEquals(PreviewPace.DEFAULT, PreviewPace.clamp(99));
        assertEquals(PreviewPace.DEFAULT, PreviewPace.clamp(-1));
        assertEquals(0, PreviewPace.next(PreviewPace.DELAY_MS.length - 1, +1));
        assertEquals(PreviewPace.DELAY_MS.length - 1, PreviewPace.next(0, -1));
    }

    @Test void aMissingEventTimeDoesNotPoisonTheNumbers() {
        PreviewPace p = new PreviewPace();
        p.ask(1000, 1000, 0);
        p.keyLag(-5);
        p.keyLag(5000000);
        p.keyLag(40);
        assertTrue(p.summary(0).contains("key lag avg 40ms max 40ms"), p.summary(0));
    }

    @Test void theSummarySaysWhatTheCameraGot() {
        PreviewPace p = new PreviewPace();
        assertFalse(p.any());
        p.ask(1000, 1000, DELAY);
        p.applied(1300, 300);
        p.ask(1301, 1301, DELAY);
        p.handled(20);
        assertTrue(p.any());
        assertEquals("pace 150ms  clicks 2  applied 1  apply avg 300ms max 300ms  key lag avg 0ms max 0ms  handler avg 20ms max 20ms", p.summary(DELAY));
        p.reset();
        assertFalse(p.any());
    }

    // ------------------------------------------------------------ the replay
    private static final class Run {
        long now, maxLag, lastShownAt, lastClickAt;
        int applies, shown;
        void apply(PreviewPace pace, long cost, int recipe) {
            now += cost;
            pace.applied(now, cost);
            applies++;
            shown = recipe;
            lastShownAt = now;
        }
    }

    /** {@code clicks} clicks, {@code gap} ms apart, against a camera that blocks for {@code cost} ms; one thing at a time */
    private static Run replay(int clicks, long gap, long cost, int delay) { return replay(clicks, gap, cost, 0, delay); }

    /** the same with {@code handler} ms of the app's own work on each click */
    private static Run replay(int clicks, long gap, long cost, long handler, int delay) {
        PreviewPace pace = new PreviewPace();
        Run r = new Run();
        r.lastClickAt = (clicks - 1) * gap;
        long timer = -1;
        int next = 0;
        while (next < clicks || timer >= 0) {
            long click = next < clicks ? next * gap : Long.MAX_VALUE;
            if (timer < 0 || click <= Math.max(r.now, timer)) {
                r.now = Math.max(r.now, click);
                r.maxLag = Math.max(r.maxLag, r.now - click);
                next++;
                r.now += handler;
                boolean at = pace.ask(r.now, click, delay);
                if (delay == 0) r.apply(pace, cost, next - 1);
                else timer = at ? r.now : r.now + delay;
            } else {
                r.now = Math.max(r.now, timer);
                timer = -1;
                r.apply(pace, cost, next - 1);
            }
        }
        return r;
    }

    @Test void theOldWayRunsBehindTheWheel() {
        Run r = replay(20, 50, 300, 0);
        assertEquals(20, r.applies);
        assertTrue(r.maxLag > 4000, "the last click waits for nineteen round trips: " + r.maxLag);
    }

    @Test void aPacedTurnKeepsUpAndLandsOnTheLastRecipe() {
        Run old = replay(20, 50, 300, 0), paced = replay(20, 50, 300, DELAY);
        assertEquals(19, paced.shown, "the camera ends on the recipe on screen");
        assertTrue(paced.applies < old.applies / 3, "applied " + paced.applies);
        assertTrue(paced.maxLag <= 300, "a click waits for one apply at most: " + paced.maxLag);
        assertTrue(paced.lastShownAt < old.lastShownAt / 2, paced.lastShownAt + " against " + old.lastShownAt);
        assertTrue(paced.lastShownAt - paced.lastClickAt <= 300 + DELAY + 300, "at worst an apply already running, the pause, and the last apply");
    }

    @Test void aSlowHandlerDoesNotTurnEveryClickIntoAnApply() {
        Run r = replay(20, 50, 250, 200, DELAY);
        assertTrue(r.applies <= 3, "applied " + r.applies);
        assertEquals(19, r.shown);
    }

    @Test void aSlowDeliberateTurnStillShowsEveryRecipe() {
        Run r = replay(5, 1000, 300, DELAY);
        assertEquals(5, r.applies);
    }
}

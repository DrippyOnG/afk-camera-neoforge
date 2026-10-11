package de.drippyong.afkcamera.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static de.drippyong.afkcamera.core.IdleTracker.Transition.*;

class IdleTrackerTest {
    @Test void startsExactlyAtTimeoutAndDoesNotStartTwice() {
        var idle = new IdleTracker();
        for (int i = 0; i < 499; i++) assertEquals(NONE, idle.tick(true, false, false, 500));
        assertEquals(START, idle.tick(true, false, false, 500));
        assertTrue(idle.isActive());
        assertEquals(NONE, idle.tick(true, false, false, 500));
    }

    @Test void inputResetsEntireInactivityDelay() {
        var idle = new IdleTracker();
        idle.tick(true, false, false, 3);
        idle.tick(true, false, false, 3);
        idle.tick(true, true, false, 3);
        assertEquals(NONE, idle.tick(true, false, false, 3));
        assertEquals(NONE, idle.tick(true, false, false, 3));
        assertEquals(START, idle.tick(true, false, false, 3));
    }

    @Test void movementOrInputStopsActiveCinematicImmediately() {
        var idle = new IdleTracker();
        idle.tick(true, false, true, 500);
        assertEquals(STOP, idle.tick(true, true, false, 500));
        assertFalse(idle.isActive());
        assertEquals(NONE, idle.tick(true, false, false, 500));
    }

    @Test void menusDeathDisconnectAndDisabledStateCannotStart() {
        var idle = new IdleTracker();
        for (int i = 0; i < 600; i++) assertEquals(NONE, idle.tick(false, false, true, 1));
        assertFalse(idle.isActive());
        assertEquals(START, idle.tick(true, false, false, 1));
        assertEquals(STOP, idle.tick(false, false, false, 1));
        assertFalse(idle.isActive());
    }

    @Test void manualStartOverridesCommandInputWhenGameplayResumes() {
        var idle = new IdleTracker();
        assertEquals(START, idle.tick(true, true, true, 500));
        assertEquals(NONE, idle.tick(true, false, false, 500));
        assertEquals(STOP, idle.tick(true, true, false, 500));
    }

    @Test void resetIsIdempotentAndClearsPendingTimeout() {
        var idle = new IdleTracker();
        idle.tick(true, false, true, 5);
        assertEquals(STOP, idle.reset());
        assertEquals(NONE, idle.reset());
        for (int i = 0; i < 4; i++) assertEquals(NONE, idle.tick(true, false, false, 5));
        assertEquals(START, idle.tick(true, false, false, 5));
    }
}

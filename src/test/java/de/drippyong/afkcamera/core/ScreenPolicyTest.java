package de.drippyong.afkcamera.core;

import org.junit.jupiter.api.Test;

import static de.drippyong.afkcamera.core.IdleTracker.Transition.*;
import static de.drippyong.afkcamera.core.ScreenPolicy.Context.*;
import static org.junit.jupiter.api.Assertions.*;

class ScreenPolicyTest {
    @Test void pausedEscapeMenuCanReachIdleTimeout() {
        var idle = new IdleTracker();
        assertEquals(NONE, tick(idle, PAUSE_MENU, true, true, false, false, 3));
        assertEquals(NONE, tick(idle, PAUSE_MENU, true, true, false, false, 3));
        assertEquals(START, tick(idle, PAUSE_MENU, true, true, false, false, 3));
        assertTrue(idle.isActive());
    }

    @Test void chatTypingDoesNotResetDelayOrStopActiveCameraWhenEnabled() {
        var idle = new IdleTracker();
        assertEquals(NONE, tick(idle, CHAT, false, false, true, true, 3));
        assertEquals(NONE, tick(idle, CHAT, false, false, true, true, 3));
        assertEquals(START, tick(idle, CHAT, false, false, true, true, 3));
        assertEquals(NONE, tick(idle, CHAT, false, false, true, true, 3));
        assertTrue(idle.isActive());
    }

    @Test void disablingPauseMenuSettingStopsItsActiveCamera() {
        var idle = new IdleTracker();
        assertEquals(START, tick(idle, PAUSE_MENU, true, true, false, false, 1));
        assertEquals(STOP, tick(idle, PAUSE_MENU, true, false, true, false, 1));
        assertFalse(idle.isActive());
    }

    @Test void disablingChatSettingStopsItsActiveCamera() {
        var idle = new IdleTracker();
        assertEquals(START, tick(idle, CHAT, false, false, true, false, 1));
        assertEquals(STOP, tick(idle, CHAT, false, true, false, false, 1));
        assertFalse(idle.isActive());
    }

    @Test void gameplayKeyStillWakesCameraWithBothMenuSettingsEnabled() {
        var idle = new IdleTracker();
        assertEquals(START, tick(idle, GAMEPLAY, false, true, true, false, 1));
        assertEquals(STOP, tick(idle, GAMEPLAY, false, true, true, true, 1));
        assertFalse(idle.isActive());
    }

    @Test void inventoryAndSettingsRemainIneligibleWithBothOptionsEnabled() {
        var idle = new IdleTracker();
        assertEquals(START, tick(idle, GAMEPLAY, false, true, true, false, 1));
        assertEquals(STOP, tick(idle, OTHER, false, true, true, false, 1));
        assertEquals(NONE, tick(idle, OTHER, false, true, true, false, 1));
        assertFalse(ScreenPolicy.ignoresInput(OTHER, true, true));
        assertFalse(idle.isActive());
    }

    @Test void pauseWithoutAnAllowedScreenCannotStartCamera() {
        var idle = new IdleTracker();
        assertEquals(NONE, tick(idle, GAMEPLAY, true, true, true, false, 1));
        assertEquals(NONE, tick(idle, OTHER, true, true, true, false, 1));
        assertFalse(idle.isActive());
    }

    @Test void pauseMenuAndChatSettingsDoNotEnableEachOther() {
        var idle = new IdleTracker();
        assertEquals(NONE, tick(idle, CHAT, false, true, false, false, 1));
        assertEquals(START, tick(idle, PAUSE_MENU, true, true, false, false, 1));
        assertEquals(STOP, tick(idle, PAUSE_MENU, true, false, true, false, 1));
        assertEquals(START, tick(idle, CHAT, false, false, true, false, 1));
    }

    private static IdleTracker.Transition tick(IdleTracker idle, ScreenPolicy.Context context,
                                               boolean paused, boolean allowPauseMenu,
                                               boolean allowChat, boolean rawInput, int timeout) {
        boolean eligible = ScreenPolicy.allowsCinematics(context, paused, allowPauseMenu, allowChat);
        boolean activity = rawInput && !ScreenPolicy.ignoresInput(context, allowPauseMenu, allowChat);
        return idle.tick(eligible, activity, false, timeout);
    }
}

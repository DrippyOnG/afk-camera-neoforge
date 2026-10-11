package de.drippyong.afkcamera.core;

/** Keeps AFK transitions independent of the renderer and wall-clock adjustments. */
public final class IdleTracker {
    public enum Transition { NONE, START, STOP }

    private int idleTicks;
    private boolean active;

    public Transition tick(boolean eligible, boolean activity, boolean manualStart, int timeoutTicks) {
        if (!eligible) return reset();
        if (manualStart) {
            idleTicks = 0;
            if (active) return Transition.NONE;
            active = true;
            return Transition.START;
        }
        if (activity) return reset();
        if (active) return Transition.NONE;
        idleTicks = Math.min(idleTicks + 1, Math.max(1, timeoutTicks));
        if (idleTicks >= Math.max(1, timeoutTicks)) {
            active = true;
            return Transition.START;
        }
        return Transition.NONE;
    }

    public Transition reset() {
        idleTicks = 0;
        if (!active) return Transition.NONE;
        active = false;
        return Transition.STOP;
    }

    public boolean isActive() { return active; }
}

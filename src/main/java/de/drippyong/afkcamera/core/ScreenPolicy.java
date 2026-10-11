package de.drippyong.afkcamera.core;

/** Controls which screens permit AFK cinematics and keep their input inside the GUI. */
public final class ScreenPolicy {
    public enum Context { GAMEPLAY, PAUSE_MENU, CHAT, OTHER }

    private ScreenPolicy() {}

    public static boolean allowsCinematics(Context context, boolean paused,
                                           boolean allowPauseMenu, boolean allowChat) {
        return switch (context) {
            case GAMEPLAY -> !paused;
            case PAUSE_MENU -> allowPauseMenu;
            case CHAT -> allowChat;
            case OTHER -> false;
        };
    }

    /** Allowed menus own mouse and keyboard input, so their input should not wake the camera. */
    public static boolean ignoresInput(Context context, boolean allowPauseMenu, boolean allowChat) {
        return switch (context) {
            case PAUSE_MENU -> allowPauseMenu;
            case CHAT -> allowChat;
            case GAMEPLAY, OTHER -> false;
        };
    }
}

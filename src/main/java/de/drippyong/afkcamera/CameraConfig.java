package de.drippyong.afkcamera;

import de.drippyong.afkcamera.core.CinematicSequence;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class CameraConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("Automatically start cinematic shots when you are idle.")
            .translation("afkcamera.configuration.enabled").define("enabled", true);
    public static final ModConfigSpec.IntValue IDLE_SECONDS = BUILDER
            .comment("Seconds without input or player movement before the camera starts.")
            .translation("afkcamera.configuration.idleSeconds").defineInRange("idleSeconds", 25, 1, 36000);
    public static final ModConfigSpec.BooleanValue MUSIC = BUILDER
            .comment("Play dimension-appropriate Minecraft music without the usual delay during cinematics.")
            .translation("afkcamera.configuration.music").define("music", true);
    public static final ModConfigSpec.BooleanValue HIDE_HUD = BUILDER
            .translation("afkcamera.configuration.hideHud").define("hideHud", true);
    public static final ModConfigSpec.BooleanValue LETTERBOX = BUILDER
            .translation("afkcamera.configuration.letterbox").define("letterbox", true);
    public static final ModConfigSpec.EnumValue<CinematicSequence.Motion> MOTION = BUILDER
            .translation("afkcamera.configuration.motion").defineEnum("motion", CinematicSequence.Motion.DEFAULT);
    public static final ModConfigSpec.EnumValue<CinematicSequence.Viewpoint> VIEWPOINT = BUILDER
            .comment("AUTO mixes character and scenery shots; WORLD_TOUR randomly shows only the surroundings.")
            .translation("afkcamera.configuration.viewpoint").defineEnum("viewpoint", CinematicSequence.Viewpoint.AUTO);
    public static final ModConfigSpec.BooleanValue IN_PAUSE_MENU = BUILDER
            .comment("Allow cinematics behind the Escape menu. Single-player worlds stay paused.")
            .translation("afkcamera.configuration.inPauseMenu").define("inPauseMenu", true);
    public static final ModConfigSpec.BooleanValue IN_CHAT = BUILDER
            .comment("Allow cinematics while chat is open; typing and scrolling do not interrupt them.")
            .translation("afkcamera.configuration.inChat").define("inChat", true);
    public static final ModConfigSpec SPEC = BUILDER.build();

    private CameraConfig() {}
}

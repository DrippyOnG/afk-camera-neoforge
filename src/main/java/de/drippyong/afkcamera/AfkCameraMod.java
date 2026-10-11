package de.drippyong.afkcamera;

import de.drippyong.afkcamera.core.CinematicSequence;
import de.drippyong.afkcamera.core.IdleTracker;
import de.drippyong.afkcamera.core.ScreenPolicy;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.Music;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** All registration is client-only, including class loading on dedicated servers. */
@Mod(value = AfkCameraMod.ID, dist = Dist.CLIENT)
public final class AfkCameraMod {
    public static final String ID = "afkcamera";
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(
            Identifier.fromNamespaceAndPath(ID, "controls"));
    private static final KeyMapping SETTINGS = new KeyMapping("key.afkcamera.settings", GLFW.GLFW_KEY_J, CATEGORY);
    private static final KeyMapping START = new KeyMapping("key.afkcamera.start", GLFW.GLFW_KEY_UNKNOWN, CATEGORY);
    private static final KeyMapping TOGGLE = new KeyMapping("key.afkcamera.toggle", GLFW.GLFW_KEY_UNKNOWN, CATEGORY);
    private static final KeyMapping NEXT_VIEWPOINT = new KeyMapping("key.afkcamera.next_viewpoint", GLFW.GLFW_KEY_UNKNOWN, CATEGORY);
    private static final KeyMapping TOGGLE_SCREENS = new KeyMapping("key.afkcamera.toggle_screens", GLFW.GLFW_KEY_UNKNOWN, CATEGORY);

    private final IdleTracker idle = new IdleTracker();
    private final ModContainer container;
    private CinematicSequence sequence;
    private CinematicSequence.Environment environment = CinematicSequence.Environment.OVERWORLD;
    private CinematicSequence.Pose pose;
    private CameraType previousCamera;
    private CameraType activeCamera;
    private CinematicSequence.Viewpoint activeViewpoint;
    private Object previousPlayer;
    private Object previousLevel;
    private Vec3 previousPosition;
    private float previousYaw;
    private float previousPitch;
    private long cinematicStartedAtNanos;
    private boolean activity;
    private boolean manualStart;

    public AfkCameraMod(IEventBus modBus, ModContainer container) {
        this.container = container;
        container.registerConfig(ModConfig.Type.CLIENT, CameraConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(this::registerKeys);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(this::tick);
        bus.addListener(this::frame);
        bus.addListener(this::angles);
        bus.addListener(this::distance);
        bus.addListener(this::fov);
        bus.addListener(this::hud);
        bus.addListener(this::hand);
        bus.addListener(this::playerBody);
        bus.addListener(this::music);
        bus.addListener(this::commands);
        bus.addListener(this::key);
        bus.addListener(this::screenOpened);
        bus.addListener(this::screenClosed);
        bus.addListener((InputEvent.MouseButton.Pre event) -> {
            if (event.getAction() != GLFW.GLFW_RELEASE && !ignoreScreenInput(Minecraft.getInstance())) wake();
        });
        bus.addListener((InputEvent.MouseScrollingEvent event) -> {
            if (!ignoreScreenInput(Minecraft.getInstance())) wake();
        });
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(SETTINGS);
        event.register(START);
        event.register(TOGGLE);
        event.register(NEXT_VIEWPOINT);
        event.register(TOGGLE_SCREENS);
    }

    private void wake() {
        activity = true;
        stop();
    }

    private boolean eligible(Minecraft mc) {
        return mc.player != null && mc.level != null && mc.getCameraEntity() == mc.player && mc.player.isAlive()
                && !mc.player.isSleeping() && mc.player.hurtTime == 0
                && ScreenPolicy.allowsCinematics(screenContext(mc.gui.screen()), mc.isPaused(),
                CameraConfig.IN_PAUSE_MENU.get(), CameraConfig.IN_CHAT.get());
    }

    private ScreenPolicy.Context screenContext(Screen screen) {
        if (screen == null) return ScreenPolicy.Context.GAMEPLAY;
        if (screen instanceof PauseScreen) return ScreenPolicy.Context.PAUSE_MENU;
        if (screen instanceof ChatScreen) return ScreenPolicy.Context.CHAT;
        return ScreenPolicy.Context.OTHER;
    }

    private boolean ignoreScreenInput(Minecraft mc) {
        return ScreenPolicy.ignoresInput(screenContext(mc.gui.screen()),
                CameraConfig.IN_PAUSE_MENU.get(), CameraConfig.IN_CHAT.get());
    }

    private void key(InputEvent.Key event) {
        if (event.getAction() == GLFW.GLFW_RELEASE) return;
        Minecraft mc = Minecraft.getInstance();
        if (ignoreScreenInput(mc)) return;
        // Chat opens on the next tick, after this key event has been dispatched.
        if (mc.gui.screen() == null && CameraConfig.IN_CHAT.get()
                && (mc.options.keyChat.matches(event.getKeyEvent())
                || mc.options.keyCommand.matches(event.getKeyEvent()))) return;
        wake();
    }

    private void screenOpened(ScreenEvent.Opening event) {
        var next = screenContext(event.getNewScreen());
        if (next == ScreenPolicy.Context.OTHER
                || (next == ScreenPolicy.Context.PAUSE_MENU && !CameraConfig.IN_PAUSE_MENU.get())
                || (next == ScreenPolicy.Context.CHAT && !CameraConfig.IN_CHAT.get())) {
            stop();
        }
    }

    private void screenClosed(ScreenEvent.Closing event) {
        // Vanilla does not emit Opening when returning to gameplay with a null screen.
        if (ScreenPolicy.ignoresInput(screenContext(event.getScreen()),
                CameraConfig.IN_PAUSE_MENU.get(), CameraConfig.IN_CHAT.get())) stop();
    }

    private void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (SETTINGS.consumeClick()) {
            stop();
            mc.gui.setScreen(new ConfigurationScreen(container, mc.gui.screen()));
        }
        while (TOGGLE.consumeClick()) setEnabled(!CameraConfig.ENABLED.get());
        while (START.consumeClick()) manualStart = true;
        while (NEXT_VIEWPOINT.consumeClick()) selectViewpoint(nextViewpoint());
        while (TOGGLE_SCREENS.consumeClick()) setScreensEnabled(
                !(CameraConfig.IN_PAUSE_MENU.get() && CameraConfig.IN_CHAT.get()));

        if (previousPlayer != mc.player || previousLevel != mc.level) {
            stop();
            manualStart = false;
            previousPosition = null;
            previousPlayer = mc.player;
            previousLevel = mc.level;
        }
        boolean moved = false;
        if (mc.player != null) {
            Vec3 position = mc.player.position();
            moved = previousPosition != null && (position.distanceToSqr(previousPosition) > 0.0001
                    || Math.abs(CinematicSequence.shortestAngle(mc.player.getYRot() - previousYaw)) > 0.001
                    || Math.abs(mc.player.getXRot() - previousPitch) > 0.001);
            previousPosition = position;
            previousYaw = mc.player.getYRot();
            previousPitch = mc.player.getXRot();
        }
        boolean heldInput = !ignoreScreenInput(mc) && (mc.options.keyUp.isDown() || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown()
                || mc.options.keyJump.isDown() || mc.options.keyShift.isDown()
                || mc.options.keySprint.isDown() || mc.options.keyAttack.isDown() || mc.options.keyUse.isDown());
        boolean eligible = eligible(mc) && CameraConfig.ENABLED.get();
        boolean startNow = eligible && manualStart;
        if (startNow || mc.player == null || !CameraConfig.ENABLED.get()) manualStart = false;
        IdleTracker.Transition transition = idle.tick(eligible, activity || moved || heldInput, startNow,
                CameraConfig.IDLE_SECONDS.get() * 20);
        activity = false;
        if (transition == IdleTracker.Transition.STOP) restoreCamera();
        if (transition == IdleTracker.Transition.START) {
            previousCamera = mc.options.getCameraType();
            activeViewpoint = CameraConfig.VIEWPOINT.get();
            boolean firstPerson = activeViewpoint == CinematicSequence.Viewpoint.FIRST_PERSON;
            activeCamera = firstPerson ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_BACK;
            mc.options.setCameraType(activeCamera);
            boolean frontView = previousCamera == CameraType.THIRD_PERSON_FRONT;
            sequence = new CinematicSequence(ThreadLocalRandom.current().nextLong(),
                    previousYaw + (frontView && !firstPerson ? 180 : 0),
                    frontView && !firstPerson ? -previousPitch : previousPitch,
                    previousCamera.isFirstPerson() ? 0 : 4, previousYaw);
            environment = mc.level.dimension().equals(Level.NETHER) ? CinematicSequence.Environment.NETHER
                    : mc.level.dimension().equals(Level.END) ? CinematicSequence.Environment.END
                    : !mc.level.canSeeSky(mc.player.blockPosition().above()) ? CinematicSequence.Environment.CAVE
                    : CinematicSequence.Environment.OVERWORLD;
            cinematicStartedAtNanos = System.nanoTime();
            pose = sequence.sample(0, environment, CameraConfig.MOTION.get(), activeViewpoint);
        }
    }

    private void frame(RenderFrameEvent.Pre event) {
        if (!idle.isActive()) return;
        Minecraft mc = Minecraft.getInstance();
        if (shouldWake(mc)) wake();
    }

    private boolean shouldWake(Minecraft mc) {
        if (!eligible(mc) || !CameraConfig.ENABLED.get() || mc.options.getCameraType() != activeCamera) return true;
        return !ignoreScreenInput(mc) && (mc.mouseHandler.getXVelocity() != 0 || mc.mouseHandler.getYVelocity() != 0
                || Math.abs(CinematicSequence.shortestAngle(mc.player.getYRot() - previousYaw)) > 0.001
                || Math.abs(mc.player.getXRot() - previousPitch) > 0.001);
    }

    private void angles(ViewportEvent.ComputeCameraAngles event) {
        if (!idle.isActive() || sequence == null || event.getCamera().entity() != Minecraft.getInstance().player) return;
        if (shouldWake(Minecraft.getInstance())) {
            wake();
            return;
        }
        // Game partial ticks freeze while paused. Monotonic time keeps only the camera moving.
        pose = sequence.sample(Math.max(0, System.nanoTime() - cinematicStartedAtNanos) / 1_000_000_000.0,
                environment, CameraConfig.MOTION.get(), activeViewpoint);
        event.setYaw((float) pose.yaw());
        event.setPitch((float) pose.pitch());
        event.setRoll((float) pose.roll());
    }

    private void distance(CalculateDetachedCameraDistanceEvent event) {
        if (idle.isActive() && pose != null && event.getCamera().entity() == Minecraft.getInstance().player) {
            // NeoForge invokes Minecraft's obstruction ray casts after this event.
            event.setDistance((float) pose.distance());
        }
    }

    private void fov(ViewportEvent.ComputeFov event) {
        if (!idle.isActive() || pose == null || event.getCamera().entity() != Minecraft.getInstance().player) return;
        // Alignment and vanilla collision use the radial angles above. Scenery looks
        // outward from that safe camera position, before the world frustum is built.
        event.getCamera().setRotation((float) pose.viewYaw(), (float) pose.viewPitch(), (float) pose.roll());
        event.setFOV((float) (event.getFOV() * pose.fovScale()));
    }

    private void hand(RenderHandEvent event) {
        if (idle.isActive() && CameraConfig.HIDE_HUD.get()) event.setCanceled(true);
    }

    private void playerBody(RenderPlayerEvent.Pre<?> event) {
        Minecraft mc = Minecraft.getInstance();
        if (!idle.isActive() || mc.player == null || event.getRenderState().id != mc.player.getId()) return;
        if (activeViewpoint == CinematicSequence.Viewpoint.WORLD_TOUR || activeViewpoint.isScenery()) {
            event.setCanceled(true);
            return;
        }
        var state = event.getRenderState();
        // Collision or a smooth first-person entry can put the lens inside the head.
        if (mc.gameRenderer.mainCamera().position().distanceToSqr(
                new Vec3(state.x, state.y + state.eyeHeight, state.z)) < 0.36) {
            event.setCanceled(true);
        }
    }

    private void hud(RenderGuiEvent.Pre event) {
        if (!idle.isActive()) return;
        if (CameraConfig.HIDE_HUD.get()) event.setCanceled(true);
        var mc = Minecraft.getInstance();
        // The input field is at the bottom of the screen; keep chat unobstructed.
        if (CameraConfig.LETTERBOX.get() && !(mc.gui.screen() instanceof ChatScreen)) {
            int width = mc.getWindow().getGuiScaledWidth();
            int height = mc.getWindow().getGuiScaledHeight();
            int bar = Math.max(1, height / 10);
            event.getGuiGraphics().fill(0, 0, width, bar, 0xFF000000);
            event.getGuiGraphics().fill(0, height - bar, width, height, 0xFF000000);
        }
    }

    private void music(SelectMusicEvent event) {
        if (!idle.isActive()) return;
        if (!CameraConfig.MUSIC.get()) {
            event.setMusic(null);
            return;
        }
        Music original = event.getOriginalMusic();
        if (original != null) event.setMusic(new Music(original.sound(), 0, 0, original.replaceCurrentMusic()));
    }

    private void stop() {
        idle.reset();
        restoreCamera();
    }

    private void restoreCamera() {
        if (previousCamera != null) Minecraft.getInstance().options.setCameraType(previousCamera);
        previousCamera = null;
        activeCamera = null;
        activeViewpoint = null;
        sequence = null;
        pose = null;
        cinematicStartedAtNanos = 0;
    }

    private void setEnabled(boolean enabled) {
        CameraConfig.ENABLED.set(enabled);
        CameraConfig.SPEC.save();
        activity = true;
        if (!enabled) {
            manualStart = false;
            stop();
        }
    }

    private int setScreensEnabled(boolean enabled) {
        CameraConfig.IN_PAUSE_MENU.set(enabled);
        CameraConfig.IN_CHAT.set(enabled);
        CameraConfig.SPEC.save();
        if (!eligible(Minecraft.getInstance())) stop();
        return feedback("Cinematics in Escape menu and chat: " + (enabled ? "on" : "off") + ".");
    }

    private int setScreenEnabled(boolean chat, boolean enabled) {
        (chat ? CameraConfig.IN_CHAT : CameraConfig.IN_PAUSE_MENU).set(enabled);
        CameraConfig.SPEC.save();
        if (!eligible(Minecraft.getInstance())) stop();
        return feedback("Cinematics in " + (chat ? "chat" : "Escape menu") + ": " + (enabled ? "on" : "off") + ".");
    }

    private int feedback(String text) {
        Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(Component.literal(text));
        return 1;
    }

    private CinematicSequence.Viewpoint nextViewpoint() {
        var modes = CinematicSequence.Viewpoint.values();
        return modes[(CameraConfig.VIEWPOINT.get().ordinal() + 1) % modes.length];
    }

    private int selectViewpoint(CinematicSequence.Viewpoint viewpoint) {
        stop();
        CameraConfig.VIEWPOINT.set(viewpoint);
        CameraConfig.SPEC.save();
        manualStart = CameraConfig.ENABLED.get();
        return feedback("Camera viewpoint: " + viewpoint.name().toLowerCase(Locale.ROOT)
                + (manualStart ? ". Preview requested; gameplay input exits." : ". Use /afkc on to enable previews."));
    }

    private void commands(RegisterClientCommandsEvent event) {
        var root = literal("afkc")
                .executes(context -> feedback("AFK Camera: /afkc start, stop, pov <mode>|next, screens on|off, menu on|off, chat on|off, time <seconds>, music on|off, motion <level>, on, off, settings, about"))
                .then(literal("start").executes(context -> {
                    if (!CameraConfig.ENABLED.get()) return feedback("AFK Camera is disabled. Use /afkc on first.");
                    manualStart = true;
                    return feedback("Cinematic camera requested. Gameplay input exits; allowed menus and chat keep it running.");
                }))
                .then(literal("stop").executes(context -> {
                    manualStart = false;
                    wake();
                    return feedback("Cinematic camera stopped.");
                }))
                .then(literal("on").executes(context -> { setEnabled(true); return feedback("AFK Camera enabled."); }))
                .then(literal("off").executes(context -> { setEnabled(false); return feedback("AFK Camera disabled."); }))
                .then(literal("time")
                        .executes(context -> feedback("AFK delay: " + CameraConfig.IDLE_SECONDS.get() + " seconds."))
                        .then(argument("seconds", IntegerArgumentType.integer(1, 36000)).executes(context -> {
                            CameraConfig.IDLE_SECONDS.set(IntegerArgumentType.getInteger(context, "seconds"));
                            CameraConfig.SPEC.save();
                            wake();
                            return feedback("AFK delay: " + CameraConfig.IDLE_SECONDS.get() + " seconds.");
                        })))
                .then(literal("settings").executes(context -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> mc.gui.setScreen(new ConfigurationScreen(container, null)));
                    return 1;
                }))
                .then(literal("about").executes(context -> feedback("AFK Camera for NeoForge 26.2. Inspired by AFK Cinematics by Spunky Insaan: https://modrinth.com/mod/afk-cinematics")));
        var music = literal("music").executes(context -> {
            CameraConfig.MUSIC.set(!CameraConfig.MUSIC.get());
            CameraConfig.SPEC.save();
            return feedback("Cinematic music: " + CameraConfig.MUSIC.get());
        });
        for (boolean enabled : new boolean[] {true, false}) {
            music.then(literal(enabled ? "on" : "off").executes(context -> {
                CameraConfig.MUSIC.set(enabled);
                CameraConfig.SPEC.save();
                return feedback("Cinematic music: " + enabled);
            }));
        }
        var motion = literal("motion").executes(context -> feedback("Camera motion: " + CameraConfig.MOTION.get()));
        for (var level : CinematicSequence.Motion.values()) {
            motion.then(literal(level.name().toLowerCase(Locale.ROOT)).executes(context -> {
                CameraConfig.MOTION.set(level);
                CameraConfig.SPEC.save();
                return feedback("Camera motion: " + level);
            }));
        }
        var viewpoint = literal("pov").executes(context -> feedback("Camera viewpoint: "
                + CameraConfig.VIEWPOINT.get().name().toLowerCase(Locale.ROOT)
                + ". Use /afkc pov world_tour for scenery only, auto for a mixed tour, or press Tab to list modes."));
        viewpoint.then(literal("next").executes(context -> selectViewpoint(nextViewpoint())));
        for (var mode : CinematicSequence.Viewpoint.values()) {
            viewpoint.then(literal(mode.name().toLowerCase(Locale.ROOT)).executes(context -> selectViewpoint(mode)));
        }
        var screens = literal("screens").executes(context -> setScreensEnabled(
                !(CameraConfig.IN_PAUSE_MENU.get() && CameraConfig.IN_CHAT.get())));
        var menu = literal("menu").executes(context -> setScreenEnabled(false, !CameraConfig.IN_PAUSE_MENU.get()));
        var chat = literal("chat").executes(context -> setScreenEnabled(true, !CameraConfig.IN_CHAT.get()));
        for (boolean enabled : new boolean[] {true, false}) {
            String name = enabled ? "on" : "off";
            screens.then(literal(name).executes(context -> setScreensEnabled(enabled)));
            menu.then(literal(name).executes(context -> setScreenEnabled(false, enabled)));
            chat.then(literal(name).executes(context -> setScreenEnabled(true, enabled)));
        }
        event.getDispatcher().register(root.then(music).then(motion).then(viewpoint).then(screens).then(menu).then(chat));
    }
}

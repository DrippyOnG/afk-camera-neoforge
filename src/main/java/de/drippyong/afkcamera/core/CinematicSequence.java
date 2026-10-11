package de.drippyong.afkcamera.core;

import java.util.Random;

/** Pure camera choreography; Minecraft retains responsibility for block collision. */
public final class CinematicSequence {
    public enum Environment { OVERWORLD, CAVE, NETHER, END }
    public enum Viewpoint {
        AUTO, WORLD_TOUR, ORBIT, FRONT, BACK, LEFT_SIDE, RIGHT_SIDE, CLOSE_UP, WIDE, LOW_ANGLE, OVERHEAD,
        LANDSCAPE, PANORAMA, SKYWARD, HORIZON, AERIAL, GROUND_SWEEP, WORLD_ORBIT, FIRST_PERSON;

        public boolean isScenery() {
            return switch (this) {
                case LANDSCAPE, PANORAMA, SKYWARD, HORIZON, AERIAL, GROUND_SWEEP, WORLD_ORBIT -> true;
                default -> false;
            };
        }
    }
    public enum Motion {
        LOW(0.55), DEFAULT(1.0), MEDIUM(1.3), HIGH(1.65);
        private final double speed;
        Motion(double speed) { this.speed = speed; }
    }
    /** yaw/pitch locate the camera relative to the player; viewYaw/viewPitch determine where it looks. */
    public record Pose(double yaw, double pitch, double distance, double fovScale, double roll,
                       double viewYaw, double viewPitch) {
        public Pose(double yaw, double pitch, double distance, double fovScale, double roll) {
            this(yaw, pitch, distance, fovScale, roll, yaw, pitch);
        }
    }
    private record Shot(double yaw, double arc, double pitch, double lift, double distance, double dolly,
                        double fov, double roll, double viewYaw, double viewArc, double viewPitch, double viewLift) {
        Pose sample(double progress) {
            double eased = smooth(progress);
            return new Pose(yaw + arc * eased, pitch + lift * eased, distance + dolly * eased,
                    fov, roll * Math.sin(Math.PI * progress), viewYaw + viewArc * eased,
                    viewPitch + viewLift * eased);
        }
    }

    private static final double SHOT_SECONDS = 14.0;
    private static final double BLEND_SECONDS = 2.5;
    private static final long AUTO_SHUFFLE_SALT = 0xD1B54A32D192ED03L;
    private static final long WORLD_SHUFFLE_SALT = 0xA0761D6478BD642FL;
    private static final Viewpoint[] AUTO_VIEWPOINTS = {
            Viewpoint.ORBIT, Viewpoint.FRONT, Viewpoint.BACK, Viewpoint.LEFT_SIDE, Viewpoint.RIGHT_SIDE,
            Viewpoint.CLOSE_UP, Viewpoint.WIDE, Viewpoint.LOW_ANGLE, Viewpoint.OVERHEAD,
            Viewpoint.LANDSCAPE, Viewpoint.PANORAMA, Viewpoint.SKYWARD,
            Viewpoint.HORIZON, Viewpoint.AERIAL, Viewpoint.GROUND_SWEEP, Viewpoint.WORLD_ORBIT
    };
    private static final Viewpoint[] WORLD_VIEWPOINTS = {
            Viewpoint.LANDSCAPE, Viewpoint.PANORAMA, Viewpoint.SKYWARD,
            Viewpoint.HORIZON, Viewpoint.AERIAL, Viewpoint.GROUND_SWEEP, Viewpoint.WORLD_ORBIT
    };
    private final long seed;
    private final Pose initial;
    private final double playerYaw;
    private final Viewpoint[] autoViewpoints;
    private final Viewpoint[] worldViewpoints;

    public CinematicSequence(long seed, double yaw, double pitch) {
        this(seed, yaw, pitch, 4);
    }

    public CinematicSequence(long seed, double yaw, double pitch, double distance) {
        this(seed, yaw, pitch, distance, yaw);
    }

    /** The player's facing stays independent of the initial front/back camera orientation. */
    public CinematicSequence(long seed, double cameraYaw, double cameraPitch, double initialDistance,
                             double playerYaw) {
        this.seed = seed;
        this.playerYaw = playerYaw;
        initial = new Pose(cameraYaw, Math.clamp(cameraPitch, -80, 80),
                Math.clamp(initialDistance, 0, 10), 1, 0);
        autoViewpoints = shuffledViewpoints(0, AUTO_VIEWPOINTS, AUTO_SHUFFLE_SALT);
        worldViewpoints = shuffledViewpoints(0, WORLD_VIEWPOINTS, WORLD_SHUFFLE_SALT);
    }

    public Pose sample(double elapsedSeconds, Environment environment, Motion motion) {
        return sample(elapsedSeconds, environment, motion, Viewpoint.AUTO);
    }

    public Pose sample(double elapsedSeconds, Environment environment, Motion motion, Viewpoint viewpoint) {
        if (!Double.isFinite(elapsedSeconds)) throw new IllegalArgumentException("Time must be finite");
        double time = Math.max(0, elapsedSeconds) * motion.speed;
        if (!Double.isFinite(time)) throw new IllegalArgumentException("Scaled time must be finite");
        long index = (long) (time / SHOT_SECONDS);
        double local = time - index * SHOT_SECONDS;
        Shot shot = shot(index, environment, viewpoint);
        Pose target = shot.sample(Math.max(0, (local - BLEND_SECONDS) / (SHOT_SECONDS - BLEND_SECONDS)));
        if (local >= BLEND_SECONDS) return target;
        Pose entry = viewpoint == Viewpoint.FIRST_PERSON
                ? new Pose(playerYaw, initial.pitch, 0, 1, 0) : initial;
        Pose previous = index == 0 ? entry : shot(index - 1, environment, viewpoint).sample(1);
        double blend = smooth(local / BLEND_SECONDS);
        double yaw = previous.yaw + shortestAngle(target.yaw - previous.yaw) * blend;
        double viewYaw = previous.viewYaw + shortestAngle(target.viewYaw - previous.viewYaw) * blend;
        double previousOffset = shortestAngle(previous.viewYaw - previous.yaw);
        double targetOffset = shortestAngle(target.viewYaw - target.yaw);
        if (index > 0 && Math.abs(previousOffset) > 100 && Math.abs(targetOffset) > 100) {
            // Follow the outward framing through scenery blends instead of swivelling past the player.
            viewYaw = yaw + previousOffset + shortestAngle(targetOffset - previousOffset) * blend;
        }
        return new Pose(yaw,
                lerp(previous.pitch, target.pitch, blend), lerp(previous.distance, target.distance, blend),
                lerp(previous.fovScale, target.fovScale, blend), lerp(previous.roll, target.roll, blend),
                viewYaw,
                lerp(previous.viewPitch, target.viewPitch, blend));
    }

    private Shot shot(long index, Environment environment, Viewpoint selected) {
        Random random = new Random(seed ^ (index * 0x9E3779B97F4A7C15L));
        Viewpoint viewpoint = switch (selected) {
            case AUTO -> cycleViewpoint(index, AUTO_VIEWPOINTS, autoViewpoints, AUTO_SHUFFLE_SALT);
            case WORLD_TOUR -> cycleViewpoint(index, WORLD_VIEWPOINTS, worldViewpoints, WORLD_SHUFFLE_SALT);
            default -> selected;
        };
        double maxRadius = switch (environment) {
            case CAVE -> 3.5;
            case NETHER -> 5.5;
            case END -> 8.0;
            case OVERWORLD -> 10.0;
        };
        boolean cave = environment == Environment.CAVE;
        double direction = random.nextBoolean() ? 1 : -1;
        double yaw = playerYaw;
        double arc = direction * 6;
        double pitch = between(random, cave ? 4 : 8, cave ? 12 : 18);
        double lift = between(random, -2, 2);
        double distance = Math.min(maxRadius, 5) * between(random, 0.66, 0.9);
        double dolly = between(random, -0.15, 0.15);
        double fov = between(random, 0.88, 0.96);
        double roll = cave ? 0 : 0.55;
        boolean scenery = false;
        double viewYaw = 0;
        double viewArc = 0;
        double viewPitch = 0;
        double viewLift = 0;

        switch (viewpoint) {
            case FRONT, BACK, LEFT_SIDE, RIGHT_SIDE -> {
                yaw += switch (viewpoint) {
                    case FRONT -> 180;
                    case LEFT_SIDE -> 90;
                    case RIGHT_SIDE -> -90;
                    default -> 0;
                };
                yaw += between(random, -5, 5);
            }
            case ORBIT -> {
                yaw += between(random, -180, 180);
                arc = direction * 70;
                distance = maxRadius * between(random, 0.5, 0.66);
                pitch = between(random, cave ? 6 : 13, cave ? 14 : 25);
                dolly = direction * 0.25;
            }
            case CLOSE_UP -> {
                yaw += 180 + between(random, -8, 8);
                pitch = between(random, 4, 11);
                distance = between(random, 1.15, 1.6);
                dolly = direction * 0.12;
                fov = between(random, 0.94, 0.98);
                roll = cave ? 0 : 0.2;
            }
            case WIDE -> {
                yaw += 155 + between(random, -20, 20);
                arc = direction * 15;
                pitch = between(random, cave ? 10 : 20, cave ? 18 : 28);
                distance = maxRadius * between(random, 0.82, 0.92);
                dolly = direction * maxRadius * 0.04;
                fov = between(random, 0.78, 0.84);
            }
            case LOW_ANGLE -> {
                yaw += 180 + between(random, -10, 10);
                arc = direction * 10;
                pitch = switch (environment) {
                    case CAVE -> between(random, -9, -5);
                    case NETHER -> between(random, -14, -8);
                    default -> between(random, -18, -10);
                };
                lift = between(random, -1, 1);
                distance = Math.min(maxRadius, 4.7) * between(random, 0.68, 0.87);
                roll = cave ? 0 : 0.15;
            }
            case OVERHEAD -> {
                yaw += 45 + between(random, -20, 20);
                arc = direction * 15;
                pitch = switch (environment) {
                    case CAVE -> between(random, 32, 40);
                    case NETHER -> between(random, 48, 57);
                    default -> between(random, 68, 77);
                };
                distance = maxRadius * between(random, 0.5, 0.68);
                dolly = direction * 0.15;
                roll = cave ? 0 : 0.4;
            }
            case LANDSCAPE, PANORAMA, SKYWARD, HORIZON, AERIAL, GROUND_SWEEP, WORLD_ORBIT -> {
                scenery = true;
                yaw += between(random, -180, 180);
                arc = direction * (viewpoint == Viewpoint.PANORAMA ? 10 : 8);
                pitch = between(random, cave ? 6 : 12, cave ? 12 : 20);
                lift = between(random, -1, 1);
                distance = Math.min(maxRadius, 4) * between(random, 0.65, 0.95);
                dolly = direction * 0.1;
                roll = cave ? 0 : 0.2;
                viewYaw = yaw + between(random, 140, 175);
                viewArc = direction * 10;
                viewLift = between(random, -1, 1);
                switch (viewpoint) {
                    case LANDSCAPE -> {
                        viewPitch = between(random, 6, 14);
                        fov = 0.95;
                    }
                    case PANORAMA -> {
                        // Sweep the horizon while the camera itself only drifts a little.
                        viewYaw = yaw + 180 - direction * 30 + between(random, -8, 8);
                        viewArc = direction * 60;
                        viewPitch = between(random, -6, 6);
                        viewLift = between(random, -2, 2);
                        fov = 1;
                    }
                    case SKYWARD -> {
                        viewPitch = between(random, -62, -48);
                        viewLift = between(random, -2, 2);
                        fov = 0.95;
                    }
                    case HORIZON -> {
                        viewPitch = between(random, -1.5, 1.5);
                        viewLift = between(random, -0.75, 0.75);
                        fov = 1;
                        roll = cave ? 0 : 0.1;
                    }
                    case AERIAL -> {
                        pitch = switch (environment) {
                            case CAVE -> between(random, 25, 36);
                            case NETHER -> between(random, 35, 48);
                            default -> between(random, 42, 58);
                        };
                        distance = maxRadius * between(random, 0.62, 0.82);
                        viewPitch = between(random, 23, 32);
                        fov = 0.9;
                    }
                    case GROUND_SWEEP -> {
                        pitch = between(random, -8, -5);
                        arc = direction * 24;
                        viewArc = direction * 35;
                        viewPitch = between(random, 19, 27);
                        fov = 0.98;
                    }
                    case WORLD_ORBIT -> {
                        arc = direction * between(random, 90, 110);
                        viewArc = arc;
                        viewYaw = yaw + between(random, 150, 180);
                        distance = maxRadius * between(random, 0.58, 0.76);
                        viewPitch = between(random, 1, 9);
                        fov = 0.96;
                    }
                    default -> throw new IllegalStateException("Unexpected scenery viewpoint");
                }
            }
            case FIRST_PERSON -> {
                yaw += between(random, -2, 2);
                arc = direction * 2;
                pitch = Math.clamp(initial.pitch + between(random, -1.5, 1.5), -80, 80);
                lift = Math.clamp(pitch + direction, -80, 80) - pitch;
                distance = 0;
                dolly = 0;
                fov = 1;
                roll = 0;
            }
            case AUTO, WORLD_TOUR -> throw new IllegalStateException("Random viewpoint must be resolved first");
        }
        // Bound both endpoints; easing is monotonic, so every intermediate dolly stays bounded too.
        if (viewpoint != Viewpoint.FIRST_PERSON) {
            distance = Math.clamp(distance, 1, maxRadius);
            dolly = Math.clamp(distance + dolly, 1, maxRadius) - distance;
        }
        return new Shot(yaw, arc, pitch, lift, distance, dolly, fov, roll,
                scenery ? viewYaw : yaw, scenery ? viewArc : arc,
                scenery ? viewPitch : pitch, scenery ? viewLift : lift);
    }

    private static double between(Random random, double min, double max) {
        return min + random.nextDouble() * (max - min);
    }

    private Viewpoint cycleViewpoint(long index, Viewpoint[] pool, Viewpoint[] firstCycle, long salt) {
        long cycle = index / pool.length;
        if (cycle == 0) return firstCycle[(int) index];
        Viewpoint[] current = shuffledViewpoints(cycle, pool, salt);
        Viewpoint[] previous = cycle == 1 ? firstCycle : shuffledViewpoints(cycle - 1, pool, salt);
        // Swapping only the first two leaves the last shot unchanged, so this also works out of order.
        if (current[0] == previous[previous.length - 1]) {
            Viewpoint swap = current[0];
            current[0] = current[1];
            current[1] = swap;
        }
        return current[(int) (index % current.length)];
    }

    private Viewpoint[] shuffledViewpoints(long cycle, Viewpoint[] pool, long salt) {
        Viewpoint[] viewpoints = pool.clone();
        Random random = new Random(seed ^ salt ^ (cycle * 0x94D049BB133111EBL));
        for (int i = viewpoints.length - 1; i > 0; i--) {
            int other = random.nextInt(i + 1);
            Viewpoint swap = viewpoints[i];
            viewpoints[i] = viewpoints[other];
            viewpoints[other] = swap;
        }
        return viewpoints;
    }

    private static double smooth(double value) {
        double t = Math.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    public static double shortestAngle(double angle) { return ((angle + 180) % 360 + 360) % 360 - 180; }
}

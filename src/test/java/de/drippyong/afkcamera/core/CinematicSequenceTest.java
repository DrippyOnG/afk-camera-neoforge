package de.drippyong.afkcamera.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import de.drippyong.afkcamera.core.CinematicSequence.Environment;
import de.drippyong.afkcamera.core.CinematicSequence.Motion;
import de.drippyong.afkcamera.core.CinematicSequence.Pose;
import de.drippyong.afkcamera.core.CinematicSequence.Viewpoint;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

class CinematicSequenceTest {
    @Test void firstPersonEntryStartsAtEyePositionAndPullsBackSmoothly() {
        var sequence = new CinematicSequence(42, 10, 0, 0);
        assertEquals(0, sequence.sample(0, Environment.OVERWORLD, Motion.DEFAULT).distance());
        assertTrue(sequence.sample(0.001, Environment.OVERWORLD, Motion.DEFAULT).distance() < 0.0001);
        assertTrue(sequence.sample(3, Environment.OVERWORLD, Motion.DEFAULT).distance() > 2);
    }

    @Test void startsAtOriginalViewAndEasesIn() {
        var sequence = new CinematicSequence(42, 179, -20);
        assertEquals(new Pose(179, -20, 4, 1, 0), sequence.sample(0, Environment.OVERWORLD, Motion.DEFAULT));
        assertEquals(sequence.sample(0, Environment.OVERWORLD, Motion.DEFAULT),
                sequence.sample(-1, Environment.OVERWORLD, Motion.DEFAULT));
        assertEquals(179, sequence.sample(0.001, Environment.OVERWORLD, Motion.DEFAULT).yaw(), 0.001);
    }

    @Test void frontBackAndSidesReferencePlayerFacingRatherThanInitialCameraFacing() {
        double playerYaw = 31;
        var sequence = new CinematicSequence(42, playerYaw + 180, -12, 4, playerYaw);
        for (int shot = 0; shot < 30; shot++) {
            double time = shot * 14 + 7;
            assertFacing(sequence, time, Viewpoint.FRONT, playerYaw + 180);
            assertFacing(sequence, time, Viewpoint.BACK, playerYaw);
            assertFacing(sequence, time, Viewpoint.LEFT_SIDE, playerYaw + 90);
            assertFacing(sequence, time, Viewpoint.RIGHT_SIDE, playerYaw - 90);
        }
        assertEquals(new Pose(playerYaw + 180, -12, 4, 1, 0),
                sequence.sample(0, Environment.OVERWORLD, Motion.DEFAULT, Viewpoint.BACK));
    }

    @Test void fixedFramingPreservesDistinctCloseWideLowAndOverheadViewsAcrossShots() {
        var sequence = new CinematicSequence(123, 0, 0);
        for (int shot = 0; shot < 30; shot++) {
            double time = shot * 14 + 7;
            Pose close = sequence.sample(time, Environment.OVERWORLD, Motion.DEFAULT, Viewpoint.CLOSE_UP);
            Pose wide = sequence.sample(time, Environment.OVERWORLD, Motion.DEFAULT, Viewpoint.WIDE);
            Pose low = sequence.sample(time, Environment.OVERWORLD, Motion.DEFAULT, Viewpoint.LOW_ANGLE);
            Pose overhead = sequence.sample(time, Environment.OVERWORLD, Motion.DEFAULT, Viewpoint.OVERHEAD);
            assertTrue(close.distance() < 1.8, "Close-up should stay near the player");
            assertTrue(wide.distance() > 7.5, "Wide view should visibly frame the surroundings");
            assertTrue(low.pitch() < -8, "Low-angle view should look upward toward the player");
            assertTrue(overhead.pitch() > 60, "Overhead view should look down from above");
        }
    }

    @Test void fixedFirstPersonStaysAtEyesWithGentleMotionAndNoZoomOrRoll() {
        double playerYaw = 31;
        var sequence = new CinematicSequence(42, playerYaw + 180, -20, 4, playerYaw);
        assertEquals(new Pose(playerYaw, -20, 0, 1, 0),
                sequence.sample(0, Environment.OVERWORLD, Motion.DEFAULT, Viewpoint.FIRST_PERSON));
        for (var environment : Environment.values()) {
            for (double time = 0; time < 900; time += 0.17) {
                Pose pose = sequence.sample(time, environment, Motion.HIGH, Viewpoint.FIRST_PERSON);
                assertEquals(0, pose.distance());
                assertEquals(1, pose.fovScale());
                assertEquals(0, pose.roll());
                assertEquals(pose.yaw(), pose.viewYaw());
                assertEquals(pose.pitch(), pose.viewPitch());
                assertTrue(Math.abs(CinematicSequence.shortestAngle(pose.yaw() - playerYaw)) <= 4);
                assertEquals(-20, pose.pitch(), 2.5);
            }
        }
    }

    @Test void sceneryLooksOutwardIndependentlyOfElevatedCameraPosition() {
        var sequence = new CinematicSequence(42, 0, 0);
        for (var environment : Environment.values()) {
            for (var viewpoint : List.of(Viewpoint.LANDSCAPE, Viewpoint.PANORAMA, Viewpoint.SKYWARD)) {
                for (int shot = 0; shot < 30; shot++) {
                    Pose pose = sequence.sample(shot * 14 + 7, environment, Motion.DEFAULT, viewpoint);
                    assertTrue(pose.pitch() > 4, "Scenery camera should sit above eye level");
                    assertTrue(pose.distance() >= 2 && pose.distance() <= 4);
                    assertTrue(Math.abs(CinematicSequence.shortestAngle(pose.viewYaw() - pose.yaw())) > 100,
                            "Scenery must look outward rather than continue focusing the player");
                    switch (viewpoint) {
                        case LANDSCAPE -> assertTrue(pose.viewPitch() >= 5 && pose.viewPitch() <= 15);
                        case PANORAMA -> assertTrue(Math.abs(pose.viewPitch()) <= 8);
                        case SKYWARD -> assertTrue(pose.viewPitch() >= -65 && pose.viewPitch() <= -45);
                        default -> fail("Expected scenery viewpoint");
                    }
                }
            }
        }
    }

    @Test void panoramaSweepsTheHorizonWithoutOrbitingThePlayerAtTheSameSpeed() {
        var sequence = new CinematicSequence(42, 0, 0);
        Pose start = sequence.sample(2.5, Environment.OVERWORLD, Motion.DEFAULT, Viewpoint.PANORAMA);
        Pose end = sequence.sample(13.999, Environment.OVERWORLD, Motion.DEFAULT, Viewpoint.PANORAMA);
        assertEquals(60, Math.abs(CinematicSequence.shortestAngle(end.viewYaw() - start.viewYaw())), 0.001);
        assertEquals(10, Math.abs(CinematicSequence.shortestAngle(end.yaw() - start.yaw())), 0.001);
    }

    @Test void playerFocusedPresetsKeepTheirViewAlignedWithTheCameraPosition() {
        var sequence = new CinematicSequence(42, 0, 0);
        for (var viewpoint : List.of(Viewpoint.ORBIT, Viewpoint.FRONT, Viewpoint.BACK,
                Viewpoint.LEFT_SIDE, Viewpoint.RIGHT_SIDE, Viewpoint.CLOSE_UP,
                Viewpoint.WIDE, Viewpoint.LOW_ANGLE, Viewpoint.OVERHEAD)) {
            for (double time = 0; time < 100; time += 0.37) {
                Pose pose = sequence.sample(time, Environment.OVERWORLD, Motion.DEFAULT, viewpoint);
                assertEquals(pose.yaw(), pose.viewYaw());
                assertEquals(pose.pitch(), pose.viewPitch());
            }
        }
    }

    @Test void automaticCycleShowsEveryThirdPersonPresetWithoutAdjacentRepeats() {
        int presetCount = Viewpoint.values().length - 2;
        for (int seed = 0; seed < 10; seed++) {
            var sequence = new CinematicSequence(seed, 0, 0);
            Viewpoint previous = null;
            for (int cycle = 0; cycle < 3; cycle++) {
                var seen = EnumSet.noneOf(Viewpoint.class);
                for (int slot = 0; slot < presetCount; slot++) {
                    Viewpoint current = automaticViewpoint(sequence, (cycle * presetCount + slot) * 14 + 7);
                    assertNotEquals(previous, current, "Adjacent automatic shots should change perspective");
                    assertNotEquals(Viewpoint.FIRST_PERSON, current);
                    assertTrue(seen.add(current), "Each cycle should show a new perspective");
                    previous = current;
                }
                assertEquals(presetCount, seen.size());
                assertTrue(seen.containsAll(List.of(Viewpoint.LANDSCAPE, Viewpoint.PANORAMA, Viewpoint.SKYWARD)));
            }
        }
    }

    @Test void automaticCycleIsSeededAndLegacySampleDelegatesToIt() {
        var sequence = new CinematicSequence(42, 0, 0);
        var sameSeed = new CinematicSequence(42, 0, 0);
        var anotherSeed = new CinematicSequence(987, 0, 0);
        List<Viewpoint> firstCycle = new ArrayList<>();
        List<Viewpoint> otherCycle = new ArrayList<>();
        List<Viewpoint> nextCycle = new ArrayList<>();
        for (int shot = 0; shot < Viewpoint.values().length - 2; shot++) {
            double time = shot * 14 + 7;
            Pose automatic = sequence.sample(time, Environment.OVERWORLD, Motion.DEFAULT, Viewpoint.AUTO);
            assertEquals(automatic, sequence.sample(time, Environment.OVERWORLD, Motion.DEFAULT));
            assertEquals(automatic, sameSeed.sample(time, Environment.OVERWORLD, Motion.DEFAULT, Viewpoint.AUTO));
            firstCycle.add(automaticViewpoint(sequence, time));
            otherCycle.add(automaticViewpoint(anotherSeed, time));
            double nextTime = time + (Viewpoint.values().length - 2) * 14;
            nextCycle.add(automaticViewpoint(sequence, nextTime));
            assertEquals(sequence.sample(nextTime, Environment.OVERWORLD, Motion.DEFAULT),
                    sameSeed.sample(nextTime, Environment.OVERWORLD, Motion.DEFAULT));
        }
        assertNotEquals(firstCycle, otherCycle);
        assertNotEquals(firstCycle, nextCycle, "Automatic mode should reshuffle for each new cycle");
    }

    @Test void everyShotBoundaryIsContinuousIncludingYawWrap() {
        var sequence = new CinematicSequence(987, 355, 12);
        for (var environment : Environment.values()) {
            for (var motion : Motion.values()) {
                for (var viewpoint : Viewpoint.values()) {
                    // Dense sampling includes every shot/blend boundary and both sides of yaw wrap.
                    for (double time = 0.025; time < 180; time += 0.025) {
                        Pose before = sequence.sample(time - 0.001, environment, motion, viewpoint);
                        Pose after = sequence.sample(time, environment, motion, viewpoint);
                        assertTrue(Math.abs(CinematicSequence.shortestAngle(after.yaw() - before.yaw())) < 0.25,
                                "Abrupt yaw at " + time + " in " + environment + "/" + motion + "/" + viewpoint);
                        assertEquals(before.pitch(), after.pitch(), 0.1);
                        assertEquals(before.distance(), after.distance(), 0.05);
                        assertEquals(before.fovScale(), after.fovScale(), 0.005);
                        assertEquals(before.roll(), after.roll(), 0.05);
                        assertTrue(Math.abs(CinematicSequence.shortestAngle(after.viewYaw() - before.viewYaw())) < 0.25,
                                "Abrupt look yaw at " + time + " in " + environment + "/" + motion + "/" + viewpoint);
                        assertEquals(before.viewPitch(), after.viewPitch(), 0.15);
                    }
                }
            }
        }
    }

    @Test void allDimensionsKeepShotsBoundedDuringLongSessions() {
        for (int seed = 0; seed < 10; seed++) {
            var sequence = new CinematicSequence(seed, -150, 0);
            for (var environment : Environment.values()) {
                for (var viewpoint : Viewpoint.values()) {
                    for (int time = 3; time < 7200; time += 11) {
                        var pose = sequence.sample(time, environment, Motion.DEFAULT, viewpoint);
                        assertTrue(Double.isFinite(pose.yaw()));
                        assertTrue(Double.isFinite(pose.viewYaw()));
                        assertTrue(pose.pitch() >= -20 && pose.pitch() <= 80);
                        assertTrue(pose.viewPitch() >= -65 && pose.viewPitch() <= 80);
                        assertTrue(pose.distance() >= (viewpoint == Viewpoint.FIRST_PERSON ? 0 : 1));
                        assertTrue(pose.distance() <= 10);
                        assertTrue(pose.fovScale() >= 0.7 && pose.fovScale() <= 1);
                        assertTrue(Math.abs(pose.roll()) <= 0.7);
                        if (environment == Environment.CAVE) {
                            assertTrue(pose.distance() <= 3.5);
                            assertTrue(pose.pitch() <= 42);
                        }
                        if (environment == Environment.NETHER) {
                            assertTrue(pose.distance() <= 5.5);
                            assertTrue(pose.pitch() <= 59);
                        }
                        if (environment == Environment.END) assertTrue(pose.distance() <= 8);
                    }
                }
            }
        }
    }

    @Test void lowMotionActuallyMovesLessThanHighMotion() {
        var sequence = new CinematicSequence(1, 0, 0);
        double low = Math.abs(CinematicSequence.shortestAngle(sequence.sample(1, Environment.OVERWORLD, Motion.LOW).yaw()));
        double high = Math.abs(CinematicSequence.shortestAngle(sequence.sample(1, Environment.OVERWORLD, Motion.HIGH).yaw()));
        assertTrue(low < high);
    }

    @Test void rejectsNonFiniteTimeInsteadOfProducingCorruptCameraCoordinates() {
        var sequence = new CinematicSequence(1, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> sequence.sample(Double.NaN, Environment.END, Motion.DEFAULT));
        assertThrows(IllegalArgumentException.class, () -> sequence.sample(Double.POSITIVE_INFINITY, Environment.END, Motion.DEFAULT));
        assertThrows(IllegalArgumentException.class, () -> sequence.sample(Double.MAX_VALUE, Environment.END, Motion.HIGH));
    }

    private static void assertFacing(CinematicSequence sequence, double time, Viewpoint viewpoint, double expectedYaw) {
        Pose pose = sequence.sample(time, Environment.OVERWORLD, Motion.DEFAULT, viewpoint);
        assertEquals(0, CinematicSequence.shortestAngle(pose.yaw() - expectedYaw), 11,
                viewpoint + " must remain relative to the player's facing across shots");
    }

    private static Viewpoint automaticViewpoint(CinematicSequence sequence, double time) {
        Pose automatic = sequence.sample(time, Environment.OVERWORLD, Motion.DEFAULT, Viewpoint.AUTO);
        for (var viewpoint : Viewpoint.values()) {
            if (viewpoint != Viewpoint.AUTO && viewpoint != Viewpoint.FIRST_PERSON
                    && automatic.equals(sequence.sample(time, Environment.OVERWORLD, Motion.DEFAULT, viewpoint))) {
                return viewpoint;
            }
        }
        fail("Automatic shot must match a third-person preset");
        return null;
    }
}

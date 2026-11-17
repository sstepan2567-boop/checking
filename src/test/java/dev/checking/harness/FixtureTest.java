package dev.checking.harness;

import dev.checking.harness.fixtures.GrimFixtures;
import dev.checking.harness.fixtures.MatrixFixtures;
import dev.checking.harness.fixtures.PacketSequenceFixture;
import dev.checking.harness.fixtures.TimedPacket;
import dev.checking.harness.packets.ActionPacket;
import dev.checking.harness.packets.ActionType;
import dev.checking.harness.packets.BlockPos;
import dev.checking.harness.packets.Direction;
import dev.checking.harness.packets.InteractPacket;
import dev.checking.harness.packets.InteractType;
import dev.checking.harness.packets.MovePacket;
import dev.checking.harness.packets.TransactionPacket;
import dev.checking.harness.packets.TransactionType;
import dev.checking.harness.packets.VelocityPacket;
import dev.checking.harness.validation.ServerState;
import dev.checking.harness.validation.ValidationResult;
import dev.checking.harness.validation.ValidatorStub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test suite verifying construction and {@link ValidatorStub} replay for all 16 GrimAC and Matrix 7.x
 * protocol boundary fixtures.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FixtureTest {

    private ValidatorStub validator;

    @BeforeEach
    void setUp() {
        validator = new ValidatorStub();
    }

    // -------------------------------------------------------------------------
    // Builder sanity check
    // -------------------------------------------------------------------------

    @Test
    @Order(0)
    @DisplayName("PacketSequenceFixture builder validates inputs and returns immutable sequence")
    void testPacketSequenceFixtureBuilder() {
        assertThrows(IllegalArgumentException.class, () -> PacketSequenceFixture.named("   "));
        assertThrows(IllegalArgumentException.class, () -> new TimedPacket(
                new TransactionPacket(1, TransactionType.PING), -1));

        List<TimedPacket> seq = PacketSequenceFixture.named("custom")
                .addImmediate(new TransactionPacket(1, TransactionType.PING))
                .addNextTick(new TransactionPacket(1, TransactionType.PONG))
                .build();

        assertEquals(2, seq.size());
        assertEquals(List.of(0, 1), delaysOf(seq));
        assertThrows(UnsupportedOperationException.class, () -> seq.add(
                new TimedPacket(new TransactionPacket(2, TransactionType.PING), 1)));
    }

    // -------------------------------------------------------------------------
    // GrimAC fixtures (grim.md)
    // -------------------------------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("§2.1 grim.md | fixturePointThreeSkip: hasPosition=false followed by 0.029 delta")
    void testFixturePointThreeSkip() {
        List<TimedPacket> seq = GrimFixtures.fixturePointThreeSkip();
        ServerState state = ServerState.forFixture("fixturePointThreeSkip");
        List<ValidationResult> results = validator.replay(state, seq);

        assertEquals(3, seq.size());
        assertEquals(3, results.size());
        assertEquals(3, state.getProcessedPackets());
        assertEquals(List.of(0, 1, 1), delaysOf(seq));
        assertEquals("§2.1 grim.md", results.getFirst().documentRef());

        MovePacket base = assertInstanceOf(MovePacket.class, seq.get(0).packet());
        MovePacket skipped = assertInstanceOf(MovePacket.class, seq.get(1).packet());
        MovePacket compensated = assertInstanceOf(MovePacket.class, seq.get(2).packet());

        assertTrue(base.hasPosition());
        assertFalse(skipped.hasPosition());
        assertTrue(compensated.hasPosition());

        double delta = Math.abs(compensated.x() - base.x());
        assertEquals(0.029, delta, 1e-9);
        assertTrue(delta < 0.03, "Compensated delta must remain within 0.03 window");
    }

    @Test
    @Order(2)
    @DisplayName("§2.2 grim.md | fixtureVelocityBoundary: components 0.0031 (kept) and 0.0029 (zeroed)")
    void testFixtureVelocityBoundary() {
        List<TimedPacket> seq = GrimFixtures.fixtureVelocityBoundary();
        ServerState state = ServerState.forFixture("fixtureVelocityBoundary");
        List<ValidationResult> results = validator.replay(state, seq);

        assertEquals(8, seq.size());
        assertEquals(8, results.size());
        assertEquals(List.of(0, 0, 1, 1, 1, 0, 1, 1), delaysOf(seq));
        assertEquals("§2.2 grim.md", results.getFirst().documentRef());

        VelocityPacket above = assertInstanceOf(VelocityPacket.class, seq.get(1).packet());
        MovePacket afterAbove = assertInstanceOf(MovePacket.class, seq.get(3).packet());
        VelocityPacket below = assertInstanceOf(VelocityPacket.class, seq.get(5).packet());
        MovePacket afterBelow = assertInstanceOf(MovePacket.class, seq.get(7).packet());

        assertEquals(0.0031, above.vx(), 1e-12);
        assertTrue(above.vx() > GrimFixtures.VELOCITY_ZERO_THRESHOLD);
        assertEquals(0.0031, afterAbove.x(), 1e-12);

        assertEquals(0.0029, below.vx(), 1e-12);
        assertTrue(below.vx() < GrimFixtures.VELOCITY_ZERO_THRESHOLD);
        assertEquals(0.0, afterBelow.x() - afterAbove.x(), 1e-12,
                "Sub-threshold velocity 0.0029 must be zeroed");
    }

    @Test
    @Order(3)
    @DisplayName("§2.3 grim.md | fixtureGroundSpoofNearEntity: onGround=true within radius 4 of boat/shulker")
    void testFixtureGroundSpoofNearEntity() {
        List<TimedPacket> seq = GrimFixtures.fixtureGroundSpoofNearEntity();
        ServerState state = ServerState.forFixture("fixtureGroundSpoofNearEntity");
        List<ValidationResult> results = validator.replay(state, seq);

        assertEquals(3, seq.size());
        assertEquals(3, results.size());
        assertEquals(List.of(0, 1, 1), delaysOf(seq));
        assertEquals("§2.3 grim.md", results.getFirst().documentRef());

        MovePacket outside = assertInstanceOf(MovePacket.class, seq.get(0).packet());
        MovePacket inside1 = assertInstanceOf(MovePacket.class, seq.get(1).packet());
        MovePacket inside2 = assertInstanceOf(MovePacket.class, seq.get(2).packet());

        assertFalse(outside.onGround());
        assertTrue(distanceToBoat(outside) > GrimFixtures.GROUND_EXEMPTION_RADIUS);

        assertTrue(inside1.onGround());
        assertTrue(distanceToBoat(inside1) <= GrimFixtures.GROUND_EXEMPTION_RADIUS);

        assertTrue(inside2.onGround());
        assertTrue(distanceToBoat(inside2) <= GrimFixtures.GROUND_EXEMPTION_RADIUS);
    }

    @Test
    @Order(4)
    @DisplayName("§3.1 grim.md | fixtureReachWithSkip: hasPosition=false then InteractPacket within 3.03")
    void testFixtureReachWithSkip() {
        List<TimedPacket> seq = GrimFixtures.fixtureReachWithSkip();
        ServerState state = ServerState.forFixture("fixtureReachWithSkip");
        List<ValidationResult> results = validator.replay(state, seq);

        assertEquals(4, seq.size());
        assertEquals(4, results.size());
        assertEquals(List.of(0, 1, 0, 1), delaysOf(seq));
        assertEquals("§3.1 grim.md", results.getFirst().documentRef());

        MovePacket anchor = assertInstanceOf(MovePacket.class, seq.get(0).packet());
        MovePacket skip = assertInstanceOf(MovePacket.class, seq.get(1).packet());
        InteractPacket attack = assertInstanceOf(InteractPacket.class, seq.get(2).packet());
        MovePacket followUp = assertInstanceOf(MovePacket.class, seq.get(3).packet());

        assertTrue(anchor.hasPosition());
        assertFalse(skip.hasPosition());
        assertEquals(GrimFixtures.REACH_TARGET_ID, attack.targetId());
        assertEquals(InteractType.ATTACK, attack.type());

        double distanceToHitboxFace = (GrimFixtures.REACH_TARGET_CENTER_X - GrimFixtures.PLAYER_HALF_WIDTH) - anchor.x();
        assertTrue(distanceToHitboxFace > 3.00, "Reach should exceed base 3.00 before skip expansion");
        assertTrue(distanceToHitboxFace <= GrimFixtures.REACH_LIMIT_WITH_SKIP,
                "Reach must remain within 3.03 skip-expanded threshold");
        assertTrue(Math.abs(followUp.x() - anchor.x()) < 0.03);
    }

    @Test
    @Order(5)
    @DisplayName("§3.2 grim.md | fixtureTimerDrift: >=120ms lag accumulation then accelerated burst")
    void testFixtureTimerDrift() {
        List<TimedPacket> seq = GrimFixtures.fixtureTimerDrift();
        ServerState state = ServerState.forFixture("fixtureTimerDrift");
        List<ValidationResult> results = validator.replay(state, seq);

        assertEquals(7, seq.size());
        assertEquals(7, results.size());
        assertEquals(List.of(0, 2, 3, 0, 0, 0, 0), delaysOf(seq));
        assertEquals("§3.2 grim.md", results.getFirst().documentRef());

        // Drift accumulation: packets 1 & 2 have extra ticks (2-1) + (3-1) = 3 ticks = 150ms >= 120ms
        int accumulatedLagMs = ((seq.get(1).delayTicks() - 1) + (seq.get(2).delayTicks() - 1)) * 50;
        assertTrue(accumulatedLagMs >= 120, "Must accumulate at least 120ms of timer drift");

        // Burst phase: 4 consecutive zero-delay MovePackets
        for (int i = 3; i < 7; i++) {
            assertEquals(0, seq.get(i).delayTicks());
            MovePacket move = assertInstanceOf(MovePacket.class, seq.get(i).packet());
            assertTrue(move.hasPosition());
        }
        assertEquals(5, state.getElapsedTicks());
    }

    @Test
    @Order(6)
    @DisplayName("§4.1 grim.md | fixtureTransactionDelay: delayed PONGs with preserved FIFO order")
    void testFixtureTransactionDelay() {
        List<TimedPacket> seq = GrimFixtures.fixtureTransactionDelay();
        ServerState state = ServerState.forFixture("fixtureTransactionDelay");
        List<ValidationResult> results = validator.replay(state, seq);

        assertEquals(8, seq.size());
        assertEquals(8, results.size());
        assertEquals(List.of(0, 1, 1, 1, 5, 1, 2, 1), delaysOf(seq));
        assertEquals("§4.1 grim.md", results.getFirst().documentRef());

        List<Integer> pingIds = new ArrayList<>();
        List<Integer> pongIds = new ArrayList<>();
        for (TimedPacket tp : seq) {
            TransactionPacket tx = assertInstanceOf(TransactionPacket.class, tp.packet());
            if (tx.type() == TransactionType.PING) {
                pingIds.add(tx.id());
            } else if (tx.type() == TransactionType.PONG) {
                pongIds.add(tx.id());
            }
        }

        assertEquals(List.of(2001, 2002, 2003, 2004), pingIds);
        assertEquals(pingIds, pongIds, "PONG order must strictly preserve PING FIFO order");
        assertTrue(seq.get(4).delayTicks() >= 5, "First PONG must arrive after noticeable RTT delay");
    }

    @Test
    @Order(7)
    @DisplayName("§4.2 grim.md | fixtureNoSlowHandSwap: SWAP_ITEM_WITH_OFFHAND in item-use tick then MovePacket")
    void testFixtureNoSlowHandSwap() {
        List<TimedPacket> seq = GrimFixtures.fixtureNoSlowHandSwap();
        ServerState state = ServerState.forFixture("fixtureNoSlowHandSwap");
        List<ValidationResult> results = validator.replay(state, seq);

        assertEquals(5, seq.size());
        assertEquals(5, results.size());
        assertEquals(List.of(0, 1, 0, 0, 1), delaysOf(seq));
        assertEquals("§4.2 grim.md", results.getFirst().documentRef());

        ActionPacket useItem = assertInstanceOf(ActionPacket.class, seq.get(1).packet());
        ActionPacket swapOffhand = assertInstanceOf(ActionPacket.class, seq.get(2).packet());
        MovePacket moveAfterSwap = assertInstanceOf(MovePacket.class, seq.get(3).packet());
        MovePacket nextMove = assertInstanceOf(MovePacket.class, seq.get(4).packet());

        assertEquals(ActionType.USE_ITEM, useItem.action());
        assertEquals(ActionType.SWAP_ITEM_WITH_OFFHAND, swapOffhand.action());
        assertEquals(0, seq.get(2).delayTicks(), "Offhand swap must occur in the same tick as USE_ITEM");
        assertEquals(0, seq.get(3).delayTicks(), "MovePacket follows in the same tick");
        assertEquals(0.28, moveAfterSwap.x(), 1e-9);
        assertEquals(0.56, nextMove.x(), 1e-9);
    }

    @Test
    @Order(8)
    @DisplayName("§4.3 grim.md | fixturePhaseResync: place block, cancel, then MovePacket colliding with phantom block")
    void testFixturePhaseResync() {
        List<TimedPacket> seq = GrimFixtures.fixturePhaseResync();
        ServerState state = ServerState.forFixture("fixturePhaseResync");
        List<ValidationResult> results = validator.replay(state, seq);

        assertEquals(5, seq.size());
        assertEquals(5, results.size());
        assertEquals(List.of(0, 1, 0, 1, 1), delaysOf(seq));
        assertEquals("§4.3 grim.md", results.getFirst().documentRef());

        ActionPacket place = assertInstanceOf(ActionPacket.class, seq.get(1).packet());
        ActionPacket cancel = assertInstanceOf(ActionPacket.class, seq.get(2).packet());
        MovePacket touchFace = assertInstanceOf(MovePacket.class, seq.get(3).packet());
        MovePacket insidePhantom = assertInstanceOf(MovePacket.class, seq.get(4).packet());

        assertEquals(ActionType.PLACE_BLOCK, place.action());
        assertEquals(ActionType.CANCEL_PLACE_BLOCK, cancel.action());
        assertEquals(GrimFixtures.PHANTOM_BLOCK_POS, place.pos());
        assertEquals(place.pos(), cancel.pos());
        assertEquals(Direction.WEST, place.direction());

        // Player half-width = 0.30; at x=5.70 maxX == 6.00 (block minX); at x=6.05 player overlaps block [6,7]
        assertEquals(6.00, touchFace.x() + GrimFixtures.PLAYER_HALF_WIDTH, 1e-9);
        assertTrue(insidePhantom.x() >= GrimFixtures.PHANTOM_BLOCK_POS.x()
                && insidePhantom.x() < GrimFixtures.PHANTOM_BLOCK_POS.x() + 1.0);
    }

    // -------------------------------------------------------------------------
    // Matrix 7.x fixtures (matrix.md)
    // -------------------------------------------------------------------------

    @Test
    @Order(9)
    @DisplayName("§2.1 matrix.md | fixtureTimerCredit: 280 idle ticks then 20 ticks at >20Hz")
    void testFixtureTimerCredit() {
        List<TimedPacket> seq = MatrixFixtures.fixtureTimerCredit();
        ServerState state = ServerState.forFixture("fixtureTimerCredit");
        List<ValidationResult> results = validator.replay(state, seq);

        assertEquals(55, seq.size());
        assertEquals(55, results.size());
        assertEquals("§2.1 matrix.md", results.getFirst().documentRef());

        // Phase 1: first 15 packets cover exactly 280 ticks (0 + 14*20)
        int phase1Delay = 0;
        for (int i = 0; i < 15; i++) {
            assertInstanceOf(MovePacket.class, seq.get(i).packet());
            phase1Delay += seq.get(i).delayTicks();
        }
        assertEquals(280, phase1Delay, "Phase 1 must span exactly 280 idle ticks");

        // Phase 2: remaining 40 packets cover exactly 20 ticks (40 Hz > 20 Hz)
        int phase2Delay = 0;
        int phase2Packets = seq.size() - 15;
        for (int i = 15; i < seq.size(); i++) {
            assertInstanceOf(MovePacket.class, seq.get(i).packet());
            phase2Delay += seq.get(i).delayTicks();
        }
        assertEquals(20, phase2Delay, "Phase 2 must span exactly 20 ticks");
        double phase2Hz = (phase2Packets / (double) phase2Delay) * 20.0;
        assertTrue(phase2Hz > 20.0, "Phase 2 packet rate must exceed 20 Hz");
        assertEquals(40.0, phase2Hz, 1e-9);
        assertEquals(300, state.getElapsedTicks());
    }

    @Test
    @Order(10)
    @DisplayName("§2.2 matrix.md | fixtureVelocityEdge: MovePacket at tick 30 (near wait_time 33) and tick 31 with collision")
    void testFixtureVelocityEdge() {
        List<TimedPacket> seq = MatrixFixtures.fixtureVelocityEdge();
        ServerState state = ServerState.forFixture("fixtureVelocityEdge");
        List<ValidationResult> results = validator.replay(state, seq);

        assertEquals(3, seq.size());
        assertEquals(3, results.size());
        assertEquals(List.of(0, 30, 1), delaysOf(seq));
        assertEquals("§2.2 matrix.md", results.getFirst().documentRef());

        VelocityPacket velocity = assertInstanceOf(VelocityPacket.class, seq.get(0).packet());
        MovePacket tick30Move = assertInstanceOf(MovePacket.class, seq.get(1).packet());
        MovePacket tick31Collision = assertInstanceOf(MovePacket.class, seq.get(2).packet());

        assertTrue(velocity.vx() > 0.0 && velocity.vy() > 0.0);
        int elapsedAtFirstMove = seq.get(1).delayTicks();
        int elapsedAtCollisionMove = elapsedAtFirstMove + seq.get(2).delayTicks();

        assertEquals(30, elapsedAtFirstMove);
        assertEquals(31, elapsedAtCollisionMove);
        assertTrue(elapsedAtCollisionMove < MatrixFixtures.VELOCITY_WAIT_TIME_TICKS,
                "Both MovePackets must occur inside Matrix wait_time=33");
        assertEquals(tick30Move.x(), tick31Collision.x(), 1e-9,
                "Collision tick 31 clamps horizontal displacement to 0");
    }

    @Test
    @Order(11)
    @DisplayName("§3.1 matrix.md | fixtureHitboxHistory: 12 ticks target MovePacket history then InteractPacket <= 3.075")
    void testFixtureHitboxHistory() {
        List<TimedPacket> seq = MatrixFixtures.fixtureHitboxHistory();
        ServerState state = ServerState.forFixture("fixtureHitboxHistory");
        List<ValidationResult> results = validator.replay(state, seq);

        assertEquals(13, seq.size());
        assertEquals(13, results.size());
        assertEquals("§3.1 matrix.md", results.getFirst().documentRef());

        for (int i = 0; i < MatrixFixtures.HITBOX_HISTORY_TICKS; i++) {
            MovePacket targetMove = assertInstanceOf(MovePacket.class, seq.get(i).packet());
            assertEquals(i == 0 ? 0 : 1, seq.get(i).delayTicks());
            assertTrue(targetMove.hasPosition());
        }

        MovePacket oldest = assertInstanceOf(MovePacket.class, seq.get(0).packet());
        MovePacket newest = assertInstanceOf(MovePacket.class, seq.get(11).packet());
        InteractPacket interact = assertInstanceOf(InteractPacket.class, seq.get(12).packet());

        assertEquals(0, seq.get(12).delayTicks());
        assertEquals(MatrixFixtures.HITBOX_TARGET_ID, interact.targetId());
        assertEquals(InteractType.ATTACK, interact.type());

        double oldestHitboxDistance = oldest.x() - MatrixFixtures.TARGET_HALF_WIDTH;
        double newestHitboxDistance = newest.x() - MatrixFixtures.TARGET_HALF_WIDTH;
        assertTrue(oldestHitboxDistance <= MatrixFixtures.HITBOX_REACH_LIMIT,
                "Historical hitbox position must lie within 3.075");
        assertTrue(newestHitboxDistance > MatrixFixtures.HITBOX_REACH_LIMIT,
                "Current target position must exceed 3.075 so history lookup is required");
    }

    @Test
    @Order(12)
    @DisplayName("§3.2 matrix.md | fixtureSetbackWindow: anomaly, SETBACK transaction, then 5 ticks onGround=true")
    void testFixtureSetbackWindow() {
        List<TimedPacket> seq = MatrixFixtures.fixtureSetbackWindow();
        ServerState state = ServerState.forFixture("fixtureSetbackWindow");
        List<ValidationResult> results = validator.replay(state, seq);

        assertEquals(8, seq.size());
        assertEquals(8, results.size());
        assertEquals(List.of(0, 1, 0, 1, 1, 1, 1, 1), delaysOf(seq));
        assertEquals("§3.2 matrix.md", results.getFirst().documentRef());

        MovePacket baseline = assertInstanceOf(MovePacket.class, seq.get(0).packet());
        MovePacket anomaly = assertInstanceOf(MovePacket.class, seq.get(1).packet());
        TransactionPacket setback = assertInstanceOf(TransactionPacket.class, seq.get(2).packet());

        assertTrue(Math.abs(anomaly.x() - baseline.x()) > 4.0, "Anomaly must have large single-tick displacement");
        assertFalse(anomaly.onGround());
        assertEquals(TransactionType.SETBACK, setback.type());

        for (int i = 3; i < 8; i++) {
            MovePacket postSetback = assertInstanceOf(MovePacket.class, seq.get(i).packet());
            assertTrue(postSetback.onGround(), "Post-setback tick " + (i - 2) + " must report onGround=true");
            assertEquals(baseline.x(), postSetback.x(), 1e-9);
            assertEquals(baseline.y(), postSetback.y(), 1e-9);
        }
    }

    @Test
    @Order(13)
    @DisplayName("§4.1 matrix.md | fixtureScaffoldCadence: place every 6 (straight), 4 (diagonal), 3 (violation) ticks")
    void testFixtureScaffoldCadence() {
        List<TimedPacket> seq = MatrixFixtures.fixtureScaffoldCadence();
        ServerState state = ServerState.forFixture("fixtureScaffoldCadence");
        List<ValidationResult> results = validator.replay(state, seq);

        assertEquals(9, seq.size());
        assertEquals(9, results.size());
        assertEquals(List.of(0, 6, 6, 4, 4, 4, 3, 3, 3), delaysOf(seq));
        assertEquals("§4.1 matrix.md", results.getFirst().documentRef());

        for (TimedPacket tp : seq) {
            ActionPacket action = assertInstanceOf(ActionPacket.class, tp.packet());
            assertEquals(ActionType.PLACE_BLOCK, action.action());
        }

        // Verify distinct sequential placement coordinates
        ActionPacket p0 = (ActionPacket) seq.get(0).packet();
        ActionPacket p1 = (ActionPacket) seq.get(1).packet();
        ActionPacket p3 = (ActionPacket) seq.get(3).packet();
        assertEquals(new BlockPos(10, 63, 10), p0.pos());
        assertEquals(new BlockPos(11, 63, 10), p1.pos());
        assertEquals(new BlockPos(12, 63, 11), p3.pos());
    }

    @Test
    @Order(14)
    @DisplayName("§4.2 matrix.md | fixturePoseSwitch: PRESS_SHIFT and RELEASE_SHIFT bracketing MovePackets")
    void testFixturePoseSwitch() {
        List<TimedPacket> seq = MatrixFixtures.fixturePoseSwitch();
        ServerState state = ServerState.forFixture("fixturePoseSwitch");
        List<ValidationResult> results = validator.replay(state, seq);

        assertEquals(6, seq.size());
        assertEquals(6, results.size());
        assertEquals(List.of(0, 1, 0, 1, 0, 1), delaysOf(seq));
        assertEquals("§4.2 matrix.md", results.getFirst().documentRef());

        assertInstanceOf(MovePacket.class, seq.get(0).packet());
        ActionPacket pressShift = assertInstanceOf(ActionPacket.class, seq.get(1).packet());
        MovePacket crouchMove1 = assertInstanceOf(MovePacket.class, seq.get(2).packet());
        MovePacket crouchMove2 = assertInstanceOf(MovePacket.class, seq.get(3).packet());
        ActionPacket releaseShift = assertInstanceOf(ActionPacket.class, seq.get(4).packet());
        MovePacket standMove = assertInstanceOf(MovePacket.class, seq.get(5).packet());

        assertEquals(ActionType.PRESS_SHIFT, pressShift.action());
        assertEquals(ActionType.RELEASE_SHIFT, releaseShift.action());
        assertEquals(0.065, crouchMove2.x() - crouchMove1.x(), 1e-9);
        assertTrue(standMove.x() - crouchMove2.x() > crouchMove2.x() - crouchMove1.x());
    }

    @Test
    @Order(15)
    @DisplayName("§4.3 matrix.md | fixtureInteractRays: InteractPacket in tight geometry under trapdoor")
    void testFixtureInteractRays() {
        List<TimedPacket> seq = MatrixFixtures.fixtureInteractRays();
        ServerState state = ServerState.forFixture("fixtureInteractRays");
        List<ValidationResult> results = validator.replay(state, seq);

        assertEquals(4, seq.size());
        assertEquals(4, results.size());
        assertEquals(List.of(0, 1, 0, 1), delaysOf(seq));
        assertEquals("§4.3 matrix.md", results.getFirst().documentRef());

        ActionPacket crouch = assertInstanceOf(ActionPacket.class, seq.get(0).packet());
        MovePacket underTrapdoor = assertInstanceOf(MovePacket.class, seq.get(1).packet());
        InteractPacket interactAt = assertInstanceOf(InteractPacket.class, seq.get(2).packet());
        InteractPacket attack = assertInstanceOf(InteractPacket.class, seq.get(3).packet());

        assertEquals(ActionType.PRESS_SHIFT, crouch.action());
        assertEquals(MatrixFixtures.TRAPDOOR_POS, crouch.pos());

        // Player stands directly underneath trapdoor block (20, 65, 20)
        assertEquals(MatrixFixtures.TRAPDOOR_POS.x() + 0.5, underTrapdoor.x(), 1e-9);
        assertEquals(MatrixFixtures.TRAPDOOR_POS.y() - 1.0, underTrapdoor.y(), 1e-9);
        assertEquals(MatrixFixtures.TRAPDOOR_POS.z() + 0.5, underTrapdoor.z(), 1e-9);
        assertEquals(MatrixFixtures.TRAPDOOR_TARGET_ID, interactAt.targetId());
        assertEquals(InteractType.INTERACT_AT, interactAt.type());
        assertEquals(InteractType.ATTACK, attack.type());
    }

    @Test
    @Order(16)
    @DisplayName("§4.4 matrix.md | fixtureBambooCollision: MovePackets flush against XZ-offset bamboo hitbox")
    void testFixtureBambooCollision() {
        List<TimedPacket> seq = MatrixFixtures.fixtureBambooCollision();
        ServerState state = ServerState.forFixture("fixtureBambooCollision");
        List<ValidationResult> results = validator.replay(state, seq);

        assertEquals(4, seq.size());
        assertEquals(4, results.size());
        assertEquals(List.of(0, 1, 1, 1), delaysOf(seq));
        assertEquals("§4.4 matrix.md", results.getFirst().documentRef());

        double shiftedCenterX = MatrixFixtures.BAMBOO_POS.x() + 0.5 + MatrixFixtures.BAMBOO_OFFSET_X;
        double shiftedCenterZ = MatrixFixtures.BAMBOO_POS.z() + 0.5 + MatrixFixtures.BAMBOO_OFFSET_Z;
        double bambooMinX = shiftedCenterX - MatrixFixtures.BAMBOO_STALK_HALF_WIDTH; // 30.50
        double bambooMaxX = shiftedCenterX + MatrixFixtures.BAMBOO_STALK_HALF_WIDTH; // 30.875
        double bambooMinZ = shiftedCenterZ - MatrixFixtures.BAMBOO_STALK_HALF_WIDTH; // 30.15625
        double unoffsetMinX = MatrixFixtures.BAMBOO_POS.x() + 0.5 - MatrixFixtures.BAMBOO_STALK_HALF_WIDTH; // 30.3125

        MovePacket grazeNegX = assertInstanceOf(MovePacket.class, seq.get(1).packet());
        MovePacket grazeNegZ = assertInstanceOf(MovePacket.class, seq.get(2).packet());
        MovePacket grazePosX = assertInstanceOf(MovePacket.class, seq.get(3).packet());

        double playerMaxXAtNegX = grazeNegX.x() + MatrixFixtures.PLAYER_HALF_WIDTH;
        assertTrue(playerMaxXAtNegX > unoffsetMinX,
                "Must overlap un-offset bamboo AABB to exercise offset-collision logic");
        assertTrue(playerMaxXAtNegX < bambooMinX,
                "Must clear the true XZ-offset bamboo minX boundary");
        assertEquals(0.001, bambooMinX - playerMaxXAtNegX, 1e-6);

        double playerMaxZAtNegZ = grazeNegZ.z() + MatrixFixtures.PLAYER_HALF_WIDTH;
        assertTrue(playerMaxZAtNegZ < bambooMinZ);
        assertEquals(0.00125, bambooMinZ - playerMaxZAtNegZ, 1e-6);

        double playerMinXAtPosX = grazePosX.x() - MatrixFixtures.PLAYER_HALF_WIDTH;
        assertTrue(playerMinXAtPosX > bambooMaxX);
        assertEquals(0.001, playerMinXAtPosX - bambooMaxX, 1e-6);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static List<Integer> delaysOf(List<TimedPacket> sequence) {
        return sequence.stream().map(TimedPacket::delayTicks).toList();
    }

    private static double distanceToBoat(MovePacket move) {
        double dx = move.x() - GrimFixtures.BOAT_SHULKER_X;
        double dy = move.y() - GrimFixtures.BOAT_SHULKER_Y;
        double dz = move.z() - GrimFixtures.BOAT_SHULKER_Z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}

package dev.checking.harness;

import dev.checking.harness.fixtures.GrimFixtures;
import dev.checking.harness.fixtures.MatrixFixtures;
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
import dev.checking.harness.scenarios.FixtureBambooCollisionScenario;
import dev.checking.harness.scenarios.FixtureGroundSpoofNearEntityScenario;
import dev.checking.harness.scenarios.FixtureHitboxHistoryScenario;
import dev.checking.harness.scenarios.FixtureInteractRaysScenario;
import dev.checking.harness.scenarios.FixtureNoSlowHandSwapScenario;
import dev.checking.harness.scenarios.FixturePhaseResyncScenario;
import dev.checking.harness.scenarios.FixturePointThreeSkipScenario;
import dev.checking.harness.scenarios.FixturePoseSwitchScenario;
import dev.checking.harness.scenarios.FixtureReachWithSkipScenario;
import dev.checking.harness.scenarios.FixtureScaffoldCadenceScenario;
import dev.checking.harness.scenarios.FixtureSetbackWindowScenario;
import dev.checking.harness.scenarios.FixtureTimerCreditScenario;
import dev.checking.harness.scenarios.FixtureTimerDriftScenario;
import dev.checking.harness.scenarios.FixtureTransactionDelayScenario;
import dev.checking.harness.scenarios.FixtureVelocityBoundaryScenario;
import dev.checking.harness.scenarios.FixtureVelocityEdgeScenario;
import dev.checking.harness.scenarios.RecordingSink;
import dev.checking.harness.scenarios.RecordingSink.Operation;
import dev.checking.harness.scenarios.RecordingSink.RecordedCall;
import dev.checking.harness.scenarios.Scenario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests verifying all 16 {@link Scenario} implementations and {@link RecordingSink}
 * against the canonical fixture sequences from {@link GrimFixtures} and {@link MatrixFixtures}.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ScenarioTest {

    private RecordingSink sink;

    @BeforeEach
    void setUp() {
        sink = new RecordingSink();
    }

    // -------------------------------------------------------------------------
    // RecordingSink contract check
    // -------------------------------------------------------------------------

    @Test
    @Order(0)
    @DisplayName("RecordingSink records all typed send, cancel, and delay calls with cumulative ticks")
    void testRecordingSinkContract() {
        MovePacket move = new MovePacket(1, 0, 0.0, 64.0, 0.0, 0.0f, 0.0f, true, true);
        VelocityPacket vel = new VelocityPacket(1, 0.1, 0.2, 0.0);
        TransactionPacket tx = new TransactionPacket(10, TransactionType.PING);
        ActionPacket act = new ActionPacket(ActionType.PLACE_BLOCK, new BlockPos(1, 64, 1), Direction.UP);
        InteractPacket inter = new InteractPacket(5, InteractType.ATTACK);

        sink.send(move);
        sink.delay(vel, 2);
        sink.send(vel);
        sink.delay(tx, 3);
        sink.send(tx);
        sink.send(act);
        sink.cancel(act);
        sink.delay(inter, 1);
        sink.send(inter);

        assertEquals(6L, sink.getCurrentTick());
        assertEquals(9, sink.getCalls().size());
        assertEquals(5, sink.getCallsByOperation(Operation.SEND).size());
        assertEquals(1, sink.getCallsByOperation(Operation.CANCEL).size());
        assertEquals(3, sink.getCallsByOperation(Operation.DELAY).size());

        List<TimedPacket> reconstructed = sink.toTimedPackets();
        assertEquals(6, reconstructed.size());
        assertEquals(List.of(0, 2, 3, 0, 0, 1), reconstructed.stream().map(TimedPacket::delayTicks).toList());

        assertThrows(IllegalArgumentException.class, () -> sink.delay(move, -1));

        sink.reset();
        assertEquals(0L, sink.getCurrentTick());
        assertTrue(sink.getCalls().isEmpty());
    }

    // -------------------------------------------------------------------------
    // GrimAC scenarios (grim.md)
    // -------------------------------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("§2.1 grim.md | FixturePointThreeSkipScenario matches GrimFixtures.fixturePointThreeSkip")
    void testFixturePointThreeSkipScenario() {
        FixturePointThreeSkipScenario scenario = new FixturePointThreeSkipScenario();
        assertEquals(0.029, scenario.getCompensationDelta(), 1e-12);
        assertEquals(1, scenario.getSkipDelayTicks());
        assertEquals(1, scenario.getCompensationDelayTicks());

        assertScenarioMatchesFixture(scenario, GrimFixtures.fixturePointThreeSkip(), 0);
    }

    @Test
    @Order(2)
    @DisplayName("§2.2 grim.md | FixtureVelocityBoundaryScenario matches GrimFixtures.fixtureVelocityBoundary")
    void testFixtureVelocityBoundaryScenario() {
        FixtureVelocityBoundaryScenario scenario = new FixtureVelocityBoundaryScenario();
        assertEquals(0.0031, scenario.getAboveThresholdVelocity(), 1e-12);
        assertEquals(0.0029, scenario.getBelowThresholdVelocity(), 1e-12);
        assertEquals(0.003, scenario.getZeroThreshold(), 1e-12);

        assertScenarioMatchesFixture(scenario, GrimFixtures.fixtureVelocityBoundary(), 0);
    }

    @Test
    @Order(3)
    @DisplayName("§2.3 grim.md | FixtureGroundSpoofNearEntityScenario matches GrimFixtures.fixtureGroundSpoofNearEntity")
    void testFixtureGroundSpoofNearEntityScenario() {
        FixtureGroundSpoofNearEntityScenario scenario = new FixtureGroundSpoofNearEntityScenario();
        assertEquals(4.0, scenario.getExemptionRadius(), 1e-12);

        assertScenarioMatchesFixture(scenario, GrimFixtures.fixtureGroundSpoofNearEntity(), 0);
    }

    @Test
    @Order(4)
    @DisplayName("§3.1 grim.md | FixtureReachWithSkipScenario matches GrimFixtures.fixtureReachWithSkip")
    void testFixtureReachWithSkipScenario() {
        FixtureReachWithSkipScenario scenario = new FixtureReachWithSkipScenario();
        assertEquals(GrimFixtures.REACH_TARGET_ID, scenario.getTargetId());
        assertEquals(3.03, scenario.getReachLimit(), 1e-12);

        assertScenarioMatchesFixture(scenario, GrimFixtures.fixtureReachWithSkip(), 0);
    }

    @Test
    @Order(5)
    @DisplayName("§3.2 grim.md | FixtureTimerDriftScenario matches GrimFixtures.fixtureTimerDrift")
    void testFixtureTimerDriftScenario() {
        FixtureTimerDriftScenario scenario = new FixtureTimerDriftScenario();
        assertEquals(2, scenario.getFirstLagDelayTicks());
        assertEquals(3, scenario.getSecondLagDelayTicks());
        assertEquals(4, scenario.getBurstPacketCount());
        assertEquals(120, scenario.getDriftCapMs());

        assertScenarioMatchesFixture(scenario, GrimFixtures.fixtureTimerDrift(), 0);
    }

    @Test
    @Order(6)
    @DisplayName("§4.1 grim.md | FixtureTransactionDelayScenario matches GrimFixtures.fixtureTransactionDelay")
    void testFixtureTransactionDelayScenario() {
        FixtureTransactionDelayScenario scenario = new FixtureTransactionDelayScenario();
        assertEquals(1, scenario.getPingIntervalTicks());
        assertEquals(5, scenario.getInitialPongDelayTicks());
        assertEquals(2, scenario.getJitterDelayTicks());

        assertScenarioMatchesFixture(scenario, GrimFixtures.fixtureTransactionDelay(), 0);
    }

    @Test
    @Order(7)
    @DisplayName("§4.2 grim.md | FixtureNoSlowHandSwapScenario matches GrimFixtures.fixtureNoSlowHandSwap")
    void testFixtureNoSlowHandSwapScenario() {
        FixtureNoSlowHandSwapScenario scenario = new FixtureNoSlowHandSwapScenario();
        assertEquals(0.28, scenario.getStepDeltaX(), 1e-12);
        assertEquals(0, scenario.getSwapDelayTicks());

        assertScenarioMatchesFixture(scenario, GrimFixtures.fixtureNoSlowHandSwap(), 0);
    }

    @Test
    @Order(8)
    @DisplayName("§4.3 grim.md | FixturePhaseResyncScenario matches GrimFixtures.fixturePhaseResync and invokes cancel")
    void testFixturePhaseResyncScenario() {
        FixturePhaseResyncScenario scenario = new FixturePhaseResyncScenario();
        assertEquals(GrimFixtures.PHANTOM_BLOCK_POS, scenario.getPhantomBlockPos());
        assertEquals(5.70, scenario.getTouchFaceX(), 1e-12);
        assertEquals(6.05, scenario.getPenetrateX(), 1e-12);

        assertScenarioMatchesFixture(scenario, GrimFixtures.fixturePhaseResync(), 1);

        List<RecordedCall> cancelled = sink.getCallsByOperation(Operation.CANCEL);
        assertEquals(1, cancelled.size());
        ActionPacket cancelledAction = assertInstanceOf(ActionPacket.class, cancelled.getFirst().packet());
        assertEquals(ActionType.CANCEL_PLACE_BLOCK, cancelledAction.action());
        assertEquals(GrimFixtures.PHANTOM_BLOCK_POS, cancelledAction.pos());
        assertEquals(1L, cancelled.getFirst().tick());
    }

    // -------------------------------------------------------------------------
    // Matrix 7.x scenarios (matrix.md)
    // -------------------------------------------------------------------------

    @Test
    @Order(9)
    @DisplayName("§2.1 matrix.md | FixtureTimerCreditScenario matches MatrixFixtures.fixtureTimerCredit")
    void testFixtureTimerCreditScenario() {
        FixtureTimerCreditScenario scenario = new FixtureTimerCreditScenario();
        assertEquals(280, scenario.getIdleTicks());
        assertEquals(20, scenario.getIdleIntervalTicks());
        assertEquals(20, scenario.getBurstTicks());
        assertEquals(2, scenario.getPacketsPerBurstTick());

        assertScenarioMatchesFixture(scenario, MatrixFixtures.fixtureTimerCredit(), 0);
    }

    @Test
    @Order(10)
    @DisplayName("§2.2 matrix.md | FixtureVelocityEdgeScenario matches MatrixFixtures.fixtureVelocityEdge")
    void testFixtureVelocityEdgeScenario() {
        FixtureVelocityEdgeScenario scenario = new FixtureVelocityEdgeScenario();
        assertEquals(33, scenario.getWaitTimeLimitTicks());
        assertEquals(30, scenario.getFirstMoveDelayTicks());
        assertEquals(1, scenario.getCollisionDeltaTicks());

        assertScenarioMatchesFixture(scenario, MatrixFixtures.fixtureVelocityEdge(), 0);
    }

    @Test
    @Order(11)
    @DisplayName("§3.1 matrix.md | FixtureHitboxHistoryScenario matches MatrixFixtures.fixtureHitboxHistory")
    void testFixtureHitboxHistoryScenario() {
        FixtureHitboxHistoryScenario scenario = new FixtureHitboxHistoryScenario();
        assertEquals(12, scenario.getHistoryTicks());
        assertEquals(77, scenario.getTargetId());
        assertEquals(3.075, scenario.getReachLimit(), 1e-12);

        assertScenarioMatchesFixture(scenario, MatrixFixtures.fixtureHitboxHistory(), 0);
    }

    @Test
    @Order(12)
    @DisplayName("§3.2 matrix.md | FixtureSetbackWindowScenario matches MatrixFixtures.fixtureSetbackWindow")
    void testFixtureSetbackWindowScenario() {
        FixtureSetbackWindowScenario scenario = new FixtureSetbackWindowScenario();
        assertEquals(5, scenario.getGraceWindowTicks());
        assertEquals(4.5, scenario.getAnomalyDeltaX(), 1e-12);
        assertEquals(2.0, scenario.getAnomalyDeltaY(), 1e-12);

        assertScenarioMatchesFixture(scenario, MatrixFixtures.fixtureSetbackWindow(), 0);
    }

    @Test
    @Order(13)
    @DisplayName("§4.1 matrix.md | FixtureScaffoldCadenceScenario matches MatrixFixtures.fixtureScaffoldCadence")
    void testFixtureScaffoldCadenceScenario() {
        FixtureScaffoldCadenceScenario scenario = new FixtureScaffoldCadenceScenario();
        assertEquals(6, scenario.getStraightCadenceTicks());
        assertEquals(4, scenario.getDiagonalCadenceTicks());
        assertEquals(3, scenario.getViolationCadenceTicks());

        assertScenarioMatchesFixture(scenario, MatrixFixtures.fixtureScaffoldCadence(), 0);
    }

    @Test
    @Order(14)
    @DisplayName("§4.2 matrix.md | FixturePoseSwitchScenario matches MatrixFixtures.fixturePoseSwitch")
    void testFixturePoseSwitchScenario() {
        FixturePoseSwitchScenario scenario = new FixturePoseSwitchScenario();
        assertEquals(0.065, scenario.getCrouchStepX(), 1e-12);
        assertEquals(0.22, scenario.getStandStepX(), 1e-12);

        assertScenarioMatchesFixture(scenario, MatrixFixtures.fixturePoseSwitch(), 0);
    }

    @Test
    @Order(15)
    @DisplayName("§4.3 matrix.md | FixtureInteractRaysScenario matches MatrixFixtures.fixtureInteractRays")
    void testFixtureInteractRaysScenario() {
        FixtureInteractRaysScenario scenario = new FixtureInteractRaysScenario();
        assertEquals(MatrixFixtures.TRAPDOOR_POS, scenario.getTrapdoorPos());
        assertEquals(MatrixFixtures.TRAPDOOR_TARGET_ID, scenario.getTargetId());
        assertEquals(5.0f, scenario.getLookPitch(), 1e-6f);

        assertScenarioMatchesFixture(scenario, MatrixFixtures.fixtureInteractRays(), 0);
    }

    @Test
    @Order(16)
    @DisplayName("§4.4 matrix.md | FixtureBambooCollisionScenario matches MatrixFixtures.fixtureBambooCollision")
    void testFixtureBambooCollisionScenario() {
        FixtureBambooCollisionScenario scenario = new FixtureBambooCollisionScenario();
        assertEquals(MatrixFixtures.BAMBOO_POS, scenario.getBambooPos());
        assertEquals(MatrixFixtures.BAMBOO_OFFSET_X, scenario.getOffsetX(), 1e-12);
        assertEquals(MatrixFixtures.BAMBOO_OFFSET_Z, scenario.getOffsetZ(), 1e-12);
        assertEquals(0.001, scenario.getClearance(), 1e-12);

        assertScenarioMatchesFixture(scenario, MatrixFixtures.fixtureBambooCollision(), 0);
    }

    // -------------------------------------------------------------------------
    // Parameter override verification
    // -------------------------------------------------------------------------

    @Test
    @Order(17)
    @DisplayName("Parameterized scenarios apply custom tick delays and coefficients")
    void testParameterizedScenarios() {
        // Custom PointThreeSkip delta & delays
        new FixturePointThreeSkipScenario(0.021, 2, 3).execute(sink);
        List<TimedPacket> customSkip = sink.toTimedPackets();
        assertEquals(List.of(0, 2, 3), customSkip.stream().map(TimedPacket::delayTicks).toList());
        assertEquals(0.021, ((MovePacket) customSkip.get(2).packet()).x(), 1e-12);

        // Custom VelocityEdge timing & scale
        sink.reset();
        new FixtureVelocityEdgeScenario(33, 32, 1, 0.5).execute(sink);
        List<TimedPacket> customVel = sink.toTimedPackets();
        assertEquals(33L, sink.getCurrentTick());
        assertEquals(0.21, ((VelocityPacket) customVel.get(0).packet()).vx(), 1e-12);
        assertEquals(List.of(0, 32, 1), customVel.stream().map(TimedPacket::delayTicks).toList());

        // Custom Scaffold cadence
        sink.reset();
        new FixtureScaffoldCadenceScenario(7, 5, 2).execute(sink);
        assertEquals(List.of(0, 7, 7, 5, 5, 5, 2, 2, 2),
                sink.toTimedPackets().stream().map(TimedPacket::delayTicks).toList());
    }

    // -------------------------------------------------------------------------
    // Shared assertion helper
    // -------------------------------------------------------------------------

    private void assertScenarioMatchesFixture(
            Scenario scenario,
            List<TimedPacket> expectedFixture,
            int expectedCancelCount
    ) {
        scenario.execute(sink);

        List<TimedPacket> recorded = sink.toTimedPackets();
        assertEquals(expectedFixture.size(), recorded.size(), "Packet count must match fixture");
        assertEquals(expectedFixture, recorded, "Recorded packet sequence and delays must exactly match fixture");

        long expectedTotalTicks = expectedFixture.stream().mapToLong(TimedPacket::delayTicks).sum();
        assertEquals(expectedTotalTicks, sink.getCurrentTick(), "Total elapsed ticks must match fixture");

        long expectedDelays = expectedFixture.stream().filter(tp -> tp.delayTicks() > 0).count();
        assertEquals(expectedDelays, sink.getCallsByOperation(Operation.DELAY).size(),
                "Number of delay() calls must match entries with delayTicks > 0");
        assertEquals(expectedCancelCount, sink.getCallsByOperation(Operation.CANCEL).size(),
                "Number of cancel() calls must match expected cancellations");
        assertEquals(expectedFixture.size() - expectedCancelCount, sink.getCallsByOperation(Operation.SEND).size(),
                "Number of send() calls must match non-cancelled packets");
    }
}

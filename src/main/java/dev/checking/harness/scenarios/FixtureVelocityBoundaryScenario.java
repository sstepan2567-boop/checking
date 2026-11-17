package dev.checking.harness.scenarios;

import dev.checking.harness.fixtures.GrimFixtures;
import dev.checking.harness.fixtures.TimedPacket;
import dev.checking.harness.packets.MovePacket;
import dev.checking.harness.packets.VelocityPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Сценарий проигрывания фикстуры {@link GrimFixtures#fixtureVelocityBoundary()} через {@link PacketSink}.
 *
 * <p>Раздел документа: {@code §2.2 grim.md} — Knockback / Velocity Zeroing Threshold (0.003).
 * Отправляет два цикла PING → {@link VelocityPacket} → PONG → {@link MovePacket} для компонент
 * скорости {@link #aboveThresholdVelocity} (выше порога) и {@link #belowThresholdVelocity} (ниже порога).
 */
public final class FixtureVelocityBoundaryScenario implements Scenario {

    public static final double DEFAULT_ABOVE_THRESHOLD_VELOCITY = GrimFixtures.VELOCITY_ABOVE_THRESHOLD;
    public static final double DEFAULT_BELOW_THRESHOLD_VELOCITY = GrimFixtures.VELOCITY_BELOW_THRESHOLD;
    public static final double DEFAULT_ZERO_THRESHOLD = GrimFixtures.VELOCITY_ZERO_THRESHOLD;
    public static final int DEFAULT_RESPONSE_DELAY_TICKS = 1;

    private final double aboveThresholdVelocity;
    private final double belowThresholdVelocity;
    private final double zeroThreshold;
    private final int responseDelayTicks;

    public FixtureVelocityBoundaryScenario() {
        this(
                DEFAULT_ABOVE_THRESHOLD_VELOCITY,
                DEFAULT_BELOW_THRESHOLD_VELOCITY,
                DEFAULT_ZERO_THRESHOLD,
                DEFAULT_RESPONSE_DELAY_TICKS
        );
    }

    public FixtureVelocityBoundaryScenario(
            double aboveThresholdVelocity,
            double belowThresholdVelocity,
            double zeroThreshold,
            int responseDelayTicks
    ) {
        if (responseDelayTicks < 0) {
            throw new IllegalArgumentException("responseDelayTicks must be >= 0");
        }
        this.aboveThresholdVelocity = aboveThresholdVelocity;
        this.belowThresholdVelocity = belowThresholdVelocity;
        this.zeroThreshold = zeroThreshold;
        this.responseDelayTicks = responseDelayTicks;
    }

    @Override
    public void execute(PacketSink sink) {
        Objects.requireNonNull(sink, "sink must not be null");
        List<TimedPacket> base = GrimFixtures.fixtureVelocityBoundary();

        VelocityPacket v1Raw = (VelocityPacket) base.get(1).packet();
        VelocityPacket v1 = new VelocityPacket(v1Raw.entityId(), aboveThresholdVelocity, v1Raw.vy(), v1Raw.vz());
        double appliedV1 = Math.abs(aboveThresholdVelocity) < zeroThreshold ? 0.0 : aboveThresholdVelocity;

        MovePacket m1Raw = (MovePacket) base.get(3).packet();
        MovePacket m1 = new MovePacket(
                m1Raw.seq(), m1Raw.tick(), appliedV1, m1Raw.y(), m1Raw.z(),
                m1Raw.yaw(), m1Raw.pitch(), m1Raw.onGround(), m1Raw.hasPosition()
        );

        VelocityPacket v2Raw = (VelocityPacket) base.get(5).packet();
        VelocityPacket v2 = new VelocityPacket(v2Raw.entityId(), belowThresholdVelocity, v2Raw.vy(), v2Raw.vz());
        double appliedV2 = Math.abs(belowThresholdVelocity) < zeroThreshold ? 0.0 : belowThresholdVelocity;

        MovePacket m2Raw = (MovePacket) base.get(7).packet();
        MovePacket m2 = new MovePacket(
                m2Raw.seq(), m2Raw.tick(), appliedV1 + appliedV2, m2Raw.y(), m2Raw.z(),
                m2Raw.yaw(), m2Raw.pitch(), m2Raw.onGround(), m2Raw.hasPosition()
        );

        List<TimedPacket> sequence = new ArrayList<>(8);
        sequence.add(base.get(0));
        sequence.add(new TimedPacket(v1, base.get(1).delayTicks()));
        sequence.add(new TimedPacket(base.get(2).packet(), responseDelayTicks));
        sequence.add(new TimedPacket(m1, responseDelayTicks));
        sequence.add(new TimedPacket(base.get(4).packet(), responseDelayTicks));
        sequence.add(new TimedPacket(v2, base.get(5).delayTicks()));
        sequence.add(new TimedPacket(base.get(6).packet(), responseDelayTicks));
        sequence.add(new TimedPacket(m2, responseDelayTicks));
        ScenarioSupport.replay(sink, sequence);
    }

    public double getAboveThresholdVelocity() {
        return aboveThresholdVelocity;
    }

    public double getBelowThresholdVelocity() {
        return belowThresholdVelocity;
    }

    public double getZeroThreshold() {
        return zeroThreshold;
    }

    public int getResponseDelayTicks() {
        return responseDelayTicks;
    }
}

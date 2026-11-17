package dev.checking.harness.scenarios;

import dev.checking.harness.fixtures.GrimFixtures;
import dev.checking.harness.fixtures.TimedPacket;
import dev.checking.harness.packets.MovePacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Сценарий проигрывания фикстуры {@link GrimFixtures#fixtureGroundSpoofNearEntity()} через {@link PacketSink}.
 *
 * <p>Раздел документа: {@code §2.3 grim.md} — GroundSpoof &amp; Boat/Shulker Collision Exemption.
 * Проигрывает серию {@link MovePacket} при входе игрока в радиус {@link #exemptionRadius}
 * от коллизионной сущности с флагом {@code onGround=true}.
 */
public final class FixtureGroundSpoofNearEntityScenario implements Scenario {

    public static final double DEFAULT_EXEMPTION_RADIUS = GrimFixtures.GROUND_EXEMPTION_RADIUS;
    public static final double DEFAULT_ENTITY_X = GrimFixtures.BOAT_SHULKER_X;
    public static final double DEFAULT_ENTITY_Y = GrimFixtures.BOAT_SHULKER_Y;
    public static final double DEFAULT_ENTITY_Z = GrimFixtures.BOAT_SHULKER_Z;
    public static final int DEFAULT_STEP_DELAY_TICKS = 1;

    private final double exemptionRadius;
    private final double entityX;
    private final double entityY;
    private final double entityZ;
    private final int stepDelayTicks;

    public FixtureGroundSpoofNearEntityScenario() {
        this(
                DEFAULT_EXEMPTION_RADIUS,
                DEFAULT_ENTITY_X,
                DEFAULT_ENTITY_Y,
                DEFAULT_ENTITY_Z,
                DEFAULT_STEP_DELAY_TICKS
        );
    }

    public FixtureGroundSpoofNearEntityScenario(
            double exemptionRadius,
            double entityX,
            double entityY,
            double entityZ,
            int stepDelayTicks
    ) {
        if (exemptionRadius <= 0.0) {
            throw new IllegalArgumentException("exemptionRadius must be > 0");
        }
        if (stepDelayTicks < 0) {
            throw new IllegalArgumentException("stepDelayTicks must be >= 0");
        }
        this.exemptionRadius = exemptionRadius;
        this.entityX = entityX;
        this.entityY = entityY;
        this.entityZ = entityZ;
        this.stepDelayTicks = stepDelayTicks;
    }

    @Override
    public void execute(PacketSink sink) {
        Objects.requireNonNull(sink, "sink must not be null");
        List<TimedPacket> base = GrimFixtures.fixtureGroundSpoofNearEntity();

        double dxShift = entityX - DEFAULT_ENTITY_X;
        double dyShift = entityY - DEFAULT_ENTITY_Y;
        double dzShift = entityZ - DEFAULT_ENTITY_Z;

        List<TimedPacket> sequence = new ArrayList<>(base.size());
        for (int i = 0; i < base.size(); i++) {
            TimedPacket tp = base.get(i);
            MovePacket m = (MovePacket) tp.packet();
            double nx = m.x() + dxShift;
            double ny = m.y() + dyShift;
            double nz = m.z() + dzShift;
            double dist = Math.sqrt(
                    Math.pow(nx - entityX, 2) + Math.pow(ny - entityY, 2) + Math.pow(nz - entityZ, 2));
            boolean onGround = dist <= exemptionRadius;
            MovePacket adjusted = new MovePacket(
                    m.seq(), m.tick(), nx, ny, nz, m.yaw(), m.pitch(), onGround, m.hasPosition());
            int delay = i == 0 ? tp.delayTicks() : stepDelayTicks;
            sequence.add(new TimedPacket(adjusted, delay));
        }

        ScenarioSupport.replay(sink, sequence);
    }

    public double getExemptionRadius() {
        return exemptionRadius;
    }

    public double getEntityX() {
        return entityX;
    }

    public double getEntityY() {
        return entityY;
    }

    public double getEntityZ() {
        return entityZ;
    }

    public int getStepDelayTicks() {
        return stepDelayTicks;
    }
}

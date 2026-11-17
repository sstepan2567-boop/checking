package dev.checking.harness.scenarios;

import dev.checking.harness.fixtures.MatrixFixtures;
import dev.checking.harness.fixtures.TimedPacket;
import dev.checking.harness.packets.BlockPos;
import dev.checking.harness.packets.MovePacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Сценарий проигрывания фикстуры {@link MatrixFixtures#fixtureBambooCollision()} через {@link PacketSink}.
 *
 * <p>Раздел документа: {@code §4.4 matrix.md} — Offset-Hitbox Block Collision (Bamboo XZ Offset).
 * Проигрывает серию {@link MovePacket} вплотную к смещённому хитбоксу бамбука в {@link #bambooPos}
 * со смещением ({@link #offsetX}, {@link #offsetZ}) и зазором {@link #clearance}.
 */
public final class FixtureBambooCollisionScenario implements Scenario {

    public static final BlockPos DEFAULT_BAMBOO_POS = MatrixFixtures.BAMBOO_POS;
    public static final double DEFAULT_OFFSET_X = MatrixFixtures.BAMBOO_OFFSET_X;
    public static final double DEFAULT_OFFSET_Z = MatrixFixtures.BAMBOO_OFFSET_Z;
    public static final double DEFAULT_CLEARANCE = 0.001;
    public static final int DEFAULT_STEP_DELAY_TICKS = 1;

    private final BlockPos bambooPos;
    private final double offsetX;
    private final double offsetZ;
    private final double clearance;
    private final int stepDelayTicks;

    public FixtureBambooCollisionScenario() {
        this(
                DEFAULT_BAMBOO_POS,
                DEFAULT_OFFSET_X,
                DEFAULT_OFFSET_Z,
                DEFAULT_CLEARANCE,
                DEFAULT_STEP_DELAY_TICKS
        );
    }

    public FixtureBambooCollisionScenario(
            BlockPos bambooPos,
            double offsetX,
            double offsetZ,
            double clearance,
            int stepDelayTicks
    ) {
        Objects.requireNonNull(bambooPos, "bambooPos must not be null");
        if (clearance < 0.0 || stepDelayTicks < 0) {
            throw new IllegalArgumentException("clearance and stepDelayTicks must be >= 0");
        }
        this.bambooPos = bambooPos;
        this.offsetX = offsetX;
        this.offsetZ = offsetZ;
        this.clearance = clearance;
        this.stepDelayTicks = stepDelayTicks;
    }

    @Override
    public void execute(PacketSink sink) {
        Objects.requireNonNull(sink, "sink must not be null");
        List<TimedPacket> base = MatrixFixtures.fixtureBambooCollision();

        if (bambooPos.equals(DEFAULT_BAMBOO_POS)
                && Double.compare(offsetX, DEFAULT_OFFSET_X) == 0
                && Double.compare(offsetZ, DEFAULT_OFFSET_Z) == 0
                && Double.compare(clearance, DEFAULT_CLEARANCE) == 0
                && stepDelayTicks == DEFAULT_STEP_DELAY_TICKS) {
            ScenarioSupport.replay(sink, base);
            return;
        }

        double centerX = bambooPos.x() + 0.5 + offsetX;
        double centerZ = bambooPos.z() + 0.5 + offsetZ;
        double minX = centerX - MatrixFixtures.BAMBOO_STALK_HALF_WIDTH;
        double maxX = centerX + MatrixFixtures.BAMBOO_STALK_HALF_WIDTH;
        double minZ = centerZ - MatrixFixtures.BAMBOO_STALK_HALF_WIDTH;
        double baseY = bambooPos.y();

        MovePacket m0Raw = (MovePacket) base.get(0).packet();
        MovePacket m1Raw = (MovePacket) base.get(1).packet();
        MovePacket m2Raw = (MovePacket) base.get(2).packet();
        MovePacket m3Raw = (MovePacket) base.get(3).packet();

        List<TimedPacket> sequence = new ArrayList<>(4);
        sequence.add(new TimedPacket(new MovePacket(
                m0Raw.seq(), m0Raw.tick(), bambooPos.x() - 0.20, baseY, centerZ,
                m0Raw.yaw(), m0Raw.pitch(), m0Raw.onGround(), m0Raw.hasPosition()), 0));
        sequence.add(new TimedPacket(new MovePacket(
                m1Raw.seq(), m1Raw.tick(), minX - MatrixFixtures.PLAYER_HALF_WIDTH - clearance, baseY, centerZ,
                m1Raw.yaw(), m1Raw.pitch(), m1Raw.onGround(), m1Raw.hasPosition()), stepDelayTicks));
        sequence.add(new TimedPacket(new MovePacket(
                m2Raw.seq(), m2Raw.tick(), centerX, baseY, minZ - MatrixFixtures.PLAYER_HALF_WIDTH - clearance,
                m2Raw.yaw(), m2Raw.pitch(), m2Raw.onGround(), m2Raw.hasPosition()), stepDelayTicks));
        sequence.add(new TimedPacket(new MovePacket(
                m3Raw.seq(), m3Raw.tick(), maxX + MatrixFixtures.PLAYER_HALF_WIDTH + clearance, baseY, centerZ,
                m3Raw.yaw(), m3Raw.pitch(), m3Raw.onGround(), m3Raw.hasPosition()), stepDelayTicks));

        ScenarioSupport.replay(sink, sequence);
    }

    public BlockPos getBambooPos() {
        return bambooPos;
    }

    public double getOffsetX() {
        return offsetX;
    }

    public double getOffsetZ() {
        return offsetZ;
    }

    public double getClearance() {
        return clearance;
    }

    public int getStepDelayTicks() {
        return stepDelayTicks;
    }
}

package dev.checking.harness.scenarios;

import dev.checking.harness.fixtures.MatrixFixtures;
import dev.checking.harness.fixtures.TimedPacket;
import dev.checking.harness.packets.MovePacket;
import dev.checking.harness.packets.VelocityPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Сценарий проигрывания фикстуры {@link MatrixFixtures#fixtureVelocityEdge()} через {@link PacketSink}.
 *
 * <p>Раздел документа: {@code §2.2 matrix.md} — Velocity {@code wait_time} (33 Ticks) Boundary &amp; Wall Collision.
 * Отправляет {@link VelocityPacket}, затем {@link MovePacket} через {@link #firstMoveDelayTicks} тиков
 * (в пределах {@link #waitTimeLimitTicks}) и коллизионный {@link MovePacket} через {@link #collisionDeltaTicks}.
 */
public final class FixtureVelocityEdgeScenario implements Scenario {

    public static final int DEFAULT_WAIT_TIME_LIMIT_TICKS = MatrixFixtures.VELOCITY_WAIT_TIME_TICKS;
    public static final int DEFAULT_FIRST_MOVE_DELAY_TICKS = 30;
    public static final int DEFAULT_COLLISION_DELTA_TICKS = 1;
    public static final double DEFAULT_VELOCITY_SCALE = 1.0;

    private final int waitTimeLimitTicks;
    private final int firstMoveDelayTicks;
    private final int collisionDeltaTicks;
    private final double velocityScale;

    public FixtureVelocityEdgeScenario() {
        this(
                DEFAULT_WAIT_TIME_LIMIT_TICKS,
                DEFAULT_FIRST_MOVE_DELAY_TICKS,
                DEFAULT_COLLISION_DELTA_TICKS,
                DEFAULT_VELOCITY_SCALE
        );
    }

    public FixtureVelocityEdgeScenario(
            int waitTimeLimitTicks,
            int firstMoveDelayTicks,
            int collisionDeltaTicks,
            double velocityScale
    ) {
        if (waitTimeLimitTicks <= 0 || firstMoveDelayTicks < 0 || collisionDeltaTicks < 0) {
            throw new IllegalArgumentException("Tick parameters must be valid non-negative numbers");
        }
        this.waitTimeLimitTicks = waitTimeLimitTicks;
        this.firstMoveDelayTicks = firstMoveDelayTicks;
        this.collisionDeltaTicks = collisionDeltaTicks;
        this.velocityScale = velocityScale;
    }

    @Override
    public void execute(PacketSink sink) {
        Objects.requireNonNull(sink, "sink must not be null");
        List<TimedPacket> base = MatrixFixtures.fixtureVelocityEdge();

        VelocityPacket rawVel = (VelocityPacket) base.get(0).packet();
        VelocityPacket vel = new VelocityPacket(
                rawVel.entityId(),
                rawVel.vx() * velocityScale,
                rawVel.vy() * velocityScale,
                rawVel.vz() * velocityScale
        );

        MovePacket rawM1 = (MovePacket) base.get(1).packet();
        MovePacket m1 = new MovePacket(
                rawM1.seq(), firstMoveDelayTicks, rawM1.x(), rawM1.y(), rawM1.z(),
                rawM1.yaw(), rawM1.pitch(), rawM1.onGround(), rawM1.hasPosition()
        );

        MovePacket rawM2 = (MovePacket) base.get(2).packet();
        MovePacket m2 = new MovePacket(
                rawM2.seq(), (long) firstMoveDelayTicks + collisionDeltaTicks, rawM2.x(), rawM2.y(), rawM2.z(),
                rawM2.yaw(), rawM2.pitch(), rawM2.onGround(), rawM2.hasPosition()
        );

        List<TimedPacket> sequence = new ArrayList<>(3);
        sequence.add(new TimedPacket(vel, base.get(0).delayTicks()));
        sequence.add(new TimedPacket(m1, firstMoveDelayTicks));
        sequence.add(new TimedPacket(m2, collisionDeltaTicks));
        ScenarioSupport.replay(sink, sequence);
    }

    public int getWaitTimeLimitTicks() {
        return waitTimeLimitTicks;
    }

    public int getFirstMoveDelayTicks() {
        return firstMoveDelayTicks;
    }

    public int getCollisionDeltaTicks() {
        return collisionDeltaTicks;
    }

    public double getVelocityScale() {
        return velocityScale;
    }
}

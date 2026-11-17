package dev.checking.harness.scenarios;

import dev.checking.harness.fixtures.MatrixFixtures;
import dev.checking.harness.fixtures.TimedPacket;
import dev.checking.harness.packets.ActionPacket;
import dev.checking.harness.packets.BlockPos;
import dev.checking.harness.packets.InteractPacket;
import dev.checking.harness.packets.MovePacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Сценарий проигрывания фикстуры {@link MatrixFixtures#fixtureInteractRays()} через {@link PacketSink}.
 *
 * <p>Раздел документа: {@code §4.3 matrix.md} — Tight-Geometry Interaction Raycasting Under Trapdoor.
 * Позиционирует игрока под люком {@link #trapdoorPos} с углом {@link #lookPitch} и отправляет
 * {@link InteractPacket} по цели {@link #targetId}.
 */
public final class FixtureInteractRaysScenario implements Scenario {

    public static final BlockPos DEFAULT_TRAPDOOR_POS = MatrixFixtures.TRAPDOOR_POS;
    public static final int DEFAULT_TARGET_ID = MatrixFixtures.TRAPDOOR_TARGET_ID;
    public static final float DEFAULT_LOOK_PITCH = 5.0f;
    public static final int DEFAULT_STEP_DELAY_TICKS = 1;

    private final BlockPos trapdoorPos;
    private final int targetId;
    private final float lookPitch;
    private final int stepDelayTicks;

    public FixtureInteractRaysScenario() {
        this(DEFAULT_TRAPDOOR_POS, DEFAULT_TARGET_ID, DEFAULT_LOOK_PITCH, DEFAULT_STEP_DELAY_TICKS);
    }

    public FixtureInteractRaysScenario(BlockPos trapdoorPos, int targetId, float lookPitch, int stepDelayTicks) {
        Objects.requireNonNull(trapdoorPos, "trapdoorPos must not be null");
        if (stepDelayTicks < 0) {
            throw new IllegalArgumentException("stepDelayTicks must be >= 0");
        }
        this.trapdoorPos = trapdoorPos;
        this.targetId = targetId;
        this.lookPitch = lookPitch;
        this.stepDelayTicks = stepDelayTicks;
    }

    @Override
    public void execute(PacketSink sink) {
        Objects.requireNonNull(sink, "sink must not be null");
        List<TimedPacket> base = MatrixFixtures.fixtureInteractRays();

        ActionPacket rawShift = (ActionPacket) base.get(0).packet();
        MovePacket rawMove = (MovePacket) base.get(1).packet();
        InteractPacket rawInteractAt = (InteractPacket) base.get(2).packet();
        InteractPacket rawAttack = (InteractPacket) base.get(3).packet();

        ActionPacket shift = new ActionPacket(rawShift.action(), trapdoorPos, rawShift.direction());
        MovePacket underTrapdoor = new MovePacket(
                rawMove.seq(),
                rawMove.tick(),
                trapdoorPos.x() + 0.5,
                trapdoorPos.y() - 1.0,
                trapdoorPos.z() + 0.5,
                rawMove.yaw(),
                lookPitch,
                rawMove.onGround(),
                rawMove.hasPosition()
        );
        InteractPacket interactAt = new InteractPacket(targetId, rawInteractAt.type());
        InteractPacket attack = new InteractPacket(targetId, rawAttack.type());

        List<TimedPacket> sequence = new ArrayList<>(4);
        sequence.add(new TimedPacket(shift, base.get(0).delayTicks()));
        sequence.add(new TimedPacket(underTrapdoor, stepDelayTicks));
        sequence.add(new TimedPacket(interactAt, base.get(2).delayTicks()));
        sequence.add(new TimedPacket(attack, stepDelayTicks));
        ScenarioSupport.replay(sink, sequence);
    }

    public BlockPos getTrapdoorPos() {
        return trapdoorPos;
    }

    public int getTargetId() {
        return targetId;
    }

    public float getLookPitch() {
        return lookPitch;
    }

    public int getStepDelayTicks() {
        return stepDelayTicks;
    }
}

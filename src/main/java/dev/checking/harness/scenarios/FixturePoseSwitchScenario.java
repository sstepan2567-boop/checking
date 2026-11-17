package dev.checking.harness.scenarios;

import dev.checking.harness.fixtures.MatrixFixtures;
import dev.checking.harness.fixtures.TimedPacket;
import dev.checking.harness.packets.MovePacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Сценарий проигрывания фикстуры {@link MatrixFixtures#fixturePoseSwitch()} через {@link PacketSink}.
 *
 * <p>Раздел документа: {@code §4.2 matrix.md} — Pose Switch &amp; Sneak Bounding-Box Transitions.
 * Обрамляет {@link MovePacket} пакетами {@code PRESS_SHIFT} и {@code RELEASE_SHIFT} с шагами
 * {@link #crouchStepX} в приседе и {@link #standStepX} после вставания.
 */
public final class FixturePoseSwitchScenario implements Scenario {

    public static final double DEFAULT_CROUCH_STEP_X = 0.065;
    public static final double DEFAULT_STAND_STEP_X = 0.22;
    public static final int DEFAULT_TRANSITION_DELAY_TICKS = 1;

    private final double crouchStepX;
    private final double standStepX;
    private final int transitionDelayTicks;

    public FixturePoseSwitchScenario() {
        this(DEFAULT_CROUCH_STEP_X, DEFAULT_STAND_STEP_X, DEFAULT_TRANSITION_DELAY_TICKS);
    }

    public FixturePoseSwitchScenario(double crouchStepX, double standStepX, int transitionDelayTicks) {
        if (transitionDelayTicks < 0) {
            throw new IllegalArgumentException("transitionDelayTicks must be >= 0");
        }
        this.crouchStepX = crouchStepX;
        this.standStepX = standStepX;
        this.transitionDelayTicks = transitionDelayTicks;
    }

    @Override
    public void execute(PacketSink sink) {
        Objects.requireNonNull(sink, "sink must not be null");
        List<TimedPacket> base = MatrixFixtures.fixturePoseSwitch();

        MovePacket start = (MovePacket) base.get(0).packet();
        MovePacket m2Raw = (MovePacket) base.get(2).packet();
        MovePacket m3Raw = (MovePacket) base.get(3).packet();
        MovePacket m4Raw = (MovePacket) base.get(5).packet();

        double x2 = Math.round((start.x() + crouchStepX) * 10000.0) / 10000.0;
        double x3 = Math.round((x2 + crouchStepX) * 10000.0) / 10000.0;
        double x4 = Math.round((x3 + standStepX) * 10000.0) / 10000.0;

        MovePacket m2 = new MovePacket(
                m2Raw.seq(), m2Raw.tick(), x2, m2Raw.y(), m2Raw.z(),
                m2Raw.yaw(), m2Raw.pitch(), m2Raw.onGround(), m2Raw.hasPosition()
        );
        MovePacket m3 = new MovePacket(
                m3Raw.seq(), m3Raw.tick(), x3, m3Raw.y(), m3Raw.z(),
                m3Raw.yaw(), m3Raw.pitch(), m3Raw.onGround(), m3Raw.hasPosition()
        );
        MovePacket m4 = new MovePacket(
                m4Raw.seq(), m4Raw.tick(), x4, m4Raw.y(), m4Raw.z(),
                m4Raw.yaw(), m4Raw.pitch(), m4Raw.onGround(), m4Raw.hasPosition()
        );

        List<TimedPacket> sequence = new ArrayList<>(6);
        sequence.add(base.get(0));
        sequence.add(new TimedPacket(base.get(1).packet(), transitionDelayTicks));
        sequence.add(new TimedPacket(m2, base.get(2).delayTicks()));
        sequence.add(new TimedPacket(m3, transitionDelayTicks));
        sequence.add(base.get(4));
        sequence.add(new TimedPacket(m4, transitionDelayTicks));
        ScenarioSupport.replay(sink, sequence);
    }

    public double getCrouchStepX() {
        return crouchStepX;
    }

    public double getStandStepX() {
        return standStepX;
    }

    public int getTransitionDelayTicks() {
        return transitionDelayTicks;
    }
}

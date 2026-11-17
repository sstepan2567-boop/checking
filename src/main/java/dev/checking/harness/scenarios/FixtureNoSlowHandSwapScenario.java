package dev.checking.harness.scenarios;

import dev.checking.harness.fixtures.GrimFixtures;
import dev.checking.harness.fixtures.TimedPacket;
import dev.checking.harness.packets.MovePacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Сценарий проигрывания фикстуры {@link GrimFixtures#fixtureNoSlowHandSwap()} через {@link PacketSink}.
 *
 * <p>Раздел документа: {@code §4.2 grim.md} — NoSlow Item-Use State &amp; Offhand Swap Desync.
 * Отправляет {@code USE_ITEM}, в том же тике ({@link #swapDelayTicks}) {@code SWAP_ITEM_WITH_OFFHAND}
 * и два {@link MovePacket} с шагом {@link #stepDeltaX} на полной скорости.
 */
public final class FixtureNoSlowHandSwapScenario implements Scenario {

    public static final double DEFAULT_STEP_DELTA_X = 0.28;
    public static final int DEFAULT_SWAP_DELAY_TICKS = 0;
    public static final int DEFAULT_FOLLOW_UP_DELAY_TICKS = 1;

    private final double stepDeltaX;
    private final int swapDelayTicks;
    private final int followUpDelayTicks;

    public FixtureNoSlowHandSwapScenario() {
        this(DEFAULT_STEP_DELTA_X, DEFAULT_SWAP_DELAY_TICKS, DEFAULT_FOLLOW_UP_DELAY_TICKS);
    }

    public FixtureNoSlowHandSwapScenario(double stepDeltaX, int swapDelayTicks, int followUpDelayTicks) {
        if (swapDelayTicks < 0 || followUpDelayTicks < 0) {
            throw new IllegalArgumentException("Delay ticks must be >= 0");
        }
        this.stepDeltaX = stepDeltaX;
        this.swapDelayTicks = swapDelayTicks;
        this.followUpDelayTicks = followUpDelayTicks;
    }

    @Override
    public void execute(PacketSink sink) {
        Objects.requireNonNull(sink, "sink must not be null");
        List<TimedPacket> base = GrimFixtures.fixtureNoSlowHandSwap();

        MovePacket anchor = (MovePacket) base.get(0).packet();
        MovePacket m2Raw = (MovePacket) base.get(3).packet();
        MovePacket m3Raw = (MovePacket) base.get(4).packet();

        MovePacket m2 = new MovePacket(
                m2Raw.seq(), m2Raw.tick(), anchor.x() + stepDeltaX, m2Raw.y(), m2Raw.z(),
                m2Raw.yaw(), m2Raw.pitch(), m2Raw.onGround(), m2Raw.hasPosition()
        );
        MovePacket m3 = new MovePacket(
                m3Raw.seq(), m3Raw.tick(), anchor.x() + 2.0 * stepDeltaX, m3Raw.y(), m3Raw.z(),
                m3Raw.yaw(), m3Raw.pitch(), m3Raw.onGround(), m3Raw.hasPosition()
        );

        List<TimedPacket> sequence = new ArrayList<>(5);
        sequence.add(base.get(0));
        sequence.add(new TimedPacket(base.get(1).packet(), followUpDelayTicks));
        sequence.add(new TimedPacket(base.get(2).packet(), swapDelayTicks));
        sequence.add(new TimedPacket(m2, base.get(3).delayTicks()));
        sequence.add(new TimedPacket(m3, followUpDelayTicks));
        ScenarioSupport.replay(sink, sequence);
    }

    public double getStepDeltaX() {
        return stepDeltaX;
    }

    public int getSwapDelayTicks() {
        return swapDelayTicks;
    }

    public int getFollowUpDelayTicks() {
        return followUpDelayTicks;
    }
}

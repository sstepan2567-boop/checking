package dev.checking.harness.scenarios;

import dev.checking.harness.fixtures.GrimFixtures;
import dev.checking.harness.fixtures.TimedPacket;
import dev.checking.harness.packets.MovePacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Сценарий проигрывания фикстуры {@link GrimFixtures#fixturePointThreeSkip()} через {@link PacketSink}.
 *
 * <p>Раздел документа: {@code §2.1 grim.md} — 0.03 Movement Threshold / Point-Three Skip.
 * Отправляет базовый {@link MovePacket}, затем {@link MovePacket} с {@code hasPosition=false},
 * затем компенсационный {@link MovePacket} со смещением {@link #compensationDelta} в окне 0.03.
 */
public final class FixturePointThreeSkipScenario implements Scenario {

    public static final double DEFAULT_COMPENSATION_DELTA = 0.029;
    public static final int DEFAULT_SKIP_DELAY_TICKS = 1;
    public static final int DEFAULT_COMPENSATION_DELAY_TICKS = 1;

    private final double compensationDelta;
    private final int skipDelayTicks;
    private final int compensationDelayTicks;

    public FixturePointThreeSkipScenario() {
        this(DEFAULT_COMPENSATION_DELTA, DEFAULT_SKIP_DELAY_TICKS, DEFAULT_COMPENSATION_DELAY_TICKS);
    }

    public FixturePointThreeSkipScenario(double compensationDelta, int skipDelayTicks, int compensationDelayTicks) {
        if (skipDelayTicks < 0 || compensationDelayTicks < 0) {
            throw new IllegalArgumentException("Delay ticks must be >= 0");
        }
        this.compensationDelta = compensationDelta;
        this.skipDelayTicks = skipDelayTicks;
        this.compensationDelayTicks = compensationDelayTicks;
    }

    @Override
    public void execute(PacketSink sink) {
        Objects.requireNonNull(sink, "sink must not be null");
        List<TimedPacket> base = GrimFixtures.fixturePointThreeSkip();

        MovePacket anchor = (MovePacket) base.get(0).packet();
        MovePacket skipped = (MovePacket) base.get(1).packet();
        MovePacket rawComp = (MovePacket) base.get(2).packet();
        MovePacket adjustedComp = new MovePacket(
                rawComp.seq(),
                rawComp.tick(),
                anchor.x() + compensationDelta,
                rawComp.y(),
                rawComp.z(),
                rawComp.yaw(),
                rawComp.pitch(),
                rawComp.onGround(),
                rawComp.hasPosition()
        );

        List<TimedPacket> sequence = new ArrayList<>(3);
        sequence.add(base.get(0));
        sequence.add(new TimedPacket(skipped, skipDelayTicks));
        sequence.add(new TimedPacket(adjustedComp, compensationDelayTicks));
        ScenarioSupport.replay(sink, sequence);
    }

    public double getCompensationDelta() {
        return compensationDelta;
    }

    public int getSkipDelayTicks() {
        return skipDelayTicks;
    }

    public int getCompensationDelayTicks() {
        return compensationDelayTicks;
    }
}

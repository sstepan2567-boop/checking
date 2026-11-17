package dev.checking.harness.scenarios;

import dev.checking.harness.fixtures.GrimFixtures;
import dev.checking.harness.fixtures.TimedPacket;
import dev.checking.harness.packets.MovePacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Сценарий проигрывания фикстуры {@link GrimFixtures#fixtureTimerDrift()} через {@link PacketSink}.
 *
 * <p>Раздел документа: {@code §3.2 grim.md} — Timer Balance Drift Cap (120 ms) &amp; Catch-Up Burst.
 * Накапливает отставание таймера за счёт {@link #firstLagDelayTicks} и {@link #secondLagDelayTicks}
 * (порог дрифта {@link #driftCapMs} ms), затем отправляет пачку из {@link #burstPacketCount}
 * ускоренных {@link MovePacket} с нулевой задержкой.
 */
public final class FixtureTimerDriftScenario implements Scenario {

    public static final int DEFAULT_FIRST_LAG_DELAY_TICKS = 2;
    public static final int DEFAULT_SECOND_LAG_DELAY_TICKS = 3;
    public static final int DEFAULT_BURST_PACKET_COUNT = 4;
    public static final int DEFAULT_DRIFT_CAP_MS = 120;

    private final int firstLagDelayTicks;
    private final int secondLagDelayTicks;
    private final int burstPacketCount;
    private final int driftCapMs;

    public FixtureTimerDriftScenario() {
        this(
                DEFAULT_FIRST_LAG_DELAY_TICKS,
                DEFAULT_SECOND_LAG_DELAY_TICKS,
                DEFAULT_BURST_PACKET_COUNT,
                DEFAULT_DRIFT_CAP_MS
        );
    }

    public FixtureTimerDriftScenario(
            int firstLagDelayTicks,
            int secondLagDelayTicks,
            int burstPacketCount,
            int driftCapMs
    ) {
        if (firstLagDelayTicks < 0 || secondLagDelayTicks < 0 || burstPacketCount < 0 || driftCapMs < 0) {
            throw new IllegalArgumentException("Timing parameters must be >= 0");
        }
        this.firstLagDelayTicks = firstLagDelayTicks;
        this.secondLagDelayTicks = secondLagDelayTicks;
        this.burstPacketCount = burstPacketCount;
        this.driftCapMs = driftCapMs;
    }

    @Override
    public void execute(PacketSink sink) {
        Objects.requireNonNull(sink, "sink must not be null");
        List<TimedPacket> base = GrimFixtures.fixtureTimerDrift();

        List<TimedPacket> sequence = new ArrayList<>(3 + burstPacketCount);
        sequence.add(base.get(0));
        sequence.add(new TimedPacket(base.get(1).packet(), firstLagDelayTicks));
        sequence.add(new TimedPacket(base.get(2).packet(), secondLagDelayTicks));

        int availableBurst = base.size() - 3;
        MovePacket lastLagMove = (MovePacket) base.get(2).packet();
        for (int i = 0; i < burstPacketCount; i++) {
            if (i < availableBurst) {
                sequence.add(base.get(3 + i));
            } else {
                long seq = lastLagMove.seq() + 1L + i;
                long tick = lastLagMove.tick() + 1L + i;
                double x = Math.round((lastLagMove.x() + 0.2 * (i + 1)) * 1000.0) / 1000.0;
                MovePacket extra = new MovePacket(
                        seq, tick, x, lastLagMove.y(), lastLagMove.z(),
                        lastLagMove.yaw(), lastLagMove.pitch(), lastLagMove.onGround(), lastLagMove.hasPosition()
                );
                sequence.add(new TimedPacket(extra, 0));
            }
        }

        ScenarioSupport.replay(sink, sequence);
    }

    public int getFirstLagDelayTicks() {
        return firstLagDelayTicks;
    }

    public int getSecondLagDelayTicks() {
        return secondLagDelayTicks;
    }

    public int getBurstPacketCount() {
        return burstPacketCount;
    }

    public int getDriftCapMs() {
        return driftCapMs;
    }
}

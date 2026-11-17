package dev.checking.harness.scenarios;

import dev.checking.harness.fixtures.GrimFixtures;
import dev.checking.harness.fixtures.TimedPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Сценарий проигрывания фикстуры {@link GrimFixtures#fixtureTransactionDelay()} через {@link PacketSink}.
 *
 * <p>Раздел документа: {@code §4.1 grim.md} — Transaction PING/PONG Lag Compensation &amp; FIFO Ordering.
 * Проигрывает 4 серверных PING с шагом {@link #pingIntervalTicks} и 4 отложенных PONG
 * (начальная задержка {@link #initialPongDelayTicks}, джиттер {@link #jitterDelayTicks})
 * при строгом сохранении порядка FIFO.
 */
public final class FixtureTransactionDelayScenario implements Scenario {

    public static final int DEFAULT_PING_INTERVAL_TICKS = 1;
    public static final int DEFAULT_INITIAL_PONG_DELAY_TICKS = 5;
    public static final int DEFAULT_JITTER_DELAY_TICKS = 2;

    private final int pingIntervalTicks;
    private final int initialPongDelayTicks;
    private final int jitterDelayTicks;

    public FixtureTransactionDelayScenario() {
        this(DEFAULT_PING_INTERVAL_TICKS, DEFAULT_INITIAL_PONG_DELAY_TICKS, DEFAULT_JITTER_DELAY_TICKS);
    }

    public FixtureTransactionDelayScenario(
            int pingIntervalTicks,
            int initialPongDelayTicks,
            int jitterDelayTicks
    ) {
        if (pingIntervalTicks < 0 || initialPongDelayTicks < 0 || jitterDelayTicks < 0) {
            throw new IllegalArgumentException("Delay ticks must be >= 0");
        }
        this.pingIntervalTicks = pingIntervalTicks;
        this.initialPongDelayTicks = initialPongDelayTicks;
        this.jitterDelayTicks = jitterDelayTicks;
    }

    @Override
    public void execute(PacketSink sink) {
        Objects.requireNonNull(sink, "sink must not be null");
        List<TimedPacket> base = GrimFixtures.fixtureTransactionDelay();

        List<TimedPacket> sequence = new ArrayList<>(base.size());
        sequence.add(base.get(0));
        sequence.add(new TimedPacket(base.get(1).packet(), pingIntervalTicks));
        sequence.add(new TimedPacket(base.get(2).packet(), pingIntervalTicks));
        sequence.add(new TimedPacket(base.get(3).packet(), pingIntervalTicks));
        sequence.add(new TimedPacket(base.get(4).packet(), initialPongDelayTicks));
        sequence.add(new TimedPacket(base.get(5).packet(), pingIntervalTicks));
        sequence.add(new TimedPacket(base.get(6).packet(), jitterDelayTicks));
        sequence.add(new TimedPacket(base.get(7).packet(), pingIntervalTicks));
        ScenarioSupport.replay(sink, sequence);
    }

    public int getPingIntervalTicks() {
        return pingIntervalTicks;
    }

    public int getInitialPongDelayTicks() {
        return initialPongDelayTicks;
    }

    public int getJitterDelayTicks() {
        return jitterDelayTicks;
    }
}

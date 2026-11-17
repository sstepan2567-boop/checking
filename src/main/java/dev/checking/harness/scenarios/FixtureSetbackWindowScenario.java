package dev.checking.harness.scenarios;

import dev.checking.harness.fixtures.MatrixFixtures;
import dev.checking.harness.fixtures.TimedPacket;
import dev.checking.harness.packets.MovePacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Сценарий проигрывания фикстуры {@link MatrixFixtures#fixtureSetbackWindow()} через {@link PacketSink}.
 *
 * <p>Раздел документа: {@code §3.2 matrix.md} — Post-Setback Grace Window (5 Ticks).
 * Проигрывает базовый {@link MovePacket}, аномальный скачок ({@link #anomalyDeltaX}, {@link #anomalyDeltaY}),
 * серверный {@code TransactionPacket(SETBACK)} и {@link #graceWindowTicks} тиков с {@code onGround=true}.
 */
public final class FixtureSetbackWindowScenario implements Scenario {

    public static final int DEFAULT_GRACE_WINDOW_TICKS = MatrixFixtures.SETBACK_WINDOW_TICKS;
    public static final double DEFAULT_ANOMALY_DELTA_X = 4.5;
    public static final double DEFAULT_ANOMALY_DELTA_Y = 2.0;
    public static final int DEFAULT_POST_SETBACK_STEP_TICKS = 1;

    private final int graceWindowTicks;
    private final double anomalyDeltaX;
    private final double anomalyDeltaY;
    private final int postSetbackStepTicks;

    public FixtureSetbackWindowScenario() {
        this(
                DEFAULT_GRACE_WINDOW_TICKS,
                DEFAULT_ANOMALY_DELTA_X,
                DEFAULT_ANOMALY_DELTA_Y,
                DEFAULT_POST_SETBACK_STEP_TICKS
        );
    }

    public FixtureSetbackWindowScenario(
            int graceWindowTicks,
            double anomalyDeltaX,
            double anomalyDeltaY,
            int postSetbackStepTicks
    ) {
        if (graceWindowTicks < 0 || postSetbackStepTicks < 0) {
            throw new IllegalArgumentException("Tick parameters must be >= 0");
        }
        this.graceWindowTicks = graceWindowTicks;
        this.anomalyDeltaX = anomalyDeltaX;
        this.anomalyDeltaY = anomalyDeltaY;
        this.postSetbackStepTicks = postSetbackStepTicks;
    }

    @Override
    public void execute(PacketSink sink) {
        Objects.requireNonNull(sink, "sink must not be null");
        List<TimedPacket> base = MatrixFixtures.fixtureSetbackWindow();

        MovePacket baseline = (MovePacket) base.get(0).packet();
        MovePacket rawAnomaly = (MovePacket) base.get(1).packet();
        MovePacket anomaly = new MovePacket(
                rawAnomaly.seq(),
                rawAnomaly.tick(),
                baseline.x() + anomalyDeltaX,
                baseline.y() + anomalyDeltaY,
                rawAnomaly.z(),
                rawAnomaly.yaw(),
                rawAnomaly.pitch(),
                rawAnomaly.onGround(),
                rawAnomaly.hasPosition()
        );

        List<TimedPacket> sequence = new ArrayList<>(3 + graceWindowTicks);
        sequence.add(base.get(0));
        sequence.add(new TimedPacket(anomaly, base.get(1).delayTicks()));
        sequence.add(base.get(2));

        for (int i = 1; i <= graceWindowTicks; i++) {
            MovePacket post = new MovePacket(
                    rawAnomaly.seq() + i,
                    rawAnomaly.tick() + (long) i * postSetbackStepTicks,
                    baseline.x(),
                    baseline.y(),
                    baseline.z(),
                    baseline.yaw(),
                    baseline.pitch(),
                    true,
                    true
            );
            sequence.add(new TimedPacket(post, postSetbackStepTicks));
        }

        ScenarioSupport.replay(sink, sequence);
    }

    public int getGraceWindowTicks() {
        return graceWindowTicks;
    }

    public double getAnomalyDeltaX() {
        return anomalyDeltaX;
    }

    public double getAnomalyDeltaY() {
        return anomalyDeltaY;
    }

    public int getPostSetbackStepTicks() {
        return postSetbackStepTicks;
    }
}

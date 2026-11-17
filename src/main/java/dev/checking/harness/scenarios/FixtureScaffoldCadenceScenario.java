package dev.checking.harness.scenarios;

import dev.checking.harness.fixtures.MatrixFixtures;
import dev.checking.harness.fixtures.TimedPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Сценарий проигрывания фикстуры {@link MatrixFixtures#fixtureScaffoldCadence()} через {@link PacketSink}.
 *
 * <p>Раздел документа: {@code §4.1 matrix.md} — Scaffold Placement Cadence (6 / 4 / 3 Ticks).
 * Проигрывает 3 сегмента установки блоков: прямой ({@link #straightCadenceTicks} тиков),
 * диагональный ({@link #diagonalCadenceTicks} тиков) и нарушающий ({@link #violationCadenceTicks} тиков).
 */
public final class FixtureScaffoldCadenceScenario implements Scenario {

    public static final int DEFAULT_STRAIGHT_CADENCE_TICKS = 6;
    public static final int DEFAULT_DIAGONAL_CADENCE_TICKS = 4;
    public static final int DEFAULT_VIOLATION_CADENCE_TICKS = 3;

    private final int straightCadenceTicks;
    private final int diagonalCadenceTicks;
    private final int violationCadenceTicks;

    public FixtureScaffoldCadenceScenario() {
        this(
                DEFAULT_STRAIGHT_CADENCE_TICKS,
                DEFAULT_DIAGONAL_CADENCE_TICKS,
                DEFAULT_VIOLATION_CADENCE_TICKS
        );
    }

    public FixtureScaffoldCadenceScenario(
            int straightCadenceTicks,
            int diagonalCadenceTicks,
            int violationCadenceTicks
    ) {
        if (straightCadenceTicks < 0 || diagonalCadenceTicks < 0 || violationCadenceTicks < 0) {
            throw new IllegalArgumentException("Cadence ticks must be >= 0");
        }
        this.straightCadenceTicks = straightCadenceTicks;
        this.diagonalCadenceTicks = diagonalCadenceTicks;
        this.violationCadenceTicks = violationCadenceTicks;
    }

    @Override
    public void execute(PacketSink sink) {
        Objects.requireNonNull(sink, "sink must not be null");
        List<TimedPacket> base = MatrixFixtures.fixtureScaffoldCadence();

        List<TimedPacket> sequence = new ArrayList<>(base.size());
        // Straight segment: indices 0, 1, 2
        sequence.add(base.get(0));
        sequence.add(new TimedPacket(base.get(1).packet(), straightCadenceTicks));
        sequence.add(new TimedPacket(base.get(2).packet(), straightCadenceTicks));
        // Diagonal segment: indices 3, 4, 5
        sequence.add(new TimedPacket(base.get(3).packet(), diagonalCadenceTicks));
        sequence.add(new TimedPacket(base.get(4).packet(), diagonalCadenceTicks));
        sequence.add(new TimedPacket(base.get(5).packet(), diagonalCadenceTicks));
        // Violation segment: indices 6, 7, 8
        sequence.add(new TimedPacket(base.get(6).packet(), violationCadenceTicks));
        sequence.add(new TimedPacket(base.get(7).packet(), violationCadenceTicks));
        sequence.add(new TimedPacket(base.get(8).packet(), violationCadenceTicks));

        ScenarioSupport.replay(sink, sequence);
    }

    public int getStraightCadenceTicks() {
        return straightCadenceTicks;
    }

    public int getDiagonalCadenceTicks() {
        return diagonalCadenceTicks;
    }

    public int getViolationCadenceTicks() {
        return violationCadenceTicks;
    }
}

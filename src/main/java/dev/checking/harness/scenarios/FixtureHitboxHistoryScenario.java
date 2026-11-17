package dev.checking.harness.scenarios;

import dev.checking.harness.fixtures.MatrixFixtures;
import dev.checking.harness.fixtures.TimedPacket;
import dev.checking.harness.packets.InteractPacket;
import dev.checking.harness.packets.MovePacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Сценарий проигрывания фикстуры {@link MatrixFixtures#fixtureHitboxHistory()} через {@link PacketSink}.
 *
 * <p>Раздел документа: {@code §3.1 matrix.md} — Hitbox History Lag Compensation (12 Ticks, 3.075 Reach).
 * Отправляет {@link #historyTicks} пакетов истории позиций цели {@link #targetId} с шагом
 * {@link #targetStepPerTick}, затем {@link InteractPacket} в пределах {@link #reachLimit}.
 */
public final class FixtureHitboxHistoryScenario implements Scenario {

    public static final int DEFAULT_HISTORY_TICKS = MatrixFixtures.HITBOX_HISTORY_TICKS;
    public static final int DEFAULT_TARGET_ID = MatrixFixtures.HITBOX_TARGET_ID;
    public static final double DEFAULT_REACH_LIMIT = MatrixFixtures.HITBOX_REACH_LIMIT;
    public static final double DEFAULT_TARGET_STEP_PER_TICK = 0.10;

    private final int historyTicks;
    private final int targetId;
    private final double reachLimit;
    private final double targetStepPerTick;

    public FixtureHitboxHistoryScenario() {
        this(DEFAULT_HISTORY_TICKS, DEFAULT_TARGET_ID, DEFAULT_REACH_LIMIT, DEFAULT_TARGET_STEP_PER_TICK);
    }

    public FixtureHitboxHistoryScenario(
            int historyTicks,
            int targetId,
            double reachLimit,
            double targetStepPerTick
    ) {
        if (historyTicks <= 0 || reachLimit <= 0.0) {
            throw new IllegalArgumentException("historyTicks and reachLimit must be > 0");
        }
        this.historyTicks = historyTicks;
        this.targetId = targetId;
        this.reachLimit = reachLimit;
        this.targetStepPerTick = targetStepPerTick;
    }

    @Override
    public void execute(PacketSink sink) {
        Objects.requireNonNull(sink, "sink must not be null");
        List<TimedPacket> base = MatrixFixtures.fixtureHitboxHistory();

        if (historyTicks == DEFAULT_HISTORY_TICKS
                && targetId == DEFAULT_TARGET_ID
                && Double.compare(targetStepPerTick, DEFAULT_TARGET_STEP_PER_TICK) == 0) {
            ScenarioSupport.replay(sink, base);
            return;
        }

        MovePacket firstMove = (MovePacket) base.getFirst().packet();
        InteractPacket rawInteract = (InteractPacket) base.getLast().packet();

        List<TimedPacket> sequence = new ArrayList<>(historyTicks + 1);
        for (int i = 0; i < historyTicks; i++) {
            double x = Math.round((firstMove.x() + i * targetStepPerTick) * 1000.0) / 1000.0;
            sequence.add(new TimedPacket(
                    new MovePacket(
                            firstMove.seq() + i, firstMove.tick() + i, x, firstMove.y(), firstMove.z(),
                            firstMove.yaw(), firstMove.pitch(), firstMove.onGround(), firstMove.hasPosition()
                    ),
                    i == 0 ? 0 : 1
            ));
        }
        sequence.add(new TimedPacket(new InteractPacket(targetId, rawInteract.type()), 0));
        ScenarioSupport.replay(sink, sequence);
    }

    public int getHistoryTicks() {
        return historyTicks;
    }

    public int getTargetId() {
        return targetId;
    }

    public double getReachLimit() {
        return reachLimit;
    }

    public double getTargetStepPerTick() {
        return targetStepPerTick;
    }
}

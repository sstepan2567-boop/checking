package dev.checking.harness.scenarios;

import dev.checking.harness.fixtures.GrimFixtures;
import dev.checking.harness.fixtures.TimedPacket;
import dev.checking.harness.packets.InteractPacket;
import dev.checking.harness.packets.MovePacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Сценарий проигрывания фикстуры {@link GrimFixtures#fixtureReachWithSkip()} через {@link PacketSink}.
 *
 * <p>Раздел документа: {@code §3.1 grim.md} — Reach Validation with 0.03 Position Uncertainty.
 * Проигрывает пропуск позиции ({@code hasPosition=false}), немедленную атаку {@link InteractPacket}
 * в пределах {@link #reachLimit} и последующий подтверждающий {@link MovePacket}.
 */
public final class FixtureReachWithSkipScenario implements Scenario {

    public static final int DEFAULT_TARGET_ID = GrimFixtures.REACH_TARGET_ID;
    public static final double DEFAULT_REACH_LIMIT = GrimFixtures.REACH_LIMIT_WITH_SKIP;
    public static final double DEFAULT_FOLLOW_UP_DELTA_X = 0.025;
    public static final int DEFAULT_SKIP_DELAY_TICKS = 1;

    private final int targetId;
    private final double reachLimit;
    private final double followUpDeltaX;
    private final int skipDelayTicks;

    public FixtureReachWithSkipScenario() {
        this(DEFAULT_TARGET_ID, DEFAULT_REACH_LIMIT, DEFAULT_FOLLOW_UP_DELTA_X, DEFAULT_SKIP_DELAY_TICKS);
    }

    public FixtureReachWithSkipScenario(int targetId, double reachLimit, double followUpDeltaX, int skipDelayTicks) {
        if (reachLimit <= 0.0) {
            throw new IllegalArgumentException("reachLimit must be > 0");
        }
        if (skipDelayTicks < 0) {
            throw new IllegalArgumentException("skipDelayTicks must be >= 0");
        }
        this.targetId = targetId;
        this.reachLimit = reachLimit;
        this.followUpDeltaX = followUpDeltaX;
        this.skipDelayTicks = skipDelayTicks;
    }

    @Override
    public void execute(PacketSink sink) {
        Objects.requireNonNull(sink, "sink must not be null");
        List<TimedPacket> base = GrimFixtures.fixtureReachWithSkip();

        MovePacket anchor = (MovePacket) base.get(0).packet();
        MovePacket skipped = (MovePacket) base.get(1).packet();
        InteractPacket rawInteract = (InteractPacket) base.get(2).packet();
        MovePacket rawFollowUp = (MovePacket) base.get(3).packet();

        InteractPacket interact = new InteractPacket(targetId, rawInteract.type());
        MovePacket followUp = new MovePacket(
                rawFollowUp.seq(),
                rawFollowUp.tick(),
                anchor.x() + followUpDeltaX,
                rawFollowUp.y(),
                rawFollowUp.z(),
                rawFollowUp.yaw(),
                rawFollowUp.pitch(),
                rawFollowUp.onGround(),
                rawFollowUp.hasPosition()
        );

        List<TimedPacket> sequence = new ArrayList<>(4);
        sequence.add(base.get(0));
        sequence.add(new TimedPacket(skipped, skipDelayTicks));
        sequence.add(new TimedPacket(interact, base.get(2).delayTicks()));
        sequence.add(new TimedPacket(followUp, skipDelayTicks));
        ScenarioSupport.replay(sink, sequence);
    }

    public int getTargetId() {
        return targetId;
    }

    public double getReachLimit() {
        return reachLimit;
    }

    public double getFollowUpDeltaX() {
        return followUpDeltaX;
    }

    public int getSkipDelayTicks() {
        return skipDelayTicks;
    }
}

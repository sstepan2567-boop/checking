package dev.checking.harness.scenarios;

import dev.checking.harness.fixtures.GrimFixtures;
import dev.checking.harness.fixtures.TimedPacket;
import dev.checking.harness.packets.ActionPacket;
import dev.checking.harness.packets.BlockPos;
import dev.checking.harness.packets.MovePacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Сценарий проигрывания фикстуры {@link GrimFixtures#fixturePhaseResync()} через {@link PacketSink}.
 *
 * <p>Раздел документа: {@code §4.3 grim.md} — Phase / Ghost Block Placement Cancellation Resync.
 * Отправляет {@code PLACE_BLOCK} в {@link #phantomBlockPos}, затем вызывает {@link PacketSink#cancel}
 * для пакета отмены {@code CANCEL_PLACE_BLOCK} и проигрывает {@link MovePacket} с коллизией
 * в фантомный блок.
 */
public final class FixturePhaseResyncScenario implements Scenario {

    public static final BlockPos DEFAULT_PHANTOM_BLOCK_POS = GrimFixtures.PHANTOM_BLOCK_POS;
    public static final double DEFAULT_TOUCH_FACE_X = 5.70;
    public static final double DEFAULT_PENETRATE_X = 6.05;
    public static final int DEFAULT_RESYNC_DELAY_TICKS = 1;

    private final BlockPos phantomBlockPos;
    private final double touchFaceX;
    private final double penetrateX;
    private final int resyncDelayTicks;

    public FixturePhaseResyncScenario() {
        this(DEFAULT_PHANTOM_BLOCK_POS, DEFAULT_TOUCH_FACE_X, DEFAULT_PENETRATE_X, DEFAULT_RESYNC_DELAY_TICKS);
    }

    public FixturePhaseResyncScenario(
            BlockPos phantomBlockPos,
            double touchFaceX,
            double penetrateX,
            int resyncDelayTicks
    ) {
        Objects.requireNonNull(phantomBlockPos, "phantomBlockPos must not be null");
        if (resyncDelayTicks < 0) {
            throw new IllegalArgumentException("resyncDelayTicks must be >= 0");
        }
        this.phantomBlockPos = phantomBlockPos;
        this.touchFaceX = touchFaceX;
        this.penetrateX = penetrateX;
        this.resyncDelayTicks = resyncDelayTicks;
    }

    @Override
    public void execute(PacketSink sink) {
        Objects.requireNonNull(sink, "sink must not be null");
        List<TimedPacket> base = GrimFixtures.fixturePhaseResync();

        ActionPacket placeRaw = (ActionPacket) base.get(1).packet();
        ActionPacket cancelRaw = (ActionPacket) base.get(2).packet();
        MovePacket touchRaw = (MovePacket) base.get(3).packet();
        MovePacket penetrateRaw = (MovePacket) base.get(4).packet();

        ActionPacket place = new ActionPacket(placeRaw.action(), phantomBlockPos, placeRaw.direction());
        ActionPacket cancel = new ActionPacket(cancelRaw.action(), phantomBlockPos, cancelRaw.direction());
        MovePacket touch = new MovePacket(
                touchRaw.seq(), touchRaw.tick(), touchFaceX, touchRaw.y(), touchRaw.z(),
                touchRaw.yaw(), touchRaw.pitch(), touchRaw.onGround(), touchRaw.hasPosition()
        );
        MovePacket penetrate = new MovePacket(
                penetrateRaw.seq(), penetrateRaw.tick(), penetrateX, penetrateRaw.y(), penetrateRaw.z(),
                penetrateRaw.yaw(), penetrateRaw.pitch(), penetrateRaw.onGround(), penetrateRaw.hasPosition()
        );

        List<TimedPacket> sequence = new ArrayList<>(5);
        sequence.add(base.get(0));
        sequence.add(new TimedPacket(place, resyncDelayTicks));
        sequence.add(new TimedPacket(cancel, base.get(2).delayTicks()));
        sequence.add(new TimedPacket(touch, resyncDelayTicks));
        sequence.add(new TimedPacket(penetrate, resyncDelayTicks));
        // ScenarioSupport.replay invokes sink.cancel(cancel) for CANCEL_PLACE_BLOCK
        ScenarioSupport.replay(sink, sequence);
    }

    public BlockPos getPhantomBlockPos() {
        return phantomBlockPos;
    }

    public double getTouchFaceX() {
        return touchFaceX;
    }

    public double getPenetrateX() {
        return penetrateX;
    }

    public int getResyncDelayTicks() {
        return resyncDelayTicks;
    }
}

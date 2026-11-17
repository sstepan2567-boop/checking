package dev.checking.harness.scenarios;

import dev.checking.harness.fixtures.TimedPacket;
import dev.checking.harness.packets.ActionPacket;
import dev.checking.harness.packets.ActionType;
import dev.checking.harness.packets.InteractPacket;
import dev.checking.harness.packets.MovePacket;
import dev.checking.harness.packets.Packet;
import dev.checking.harness.packets.TransactionPacket;
import dev.checking.harness.packets.VelocityPacket;

import java.util.List;
import java.util.Objects;

/**
 * Package-private replay utilities shared by scenario implementations.
 */
final class ScenarioSupport {

    private ScenarioSupport() {
    }

    /**
     * Dispatches a {@link Packet} to the matching typed {@code send} overload of {@link PacketSink}.
     */
    static void sendTyped(PacketSink sink, Packet packet) {
        Objects.requireNonNull(sink, "sink must not be null");
        Objects.requireNonNull(packet, "packet must not be null");
        switch (packet) {
            case MovePacket m -> sink.send(m);
            case VelocityPacket v -> sink.send(v);
            case TransactionPacket t -> sink.send(t);
            case ActionPacket a -> sink.send(a);
            case InteractPacket i -> sink.send(i);
        }
    }

    /**
     * Replays a timed packet sequence through {@code sink}, invoking {@link PacketSink#delay(Packet, int)}
     * when {@code delayTicks > 0}, {@link PacketSink#cancel(Packet)} for cancellation actions,
     * and the strongly-typed {@code send} method for all other packets.
     */
    static void replay(PacketSink sink, List<TimedPacket> sequence) {
        Objects.requireNonNull(sink, "sink must not be null");
        Objects.requireNonNull(sequence, "sequence must not be null");
        for (TimedPacket timedPacket : sequence) {
            Packet packet = timedPacket.packet();
            if (timedPacket.delayTicks() > 0) {
                sink.delay(packet, timedPacket.delayTicks());
            }
            if (packet instanceof ActionPacket action && action.action() == ActionType.CANCEL_PLACE_BLOCK) {
                sink.cancel(packet);
            } else {
                sendTyped(sink, packet);
            }
        }
    }
}

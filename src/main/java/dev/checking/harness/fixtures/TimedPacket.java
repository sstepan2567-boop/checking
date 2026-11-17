package dev.checking.harness.fixtures;

import dev.checking.harness.packets.Packet;

import java.util.Objects;

/**
 * Pairs a protocol {@link Packet} with the tick delay elapsed since the previous packet in a sequence.
 *
 * @param packet     protocol packet to replay
 * @param delayTicks non-negative number of server ticks waited before dispatching {@code packet}
 */
public record TimedPacket(Packet packet, int delayTicks) {

    public TimedPacket {
        Objects.requireNonNull(packet, "packet must not be null");
        if (delayTicks < 0) {
            throw new IllegalArgumentException("delayTicks must be >= 0, got: " + delayTicks);
        }
    }
}

package dev.checking.harness.packets;

import java.util.Objects;

/**
 * Transaction synchronization packet ({@code PING}/{@code PONG}) or emulated server
 * {@code PlayerPositionLook} correction ({@code SETBACK}).
 *
 * @param id   monotonic transaction or teleport confirmation identifier
 * @param type transaction variant ({@link TransactionType#PING}, {@link TransactionType#PONG}, or {@link TransactionType#SETBACK})
 */
public record TransactionPacket(
        int id,
        TransactionType type
) implements Packet {

    public TransactionPacket {
        Objects.requireNonNull(type, "type must not be null");
    }
}

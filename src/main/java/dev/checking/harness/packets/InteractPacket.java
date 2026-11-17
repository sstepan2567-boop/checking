package dev.checking.harness.packets;

import java.util.Objects;

/**
 * Client-to-server entity interaction packet.
 *
 * @param targetId interacted entity identifier
 * @param type     interaction kind ({@link InteractType#ATTACK}, {@link InteractType#INTERACT}, or {@link InteractType#INTERACT_AT})
 */
public record InteractPacket(
        int targetId,
        InteractType type
) implements Packet {

    public InteractPacket {
        Objects.requireNonNull(type, "type must not be null");
    }
}

package dev.checking.harness.packets;

import java.util.Objects;

/**
 * Client-side player action packet (block placement/cancellation, item use, offhand swap, crouch toggle).
 *
 * @param action    action type
 * @param pos       target block position ({@link BlockPos#ZERO} when non-positional)
 * @param direction target block face ({@link Direction#NONE} when non-directional)
 */
public record ActionPacket(
        ActionType action,
        BlockPos pos,
        Direction direction
) implements Packet {

    public ActionPacket {
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(pos, "pos must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
    }
}

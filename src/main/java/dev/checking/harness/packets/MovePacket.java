package dev.checking.harness.packets;

/**
 * Client-to-server player or entity movement update packet.
 *
 * @param seq         monotonic sequence number within the test sequence
 * @param tick        logical game tick associated with this packet
 * @param x           world X coordinate (ignored by server when {@code hasPosition == false})
 * @param y           world Y coordinate (ignored by server when {@code hasPosition == false})
 * @param z           world Z coordinate (ignored by server when {@code hasPosition == false})
 * @param yaw         horizontal look rotation in degrees
 * @param pitch       vertical look rotation in degrees
 * @param onGround    client-claimed ground contact flag
 * @param hasPosition whether this packet carries explicit positional coordinates
 */
public record MovePacket(
        long seq,
        long tick,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        boolean onGround,
        boolean hasPosition
) implements Packet {
}

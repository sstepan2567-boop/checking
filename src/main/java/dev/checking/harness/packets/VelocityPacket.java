package dev.checking.harness.packets;

/**
 * Server-to-client entity velocity impulse packet.
 *
 * @param entityId target entity identifier
 * @param vx       velocity X component in blocks per tick
 * @param vy       velocity Y component in blocks per tick
 * @param vz       velocity Z component in blocks per tick
 */
public record VelocityPacket(
        int entityId,
        double vx,
        double vy,
        double vz
) implements Packet {
}

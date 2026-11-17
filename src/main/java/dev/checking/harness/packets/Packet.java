package dev.checking.harness.packets;

/**
 * Sealed root type for all isolated protocol packets modeled by this server-side test harness.
 */
public sealed interface Packet
        permits MovePacket, VelocityPacket, TransactionPacket, ActionPacket, InteractPacket {
}

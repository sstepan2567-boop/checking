package dev.checking.harness.packets;

/**
 * Immutable 3D integer block coordinate used in server-side action packets.
 */
public record BlockPos(int x, int y, int z) {

    /** Origin sentinel used for non-positional player actions (e.g. offhand swap, shift toggle). */
    public static final BlockPos ZERO = new BlockPos(0, 0, 0);
}

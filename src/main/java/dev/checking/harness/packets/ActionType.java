package dev.checking.harness.packets;

/**
 * Protocol action variants carried by {@link ActionPacket}.
 */
public enum ActionType {
    USE_ITEM,
    SWAP_ITEM_WITH_OFFHAND,
    PLACE_BLOCK,
    CANCEL_PLACE_BLOCK,
    PRESS_SHIFT,
    RELEASE_SHIFT
}

package dev.checking.harness.packets;

/**
 * Transaction synchronization packet variants, including server setback emulation.
 */
public enum TransactionType {
    PING,
    PONG,
    SETBACK
}

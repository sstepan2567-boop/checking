package dev.checking.harness.validation;

import dev.checking.harness.packets.Packet;

/**
 * Core server-side protocol invariant validator contract.
 */
@FunctionalInterface
public interface Validator {

    /**
     * Validates an incoming protocol {@link Packet} against the current isolated {@link ServerState}.
     *
     * @param state  mutable server-side simulation state for the active sequence
     * @param packet incoming protocol packet
     * @return validation outcome describing invariant status
     */
    ValidationResult validate(ServerState state, Packet packet);
}

package dev.checking.harness.validation;

import java.util.Objects;

/**
 * Immutable result returned by {@link Validator#validate(ServerState, dev.checking.harness.packets.Packet)}.
 *
 * @param valid       {@code true} when no invariant violation is reported
 * @param fixtureName name of the active fixture/scenario
 * @param documentRef specification section reference (e.g. {@code "§2.1 grim.md"})
 * @param invariant   short invariant identifier
 * @param message     human-readable diagnostic log message
 */
public record ValidationResult(
        boolean valid,
        String fixtureName,
        String documentRef,
        String invariant,
        String message
) {

    public ValidationResult {
        Objects.requireNonNull(fixtureName, "fixtureName must not be null");
        Objects.requireNonNull(documentRef, "documentRef must not be null");
        Objects.requireNonNull(invariant, "invariant must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }

    public static ValidationResult ok(String fixtureName, String documentRef, String invariant, String message) {
        return new ValidationResult(true, fixtureName, documentRef, invariant, message);
    }

    public static ValidationResult violation(String fixtureName, String documentRef, String invariant, String message) {
        return new ValidationResult(false, fixtureName, documentRef, invariant, message);
    }
}

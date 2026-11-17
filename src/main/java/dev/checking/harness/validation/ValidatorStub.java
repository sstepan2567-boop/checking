package dev.checking.harness.validation;

import dev.checking.harness.fixtures.TimedPacket;
import dev.checking.harness.packets.ActionPacket;
import dev.checking.harness.packets.InteractPacket;
import dev.checking.harness.packets.MovePacket;
import dev.checking.harness.packets.Packet;
import dev.checking.harness.packets.TransactionPacket;
import dev.checking.harness.packets.VelocityPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Stub {@link Validator} implementation without real anti-cheat check logic.
 *
 * <p>Logs to {@code System.out} which specification invariant is violated/exercised in each
 * fixture and returns a corresponding {@link ValidationResult}.
 */
public final class ValidatorStub implements Validator {

    private record InvariantDescriptor(String documentRef, String invariantId, String violationSummary) {
    }

    private static final Map<String, InvariantDescriptor> FIXTURE_INVARIANTS = Map.ofEntries(
            // GrimAC (grim.md)
            Map.entry("fixturePointThreeSkip", new InvariantDescriptor(
                    "§2.1 grim.md",
                    "GRIM_POINT_THREE_SKIP",
                    "Пропуск позиции (hasPosition=false) и компенсация смещения в окне 0.03 (dx=0.029)")),
            Map.entry("fixtureVelocityBoundary", new InvariantDescriptor(
                    "§2.2 grim.md",
                    "GRIM_VELOCITY_ZERO_BOUNDARY",
                    "Порог обнуления скорости 0.003: 0.0031 сохраняется, 0.0029 обнуляется")),
            Map.entry("fixtureGroundSpoofNearEntity", new InvariantDescriptor(
                    "§2.3 grim.md",
                    "GRIM_GROUND_SPOOF_NEAR_ENTITY",
                    "onGround=true в воздухе в радиусе 4 блоков от лодки/шалкера")),
            Map.entry("fixtureReachWithSkip", new InvariantDescriptor(
                    "§3.1 grim.md",
                    "GRIM_REACH_WITH_003_SKIP",
                    "InteractPacket после hasPosition=false с расширенным reach <= 3.03")),
            Map.entry("fixtureTimerDrift", new InvariantDescriptor(
                    "§3.2 grim.md",
                    "GRIM_TIMER_DRIFT_120MS",
                    "Накопление отставания таймера >= 120ms с последующей пачкой ускоренных MovePacket")),
            Map.entry("fixtureTransactionDelay", new InvariantDescriptor(
                    "§4.1 grim.md",
                    "GRIM_TRANSACTION_FIFO_DELAY",
                    "Отложенные TransactionPacket PONG с сохранением порядка FIFO")),
            Map.entry("fixtureNoSlowHandSwap", new InvariantDescriptor(
                    "§4.2 grim.md",
                    "GRIM_NOSLOW_HAND_SWAP",
                    "SWAP_ITEM_WITH_OFFHAND в тике использования предмета + полный MovePacket")),
            Map.entry("fixturePhaseResync", new InvariantDescriptor(
                    "§4.3 grim.md",
                    "GRIM_PHASE_GHOST_RESYNC",
                    "PLACE_BLOCK + отмена + MovePacket с коллизией в фантомный блок")),

            // Matrix 7.x (matrix.md)
            Map.entry("fixtureTimerCredit", new InvariantDescriptor(
                    "§2.1 matrix.md",
                    "MATRIX_TIMER_CREDIT_POOL",
                    "Простой 280 тиков (низкая частота) + 20 тиков с частотой > 20 Гц")),
            Map.entry("fixtureVelocityEdge", new InvariantDescriptor(
                    "§2.2 matrix.md",
                    "MATRIX_VELOCITY_WAIT_TIME_EDGE",
                    "VelocityPacket + MovePacket через 30 тиков (граница wait_time 33) и на 31-м с коллизией")),
            Map.entry("fixtureHitboxHistory", new InvariantDescriptor(
                    "§3.1 matrix.md",
                    "MATRIX_HITBOX_HISTORY_12T",
                    "12 тиков истории MovePacket цели + InteractPacket по исторической позиции <= 3.075")),
            Map.entry("fixtureSetbackWindow", new InvariantDescriptor(
                    "§3.2 matrix.md",
                    "MATRIX_SETBACK_GRACE_WINDOW",
                    "Аномалия MovePacket + TransactionPacket SETBACK + 5 тиков onGround=true")),
            Map.entry("fixtureScaffoldCadence", new InvariantDescriptor(
                    "§4.1 matrix.md",
                    "MATRIX_SCAFFOLD_CADENCE",
                    "Каденс установки блоков: 6 тиков (прямой), 4 (диагональ), 3 (нарушение)")),
            Map.entry("fixturePoseSwitch", new InvariantDescriptor(
                    "§4.2 matrix.md",
                    "MATRIX_POSE_SWITCH_SHIFT",
                    "ActionPacket PRESS_SHIFT/RELEASE_SHIFT вокруг MovePacket")),
            Map.entry("fixtureInteractRays", new InvariantDescriptor(
                    "§4.3 matrix.md",
                    "MATRIX_INTERACT_RAYS_TRAPDOOR",
                    "InteractPacket в тесной геометрии под люком")),
            Map.entry("fixtureBambooCollision", new InvariantDescriptor(
                    "§4.4 matrix.md",
                    "MATRIX_BAMBOO_OFFSET_COLLISION",
                    "MovePacket вплотную к координатам бамбука со смещённым XZ-хитбоксом"))
    );

    private static final InvariantDescriptor DEFAULT_DESCRIPTOR = new InvariantDescriptor(
            "unknown",
            "UNREGISTERED_INVARIANT",
            "Инвариант не зарегистрирован в ValidatorStub");

    @Override
    public ValidationResult validate(ServerState state, Packet packet) {
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(packet, "packet must not be null");

        state.recordPacket(packet);
        String fixture = state.getFixtureName();
        InvariantDescriptor descriptor = FIXTURE_INVARIANTS.getOrDefault(fixture, DEFAULT_DESCRIPTOR);

        if (state.getProcessedPackets() == 1) {
            System.out.printf(
                    Locale.ROOT,
                    "[ValidatorStub] VIOLATION fixture=%s | ref=%s | invariant=%s | %s%n",
                    fixture,
                    descriptor.documentRef(),
                    descriptor.invariantId(),
                    descriptor.violationSummary()
            );
        }

        return ValidationResult.violation(
                fixture,
                descriptor.documentRef(),
                descriptor.invariantId(),
                descriptor.violationSummary()
        );
    }

    /**
     * Replays an entire {@link TimedPacket} sequence through this validator stub, advancing
     * {@link ServerState} by each packet's {@code delayTicks}.
     *
     * @param state    isolated server state for the fixture
     * @param sequence timed packet sequence to replay
     * @return list of per-packet {@link ValidationResult} objects
     */
    public List<ValidationResult> replay(ServerState state, List<TimedPacket> sequence) {
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(sequence, "sequence must not be null");

        List<ValidationResult> results = new ArrayList<>(sequence.size());
        for (TimedPacket timedPacket : sequence) {
            state.advanceTicks(timedPacket.delayTicks());
            results.add(validate(state, timedPacket.packet()));
        }

        String lastSummary = state.getLastPacket() == null ? "none" : formatPacket(state.getLastPacket());
        System.out.printf(
                Locale.ROOT,
                "  -> replayed=%d packets, elapsedTicks=%d, last=%s%n",
                state.getProcessedPackets(),
                state.getElapsedTicks(),
                lastSummary
        );
        return List.copyOf(results);
    }

    private static String formatPacket(Packet packet) {
        return switch (packet) {
            case MovePacket m -> String.format(
                    Locale.ROOT,
                    "MovePacket(seq=%d, tick=%d, pos=(%.4f,%.4f,%.4f), onGround=%s, hasPos=%s)",
                    m.seq(), m.tick(), m.x(), m.y(), m.z(), m.onGround(), m.hasPosition());
            case VelocityPacket v -> String.format(
                    Locale.ROOT,
                    "VelocityPacket(entity=%d, v=(%.4f,%.4f,%.4f))",
                    v.entityId(), v.vx(), v.vy(), v.vz());
            case TransactionPacket t -> String.format(
                    Locale.ROOT,
                    "TransactionPacket(id=%d, type=%s)",
                    t.id(), t.type());
            case ActionPacket a -> String.format(
                    Locale.ROOT,
                    "ActionPacket(action=%s, pos=(%d,%d,%d), dir=%s)",
                    a.action(), a.pos().x(), a.pos().y(), a.pos().z(), a.direction());
            case InteractPacket i -> String.format(
                    Locale.ROOT,
                    "InteractPacket(target=%d, type=%s)",
                    i.targetId(), i.type());
        };
    }
}

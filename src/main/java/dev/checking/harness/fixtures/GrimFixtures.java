package dev.checking.harness.fixtures;

import dev.checking.harness.packets.ActionPacket;
import dev.checking.harness.packets.ActionType;
import dev.checking.harness.packets.BlockPos;
import dev.checking.harness.packets.Direction;
import dev.checking.harness.packets.InteractPacket;
import dev.checking.harness.packets.InteractType;
import dev.checking.harness.packets.MovePacket;
import dev.checking.harness.packets.TransactionPacket;
import dev.checking.harness.packets.TransactionType;
import dev.checking.harness.packets.VelocityPacket;

import java.util.List;

/**
 * Named protocol packet sequence fixtures covering GrimAC server-side validator boundary conditions
 * documented in {@code grim.md}.
 */
public final class GrimFixtures {

    /** Entity coordinates of a nearby boat/shulker used in {@link #fixtureGroundSpoofNearEntity()}. */
    public static final double BOAT_SHULKER_X = 10.0;
    public static final double BOAT_SHULKER_Y = 65.0;
    public static final double BOAT_SHULKER_Z = 10.0;
    public static final double GROUND_EXEMPTION_RADIUS = 4.0;

    /** Target entity parameters for {@link #fixtureReachWithSkip()} (hitbox face at x = 3.02 <= 3.03). */
    public static final int REACH_TARGET_ID = 42;
    public static final double REACH_TARGET_CENTER_X = 3.32;
    public static final double PLAYER_HALF_WIDTH = 0.30;
    public static final double REACH_LIMIT_WITH_SKIP = 3.03;

    /** Vanilla entity velocity zeroing threshold modeled in {@link #fixtureVelocityBoundary()}. */
    public static final double VELOCITY_ZERO_THRESHOLD = 0.003;
    public static final double VELOCITY_ABOVE_THRESHOLD = 0.0031;
    public static final double VELOCITY_BELOW_THRESHOLD = 0.0029;

    /** Target block coordinate for phantom-block collision in {@link #fixturePhaseResync()}. */
    public static final BlockPos PHANTOM_BLOCK_POS = new BlockPos(6, 64, 5);

    private GrimFixtures() {
    }

    /**
     * Позиционный пакет пропущен при суб-пороговой скорости (&lt; 0.03 блока),
     * затем поступает компенсационное смещение в пределах окна неопределённости 0.03.
     *
     * <p>Раздел документа: {@code §2.1 grim.md} — 0.03 Movement Threshold / Point-Three Skip.
     *
     * <p>Последовательность:
     * <ol>
     *   <li>Базовый {@link MovePacket} ({@code hasPosition=true}) в (0.0, 64.0, 0.0).</li>
     *   <li>{@link MovePacket} с {@code hasPosition=false} (пропуск позиции при суб-пороговой скорости).</li>
     *   <li>{@link MovePacket} с {@code hasPosition=true} и дельтой {@code dx = 0.029} (&lt; 0.03).</li>
     * </ol>
     *
     * @return последовательность пакетов фикстуры {@code fixturePointThreeSkip}
     */
    public static List<TimedPacket> fixturePointThreeSkip() {
        return PacketSequenceFixture.named("fixturePointThreeSkip")
                .add(new MovePacket(1, 100, 0.0, 64.0, 0.0, 0.0f, 0.0f, true, true), 0)
                .add(new MovePacket(2, 101, 0.0, 64.0, 0.0, 0.5f, 0.0f, true, false), 1)
                .add(new MovePacket(3, 102, 0.029, 64.0, 0.0, 0.5f, 0.0f, true, true), 1)
                .build();
    }

    /**
     * Граница обнуления малых компонент импульса скорости ({@code |v| < 0.003} в {@code LivingEntity.aiStep()}).
     * Проверяет два импульса: {@code 0.0031} (сохраняется) и {@code 0.0029} (обнуляется сервером/клиентом).
     *
     * <p>Раздел документа: {@code §2.2 grim.md} — Knockback / Velocity Zeroing Threshold (0.003).
     *
     * <p>Последовательность:
     * <ol>
     *   <li>{@link TransactionPacket} PING(1001) + {@link VelocityPacket} ({@code vx = 0.0031}).</li>
     *   <li>{@link TransactionPacket} PONG(1001) + {@link MovePacket} со смещением {@code +0.0031}.</li>
     *   <li>{@link TransactionPacket} PING(1002) + {@link VelocityPacket} ({@code vx = 0.0029}).</li>
     *   <li>{@link TransactionPacket} PONG(1002) + {@link MovePacket} с нулевым смещением (импульс обнулён).</li>
     * </ol>
     *
     * @return последовательность пакетов фикстуры {@code fixtureVelocityBoundary}
     */
    public static List<TimedPacket> fixtureVelocityBoundary() {
        return PacketSequenceFixture.named("fixtureVelocityBoundary")
                .add(new TransactionPacket(1001, TransactionType.PING), 0)
                .add(new VelocityPacket(1, VELOCITY_ABOVE_THRESHOLD, 0.0, 0.0), 0)
                .add(new TransactionPacket(1001, TransactionType.PONG), 1)
                .add(new MovePacket(1, 201, 0.0031, 64.0, 0.0, 0.0f, 0.0f, true, true), 1)
                .add(new TransactionPacket(1002, TransactionType.PING), 1)
                .add(new VelocityPacket(1, VELOCITY_BELOW_THRESHOLD, 0.0, 0.0), 0)
                .add(new TransactionPacket(1002, TransactionType.PONG), 1)
                .add(new MovePacket(2, 204, 0.0031, 64.0, 0.0, 0.0f, 0.0f, true, true), 1)
                .build();
    }

    /**
     * Заявление {@code onGround=true} в воздухе в радиусе 4 блоков от коллизионной сущности
     * (лодка или шалкер в точке {@code (10.0, 65.0, 10.0)}), где GrimAC расширяет допуск опоры.
     *
     * <p>Раздел документа: {@code §2.3 grim.md} — GroundSpoof &amp; Boat/Shulker Collision Exemption.
     *
     * <p>Последовательность:
     * <ol>
     *   <li>{@link MovePacket} вне радиуса 4.0 ({@code d ≈ 4.031}, {@code onGround=false}).</li>
     *   <li>{@link MovePacket} внутри радиуса 4.0 ({@code d ≈ 3.354}, {@code onGround=true}).</li>
     *   <li>{@link MovePacket} ближе к сущности ({@code d ≈ 2.773}, {@code onGround=true}).</li>
     * </ol>
     *
     * @return последовательность пакетов фикстуры {@code fixtureGroundSpoofNearEntity}
     */
    public static List<TimedPacket> fixtureGroundSpoofNearEntity() {
        return PacketSequenceFixture.named("fixtureGroundSpoofNearEntity")
                .add(new MovePacket(1, 300, 13.5, 67.0, 10.0, 90.0f, 15.0f, false, true), 0)
                .add(new MovePacket(2, 301, 13.0, 66.5, 10.0, 90.0f, 15.0f, true, true), 1)
                .add(new MovePacket(3, 302, 12.5, 66.2, 10.0, 90.0f, 15.0f, true, true), 1)
                .build();
    }

    /**
     * Атака цели после пропуска позиционного пакета ({@code hasPosition=false}):
     * допустимая дистанция удара расширяется с 3.00 до 3.03 блока за счёт неопределённости 0.03.
     * Цель {@code id=42} имеет ближнюю грань хитбокса на {@code x = 3.02} (&le; 3.03).
     *
     * <p>Раздел документа: {@code §3.1 grim.md} — Reach Validation with 0.03 Position Uncertainty.
     *
     * <p>Последовательность:
     * <ol>
     *   <li>Базовый {@link MovePacket} в (0.0, 64.0, 0.0), взгляд по +X ({@code yaw=-90}).</li>
     *   <li>{@link MovePacket} с {@code hasPosition=false} (открывает расширение 0.03).</li>
     *   <li>{@link InteractPacket} ATTACK по цели 42 (дистанция до грани 3.02 &le; 3.03).</li>
     *   <li>Подтверждающий {@link MovePacket} с суб-пороговым смещением {@code x = 0.025}.</li>
     * </ol>
     *
     * @return последовательность пакетов фикстуры {@code fixtureReachWithSkip}
     */
    public static List<TimedPacket> fixtureReachWithSkip() {
        return PacketSequenceFixture.named("fixtureReachWithSkip")
                .add(new MovePacket(1, 400, 0.0, 64.0, 0.0, -90.0f, 0.0f, true, true), 0)
                .add(new MovePacket(2, 401, 0.0, 64.0, 0.0, -90.0f, 0.0f, true, false), 1)
                .add(new InteractPacket(REACH_TARGET_ID, InteractType.ATTACK), 0)
                .add(new MovePacket(3, 402, 0.025, 64.0, 0.0, -90.0f, 0.0f, true, true), 1)
                .build();
    }

    /**
     * Накопление отставания баланса таймера (&ge; 120 ms, потолок дрифта GrimAC {@code TimerA.drift = 120ms}),
     * за которым следует пачка ускоренных {@link MovePacket} с нулевой задержкой ({@code delayTicks=0}).
     *
     * <p>Раздел документа: {@code §3.2 grim.md} — Timer Balance Drift Cap (120 ms) &amp; Catch-Up Burst.
     *
     * <p>Последовательность:
     * <ol>
     *   <li>Фаза отставания: 3 {@link MovePacket} с задержками {@code [0, 2, 3]} тиков
     *       (накопленное отставание 150 ms &ge; потолка 120 ms).</li>
     *   <li>Фаза ускоренной пачки: 4 {@link MovePacket} подряд с {@code delayTicks=0}
     *       (первые 2 расходуют буфер 120 ms, 3-й и 4-й выходят за предел баланса).</li>
     * </ol>
     *
     * @return последовательность пакетов фикстуры {@code fixtureTimerDrift}
     */
    public static List<TimedPacket> fixtureTimerDrift() {
        return PacketSequenceFixture.named("fixtureTimerDrift")
                // Phase 1: lag/drift accumulation (+50ms + 100ms = 150ms >= 120ms drift cap)
                .add(new MovePacket(1, 500, 0.0, 64.0, 0.0, 0.0f, 0.0f, true, true), 0)
                .add(new MovePacket(2, 501, 0.2, 64.0, 0.0, 0.0f, 0.0f, true, true), 2)
                .add(new MovePacket(3, 502, 0.4, 64.0, 0.0, 0.0f, 0.0f, true, true), 3)
                // Phase 2: burst of accelerated MovePackets with 0-tick spacing
                .add(new MovePacket(4, 503, 0.6, 64.0, 0.0, 0.0f, 0.0f, true, true), 0)
                .add(new MovePacket(5, 504, 0.8, 64.0, 0.0, 0.0f, 0.0f, true, true), 0)
                .add(new MovePacket(6, 505, 1.0, 64.0, 0.0, 0.0f, 0.0f, true, true), 0)
                .add(new MovePacket(7, 506, 1.2, 64.0, 0.0, 0.0f, 0.0f, true, true), 0)
                .build();
    }

    /**
     * Серия отложенных ответов {@link TransactionPacket} типа {@code PONG} с сетевым джиттером,
     * при которой строго сохраняется порядок очереди FIFO для идентификаторов транзакций.
     *
     * <p>Раздел документа: {@code §4.1 grim.md} — Transaction PING/PONG Lag Compensation &amp; FIFO Ordering.
     *
     * <p>Последовательность:
     * <ol>
     *   <li>4 серверных {@code PING} с ID {@code 2001..2004} (задержки {@code [0, 1, 1, 1]}).</li>
     *   <li>4 клиентских {@code PONG} с ID {@code 2001..2004} после задержки RTT
     *       (задержки {@code [5, 1, 2, 1]}), FIFO-порядок строго сохранён.</li>
     * </ol>
     *
     * @return последовательность пакетов фикстуры {@code fixtureTransactionDelay}
     */
    public static List<TimedPacket> fixtureTransactionDelay() {
        return PacketSequenceFixture.named("fixtureTransactionDelay")
                .add(new TransactionPacket(2001, TransactionType.PING), 0)
                .add(new TransactionPacket(2002, TransactionType.PING), 1)
                .add(new TransactionPacket(2003, TransactionType.PING), 1)
                .add(new TransactionPacket(2004, TransactionType.PING), 1)
                .add(new TransactionPacket(2001, TransactionType.PONG), 5)
                .add(new TransactionPacket(2002, TransactionType.PONG), 1)
                .add(new TransactionPacket(2003, TransactionType.PONG), 2)
                .add(new TransactionPacket(2004, TransactionType.PONG), 1)
                .build();
    }

    /**
     * Смена рук {@link ActionType#SWAP_ITEM_WITH_OFFHAND} в том же тике ({@code delayTicks=0}),
     * что и активация использования предмета ({@link ActionType#USE_ITEM}), с последующим
     * {@link MovePacket} на полной скорости (граничный кейс NoSlow-синхронизации состояния использования).
     *
     * <p>Раздел документа: {@code §4.2 grim.md} — NoSlow Item-Use State &amp; Offhand Swap Desync.
     *
     * <p>Последовательность:
     * <ol>
     *   <li>Базовый {@link MovePacket} в (0.0, 64.0, 0.0).</li>
     *   <li>{@link ActionPacket} {@code USE_ITEM} на тике +1.</li>
     *   <li>{@link ActionPacket} {@code SWAP_ITEM_WITH_OFFHAND} в том же тике ({@code delayTicks=0}).</li>
     *   <li>{@link MovePacket} в том же тике с полным шагом {@code dx = 0.28} (без замедления 0.2x).</li>
     *   <li>Следующий {@link MovePacket} на тике +1 с шагом {@code dx = 0.28}.</li>
     * </ol>
     *
     * @return последовательность пакетов фикстуры {@code fixtureNoSlowHandSwap}
     */
    public static List<TimedPacket> fixtureNoSlowHandSwap() {
        return PacketSequenceFixture.named("fixtureNoSlowHandSwap")
                .add(new MovePacket(1, 600, 0.0, 64.0, 0.0, 0.0f, 0.0f, true, true), 0)
                .add(new ActionPacket(ActionType.USE_ITEM, BlockPos.ZERO, Direction.NONE), 1)
                .add(new ActionPacket(ActionType.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.NONE), 0)
                .add(new MovePacket(2, 601, 0.28, 64.0, 0.0, 0.0f, 0.0f, true, true), 0)
                .add(new MovePacket(3, 602, 0.56, 64.0, 0.0, 0.0f, 0.0f, true, true), 1)
                .build();
    }

    /**
     * Попытка установки блока с последующей серверной отменой и движением игрока в коллизию
     * с клиентским фантомным (ghost) блоком до завершения ресинхронизации.
     *
     * <p>Раздел документа: {@code §4.3 grim.md} — Phase / Ghost Block Placement Cancellation Resync.
     *
     * <p>Последовательность:
     * <ol>
     *   <li>Базовый {@link MovePacket} перед точкой установки {@code (5.0, 64.0, 5.0)}.</li>
     *   <li>{@link ActionPacket} {@code PLACE_BLOCK} в {@link #PHANTOM_BLOCK_POS} {@code (6, 64, 5)}.</li>
     *   <li>{@link ActionPacket} {@code CANCEL_PLACE_BLOCK} в {@code (6, 64, 5)} (отмена сервером).</li>
     *   <li>{@link MovePacket} вплотную к грани фантомного блока ({@code x = 5.70}, AABB maxX = 6.00).</li>
     *   <li>{@link MovePacket} с проникновением в координаты фантомного блока ({@code x = 6.05}).</li>
     * </ol>
     *
     * @return последовательность пакетов фикстуры {@code fixturePhaseResync}
     */
    public static List<TimedPacket> fixturePhaseResync() {
        return PacketSequenceFixture.named("fixturePhaseResync")
                .add(new MovePacket(1, 700, 5.0, 64.0, 5.0, -90.0f, 20.0f, true, true), 0)
                .add(new ActionPacket(ActionType.PLACE_BLOCK, PHANTOM_BLOCK_POS, Direction.WEST), 1)
                .add(new ActionPacket(ActionType.CANCEL_PLACE_BLOCK, PHANTOM_BLOCK_POS, Direction.WEST), 0)
                .add(new MovePacket(2, 702, 5.70, 64.0, 5.0, -90.0f, 20.0f, true, true), 1)
                .add(new MovePacket(3, 703, 6.05, 64.0, 5.0, -90.0f, 20.0f, true, true), 1)
                .build();
    }
}

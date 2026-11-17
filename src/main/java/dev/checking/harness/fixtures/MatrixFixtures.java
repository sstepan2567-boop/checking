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
 * Named protocol packet sequence fixtures covering Matrix 7.x server-side validator boundary conditions
 * documented in {@code matrix.md}.
 */
public final class MatrixFixtures {

    /** Matrix 7.x velocity wait window limit in ticks modeled by {@link #fixtureVelocityEdge()}. */
    public static final int VELOCITY_WAIT_TIME_TICKS = 33;

    /** Hitbox lag-compensation parameters modeled by {@link #fixtureHitboxHistory()}. */
    public static final int HITBOX_HISTORY_TICKS = 12;
    public static final int HITBOX_TARGET_ID = 77;
    public static final double HITBOX_REACH_LIMIT = 3.075;
    public static final double TARGET_HALF_WIDTH = 0.30;

    /** Number of post-setback grace ticks in {@link #fixtureSetbackWindow()}. */
    public static final int SETBACK_WINDOW_TICKS = 5;

    /** Tight geometry trapdoor parameters in {@link #fixtureInteractRays()}. */
    public static final BlockPos TRAPDOOR_POS = new BlockPos(20, 65, 20);
    public static final int TRAPDOOR_TARGET_ID = 88;

    /** Offset bamboo block geometry in {@link #fixtureBambooCollision()}. */
    public static final BlockPos BAMBOO_POS = new BlockPos(30, 64, 30);
    public static final double BAMBOO_OFFSET_X = 0.1875;
    public static final double BAMBOO_OFFSET_Z = -0.15625;
    public static final double BAMBOO_STALK_HALF_WIDTH = 0.1875;
    public static final double PLAYER_HALF_WIDTH = 0.30;

    private MatrixFixtures() {
    }

    /**
     * Накопление таймер-кредита при простое в 280 тиков (низкая частота {@link MovePacket}:
     * 1 пакет каждые 20 тиков = 1 Гц), за которым следуют 20 тиков с частотой выше 20 Гц
     * (2 пакета за тик = 40 Гц).
     *
     * <p>Раздел документа: {@code §2.1 matrix.md} — Timer Credit Pool &amp; Over-20Hz Burst Window.
     *
     * <p>Последовательность:
     * <ol>
     *   <li>Фаза простоя: 15 {@link MovePacket} (стартовый + 14 пакетов с шагом {@code delayTicks=20},
     *       итого ровно 280 тиков простоя при частоте ~1 Гц).</li>
     *   <li>Фаза ускорения: 40 {@link MovePacket} за 20 тиков (пары {@code delayTicks=1} и {@code delayTicks=0},
     *       эффективная частота 40 Гц &gt; 20 Гц).</li>
     * </ol>
     *
     * @return последовательность пакетов фикстуры {@code fixtureTimerCredit}
     */
    public static List<TimedPacket> fixtureTimerCredit() {
        PacketSequenceFixture builder = PacketSequenceFixture.named("fixtureTimerCredit");
        long seq = 1;
        long tick = 0;

        // Phase 1: 280 ticks of idle/low-frequency packets (1 packet every 20 ticks = 1 Hz)
        builder.add(new MovePacket(seq++, tick, 0.0, 64.0, 0.0, 0.0f, 0.0f, true, true), 0);
        for (int i = 0; i < 14; i++) {
            tick += 20;
            builder.add(new MovePacket(seq++, tick, 0.0, 64.0, 0.0, 0.0f, 0.0f, true, true), 20);
        }

        // Phase 2: 20 ticks at 40 Hz (> 20 Hz): 2 MovePackets per tick (delay 1, then delay 0)
        double x = 0.0;
        for (int i = 0; i < 20; i++) {
            tick += 1;
            x += 0.22;
            builder.add(new MovePacket(seq++, tick, x, 64.0, 0.0, 0.0f, 0.0f, true, true), 1);
            x += 0.22;
            builder.add(new MovePacket(seq++, tick, x, 64.0, 0.0, 0.0f, 0.0f, true, true), 0);
        }

        return builder.build();
    }

    /**
     * Проверка граничного окна ожидания отбрасывания {@code wait_time = 33} тика в Matrix 7.x:
     * {@link VelocityPacket}, затем {@link MovePacket} на 30-м тике (вблизи границы 33)
     * и на 31-м тике с нулевым горизонтальным смещением из-за коллизии со стеной.
     *
     * <p>Раздел документа: {@code §2.2 matrix.md} — Velocity {@code wait_time} (33 Ticks) Boundary &amp; Wall Collision.
     *
     * <p>Последовательность:
     * <ol>
     *   <li>{@link VelocityPacket} ({@code vx=0.42, vy=0.36, vz=0.0}) в тике 0.</li>
     *   <li>{@link MovePacket} через 30 тиков ({@code delayTicks=30}, суммарно тик 30 &lt; 33).</li>
     *   <li>{@link MovePacket} на следующем тике ({@code delayTicks=1}, суммарно тик 31 &lt; 33,
     *       коллизия: позиция {@code x=0.0} зажата без горизонтального сдвига).</li>
     * </ol>
     *
     * @return последовательность пакетов фикстуры {@code fixtureVelocityEdge}
     */
    public static List<TimedPacket> fixtureVelocityEdge() {
        return PacketSequenceFixture.named("fixtureVelocityEdge")
                .add(new VelocityPacket(1, 0.42, 0.36, 0.0), 0)
                .add(new MovePacket(1, 30, 0.0, 64.0, 0.0, 0.0f, 0.0f, true, true), 30)
                .add(new MovePacket(2, 31, 0.0, 64.0, 0.0, 0.0f, 0.0f, true, true), 1)
                .build();
    }

    /**
     * История позиций цели за 12 тиков и атака {@link InteractPacket} по историческому хитбоксу
     * в пределах {@code 3.075} блока (при том, что текущая позиция цели уже вышла за лимит).
     *
     * <p>Раздел документа: {@code §3.1 matrix.md} — Hitbox History Lag Compensation (12 Ticks, 3.075 Reach).
     *
     * <p>Последовательность:
     * <ol>
     *   <li>12 {@link MovePacket} цели {@code id=77} (тики 100..111, {@code x} от 3.35 до 4.45 с шагом 0.10):
     *       ближняя грань самого старого хитбокса находится на {@code 3.35 - 0.30 = 3.05 <= 3.075},
     *       а последнего — на {@code 4.45 - 0.30 = 4.15 > 3.075}.</li>
     *   <li>{@link InteractPacket} ATTACK по цели {@code 77} в последнем тике ({@code delayTicks=0}).</li>
     * </ol>
     *
     * @return последовательность пакетов фикстуры {@code fixtureHitboxHistory}
     */
    public static List<TimedPacket> fixtureHitboxHistory() {
        PacketSequenceFixture builder = PacketSequenceFixture.named("fixtureHitboxHistory");
        for (int i = 0; i < HITBOX_HISTORY_TICKS; i++) {
            double targetX = Math.round((3.35 + i * 0.10) * 1000.0) / 1000.0;
            builder.add(
                    new MovePacket(i + 1L, 100L + i, targetX, 64.0, 0.0, 90.0f, 0.0f, true, true),
                    i == 0 ? 0 : 1
            );
        }
        builder.add(new InteractPacket(HITBOX_TARGET_ID, InteractType.ATTACK), 0);
        return builder.build();
    }

    /**
     * Окно ресинхронизации после серверного отката (setback): аномальный {@link MovePacket},
     * ответ сервера {@code PlayerPositionLook} (эмулируется как {@link TransactionPacket} типа {@code SETBACK}),
     * и 5 последующих тиков с {@link MovePacket} ({@code onGround=true}).
     *
     * <p>Раздел документа: {@code §3.2 matrix.md} — Post-Setback Grace Window (5 Ticks).
     *
     * <p>Последовательность:
     * <ol>
     *   <li>Базовый {@link MovePacket} в (0.0, 64.0, 0.0).</li>
     *   <li>Аномальный {@link MovePacket} в (4.5, 66.0, 0.0), {@code onGround=false}.</li>
     *   <li>{@link TransactionPacket} {@code SETBACK(3001)} — эмуляция {@code PlayerPositionLook}.</li>
     *   <li>5 последовательных {@link MovePacket} с {@code onGround=true} ({@code delayTicks=1} на каждый тик).</li>
     * </ol>
     *
     * @return последовательность пакетов фикстуры {@code fixtureSetbackWindow}
     */
    public static List<TimedPacket> fixtureSetbackWindow() {
        PacketSequenceFixture builder = PacketSequenceFixture.named("fixtureSetbackWindow")
                .add(new MovePacket(1, 800, 0.0, 64.0, 0.0, 0.0f, 0.0f, true, true), 0)
                .add(new MovePacket(2, 801, 4.5, 66.0, 0.0, 0.0f, 0.0f, false, true), 1)
                .add(new TransactionPacket(3001, TransactionType.SETBACK), 0);

        for (int i = 1; i <= SETBACK_WINDOW_TICKS; i++) {
            builder.add(new MovePacket(2L + i, 801L + i, 0.0, 64.0, 0.0, 0.0f, 0.0f, true, true), 1);
        }
        return builder.build();
    }

    /**
     * Частота установки блоков (каденс Scaffold): прямой мост каждые 6 тиков (норма),
     * диагональный мост каждые 4 тика (граница нормы), ускоренная установка каждые 3 тика (нарушение).
     *
     * <p>Раздел документа: {@code §4.1 matrix.md} — Scaffold Placement Cadence (6 / 4 / 3 Ticks).
     *
     * <p>Последовательность:
     * <ol>
     *   <li>Прямой мост (3 установки): задержки {@code [0, 6, 6]} по оси +X.</li>
     *   <li>Диагональный мост (3 установки): задержки {@code [4, 4, 4]} с чередованием +Z/+X.</li>
     *   <li>Нарушение каденса (3 установки): задержки {@code [3, 3, 3]}.</li>
     * </ol>
     *
     * @return последовательность пакетов фикстуры {@code fixtureScaffoldCadence}
     */
    public static List<TimedPacket> fixtureScaffoldCadence() {
        return PacketSequenceFixture.named("fixtureScaffoldCadence")
                // Straight bridge: every 6 ticks
                .add(new ActionPacket(ActionType.PLACE_BLOCK, new BlockPos(10, 63, 10), Direction.EAST), 0)
                .add(new ActionPacket(ActionType.PLACE_BLOCK, new BlockPos(11, 63, 10), Direction.EAST), 6)
                .add(new ActionPacket(ActionType.PLACE_BLOCK, new BlockPos(12, 63, 10), Direction.EAST), 6)
                // Diagonal bridge: every 4 ticks
                .add(new ActionPacket(ActionType.PLACE_BLOCK, new BlockPos(12, 63, 11), Direction.SOUTH), 4)
                .add(new ActionPacket(ActionType.PLACE_BLOCK, new BlockPos(13, 63, 11), Direction.EAST), 4)
                .add(new ActionPacket(ActionType.PLACE_BLOCK, new BlockPos(13, 63, 12), Direction.SOUTH), 4)
                // Violation cadence: every 3 ticks
                .add(new ActionPacket(ActionType.PLACE_BLOCK, new BlockPos(14, 63, 12), Direction.EAST), 3)
                .add(new ActionPacket(ActionType.PLACE_BLOCK, new BlockPos(15, 63, 12), Direction.EAST), 3)
                .add(new ActionPacket(ActionType.PLACE_BLOCK, new BlockPos(16, 63, 12), Direction.EAST), 3)
                .build();
    }

    /**
     * Переключение позы (приседание/вставание) пакетами {@link ActionType#PRESS_SHIFT} и
     * {@link ActionType#RELEASE_SHIFT}, обрамляющими {@link MovePacket} при смене высоты хитбокса и скорости.
     *
     * <p>Раздел документа: {@code §4.2 matrix.md} — Pose Switch &amp; Sneak Bounding-Box Transitions.
     *
     * <p>Последовательность:
     * <ol>
     *   <li>{@link MovePacket} стоя (0.0, 64.0, 0.0).</li>
     *   <li>{@link ActionPacket} {@code PRESS_SHIFT}.</li>
     *   <li>Два {@link MovePacket} в приседе ({@code dx = 0.065} на тик).</li>
     *   <li>{@link ActionPacket} {@code RELEASE_SHIFT} в том же тике ({@code delayTicks=0}).</li>
     *   <li>{@link MovePacket} после вставания с обычной скоростью ({@code dx = 0.22}).</li>
     * </ol>
     *
     * @return последовательность пакетов фикстуры {@code fixturePoseSwitch}
     */
    public static List<TimedPacket> fixturePoseSwitch() {
        return PacketSequenceFixture.named("fixturePoseSwitch")
                .add(new MovePacket(1, 900, 0.0, 64.0, 0.0, 0.0f, 0.0f, true, true), 0)
                .add(new ActionPacket(ActionType.PRESS_SHIFT, BlockPos.ZERO, Direction.NONE), 1)
                .add(new MovePacket(2, 901, 0.065, 64.0, 0.0, 0.0f, 0.0f, true, true), 0)
                .add(new MovePacket(3, 902, 0.130, 64.0, 0.0, 0.0f, 0.0f, true, true), 1)
                .add(new ActionPacket(ActionType.RELEASE_SHIFT, BlockPos.ZERO, Direction.NONE), 0)
                .add(new MovePacket(4, 903, 0.350, 64.0, 0.0, 0.0f, 0.0f, true, true), 1)
                .build();
    }

    /**
     * Рейкаст взаимодействия {@link InteractPacket} в тесной геометрии под закрытым люком
     * ({@link #TRAPDOOR_POS} {@code (20, 65, 20)}, высота зазора 1.0 блок {@code y ∈ [64.0, 65.0]},
     * игрок в точке {@code (20.5, 64.0, 20.5)} с пониженной высотой глаз).
     *
     * <p>Раздел документа: {@code §4.3 matrix.md} — Tight-Geometry Interaction Raycasting Under Trapdoor.
     *
     * <p>Последовательность:
     * <ol>
     *   <li>{@link ActionPacket} {@code PRESS_SHIFT} под люком {@code (20, 65, 20)}.</li>
     *   <li>{@link MovePacket} в координатах под люком {@code (20.5, 64.0, 20.5)}, {@code pitch = 5.0}.</li>
     *   <li>{@link InteractPacket} {@code INTERACT_AT} по цели {@code 88} в том же тике.</li>
     *   <li>{@link InteractPacket} {@code ATTACK} по цели {@code 88} на следующем тике.</li>
     * </ol>
     *
     * @return последовательность пакетов фикстуры {@code fixtureInteractRays}
     */
    public static List<TimedPacket> fixtureInteractRays() {
        return PacketSequenceFixture.named("fixtureInteractRays")
                .add(new ActionPacket(ActionType.PRESS_SHIFT, TRAPDOOR_POS, Direction.DOWN), 0)
                .add(new MovePacket(1, 1000, 20.5, 64.0, 20.5, 0.0f, 5.0f, true, true), 1)
                .add(new InteractPacket(TRAPDOOR_TARGET_ID, InteractType.INTERACT_AT), 0)
                .add(new InteractPacket(TRAPDOOR_TARGET_ID, InteractType.ATTACK), 1)
                .build();
    }

    /**
     * Движение {@link MovePacket} вплотную к бамбуку {@link #BAMBOO_POS} {@code (30, 64, 30)},
     * чей хитбокс смещён по осям XZ ({@code offsetX = +0.1875, offsetZ = -0.15625},
     * истинный AABB стебля: {@code x ∈ [30.50, 30.875], z ∈ [30.15625, 30.53125]}).
     *
     * <p>Раздел документа: {@code §4.4 matrix.md} — Offset-Hitbox Block Collision (Bamboo XZ Offset).
     *
     * <p>Последовательность:
     * <ol>
     *   <li>Подход к блоку бамбука ({@code x = 29.80, z = 30.34375}).</li>
     *   <li>Проход вплотную к смещённой грани {@code -X} ({@code x = 30.199}, {@code player maxX = 30.499}
     *       &lt; смещённой {@code minX = 30.50}, но пересекает несмещённую {@code 30.3125}).</li>
     *   <li>Проход вплотную к смещённой грани {@code -Z} ({@code z = 29.855}, {@code player maxZ = 30.155}).</li>
     *   <li>Проход вплотную к смещённой грани {@code +X} ({@code x = 31.176}, {@code player minX = 30.876}).</li>
     * </ol>
     *
     * @return последовательность пакетов фикстуры {@code fixtureBambooCollision}
     */
    public static List<TimedPacket> fixtureBambooCollision() {
        return PacketSequenceFixture.named("fixtureBambooCollision")
                .add(new MovePacket(1, 1100, 29.80, 64.0, 30.34375, -90.0f, 0.0f, true, true), 0)
                .add(new MovePacket(2, 1101, 30.199, 64.0, 30.34375, -90.0f, 0.0f, true, true), 1)
                .add(new MovePacket(3, 1102, 30.6875, 64.0, 29.855, 0.0f, 0.0f, true, true), 1)
                .add(new MovePacket(4, 1103, 31.176, 64.0, 30.34375, 90.0f, 0.0f, true, true), 1)
                .build();
    }
}

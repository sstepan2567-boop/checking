package dev.checking.harness.scenarios;

import dev.checking.harness.fixtures.MatrixFixtures;
import dev.checking.harness.fixtures.TimedPacket;
import dev.checking.harness.packets.MovePacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Сценарий проигрывания фикстуры {@link MatrixFixtures#fixtureTimerCredit()} через {@link PacketSink}.
 *
 * <p>Раздел документа: {@code §2.1 matrix.md} — Timer Credit Pool &amp; Over-20Hz Burst Window.
 * Воспроизводит простой {@link #idleTicks} тиков (шаг {@link #idleIntervalTicks} тиков),
 * затем {@link #burstTicks} тиков с плотностью {@link #packetsPerBurstTick} пакета за тик (&gt; 20 Гц).
 */
public final class FixtureTimerCreditScenario implements Scenario {

    public static final int DEFAULT_IDLE_TICKS = 280;
    public static final int DEFAULT_IDLE_INTERVAL_TICKS = 20;
    public static final int DEFAULT_BURST_TICKS = 20;
    public static final int DEFAULT_PACKETS_PER_BURST_TICK = 2;
    public static final double DEFAULT_BURST_STEP_X = 0.22;

    private final int idleTicks;
    private final int idleIntervalTicks;
    private final int burstTicks;
    private final int packetsPerBurstTick;
    private final double burstStepX;

    public FixtureTimerCreditScenario() {
        this(
                DEFAULT_IDLE_TICKS,
                DEFAULT_IDLE_INTERVAL_TICKS,
                DEFAULT_BURST_TICKS,
                DEFAULT_PACKETS_PER_BURST_TICK,
                DEFAULT_BURST_STEP_X
        );
    }

    public FixtureTimerCreditScenario(
            int idleTicks,
            int idleIntervalTicks,
            int burstTicks,
            int packetsPerBurstTick,
            double burstStepX
    ) {
        if (idleTicks < 0 || idleIntervalTicks <= 0 || burstTicks < 0 || packetsPerBurstTick <= 0) {
            throw new IllegalArgumentException("Invalid timer credit parameters");
        }
        if (idleTicks % idleIntervalTicks != 0) {
            throw new IllegalArgumentException("idleTicks must be divisible by idleIntervalTicks");
        }
        this.idleTicks = idleTicks;
        this.idleIntervalTicks = idleIntervalTicks;
        this.burstTicks = burstTicks;
        this.packetsPerBurstTick = packetsPerBurstTick;
        this.burstStepX = burstStepX;
    }

    @Override
    public void execute(PacketSink sink) {
        Objects.requireNonNull(sink, "sink must not be null");
        List<TimedPacket> base = MatrixFixtures.fixtureTimerCredit();

        if (idleTicks == DEFAULT_IDLE_TICKS
                && idleIntervalTicks == DEFAULT_IDLE_INTERVAL_TICKS
                && burstTicks == DEFAULT_BURST_TICKS
                && packetsPerBurstTick == DEFAULT_PACKETS_PER_BURST_TICK
                && Double.compare(burstStepX, DEFAULT_BURST_STEP_X) == 0) {
            ScenarioSupport.replay(sink, base);
            return;
        }

        MovePacket template = (MovePacket) base.getFirst().packet();
        int idleSteps = idleTicks / idleIntervalTicks;
        List<TimedPacket> sequence = new ArrayList<>(1 + idleSteps + burstTicks * packetsPerBurstTick);

        long seq = template.seq();
        long tick = template.tick();
        sequence.add(new TimedPacket(template, 0));
        seq++;

        for (int i = 0; i < idleSteps; i++) {
            tick += idleIntervalTicks;
            sequence.add(new TimedPacket(
                    new MovePacket(seq++, tick, template.x(), template.y(), template.z(),
                            template.yaw(), template.pitch(), template.onGround(), template.hasPosition()),
                    idleIntervalTicks
            ));
        }

        double x = template.x();
        for (int t = 0; t < burstTicks; t++) {
            tick += 1;
            for (int p = 0; p < packetsPerBurstTick; p++) {
                x += burstStepX;
                sequence.add(new TimedPacket(
                        new MovePacket(seq++, tick, x, template.y(), template.z(),
                                template.yaw(), template.pitch(), template.onGround(), template.hasPosition()),
                        p == 0 ? 1 : 0
                ));
            }
        }

        ScenarioSupport.replay(sink, sequence);
    }

    public int getIdleTicks() {
        return idleTicks;
    }

    public int getIdleIntervalTicks() {
        return idleIntervalTicks;
    }

    public int getBurstTicks() {
        return burstTicks;
    }

    public int getPacketsPerBurstTick() {
        return packetsPerBurstTick;
    }

    public double getBurstStepX() {
        return burstStepX;
    }
}

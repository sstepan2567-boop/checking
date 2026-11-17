package dev.checking.harness.scenarios;

import dev.checking.harness.fixtures.TimedPacket;
import dev.checking.harness.packets.ActionPacket;
import dev.checking.harness.packets.InteractPacket;
import dev.checking.harness.packets.MovePacket;
import dev.checking.harness.packets.Packet;
import dev.checking.harness.packets.TransactionPacket;
import dev.checking.harness.packets.VelocityPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * In-memory {@link PacketSink} implementation that records every {@code send}, {@code cancel},
 * and {@code delay} invocation together with its simulated tick timestamp.
 */
public final class RecordingSink implements PacketSink {

    /**
     * Kind of operation invoked on {@link PacketSink}.
     */
    public enum Operation {
        SEND,
        CANCEL,
        DELAY
    }

    /**
     * Immutable record of a single {@link PacketSink} call.
     *
     * @param operation  invoked operation ({@link Operation#SEND}, {@link Operation#CANCEL}, or {@link Operation#DELAY})
     * @param packet     protocol packet passed to the sink
     * @param tick       cumulative server tick at the time the call completed
     * @param delayTicks tick delay supplied to {@link PacketSink#delay(Packet, int)} ({@code 0} for send/cancel)
     */
    public record RecordedCall(Operation operation, Packet packet, long tick, int delayTicks) {

        public RecordedCall {
            Objects.requireNonNull(operation, "operation must not be null");
            Objects.requireNonNull(packet, "packet must not be null");
            if (tick < 0) {
                throw new IllegalArgumentException("tick must be >= 0, got: " + tick);
            }
            if (delayTicks < 0) {
                throw new IllegalArgumentException("delayTicks must be >= 0, got: " + delayTicks);
            }
        }
    }

    private final List<RecordedCall> calls = new ArrayList<>();
    private long currentTick;

    @Override
    public void send(MovePacket p) {
        recordEmit(Operation.SEND, p);
    }

    @Override
    public void send(VelocityPacket p) {
        recordEmit(Operation.SEND, p);
    }

    @Override
    public void send(TransactionPacket p) {
        recordEmit(Operation.SEND, p);
    }

    @Override
    public void send(ActionPacket p) {
        recordEmit(Operation.SEND, p);
    }

    @Override
    public void send(InteractPacket p) {
        recordEmit(Operation.SEND, p);
    }

    @Override
    public void cancel(Packet p) {
        recordEmit(Operation.CANCEL, p);
    }

    @Override
    public void delay(Packet p, int ticks) {
        Objects.requireNonNull(p, "packet must not be null");
        if (ticks < 0) {
            throw new IllegalArgumentException("ticks must be >= 0, got: " + ticks);
        }
        currentTick += ticks;
        calls.add(new RecordedCall(Operation.DELAY, p, currentTick, ticks));
    }

    private void recordEmit(Operation operation, Packet packet) {
        Objects.requireNonNull(packet, "packet must not be null");
        calls.add(new RecordedCall(operation, packet, currentTick, 0));
    }

    /**
     * Returns an immutable snapshot of all recorded sink invocations in execution order.
     *
     * @return recorded calls
     */
    public List<RecordedCall> getCalls() {
        return List.copyOf(calls);
    }

    /**
     * Returns all recorded calls matching the given {@link Operation}.
     *
     * @param operation operation filter
     * @return filtered immutable list of calls
     */
    public List<RecordedCall> getCallsByOperation(Operation operation) {
        Objects.requireNonNull(operation, "operation must not be null");
        return calls.stream().filter(c -> c.operation() == operation).toList();
    }

    /**
     * Returns the current cumulative tick reached after all recorded {@link #delay(Packet, int)} calls.
     *
     * @return current simulated tick
     */
    public long getCurrentTick() {
        return currentTick;
    }

    /**
     * Reconstructs the {@link TimedPacket} sequence corresponding to all emitted
     * ({@link Operation#SEND} / {@link Operation#CANCEL}) packets and their preceding
     * {@link Operation#DELAY} tick offsets.
     *
     * @return immutable list of reconstructed {@link TimedPacket} entries
     */
    public List<TimedPacket> toTimedPackets() {
        List<TimedPacket> result = new ArrayList<>();
        int pendingDelay = 0;
        for (RecordedCall call : calls) {
            switch (call.operation()) {
                case DELAY -> pendingDelay += call.delayTicks();
                case SEND, CANCEL -> {
                    result.add(new TimedPacket(call.packet(), pendingDelay));
                    pendingDelay = 0;
                }
            }
        }
        return List.copyOf(result);
    }

    /**
     * Clears all recorded calls and resets the simulated tick counter to {@code 0}.
     */
    public void reset() {
        calls.clear();
        currentTick = 0;
    }
}

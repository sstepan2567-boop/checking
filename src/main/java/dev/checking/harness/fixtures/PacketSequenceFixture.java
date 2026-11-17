package dev.checking.harness.fixtures;

import dev.checking.harness.packets.Packet;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fluent builder that assembles named sequences of {@link TimedPacket} entries {@code (packet, delayTicks)}.
 *
 * <p>Usage example:
 * <pre>{@code
 * List<TimedPacket> seq = PacketSequenceFixture.named("fixturePointThreeSkip")
 *         .add(move1, 0)
 *         .add(move2, 1)
 *         .build();
 * }</pre>
 */
public final class PacketSequenceFixture {

    private final String name;
    private final List<TimedPacket> entries = new ArrayList<>();

    private PacketSequenceFixture(String name) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }

    /**
     * Starts a new named packet sequence builder.
     *
     * @param name unique fixture identifier (e.g. {@code "fixturePointThreeSkip"})
     * @return builder instance
     */
    public static PacketSequenceFixture named(String name) {
        return new PacketSequenceFixture(name);
    }

    /**
     * Alias for {@link #named(String)}.
     *
     * @param name unique fixture identifier
     * @return builder instance
     */
    public static PacketSequenceFixture builder(String name) {
        return named(name);
    }

    /**
     * Appends a packet with the specified tick delay relative to the previous entry.
     *
     * @param packet     protocol packet
     * @param delayTicks non-negative tick delay
     * @return this builder
     */
    public PacketSequenceFixture add(Packet packet, int delayTicks) {
        entries.add(new TimedPacket(packet, delayTicks));
        return this;
    }

    /**
     * Appends a packet occurring in the same tick ({@code delayTicks = 0}).
     *
     * @param packet protocol packet
     * @return this builder
     */
    public PacketSequenceFixture addImmediate(Packet packet) {
        return add(packet, 0);
    }

    /**
     * Appends a packet occurring on the immediately following tick ({@code delayTicks = 1}).
     *
     * @param packet protocol packet
     * @return this builder
     */
    public PacketSequenceFixture addNextTick(Packet packet) {
        return add(packet, 1);
    }

    /**
     * Appends all pre-built {@link TimedPacket} entries.
     *
     * @param timedPackets list of timed packets
     * @return this builder
     */
    public PacketSequenceFixture addAll(List<TimedPacket> timedPackets) {
        Objects.requireNonNull(timedPackets, "timedPackets must not be null");
        for (TimedPacket timedPacket : timedPackets) {
            Objects.requireNonNull(timedPacket, "timedPacket element must not be null");
            entries.add(timedPacket);
        }
        return this;
    }

    /**
     * Returns the fixture name associated with this builder.
     *
     * @return fixture name
     */
    public String name() {
        return name;
    }

    /**
     * Builds an immutable {@link List} of {@link TimedPacket} entries.
     *
     * @return immutable packet sequence
     */
    public List<TimedPacket> build() {
        return List.copyOf(entries);
    }
}

package dev.checking.harness.validation;

import dev.checking.harness.packets.MovePacket;
import dev.checking.harness.packets.Packet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Isolated, dependency-free server-side state tracked while replaying a packet sequence through a {@link Validator}.
 */
public final class ServerState {

    private final String fixtureName;
    private long elapsedTicks;
    private int processedPackets;
    private Packet lastPacket;
    private MovePacket lastMovePacket;
    private final List<Packet> packetHistory = new ArrayList<>();

    private ServerState(String fixtureName) {
        this.fixtureName = Objects.requireNonNull(fixtureName, "fixtureName must not be null");
    }

    /**
     * Creates a fresh {@link ServerState} scoped to the given fixture name.
     *
     * @param fixtureName fixture identifier (e.g. {@code "fixturePointThreeSkip"})
     * @return new isolated server state
     */
    public static ServerState forFixture(String fixtureName) {
        return new ServerState(fixtureName);
    }

    /**
     * Advances simulated server time by {@code delayTicks}.
     *
     * @param delayTicks non-negative tick delta
     */
    public void advanceTicks(int delayTicks) {
        if (delayTicks < 0) {
            throw new IllegalArgumentException("delayTicks must be >= 0, got: " + delayTicks);
        }
        this.elapsedTicks += delayTicks;
    }

    /**
     * Records an observed packet in this state's minimal replay bookkeeping.
     *
     * @param packet observed packet
     */
    public void recordPacket(Packet packet) {
        Objects.requireNonNull(packet, "packet must not be null");
        this.processedPackets++;
        this.lastPacket = packet;
        if (packet instanceof MovePacket move) {
            this.lastMovePacket = move;
        }
        this.packetHistory.add(packet);
    }

    public String getFixtureName() {
        return fixtureName;
    }

    public long getElapsedTicks() {
        return elapsedTicks;
    }

    public int getProcessedPackets() {
        return processedPackets;
    }

    public Packet getLastPacket() {
        return lastPacket;
    }

    public MovePacket getLastMovePacket() {
        return lastMovePacket;
    }

    public List<Packet> getPacketHistory() {
        return Collections.unmodifiableList(packetHistory);
    }
}

package dev.checking.harness.scenarios;

import dev.checking.harness.packets.ActionPacket;
import dev.checking.harness.packets.InteractPacket;
import dev.checking.harness.packets.MovePacket;
import dev.checking.harness.packets.Packet;
import dev.checking.harness.packets.TransactionPacket;
import dev.checking.harness.packets.VelocityPacket;

/**
 * Abstract server-side packet receiver driven by protocol {@link Scenario} implementations.
 */
public interface PacketSink {

    void send(MovePacket p);

    void send(VelocityPacket p);

    void send(TransactionPacket p);

    void send(ActionPacket p);

    void send(InteractPacket p);

    void cancel(Packet p);

    void delay(Packet p, int ticks);
}

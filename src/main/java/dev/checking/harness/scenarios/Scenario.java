package dev.checking.harness.scenarios;

/**
 * Executable protocol scenario that replays a fixture sequence through an abstract {@link PacketSink}.
 */
@FunctionalInterface
public interface Scenario {

    /**
     * Replays this scenario's packet sequence against the supplied {@link PacketSink}.
     *
     * @param sink target packet sink
     */
    void execute(PacketSink sink);
}

package io.github.agorokh.scenariocraft.buildbattle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

class BattleCommandTest {
    @Test
    void allowedPlayerCanStartButCannotStop() {
        FakeRound round = new FakeRound();
        BattleCommand command = new BattleCommand(settings(true), round, new FakeResults());
        SenderRig sender = new SenderRig("BuilderKid", false);

        assertTrue(command.onCommand(sender.sender(), null, "battle", new String[] {"start"}));
        assertEquals(1, round.starts);

        assertTrue(command.onCommand(sender.sender(), null, "battle", new String[] {"stop"}));
        assertEquals(0, round.stops);
        assertEquals("Only a grown-up helper can stop the battle.", sender.messages().getLast());
    }

    @Test
    void restrictedStartRequiresOperatorOrConfiguredName() {
        FakeRound round = new FakeRound();
        BattleCommand command = new BattleCommand(settings(false), round, new FakeResults());
        SenderRig visitor = new SenderRig("Visitor", false);
        SenderRig configured = new SenderRig("BuilderKid", false);

        command.onCommand(visitor.sender(), null, "battle", new String[] {"start"});
        command.onCommand(configured.sender(), null, "battle", new String[] {"start"});

        assertEquals(1, round.starts);
        assertEquals(
                "A grown-up helper needs to start this battle.",
                visitor.messages().getFirst());
    }

    @Test
    void operatorCanStopAndUnknownSubcommandShowsTextUsage() {
        FakeRound round = new FakeRound();
        BattleCommand command = new BattleCommand(settings(true), round, new FakeResults());
        SenderRig operator = new SenderRig("Parent", true);

        command.onCommand(operator.sender(), null, "bb", new String[] {"stop"});
        command.onCommand(operator.sender(), null, "bb", new String[] {"menu"});

        assertEquals(1, round.stops);
        assertEquals(
                "Try /bb start, /bb stop, or /bb results.",
                operator.messages().getLast());
    }

    @Test
    void resultsDelegatesWithoutOperatorPermission() {
        FakeResults results = new FakeResults();
        BattleCommand command = new BattleCommand(settings(false), new FakeRound(), results);
        SenderRig visitor = new SenderRig("Visitor", false);

        command.onCommand(visitor.sender(), null, "battle", new String[] {"results"});

        assertEquals(1, results.latestRequests);
    }

    private static BattleSettings settings(boolean allowAnyStart) {
        return new BattleSettings(
                new ArenaSettings(33, 30, 64, 8, 4_000),
                new PhaseTimings(30, 60, 1_200, 900),
                List.of("A dragon treehouse"),
                List.of("BuilderKid"),
                allowAnyStart);
    }

    private static final class FakeRound implements BattleRound {
        private int starts;
        private int stops;

        @Override
        public RoundPhase phase() {
            return RoundPhase.IDLE;
        }

        @Override
        public void start(CommandSender sender) {
            starts++;
        }

        @Override
        public void stop(CommandSender sender) {
            stops++;
        }
    }

    private static final class FakeResults implements BattleResultCommands {
        private int latestRequests;

        @Override
        public void showLatest(CommandSender sender) {
            latestRequests++;
        }

        @Override
        public void announceRound(String roundId, CommandSender sender) {}
    }

    private record SenderRig(CommandSender sender, List<String> messages) {
        private SenderRig(String name, boolean operator) {
            this(messages(name, operator));
        }

        private SenderRig(SenderParts parts) {
            this(parts.sender(), parts.messages());
        }

        private static SenderParts messages(String name, boolean operator) {
            List<String> messages = new ArrayList<>();
            CommandSender sender =
                    proxy(
                            CommandSender.class,
                            (ignored, method, arguments) ->
                                    switch (method.getName()) {
                                        case "getName" -> name;
                                        case "isOp" -> operator;
                                        case "sendMessage" -> {
                                            messages.add(String.valueOf(arguments[0]));
                                            yield null;
                                        }
                                        default -> defaultValue(method.getReturnType());
                                    });
            return new SenderParts(sender, messages);
        }
    }

    private record SenderParts(CommandSender sender, List<String> messages) {}

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T)
                Proxy.newProxyInstance(
                        type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }
}

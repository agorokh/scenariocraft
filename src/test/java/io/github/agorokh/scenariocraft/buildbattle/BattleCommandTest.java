package io.github.agorokh.scenariocraft.buildbattle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.junit.jupiter.api.Test;

class BattleCommandTest {
    @Test
    void allowedPlayerCanStartButCannotStop() {
        FakeRound round = new FakeRound();
        BattleCommand command = new BattleCommand(settings(true), round);
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
        BattleCommand command = new BattleCommand(settings(false), round);
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
        BattleCommand command = new BattleCommand(settings(true), round);
        SenderRig operator = new SenderRig("Parent", true);

        command.onCommand(operator.sender(), null, "bb", new String[] {"stop"});
        command.onCommand(operator.sender(), null, "bb", new String[] {"menu"});

        assertEquals(1, round.stops);
        assertEquals(
                "Try /bb start, /bb stop, or /bb results.",
                operator.messages().getLast());
    }

    @Test
    void resultsReplaysForPlayersAndConsoleAnnouncementHasASeparateGuardedPath() {
        FakeRound round = new FakeRound();
        FakeResults results = new FakeResults();
        BattleCommand command = new BattleCommand(settings(true), round, results);
        SenderRig player = new SenderRig("BuilderKid", false, true);
        SenderRig commandBlock = new SenderRig("@", true, BlockCommandSender.class);
        SenderRig console = new SenderRig("CONSOLE", true, ConsoleCommandSender.class);
        SenderRig rcon = new SenderRig("Rcon", true, RemoteConsoleCommandSender.class);

        command.onCommand(player.sender(), null, "battle", new String[] {"results"});
        command.onCommand(player.sender(), null, "battle", new String[] {"announce-results"});
        command.onCommand(
                commandBlock.sender(), null, "battle", new String[] {"announce-results"});
        command.onCommand(console.sender(), null, "battle", new String[] {"announce-results"});
        command.onCommand(rcon.sender(), null, "battle", new String[] {"announce-results"});

        assertEquals(1, results.replays);
        assertEquals(2, results.announcements);
        assertEquals(
                "Only the server console can announce judge results.",
                player.messages().getLast());
        assertEquals(
                "Only the server console can announce judge results.",
                commandBlock.messages().getLast());
    }

    @Test
    void resultsBeforeTheFirstVerdictUsesFriendlyNoResultsMessage() {
        BattleCommand command = new BattleCommand(settings(true), new FakeRound());
        SenderRig sender = new SenderRig("BuilderKid", false);

        command.onCommand(sender.sender(), null, "battle", new String[] {"results"});

        assertEquals(
                "No judge results yet — check back after the reveal!",
                sender.messages().getLast());
    }

    private static BattleSettings settings(boolean allowAnyStart) {
        return new BattleSettings(
                new ArenaSettings(33, 30, 64, 8, 4_000),
                new PhaseTimings(30, 60, 1_200, 900),
                List.of("A dragon treehouse"),
                List.of("BuilderKid"),
                allowAnyStart,
                20);
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

    private static final class FakeResults implements BattleResultsReporter {
        private int replays;
        private int announcements;

        @Override
        public void replayLatest(CommandSender sender) {
            replays++;
        }

        @Override
        public void announceLatest(CommandSender sender) {
            announcements++;
        }
    }

    private record SenderRig(CommandSender sender, List<String> messages) {
        private SenderRig(String name, boolean operator) {
            this(name, operator, false);
        }

        private SenderRig(String name, boolean operator, boolean player) {
            this(messages(
                    name,
                    operator,
                    player ? org.bukkit.entity.Player.class : CommandSender.class));
        }

        private SenderRig(
                String name, boolean operator, Class<? extends CommandSender> senderType) {
            this(messages(name, operator, senderType));
        }

        private SenderRig(SenderParts parts) {
            this(parts.sender(), parts.messages());
        }

        private static SenderParts messages(
                String name, boolean operator, Class<? extends CommandSender> senderType) {
            List<String> messages = new ArrayList<>();
            java.lang.reflect.InvocationHandler handler =
                    (ignored, method, arguments) ->
                            switch (method.getName()) {
                                case "getName" -> name;
                                case "isOp" -> operator;
                                case "sendMessage" -> {
                                    messages.add(String.valueOf(arguments[0]));
                                    yield null;
                                }
                                default -> defaultValue(method.getReturnType());
                            };
            CommandSender sender = proxy(senderType, handler);
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

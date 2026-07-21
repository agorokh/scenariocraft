package io.github.agorokh.scenariocraft.buildbattle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResultAnnouncementServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void revealPollReadsOffThreadAndAnnouncesOnceOnServerThread() throws Exception {
        Path round = temporaryDirectory.resolve("rounds/round-20260721-193000");
        Files.createDirectories(round);
        Files.writeString(
                round.resolve("results.txt"), BattleResultsReaderTest.winningResults());
        List<String> playerChat = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        AtomicInteger particles = new AtomicInteger();
        AtomicInteger cancellations = new AtomicInteger();
        List<Runnable> asynchronousTasks = new ArrayList<>();
        List<Runnable> serverTasks = new ArrayList<>();
        World world = proxy(
                World.class,
                (ignored, method, arguments) -> {
                    if (method.getName().equals("spawnParticle")) {
                        particles.incrementAndGet();
                    }
                    return defaultValue(method.getReturnType());
                });
        Player player = proxy(
                Player.class,
                (ignored, method, arguments) -> {
                    if (method.getName().equals("sendMessage")) {
                        playerChat.add(String.valueOf(arguments[0]));
                    }
                    if (method.getName().equals("sendTitle")) {
                        titles.add(String.valueOf(arguments[0]));
                    }
                    return defaultValue(method.getReturnType());
                });
        BukkitTask task = proxy(
                BukkitTask.class,
                (ignored, method, arguments) -> {
                    if (method.getName().equals("cancel")) {
                        cancellations.incrementAndGet();
                    }
                    return defaultValue(method.getReturnType());
                });
        BukkitScheduler scheduler = proxy(
                BukkitScheduler.class,
                (ignored, method, arguments) -> {
                    if (method.getName().equals("runTaskTimer")) {
                        return task;
                    }
                    if (method.getName().equals("runTaskAsynchronously")) {
                        asynchronousTasks.add((Runnable) arguments[1]);
                        return task;
                    }
                    if (method.getName().equals("runTask")) {
                        serverTasks.add((Runnable) arguments[1]);
                        return task;
                    }
                    return defaultValue(method.getReturnType());
                });
        Server server = proxy(
                Server.class,
                (ignored, method, arguments) ->
                        switch (method.getName()) {
                            case "getScheduler" -> scheduler;
                            case "getOnlinePlayers" -> Set.of(player);
                            default -> defaultValue(method.getReturnType());
                        });
        Plugin plugin = proxy(
                Plugin.class,
                (ignored, method, arguments) ->
                        switch (method.getName()) {
                            case "getServer" -> server;
                            case "getLogger" -> Logger.getAnonymousLogger();
                            default -> defaultValue(method.getReturnType());
                        });
        BattleRound reveal = new FixedPhaseRound(RoundPhase.REVEAL);
        ResultAnnouncementService service = new ResultAnnouncementService(
                plugin,
                reveal,
                new BattleResultsReader(temporaryDirectory.resolve("rounds")),
                ignored -> new Location(world, 1.5, 80, 2.5),
                20L);

        service.poll();
        assertTrue(playerChat.isEmpty());
        asynchronousTasks.removeFirst().run();
        assertTrue(playerChat.isEmpty());
        serverTasks.removeFirst().run();
        int firstChatCount = playerChat.size();
        service.poll();
        asynchronousTasks.removeFirst().run();
        serverTasks.removeFirst().run();
        service.close();

        assertTrue(firstChatCount >= 4);
        assertEquals(firstChatCount, playerChat.size());
        assertEquals(List.of("§6Winner: Alex!"), titles);
        assertEquals(1, particles.get());
        assertEquals(1, cancellations.get());
    }

    @Test
    void replayWithoutAResultSendsTheFriendlyMessage() {
        List<String> messages = new ArrayList<>();
        List<Runnable> asynchronousTasks = new ArrayList<>();
        List<Runnable> serverTasks = new ArrayList<>();
        BukkitTask task = proxy(BukkitTask.class, (ignored, method, arguments) -> null);
        BukkitScheduler scheduler = proxy(
                BukkitScheduler.class,
                (ignored, method, arguments) -> {
                    if (method.getName().equals("runTaskTimer")) {
                        return task;
                    }
                    if (method.getName().equals("runTaskAsynchronously")) {
                        asynchronousTasks.add((Runnable) arguments[1]);
                        return task;
                    }
                    if (method.getName().equals("runTask")) {
                        serverTasks.add((Runnable) arguments[1]);
                        return task;
                    }
                    return defaultValue(method.getReturnType());
                });
        Server server = proxy(
                Server.class,
                (ignored, method, arguments) ->
                        method.getName().equals("getScheduler") ? scheduler : null);
        Plugin plugin = proxy(
                Plugin.class,
                (ignored, method, arguments) ->
                        switch (method.getName()) {
                            case "getServer" -> server;
                            case "getLogger" -> Logger.getAnonymousLogger();
                            default -> defaultValue(method.getReturnType());
                        });
        CommandSender sender = proxy(
                CommandSender.class,
                (ignored, method, arguments) -> {
                    if (method.getName().equals("sendMessage")) {
                        messages.add(String.valueOf(arguments[0]));
                    }
                    return defaultValue(method.getReturnType());
                });
        ResultAnnouncementService service = new ResultAnnouncementService(
                plugin,
                new FixedPhaseRound(RoundPhase.IDLE),
                new BattleResultsReader(temporaryDirectory.resolve("missing")),
                ignored -> null,
                20L);

        service.replayLatest(sender);
        assertTrue(messages.isEmpty());
        asynchronousTasks.removeFirst().run();
        assertTrue(messages.isEmpty());
        serverTasks.removeFirst().run();

        assertEquals(
                List.of("No judge results yet — check back after the reveal!"), messages);
    }

    private record FixedPhaseRound(RoundPhase phase) implements BattleRound {
        @Override
        public void start(CommandSender sender) {}

        @Override
        public void stop(CommandSender sender) {}
    }

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

package io.github.agorokh.scenariocraft.buildbattle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BattleResultServiceTest {
    @TempDir Path temporaryDirectory;

    @Test
    void noResultsPathIsFriendly() {
        ServiceRig rig = new ServiceRig(temporaryDirectory.resolve("rounds"), RoundPhase.IDLE);
        List<String> messages = new ArrayList<>();
        CommandSender sender = sender(messages);

        rig.service.showLatest(sender);

        assertEquals("No judging results are ready yet — check back after the reveal!", messages.getLast());
        rig.service.close();
    }

    @Test
    void revealPollingAnnouncesOneResultOnlyOnce() throws Exception {
        Path rounds = temporaryDirectory.resolve("rounds");
        Path round = Files.createDirectories(rounds.resolve("round-20260721-193000"));
        Files.writeString(round.resolve("results.txt"), BattleResultRepositoryTest.validResult("round-20260721-193000"));
        ServiceRig rig = new ServiceRig(rounds, RoundPhase.REVEAL);

        rig.poll.get().run();
        rig.poll.get().run();

        assertEquals(1, rig.titles.size());
        assertTrue(rig.messages.stream().anyMatch(message -> message.equals("Winner: Alex!")));
        rig.service.close();
    }

    @Test
    void correctedResultForTheSameRoundCanReplaceAnEarlyAnnouncement() throws Exception {
        Path rounds = temporaryDirectory.resolve("replacement-rounds");
        Path round = Files.createDirectories(rounds.resolve("round-20260721-193000"));
        Path resultPath = round.resolve("results.txt");
        Files.writeString(
                resultPath,
                BattleResultRepositoryTest.validResult("round-20260721-193000"));
        ServiceRig rig = new ServiceRig(rounds, RoundPhase.REVEAL);

        rig.poll.get().run();
        Files.writeString(
                resultPath,
                BattleResultRepositoryTest.validResult("round-20260721-193000")
                        .replace("Alex", "Casey"));
        for (int tick = 0; tick <= 40; tick++) {
            rig.poll.get().run();
        }

        assertEquals(2, rig.titles.size());
        rig.service.close();
    }

    @Test
    void revealPollingDoesNotReplayThePreviousRoundsLatestResult() throws Exception {
        Path rounds = temporaryDirectory.resolve("rounds");
        Path previous = Files.createDirectories(rounds.resolve("round-20260721-190000"));
        Files.writeString(
                previous.resolve("results.txt"),
                BattleResultRepositoryTest.validResult("round-20260721-190000"));
        ServiceRig rig =
                new ServiceRig(
                        rounds,
                        RoundPhase.REVEAL,
                        "round-20260721-193000");

        rig.poll.get().run();
        assertTrue(rig.titles.isEmpty());

        Path active = Files.createDirectories(rounds.resolve("round-20260721-193000"));
        Files.writeString(
                active.resolve("results.txt"),
                BattleResultRepositoryTest.validResult("round-20260721-193000"));
        for (int tick = 0; tick <= 40; tick++) {
            rig.poll.get().run();
        }

        assertEquals(1, rig.titles.size());
        rig.service.close();
    }

    @Test
    void delayedRconAnnouncementCannotPresentAnInactiveRound() throws Exception {
        Path rounds = temporaryDirectory.resolve("rcon-rounds");
        Path previous = Files.createDirectories(rounds.resolve("round-20260721-190000"));
        Files.writeString(
                previous.resolve("results.txt"),
                BattleResultRepositoryTest.validResult("round-20260721-190000"));
        ServiceRig rig =
                new ServiceRig(
                        rounds,
                        RoundPhase.REVEAL,
                        "round-20260721-193000");
        List<String> consoleMessages = new ArrayList<>();

        rig.service.announceRound("round-20260721-190000", sender(consoleMessages));

        assertTrue(rig.titles.isEmpty());
        assertTrue(consoleMessages.getLast().contains("no longer the active reveal"));
        rig.service.close();
    }

    @Test
    void uncheckedReadFailureClearsTheGateForTheNextRequest() {
        AtomicInteger reads = new AtomicInteger();
        BattleResultReader reader =
                new BattleResultReader() {
                    @Override
                    public Optional<BattleResult> latest() {
                        if (reads.getAndIncrement() == 0) {
                            throw new UncheckedIOException(new IOException("directory stream failed"));
                        }
                        return Optional.empty();
                    }

                    @Override
                    public Optional<BattleResult> round(String roundId) {
                        return Optional.empty();
                    }
                };
        ServiceRig rig = new ServiceRig(reader, RoundPhase.IDLE, "round-20260721-193000");
        List<String> senderMessages = new ArrayList<>();
        CommandSender sender = sender(senderMessages);

        rig.service.showLatest(sender);
        rig.service.showLatest(sender);

        assertEquals(2, reads.get());
        assertTrue(senderMessages.getFirst().contains("could not be read safely"));
        assertEquals(
                "No judging results are ready yet — check back after the reveal!",
                senderMessages.getLast());
        rig.service.close();
    }

    @Test
    void manualReplayReadDoesNotSuppressActiveRevealPolling() {
        BattleResult active =
                new BattleResultParser()
                        .parse(BattleResultRepositoryTest.validResult("round-20260721-193000"));
        BattleResultReader reader =
                new BattleResultReader() {
                    @Override
                    public Optional<BattleResult> latest() {
                        return Optional.empty();
                    }

                    @Override
                    public Optional<BattleResult> round(String roundId) {
                        return Optional.of(active);
                    }
                };
        ServiceRig rig =
                new ServiceRig(
                        reader,
                        RoundPhase.REVEAL,
                        "round-20260721-193000",
                        false);

        rig.service.showLatest(sender(new ArrayList<>()));
        rig.poll.get().run();

        assertEquals(2, rig.asyncReads.size());
        rig.asyncReads.get(1).run();
        assertEquals(1, rig.titles.size());
        rig.service.close();
    }

    private static final class ServiceRig {
        private final AtomicReference<Runnable> poll = new AtomicReference<>();
        private final List<String> messages = new ArrayList<>();
        private final List<String> titles = new ArrayList<>();
        private final List<Runnable> asyncReads = new ArrayList<>();
        private final BattleResultService service;

        private ServiceRig(Path rounds, RoundPhase phase) {
            this(rounds, phase, "round-20260721-193000");
        }

        private ServiceRig(Path rounds, RoundPhase phase, String resultRoundId) {
            this(new BattleResultRepository(rounds), phase, resultRoundId);
        }

        private ServiceRig(BattleResultReader reader, RoundPhase phase, String resultRoundId) {
            this(reader, phase, resultRoundId, true);
        }

        private ServiceRig(
                BattleResultReader reader,
                RoundPhase phase,
                String resultRoundId,
                boolean runAsyncImmediately) {
            BukkitTask task = proxy(BukkitTask.class, (ignored, method, arguments) -> null);
            BukkitScheduler scheduler =
                    proxy(
                            BukkitScheduler.class,
                            (ignored, method, arguments) -> {
                                switch (method.getName()) {
                                    case "runTaskTimer" -> poll.set((Runnable) arguments[1]);
                                    case "runTaskAsynchronously" -> {
                                        Runnable read = (Runnable) arguments[1];
                                        if (runAsyncImmediately) {
                                            read.run();
                                        } else {
                                            asyncReads.add(read);
                                        }
                                    }
                                    case "runTask", "runTaskLater" -> ((Runnable) arguments[1]).run();
                                    default -> {}
                                }
                                return task;
                            });
            Player player =
                    proxy(
                            Player.class,
                            (ignored, method, arguments) -> {
                                if (method.getName().equals("sendMessage")) {
                                    messages.add(String.valueOf(arguments[0]));
                                } else if (method.getName().equals("sendTitle")) {
                                    titles.add(String.valueOf(arguments[0]));
                                }
                                return defaultValue(method.getReturnType());
                            });
            Server server =
                    proxy(
                            Server.class,
                            (ignored, method, arguments) ->
                                    switch (method.getName()) {
                                        case "getScheduler" -> scheduler;
                                        case "getOnlinePlayers" -> List.of(player);
                                        default -> defaultValue(method.getReturnType());
                                    });
            Plugin plugin =
                    proxy(
                            Plugin.class,
                            (ignored, method, arguments) ->
                                    method.getName().equals("getServer")
                                            ? server
                                            : defaultValue(method.getReturnType()));
            service =
                    new BattleResultService(
                            plugin,
                            reader,
                            new ResultAnnouncementSettings(2, 3, 10),
                            () -> phase,
                            () -> Optional.of(resultRoundId),
                            ignored -> Optional.empty(),
                            Logger.getAnonymousLogger());
        }
    }

    private static CommandSender sender(List<String> messages) {
        return proxy(
                CommandSender.class,
                (ignored, method, arguments) -> {
                    if (method.getName().equals("sendMessage")) {
                        messages.add(String.valueOf(arguments[0]));
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
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

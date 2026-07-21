package io.github.agorokh.scenariocraft.buildbattle;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Pure ownership, phase, and boundary policy for mutations that can reach an active arena. */
final class ActiveArenaMutationPolicy {
    enum Family {
        BLOCK_BREAK(Rule.OWNER_TARGETS),
        BLOCK_PLACE(Rule.OWNER_TARGETS),
        SIGN_CHANGE(Rule.OWNER_TARGETS),
        BUCKET_EMPTY(Rule.OWNER_TARGETS),
        BUCKET_FILL(Rule.OWNER_TARGETS),
        PLAYER_IGNITE(Rule.OWNER_TARGETS),
        PLAYER_INTERACTION(Rule.OWNER_TARGETS),
        PLAYER_ENTITY_INTERACTION(Rule.OWNER_TARGETS),
        PLAYER_DECORATIVE_ENTITY_DAMAGE(Rule.OWNER_TARGETS),
        HANGING_PLACE(Rule.OWNER_OR_DENY),
        HANGING_BREAK(Rule.OWNER_OR_DENY),
        DECORATIVE_ENTITY_PLACE(Rule.OWNER_OR_DENY),
        ENTITY_BLOCK_FORM(Rule.OWNER_OR_DENY),
        CAULDRON_LEVEL_CHANGE(Rule.OWNER_OR_DENY),
        FERTILIZE(Rule.OWNER_OR_SAME_PLOT),
        STRUCTURE_GROW(Rule.OWNER_OR_SAME_PLOT),
        SPONGE_ABSORB(Rule.SAME_PLOT),
        FLUID_FLOW(Rule.SAME_PLOT),
        FALLING_BLOCK(Rule.SAME_PLOT),
        ENTITY_BLOCK_CHANGE(Rule.DENY),
        ENTITY_INTERACTION(Rule.DENY),
        ENTITY_CREATE_PORTAL(Rule.DENY),
        PISTON_EXTEND(Rule.SAME_PLOT),
        PISTON_RETRACT(Rule.SAME_PLOT),
        BLOCK_GROW(Rule.EDITABLE_TARGETS),
        BLOCK_FORM(Rule.EDITABLE_TARGETS),
        FLUID_LEVEL_CHANGE(Rule.EDITABLE_TARGETS),
        MOISTURE_CHANGE(Rule.EDITABLE_TARGETS),
        BLOCK_DISPENSE(Rule.DENY),
        BLOCK_SPREAD(Rule.DENY),
        BLOCK_BURN(Rule.DENY),
        ENVIRONMENTAL_IGNITE(Rule.DENY),
        TNT_PRIME(Rule.DENY),
        BLOCK_EXPLOSION(Rule.DENY),
        ENTITY_EXPLOSION(Rule.DENY),
        LEAVES_DECAY(Rule.DENY),
        BLOCK_FADE(Rule.DENY),
        BLOCK_PHYSICS(Rule.ALLOW);

        private final Rule rule;

        Family(Rule rule) {
            this.rule = rule;
        }

        Rule rule() {
            return rule;
        }
    }

    enum Decision {
        ALLOW,
        DENY
    }

    record Position(Object world, int x, int y, int z) {
        Position {
            Objects.requireNonNull(world, "world");
        }
    }

    private enum Rule {
        OWNER_TARGETS,
        OWNER_OR_DENY,
        OWNER_OR_SAME_PLOT,
        SAME_PLOT,
        EDITABLE_TARGETS,
        DENY,
        ALLOW
    }

    private final Object arenaWorld;
    private final Supplier<RoundPhase> phase;
    private final BooleanSupplier arenaProtected;
    private final Function<UUID, PlotBoundary> assignedBoundary;
    private final Supplier<? extends Collection<PlotBoundary>> assignedBoundaries;
    private final Predicate<UUID> strandedPlayer;

    ActiveArenaMutationPolicy(
            Object arenaWorld,
            Supplier<RoundPhase> phase,
            BooleanSupplier arenaProtected,
            Function<UUID, PlotBoundary> assignedBoundary,
            Supplier<? extends Collection<PlotBoundary>> assignedBoundaries,
            Predicate<UUID> strandedPlayer) {
        this.arenaWorld = Objects.requireNonNull(arenaWorld, "arenaWorld");
        this.phase = Objects.requireNonNull(phase, "phase");
        this.arenaProtected = Objects.requireNonNull(arenaProtected, "arenaProtected");
        this.assignedBoundary = Objects.requireNonNull(assignedBoundary, "assignedBoundary");
        this.assignedBoundaries =
                Objects.requireNonNull(assignedBoundaries, "assignedBoundaries");
        this.strandedPlayer = Objects.requireNonNull(strandedPlayer, "strandedPlayer");
    }

    Decision decide(
            Family family, UUID actorId, Position source, Collection<Position> destinations) {
        Objects.requireNonNull(family, "family");
        List<Position> targets = List.copyOf(destinations);
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("at least one mutation destination is required");
        }
        if (family.rule() == Rule.ALLOW) {
            return Decision.ALLOW;
        }

        PlotBoundary actorBoundary = actorId == null ? null : assignedBoundary.apply(actorId);
        boolean touchesArena = targets.stream().anyMatch(this::isArenaPosition);
        boolean assignedActor = actorBoundary != null;
        boolean strandedActor = actorId != null && strandedPlayer.test(actorId);
        if (!touchesArena && !assignedActor && !strandedActor) {
            return Decision.ALLOW;
        }
        if (touchesArena
                && !arenaProtected.getAsBoolean()
                && !assignedActor
                && !strandedActor) {
            return Decision.ALLOW;
        }

        boolean allowed =
                switch (family.rule()) {
                    case OWNER_TARGETS ->
                            actorBoundary != null && allInside(actorBoundary, targets);
                    case OWNER_OR_DENY ->
                            actorBoundary != null && allInside(actorBoundary, targets);
                    case OWNER_OR_SAME_PLOT ->
                            actorId == null
                                    ? sameEditablePlot(source, targets)
                                    : actorBoundary != null && allInside(actorBoundary, targets);
                    case SAME_PLOT -> sameEditablePlot(source, targets);
                    case EDITABLE_TARGETS -> insideAnyEditablePlot(targets);
                    case DENY -> false;
                    case ALLOW -> true;
                };
        return allowed ? Decision.ALLOW : Decision.DENY;
    }

    boolean allows(
            Family family, UUID actorId, Position source, Collection<Position> destinations) {
        return decide(family, actorId, source, destinations) == Decision.ALLOW;
    }

    private boolean sameEditablePlot(Position source, List<Position> targets) {
        if (source == null || phase.get() != RoundPhase.BUILDING) {
            return false;
        }
        return assignedBoundaries.get().stream()
                .anyMatch(
                        boundary ->
                                contains(boundary, source)
                                        && targets.stream()
                                                .allMatch(target -> contains(boundary, target)));
    }

    private boolean insideAnyEditablePlot(List<Position> targets) {
        if (phase.get() != RoundPhase.BUILDING) {
            return false;
        }
        return assignedBoundaries.get().stream()
                .anyMatch(
                        boundary ->
                                targets.stream().allMatch(target -> contains(boundary, target)));
    }

    private boolean allInside(PlotBoundary boundary, List<Position> targets) {
        RoundPhase currentPhase = phase.get();
        return targets.stream()
                .allMatch(
                        target ->
                                isArenaPosition(target)
                                        && PlotEditPolicy.mayEdit(
                                                currentPhase,
                                                boundary,
                                                target.x(),
                                                target.y(),
                                                target.z()));
    }

    private boolean contains(PlotBoundary boundary, Position position) {
        return isArenaPosition(position)
                && boundary.containsEditableBlock(
                        position.x(), position.y(), position.z());
    }

    private boolean isArenaPosition(Position position) {
        return position.world() == arenaWorld;
    }
}

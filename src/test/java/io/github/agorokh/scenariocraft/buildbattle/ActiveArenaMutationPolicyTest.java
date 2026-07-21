package io.github.agorokh.scenariocraft.buildbattle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.agorokh.scenariocraft.buildbattle.ActiveArenaMutationPolicy.Decision;
import io.github.agorokh.scenariocraft.buildbattle.ActiveArenaMutationPolicy.Family;
import io.github.agorokh.scenariocraft.buildbattle.ActiveArenaMutationPolicy.Position;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

final class ActiveArenaMutationPolicyTest {
    private static final Object ARENA = new Object();
    private static final Object OTHER_WORLD = new Object();
    private static final UUID OWNER =
            UUID.fromString("21b6e274-8cbd-49d8-a168-f46212468110");
    private static final UUID NON_OWNER =
            UUID.fromString("ed87d371-3e68-47e3-a331-ce47a9ba3bb0");
    private static final PlotBoundary PLOT =
            new PlotBoundary(new PlotBounds(0, 4, 0, 4), 1, 8);

    @ParameterizedTest(name = "{0} is denied while the protected arena is not building")
    @EnumSource(value = Family.class, names = "BLOCK_PHYSICS", mode = EnumSource.Mode.EXCLUDE)
    void everyDenyCapableFamilyHasARegressionCase(Family family) {
        Decision decision =
                policy(RoundPhase.PREPARING).decide(
                        family,
                        OWNER,
                        position(1, 2, 1),
                        List.of(position(1, 2, 1)));

        assertEquals(Decision.DENY, decision);
    }

    @ParameterizedTest(name = "{0} denies a source/destination boundary crossing")
    @MethodSource("boundarySensitiveFamilies")
    void boundarySensitiveFamiliesDenyCrossPlotMutation(Family family) {
        Decision decision =
                policy(RoundPhase.BUILDING).decide(
                        family,
                        OWNER,
                        position(1, 2, 1),
                        List.of(position(5, 2, 1)));

        assertEquals(Decision.DENY, decision);
    }

    @ParameterizedTest(name = "{0} allows its representative in-plot mutation")
    @MethodSource("representativeAllowFamilies")
    void legitimateInPlotMutationsRemainAllowed(Family family) {
        Decision decision =
                policy(RoundPhase.BUILDING).decide(
                        family,
                        OWNER,
                        position(1, 2, 1),
                        List.of(position(2, 2, 1)));

        assertEquals(Decision.ALLOW, decision);
    }

    @ParameterizedTest(name = "{0} rejects a non-owner in an assigned plot")
    @MethodSource("actorOwnedFamilies")
    void actorOwnedFamiliesDenyNonOwners(Family family) {
        Decision decision =
                policy(RoundPhase.BUILDING).decide(
                        family,
                        NON_OWNER,
                        position(1, 2, 1),
                        List.of(position(1, 2, 1)));

        assertEquals(Decision.DENY, decision);
    }

    @Test
    void normalBlockPhysicsIsNeverCancelledWholesale() {
        assertEquals(
                Decision.ALLOW,
                policy(RoundPhase.PREPARING).decide(
                        Family.BLOCK_PHYSICS,
                        null,
                        position(5, 2, 1),
                        List.of(position(1, 2, 1))));
    }

    @Test
    void unrelatedWorldMutationRemainsAllowedForAnUnassignedPlayer() {
        Position elsewhere = new Position(OTHER_WORLD, 1, 2, 1);

        assertEquals(
                Decision.ALLOW,
                policy(RoundPhase.BUILDING).decide(
                        Family.BLOCK_BREAK,
                        NON_OWNER,
                        elsewhere,
                        List.of(elsewhere)));
    }

    private static Stream<Family> boundarySensitiveFamilies() {
        return Stream.of(
                Family.BLOCK_BREAK,
                Family.BLOCK_PLACE,
                Family.SIGN_CHANGE,
                Family.BUCKET_EMPTY,
                Family.BUCKET_FILL,
                Family.PLAYER_IGNITE,
                Family.PLAYER_INTERACTION,
                Family.PLAYER_ENTITY_INTERACTION,
                Family.PLAYER_DECORATIVE_ENTITY_DAMAGE,
                Family.HANGING_PLACE,
                Family.HANGING_BREAK,
                Family.DECORATIVE_ENTITY_PLACE,
                Family.ENTITY_BLOCK_FORM,
                Family.CAULDRON_LEVEL_CHANGE,
                Family.FERTILIZE,
                Family.STRUCTURE_GROW,
                Family.SPONGE_ABSORB,
                Family.FLUID_FLOW,
                Family.FALLING_BLOCK,
                Family.PISTON_EXTEND,
                Family.PISTON_RETRACT,
                Family.BLOCK_GROW,
                Family.BLOCK_FORM,
                Family.FLUID_LEVEL_CHANGE,
                Family.MOISTURE_CHANGE);
    }

    private static Stream<Family> representativeAllowFamilies() {
        return Stream.of(
                Family.BLOCK_BREAK,
                Family.BLOCK_PLACE,
                Family.SIGN_CHANGE,
                Family.BUCKET_EMPTY,
                Family.BUCKET_FILL,
                Family.PLAYER_IGNITE,
                Family.PLAYER_INTERACTION,
                Family.PLAYER_ENTITY_INTERACTION,
                Family.PLAYER_DECORATIVE_ENTITY_DAMAGE,
                Family.HANGING_PLACE,
                Family.HANGING_BREAK,
                Family.DECORATIVE_ENTITY_PLACE,
                Family.ENTITY_BLOCK_FORM,
                Family.CAULDRON_LEVEL_CHANGE,
                Family.FERTILIZE,
                Family.STRUCTURE_GROW,
                Family.SPONGE_ABSORB,
                Family.FLUID_FLOW,
                Family.FALLING_BLOCK,
                Family.PISTON_EXTEND,
                Family.PISTON_RETRACT,
                Family.BLOCK_GROW,
                Family.BLOCK_FORM,
                Family.FLUID_LEVEL_CHANGE,
                Family.MOISTURE_CHANGE,
                Family.BLOCK_PHYSICS);
    }

    private static Stream<Family> actorOwnedFamilies() {
        return Stream.of(
                Family.BLOCK_BREAK,
                Family.BLOCK_PLACE,
                Family.SIGN_CHANGE,
                Family.BUCKET_EMPTY,
                Family.BUCKET_FILL,
                Family.PLAYER_IGNITE,
                Family.PLAYER_INTERACTION,
                Family.PLAYER_ENTITY_INTERACTION,
                Family.PLAYER_DECORATIVE_ENTITY_DAMAGE,
                Family.HANGING_PLACE,
                Family.HANGING_BREAK,
                Family.DECORATIVE_ENTITY_PLACE,
                Family.ENTITY_BLOCK_FORM,
                Family.CAULDRON_LEVEL_CHANGE,
                Family.FERTILIZE,
                Family.STRUCTURE_GROW);
    }

    private static Position position(int x, int y, int z) {
        return new Position(ARENA, x, y, z);
    }

    private static ActiveArenaMutationPolicy policy(RoundPhase phase) {
        return new ActiveArenaMutationPolicy(
                ARENA,
                () -> phase,
                () -> true,
                playerId -> Map.of(OWNER, PLOT).get(playerId),
                () -> List.of(PLOT),
                Set.of()::contains);
    }
}

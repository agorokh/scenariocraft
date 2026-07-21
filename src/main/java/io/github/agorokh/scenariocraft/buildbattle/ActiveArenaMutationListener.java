package io.github.agorokh.scenariocraft.buildbattle;

import io.github.agorokh.scenariocraft.buildbattle.ActiveArenaMutationPolicy.Family;
import io.github.agorokh.scenariocraft.buildbattle.ActiveArenaMutationPolicy.Position;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.block.FluidLevelChangeEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.MoistureChangeEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityCreatePortalEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.StructureGrowEvent;

/** Maps Paper's block and build-backed entity event surface onto the active-arena policy. */
final class ActiveArenaMutationListener implements Listener {
    private final ActiveArenaMutationPolicy policy;

    ActiveArenaMutationListener(ActiveArenaMutationPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onContestantBlockBreak(BlockBreakEvent event) {
        cancelUnlessAllowed(
                event,
                Family.BLOCK_BREAK,
                event.getPlayer(),
                event.getBlock(),
                List.of(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onContestantBlockPlace(BlockPlaceEvent event) {
        Collection<Block> targets =
                event instanceof BlockMultiPlaceEvent multiPlace
                        ? multiPlace.getReplacedBlockStates().stream()
                                .map(BlockState::getBlock)
                                .toList()
                        : List.of(event.getBlockPlaced());
        if (!allows(Family.BLOCK_PLACE, event.getPlayer(), event.getBlockAgainst(), targets)) {
            event.setCancelled(true);
            event.setBuild(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaSignChange(SignChangeEvent event) {
        cancelUnlessAllowed(
                event,
                Family.SIGN_CHANGE,
                event.getPlayer(),
                event.getBlock(),
                List.of(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaBlockDispense(BlockDispenseEvent event) {
        cancelUnlessAllowed(
                event,
                Family.BLOCK_DISPENSE,
                null,
                event.getBlock(),
                List.of(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaBlockSpread(BlockSpreadEvent event) {
        cancelUnlessAllowed(
                event,
                Family.BLOCK_SPREAD,
                null,
                event.getSource(),
                List.of(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaBlockBurn(BlockBurnEvent event) {
        cancelUnlessAllowed(
                event,
                Family.BLOCK_BURN,
                null,
                event.getIgnitingBlock(),
                List.of(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaBlockIgnite(BlockIgniteEvent event) {
        Player player = event.getPlayer();
        cancelUnlessAllowed(
                event,
                player == null ? Family.ENVIRONMENTAL_IGNITE : Family.PLAYER_IGNITE,
                player,
                event.getIgnitingBlock(),
                List.of(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaBlockFertilize(BlockFertilizeEvent event) {
        cancelUnlessAllowed(
                event,
                Family.FERTILIZE,
                event.getPlayer(),
                event.getBlock(),
                blocks(event.getBlocks()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaStructureGrow(StructureGrowEvent event) {
        cancelUnlessAllowed(
                event,
                Family.STRUCTURE_GROW,
                event.getPlayer(),
                event.getLocation(),
                blocks(event.getBlocks()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaSpongeAbsorb(SpongeAbsorbEvent event) {
        cancelUnlessAllowed(
                event,
                Family.SPONGE_ABSORB,
                null,
                event.getBlock(),
                blocks(event.getBlocks()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaBlockGrow(BlockGrowEvent event) {
        cancelUnlessAllowed(
                event,
                Family.BLOCK_GROW,
                null,
                event.getBlock(),
                List.of(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaFluidLevelChange(FluidLevelChangeEvent event) {
        cancelUnlessAllowed(
                event,
                Family.FLUID_LEVEL_CHANGE,
                null,
                event.getBlock(),
                List.of(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaMoistureChange(MoistureChangeEvent event) {
        cancelUnlessAllowed(
                event,
                Family.MOISTURE_CHANGE,
                null,
                event.getBlock(),
                List.of(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaCauldronLevelChange(CauldronLevelChangeEvent event) {
        Player player = responsiblePlayer(event.getEntity());
        cancelUnlessAllowed(
                event,
                Family.CAULDRON_LEVEL_CHANGE,
                player,
                event.getBlock(),
                List.of(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaTntPrime(TNTPrimeEvent event) {
        cancelUnlessAllowed(
                event,
                Family.TNT_PRIME,
                responsiblePlayer(event.getPrimingEntity()),
                event.getPrimingBlock(),
                List.of(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onContestantHangingPlace(HangingPlaceEvent event) {
        Block placedBlock = event.getBlock().getRelative(event.getBlockFace());
        cancelUnlessAllowed(
                event,
                Family.HANGING_PLACE,
                event.getPlayer(),
                event.getBlock(),
                List.of(placedBlock));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaHangingBreak(HangingBreakEvent event) {
        Player player =
                event instanceof HangingBreakByEntityEvent byEntity
                        ? responsiblePlayer(byEntity.getRemover())
                        : null;
        Block occupiedBlock = event.getEntity().getLocation().getBlock();
        cancelUnlessAllowed(
                event,
                Family.HANGING_BREAK,
                player,
                occupiedBlock,
                List.of(occupiedBlock));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedEntityInteract(PlayerInteractEntityEvent event) {
        Block occupiedBlock = event.getRightClicked().getLocation().getBlock();
        cancelUnlessAllowed(
                event,
                Family.PLAYER_ENTITY_INTERACTION,
                event.getPlayer(),
                occupiedBlock,
                List.of(occupiedBlock));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedArmorStandDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof ArmorStand)) {
            return;
        }
        Block occupiedBlock = event.getEntity().getLocation().getBlock();
        cancelUnlessAllowed(
                event,
                Family.PLAYER_DECORATIVE_ENTITY_DAMAGE,
                responsiblePlayer(event.getDamager()),
                occupiedBlock,
                List.of(occupiedBlock));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onContestantEntityPlace(EntityPlaceEvent event) {
        BlockFace face = event.getBlockFace();
        Block placedBlock = face == null ? event.getBlock() : event.getBlock().getRelative(face);
        cancelUnlessAllowed(
                event,
                Family.DECORATIVE_ENTITY_PLACE,
                event.getPlayer(),
                event.getBlock(),
                List.of(placedBlock));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaEntityChangeBlock(EntityChangeBlockEvent event) {
        Family family =
                event.getEntity() instanceof FallingBlock
                        ? Family.FALLING_BLOCK
                        : Family.ENTITY_BLOCK_CHANGE;
        Location source =
                event.getEntity() instanceof FallingBlock fallingBlock
                        ? fallingBlock.getSourceLoc()
                        : event.getEntity().getLocation();
        cancelUnlessAllowed(event, family, null, source, List.of(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaEntityInteract(EntityInteractEvent event) {
        cancelUnlessAllowed(
                event,
                Family.ENTITY_INTERACTION,
                responsiblePlayer(event.getEntity()),
                event.getBlock(),
                List.of(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaEntityCreatePortal(EntityCreatePortalEvent event) {
        cancelUnlessAllowed(
                event,
                Family.ENTITY_CREATE_PORTAL,
                responsiblePlayer(event.getEntity()),
                event.getEntity().getLocation(),
                blocks(event.getBlocks()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaBlockForm(BlockFormEvent event) {
        cancelUnlessAllowed(
                event,
                Family.BLOCK_FORM,
                null,
                event.getBlock(),
                List.of(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaEntityBlockForm(EntityBlockFormEvent event) {
        cancelUnlessAllowed(
                event,
                Family.ENTITY_BLOCK_FORM,
                event.getEntity() instanceof Player player ? player : null,
                event.getBlock(),
                List.of(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaLeavesDecay(LeavesDecayEvent event) {
        cancelUnlessAllowed(
                event,
                Family.LEAVES_DECAY,
                null,
                event.getBlock(),
                List.of(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaBlockFade(BlockFadeEvent event) {
        cancelUnlessAllowed(
                event,
                Family.BLOCK_FADE,
                null,
                event.getBlock(),
                List.of(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onContestantBucketEmpty(PlayerBucketEmptyEvent event) {
        cancelUnlessAllowed(
                event,
                Family.BUCKET_EMPTY,
                event.getPlayer(),
                event.getBlock(),
                List.of(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onContestantBucketFill(PlayerBucketFillEvent event) {
        cancelUnlessAllowed(
                event,
                Family.BUCKET_FILL,
                event.getPlayer(),
                event.getBlock(),
                List.of(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaFluidFlow(BlockFromToEvent event) {
        cancelUnlessAllowed(
                event,
                Family.FLUID_FLOW,
                null,
                event.getBlock(),
                List.of(event.getToBlock()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaBlockExplode(BlockExplodeEvent event) {
        List<Block> targets = new ArrayList<>(event.blockList());
        targets.add(event.getBlock());
        cancelUnlessAllowed(
                event, Family.BLOCK_EXPLOSION, null, event.getBlock(), targets);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaEntityExplode(EntityExplodeEvent event) {
        List<Position> targets = new ArrayList<>(positions(event.blockList()));
        targets.add(position(event.getLocation()));
        Player responsiblePlayer = responsiblePlayer(event.getEntity());
        if (!policy.allows(
                Family.ENTITY_EXPLOSION,
                responsiblePlayer == null ? null : responsiblePlayer.getUniqueId(),
                position(event.getLocation()),
                targets)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaPistonExtend(BlockPistonExtendEvent event) {
        cancelPistonUnlessAllowed(
                event,
                Family.PISTON_EXTEND,
                event.getBlock(),
                event.getBlocks(),
                event.getDirection());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaPistonRetract(BlockPistonRetractEvent event) {
        cancelPistonUnlessAllowed(
                event,
                Family.PISTON_RETRACT,
                event.getBlock(),
                event.getBlocks(),
                event.getDirection());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaBlockPhysics(BlockPhysicsEvent event) {
        cancelUnlessAllowed(
                event,
                Family.BLOCK_PHYSICS,
                null,
                event.getSourceBlock(),
                List.of(event.getBlock()));
    }

    private void cancelPistonUnlessAllowed(
            org.bukkit.event.Cancellable event,
            Family family,
            Block piston,
            List<Block> movedBlocks,
            BlockFace direction) {
        List<Block> targets = new ArrayList<>();
        targets.add(piston);
        for (Block movedBlock : movedBlocks) {
            targets.add(movedBlock);
            targets.add(movedBlock.getRelative(direction));
        }
        cancelUnlessAllowed(event, family, null, piston, targets);
    }

    private void cancelUnlessAllowed(
            org.bukkit.event.Cancellable event,
            Family family,
            Player actor,
            Block source,
            Collection<Block> targets) {
        if (targets.isEmpty()) {
            return;
        }
        if (!allows(family, actor, source, targets)) {
            event.setCancelled(true);
        }
    }

    private void cancelUnlessAllowed(
            org.bukkit.event.Cancellable event,
            Family family,
            Player actor,
            Location source,
            Collection<Block> targets) {
        if (targets.isEmpty()) {
            return;
        }
        if (!policy.allows(
                family,
                actor == null ? null : actor.getUniqueId(),
                source == null ? null : position(source),
                positions(targets))) {
            event.setCancelled(true);
        }
    }

    private boolean allows(
            Family family, Player actor, Block source, Collection<Block> targets) {
        return policy.allows(
                family,
                actor == null ? null : actor.getUniqueId(),
                source == null ? null : position(source),
                positions(targets));
    }

    private static Collection<Block> blocks(Collection<BlockState> states) {
        return states.stream().map(BlockState::getBlock).toList();
    }

    private static List<Position> positions(Collection<Block> blocks) {
        return blocks.stream().map(ActiveArenaMutationListener::position).toList();
    }

    private static Position position(Block block) {
        return new Position(block.getWorld(), block.getX(), block.getY(), block.getZ());
    }

    private static Position position(Location location) {
        return new Position(
                Objects.requireNonNull(location.getWorld(), "location world"),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
    }

    private static Player responsiblePlayer(org.bukkit.entity.Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }
}

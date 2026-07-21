# Active arena mutation matrix

This matrix is the durable review contract for Build Battle's active arena. “Building” means
`RoundPhase.BUILDING`; “protected” means any non-idle round phase or an arena export read. An
assigned plot includes its editable vertical range. Outside a protected arena these listeners do
not interfere with normal server play.

| Bukkit/Paper event family | Source | Destination(s) | Actor | Phase | Decision | Safety reason |
| --- | --- | --- | --- | --- | --- | --- |
| `BlockBreakEvent` | Broken block | Broken block | Player | Building | Allow only for the assigned owner in their plot; otherwise cancel | Direct contestant edit |
| `BlockPlaceEvent`, `BlockMultiPlaceEvent` | Block placed against | Every replaced/placed block | Player | Building | Allow only when every destination is in the owner's plot; otherwise cancel and deny build | Multi-place must be atomic at the boundary |
| `SignChangeEvent` | Sign block | Sign block | Player | Building | Allow only for the assigned owner in their plot; otherwise cancel | Sign text edits mutate a build-backed block entity |
| `PlayerBucketEmptyEvent`, `PlayerBucketFillEvent` | Target block | Target block | Player | Building | Allow only for the assigned owner in their plot; otherwise cancel | Direct fluid edits must stay owned |
| `PlayerInteractEvent` physical/right-click build interaction | Clicked block | Clicked or adjacent placement block | Player | Building | Allow only for the assigned owner in their plot; otherwise deny the affected interaction | Protects trampling and build-backed interactions while preserving placement against the plot floor |
| `BlockIgniteEvent` with player | Igniting block when present | Ignited block | Player | Building | Allow only for the assigned owner in their plot; otherwise cancel | Direct owner action |
| `BlockIgniteEvent` without player | Igniting block when present | Ignited block | Environment | Protected | Cancel | Fire has no accountable owner and can propagate |
| `BlockFertilizeEvent` | Fertilized block | Every changed block state | Player or environment | Building | Player: allow only in the owner's plot. Environment: allow only when source and all destinations share one assigned plot. Otherwise cancel | Bone meal/tree growth may be a legitimate build but cannot cross plots |
| `StructureGrowEvent` | Growth location | Every changed block state | Player or environment | Building | Player: allow only in the owner's plot. Environment: allow only when source and all destinations share one assigned plot. Otherwise cancel | Multi-block growth is boundary-checked atomically |
| `SpongeAbsorbEvent` | Sponge | Every absorbed fluid block | Environment | Building | Allow only when source and all destinations share one assigned plot; otherwise cancel | Sponge absorption is a bounded event list but may cross a plot edge |
| `BlockFromToEvent` | Flowing block | Destination block | Environment | Building | Allow only when source and destination share one assigned plot; otherwise cancel | Prevents fluid crossing while preserving in-plot fluid builds |
| `FluidLevelChangeEvent` | Fluid block | Same block/new level | Environment | Building | Allow only inside an assigned editable plot; otherwise cancel | Cross-block flow is guarded separately by `BlockFromToEvent` |
| `MoistureChangeEvent` | Farmland block | Same block/new state | Environment | Building | Allow only inside an assigned editable plot; otherwise cancel | Local crop builds remain functional |
| `CauldronLevelChangeEvent` | Cauldron | Same cauldron block entity | Responsible player or environment | Building | Allow only for the assigned owner; cancel environmental/non-owner changes | The event exposes an actor when the mutation is accountable |
| `BlockGrowEvent` | Growing block | Same block/new state | Environment | Building | Allow only inside an assigned editable plot; otherwise cancel | Local plant growth is safe; boundary-spanning structures use `StructureGrowEvent` |
| `BlockFormEvent` | Forming block | Same block/new state | Environment | Building | Allow only inside an assigned editable plot; otherwise cancel | Preserves local snow/ice-style builds without touching walls or the hub |
| `EntityBlockFormEvent` | Forming block | Same block/new state | Entity/player | Building | Allow only when the actor is the assigned owner; otherwise cancel | Non-player entity forms are indirect/non-owned |
| `EntityChangeBlockEvent` for falling blocks | Falling block source location | Changed block | Environment | Building | Allow only when source and destination share one assigned plot; otherwise cancel | Sand/gravel builds work without crossing boundaries |
| Other `EntityChangeBlockEvent` | Entity location | Changed block | Entity | Protected | Cancel | Indirect entity edits have no safe ownership proof |
| `EntityInteractEvent` | Interacting entity | Interacted block | Entity | Protected | Cancel | Non-player physical interactions such as farmland trampling have no contestant owner |
| `EntityCreatePortalEvent` | Entity location | Every portal block state | Entity | Protected | Cancel | Portal creation is an indirect multi-block mutation with no contestant owner |
| `BlockDispenseEvent` | Dispenser | Dispenser output | Automation | Protected | Cancel | The event does not provide a trustworthy final destination for fluid/entity output |
| `TNTPrimeEvent` | Priming block/entity | Primed TNT block | Player, entity, or automation | Protected | Cancel | Explosions are denied, so priming is denied early and deterministically |
| `BlockExplodeEvent`, `EntityExplodeEvent` | Explosion block/location | Explosion block list | Player, entity, or environment | Protected | Cancel | Chain effects and mutable explosion lists cannot prove plot containment |
| `BlockPistonExtendEvent` | Piston | Piston plus each moved block and its destination | Automation | Building | Allow only when every source/destination shares one assigned plot; otherwise cancel | Narrow boundary guard preserves normal in-plot redstone |
| `BlockPistonRetractEvent` | Piston | Piston plus each moved block and its destination | Automation | Building | Allow only when every source/destination shares one assigned plot; otherwise cancel | Narrow boundary guard preserves normal in-plot redstone |
| `BlockSpreadEvent` | Source block | Spread destination | Environment | Protected | Cancel | Fire/mushroom-style spread is non-owned and can continue beyond one event |
| `BlockBurnEvent` | Igniting block | Burned block | Environment | Protected | Cancel | Prevents indirect destruction of completed build material |
| `BlockFadeEvent` | Fading block | Same block/new state | Environment | Protected | Cancel | Prevents melt/fade from erasing contestant work |
| `LeavesDecayEvent` | Leaf block | Same block | Environment | Protected | Cancel | Prevents decay from erasing contestant work |
| `HangingPlaceEvent` | Supporting block/face | Occupied hanging block | Player or automation | Building | Allow only for the assigned owner; otherwise cancel | Paintings/item frames are build-backed entities |
| `HangingBreakEvent`, `HangingBreakByEntityEvent` | Occupied hanging block | Removed hanging entity | Responsible player or environment | Building | Allow only for the assigned owner; otherwise cancel | Physics/projectiles/non-owners cannot remove another build |
| `EntityPlaceEvent` | Supporting block/face | Occupied decorative-entity block | Player or automation | Building | Allow only for the assigned owner; otherwise cancel | Covers armor stands and other placed decorative entities |
| `PlayerInteractEntityEvent` | Occupied entity block | Entity state | Player | Building | Allow only for the assigned owner; otherwise cancel | Item frames/armor stands carry build state |
| `EntityDamageByEntityEvent` against armor stand | Damager/projectile | Occupied armor-stand block | Responsible player or environment | Building | Allow only for the assigned owner; otherwise cancel | Projectile ownership is resolved to its shooter |
| `BlockPhysicsEvent` | Reported source block | Reported changed block | Environment | Any | Allow | Physics is not cancelled wholesale; concrete mutations are guarded by their specific event families above |

During any protected phase other than Building, every family above that can mutate arena build
state is cancelled. The sole exception is `BlockPhysicsEvent`: cancellation can suppress normal
neighbor updates and break legitimate builds, while the concrete mutation events provide the
narrow enforcement points. None of these handlers scans the arena or mutates blocks; all work is
limited to the event's bounded block list.

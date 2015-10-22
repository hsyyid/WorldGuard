/*
 * WorldGuard, a suite of tools for Minecraft
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldGuard team and contributors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.worldguard.sponge.listener;

import static com.sk89q.worldguard.sponge.cause.Cause.create;

import com.flowpowered.math.vector.Vector3d;
import com.google.common.collect.Lists;
import com.sk89q.worldedit.foundation.Block;
import com.sk89q.worldguard.blacklist.action.Action;
import com.sk89q.worldguard.protection.FlagValueCalculator.Result;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.sponge.WorldConfiguration;
import com.sk89q.worldguard.sponge.WorldGuardPlugin;
import com.sk89q.worldguard.sponge.cause.Cause;
import com.sk89q.worldguard.sponge.event.DelegateEvent;
import com.sk89q.worldguard.sponge.event.block.BreakBlockEvent;
import com.sk89q.worldguard.sponge.event.block.PlaceBlockEvent;
import com.sk89q.worldguard.sponge.event.block.UseBlockEvent;
import com.sk89q.worldguard.sponge.event.entity.DamageEntityEvent;
import com.sk89q.worldguard.sponge.event.entity.DestroyEntityEvent;
import com.sk89q.worldguard.sponge.event.entity.SpawnEntityEvent;
import com.sk89q.worldguard.sponge.event.entity.UseEntityEvent;
import com.sk89q.worldguard.sponge.event.inventory.UseItemEvent;
import com.sk89q.worldguard.sponge.listener.debounce.BlockPistonExtendKey;
import com.sk89q.worldguard.sponge.listener.debounce.BlockPistonRetractKey;
import com.sk89q.worldguard.sponge.listener.debounce.EventDebounce;
import com.sk89q.worldguard.sponge.listener.debounce.legacy.AbstractEventDebounce.Entry;
import com.sk89q.worldguard.sponge.listener.debounce.legacy.BlockEntityEventDebounce;
import com.sk89q.worldguard.sponge.listener.debounce.legacy.EntityEntityEventDebounce;
import com.sk89q.worldguard.sponge.listener.debounce.legacy.InventoryMoveItemEventDebounce;
import com.sk89q.worldguard.sponge.util.Blocks;
import com.sk89q.worldguard.sponge.util.Events;
import com.sk89q.worldguard.sponge.util.Items;
import com.sk89q.worldguard.sponge.util.Materials;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.carrier.Dispenser;
import org.spongepowered.api.block.tileentity.carrier.Hopper;
import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.type.DyeColor;
import org.spongepowered.api.effect.Viewer;
import org.spongepowered.api.effect.particle.ParticleEffect;
import org.spongepowered.api.effect.particle.ParticleEffect.Material;
import org.spongepowered.api.effect.particle.ParticleTypes;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.EntityType;
import org.spongepowered.api.entity.EntityTypes;
import org.spongepowered.api.entity.FallingBlock;
import org.spongepowered.api.entity.Item;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.gamemode.GameModes;
import org.spongepowered.api.entity.projectile.ThrownPotion;
import org.spongepowered.api.entity.projectile.source.ProjectileSource;
import org.spongepowered.api.event.Cancellable;
import org.spongepowered.api.event.Event;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.ChangeBlockEvent;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.block.NotifyNeighborBlockEvent;
import org.spongepowered.api.event.block.tileentity.ChangeSignEvent;
import org.spongepowered.api.event.entity.DestructEntityEvent;
import org.spongepowered.api.event.entity.InteractEntityEvent;
import org.spongepowered.api.event.entity.TameEntityEvent;
import org.spongepowered.api.event.item.inventory.ChangeInventoryEvent;
import org.spongepowered.api.event.item.inventory.DropItemEvent;
import org.spongepowered.api.event.item.inventory.UseItemStackEvent;
import org.spongepowered.api.event.world.ExplosionEvent;
import org.spongepowered.api.item.ItemTypes;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.transaction.SlotTransaction;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Vector;

import javax.annotation.Nullable;

public class EventAbstractionListener extends AbstractListener {

    private final BlockEntityEventDebounce interactDebounce = new BlockEntityEventDebounce(10000);
    private final EntityEntityEventDebounce pickupDebounce = new EntityEntityEventDebounce(10000);
    private final BlockEntityEventDebounce entityBreakBlockDebounce = new BlockEntityEventDebounce(10000);
    private final InventoryMoveItemEventDebounce moveItemDebounce = new InventoryMoveItemEventDebounce(30000);
    private final EventDebounce<BlockPistonRetractKey> pistonRetractDebounce = EventDebounce.create(5000);
    private final EventDebounce<BlockPistonExtendKey> pistonExtendDebounce = EventDebounce.create(5000);

    /**
     * Construct the listener.
     *
     * @param plugin an instance of WorldGuardPlugin
     */
    public EventAbstractionListener(WorldGuardPlugin plugin) {
        super(plugin);
    }

    @Override
    public void registerEvents() {
        super.registerEvents();
    }

    // -------------------------------------------------------------------------
    // Block break / place
    // -------------------------------------------------------------------------

    @Listener
    public void onBlockBreak(ChangeBlockEvent.Break event) {
        Optional<Player> optPlayer = event.getCause().first(Player.class);

        if (!optPlayer.isPresent()) {
            return;
        }

        Player player = optPlayer.get();

        for (Transaction<BlockSnapshot> transaction : event.getTransactions()) {
            Location<World> modLoc = transaction.getFinal().getLocation().get();
            Events.fireToCancel(event, new BreakBlockEvent(event, create(player), modLoc));

            if (event.isCancelled()) {
                playDenyEffect(player, modLoc.add(0.5, 1, 0.5));
            }
        }
    }

    @Listener
    public void onBlockPlace(ChangeBlockEvent.Place event) {
        Optional<Player> optPlayer = event.getCause().first(Player.class);

        if (!optPlayer.isPresent()) {
            return;
        }

        Player player = optPlayer.get();

        for (Transaction<BlockSnapshot> transaction : event.getTransactions()) {
            BlockSnapshot previous = transaction.getOriginal();
            BlockState previousState = previous.getState();

            BlockSnapshot next = transaction.getFinal();
            BlockState nextState = next.getState();

            // Some blocks, like tall grass and fire, get replaced
            if (previousState.getType() != BlockTypes.AIR) {
                Events.fireToCancel(event, new BreakBlockEvent(event, create(player), previous.getLocation().get(), previousState.getType()));
            }

            if (!event.isCancelled()) {
                ItemStack itemStack = Items.toItemStack(nextState.getType(), 1).get();
                Events.fireToCancel(event, new UseItemEvent(event, create(player), player.getWorld(), itemStack));
            }

            if (!event.isCancelled()) {
                Events.fireToCancel(event, new PlaceBlockEvent(event, create(player), next.getLocation().get()));
            }

            if (event.isCancelled()) {
                playDenyEffect(player, next.getLocation().get().add(0.5, 0.5, 0.5));
            }
        }
    }

    @Listener
    public void onBlockBurn(NotifyNeighborBlockEvent.Burn event) {
        BlockState target = event.getBlock();

        BlockState[] adjacent =
                new BlockState[] {target.getRelative(BlockFace.NORTH), target.getRelative(BlockFace.SOUTH), target.getRelative(BlockFace.WEST),
                        target.getRelative(BlockFace.EAST), target.getRelative(BlockFace.UP), target.getRelative(BlockFace.DOWN)};

        int found = 0;
        boolean allowed = false;

        for (BlockState source : adjacent) {
            if (source.getType() == BlockTypes.FIRE) {
                found++;
                if (Events.fireAndTestCancel(new BreakBlockEvent(event, create(source), target))) {
                    source.setType(Material.AIR);
                } else {
                    allowed = true;
                }
            }
        }

        if (found > 0 && !allowed) {
            event.setCancelled(true);
        }
    }

    @Listener
    public void onStructureGrowEvent(StructureGrowEvent event) {
        int originalCount = event.getBlocks().size();
        List<Block> blockList = Lists.transform(event.getBlocks(), new BlockStateAsBlockFunction());

        Player player = event.getPlayer();
        if (player != null) {
            Events.fireBulkEventToCancel(event, new PlaceBlockEvent(event, create(player), event.getLocation().getWorld(), blockList, Material.AIR));
        } else {
            Events.fireBulkEventToCancel(event, new PlaceBlockEvent(event, create(event.getLocation().getBlock()), event.getLocation().getWorld(),
                    blockList, Material.AIR));
        }

        if (!event.isCancelled() && event.getBlocks().size() != originalCount) {
            event.getLocation().getBlock().setType(Material.AIR);
        }
    }

    // TODO: Handle EntityCreatePortalEvent?

    @Listener
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Block block = event.getBlock();
        Entity entity = event.getEntity();
        Material to = event.getTo();

        // Forget about Redstone ore, especially since we handle it in INTERACT
        if (Materials.isRedstoneOre(block.getType()) && Materials.isRedstoneOre(to)) {
            return;
        }

        // Fire two events: one as BREAK and one as PLACE
        if (event.getTo() != Material.AIR && event.getBlock().getType() != Material.AIR) {
            Events.fireToCancel(event, new BreakBlockEvent(event, create(entity), block));
            Events.fireToCancel(event, new PlaceBlockEvent(event, create(entity), block.getLocation(), to));
        } else {
            if (event.getTo() == Material.AIR) {
                // Track the source so later we can create a proper chain of
                // causes
                if (entity instanceof FallingBlock) {
                    Cause.trackParentCause(entity, block);

                    // Switch around the event
                    Events.fireToCancel(event, new SpawnEntityEvent(event, create(block), entity));
                } else {
                    entityBreakBlockDebounce.debounce(event.getBlock(), event.getEntity(), event,
                            new BreakBlockEvent(event, create(entity), event.getBlock()));
                }
            } else {
                boolean wasCancelled = event.isCancelled();
                Cause cause = create(entity);

                Events.fireToCancel(event, new PlaceBlockEvent(event, cause, event.getBlock().getLocation(), to));

                if (event.isCancelled() && !wasCancelled && entity instanceof FallingBlock) {
                    FallingBlock fallingBlock = (FallingBlock) entity;
                    ItemStack itemStack = new ItemStack(fallingBlock.getMaterial(), 1, fallingBlock.getBlockData());
                    Item item = block.getWorld().dropItem(fallingBlock.getLocation(), itemStack);
                    item.setVelocity(new Vector());
                    if (Events.fireAndTestCancel(new SpawnEntityEvent(event, create(block, entity), item))) {
                        item.remove();
                    }
                }
            }
        }
    }

    @Listener
    public void onEntityExplode(ExplosionEvent.Detonate event) {
        Optional<Entity> optEntity = event.getCause().first(Entity.class);

        if (optEntity.isPresent()) {
            Events.fireBulkEventToCancel(event, new BreakBlockEvent(event, create(entity), event.getLocation().getWorld(), event.ge, BlockTypes.AIR));
        }
    }

    @Listener
    public void onBlockPistonRetract(BlockPistonRetractEvent event) {
        if (event.isSticky()) {
            EventDebounce.Entry entry = pistonRetractDebounce.getIfNotPresent(new BlockPistonRetractKey(event), event);
            if (entry != null) {
                Cause cause = create(event.getBlock());
                Events.fireToCancel(event, new BreakBlockEvent(event, cause, event.getRetractLocation(), Material.AIR));
                Events.fireToCancel(event, new PlaceBlockEvent(event, cause, event.getBlock().getRelative(event.getDirection())));
                entry.setCancelled(event.isCancelled());

                if (event.isCancelled()) {
                    playDenyEffect(event.getBlock().getLocation().add(0.5, 1, 0.5));
                }
            }
        }
    }

    @Listener
    public void onBlockPistonExtend(BlockPistonExtendEvent event) {
        EventDebounce.Entry entry = pistonExtendDebounce.getIfNotPresent(new BlockPistonExtendKey(event), event);
        if (entry != null) {
            // A hack for now
            List<Block> blocks = new ArrayList<Block>(event.getBlocks());
            Block lastBlock = event.getBlock().getRelative(event.getDirection(), event.getLength() + 1);
            blocks.add(lastBlock);
            int originalLength = blocks.size();
            Events.fireBulkEventToCancel(event, new PlaceBlockEvent(event, create(event.getBlock()), event.getBlock().getWorld(), blocks,
                    Material.STONE));
            if (blocks.size() != originalLength) {
                event.setCancelled(true);
            }
            entry.setCancelled(event.isCancelled());

            if (event.isCancelled()) {
                playDenyEffect(event.getBlock().getLocation().add(0.5, 1, 0.5));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Block external interaction
    // -------------------------------------------------------------------------

    @Listener
    public void onBlockDamage(BlockDamageEvent event) {
        Block target = event.getBlock();

        // Previously, and perhaps still, the only way to catch cake eating
        // events was through here
        if (target.getType() == Material.CAKE_BLOCK) {
            Events.fireToCancel(event, new UseBlockEvent(event, create(event.getPlayer()), target));
        }
    }

    @Listener
    public void onPlayerInteract(InteractBlockEvent event) {
        if (event.getCause().first(Player.class).isPresent()) {
            Player player = event.getCause().first(Player.class).get();
            @Nullable
            ItemStack item = player.getItemInHand().get();
            BlockSnapshot clicked = event.getTargetBlock();
            BlockSnapshot placed;
            boolean silent = false;
            boolean modifiesWorld;
            Cause cause = create(player);

            switch (event.getAction()) {
                case PHYSICAL:
                    // Forget about Redstone ore
                    if (Materials.isRedstoneOre(clicked.getType()) || clicked.getType() == Material.SOIL) {
                        silent = true;
                    }

                    interactDebounce.debounce(clicked, event.getPlayer(), event, new UseBlockEvent(event, cause, clicked).setSilent(silent)
                            .setAllowed(hasInteractBypass(clicked)));
                    break;

                case RIGHT_CLICK_BLOCK:
                    placed = clicked.getRelative(event.getBlockFace());

                    // Re-used for dispensers
                    handleBlockRightClick(event, create(event.getPlayer()), item, clicked, event.getBlockFace(), placed);

                case LEFT_CLICK_BLOCK:
                    placed = clicked.getRelative(event.getBlockFace());

                    // Only fire events for blocks that are modified when right
                    // clicked
                    modifiesWorld =
                            isBlockModifiedOnClick(clicked, event.getAction() == Action.RIGHT_CLICK_BLOCK)
                                    || (item != null && isItemAppliedToBlock(item, clicked));

                    if (Events.fireAndTestCancel(new UseBlockEvent(event, cause, clicked).setAllowed(!modifiesWorld))) {
                        event.setUseInteractedBlock(Result.DENY);
                    }

                    // Handle connected blocks (i.e. beds, chests)
                    for (Block connected : Blocks.getConnected(clicked)) {
                        if (Events.fireAndTestCancel(new UseBlockEvent(event, create(event.getPlayer()), connected).setAllowed(!modifiesWorld))) {
                            event.setUseInteractedBlock(Result.DENY);
                            break;
                        }
                    }

                    // Special handling of putting out fires
                    if (event.getAction() == Action.LEFT_CLICK_BLOCK && placed.getType() == Material.FIRE) {
                        if (Events.fireAndTestCancel(new BreakBlockEvent(event, create(event.getPlayer()), placed))) {
                            event.setUseInteractedBlock(Result.DENY);
                            break;
                        }
                    }

                    if (event.isCancelled()) {
                        playDenyEffect(event.getPlayer(), clicked.getLocation().add(0.5, 1, 0.5));
                    }

                case LEFT_CLICK_AIR:
                case RIGHT_CLICK_AIR:
                    if (item != null && !item.getType().isBlock()
                            && Events.fireAndTestCancel(new UseItemEvent(event, cause, player.getWorld(), item))) {
                        event.setUseItemInHand(Result.DENY);
                        event.setCancelled(true); // The line above does not
                                                  // appear to work with spawn
                                                  // eggs
                    }

                    // Check for items that the administrator has configured to
                    // emit a "use block here" event where the player is
                    // standing, which is a hack to protect items that don't
                    // throw events
                    if (item != null && getWorldConfig(player.getWorld()).blockUseAtFeet.test(item)) {
                        if (Events.fireAndTestCancel(new UseBlockEvent(event, cause, player.getLocation().getBlock()))) {
                            event.setCancelled(true);
                        }
                    }

                    break;
            }
        }
    }

    @Listener
    public void onEntityInteract(InteractEntityEvent event) {
        interactDebounce.debounce(event.getBlock(), event.getEntity(), event,
                new UseBlockEvent(event, create(event.getEntity()), event.getBlock()).setAllowed(hasInteractBypass(event.getBlock())));
    }

    @Listener
    public void onBlockIgnite(BlockIgniteEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        Cause cause;

        // Find the cause
        if (event.getPlayer() != null) {
            cause = create(event.getPlayer());
        } else if (event.getIgnitingEntity() != null) {
            cause = create(event.getIgnitingEntity());
        } else if (event.getIgnitingBlock() != null) {
            cause = create(event.getIgnitingBlock());
        } else {
            cause = Cause.unknown();
        }

        Events.fireToCancel(event, new PlaceBlockEvent(event, cause, block.getLocation(), Material.FIRE));
    }

    @Listener
    public void onSignChange(ChangeSignEvent event) {
        if (event.getCause().first(Player.class).isPresent()) {
            Player player = (Player) event.getCause().first(Player.class).get();
            Events.fireToCancel(event, new UseBlockEvent(event, create(player), event.getTargetTile()));

            if (event.isCancelled()) {
                playDenyEffect(event.getPlayer(), event.getBlock().getLocation().add(0.5, 0.5, 0.5));
            }
        }
    }

    @Listener
    public void onBedEnter(PlayerBedEnterEvent event) {
        Events.fireToCancel(event, new UseBlockEvent(event, create(event.getPlayer()), event.getBed()));
    }

    @Listener
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        Block blockAffected = event.getBlockClicked().getRelative(event.getBlockFace());
        boolean allowed = false;

        // Milk buckets can't be emptied as of writing
        if (event.getBucket() == Material.MILK_BUCKET) {
            allowed = true;
        }

        ItemStack item = new ItemStack(event.getBucket(), 1);
        Material blockMaterial = Materials.getBucketBlockMaterial(event.getBucket());
        Events.fireToCancel(event, new PlaceBlockEvent(event, create(player), blockAffected.getLocation(), blockMaterial).setAllowed(allowed));
        Events.fireToCancel(event, new UseItemEvent(event, create(player), player.getWorld(), item).setAllowed(allowed));

        playDenyEffect(event.getPlayer(), blockAffected.getLocation().add(0.5, 0.5, 0.5));
    }

    @Listener
    public void onPlayerBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        Block blockAffected = event.getBlockClicked().getRelative(event.getBlockFace());
        boolean allowed = false;

        // Milk buckets can't be emptied as of writing
        if (event.getBucket() == Material.MILK_BUCKET) {
            allowed = true;
        }

        ItemStack item = new ItemStack(event.getBucket(), 1);
        Events.fireToCancel(event, new BreakBlockEvent(event, create(player), blockAffected).setAllowed(allowed));
        Events.fireToCancel(event, new UseItemEvent(event, create(player), player.getWorld(), item).setAllowed(allowed));

        playDenyEffect(event.getPlayer(), blockAffected.getLocation().add(0.5, 1, 0.5));
    }

    // TODO: Handle EntityPortalEnterEvent

    // -------------------------------------------------------------------------
    // Block self-interaction
    // -------------------------------------------------------------------------

    @Listener
    public void onBlockFromTo(BlockFromToEvent event) {
        WorldConfiguration config = getWorldConfig(event.getBlock().getWorld());

        // This only applies to regions but nothing else cares about high
        // frequency events at the moment
        if (!config.useRegions || (!config.highFreqFlags && !config.checkLiquidFlow)) {
            return;
        }

        Block from = event.getBlock();
        Block to = event.getToBlock();
        Material fromType = from.getType();
        Material toType = to.getType();

        // Liquids pass this event when flowing to solid blocks
        if (toType.isSolid() && Materials.isLiquid(fromType)) {
            return;
        }

        // This significantly reduces the number of events without having
        // too much effect. Unfortunately it appears that even if this
        // check didn't exist, you can raise the level of some liquid
        // flow and the from/to data may not be correct.
        if ((Materials.isWater(fromType) && Materials.isWater(toType)) || (Materials.isLava(fromType) && Materials.isLava(toType))) {
            return;
        }

        Cause cause = create(from);

        // Disable since it's probably not needed
        /*
         * if (from.getType() != Material.AIR) { Events.fireToCancel(event, new
         * BreakBlockEvent(event, cause, to)); }
         */

        Events.fireToCancel(event, new PlaceBlockEvent(event, cause, to.getLocation(), from.getType()));
    }

    // -------------------------------------------------------------------------
    // Entity break / place
    // -------------------------------------------------------------------------

    @Listener
    public void onCreatureSpawn(org.spongepowered.api.event.entity.SpawnEntityEvent event) {
        if (event.getCause().first(Entity.class).isPresent() && event.getCause().first(Entity.class).get().getType().equals(EntityTypes.EGG)) {
            if (getWorldConfig(event.getTargetWorld()).strictEntitySpawn) {
            }
            for (Entity entity : event.getEntities()) {
                Events.fireToCancel(event, new SpawnEntityEvent(event, Cause.unknown(), entity));
            }
        }
    }

    @Listener
    public void onHangingPlace(HangingPlaceEvent event) {
        Events.fireToCancel(event, new SpawnEntityEvent(event, create(event.getPlayer()), event.getEntity()));

        if (event.isCancelled()) {
            Block effectBlock = event.getBlock().getRelative(event.getBlockFace());
            playDenyEffect(event.getPlayer(), effectBlock.getLocation().add(0.5, 0.5, 0.5));
        }
    }

    @Listener
    public void onHangingBreak(HangingBreakEvent event) {
        if (event instanceof HangingBreakByEntityEvent) {
            Entity remover = ((HangingBreakByEntityEvent) event).getRemover();
            Events.fireToCancel(event, new DestroyEntityEvent(event, create(remover), event.getEntity()));

            if (event.isCancelled() && remover instanceof Player) {
                playDenyEffect((Player) remover, event.getEntity().getLocation());
            }
        }
    }

    @Listener
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        Events.fireToCancel(event, new DestroyEntityEvent(event, create(event.getAttacker()), event.getVehicle()));
    }

    @Listener
    public void onBlockExp(BlockExpEvent event) {
        if (event.getExpToDrop() > 0) { // Event is raised even where no XP is
                                        // being dropped
            if (Events.fireAndTestCancel(new SpawnEntityEvent(event, create(event.getBlock()), event.getBlock().getLocation(),
                    EntityType.EXPERIENCE_ORB))) {
                event.setExpToDrop(0);
            }
        }
    }

    @Listener
    public void onPlayerFish(PlayerFishEvent event) {
        if (Events.fireAndTestCancel(new SpawnEntityEvent(event, create(event.getPlayer(), event.getHook()), event.getHook().getLocation(),
                EntityType.EXPERIENCE_ORB))) {
            event.setExpToDrop(0);
        }
    }

    @Listener
    public void onExpBottle(UseItemStackEvent event) {
        if (event.getCause().first(Entity.class).isPresent()) {
            Entity entity = event.getCause().first(Entity.class).get();
            if (event.getItemStackInUse().getOriginal().createStack().getItem().equals(ItemTypes.EXPERIENCE_BOTTLE)) {
                if (Events.fireAndTestCancel(new SpawnEntityEvent(event, create(entity), entity.getLocation(), EntityTypes.EXPERIENCE_ORB))) {
                    event.getItemStackInUse().getOriginal().createStack().offer(Keys.EXPERIENCE_LEVEL, 0);
                    // Give the player back his or her XP bottle
                    ProjectileSource shooter = (ProjectileSource) entity;
                    if (shooter instanceof Player) {
                        Player player = (Player) shooter;
                        if (player.getGameModeData().get(Keys.GAME_MODE).get() != GameModes.CREATIVE) {
                            player.setItemInHand(WorldGuardPlugin.inst().getGame().getRegistry().createItemBuilder()
                                    .itemType(ItemTypes.EXPERIENCE_BOTTLE).quantity(1).build());
                        }
                    }
                }
            }
        }
    }

    @Listener
    public void onEntityDeath(DestructEntityEvent event) {
        if (event.getDroppedExp() > 0) {
            if (Events.fireAndTestCancel(new SpawnEntityEvent(event, create(event.getTargetEntity()), event.getEntity().getLocation(),
                    EntityType.EXPERIENCE_ORB))) {
                event.setDroppedExp(0);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Entity external interaction
    // -------------------------------------------------------------------------

    @Listener
    public void onPlayerInteractEntity(InteractEntityEvent event) {
        if (event.getCause().first(Player.class).isPresent()) {
            Player player = (Player) event.getCause().first(Player.class).get();
            World world = player.getWorld();
            ItemStack item = player.getItemInHand().get();
            Entity entity = event.getTargetEntity();

            Events.fireToCancel(event, new UseItemEvent(event, create(player), world, item));
            Events.fireToCancel(event, new UseEntityEvent(event, create(player), entity));
        }
    }

    @Listener
    public void onEntityDamage(org.spongepowered.api.event.entity.DamageEntityEvent event) {
        if (event.getCause().first(BlockSnapshot.class).isPresent()) {
            BlockSnapshot cause = event.getCause().first(BlockSnapshot.class).get();
            Events.fireToCancel(event, new DamageEntityEvent(event, create(cause), event.getTargetEntity()));
        } else if (event.getCause().first(Entity.class).isPresent()) {
            Entity damager = event.getCause().first(Entity.class).get();
            Events.fireToCancel(event, new DamageEntityEvent(event, create(damager), event.getTargetEntity()));

            // Item use event with the item in hand
            // Older blacklist handler code used this, although it suffers from
            // race problems
            if (damager instanceof Player) {
                ItemStack item = ((Player) damager).getItemInHand().get();

                if (item != null) {
                    Events.fireToCancel(event, new UseItemEvent(event, create(damager), event.getTargetEntity().getWorld(), item));
                }
            }
        }
    }

    @Listener
    public void onEntityCombust(DestructEntityEvent event) {
        if (event.getCause().first(BlockSnapshot.class).isPresent()) {
            BlockSnapshot block = event.getCause().first(BlockSnapshot.class).get();
            Events.fireToCancel(event, new DamageEntityEvent(event, create(block), event.getTargetEntity()));

        } else if (event.getCause().first(Entity.class).isPresent()) {
            Entity cause = event.getCause().first(Entity.class).get();
            Events.fireToCancel(event, new DamageEntityEvent(event, create(cause), event.getTargetEntity()));
        }
    }

    @Listener
    public void onEntityUnleash(EntityUnleashEvent event) {
        if (event instanceof PlayerUnleashEntityEvent) {
            PlayerUnleashEntityEvent playerEvent = (PlayerUnleashEntityEvent) event;
            Events.fireToCancel(playerEvent, new UseEntityEvent(playerEvent, create(playerEvent.getPlayer()), event.getEntity()));
        } else {
            // TODO: Raise anyway?
        }
    }

    @Listener
    public void onEntityTame(TameEntityEvent event) {
        if (event.getCause().first(Entity.class).isPresent()) {
            Entity entity = event.getCause().first(Entity.class).get();
            Events.fireToCancel(event, new UseEntityEvent(event, create(entity), event.getTargetEntity()));
        }
    }

    @Listener
    public void onPlayerShearEntity(PlayerShearEntityEvent event) {
        Events.fireToCancel(event, new UseEntityEvent(event, create(event.getPlayer()), event.getEntity()));
    }

    @Listener
    public void onPlayerPickupItem(ChangeInventoryEvent.Pickup event) {
        if (event.getCause().first(Player.class).isPresent()) {
            Player player = event.getCause().first(Player.class).get();
            for (SlotTransaction transaction : event.getTransactions()) {
                ItemStack item = transaction.getFinal().createStack();
                // TODO: Get the actual Entity picked up fir the last param
                pickupDebounce.debounce(player, item, event, new DestroyEntityEvent(event, create(player), item));
            }
        }
    }

    @Listener
    public void onPlayerDropItem(DropItemEvent.Dispense event) {
        if (event.getCause().first(Player.class).isPresent()) {
            Player player = event.getCause().first(Player.class).get();
            for (Entity entity : event.getEntities()) {
                Events.fireToCancel(event, new SpawnEntityEvent(event, create(player), entity));
            }
        }
    }

    @Listener
    public void onVehicleDamage(VehicleDamageEvent event) {
        Entity attacker = event.getAttacker();
        Events.fireToCancel(event, new DestroyEntityEvent(event, create(attacker), event.getVehicle()));
    }

    @Listener
    public void onVehicleEnter(VehicleDamageEvent event) {
        Events.fireToCancel(event, new UseEntityEvent(event, create(event.getAttacker()), event.getVehicle()));
    }

    // -------------------------------------------------------------------------
    // Composite events
    // -------------------------------------------------------------------------

    @Listener
    public void onPlayerItemConsume(UseItemStackEvent event) {
        if (event.getCause().first(Player.class).isPresent()) {
            Player player = event.getCause().first(Player.class).get();
            Events.fireToCancel(event, new UseItemEvent(event, create(player), player.getWorld(), event.getItemStackInUse().getOriginal()
                    .createStack()));
        }
    }

    @Listener
    public void onInventoryOpen(InventoryOpenEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof BlockState) {
            Events.fireToCancel(event, new UseBlockEvent(event, create(event.getPlayer()), ((BlockState) holder).getBlock()));
        } else if (holder instanceof Entity) {
            if (!(holder instanceof Player)) {
                Events.fireToCancel(event, new UseEntityEvent(event, create(event.getPlayer()), (Entity) holder));
            }
        }
    }

    @Listener
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        final InventoryHolder causeHolder = event.getInitiator().getHolder();
        InventoryHolder sourceHolder = event.getSource().getHolder();
        InventoryHolder targetHolder = event.getDestination().getHolder();

        Entry entry;

        if ((entry = moveItemDebounce.tryDebounce(event)) != null) {
            Cause cause;

            if (causeHolder instanceof Entity) {
                cause = create(causeHolder);
            } else if (causeHolder instanceof BlockState) {
                cause = create(((BlockState) causeHolder).getBlock());
            } else {
                cause = Cause.unknown();
            }

            if (!causeHolder.equals(sourceHolder)) {
                handleInventoryHolderUse(event, cause, sourceHolder);
            }

            handleInventoryHolderUse(event, cause, targetHolder);

            if (event.isCancelled() && causeHolder instanceof Hopper) {
                Bukkit.getScheduler().scheduleSyncDelayedTask(getPlugin(), new Runnable() {

                    @Override
                    public void run() {
                        ((Hopper) causeHolder).getBlock().breakNaturally();
                    }
                });
            } else {
                entry.setCancelled(event.isCancelled());
            }
        }
    }

    @Listener
    public void onPotionSplash(PotionSplashEvent event) {
        Entity entity = event.getEntity();
        ThrownPotion potion = event.getPotion();
        World world = entity.getWorld();
        Cause cause = create(potion);

        // Fire item interaction event
        Events.fireToCancel(event, new UseItemEvent(event, cause, world, potion.getItem()));

        // Fire entity interaction event
        if (!event.isCancelled()) {
            int blocked = 0;
            boolean hasDamageEffect = Materials.hasDamageEffect(potion.getEffects());

            for (LivingEntity affected : event.getAffectedEntities()) {
                DelegateEvent delegate = hasDamageEffect ? new DamageEntityEvent(event, cause, affected) : new UseEntityEvent(event, cause, affected);

                // Consider the potion splash flag
                delegate.getRelevantFlags().add(DefaultFlag.POTION_SPLASH);

                if (Events.fireAndTestCancel(delegate)) {
                    event.setIntensity(affected, 0);
                    blocked++;
                }
            }

            if (blocked == event.getAffectedEntities().size()) {
                event.setCancelled(true);
            }
        }
    }

    @Listener
    public void onBlockDispense(BlockDispenseEvent event) {
        Cause cause = create(event.getBlock());
        Block dispenserBlock = event.getBlock();
        ItemStack item = event.getItem();
        MaterialData materialData = dispenserBlock.getState().getData();

        Events.fireToCancel(event, new UseItemEvent(event, cause, dispenserBlock.getWorld(), item));

        // Simulate right click event as players have it
        if (materialData instanceof Dispenser) {
            Dispenser dispenser = (Dispenser) materialData;
            Block placed = dispenserBlock.getRelative(dispenser.getFacing());
            Block clicked = placed.getRelative(dispenser.getFacing());
            handleBlockRightClick(event, cause, item, clicked, dispenser.getFacing().getOppositeFace(), placed);
        }
    }

    /**
     * Handle the right click of a block while an item is held.
     *
     * @param event the original event
     * @param cause the list of cause
     * @param item the item
     * @param clicked the clicked block
     * @param faceClicked the face of the clicked block
     * @param placed the placed block
     * @param <T> the event type
     */
    private static <T extends Event & Cancellable> void handleBlockRightClick(T event, Cause cause, @Nullable ItemStack item, Block clicked,
            BlockFace faceClicked, Block placed) {
        if (item != null && item.getType() == Material.TNT) {
            // Workaround for a bug that allowed TNT to trigger instantly if
            // placed
            // next to redstone, without plugins getting the clicked place event
            // (not sure if this actually still happens)
            Events.fireToCancel(event, new UseBlockEvent(event, cause, clicked.getLocation(), Material.TNT));
        }

        // Handle created Minecarts
        if (item != null && Materials.isMinecart(item.getType())) {
            // TODO: Give a more specific Minecart type
            Events.fireToCancel(event, new SpawnEntityEvent(event, cause, placed.getLocation().add(0.5, 0, 0.5), EntityType.MINECART));
        }

        // Handle created boats
        if (item != null && item.getType() == Material.BOAT) {
            Events.fireToCancel(event, new SpawnEntityEvent(event, cause, placed.getLocation().add(0.5, 0, 0.5), EntityType.BOAT));
        }

        // Handle created spawn eggs
        if (item != null && item.getType() == Material.MONSTER_EGG) {
            MaterialData data = item.getData();
            if (data instanceof SpawnEgg) {
                @Nullable
                EntityType type = ((SpawnEgg) data).getSpawnedType();
                if (type == null) {
                    type = EntityType.SHEEP; // Haven't investigated why it's
                                             // sometimes null
                }
                Events.fireToCancel(event, new SpawnEntityEvent(event, cause, placed.getLocation().add(0.5, 0, 0.5), type));
            }
        }

        // Handle cocoa beans
        if (item != null && item.getType() == Material.INK_SACK && Materials.isDyeColor(item.getData(), DyeColor.BROWN)) {
            // CraftBukkit doesn't or didn't throw a clicked place for this
            if (!(faceClicked == BlockFace.DOWN || faceClicked == BlockFace.UP)) {
                Events.fireToCancel(event, new PlaceBlockEvent(event, cause, placed.getLocation(), Material.COCOA));
            }
        }

        // Workaround for http://leaky.bukkit.org/issues/1034
        if (item != null && item.getType() == Material.TNT) {
            Events.fireToCancel(event, new PlaceBlockEvent(event, cause, placed.getLocation(), Material.TNT));
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Event & Cancellable> void handleInventoryHolderUse(T originalEvent, Cause cause, InventoryHolder holder) {
        if (originalEvent.isCancelled()) {
            return;
        }

        if (holder instanceof Entity) {
            Events.fireToCancel(originalEvent, new UseEntityEvent(originalEvent, cause, (Entity) holder));
        } else if (holder instanceof BlockState) {
            Events.fireToCancel(originalEvent, new UseBlockEvent(originalEvent, cause, ((BlockState) holder).getBlock()));
        } else if (holder instanceof DoubleChest) {
            Events.fireToCancel(originalEvent,
                    new UseBlockEvent(originalEvent, cause, ((BlockState) ((DoubleChest) holder).getLeftSide()).getBlock()));
            Events.fireToCancel(originalEvent,
                    new UseBlockEvent(originalEvent, cause, ((BlockState) ((DoubleChest) holder).getRightSide()).getBlock()));
        }
    }

    private boolean hasInteractBypass(Block block) {
        return getWorldConfig(block.getWorld()).allowAllInteract.test(block);
    }

    private boolean hasInteractBypass(World world, ItemStack item) {
        return getWorldConfig(world).allowAllInteract.test(item);
    }

    private boolean isBlockModifiedOnClick(Block block, boolean rightClick) {
        return Materials.isBlockModifiedOnClick(block.getType(), rightClick) && !hasInteractBypass(block);
    }

    private boolean isItemAppliedToBlock(ItemStack item, Block clicked) {
        return Materials.isItemAppliedToBlock(item.getType(), clicked.getType()) && !hasInteractBypass(clicked)
                && !hasInteractBypass(clicked.getWorld(), item);
    }

    private void playDenyEffect(Viewer viewer, Location location) {
        ParticleEffect effect =
                getPlugin().getGame().getRegistry().createParticleEffectBuilder(ParticleTypes.SMOKE_NORMAL).motion(new Vector3d(0, 1, 0)).count(1)
                        .build();

        viewer.spawnParticles(effect, location.getPosition());
    }

    private void playDenyEffect(Location<World> location) {
        playDenyEffect(location.getExtent(), location);
    }
}
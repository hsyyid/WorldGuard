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

package com.sk89q.worldguard.sponge.commands;

import com.sk89q.worldedit.event.platform.Interaction;

import com.sk89q.minecraft.util.commands.Command;
import com.sk89q.minecraft.util.commands.CommandContext;
import com.sk89q.minecraft.util.commands.CommandException;
import com.sk89q.minecraft.util.commands.CommandPermissions;
import com.sk89q.worldguard.sponge.WorldGuardPlugin;
import com.sk89q.worldguard.sponge.event.debug.*;
import com.sk89q.worldguard.util.report.CancelReport;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Event;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.text.Texts;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.util.command.CommandSource;
import org.spongepowered.api.util.command.source.ConsoleSource;
import org.spongepowered.api.world.Location;

import java.util.logging.Logger;

public class DebuggingCommands {

    private static final Logger log = Logger.getLogger(DebuggingCommands.class.getCanonicalName());
    private static final int MAX_TRACE_DISTANCE = 20;

    private final WorldGuardPlugin plugin;

    /**
     * Create a new instance.
     *
     * @param plugin The plugin instance
     */
    public DebuggingCommands(WorldGuardPlugin plugin) {
        this.plugin = plugin;
    }

    @Command(aliases = {"testbreak"}, usage = "[player]", desc = "Simulate a block break", min = 1, max = 1, flags = "t")
    @CommandPermissions("worldguard.debug.event")
    public void fireBreakEvent(CommandContext args, final CommandSource sender) throws CommandException {
        Player target = plugin.matchSinglePlayer(sender, args.getString(0));
        Location block = traceBlock(sender, target, args.hasFlag('t'));
        sender.sendMessage(Texts.of(TextColors.AQUA, "Testing BLOCK BREAK at ", TextColors.DARK_AQUA, block));
        LoggingBlockBreakEvent event = new LoggingBlockBreakEvent(block, block.createSnapshot(), target);
        testEvent(sender, target, event);
    }


    @Command(aliases = {"testplace"}, usage = "[player]", desc = "Simulate a block place", min = 1, max = 1, flags = "t")
    @CommandPermissions("worldguard.debug.event")
    public void firePlaceEvent(CommandContext args, final CommandSource sender) throws CommandException {
        Player target = plugin.matchSinglePlayer(sender, args.getString(0));
        Location block = traceBlock(sender, target, args.hasFlag('t'));
        sender.sendMessage(Texts.of(TextColors.AQUA, "Testing BLOCK PLACE at ", TextColors.DARK_AQUA, block));
        LoggingBlockPlaceEvent event = new LoggingBlockPlaceEvent(block, Direction.UP, block.createSnapshot(), target);
        testEvent(sender, target, event);
    }

    @Command(aliases = {"testinteract"}, usage = "[player]", desc = "Simulate a block interact", min = 1, max = 1, flags = "t")
    @CommandPermissions("worldguard.debug.event")
    public void fireInteractEvent(CommandContext args, final CommandSource sender) throws CommandException {
        Player target = plugin.matchSinglePlayer(sender, args.getString(0));
        Location block = traceBlock(sender, target, args.hasFlag('t'));
        sender.sendMessage(Texts.of(TextColors.AQUA, "Testing BLOCK INTERACT at ", TextColors.DARK_AQUA, block));
        LoggingPlayerInteractEvent event = new LoggingPlayerInteractEvent(target, block, Direction.UP);
        testEvent(sender, target, event);
    }

    @Command(aliases = {"testdamage"}, usage = "[player]", desc = "Simulate an entity damage", min = 1, max = 1, flags = "t")
    @CommandPermissions("worldguard.debug.event")
    public void fireDamageEvent(CommandContext args, final CommandSource sender) throws CommandException {
        Player target = plugin.matchSinglePlayer(sender, args.getString(0));
        Entity entity = traceEntity(sender, target, args.hasFlag('t'));
        sender.sendMessage(Texts.of(TextColors.AQUA, "Testing ENTITY DAMAGE on ", TextColors.DARK_AQUA, entity));
        LoggingEntityDamageByEntityEvent event = new LoggingEntityDamageByEntityEvent(target, Cause.of(entity), 1);
        testEvent(sender, target, event);
    }

    /**
     * Simulate an event and print its report.
     *
     * @param receiver The receiver of the messages
     * @param target The target
     * @param event THe event
     * @param <T> The type of event
     */
    private <T extends Event & CancelLogging> void testEvent(CommandSource receiver, Player target, T event) {
        boolean isConsole = receiver instanceof ConsoleSource;

        if (!receiver.equals(target)) {
            if (!isConsole) {
                log.info(receiver.getName() + " is simulating an event on " + target.getName());
            }

            target.sendMessage(Texts.of(TextColors.RED, "(Please ignore any messages that may immediately follow.)"));
        }

        plugin.getGame().getEventManager().post(event);
        int start = new Exception().getStackTrace().length;
        CancelReport report = new CancelReport(event, event.getCancels(), start);
        String result = report.toString();
        receiver.sendMessage(Texts.of(result));

        if (result.length() >= 500 && !isConsole) {
            receiver.sendMessage(Texts.of(TextColors.GRAY, "The report was also printed to console."));
            log.info("Event report for " + receiver.getName() + ":\n\n" + result);
        }
    }

    /**
     * Get the source of the test.
     *
     * @param sender The message sender
     * @param target The provided target
     * @param fromTarget Whether the source should be the target
     * @return The source
     * @throws CommandException Thrown if a condition is not met
     */
    private Player getSource(CommandSource sender, Player target, boolean fromTarget) throws CommandException {
        if (fromTarget) {
            return target;
        } else {
            if (sender instanceof Player) {
                return (Player) sender;
            } else {
                throw new CommandException(
                        "If this command is not to be used in-game, use -t to run the test from the viewpoint of the given player rather than yourself.");
            }
        }
    }

    /**
     * Find the first non-air block in a ray trace.
     *
     * @param sender The sender
     * @param target The target
     * @param fromTarget Whether the trace should originate from the target
     * @return The block found
     * @throws CommandException Throw on an incorrect parameter
     */
    private Location traceBlock(CommandSource sender, Player target, boolean fromTarget) throws CommandException {
        Player source = getSource(sender, target, fromTarget);

        // TODO sponge
//        BlockIterator it = new BlockIterator(source);
//        int i = 0;
//        while (it.hasNext() && i < MAX_TRACE_DISTANCE) {
//            Block block = it.next();
//            if (block.getType() != Material.AIR) {
//                return block;
//            }
//            i++;
//        }

        throw new CommandException("Not currently looking at a block that is close enough.");
    }

    /**
     * Find the first nearby entity in a ray trace.
     *
     * @param sender The sender
     * @param target The target
     * @param fromTarget Whether the trace should originate from the target
     * @return The entity found
     * @throws CommandException Throw on an incorrect parameter
     */
    private Entity traceEntity(CommandSource sender, Player target, boolean fromTarget) throws CommandException {
        Player source = getSource(sender, target, fromTarget);
//
//        BlockIterator it = new BlockIterator(source);
//        int i = 0;
//        while (it.hasNext() && i < MAX_TRACE_DISTANCE) {
//            Block block = it.next();
//
//            // A very in-accurate and slow search
//            Entity[] entities = block.getChunk().getEntities();
//            for (Entity entity : entities) {
//                if (!entity.equals(target) && entity.getLocation().distanceSquared(block.getLocation()) < 10) {
//                    return entity;
//                }
//            }
//
//            i++;
//        }

        throw new CommandException("Not currently looking at an entity that is close enough.");
    }

}
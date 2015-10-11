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

package com.sk89q.worldguard.sponge.event.debug;

import com.flowpowered.math.vector.Vector3d;
import java.util.Optional;
import com.sk89q.worldguard.sponge.WorldGuardPlugin;
import org.spongepowered.api.Game;
import org.spongepowered.api.entity.EntityInteractionType;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.AbstractEvent;
import org.spongepowered.api.event.entity.player.PlayerInteractEvent;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.world.Location;

import java.util.List;

public class LoggingPlayerInteractEvent extends AbstractEvent implements PlayerInteractEvent, CancelLogging {

    private final CancelLogger logger = new CancelLogger();

    public LoggingPlayerInteractEvent(Player who, EntityInteractionType action, Location clickedBlock, Direction clickedFace) {
        this.player = who;
        this.action = action;
        this.clicked = clickedBlock;
        this.face = clickedFace;
        this.game = WorldGuardPlugin.inst().getGame();
    }

    private boolean cancelled = false;
    private EntityInteractionType action;
    private Location clicked;
    private Direction face;
    private Player player;
    private Game game;

    @Override
    public List<CancelAttempt> getCancels() {
        return logger.getCancels();
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.logger.log(isCancelled(), cancel, new Exception().getStackTrace());

    }

    @Override
    public EntityInteractionType getInteractionType() {
        return action;
    }

    @Override
    public Optional<Vector3d> getClickedPosition() {
        return Optional.of(clicked.getPosition());
    }

    @Override
    public Player getEntity() {
        return player;
    }

    @Override
    public Player getUser() {
        return player;
    }

    @Override
    public Game getGame() {
        return game;
    }
}

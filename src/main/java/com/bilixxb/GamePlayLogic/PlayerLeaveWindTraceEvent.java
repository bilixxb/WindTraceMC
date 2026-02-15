package com.bilixxb.GamePlayLogic;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class PlayerLeaveWindTraceEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final WindTraceGame game;
    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }
    public PlayerLeaveWindTraceEvent(Player player,WindTraceGame game){
        this.player=player;
        this.game=game;
    }

    public WindTraceGame getGame() {
        return game;
    }

    public Player getPlayer() {
        return player;
    }
    public static HandlerList getHandlerList(){
        return handlers;
    }
}

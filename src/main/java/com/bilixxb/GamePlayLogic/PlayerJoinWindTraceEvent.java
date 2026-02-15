package com.bilixxb.GamePlayLogic;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class PlayerJoinWindTraceEvent extends Event {
    private final Player player;
    private final WindTraceGame game;
    private static final HandlerList handlers=new HandlerList();

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }
    public static HandlerList getHandlerList(){
        return handlers;
    }
    public PlayerJoinWindTraceEvent(Player player, WindTraceGame game){
        this.player=player;
        this.game=game;
    }

    public Player getPlayer() {
        return player;
    }

    public WindTraceGame getGame() {
        return game;
    }
}

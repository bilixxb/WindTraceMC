package com.bilixxb.GamePlayLogic;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class WindTraceStartEvent extends Event {
    private static final HandlerList handlers=new HandlerList();
    private final WindTraceGame game;
    public WindTraceStartEvent(WindTraceGame game){
        this.game=game;
    }
    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public WindTraceGame getGame() {
        return game;
    }
    public static HandlerList getHandlerList(){
        return handlers;
    }
}

package com.bilixxb.GamePlayLogic;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class SignalingDeviceProgressChangedEvent extends Event {
    private static final HandlerList handlers=new HandlerList();
    private final SignalingDevice signalingDevice;
    private final float progress;
    public SignalingDeviceProgressChangedEvent(SignalingDevice signalingDevice){
        this.signalingDevice=signalingDevice;
        this.progress=signalingDevice.getRepairProgress();
    }
    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public SignalingDevice getSignalingDevice() {
        return signalingDevice;
    }

    public float getProgress() {
        return progress;
    }

    public static HandlerList getHandlerList(){
        return handlers;
    }
}

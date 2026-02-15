package com.bilixxb;


import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

public class WTMap {
    private final World gameWorld;
    private final String mapName;
    private String displayName;
    private WTMapMode mode;
    private int minplayers;
    private int maxplayers;
    private int hunteramount;
    private List<Block> signalingDevices;
    private Location center;
    private Location cage;
    private List<Material> disguiseableBlocks=new ArrayList<>();
    public WTMap(World gameWorld,String mapName,String displayName,WTMapMode mode,int minplayers,int maxplayers,int hunteramount,List<Block> signalingDevices,Location center,Location cage){
        this.gameWorld=gameWorld;
        this.mapName=mapName;
        this.displayName=displayName;
        this.mode=mode;
        this.minplayers=minplayers;
        this.maxplayers=maxplayers;
        this.hunteramount=hunteramount;
        this.signalingDevices=signalingDevices;
        this.center=center;
        this.cage=cage;
    }
    public String getMapName(){return mapName;}
    public String getDisplayName(){return displayName;}
    public WTMapMode getMode(){return mode;}
    public int getMinplayers(){return minplayers;}
    public int getMaxplayers(){return maxplayers;}
    public int getHunteramount(){return hunteramount;}
    public Boolean isSignalingDevice(Block block){return signalingDevices.contains(block);}
    public Location getCenter(){
        return center;
    }

    public List<Block> getSignalingDevices() {
        return signalingDevices;
    }

    public World getGameWorld() {
        return gameWorld;
    }
    public void addSignalingDevice(Block block){
        signalingDevices.add(block);
    }
    public void removeSignalingDevice(Block block){
        signalingDevices.remove(block);
    }

    public void setMode(WTMapMode mode) {
        this.mode = mode;
    }

    public void setCenter(Location center) {
        this.center = center;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Boolean setHunteramount(int hunteramount) {
        if(hunteramount>maxplayers)return false;
        this.hunteramount=hunteramount;
        return true;
    }

    public Boolean setMaxplayers(int maxplayers) {
        if(maxplayers<minplayers)return false;
        this.maxplayers = maxplayers;
        return true;
    }

    public Boolean setMinplayers(int minplayers) {
        if(minplayers>maxplayers)return false;
        this.minplayers = minplayers;
        return true;
    }

    public Location getCage() {
        return cage;
    }

    public void setCage(Location cage) {
        this.cage = cage;
    }
    public void addADisguiseableBlock(Material block){
        disguiseableBlocks.add(block);
    }
    public void removeADisguiseableBlock(Material material){
        disguiseableBlocks.remove(material);
    }

    public List<Material> getDisguiseableBlocks() {
        return disguiseableBlocks;
    }
}

package com.bilixxb.GamePlayLogic;

import com.bilixxb.WTMapMode;
import com.bilixxb.WindTraceMC;
import me.filoghost.holographicdisplays.api.hologram.Hologram;
import me.filoghost.holographicdisplays.api.hologram.line.HologramLine;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Campfire;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public class SignalingDevice {
    private final WindTraceMC plugin;
    private final Block signalingDevice;
    private final Hologram hologram;
    private boolean isRepaired=false;
    private float repairProgress=0;//单位为1
    private final int repairSec; //修复这台发信机总共需要的时间（单位为秒）
    private final float defaultRepairMultiplier; //一个人每秒修复的进度(单位为百分比)
    private List<Player> inRepairingPlayers=new ArrayList<>();
    private BukkitRunnable perSecEvent;
    private BukkitRunnable resetTask;
    Boolean reached30=false;
    Boolean reached60=false;
    public SignalingDevice(Hologram hologram, Block device,int repairSec, WindTraceMC plugin){
        this.plugin=plugin;
        this.signalingDevice=device;
        this.hologram=hologram;
        this.repairSec=repairSec;
        this.defaultRepairMultiplier=1/repairSec;
    }

    public float getRepairProgress() {
        return repairProgress;
    }

    public void setRepairProgress(float repairProgress, WTMapMode mode) {
        this.repairProgress = Math.max(0, Math.min(100, repairProgress));
        Bukkit.getPluginManager().callEvent(new SignalingDeviceProgressChangedEvent(this));
        if (this.repairProgress >= 100.0f) {
            this.isRepaired = true;
            // 停止计时器
            if(perSecEvent != null){
                perSecEvent.cancel();
                perSecEvent = null;
            }
            // 强制设置为100%
            this.repairProgress = 100.0f;
            // 更新全息图
            String repairedText = plugin.getLocalizedText("signalingDevices." + mode.name() + ".repaired");
            setHologram(repairedText);
            inRepairingPlayers.forEach(player -> {
                player.playSound(player, Sound.BLOCK_BEACON_ACTIVATE,1,1);
                player.sendActionBar(plugin.getLocalizedText("signalingDevices." + mode.name() + ".repaired"));
            });
            inRepairingPlayers.clear();
            if(mode==WTMapMode.WINTER){
                resetTask = new BukkitRunnable() {
                    @Override
                    public void run() {
                        // 检查全息图是否仍有效
                        if (hologram == null) return;
                        SignalingDevice.this.repairProgress = 0f;
                        SignalingDevice.this.isRepaired=false;
                        String notRepairedText = plugin.getLocalizedText("signalingDevices." + mode.name() + ".notRepaired");
                        setHologram(notRepairedText);
                        reached30=false;
                        reached60=false;
                        Block block = signalingDevice;
                        Material type = signalingDevice.getType();
                        if (type == Material.CAMPFIRE || type == Material.SOUL_CAMPFIRE) {
                            Campfire campfire = (Campfire) block.getState();
                            Lightable lightable = (Lightable) campfire.getBlockData();
                            if (!lightable.isLit()) {
                                lightable.setLit(false);
                                campfire.setBlockData(lightable);
                                campfire.update();
                            }
                        }
                    }
                };
                resetTask.runTaskLater(plugin,600L);
            }
        }
    }

    public Block getSignalingDevice() {
        return signalingDevice;
    }

    public List<Player> getInRepairingPlayers() {
        return inRepairingPlayers;
    }

    public void addInRepairingPlayer(Player player){
        if(!inRepairingPlayers.contains(player)) inRepairingPlayers.add(player);
    }
    public void removeInRepairingPlayer(Player player){
        inRepairingPlayers.remove(player);
    }

    public void generalAddRepairProgress(WTMapMode mode){
        setRepairProgress(this.repairProgress+getProgressPerSec(),mode);
    }
    public Boolean isRepaired(){
        return isRepaired;
    }
    public void setHologram(String string) {
        if (hologram == null || hologram.isDeleted()) return; // 关键修复
        hologram.getLines().clear();
        hologram.getLines().appendText(string);
    }
    public float getProgressPerSec(){
        int playerCount = inRepairingPlayers.size();
        if (playerCount == 0) return 0;

        // 基础每秒修复百分比 = 100% / 总修复时间
        float baseProgressPerSec = 100.0f / repairSec;

        // 效率乘数
        float efficiencyMultiplier = 1.0f + (playerCount - 1) * 0.3f;

        float result = baseProgressPerSec * efficiencyMultiplier;
        return result;
    }
    private String progress(float progress){
        int p = (int) Math.floor(progress/10);
        String s = "§b";
        int i;
        for(i = 0; i < p; i++){
            s =s + "▮";
        }
        s=s+"§7";
        for(; i < 10; i++){
            s = s + "▮";
        }
        return s;
    }
    public void startRepairing(WTMapMode mode){
        // 修复：检查是否已经有任务在运行
        if(perSecEvent != null) {

            return;
        }

        perSecEvent=new BukkitRunnable() {
            @Override
            public void run() {
                // 修复：添加空值检查
                if(plugin == null) {
                    this.cancel();
                    return;
                }

                generalAddRepairProgress(mode);
                if(repairProgress<100.0) {
                    setHologram(plugin.getLocalizedText("signalingDevices."+mode.name()+".repairing").replace(
                        "{progress}",progress(repairProgress)).replace(
                        "{percent}",String.valueOf(repairProgress)
                        ));
                    inRepairingPlayers.forEach(player -> {
                        player.sendActionBar(plugin.getLocalizedText("signalingDevices."+mode.name()+".repairing").replace(
                                "{progress}",progress(repairProgress)).replace(
                                "{percent}",String.valueOf(repairProgress)
                        ));
                    });
                }
            }
        };
        perSecEvent.runTaskTimer(plugin,0L,20L);
    }
    public void stopRepairing(){
        if(perSecEvent != null) {
            perSecEvent.cancel();
            perSecEvent = null;
        }
    }
    public void reset(){
        if (resetTask != null) {
            resetTask.cancel();
            resetTask = null;
        }
        if(perSecEvent != null) {
            perSecEvent.cancel();
            perSecEvent = null;
        }
        if (hologram != null) {
            hologram.delete();
        }
        repairProgress = 0;
        reached30=false;
        reached60=false;
    }
}

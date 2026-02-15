package com.bilixxb;

import com.bilixxb.GamePlayLogic.WindTraceGame;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.*;

import java.util.HashMap;
import java.util.Map;

public class EditingModeListener implements Listener {
    private final WindTraceMC plugin;
    public EditingModeListener(WindTraceMC plugin){
        this.plugin=plugin;
    }
    @EventHandler
    public void playerQuitEditingRemove(PlayerQuitEvent e){
        Player player= e.getPlayer();
        Map<Player,WTMap> editingModeMap=WTCommands.editingModeMap;
        WTMap map=editingModeMap.get(player);
        plugin.saveMap(map);
        WTCommands.editingModeMap.remove(player);
        WTCommands.inSettingSignalingDevicePlayers.remove(player);

        if(plugin.getPlayerOnWhichGame(player)!=null)plugin.getPlayerOnWhichGame(player).leaveGame(player);
    }
    @EventHandler
    public void playerQuitGameWorld(PlayerChangedWorldEvent e){

        Map<Player,WTMap> editingModeMap=WTCommands.editingModeMap;
        WTMap map = editingModeMap.get(e.getPlayer());
        // 如果玩家不在编辑模式中，直接返回
        if (map == null) {
            return;
        }
        // 检查玩家是否从该地图的游戏世界离开，并且玩家当前的世界已经不是该游戏世界
        if(e.getFrom() == map.getGameWorld() && e.getPlayer().getWorld() != map.getGameWorld()){
            Player player= e.getPlayer();
            plugin.saveMap(map);
            WTCommands.editingModeMap.remove(player);
            WTCommands.inSettingSignalingDevicePlayers.remove(player);
            player.sendMessage(plugin.getLocalizedText("quitEditingMode"));
        }
        if(plugin.getPlayerOnWhichGame(e.getPlayer())!=null){
            WindTraceGame game=plugin.getPlayerOnWhichGame(e.getPlayer());
            if (game != null) {
                if(e.getFrom()==game.getPlayingOnMap().getGameWorld()
                && e.getPlayer().getWorld()!=game.getPlayingOnMap().getGameWorld()){
                    game.leaveGame(e.getPlayer());
                }
            }
        }
    }
    @EventHandler
    public void playerSetSignalingDevice(PlayerInteractEvent e){
        if(WTCommands.inSettingSignalingDevicePlayers.contains(e.getPlayer())&&e.getAction()== Action.RIGHT_CLICK_BLOCK){
            Map<Player,WTMap> editingModeMap=WTCommands.editingModeMap;
            WTMap map=editingModeMap.get(e.getPlayer());
            map.addSignalingDevice(e.getClickedBlock());
            plugin.saveMap(map);
            WTCommands.inSettingSignalingDevicePlayers.remove(e.getPlayer());
            e.getPlayer().sendMessage(plugin.getLocalizedText("successfullySetSignalingDevice"));
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void playerRemoveSignalingDevice(PlayerInteractEvent e){
        if(WTCommands.inRemovingSignalingDevicePlayers.contains(e.getPlayer())&&e.getAction()== Action.RIGHT_CLICK_BLOCK){
            Map<Player,WTMap> editingModeMap=WTCommands.editingModeMap;
            WTMap map=editingModeMap.get(e.getPlayer());
            map.removeSignalingDevice(e.getClickedBlock());
            plugin.saveMap(map);
            WTCommands.inSettingSignalingDevicePlayers.remove(e.getPlayer());
            e.getPlayer().sendMessage(plugin.getLocalizedText("successfullyRemoveSignalingDevice"));
            e.setCancelled(true);
        }
    }
    @EventHandler
    public void playerDamagedEvent(EntityDamageEvent e){
        if(!(e.getEntity() instanceof Player))return;
        Player player= (Player) e.getEntity();
        if(plugin.getPlayerOnWhichGame(player)==null)return;
        e.setCancelled(true);
        //玩家在游戏过程中不受任何伤害
    }
    @EventHandler
    public void playerDamageOtherEvent(EntityDamageByEntityEvent e){
        if(!(e.getDamager() instanceof Player))return;
        Player player=(Player) e.getDamager();
        if(plugin.getPlayerOnWhichGame(player)==null)return;
        e.setCancelled(true);
    }
    @EventHandler
    public void playerFoodLevelChangeEvent(FoodLevelChangeEvent e){
        if(plugin.getPlayerOnWhichGame((Player) e.getEntity())!=null){
            ((Player)e.getEntity()).setFoodLevel(20);
            e.setCancelled(true);
        }
    }
    @EventHandler
    public void PlayerBreakBlockEvent(BlockBreakEvent e){
        if(plugin.getPlayerOnWhichGame(e.getPlayer())!=null)e.setCancelled(true);
    }
    @EventHandler
    public void PlayerDropItemEvent(PlayerDropItemEvent e){
        if(plugin.getPlayerOnWhichGame(e.getPlayer())!=null)e.setCancelled(true);
    }
    @EventHandler
    public void PlayerPlaceEvent(BlockPlaceEvent e){
        if(plugin.getPlayerOnWhichGame(e.getPlayer())!=null)e.setCancelled(true);
    }
    @EventHandler
    public void PlayerJoinGameEvent(PlayerJoinEvent e){
        plugin.setInvis(e.getPlayer(),false);
        //本地特殊用途，发布前记得删
        //plugin.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(),"ci give wt "+e.getPlayer().getName());
        //e.getPlayer().teleport(plugin.getLobby());
    }
}

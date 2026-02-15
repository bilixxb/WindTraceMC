package com.bilixxb.GamePlayLogic;

import com.bilixxb.WTMap;
import com.bilixxb.WTMapMode;
import com.bilixxb.WindTraceMC;
import me.filoghost.holographicdisplays.api.HolographicDisplaysAPI;
import me.filoghost.holographicdisplays.api.hologram.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.SimpleDateFormat;
import java.util.*;

public class WindTraceGame {
    public int totalTime = 0;
    private WTMap playingOnMap;
    public boolean inForceStart;
    private List<Player> playing;
    private GameStatus status;
    private final WindTraceMC plugin;
    private int Timer;
    private Map<Player, Integer> hunters; // 猎人 -> 捕获人数
    private Map<Player, Integer> rebel; // 反抗者 -> 修复发信机数量
    private List<Player> eliminated = new ArrayList<>();
    private Map<Block, Hologram> hologramMap = new HashMap<>();
    public List<SignalingDevice> signalDevices;
    private final HolographicDisplaysAPI api;

    private Map<Player, Integer> eliminatedRebelRepairs = new HashMap<>();

    public WindTraceGame(WTMap playingOnMap, WindTraceMC plugin) {
        this.playingOnMap = playingOnMap;
        status = GameStatus.NotStarted;
        playing = new ArrayList<>();
        this.plugin = plugin;
        Timer = plugin.config.getInt("waitingTime");
        api = HolographicDisplaysAPI.get(plugin);
        hunters = new HashMap<>();
        rebel = new HashMap<>();
    }
    public Boolean isEliminated(Player player){
        return eliminated.contains(player);
    }

    public WTMap getPlayingOnMap() {
        return playingOnMap;
    }

    public List<Player> getPlaying() {
        return playing;
    }

    public int getCurrentPlayers() {
        return playing.size();
    }
    public List<Player> getEliminated() {
        return eliminated;
    }

    public String joinGame(Player player) {
        if (playing.size() > playingOnMap.getMaxplayers()) return "mapFull";
        if (playing.contains(player)) return "alreadyExists";
        if (status != GameStatus.NotStarted) return "alreadyStarted";
        playing.add(player);
        player.teleport(playingOnMap.getCenter());
        player.setWalkSpeed(0.2f);
        clearInventory(player);
        player.setHealth(player.getMaxHealth());
        plugin.setInvis(player,false);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 1, 100));
        playing.forEach(player1 -> player1.sendMessage(plugin.getLocalizedText("joinGame").replace("{player}", player.getName())
                .replace("{nowPlayers}", String.valueOf(playing.size())).
                replace("{maxPlayers}", String.valueOf(playingOnMap.getMaxplayers()))));
        PlayerJoinWindTraceEvent e = new PlayerJoinWindTraceEvent(player, this);
        Bukkit.getPluginManager().callEvent(e);
        player.setGameMode(GameMode.ADVENTURE);
        return "";
    }

    public Boolean leaveGame(Player player) {
        if (!playing.contains(player)) return false;
        player.setWalkSpeed(0.2f);
        playing.remove(player);
        hunters.remove(player); // 从猎人Map中移除
        rebel.remove(player); // 从反抗者Map中移除
        eliminated.remove(player);
        player.setAllowFlight(false);
        plugin.setInvis(player,false);
        playing.forEach(player1 -> player1.sendMessage(plugin.getLocalizedText("leaveGame").replace("{player}", player.getName())
                .replace("{nowPlayers}", String.valueOf(playing.size())).
                replace("{maxPlayers}", String.valueOf(playingOnMap.getMaxplayers()))));

        PlayerLeaveWindTraceEvent e = new PlayerLeaveWindTraceEvent(player, this);
        Bukkit.getPluginManager().callEvent(e);
        return true;
    }

    public GameStatus getStatus() {
        return status;
    }

    public void setStatus(GameStatus status) {
        this.status = status;
    }

    public void setPlayingOnMap(WTMap map) {
        this.playingOnMap = map;
    }

    private void clearInventory(Player player) {
        PlayerInventory inv = player.getInventory();
        for (ItemStack i : inv.getContents()) {
            if (i != null) {
                i.setAmount(0);
            }
        }
    }

    public void GameStart() {
        WindTraceStartEvent e = new WindTraceStartEvent(this);
        Bukkit.getPluginManager().callEvent(e);
        this.status = GameStatus.Preparing;
    }

    public void setTimer(int timer) {
        Timer = timer;
    }

    public int getTimer() {
        return Timer;
    }

    public void resetPreparingTimer() {
        Timer = plugin.config.getInt("waitingTime");
    }

    // 设置猎人Map
    public void setHunters(Map<Player, Integer> hunters) {
        this.hunters = hunters;
    }

    // 从玩家列表设置猎人Map（初始化时用）
    public void setHuntersFromList(List<Player> hunterList) {
        hunters.clear();
        for (Player hunter : hunterList) {
            hunters.put(hunter, 0); // 初始化捕获人数为0
        }
    }

    public Map<Player, Integer> getHunters() {
        return hunters;
    }

    // 获取猎人玩家列表（兼容旧代码）
    public List<Player> getHuntersList() {
        return new ArrayList<>(hunters.keySet());
    }

    // 设置反抗者Map
    public void setRebel(Map<Player, Integer> rebel) {
        this.rebel = rebel;
    }

    // 从玩家列表设置反抗者Map（初始化时用）
    public void setRebelFromList(List<Player> rebelList) {
        rebel.clear();
        for (Player r : rebelList) {
            rebel.put(r, 0); // 初始化修复发信机数量为0
        }
    }

    public Map<Player, Integer> getRebel() {
        return rebel;
    }

    public List<Player> getRebelList() {
        return new ArrayList<>(rebel.keySet());
    }

    // 增加猎人的捕获人数
    public void addHunterCapture(Player hunter) {
        if (hunters.containsKey(hunter)) {
            hunters.put(hunter, hunters.get(hunter) + 1);
        }
    }

    // 增加反抗者的修复发信机数量
    public void addRebelRepair(Player rebelPlayer) {
        if (rebel.containsKey(rebelPlayer)) {
            rebel.put(rebelPlayer, rebel.get(rebelPlayer) + 1);
        }
    }

    public void initializeSignalingDevices() {
        List<Block> signalingDevices = playingOnMap.getSignalingDevices();
        signalDevices = new ArrayList<>(); // 初始化列表
        hologramMap.clear(); // 清空旧的hologram
        signalingDevices.forEach(block -> {
            Location location = block.getLocation();
            location.setY(location.getY() + 1.5);
            location.setX(location.getX() + 0.5);
            location.setZ(location.getZ() + 0.5);
            hologramMap.put(block, api.createHologram(location));
            hologramMap.get(block).getLines().clear();
            if (playingOnMap.getMode() == WTMapMode.NORMAL) {
                hologramMap.get(block).getLines().appendText(plugin.getLocalizedText("signalingDevices.NORMAL.notRepaired"));
            } else {
                hologramMap.get(block).getLines().appendText(plugin.getLocalizedText("signalingDevices.WINTER.notRepaired"));
            }
            int repairTime = (playingOnMap.getMode() == WTMapMode.NORMAL ?
                    plugin.config.getInt("signalingDevice.repairTime.NORMAL", 80) :
                    plugin.config.getInt("signalingDevice.repairTime.WINTER", 100));
            signalDevices.add(new SignalingDevice(hologramMap.get(block), block, repairTime, plugin));
        });
    }

    public String getIdentity(Player player) {
        if (status == GameStatus.NotStarted) {
            return "null";  // 等待阶段还没有分配身份
        }

        if (status == GameStatus.Preparing) {
            // 准备阶段身份应该已经分配了
            if (hunters.containsKey(player)) return "hunter";
            if (rebel.containsKey(player)) return "rebel";
            return "null";
        }

        if (hunters == null || rebel == null) return "null";
        if (hunters.containsKey(player)) return "hunter";
        else if (rebel.containsKey(player)) return "rebel";
        else if (eliminated.contains(player)) return "eliminated";
        else return "null";
    }

    public SignalingDevice getSignalingDeviceObject(Block block) {
        if (signalDevices == null || block == null) return null;

        for (SignalingDevice signalDevice : signalDevices) {
            if (signalDevice != null && signalDevice.getSignalingDevice() != null) {
                Block deviceBlock = signalDevice.getSignalingDevice();
                if (deviceBlock.getLocation().equals(block.getLocation())) {
                    return signalDevice;
                }
            }
        }
        return null;
    }

    public SignalingDevice getSignalingDeviceObject(Player player) {
        // 修复：添加空值检查
        if (signalDevices == null || player == null) return null;

        for (SignalingDevice signalDevice : signalDevices) {
            if (signalDevice != null && signalDevice.getInRepairingPlayers() != null
                    && signalDevice.getInRepairingPlayers().contains(player)) {
                return signalDevice;
            }
        }
        return null;
    }

    public void EndGame(String whoWins) {
        if (!whoWins.equals("none")) {
            playing.forEach(player -> {
                player.setAllowFlight(false);
                player.setWalkSpeed(0.2f);
                if (getIdentity(player).equals("hunter") || getIdentity(player).equals("rebel")||(playing.contains(player)&&eliminated.contains(player))) {
                    String totalTime1 = convertSecondsToTime(totalTime);
                    player.sendTitle(plugin.getLocalizedText("titles." + whoWins),
                            plugin.getLocalizedText("titles.subtitle").replace("{totalTime}", totalTime1),
                            20, 160, 20);
                    player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP,1,(float) 0.1);
                    String result = getIdentity(player).equals(whoWins) ? plugin.getLocalizedText("stats.results.win") : plugin.getLocalizedText("stats.results.lose");
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
                    String date = sdf.format(new Date());
                    if (playingOnMap.getMode() == WTMapMode.NORMAL) {
                        if (getIdentity(player).equals("hunter")) {
                            // 获取猎人捕获人数
                            int captures = hunters.getOrDefault(player, 0);
                            plugin.getLocalizedList("stats.afterGame.hunter").forEach(str -> player.sendMessage(str.replace("{result}", result)
                                    .replace("{date}", date).replace("{amount}", String.valueOf(captures))));
                        } else if (getIdentity(player).equals("rebel")||eliminated.contains(player)) {
                            // 获取反抗者修复发信机数量
                            int repairs = rebel.getOrDefault(player, 0);
                            plugin.getLocalizedList("stats.afterGame.rebel").forEach(str -> player.sendMessage(str.replace("{result}", result)
                                    .replace("{date}", date).replace("{amount}", String.valueOf(repairs))));
                        }
                    }
                    if (playingOnMap.getMode() == WTMapMode.WINTER) {
                        if (getIdentity(player).equals("hunter")) {
                            // 获取猎人捕获人数
                            int captures = hunters.getOrDefault(player, 0);
                            plugin.getLocalizedList("stats.afterGame.hunter").forEach(str ->player.sendMessage(str.replace("{result}", result)
                                    .replace("{date}", date).replace("{amount}", String.valueOf(captures))) );
                        } else if (getIdentity(player).equals("rebel")||eliminated.contains(player)) {
                            // 获取反抗者修复发信机数量
                            int repairs = rebel.getOrDefault(player, 0);
                            plugin.getLocalizedList("stats.afterGame.rebelWINTER").forEach(str -> player.sendMessage(str.replace("{result}", result)
                                    .replace("{date}", date).replace("{amount}", String.valueOf(repairs))));
                        }
                    }
                }
            });
            for (Player player : playing) {
                String identity = null;
                int amount = 0;

                if (hunters.containsKey(player)) {
                    identity = "hunter";
                    amount = hunters.get(player);
                } else if (rebel.containsKey(player)) {
                    identity = "rebel";
                    amount = rebel.get(player);
                } else if (eliminated.contains(player)) {
                    identity = "rebel";
                    amount = eliminatedRebelRepairs.getOrDefault(player, 0);
                }

                if (identity != null) {
                    plugin.addPlayerStatics(player, playingOnMap.getMode(), identity, amount);
                }
            }
            totalTime=0;
            eliminatedRebelRepairs.clear();
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                // 创建副本，防止原列表在迭代中被修改
                List<Player> playersToProcess = new ArrayList<>(playing);
                playersToProcess.forEach(player -> {
                    player.teleport(plugin.getLobby());
                    plugin.setInvis(player, false);
                    player.setFlying(false);
                    player.setAllowFlight(false);
                });
                playing.clear(); // 此时可以安全清空原列表
            }
        }.runTaskLater(plugin, 200L);
        hunters.clear();
        rebel.clear();
        eliminated.clear();
        signalDevices.forEach(SignalingDevice::reset);
        status=GameStatus.NotStarted;
        setTimer(plugin.config.getInt("waitingTime"));
    }

    public String convertSecondsToTime(int seconds) {
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;

        return String.format("%02d:%02d", minutes, secs);
    }

    public int getRepairedSignalingDeviceCount() {
        if (signalDevices == null || signalDevices.isEmpty()) {
            return 0;
        }

        int repairedCount = 0;
        for (SignalingDevice device : signalDevices) {
            if (device != null && device.isRepaired()) {
                repairedCount++;
            }
        }
        return repairedCount;
    }

    public int getTotalSignalingDeviceCount() {
        if (signalDevices == null) {
            return 0;
        }
        return signalDevices.size();
    }

    public int getUnrepairedSignalingDeviceCount() {
        return getTotalSignalingDeviceCount() - getRepairedSignalingDeviceCount();
    }

    public boolean areAllSignalingDevicesRepaired() {
        return getRepairedSignalingDeviceCount() >= getTotalSignalingDeviceCount();
    }
    public boolean eliminate(Player player){
        if(!playing.contains(player))return false;
        if(!getRebelList().contains(player))return false;

        // 保存该玩家的修复次数
        int repairs = rebel.getOrDefault(player, 0);
        eliminatedRebelRepairs.put(player, repairs);

        rebel.remove(player);
        eliminated.add(player);

        player.sendTitle(plugin.getLocalizedText("eliminated.main"), plugin.getLocalizedText("eliminated.sub"), 0, 80, 0);
        clearInventory(player);
        plugin.setInvis(player, true);
        player.setAllowFlight(true);
        player.setFlying(true);

        return true;
    }
    public List<SignalingDevice> getSignalDevices() {
        return signalDevices; // 假设 signalDevices 是成员变量
    }
}
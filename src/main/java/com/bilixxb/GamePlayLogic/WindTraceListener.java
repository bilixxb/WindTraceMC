package com.bilixxb.GamePlayLogic;

import com.bilixxb.WTMapMode;
import com.bilixxb.WindTraceMC;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Campfire;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class WindTraceListener implements Listener {
    WindTraceGame game;
    WindTraceMC plugin;
    Boolean waitingCountingDown;
    private Visuals visuals;

    // 添加这个字段来跟踪计时器任务
    private BukkitRunnable waitingTimerTask;
    private BukkitRunnable gameTimerTask;
    List<Player> notCaptureablePlayers = new ArrayList<>();
    List<Player> cantMovePlayers = new ArrayList<>();
    Map<String, SkillCooldown> processingCooldown = new HashMap<String, SkillCooldown>();
    BukkitRunnable processCooldownRunnable;
    private Disguise disguise;

    // 定义大招物品的NamespacedKey常量（统一大小写，避免错误）
    private final NamespacedKey Q_SKILL_KEY;

    private Map<UUID,Integer> coldValue=new HashMap<>();
    private List<Player> heatedPlayers=new ArrayList<>();
    private List<UUID> hintedPlayersUUID=new ArrayList<>();
    private List<Player> frozenPlayers = new ArrayList<>();

    public WindTraceListener(WindTraceMC plugin) {
        this.plugin = plugin;
        this.visuals = new Visuals(plugin);
        this.Q_SKILL_KEY = new NamespacedKey(plugin, "WindTrace.QSkill"); // 统一Key
    }

    @EventHandler
    public void playerJoinEvent(PlayerJoinWindTraceEvent e) {
        this.game = e.getGame(); // 保存当前游戏引用

        if (e.getGame().getStatus() != GameStatus.NotStarted) return;

        // 为加入的玩家显示计分板
        visuals.showScoreboardFor(e.getPlayer(), e.getGame());

        if (e.getGame().inForceStart || e.getGame().getCurrentPlayers() >= e.getGame().getPlayingOnMap().getMinplayers()) {
            if (waitingCountingDown == null || !waitingCountingDown) {
                waitingCountingDown = true;
                e.getGame().getPlaying().forEach(player -> player.sendMessage(plugin.getLocalizedText("gameGoingToStart").replace("{time}", String.valueOf(e.getGame().getTimer()))));

                // 取消可能存在的旧计时器
                if (waitingTimerTask != null && !waitingTimerTask.isCancelled()) {
                    waitingTimerTask.cancel();
                }

                waitingTimerTask = new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!waitingCountingDown || e.getGame().getStatus() != GameStatus.NotStarted) {
                            this.cancel();
                            return;
                        }

                        e.getGame().setTimer(e.getGame().getTimer() - 1);
                        visuals.showScoreboardForAll(e.getGame()); // 更新所有玩家的计分板

                        if (e.getGame().getTimer() == 5 || e.getGame().getTimer() == 10 || e.getGame().getTimer() == 3 || e.getGame().getTimer() == 2 || e.getGame().getTimer() == 1) {
                            e.getGame().getPlaying().forEach(player -> {
                                player.sendMessage(plugin.getLocalizedText("gameGoingToStart").replace("{time}", String.valueOf(e.getGame().getTimer())));
                                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 1, 1);
                            });
                        }

                        if (e.getGame().getTimer() < 0) {
                            e.getGame().GameStart();
                            if(e.getGame().getPlayingOnMap().getMode()==WTMapMode.WINTER){
                                e.getGame().getPlaying().forEach(player -> {
                                    coldValue.put(player.getUniqueId(), 0);
                                    player.setTotalExperience(0);
                                });
                            }
                            this.cancel();
                        }
                    }
                };
                waitingTimerTask.runTaskTimer(plugin, 0L, 20L);
            }
        }
    }

    @EventHandler
    public void playerQuitEvent(PlayerLeaveWindTraceEvent e) {
        this.game = e.getGame(); // 保存当前游戏引用

        if (e.getGame().getStatus() != GameStatus.NotStarted) return;
        visuals.removeScoreboard(e.getPlayer());
        if (disguise != null) {
            disguise.cleanupPlayer(e.getPlayer());
        }
        GameStatus status = e.getGame().getStatus();
        if (status == GameStatus.NotStarted) {
            if (e.getGame().getCurrentPlayers() < e.getGame().getPlayingOnMap().getMinplayers()) {
                waitingCountingDown = false;
                e.getGame().resetPreparingTimer();
                e.getGame().getPlaying().forEach(player -> {
                    player.sendMessage(plugin.getLocalizedText("notEnoughPlayers"));
                });

                // 取消计时器
                if (waitingTimerTask != null && !waitingTimerTask.isCancelled()) {
                    waitingTimerTask.cancel();
                    waitingTimerTask = null;
                }
            }
        }
        if (status == GameStatus.Preparing || status == GameStatus.inGaming) {
            if(e.getGame().getPlayingOnMap().getMode()==WTMapMode.WINTER){
                coldValue.remove(e.getPlayer().getUniqueId());
            }
            if (e.getGame().getRebelList().isEmpty()) {
                EndGame(e.getGame(), "hunter");
            }
            if (e.getGame().getHuntersList().isEmpty()) {
                EndGame(e.getGame(), "rebel");
            }
        }
    }

    @EventHandler
    public void gameStartEvent(WindTraceStartEvent e) {
        this.game = e.getGame(); // 保存当前游戏引用

        e.getGame().setTimer(plugin.getConfig().getInt("preparationTime"));
        e.getGame().setStatus(GameStatus.Preparing);

        List<Player> allPlayers = e.getGame().getPlaying();
        List<Player> huntersList = new ArrayList<>();
        List<Player> rebelList = new ArrayList<>();

        List<Player> availablePlayers = new ArrayList<>(allPlayers);

        int hunterAmount = e.getGame().getPlayingOnMap().getHunteramount();

        int actualHunterAmount = Math.min(hunterAmount, allPlayers.size());

        // 随机选择猎人
        Random random = new Random();
        for (int i = 0; i < actualHunterAmount; i++) {
            if (availablePlayers.isEmpty()) {
                break;
            }

            int randomIndex = random.nextInt(availablePlayers.size());
            Player selectedHunter = availablePlayers.remove(randomIndex);
            huntersList.add(selectedHunter);
        }

        rebelList.addAll(availablePlayers);

        for (Player hunter : huntersList) {
            plugin.getLocalizedList("hunter").forEach(hunter::sendMessage);
            hunter.teleport(e.getGame().getPlayingOnMap().getCage());
            PotionEffect b = new PotionEffect(PotionEffectType.BLINDNESS, plugin.config.getInt("preparationTime") * 20, 1);
            hunter.addPotionEffect(b);
            heatedPlayers.add(hunter);
        }

        // 给反抗者玩家发送消息
        for (Player rebelPlayer : rebelList) {
            if (e.getGame().getPlayingOnMap().getMode() == WTMapMode.NORMAL)
                plugin.getLocalizedList("rebelNormal").forEach(rebelPlayer::sendMessage);
            else plugin.getLocalizedList("rebelWinter").forEach(rebelPlayer::sendMessage);
        }

        // 使用新的方法设置猎人和反抗者Map
        e.getGame().setHuntersFromList(huntersList);
        e.getGame().setRebelFromList(rebelList);
        // 在游戏开始时为所有玩家显示计分板
        visuals.showScoreboardForAll(e.getGame());
        e.getGame().getRebelList().forEach(p -> p.teleport(e.getGame().getPlayingOnMap().getCenter()));
        initializeGame(e);
        e.getGame().getHuntersList().forEach(player -> {
            player.getInventory().setItem(0, getSkill("hunter", 1, false));
            player.getInventory().setItem(1, getSkill("hunter", 2, false));
            player.getInventory().setItem(2, getSkill("hunter", 3, false));
        });
        e.getGame().getRebelList().forEach(player -> {
            player.getInventory().setItem(0, getSkill("rebel", 1, false));
            player.getInventory().setItem(1, getSkill("rebel", 2, false));
            player.getInventory().setItem(2, getSkill("rebel", 3, false));
        });
    }
    private int getColdValue(Player player){
        UUID uuid=player.getUniqueId();
        return coldValue.getOrDefault(uuid, 0);
    }

    private void initializeGame(WindTraceStartEvent e) {
        e.getGame().setStatus(GameStatus.Preparing);
        e.getGame().setTimer(plugin.config.getInt("preparationTime"));

        // 为所有玩家更新计分板
        visuals.showScoreboardForAll(e.getGame());

        e.getGame().getPlaying().forEach(player ->
                player.sendMessage(plugin.getLocalizedText("hunterGoingToRelease")
                        .replace("{time}", String.valueOf(e.getGame().getTimer()))));
        e.getGame().initializeSignalingDevices();

        // 取消可能存在的旧游戏计时器
        if (gameTimerTask != null && !gameTimerTask.isCancelled()) {
            gameTimerTask.cancel();
        }

        gameTimerTask = new BukkitRunnable() {
            WindTraceGame game = e.getGame();

            @Override
            public void run() {
                // 更新计分板
                visuals.updateScoreboardForAll(game);
                if (game.getStatus() == GameStatus.inGaming) {
                    game.totalTime += 1;
                    //游戏内事件处理
                    if (game.getPlayingOnMap().getMode() == WTMapMode.WINTER) {
                        for (Player coldPlayer : game.getPlaying()) {
                            if (frozenPlayers.contains(coldPlayer)) {
                                // 冰冻状态，保持寒冷值为100显示
                                setEXP(coldPlayer, 100);
                                continue;
                            }
                            if (!heatedPlayers.contains(coldPlayer)) {
                                generalAddColdValue(coldPlayer);
                                if ((getColdValue(coldPlayer) >= 70 && !hintedPlayersUUID.contains(coldPlayer.getUniqueId())) || getColdValue(coldPlayer) >= 90) {
                                    coldPlayer.sendTitle("", plugin.getLocalizedText("cold"), 5, 100, 5);
                                    hintedPlayersUUID.add(coldPlayer.getUniqueId());
                                }
                            } else {
                                addColdValue(coldPlayer, -20);
                            }
                            if (getColdValue(coldPlayer) == 100) {
                                freeze(coldPlayer, 10);
                                // 移除原有的加热添加和延迟移除
                            }
                        }
                    }
                    if (game.totalTime == 110) {
                        //110秒时掉落眷顾秘技
                        game.getPlaying().forEach(player -> {
                            player.sendTitle("", plugin.getLocalizedText("QSkillsDropped"), 5, 100, 5);
                            player.playSound(player, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1, 1);
                        });
                        ItemStack QSkill = new ItemStack(Material.ENDER_PEARL);
                        ItemMeta QSkillMeta = QSkill.getItemMeta();
                        QSkillMeta.setUnbreakable(true);
                        QSkillMeta.getPersistentDataContainer().set(Q_SKILL_KEY, PersistentDataType.BOOLEAN, true);
                        QSkill.setItemMeta(QSkillMeta);
                        dropItemAt(QSkill, game.getPlayingOnMap().getCenter());

                    }
                    if(plugin.config.getInt("gameTime")- game.totalTime==60){
                        game.getPlaying().forEach(player -> {
                            player.sendTitle(plugin.getLocalizedText("finalSeconds.main"), plugin.getLocalizedText("finalSeconds.sub"),20,100,20);
                        });
                        game.getHuntersList().forEach(player -> player.setWalkSpeed(0.8F));
                    }
                }
                if (game.getTimer() < 0) {
                    if (game.getStatus() == GameStatus.Preparing) {
                        game.setStatus(GameStatus.inGaming);
                        game.setTimer(plugin.config.getInt("gameTime"));
                        // 游戏状态改变，重新显示计分板
                        visuals.showScoreboardForAll(game);

                        game.getPlaying().forEach(player ->
                                player.sendMessage(plugin.getLocalizedText("hunterReleased")));
                        // 修改：使用getHuntersList()获取猎人玩家列表
                        game.getHuntersList().forEach(hunter ->
                                hunter.teleport(game.getPlayingOnMap().getCenter()));
                        e.getGame().getHuntersList().forEach(player -> {
                            player.setWalkSpeed(0.4f);
                            ItemStack stack = getSkill("hunter", 1, true);
                            stack.setAmount(3);
                            player.getInventory().setItem(0, stack);
                            stack = getSkill("hunter", 2, true);
                            stack.setAmount(3);
                            player.getInventory().setItem(1, stack);
                            player.getInventory().setItem(2, getSkill("hunter", 3, false));
                        });
                        e.getGame().getRebelList().forEach(player -> {
                            player.getInventory().setItem(0, getSkill("rebel", 1, true));
                            player.getInventory().setItem(1, getSkill("rebel", 2, true));
                            player.getInventory().setItem(2, getSkill("rebel", 3, false));
                        });
                    } else if (game.getStatus() == GameStatus.inGaming) {
                        EndGame(game, "rebel");//游戏自然结束，游侠胜利
                        game.getPlaying().forEach(visuals::removeScoreboard);
                        this.cancel();
                    }
                }
                if (game.areAllSignalingDevicesRepaired() && game.getPlayingOnMap().getMode() == WTMapMode.NORMAL) {
                    EndGame(game, "rebel");//所有发信机已被修复，游侠胜利（普通模式）
                    game.getPlaying().forEach(visuals::removeScoreboard);
                    this.cancel();
                }
                game.setTimer(game.getTimer() - 1);
            }
        };
        gameTimerTask.runTaskTimer(plugin, 0, 20);

        disguise = new Disguise(plugin, game.getPlayingOnMap().getDisguiseableBlocks());
    }

    private void dropItemAt(ItemStack qSkill, Location center) {
        World world = center.getWorld();
        if (world == null) return;

        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();

        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;

                // 获取该x,z列中的最高非空气方块
                Block highestBlock = null;

                // 从中心Y+8向下搜索到中心Y-8，找到最高的非空气方块
                for (int dy = 8; dy >= -8; dy--) {
                    int y = centerY + dy;

                    // 检查y坐标是否有效
                    if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
                        continue;
                    }

                    Block block = world.getBlockAt(x, y, z);

                    // 如果方块是固体且不是空气，记录为候选
                    if (!block.isEmpty() && !block.isPassable() && block.getType().isSolid()) {
                        highestBlock = block;
                        break;
                    }
                }

                // 如果找到了符合条件的方块，在该方块上方掉落物品
                if (highestBlock != null) {
                    Location dropLocation = highestBlock.getLocation().add(0.5, 1.0, 0.5);
                    world.dropItemNaturally(dropLocation, qSkill);
                    return; // 只在第一个找到的位置掉落
                }
            }
        }

        // 如果没有找到符合条件的方块，在中心位置掉落
        world.dropItemNaturally(center, qSkill);
    }

    @EventHandler
    public void playerStartToRepairSignalingDeviceEvent(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (game == null || game.getStatus() != GameStatus.inGaming) return;
        Block block = e.getClickedBlock();
        Player player = e.getPlayer();
        if (game.getIdentity(player).equals("hunter")) return;
        if (!game.getPlayingOnMap().isSignalingDevice(block)) return;

        SignalingDevice device = game.getSignalingDeviceObject(block);
        if (device == null) return;
        SignalingDevice currentDevice = game.getSignalingDeviceObject(player);
        if (currentDevice != null && currentDevice != device) {
            currentDevice.removeInRepairingPlayer(player);
            if (currentDevice.getInRepairingPlayers().isEmpty()) {
                currentDevice.stopRepairing();
            }
        }

        device.addInRepairingPlayer(player);
        device.startRepairing(game.getPlayingOnMap().getMode());
    }

    @EventHandler
    public void playerStopRepairingSignalingDeviceEvent(PlayerMoveEvent e) {
        if (game == null || game.getStatus() != GameStatus.inGaming) return;
        if (game != null && disguise != null) {
            disguise.handlePlayerMove(e.getPlayer(), e.getFrom(), e.getTo());
        }
        Player player = e.getPlayer();
        if (cantMovePlayers.contains(player)) e.setCancelled(true);
        SignalingDevice device = game.getSignalingDeviceObject(player);

        if (device != null) {
            if (!isPlayerNearBy(player, device.getSignalingDevice(), 4)) {
                // 移除玩家，并检查是否需要停止计时器
                device.removeInRepairingPlayer(player);
                if (device.getInRepairingPlayers().isEmpty()) {
                    device.stopRepairing();
                }
                player.sendActionBar(plugin.getLocalizedText("repairCancel"));
            }
        }
        if (game.getPlayingOnMap().getMode() == WTMapMode.WINTER) {
            boolean nearAnyRepaired = false;
            for (SignalingDevice sd : game.getSignalDevices()) {
                if (sd.isRepaired() && isPlayerNearBy(player, sd.getSignalingDevice(), 6)) {
                    nearAnyRepaired = true;
                    break;
                }
            }
            if(game.getRebelList().contains(player)){
                if (!frozenPlayers.contains(player)) { // 非冰冻玩家才允许加入加热列表
                    if (nearAnyRepaired) {
                        heatedPlayers.add(player);
                    } else {
                        heatedPlayers.remove(player);
                    }
                } else {
                    heatedPlayers.remove(player); // 冰冻玩家强制移出加热列表
                }
            }
        }
    }


    private boolean isPlayerNearBy(Player player, Block block, int r) {
        Location blockCenter = block.getLocation().add(0.5, 0.5, 0.5);
        Location playerLoc = player.getLocation();

        double dx = blockCenter.getX() - playerLoc.getX();
        double dy = blockCenter.getY() - playerLoc.getY();
        double dz = blockCenter.getZ() - playerLoc.getZ();

        int rSquared = r * r;
        return dx * dx + dy * dy + dz * dz <= rSquared;
    }

    private void EndGame(WindTraceGame game, String whoWins) {
        // 1. 清理玩家伪装
        if (disguise != null) {
            game.getPlaying().forEach(player -> disguise.undisguisePlayer(player));
            disguise = null;
        }

        // 2. 清理大招相关物品
        cleanupQSkillItems(game);

        // 3. 清理玩家状态列表（防止内存泄漏）
        notCaptureablePlayers.clear();
        cantMovePlayers.clear();
        hintedPlayersUUID.clear();
        coldValue.clear();
        heatedPlayers.clear();
        frozenPlayers.clear();

        // 4. 结束游戏逻辑
        game.EndGame(whoWins);
        this.waitingCountingDown = false;

        // 5. 取消所有计时器
        if (waitingTimerTask != null && !waitingTimerTask.isCancelled()) {
            waitingTimerTask.cancel();
            waitingTimerTask = null;
        }
        if (gameTimerTask != null && !gameTimerTask.isCancelled()) {
            gameTimerTask.cancel();
            gameTimerTask = null;
        }
        if (processCooldownRunnable != null) {
            processCooldownRunnable.cancel();
            processCooldownRunnable = null;
        }

        // 6. 移除计分板
        if (visuals != null && game != null && game.getPlaying() != null) {
            game.getPlaying().forEach(visuals::removeScoreboard);
        }

        // 7. 重置信号设备
        if (game != null && game.signalDevices != null) {
            for (SignalingDevice device : game.signalDevices) {
                if(game.getPlayingOnMap().getMode()==WTMapMode.WINTER){
                    Block block= device.getSignalingDevice();
                    Material type=block.getType();
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
                if (device != null) {
                    device.stopRepairing();
                    device.reset();
                }
            }
        }

        // 8. 清空冷却队列
        processingCooldown.clear();
    }

    /**
     * 清理所有大招相关物品（玩家物品栏+世界掉落物）
     */
    private void cleanupQSkillItems(WindTraceGame game) {
        if (game == null || game.getPlayingOnMap() == null || game.getPlayingOnMap().getCenter() == null) {
            return;
        }

        World gameWorld = game.getPlayingOnMap().getCenter().getWorld();
        if (gameWorld == null) {
            return;
        }

        // 1. 清理所有玩家物品栏中的大招激活物品（末影珍珠）
        if (game.getPlaying() != null) {
            for (Player player : game.getPlaying()) {
                if (player == null || player.getInventory() == null) continue;

                // 遍历玩家物品栏，移除带Q_SKILL_KEY的末影珍珠
                for (int i = 0; i < player.getInventory().getSize(); i++) {
                    ItemStack item = player.getInventory().getItem(i);
                    if (isQSkillItem(item)) {
                        player.getInventory().setItem(i, null);
                    }
                }

                // 清理玩家副手
                ItemStack offHandItem = player.getInventory().getItemInOffHand();
                if (isQSkillItem(offHandItem)) {
                    player.getInventory().setItemInOffHand(null);
                }
            }
        }

        // 2. 清理世界中掉落的大招物品实体
        for (Item itemEntity : gameWorld.getEntitiesByClass(Item.class)) {
            if (itemEntity == null || itemEntity.getItemStack() == null) continue;
            if (isQSkillItem(itemEntity.getItemStack())) {
                itemEntity.remove(); // 移除掉落的大招物品
            }
        }
    }

    /**
     * 判断物品是否是大招激活物品
     */
    private boolean isQSkillItem(ItemStack item) {
        if (item == null || item.getType() != Material.ENDER_PEARL) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        return meta.getPersistentDataContainer().has(Q_SKILL_KEY, PersistentDataType.BOOLEAN)
                && Boolean.TRUE.equals(meta.getPersistentDataContainer().get(Q_SKILL_KEY, PersistentDataType.BOOLEAN));
    }

    private ItemStack getSkill(String identity, int i, boolean available) {
        ItemStack result;
        if (available) result = new ItemStack(Material.IRON_SWORD);
        else result = new ItemStack(Material.WOODEN_SWORD);
        ItemMeta meta = result.getItemMeta();
        NamespacedKey key = new NamespacedKey(plugin, "skillName");
        NamespacedKey isAvailable = new NamespacedKey(plugin, "available");
        if (identity.equals("hunter")) {
            if (i == 1) {
                meta.setDisplayName(plugin.getLocalizedText("Skills.hunter.traceA.displayName"));
                meta.setLore(plugin.getLocalizedList("Skills.hunter.traceA.Lore"));
                meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "hunter.traceA");
            }
            if (i == 2) {
                meta.setDisplayName(plugin.getLocalizedText("Skills.hunter.captureE.displayName"));
                meta.setLore(plugin.getLocalizedList("Skills.hunter.captureE.Lore"));
                meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "hunter.captureE");
            }
            if (i == 3) {
                meta.setDisplayName(plugin.getLocalizedText("Skills.hunter.freezeQ.displayName"));
                meta.setLore(plugin.getLocalizedList("Skills.hunter.freezeQ.Lore"));
                meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "hunter.freezeQ");
            }
        } else if (identity.equals("rebel")) {
            if (i == 1) {
                meta.setDisplayName(plugin.getLocalizedText("Skills.rebel.invisA.displayName"));
                meta.setLore(plugin.getLocalizedList("Skills.rebel.invisA.Lore"));
                meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "rebel.invisA");
            }
            if (i == 2) {
                meta.setDisplayName(plugin.getLocalizedText("Skills.rebel.disguiseE.displayName"));
                meta.setLore(plugin.getLocalizedList("Skills.rebel.disguiseE.Lore"));
                meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "rebel.disguiseE");
            }
            if (i == 3) {
                meta.setDisplayName(plugin.getLocalizedText("Skills.rebel.quickenStepsQ.displayName"));
                meta.setLore(plugin.getLocalizedList("Skills.rebel.quickenStepsQ.Lore"));
                meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "rebel.quickenStepsQ");
            }
        }
        meta.getPersistentDataContainer().set(isAvailable, PersistentDataType.BOOLEAN, available);
        meta.setDisplayName(meta.getDisplayName() + " " + (available ? plugin.getLocalizedText("Skills.tags.available") : plugin.getLocalizedText("Skills.tags.unavailable")));
        result.setItemMeta(meta);
        return result;
    }

    private ItemStack changeAvailability(ItemStack skill, boolean Availability) {
        ItemMeta meta = skill.getItemMeta();
        NamespacedKey key = new NamespacedKey(plugin, "available");
        if (meta.getPersistentDataContainer().has(key)) {
            meta.getPersistentDataContainer().remove(key);
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.BOOLEAN, Availability);
        ItemStack result = skill;
        result.setItemMeta(meta);
        return result;
    }

    @EventHandler
    public void playerUseSkillEvent(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (game == null) return;
        if (game.getStatus() != GameStatus.inGaming) return;
        if (e.getPlayer() == null || e.getItem() == null) return;
        String skillName = getWhichSkill(e.getItem());
        if (skillName == null) return; //防NPE

        Player player = e.getPlayer();
        if (!isSkillAvailable(e.getItem())) {
            player.sendMessage(plugin.getLocalizedText("inCooldown"));
            return;
        }
        if (game.getIdentity(player).equals("eliminated")) return;
        else if (game.getIdentity(player).equals("hunter")) {
            addToCooldownQueue(e.getItem(), player);
            if (skillName.equals("hunter.traceA")) {
                if (game == null) return;
                @NotNull Collection<Player> detects = player.getLocation().getNearbyPlayers(5);
                detects.remove(player);
                for (Player detect : detects) {
                    if (game.getIdentity(detect).equals("rebel")) {
                        player.sendActionBar(plugin.getLocalizedText("rebelExists"));
                        player.playSound(player, Sound.BLOCK_ANVIL_LAND, 2, 1);
                        return;
                    }
                }
                return;
            } else if (skillName.equals("hunter.captureE")) {
                Collection<Player> detects = player.getLocation().getNearbyPlayers(3);
                detects.remove(player);
                player.playSound(player, Sound.ITEM_ARMOR_EQUIP_DIAMOND, 1, 1);
                for (Player detect : detects) {
                    if (game.getIdentity(detect).equals("rebel")) {
                        if (notCaptureablePlayers.contains(detect)) {
                            player.sendMessage(plugin.getLocalizedText("notCaptureable"));
                            continue;
                        }
                        smokeRise(detect);
                        disguise.undisguisePlayer(detect);
                        game.eliminate(detect);
                        disguise.cancelFixation(detect, false);
                        disguise.undisguisePlayer(detect);
                        game.addHunterCapture(player);
                        player.playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
                        if (game.getRebelList().isEmpty()) EndGame(game, "hunter");
                    }
                }
            } else if (skillName.equals("hunter.freezeQ")) {
                List<Player> rebels = game.getRebelList();
                if (rebels.isEmpty()) return;

                Player unluckyPlayer = rebels.get(new Random().nextInt(rebels.size()));
                freeze(unluckyPlayer,10);

            }
        } else if (game.getIdentity(player).equals("rebel")) {
            addToCooldownQueue(e.getItem(), player);
            if (skillName.equals("rebel.invisA")) {
                player.sendActionBar(plugin.getLocalizedText("undisguise"));
                plugin.setInvis(player, true);
                disguise.undisguisePlayer(player);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> plugin.setInvis(player, false), 100L);
            }
            if (skillName.equals("rebel.disguiseE")) {
                if (Math.random() <= 0.2) {
                    disguise.undisguisePlayer(player);
                    player.sendActionBar(plugin.getLocalizedText("undisguise"));
                } else disguise.randomDisguisePlayer(player);
            }
            if (skillName.equals("rebel.quickenStepsQ")) {
                player.setWalkSpeed(0.8f);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> player.setWalkSpeed(0.2f), 600L);
            }
        }
    }
    private void freeze(Player unluckyPlayer, int s) {
        notCaptureablePlayers.add(unluckyPlayer);
        cantMovePlayers.add(unluckyPlayer);
        unluckyPlayer.setWalkSpeed(0);
        frozenPlayers.add(unluckyPlayer);           // 加入冰冻列表
        heatedPlayers.remove(unluckyPlayer);        // 从加热列表移除

        plugin.getLocalizedList("frozen").forEach(unluckyPlayer::sendMessage);
        Location playerLocation = unluckyPlayer.getLocation();
        Block originalBlock = playerLocation.getBlock();
        BlockState originalState = originalBlock.getState();
        Material originalType = originalBlock.getType();
        game.getPlaying().forEach(player ->
                player.sendMessage(plugin.getLocalizedText("frozenBoardCast").replace("{player}", unluckyPlayer.getName()))
        );
        originalBlock.setType(Material.BLUE_ICE, true);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            unluckyPlayer.setWalkSpeed(0.2f);
            originalState.update(true, true);
            cantMovePlayers.remove(unluckyPlayer);
            thawPlayer(unluckyPlayer);               // 冰冻结束，开始解冻
        }, 20L * s);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            notCaptureablePlayers.remove(unluckyPlayer);
        }, 25L * s);
    }
    private void thawPlayer(Player player) {
        frozenPlayers.remove(player);                // 解除冰冻状态
        coldValue.put(player.getUniqueId(), 100);    // 确保寒冷值为100
        int per = -25; // 每次减少25，分4次减完
        new BukkitRunnable() {
            int i = 0;
            @Override
            public void run() {
                addColdValue(player, per);
                i++;
                if (i == 4) {
                    if (getColdValue(player) != 0) {
                        int newValue = -getColdValue(player);
                        addColdValue(player, newValue);
                    }
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 4L); // 每4 tick一次，共16 tick (0.8秒)
    }

    private String getWhichSkill(ItemStack itemStack) {
        if (itemStack == null) return null;
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.get(new NamespacedKey(plugin, "skillName"), PersistentDataType.STRING);
    }

    private boolean isSkillAvailable(ItemStack itemStack) {
        if (itemStack == null) return false;
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return Boolean.TRUE.equals(container.get(new NamespacedKey(plugin, "available"), PersistentDataType.BOOLEAN));
    }

    private void smokeRise(Player player) {
        Location loc = player.getLocation().clone();
        loc.add(0, 1, 0); // 从玩家头部位置开始

        // 创建黑烟效果（使用LARGE_SMOKE粒子）
        for (int i = 0; i < 30; i++) {
            double x = Math.random() * 2 - 1; // -1到1之间的随机数
            double z = Math.random() * 2 - 1;
            double y = Math.random() * 3; // 上升高度

            Location particleLoc = loc.clone().add(x, y, z);
            player.getWorld().spawnParticle(Particle.SMOKE_LARGE, particleLoc, 0);

        }
    }

    private SkillCooldown getDefaultCooldown(String skillName, Player player) {
        SkillCooldown cooldown;
        switch (skillName) {
            case "hunter.traceA":
                cooldown = new SkillCooldown(4.0, 3, player, skillName); // 使用double值
                break;
            case "hunter.captureE":
                if(plugin.config.getInt("gameTime")-game.totalTime>60)cooldown = new SkillCooldown(5.0, 3, player, skillName);
                else cooldown = new SkillCooldown(3.0, 3, player, skillName);
                break;
            case "hunter.freezeQ":
                cooldown = new SkillCooldown(-1.0, 1, player, skillName);
                break;
            case "rebel.invisA":
                cooldown = new SkillCooldown(30.0, 1, player, skillName);
                break;
            case "rebel.disguiseE":
                cooldown = new SkillCooldown(0.1, 1, player, skillName);
                break;
            case "rebel.quickenStepsQ":
                cooldown = new SkillCooldown(-1.0, 1, player, skillName);
                break;
            default:
                cooldown = new SkillCooldown(10.0, 1, player, skillName);
        }
        // 初始化当前可用次数
        cooldown.setNowUsableTimes(cooldown.getUsableTimes());
        return cooldown;
    }

    private String addToCooldownQueue(ItemStack skill, Player player) {
        String skillName = getWhichSkill(skill);
        if (skillName == null) return "invalidSkill";

        // 使用玩家+技能名作为唯一标识，而不是ItemStack对象
        String uniqueKey = player.getName() + ":" + skillName;

        // 检查是否已经在冷却队列中
        if (isSkillInCooldown(player, skillName)) {
            // 对于已在队列中的技能，只减少使用次数
            SkillCooldown existingCooldown = getSkillCooldown(player, skillName);
            if (existingCooldown != null && existingCooldown.getNowUsableTimes() > 0) {
                existingCooldown.addNowUsableTimes(-1);
                updateSkillItem(skill, existingCooldown);
            }
            return "alreadyInCooldownQueue";
        }

        // 创建新的冷却对象
        SkillCooldown cooldown = getDefaultCooldown(skillName, player);
        if (cooldown.getMaxCooldown() < 0) {
            if (isSkillAvailable(skill)) {
                ItemMeta m = skill.getItemMeta();
                String displayName = plugin.getLocalizedText("Skills." + getWhichSkill(skill) + ".displayName") + " " +
                        plugin.getLocalizedText("Skills.tags.unavailable");
                m.setDisplayName(displayName);
                skill.setItemMeta(m);
                skill.setType(Material.WOODEN_SWORD);
                changeAvailability(skill, false);
            }
            return "specialSkills";
        }

        // 减少一次使用次数
        cooldown.addNowUsableTimes(-1);

        // 更新物品状态
        updateSkillItem(skill, cooldown);

        // 添加到冷却队列
        processingCooldown.put(uniqueKey, cooldown);

        // 启动冷却任务（如果还没有启动）
        startCooldownTaskIfNeeded();

        return "";
    }

    private boolean isSkillInCooldown(Player player, String skillName) {
        String uniqueKey = player.getName() + ":" + skillName;
        return processingCooldown.containsKey(uniqueKey);
    }

    private SkillCooldown getSkillCooldown(Player player, String skillName) {
        String uniqueKey = player.getName() + ":" + skillName;
        return processingCooldown.get(uniqueKey);
    }

    private void startCooldownTaskIfNeeded() {
        if (processCooldownRunnable != null) return;

        processCooldownRunnable = new BukkitRunnable() {
            @Override
            public void run() {
                // 复制键集合以避免并发修改异常
                Set<String> keys = new HashSet<>(processingCooldown.keySet());

                for (String key : keys) {
                    SkillCooldown cooldown = processingCooldown.get(key);
                    if (cooldown == null) {
                        processingCooldown.remove(key);
                        continue;
                    }

                    Player player = cooldown.getPlayer();
                    String skillName = cooldown.getSkillName();

                    // 获取玩家当前的技能物品（可能需要遍历物品栏）
                    ItemStack skillItem = findSkillItem(player, skillName);
                    if (skillItem == null) {
                        // 如果找不到物品，可能是玩家丢了技能物品，从冷却队列中移除
                        processingCooldown.remove(key);
                        continue;
                    }

                    boolean finished = cooldown.generalAddNowPassed();
                    updateSkillItem(skillItem, cooldown);

                    // 如果完全冷却完成，从队列中移除
                    if (finished) {
                        processingCooldown.remove(key);
                    }
                }

                // 如果冷却队列为空，停止任务
                if (processingCooldown.isEmpty()) {
                    this.cancel();
                    processCooldownRunnable = null;
                }
            }
        };
        processCooldownRunnable.runTaskTimer(plugin, 0, 2L); // 2Tick=0.1s
    }

    private ItemStack findSkillItem(Player player, String skillName) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            String itemSkillName = getWhichSkill(item);
            if (skillName.equals(itemSkillName)) {
                return item;
            }
        }
        return null;
    }

    private void updateSkillItem(ItemStack skill, SkillCooldown cooldown) {
        String skillName = cooldown.getSkillName();
        boolean hasUsableTimes = cooldown.getNowUsableTimes() > 0;

        // 更新物品类型
        skill.setType(hasUsableTimes ? Material.IRON_SWORD : Material.WOODEN_SWORD);

        // 更新可用性
        changeAvailability(skill, hasUsableTimes);

        // 更新数量（显示可用次数）
        int displayAmount = Math.max(1, cooldown.getNowUsableTimes());
        skill.setAmount(displayAmount);

        // 更新显示名称
        ItemMeta meta = skill.getItemMeta();
        if (meta != null) {
            String baseName = plugin.getLocalizedText("Skills." + skillName + ".displayName");
            StringBuilder newDisplayName = new StringBuilder(baseName);

            boolean isTotalCooldownComplete = cooldown.getNowUsableTimes() >= cooldown.getUsableTimes();

            if (!isTotalCooldownComplete) {
                // 总冷却还没完成，显示冷却时间
                double remaining = cooldown.getRemaining();
                if (remaining > 0) {
                    String remainingStr = String.format("%.1f", remaining);
                    newDisplayName.append(" ").append(plugin.getLocalizedText("Skills.tags.inCoolDown")
                            .replace("{cd}", remainingStr));
                }
            }

            // 添加可用性标签
            newDisplayName.append(" ").append(hasUsableTimes ?
                    plugin.getLocalizedText("Skills.tags.available") :
                    plugin.getLocalizedText("Skills.tags.unavailable"));

            meta.setDisplayName(newDisplayName.toString());
            skill.setItemMeta(meta);

            //设置耐久
            if (skill.getItemMeta() instanceof Damageable) {
                Damageable damageable = (Damageable) skill.getItemMeta();
                Short maxDurability = skill.getType().getMaxDurability();
                Short damage = (short) (maxDurability - maxDurability * cooldown.getCooldownProgress());
                if (isTotalCooldownComplete) damageable.setDamage(0);
                else damageable.setDamage(damage);
                skill.setItemMeta(damageable);
            }
        }
    }

    @EventHandler
    public void PlayerChargeQSkillEvent(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Item item = e.getItem();
        Player player = (Player) e.getEntity();

        // 修复：使用统一的Q_SKILL_KEY常量，解决大小写不一致问题
        if (isQSkillItem(item.getItemStack())) {
            e.setCancelled(true);
            e.getItem().remove();

            if (game == null) return; // 空指针防护

            if (game.getIdentity(player).equals("hunter")) {
                ItemStack QSkill = findFirstNonNull(findSkillItem(player, "hunter.freezeQ"));
                if (QSkill == null) return; // 空指针防护

                ItemMeta m = QSkill.getItemMeta();
                if (m == null) return; // 空指针防护

                String displayName = plugin.getLocalizedText("Skills." + getWhichSkill(QSkill) + ".displayName") + " " +
                        plugin.getLocalizedText("Skills.tags.available");
                m.setDisplayName(displayName);
                QSkill.setItemMeta(m);
                QSkill.setType(Material.IRON_SWORD);
                changeAvailability(QSkill, true);

                game.getPlaying().forEach(player1 -> {
                    player1.sendTitle("", plugin.getLocalizedText("QSkillsPickUp").replace("{player}", player.getName()), 5, 100, 5);
                    player1.playSound(player1, Sound.BLOCK_BREWING_STAND_BREW, 1, 1);
                });
            } else if (game.getIdentity(player).equals("rebel")) {
                ItemStack QSkill = findFirstNonNull(findSkillItem(player, "rebel.quickenStepsQ"));
                if (QSkill == null) return; // 空指针防护

                ItemMeta m = QSkill.getItemMeta();
                if (m == null) return; // 空指针防护

                String displayName = plugin.getLocalizedText("Skills." + getWhichSkill(QSkill) + ".displayName") + " " +
                        plugin.getLocalizedText("Skills.tags.available");
                m.setDisplayName(displayName);
                QSkill.setItemMeta(m);
                QSkill.setType(Material.IRON_SWORD);
                changeAvailability(QSkill, true);

                game.getPlaying().forEach(player1 -> {
                    player1.sendTitle("", plugin.getLocalizedText("QSkillsPickUp").replace("{player}", player.getName()), 5, 100, 5);
                    player1.playSound(player1, Sound.BLOCK_BREWING_STAND_BREW, 1, 1);
                });
            }
        }
    }

    private static <T> T findFirstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null; // 如果所有值都为空
    }
    private String xyzToStr(Location l){
        String x= String.valueOf(l.getBlockX());
        String y= String.valueOf(l.getBlockY());
        String z= String.valueOf(l.getBlockZ());
        return x+","+y+","+z;
    }
    //冬季模式相关逻辑
    @EventHandler
    public void PlayerRepairSignalingDeviceEvent(SignalingDeviceProgressChangedEvent e) {
        if(game.getPlayingOnMap().getMode()==WTMapMode.NORMAL){
            if (e.getProgress() >= 30 && !e.getSignalingDevice().reached30) {
                e.getSignalingDevice().reached30 = true;
                game.getPlaying().forEach(player -> {
                    player.playSound(player, Sound.BLOCK_BELL_USE, 1, 1);
                    player.sendMessage(plugin.getLocalizedText("inRepairing").replace("{location}",xyzToStr(e.getSignalingDevice().getSignalingDevice().getLocation())));
                });
            }
            if (e.getProgress() >= 60 && !e.getSignalingDevice().reached60) {
                e.getSignalingDevice().reached60 = true;
                game.getPlaying().forEach(player -> {
                    player.playSound(player, Sound.BLOCK_BELL_USE, 1, 1);
                    player.sendMessage(plugin.getLocalizedText("inRepairing").replace("{location}",xyzToStr(e.getSignalingDevice().getSignalingDevice().getLocation())));
                });
            }
        }

        if (e.getProgress() != 100.0f) return;

        Block block = e.getSignalingDevice().getSignalingDevice();
        Material type = block.getType();

        e.getSignalingDevice().getInRepairingPlayers().forEach(player->game.addRebelRepair(player));
        // 检查是否是营火（普通营火或灵魂营火）
        if(game.getPlayingOnMap().getMode()==WTMapMode.WINTER){
            if (type == Material.CAMPFIRE || type == Material.SOUL_CAMPFIRE) {
                Campfire campfire = (Campfire) block.getState();
                Lightable lightable = (Lightable) campfire.getBlockData();

                if (!lightable.isLit()) { // 可选：避免重复点燃
                    lightable.setLit(true);
                    campfire.setBlockData(lightable);
                    campfire.update(); // 必须调用 update() 使更改生效
                }
            }
            game.getPlaying().forEach(player -> player.playSound(player,Sound.BLOCK_BELL_USE,1,1));
        }
    }
    private void generalAddColdValue(Player player){
        addColdValue(player,3);
    }
    private void addColdValue(Player player, int i) {
        if (frozenPlayers.contains(player)) return; // 冰冻期间不修改寒冷值
        int value = coldValue.get(player.getUniqueId());
        value = Math.min(value + i, 100);
        value = Math.max(value, 0);
        coldValue.replace(player.getUniqueId(), value);
        setEXP(player, value);
    }


    private void setEXP(Player player,int level){
        double progress= (double) level /100;
        player.setLevel(level);
        player.setExp((float) progress);
    }
}
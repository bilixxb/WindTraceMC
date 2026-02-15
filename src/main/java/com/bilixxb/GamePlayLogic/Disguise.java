package com.bilixxb.GamePlayLogic;
import com.bilixxb.WindTraceMC;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.MiscDisguise;
import me.libraryaddict.disguise.disguisetypes.watchers.FallingBlockWatcher;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class Disguise {
    private final WindTraceMC plugin;
    private final List<Material> disguiseAbleBlocks;
    private final ProtocolManager protocolManager;
    private final boolean isProtocolLibExists;

    // 伪装固定相关数据
    private final Map<Player, BukkitTask> fixationTimers = new HashMap<>();   // 等待固定的任务
    private final Map<Player, Boolean> fixedPlayers = new HashMap<>();        // 是否已固定
    private final Map<Player, Material> fixedMaterials = new HashMap<>();     // 固定时使用的方块材质
    private final Map<Player, Location> fixedLocations = new HashMap<>();     // 固定时的位置（精确坐标）
    private final Map<Player, BlockState> originalBlockStates = new HashMap<>(); // 固定时脚下的原方块状态
    private final Map<Player, Block> fixedBlocks = new HashMap<>();           // 放置的真实方块
    private final Map<Player, Material> currentDisguiseMaterials = new HashMap<>();
    private final Map<UUID, BlockPosition> fakeBlockPositions = new HashMap<>(); // 存储假方块位置

    public Disguise(WindTraceMC plugin, @NotNull List<Material> disguiseAbleBlocks) {
        this.plugin = plugin;
        this.disguiseAbleBlocks = disguiseAbleBlocks;

        // 初始化ProtocolLib相关
        boolean protocolLibExists = false;
        ProtocolManager pm = null;
        try {
            pm = ProtocolLibrary.getProtocolManager();
            protocolLibExists = true;
        } catch (NoClassDefFoundError | NullPointerException e) {
            protocolLibExists = false;
        }
        this.protocolManager = pm;
        this.isProtocolLibExists = protocolLibExists;
    }

    /**
     * 随机选取一个可伪装方块
     */
    private Material pickRandomBlock() {
        Random random = new Random();
        int randomIndex = random.nextInt(disguiseAbleBlocks.size());
        return disguiseAbleBlocks.get(randomIndex);
    }

    /**
     * 将玩家随机伪装成一个下落方块，并启动10秒固定倒计时
     */
    public void randomDisguisePlayer(Player player) {
        if (!plugin.isDisguiseLibExists()) return;

        Material randomType = pickRandomBlock();
        currentDisguiseMaterials.put(player, randomType); // 记录当前伪装材质

        MiscDisguise disguise = new MiscDisguise(DisguiseType.FALLING_BLOCK, randomType);
        disguise.setEntity(player);
        disguise.setViewSelfDisguise(true);
        player.setCollidable(false);
        player.setInvulnerable(true);

        FallingBlockWatcher watcher = (FallingBlockWatcher) disguise.getWatcher();
        watcher.setBlock(new ItemStack(randomType));

        DisguiseAPI.disguiseEntity(player, disguise);

        // 启动5秒固定倒计时（100tick）
        startFixationTimer(player, randomType);
    }

    /**
     * 解除玩家的伪装，并取消固定状态
     */
    public void undisguisePlayer(Player player) {
        if (!plugin.isDisguiseLibExists()) return;
        if (DisguiseAPI.isDisguised(player)) {
            DisguiseAPI.undisguiseToAll(player);
            player.setCollidable(true);
            player.setInvulnerable(false);
        }
        currentDisguiseMaterials.remove(player); // 清理记录
        cancelFixation(player, false);
    }

    private void restartFixationTimer(Player player) {
        if (!plugin.isDisguiseLibExists()) return;
        if (DisguiseAPI.isDisguised(player) && currentDisguiseMaterials.containsKey(player)) {
            Material material = currentDisguiseMaterials.get(player);
            // startFixationTimer 内部会取消已有倒计时，直接调用即可
            startFixationTimer(player, material);
        }
    }

    /**
     * 启动固定倒计时：5秒内玩家不移动则触发固定
     */
    private void startFixationTimer(Player player, Material blockMaterial) {
        if (!plugin.isDisguiseLibExists()) return;
        // 取消已有的倒计时
        if (fixationTimers.containsKey(player)) {
            fixationTimers.get(player).cancel();
            fixationTimers.remove(player);
        }

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // 倒计时结束，执行固定
            if (player.isOnline() && DisguiseAPI.isDisguised(player)) {
                applyFixation(player, blockMaterial);
            }
            fixationTimers.remove(player);
        }, 100L); // 5秒 = 100 tick

        fixationTimers.put(player, task);
    }

    /**
     * 执行固定操作：
     * 1. 有ProtocolLib时发送假方块数据包（仅对其他玩家可见），无则放置真实方块
     * 2. 隐藏玩家（对其他玩家不可见）
     * 3. 设置玩家为飞行模式，站在方块上方
     * 4. 记录固定状态
     * 5. 取消被固定玩家的碰撞（ProtocolLib）
     */
    private void applyFixation(Player player, Material blockMaterial) {
        if (!plugin.isDisguiseLibExists()) return;
        Location loc = player.getLocation();
        Block footBlock = loc.getBlock(); // 脚下方块

        // 有ProtocolLib时使用假方块
        if (isProtocolLibExists && protocolManager != null) {
            // 记录假方块位置
            BlockPosition blockPos = new BlockPosition(footBlock.getX(), footBlock.getY(), footBlock.getZ());
            fakeBlockPositions.put(player.getUniqueId(), blockPos);

            // 构造方块更改数据包
            PacketContainer blockChangePacket = protocolManager.createPacket(PacketType.Play.Server.BLOCK_CHANGE);
            blockChangePacket.getBlockPositionModifier().write(0, blockPos);
            WrappedBlockData blockData = WrappedBlockData.createData(blockMaterial);
            blockChangePacket.getBlockData().write(0, blockData);

            // 对除当前玩家外的所有在线玩家发送数据包
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.getUniqueId().equals(player.getUniqueId())) {
                    try {
                        protocolManager.sendServerPacket(online, blockChangePacket);
                    } catch (Exception e) {
                        plugin.getLogger().severe("发送假方块数据包失败: " + e.getMessage());
                    }
                }
            }

            // 取消当前玩家的碰撞
            disablePlayerCollision(player);
        } else {
            // 无ProtocolLib，使用原有逻辑
            // 保存原方块状态
            BlockState originalState = footBlock.getState();
            originalBlockStates.put(player, originalState);

            // 替换为伪装方块
            footBlock.setType(blockMaterial);
            fixedBlocks.put(player, footBlock);
        }

        // 通用逻辑
        fixedMaterials.put(player, blockMaterial);

        Location standLoc = footBlock.getLocation();
        standLoc.setYaw(loc.getYaw());
        standLoc.setPitch(loc.getPitch());
        player.teleport(standLoc.add(0, 0, 0));
        player.setCollidable(false);
        player.setInvulnerable(true);

        // 隐藏玩家（对其他所有人）
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.hidePlayer(plugin, player);
        }

        // 标记固定状态
        fixedPlayers.put(player, true);
        fixedLocations.put(player, loc.clone()); // 记录固定时的精确位置
    }

    /**
     * 取消固定状态
     * @param restoreDisguise 是否恢复伪装（移动取消固定时通常需要恢复）
     */
    public void cancelFixation(Player player, boolean restoreDisguise) {
        if (!plugin.isDisguiseLibExists()) return;
        if (!fixedPlayers.getOrDefault(player, false)) return;

        // 1. 处理ProtocolLib假方块
        if (isProtocolLibExists && protocolManager != null) {
            UUID playerUUID = player.getUniqueId();
            BlockPosition blockPos = fakeBlockPositions.get(playerUUID);
            if (blockPos != null) {
                // 恢复原方块数据包
                Block footBlock = new Location(player.getWorld(), blockPos.getX(), blockPos.getY(), blockPos.getZ()).getBlock();
                PacketContainer restorePacket = protocolManager.createPacket(PacketType.Play.Server.BLOCK_CHANGE);
                restorePacket.getBlockPositionModifier().write(0, blockPos);
                WrappedBlockData originalBlockData = WrappedBlockData.createData(footBlock.getType());
                restorePacket.getBlockData().write(0, originalBlockData);

                // 向除当前玩家外的所有玩家发送恢复数据包
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (!online.getUniqueId().equals(playerUUID)) {
                        try {
                            protocolManager.sendServerPacket(online, restorePacket);
                        } catch (Exception e) {
                            plugin.getLogger().severe("恢复假方块失败: " + e.getMessage());
                        }
                    }
                }
                fakeBlockPositions.remove(playerUUID);
            }

            // 恢复玩家碰撞
            restorePlayerCollision(player);
        } else {
            // 无ProtocolLib，原有逻辑还原方块
            if (originalBlockStates.containsKey(player)) {
                BlockState originalState = originalBlockStates.get(player);
                originalState.update(true, false);
                originalBlockStates.remove(player);
            }
            fixedBlocks.remove(player);
        }

        fixedMaterials.remove(player);

        // 2. 取消隐藏
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showPlayer(plugin, player);
        }

        // 3. 恢复玩家状态
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setCollidable(true);
        player.setInvulnerable(false);

        // 4. 移除固定标记
        fixedPlayers.remove(player);
        fixedLocations.remove(player);

        // 5. 处理伪装恢复与倒计时重启
        if (restoreDisguise && plugin.isDisguiseLibExists() && !DisguiseAPI.isDisguised(player)) {
            // 伪装意外丢失（极少情况），重新随机伪装并开始倒计时
            randomDisguisePlayer(player);
        } else if (DisguiseAPI.isDisguised(player) && !fixationTimers.containsKey(player)) {
            // 伪装还在且没有正在运行的倒计时 → 重启倒计时
            restartFixationTimer(player);
        }
    }

    /**
     * 处理玩家移动事件（由监听器调用）
     * @param player 移动的玩家
     * @param from   移动前位置
     * @param to     移动后位置
     */
    public void handlePlayerMove(Player player, Location from, Location to) {
        if (!plugin.isDisguiseLibExists()) return;

        // 忽略同一方块内的微小移动
        if (from.getBlock().equals(to.getBlock())) {
            return;
        }

        // 1. 玩家正在等待固定 -> 取消倒计时，重新启动
        if (fixationTimers.containsKey(player)) {
            fixationTimers.get(player).cancel();
            fixationTimers.remove(player);
            restartFixationTimer(player);
            return;
        }

        // 2. 玩家已固定 -> 取消固定，并恢复伪装倒计时（cancelFixation内部会处理重启）
        if (fixedPlayers.getOrDefault(player, false)) {
            cancelFixation(player, true);
        }
    }

    /**
     * 判断一个方块是否为当前某玩家固定时放置的方块（用于防止破坏）
     */
    public boolean isFixedBlock(Block block) {
        if (isProtocolLibExists) {
            // ProtocolLib模式下没有真实方块，返回false
            return false;
        }
        return fixedBlocks.containsValue(block);
    }

    /**
     * 清理玩家所有相关数据（玩家退出时调用）
     */
    public void cleanupPlayer(Player player) {
        if (!plugin.isDisguiseLibExists()) return;
        if (fixationTimers.containsKey(player)) {
            fixationTimers.get(player).cancel();
            fixationTimers.remove(player);
        }
        if (fixedPlayers.containsKey(player)) {
            cancelFixation(player, false);
        }
        // 移除所有映射
        fixedPlayers.remove(player);
        fixedLocations.remove(player);
        fixedMaterials.remove(player);
        originalBlockStates.remove(player);
        fixedBlocks.remove(player);
        currentDisguiseMaterials.remove(player);
        // 清理ProtocolLib假方块记录
        fakeBlockPositions.remove(player.getUniqueId());
    }

    /**
     * 使用ProtocolLib 5.4.0取消玩家的碰撞（1.20.1）
     */
    private void disablePlayerCollision(Player player) {
        if (!isProtocolLibExists || protocolManager == null) return;
        try {
            int entityId = player.getEntityId();

            // 创建实体元数据数据包（适配ProtocolLib 5.4.0）
            PacketContainer metadataPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
            metadataPacket.getIntegers().write(0, entityId);

            // 创建数据监听器并设置不可碰撞标志（1.20.1中索引6是可碰撞状态）
            WrappedDataWatcher dataWatcher = new WrappedDataWatcher();
            WrappedDataWatcher.WrappedDataWatcherObject collidableWatcher = new WrappedDataWatcher.WrappedDataWatcherObject(
                    6, WrappedDataWatcher.Registry.get(Boolean.class)
            );
            dataWatcher.setObject(collidableWatcher, false);

            // 将数据监听器写入数据包
            metadataPacket.getWatchableCollectionModifier().write(0, dataWatcher.getWatchableObjects());

            // 向所有玩家发送
            for (Player online : Bukkit.getOnlinePlayers()) {
                protocolManager.sendServerPacket(online, metadataPacket);
            }
        } catch (Exception ignored) {

        }
    }

    /**
     * 恢复玩家的碰撞状态（适配ProtocolLib 5.4.0）
     */
    private void restorePlayerCollision(Player player) {
        if (!isProtocolLibExists || protocolManager == null) return;
        try {
            int entityId = player.getEntityId();

            // 创建实体元数据数据包
            PacketContainer metadataPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
            metadataPacket.getIntegers().write(0, entityId);

            // 创建数据监听器并恢复可碰撞标志
            WrappedDataWatcher dataWatcher = new WrappedDataWatcher();
            WrappedDataWatcher.WrappedDataWatcherObject collidableWatcher = new WrappedDataWatcher.WrappedDataWatcherObject(
                    6, WrappedDataWatcher.Registry.get(Boolean.class)
            );
            dataWatcher.setObject(collidableWatcher, true);

            // 将数据监听器写入数据包
            metadataPacket.getWatchableCollectionModifier().write(0, dataWatcher.getWatchableObjects());

            for (Player online : Bukkit.getOnlinePlayers()) {
                protocolManager.sendServerPacket(online, metadataPacket);
            }
        } catch (Exception ignored) {

        }
    }
}
package com.bilixxb.GamePlayLogic;

import com.bilixxb.WindTraceMC;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.Pair;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

public class Invis {
    final private WindTraceMC plugin;
    private final ProtocolManager protocolManager;
    private final Map<UUID, Set<UUID>> invisiblePlayers; // 隐身状态记录
    private final Boolean isProtocolLibExists;
    private boolean listenerRegistered = false;

    public Invis(WindTraceMC plugin) {
        ProtocolManager protocolManager1 = null;
        Boolean isProtocolLibExists1 = plugin.isProtocolLibExists();

        if (isProtocolLibExists1) {
            try {
                protocolManager1 = ProtocolLibrary.getProtocolManager();
            } catch (NoClassDefFoundError e) {
                isProtocolLibExists1 = false;
            }
        }

        this.plugin = plugin;
        this.protocolManager = protocolManager1;
        this.isProtocolLibExists = isProtocolLibExists1;
        this.invisiblePlayers = new HashMap<>();

        if (isProtocolLibExists1) {
            registerPacketListener();
        }
    }

    /**
     * 注册 PacketListener，拦截隐身玩家的所有相关包
     */
    private void registerPacketListener() {
        if (listenerRegistered) return;
        protocolManager.addPacketListener(new PacketAdapter(plugin,
                PacketType.Play.Server.NAMED_ENTITY_SPAWN,
                PacketType.Play.Server.ENTITY_METADATA,
                PacketType.Play.Server.ENTITY_EQUIPMENT) { // 新增：拦截装备包
            @Override
            public void onPacketSending(PacketEvent event) {
                Player viewer = event.getPlayer();
                PacketContainer packet = event.getPacket();
                UUID viewerId = viewer.getUniqueId();

                if (packet.getType() == PacketType.Play.Server.NAMED_ENTITY_SPAWN) {
                    // 处理玩家生成包
                    int entityId = packet.getIntegers().read(0);
                    UUID targetId = packet.getUUIDs().read(0);
                    Player target = Bukkit.getPlayer(targetId);
                    if (target == null) return;

                    if (isInvisibleTo(target, viewer)) {
                        event.setCancelled(true);
                        destroyEntityIfExists(viewer, entityId);
                    }

                } else if (packet.getType() == PacketType.Play.Server.ENTITY_METADATA) {
                    // 处理实体元数据包，强制设置隐身标志
                    int entityId = packet.getIntegers().read(0);
                    Player target = getPlayerByEntityId(entityId);
                    if (target == null) return;

                    if (isInvisibleTo(target, viewer)) {
                        WrappedDataWatcher watcher = packet.getDataWatcherModifier().read(0);
                        if (watcher == null) watcher = new WrappedDataWatcher();

                        byte currentFlags = 0;
                        if (watcher.hasIndex(0)) {
                            currentFlags = (byte) watcher.getObject(0);
                        }
                        watcher.setObject(0, (byte) (currentFlags | 0x20)); // 隐身标志
                        packet.getDataWatcherModifier().write(0, watcher);
                    }

                } else if (packet.getType() == PacketType.Play.Server.ENTITY_EQUIPMENT) {
                    // 处理实体装备包：若目标对观察者隐身，直接取消
                    int entityId = packet.getIntegers().read(0);
                    Player target = getPlayerByEntityId(entityId);
                    if (target == null) return;

                    if (isInvisibleTo(target, viewer)) {
                        event.setCancelled(true);
                    }
                }
            }
        });
        listenerRegistered = true;
    }

    /**
     * 卸载 PacketListener（插件禁用时调用）
     */
    private void unregisterPacketListener() {
        if (listenerRegistered) {
            protocolManager.removePacketListeners(plugin);
            listenerRegistered = false;
        }
    }

    /**
     * 通过实体ID获取玩家
     */
    private Player getPlayerByEntityId(int entityId) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getEntityId() == entityId) return p;
        }
        return null;
    }

    /**
     * 销毁指定的实体（如果存在）
     */
    private void destroyEntityIfExists(Player viewer, int entityId) {
        try {
            PacketContainer destroyPacket = new PacketContainer(PacketType.Play.Server.ENTITY_DESTROY);
            destroyPacket.getIntLists().write(0, Collections.singletonList(entityId));
            protocolManager.sendServerPacket(viewer, destroyPacket);
        } catch (Exception ignored) {}
    }

    // ---------- 公开隐身/可见控制方法 ----------

    public void makePlayerInvisible(Player target, List<Player> viewers) {
        if (!isProtocolLibExists) return;
        if (target == null || viewers == null || viewers.isEmpty()) return;

        UUID targetId = target.getUniqueId();
        invisiblePlayers.putIfAbsent(targetId, new HashSet<>());

        for (Player viewer : viewers) {
            if (viewer.equals(target)) continue;
            UUID viewerId = viewer.getUniqueId();

            try {
                // 1. 立即销毁目标实体
                destroyEntity(viewer, target);

                // 2. 给目标自身添加隐身药水效果（让自己看不见自己手，非必须）
                target.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                        Integer.MAX_VALUE, 1, false, false));

                // 3. 隐藏名称标签
                hideNameTag(target, viewer);

                // 4. 记录隐身关系
                invisiblePlayers.get(targetId).add(viewerId);

            } catch (Exception ignored) {}
        }
    }

    public void makePlayerVisible(Player target, List<Player> viewers) {
        if (!isProtocolLibExists) return;
        if (target == null || viewers == null) return;

        UUID targetId = target.getUniqueId();
        if (!invisiblePlayers.containsKey(targetId)) return;

        for (Player viewer : viewers) {
            UUID viewerId = viewer.getUniqueId();
            if (!invisiblePlayers.get(targetId).contains(viewerId)) continue;

            try {
                // 1. 移除隐身效果（如果不需要）
                target.removePotionEffect(PotionEffectType.INVISIBILITY);

                // 2. 恢复名称标签
                showNameTag(target, viewer);

                // 3. 发送重生包（让玩家重新出现）
                resendSpawnPacket(target, viewer);

                // 4. **发送当前装备数据**（因为隐身期间所有装备包都被取消了）
                sendPlayerEquipment(target, viewer);

                // 5. 移除记录
                invisiblePlayers.get(targetId).remove(viewerId);

            } catch (Exception ignored) {}
        }

        if (invisiblePlayers.get(targetId).isEmpty()) {
            invisiblePlayers.remove(targetId);
        }
    }

    /**
     * 销毁实体（发送实体销毁包）
     */
    private void destroyEntity(Player viewer, Player target) throws InvocationTargetException {
        PacketContainer destroyPacket = new PacketContainer(PacketType.Play.Server.ENTITY_DESTROY);
        destroyPacket.getIntLists().write(0, Collections.singletonList(target.getEntityId()));
        protocolManager.sendServerPacket(viewer, destroyPacket);
    }

    /**
     * 重新发送实体重生包
     */
    private void resendSpawnPacket(Player target, Player viewer) throws InvocationTargetException {
        PacketContainer spawnPacket = createSpawnPacket(target);
        protocolManager.sendServerPacket(viewer, spawnPacket);
        sendCleanMetadata(viewer, target);
    }

    /**
     * 创建玩家重生包
     */
    private PacketContainer createSpawnPacket(Player player) {
        PacketContainer spawnPacket = new PacketContainer(PacketType.Play.Server.NAMED_ENTITY_SPAWN);
        spawnPacket.getIntegers().write(0, player.getEntityId());
        spawnPacket.getUUIDs().write(0, player.getUniqueId());
        spawnPacket.getDoubles().write(0, player.getLocation().getX());
        spawnPacket.getDoubles().write(1, player.getLocation().getY());
        spawnPacket.getDoubles().write(2, player.getLocation().getZ());
        spawnPacket.getBytes().write(0, (byte) ((player.getLocation().getYaw() * 256.0F) / 360.0F));
        spawnPacket.getBytes().write(1, (byte) ((player.getLocation().getPitch() * 256.0F) / 360.0F));
        return spawnPacket;
    }

    /**
     * 发送干净的元数据（无隐身标志），用于玩家恢复可见时
     */
    private void sendCleanMetadata(Player viewer, Player target) throws InvocationTargetException {
        PacketContainer metadataPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
        metadataPacket.getIntegers().write(0, target.getEntityId());

        WrappedDataWatcher watcher = WrappedDataWatcher.getEntityWatcher(target);
        if (watcher != null) {
            // 移除隐身标志 (bit 0x20)
            if (watcher.hasIndex(0)) {
                byte flags = (byte) watcher.getObject(0);
                watcher.setObject(0, (byte) (flags & ~0x20));
            }
            metadataPacket.getDataWatcherModifier().write(0, watcher);
            protocolManager.sendServerPacket(viewer, metadataPacket);
        }
    }

    /**
     * ========== 新增方法：发送玩家当前全部装备给指定观察者 ==========
     * 用于取消隐身时恢复手持物品显示
     */
    private void sendPlayerEquipment(Player target, Player viewer) throws InvocationTargetException {
        // 遍历所有装备槽位
        List<Pair<EnumWrappers.ItemSlot, ItemStack>> equipmentList = new ArrayList<>();

        // 主手
        equipmentList.add(new Pair<>(EnumWrappers.ItemSlot.MAINHAND, target.getInventory().getItemInMainHand()));
        // 副手
        equipmentList.add(new Pair<>(EnumWrappers.ItemSlot.OFFHAND, target.getInventory().getItemInOffHand()));
        // 头盔
        equipmentList.add(new Pair<>(EnumWrappers.ItemSlot.HEAD, target.getInventory().getHelmet()));
        // 胸甲
        equipmentList.add(new Pair<>(EnumWrappers.ItemSlot.CHEST, target.getInventory().getChestplate()));
        // 护腿
        equipmentList.add(new Pair<>(EnumWrappers.ItemSlot.LEGS, target.getInventory().getLeggings()));
        // 靴子
        equipmentList.add(new Pair<>(EnumWrappers.ItemSlot.FEET, target.getInventory().getBoots()));

        PacketContainer equipmentPacket = new PacketContainer(PacketType.Play.Server.ENTITY_EQUIPMENT);
        equipmentPacket.getIntegers().write(0, target.getEntityId());
        equipmentPacket.getSlotStackPairLists().write(0, equipmentList);

        protocolManager.sendServerPacket(viewer, equipmentPacket);
    }

    // ---------- 计分板队伍（隐藏/显示名称） ----------
    private void hideNameTag(Player target, Player viewer) {
        String teamName = "invis_" + target.getEntityId();
        org.bukkit.scoreboard.Scoreboard scoreboard = viewer.getScoreboard();
        org.bukkit.scoreboard.Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }
        team.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY,
                org.bukkit.scoreboard.Team.OptionStatus.NEVER);
        team.addEntry(target.getName());
    }

    private void showNameTag(Player target, Player viewer) {
        String teamName = "invis_" + target.getEntityId();
        org.bukkit.scoreboard.Scoreboard scoreboard = viewer.getScoreboard();
        org.bukkit.scoreboard.Team team = scoreboard.getTeam(teamName);
        if (team != null) {
            team.removeEntry(target.getName());
            if (team.getEntries().isEmpty()) {
                team.unregister();
            }
        }
    }

    // ---------- 批量控制 ----------
    public void makePlayerInvisibleToAll(Player target) {
        List<Player> allPlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        makePlayerInvisible(target, allPlayers);
    }

    public void makePlayerVisibleToAll(Player target) {
        List<Player> allPlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        makePlayerVisible(target, allPlayers);
    }

    // ---------- 查询状态 ----------
    public boolean isInvisibleTo(Player target, Player viewer) {
        if (!isProtocolLibExists) return true;
        UUID targetId = target.getUniqueId();
        UUID viewerId = viewer.getUniqueId();
        return invisiblePlayers.containsKey(targetId) &&
                invisiblePlayers.get(targetId).contains(viewerId);
    }

    // ---------- 清理 ----------
    public void cleanupAllInvisibility() {
        unregisterPacketListener();
        for (UUID targetId : new HashSet<>(invisiblePlayers.keySet())) {
            Player target = Bukkit.getPlayer(targetId);
            if (target != null && target.isOnline()) {
                makePlayerVisibleToAll(target);
            }
        }
        invisiblePlayers.clear();
    }


}
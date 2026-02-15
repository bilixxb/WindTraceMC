package com.bilixxb;

import com.bilixxb.GamePlayLogic.GameStatus;
import com.bilixxb.GamePlayLogic.WindTraceGame;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class joinerGUI implements Listener {
    private final WindTraceMC plugin;
    private Inventory normalModeUI;
    private Inventory winterModeUI;

    // 创建用于识别GUI的键
    private final NamespacedKey guiKey;

    public joinerGUI(WindTraceMC plugin){
        this.plugin = plugin;
        this.guiKey = new NamespacedKey(plugin, "WindTraceGUI");

        normalModeUI = Bukkit.createInventory(null, 27, plugin.getLocalizedText("GUI.normalTitle"));
        winterModeUI = Bukkit.createInventory(null, 27, plugin.getLocalizedText("GUI.winterTitle"));

        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemStack rejoin = new ItemStack(Material.ENDER_PEARL);
        ItemMeta borderMeta = border.getItemMeta();
        ItemMeta rejoinMeta = rejoin.getItemMeta();

        borderMeta.setDisplayName("   ");
        // 给GUI物品添加标记
        borderMeta.getPersistentDataContainer().set(guiKey, PersistentDataType.STRING, "true");
        border.setItemMeta(borderMeta);

        rejoinMeta.setDisplayName(plugin.getLocalizedText("GUI.rejoin.displayName"));
        rejoinMeta.setLore(plugin.getLocalizedList("GUI.rejoin.Lore"));
        rejoin.setItemMeta(rejoinMeta);

        ItemStack winter = new ItemStack(Material.SNOW_BLOCK);
        ItemStack normal = new ItemStack(Material.OAK_PLANKS);
        ItemMeta winterMeta = winter.getItemMeta();
        ItemMeta normalMeta = normal.getItemMeta();

        winterMeta.setDisplayName(plugin.getLocalizedText("GUI.winter.displayName"));
        normalMeta.setDisplayName(plugin.getLocalizedText("GUI.normal.displayName"));
        winterMeta.setLore(plugin.getLocalizedList("GUI.winter.Lore"));
        normalMeta.setLore(plugin.getLocalizedList("GUI.normal.Lore"));

        winter.setItemMeta(winterMeta);
        normal.setItemMeta(normalMeta);

        normalModeUI.setItem(18, rejoin);
        winterModeUI.setItem(18, rejoin);
        normalModeUI.setItem(26, winter);
        winterModeUI.setItem(26, normal);

        setMultipleSlots(normalModeUI, border, 0,1,2,3,4,5,6,7,8,9,17,19,20,21,22,23,24,25);
        setMultipleSlots(winterModeUI, border, 0,1,2,3,4,5,6,7,8,9,17,19,20,21,22,23,24,25);
    }

    public static void setMultipleSlots(Inventory inv, ItemStack item, int... slots) {
        for (int slot : slots) {
            inv.setItem(slot, item);
        }
    }

    public void openNormalUIFor(Player player){
        Inventory ui = initializeUI(normalModeUI, WTMapMode.NORMAL);
        player.openInventory(ui);
    }

    public void openWinterUIFor(Player player){
        Inventory ui = initializeUI(winterModeUI, WTMapMode.WINTER);
        player.openInventory(ui);
    }

    private Inventory initializeUI(Inventory ui, WTMapMode mode){
        // 创建一个新的库存副本以避免修改原始UI
        Inventory finalUI = Bukkit.createInventory(null, 27, plugin.getLocalizedText("GUI." + mode.name().toLowerCase() + "Title"));

        // 复制原有物品
        for (int i = 0; i < 27; i++) {
            ItemStack originalItem = ui.getItem(i);
            if (originalItem != null) {
                finalUI.setItem(i, originalItem.clone());
            }
        }

        ItemStack empty = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta emptyMeta = empty.getItemMeta();
        emptyMeta.setDisplayName(plugin.getLocalizedText("GUI.empty.displayName"));
        emptyMeta.setLore(plugin.getLocalizedList("GUI.empty.Lore"));
        empty.setItemMeta(emptyMeta);

        // 创建两个列表来分别存储不同状态的游戏
        List<WindTraceGame> notStartedGames = new ArrayList<>();
        List<WindTraceGame> otherGames = new ArrayList<>();

        // 遍历所有可用游戏，按状态分类
        for (WindTraceGame game : plugin.gamesAvailable) {
            if (game.getPlayingOnMap().getMode() != mode) continue;

            if (game.getStatus() == GameStatus.NotStarted) {
                notStartedGames.add(game);
            } else {
                otherGames.add(game);
            }
        }

        int slot = 0;

        // 先添加 NotStarted 状态的游戏
        for (WindTraceGame game : notStartedGames) {
            if (slot > 6) break;

            ItemStack gameItem = new ItemStack(Material.GREEN_WOOL);
            ItemMeta gameMeta = gameItem.getItemMeta();

            gameMeta.setDisplayName(plugin.getLocalizedText("GUI.availableGame.displayName")
                    .replace("{displayName}", game.getPlayingOnMap().getDisplayName()));

            List<String> lore = plugin.getLocalizedList("GUI.availableGame.Lore");
            lore.replaceAll(str -> str
                    .replace("{nowPlayers}", String.valueOf(game.getCurrentPlayers()))
                    .replace("{maxPlayers}", String.valueOf(game.getPlayingOnMap().getMaxplayers()))
                    .replace("{MODE}", plugin.getLocalizedText("GUI.mode." + game.getPlayingOnMap().getMode().name()))
                    .replace("{status}", plugin.getLocalizedText("GUI.status." + game.getStatus().name())));
            gameMeta.setLore(lore);

            NamespacedKey gameKey = new NamespacedKey(plugin, "game_map");
            gameMeta.getPersistentDataContainer().set(gameKey, PersistentDataType.STRING,
                    game.getPlayingOnMap().getMapName());
            gameItem.setItemMeta(gameMeta);

            finalUI.setItem(10 + slot, gameItem);
            slot++;
        }

        // 再添加其他状态的游戏
        for (WindTraceGame game : otherGames) {
            if (slot > 6) break;

            ItemStack gameItem = new ItemStack(Material.GREEN_WOOL);
            ItemMeta gameMeta = gameItem.getItemMeta();

            gameMeta.setDisplayName(plugin.getLocalizedText("GUI.availableGame.displayName")
                    .replace("{displayName}", game.getPlayingOnMap().getDisplayName()));

            List<String> lore = plugin.getLocalizedList("GUI.availableGame.Lore");
            lore.replaceAll(str -> str
                    .replace("{nowPlayers}", String.valueOf(game.getCurrentPlayers()))
                    .replace("{maxPlayers}", String.valueOf(game.getPlayingOnMap().getMaxplayers()))
                    .replace("{MODE}", plugin.getLocalizedText("GUI.mode." + game.getPlayingOnMap().getMode().name()))
                    .replace("{status}", plugin.getLocalizedText("GUI.status." + game.getStatus().name())));
            gameMeta.setLore(lore);

            // 为游戏物品添加标记，存储游戏地图名称
            NamespacedKey gameKey = new NamespacedKey(plugin, "game_map");
            gameMeta.getPersistentDataContainer().set(gameKey, PersistentDataType.STRING,
                    game.getPlayingOnMap().getMapName());
            gameItem.setItemMeta(gameMeta);

            finalUI.setItem(10 + slot, gameItem);
            slot++;
        }

        // 用空物品填充剩余的槽位
        if (slot < 7) {
            for (int i = slot; i < 7; i++) {
                finalUI.setItem(10 + i, empty);
            }
        }

        return finalUI;
    }

    @EventHandler
    public void onPlayerClickUI(InventoryClickEvent e){
        Inventory inv = e.getClickedInventory();
        if (inv == null) return;

        ItemStack judgement = inv.getItem(0);
        if (judgement == null) return;

        ItemMeta judgeMeta = judgement.getItemMeta();
        if (judgeMeta == null) return;

        // 检查是否是WindTraceGUI
        if (!judgeMeta.getPersistentDataContainer().has(guiKey, PersistentDataType.STRING)) {
            return;
        }

        e.setCancelled(true);

        ItemStack currentItem = e.getCurrentItem();
        if (currentItem == null) return;

        ItemMeta currentMeta = currentItem.getItemMeta();
        if (currentMeta == null) return;

        String displayName = currentMeta.getDisplayName();

        // 检查是否是空物品
        if (displayName.equals(plugin.getLocalizedText("GUI.empty.displayName"))) {
            return;
        }

        // 检查是否是模式切换按钮
        if (displayName.equals(plugin.getLocalizedText("GUI.winter.displayName"))) {
            openWinterUIFor((Player) e.getWhoClicked());
            return;
        }

        if (displayName.equals(plugin.getLocalizedText("GUI.normal.displayName"))) {
            openNormalUIFor((Player) e.getWhoClicked());
            return;
        }

        // 检查是否是重新加入按钮
        if (displayName.equals(plugin.getLocalizedText("GUI.rejoin.displayName"))) {
            // 处理重新加入逻辑
            ((Player)e.getWhoClicked()).chat("/wt r");
            return;
        }

        // 检查是否是游戏物品（通过检查是否有game_map标记）
        NamespacedKey gameKey = new NamespacedKey(plugin, "game_map");
        if (currentMeta.getPersistentDataContainer().has(gameKey, PersistentDataType.STRING)) {
            // 获取地图名称
            String mapName = currentMeta.getPersistentDataContainer().get(gameKey, PersistentDataType.STRING);

            // 查找对应的游戏
            WindTraceGame targetGame = null;
            for (WindTraceGame game : plugin.gamesAvailable) {
                if (game.getPlayingOnMap().getMapName().equals(mapName)) {
                    targetGame = game;
                    break;
                }
            }

            if (targetGame != null) {
                // 尝试让玩家加入游戏
                Player player = (Player) e.getWhoClicked();
                if (targetGame.getStatus()== GameStatus.NotStarted) {
                    String error= targetGame.joinGame(player);
                    if(error.equalsIgnoreCase(""))player.sendMessage(plugin.getLocalizedText("joining").replace("{displayName}",targetGame.getPlayingOnMap().getDisplayName()));
                    else if(error.equalsIgnoreCase("mapFull")) player.sendMessage(plugin.getLocalizedText("mapFull").replace("{displayName}",targetGame.getPlayingOnMap().getDisplayName()));
                    else if(error.equalsIgnoreCase("alreadyExists")) player.sendMessage(plugin.getLocalizedText("alreadyExists"));
                    player.closeInventory();
                } else {
                    player.sendMessage(plugin.getLocalizedText("cannotJoinGame"));
                }
            } else {
                // 游戏可能已经结束了，刷新UI
                Player player = (Player) e.getWhoClicked();
                player.sendMessage(plugin.getLocalizedText("cannotJoinGame"));

                // 根据当前UI标题判断是哪种模式，然后重新打开
                String title = e.getView().getTitle();
                if (title.equals(plugin.getLocalizedText("GUI.winterTitle"))) {
                    openWinterUIFor(player);
                } else {
                    openNormalUIFor(player);
                }
            }
        }
    }


}
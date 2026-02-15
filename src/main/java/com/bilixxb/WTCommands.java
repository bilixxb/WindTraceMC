package com.bilixxb;

import com.bilixxb.GamePlayLogic.GameStatus;
import com.bilixxb.GamePlayLogic.WindTraceGame;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

public class WTCommands implements CommandExecutor {
    private final WindTraceMC plugin;
    public static Map<Player,WTMap> editingModeMap=new HashMap<Player,WTMap>();
    public static List<Player> inSettingSignalingDevicePlayers=new ArrayList<Player>();
    public static List<Player> inRemovingSignalingDevicePlayers=new ArrayList<Player>();
    private final joinerGUI GUI;
    public WTCommands(WindTraceMC plugin) {
        this.plugin = plugin;
        GUI=new joinerGUI(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
        if (command.getName().equalsIgnoreCase("wt") || command.getName().equalsIgnoreCase("WindTrace")) {
            if(strings.length==0){processHelp(commandSender,false);return true;}
            if (strings[0].equalsIgnoreCase("help")) {
                if(strings.length==2) processHelp(commandSender, strings[1].equalsIgnoreCase("attributes"));
                else processHelp(commandSender,false);
                return true;
            }
            if (strings[0].equalsIgnoreCase("create")) {
                if (!commandSender.hasPermission("windTrace.admin.create")) {
                    commandSender.sendMessage(plugin.getLocalizedText("noPermission"));
                    return true;
                }

                // 参数长度判断
                if (strings.length == 2) {
                    // 只提供了内部名 → 尝试加载已存在地图进行编辑
                    String internalName = strings[1];

                    // 检查玩家是否已在编辑模式
                    if (commandSender instanceof Player && editingModeMap.containsKey((Player) commandSender)) {
                        commandSender.sendMessage(plugin.getLocalizedText("alreadyInEditingMode"));
                        return true;
                    }

                    // 检查地图是否存在
                    if (plugin.mapExists(internalName)) {
                        WTMap existingMap = plugin.getMap(internalName);
                        if (existingMap != null) {
                            // 从配置文件重新加载地图数据（确保最新配置）
                            reloadMapFromConfig(existingMap);

                            if (commandSender instanceof Player) {
                                Player player = (Player) commandSender;
                                player.teleport(existingMap.getCenter());
                                editingModeMap.put(player, existingMap);
                            }

                            plugin.getLocalizedList("createSuccessful").forEach(st ->
                                    commandSender.sendMessage(st.replace("{internalName}", internalName))
                            );
                            return true;
                        } else {
                            // 地图记录存在但对象为空（异常情况）
                            commandSender.sendMessage(plugin.getLocalizedText("createFailedWorldNotExists")
                                    .replace("{internalName}", internalName));
                            return true;
                        }
                    } else {
                        // 地图不存在，提示需提供显示名创建新地图
                        commandSender.sendMessage(plugin.getLocalizedText("mapNotFound")
                                .replace("{name}", internalName));
                        commandSender.sendMessage(plugin.getLocalizedText("help.create"));
                        return true;
                    }
                } else if (strings.length == 3) {
                    // 原有创建逻辑（内部名 + 显示名）
                    if (commandSender instanceof Player && editingModeMap.containsKey((Player) commandSender)) {
                        commandSender.sendMessage(plugin.getLocalizedText("alreadyInEditingMode"));
                        return true;
                    }

                    WTMap mapObject = createNewMap(strings[1], strings[2]);
                    if (mapObject == null) {
                        commandSender.sendMessage(plugin.getLocalizedText("createFailedWorldNotExists")
                                .replace("{internalName}", strings[1]));
                        commandSender.sendMessage(plugin.getLocalizedText("help.create"));
                        return true;
                    }

                    World targetWorld = mapObject.getGameWorld();
                    if (targetWorld == null) {
                        commandSender.sendMessage(plugin.getLocalizedText("createFailedWorldNotExists")
                                .replace("{internalName}", strings[1]));
                        commandSender.sendMessage(plugin.getLocalizedText("help.create"));
                        return true;
                    }

                    if (commandSender instanceof Player) {
                        Player player = (Player) commandSender;
                        player.teleport(mapObject.getCenter());
                        editingModeMap.put(player, mapObject);
                    }

                    plugin.getLocalizedList("createSuccessful").forEach(st ->
                            commandSender.sendMessage(st.replace("{internalName}", strings[1]))
                    );
                    return true;
                } else {
                    // 参数数量错误
                    commandSender.sendMessage(plugin.getLocalizedText("help.create"));
                    return true;
                }
            }
            if(strings[0].equalsIgnoreCase("setDevice")){
                if(!commandSender.hasPermission("windTrace.admin.setDevice")){
                    commandSender.sendMessage(plugin.getLocalizedText("noPermission"));return true;
                }
                if(!(commandSender instanceof Player)){
                    commandSender.sendMessage(plugin.getLocalizedText("commandFailed"));
                    return true;
                }
                Player op=(Player) commandSender;
                // 检查玩家是否在编辑模式（即editingModeMap中是否有该玩家）
                if(!editingModeMap.containsKey(op)){
                    op.sendMessage(plugin.getLocalizedText("notInEditingMode"));
                    return true;
                }
                if(inRemovingSignalingDevicePlayers.contains(op)){
                    op.sendMessage(plugin.getLocalizedText("alreadyInSettingSignalingDeviceMode")); // 如果没有这个文本，可以不加或者用其他方式提示
                    return true;
                }
                // 如果已经在设置发信机列表中，则不再重复添加（可选，根据需求）
                if(inSettingSignalingDevicePlayers.contains(op)){
                    op.sendMessage(plugin.getLocalizedText("alreadyInSettingSignalingDeviceMode")); // 如果没有这个文本，可以不加或者用其他方式提示
                    return true;
                }
                inSettingSignalingDevicePlayers.add(op);
                plugin.getLocalizedList("signalingDeviceSet").forEach(op::sendMessage);
                Bukkit.getScheduler().runTaskLater(plugin,()->{
                    inSettingSignalingDevicePlayers.remove(op);
                },1200);
            }
            if(strings[0].equalsIgnoreCase("removeDevice")){
                if(!commandSender.hasPermission("windTrace.admin.removeDevice")){
                    commandSender.sendMessage(plugin.getLocalizedText("noPermission"));return true;
                }
                if(!(commandSender instanceof Player)){
                    commandSender.sendMessage(plugin.getLocalizedText("commandFailed"));
                    return true;
                }
                Player op=(Player) commandSender;
                // 检查玩家是否在编辑模式（即editingModeMap中是否有该玩家）
                if(!editingModeMap.containsKey(op)){
                    op.sendMessage(plugin.getLocalizedText("notInEditingMode"));
                    return true;
                }
                if(inSettingSignalingDevicePlayers.contains(op)){
                    op.sendMessage(plugin.getLocalizedText("alreadyInSettingSignalingDeviceMode")); // 如果没有这个文本，可以不加或者用其他方式提示
                    return true;
                }
                if(inRemovingSignalingDevicePlayers.contains(op)){
                    op.sendMessage(plugin.getLocalizedText("alreadyInSettingSignalingDeviceMode")); // 如果没有这个文本，可以不加或者用其他方式提示
                    return true;
                }
                // 如果已经在设置发信机列表中，则不再重复添加（可选，根据需求）

                inRemovingSignalingDevicePlayers.add(op);
                plugin.getLocalizedList("signalingDeviceRemoving").forEach(op::sendMessage);
                Bukkit.getScheduler().runTaskLater(plugin,()->{
                    inRemovingSignalingDevicePlayers.remove(op);
                },1200);
            }
            if(strings[0].equalsIgnoreCase("attributes")){
                if (!(commandSender instanceof Player)) {
                    commandSender.sendMessage(plugin.getLocalizedText("commandFailed"));
                    return true;
                }
                Player op = (Player) commandSender;
                if (!editingModeMap.containsKey(op)) {
                    op.sendMessage(plugin.getLocalizedText("notInEditingMode"));
                    return true;
                }
                WTMap mapObject = editingModeMap.get(op);

                // 处理 addDisguiseBlock / removeDisguiseBlock（无需额外参数）
                if (strings.length == 2) {
                    if (strings[1].equalsIgnoreCase("addDisguiseBlock")) {
                        if (!commandSender.hasPermission("windTrace.admin.attributes.addDisguiseBlock")) {
                            commandSender.sendMessage(plugin.getLocalizedText("noPermission"));
                            return true;
                        }
                        if (!plugin.isDisguiseLibExists()) {
                            commandSender.sendMessage(plugin.getLocalizedText("disguiseDependencyNotFound"));
                            return true;
                        }
                        ItemStack item = op.getItemInHand();
                        if (!item.getType().isBlock()) {
                            commandSender.sendMessage(plugin.getLocalizedText("notABlock"));
                            return true;
                        }
                        Material material = item.getType();
                        mapObject.addADisguiseableBlock(material);
                        op.sendMessage(plugin.getLocalizedText("addDisguiseBlock")
                                .replace("{MATERIAL}", material.name()));
                        return true;
                    }
                    if (strings[1].equalsIgnoreCase("removeDisguiseBlock")) {
                        if (!commandSender.hasPermission("windTrace.admin.attributes.removeDisguiseBlock")) {
                            commandSender.sendMessage(plugin.getLocalizedText("noPermission"));
                            return true;
                        }
                        if (!plugin.isDisguiseLibExists()) {
                            commandSender.sendMessage(plugin.getLocalizedText("disguiseDependencyNotFound"));
                            return true;
                        }
                        ItemStack item = op.getItemInHand();
                        if (!item.getType().isBlock()) {
                            commandSender.sendMessage(plugin.getLocalizedText("notABlock"));
                            return true;
                        }
                        Material material = item.getType();
                        if (mapObject.getDisguiseableBlocks().contains(material)) {
                            mapObject.removeADisguiseableBlock(material);
                            op.sendMessage(plugin.getLocalizedText("removeDisguiseBlock")
                                    .replace("{MATERIAL}", material.name()));
                        } else {
                            op.sendMessage(plugin.getLocalizedText("disguiseBlockNotInList")
                                    .replace("{MATERIAL}", material.name()));
                        }
                        return true;
                    }
                }
                if(strings.length!=3){
                    processHelp(commandSender,true);
                    return true;
                }
                if(!editingModeMap.containsKey(op)){
                    op.sendMessage(plugin.getLocalizedText("notInEditingMode"));
                    return true;
                }else {
                    if(strings[1].equalsIgnoreCase("addDisguiseBlock")){
                        if(!commandSender.hasPermission("windTrace.admin.attributes.hunterAmount")){
                            commandSender.sendMessage(plugin.getLocalizedText("noPermission"));return true;
                        }

                    }
                    if(strings[1].equalsIgnoreCase("mode")){
                        if(!commandSender.hasPermission("windTrace.admin.attributes.mode")){
                            commandSender.sendMessage(plugin.getLocalizedText("noPermission"));return true;
                        }
                        if(strings[2].equalsIgnoreCase("NORMAL")){
                            mapObject.setMode(WTMapMode.NORMAL);
                            op.sendMessage(plugin.getLocalizedText("modeSuccessfullySet").replace("{MODE}","NORMAL"));
                        } else if (strings[2].equalsIgnoreCase("WINTER")) {
                            mapObject.setMode(WTMapMode.WINTER);
                            op.sendMessage(plugin.getLocalizedText("modeSuccessfullySet").replace("{MODE}","WINTER"));
                        }else {
                            commandSender.sendMessage(plugin.getLocalizedText("invalidMode"));
                        }
                        editingModeMap.replace(op,mapObject);
                        return true;
                    }
                    if(strings[1].equalsIgnoreCase("minPlayers")){
                        if(!commandSender.hasPermission("windTrace.admin.attributes.minPlayers")){
                            commandSender.sendMessage(plugin.getLocalizedText("noPermission"));return true;
                        }
                        int newMinplayers;
                        try{
                            newMinplayers=Integer.valueOf(strings[2]);
                        }catch (NumberFormatException e){
                            op.sendMessage(plugin.getLocalizedText("invalidNumberFormat"));
                            return true;
                        }
                        if(newMinplayers<2){
                            op.sendMessage(plugin.getLocalizedText("invalidMinPlayers"));
                            newMinplayers=2;
                        }
                        if(!mapObject.setMinplayers(newMinplayers)){
                            op.sendMessage(plugin.getLocalizedText("minBiggerThanMax"));
                            return true;
                        }
                        editingModeMap.replace(op,mapObject);
                        op.sendMessage(plugin.getLocalizedText("minPlayersSuccessfullySet").replace("{MINPLAYERS}",String.valueOf(newMinplayers)));
                        return true;
                    }
                    if(strings[1].equalsIgnoreCase("maxPlayers")){
                        if(!commandSender.hasPermission("windTrace.admin.maxPlayers")){
                            commandSender.sendMessage(plugin.getLocalizedText("noPermission"));return true;
                        }
                        int newMaxplayers;
                        try{
                            newMaxplayers = Integer.valueOf(strings[2]);
                        } catch (NumberFormatException e){
                            op.sendMessage(plugin.getLocalizedText("invalidNumberFormat"));
                            return true;
                        }
                        if(newMaxplayers < 2){
                            op.sendMessage(plugin.getLocalizedText("invalidMaxPlayers"));
                            newMaxplayers = 2;
                        }
                        if(!mapObject.setMaxplayers(newMaxplayers)){
                            op.sendMessage(plugin.getLocalizedText("maxSmallerThanMin"));
                            return true;
                        }
                        editingModeMap.replace(op,mapObject);
                        op.sendMessage(plugin.getLocalizedText("maxPlayersSuccessfullySet").replace("{MAXPLAYERS}", String.valueOf(newMaxplayers)));
                        return true;
                    }
                    if(strings[1].equalsIgnoreCase("hunterAmount")){
                        if(!commandSender.hasPermission("windTrace.admin.hunterAmount")){
                            commandSender.sendMessage(plugin.getLocalizedText("noPermission"));return true;
                        }
                        if(!strings[2].endsWith("%")){
                            try {
                                int hunterAmount = Integer.parseInt(strings[2]);
                                if(hunterAmount<0){
                                    op.sendMessage(plugin.getLocalizedText("invalidNumberFormat"));
                                    return true;
                                }
                                if(hunterAmount==0){
                                    op.sendMessage(plugin.getLocalizedText("zeroHunterAmount"));
                                    hunterAmount=1;
                                }
                                if(hunterAmount>=mapObject.getMaxplayers()){
                                    op.sendMessage(plugin.getLocalizedText("atLeastOneRebel"));
                                    hunterAmount=mapObject.getMaxplayers()-1;
                                }
                                if (!mapObject.setHunteramount(hunterAmount)) {
                                    op.sendMessage(plugin.getLocalizedText("hunterMoreThanMax"));
                                    return true;
                                }
                                editingModeMap.replace(op, mapObject);
                                op.sendMessage(plugin.getLocalizedText("hunterAmountSuccessfullySet").replace("{HUNTERAMOUNT}", String.valueOf(hunterAmount)));

                            } catch (NumberFormatException e) {
                                op.sendMessage(plugin.getLocalizedText("invalidNumberFormat"));
                                return true;
                            }
                        }
                        else {
                            String percentStr = strings[2].substring(0, strings[2].length() - 1);
                            double percent;
                            try {
                                percent = Double.valueOf(percentStr) / 100.0;
                            } catch (NumberFormatException e) {
                                op.sendMessage(plugin.getLocalizedText("invalidNumberFormat"));
                                return true;
                            }
                            if(percent>1.00||percent<0){
                                op.sendMessage(plugin.getLocalizedText("invalidPercent"));
                                return true;
                            }
                            int newHunterAmount = (int) Math.floor(mapObject.getMaxplayers() * percent);
                            if(newHunterAmount==0){
                                op.sendMessage(plugin.getLocalizedText("zeroHunterAmount"));
                                newHunterAmount=1;
                            }
                            if(newHunterAmount==mapObject.getMaxplayers()){
                                op.sendMessage(plugin.getLocalizedText("atLeastOneRebel"));
                                newHunterAmount=mapObject.getMaxplayers()-1;
                            }
                            if (!mapObject.setHunteramount(newHunterAmount)) {
                                op.sendMessage(plugin.getLocalizedText("hunterMoreThanMax"));
                                return true;
                            }
                            editingModeMap.replace(op, mapObject);
                            op.sendMessage(plugin.getLocalizedText("hunterAmountSuccessfullySetPercent")
                                    .replace("{HUNTERAMOUNT}", String.valueOf(newHunterAmount))
                                    .replace("{PERCENT}", percentStr));
                        }
                    }
                }
            }
            if(strings[0].equalsIgnoreCase("setCage")){
                if(!commandSender.hasPermission("windTrace.admin.setCage")){
                    commandSender.sendMessage(plugin.getLocalizedText("noPermission"));
                    return true;
                }
                if(!(commandSender instanceof Player)){
                    commandSender.sendMessage(plugin.getLocalizedText("commandFailed"));
                    return true;
                }
                Player op=(Player) commandSender;
                if(!editingModeMap.containsKey(op)){
                    op.sendMessage(plugin.getLocalizedText("notInEditingMode"));
                    return true;
                }
                WTMap mapObject = editingModeMap.get(op);
                mapObject.setCage(op.getLocation());
                editingModeMap.replace(op, mapObject);
                op.sendMessage(plugin.getLocalizedText("cageSuccessfullySet"));
                return true;
            }
            if(strings[0].equalsIgnoreCase("setCenter")){
                if(!commandSender.hasPermission("windTrace.admin.setCenter")){
                    commandSender.sendMessage(plugin.getLocalizedText("noPermission"));
                    return true;
                }
                if(!(commandSender instanceof Player)){
                    commandSender.sendMessage(plugin.getLocalizedText("commandFailed"));
                    return true;
                }
                Player op=(Player) commandSender;
                if(!editingModeMap.containsKey(op)){
                    op.sendMessage(plugin.getLocalizedText("notInEditingMode"));
                    return true;
                }
                WTMap mapObject = editingModeMap.get(op);
                mapObject.setCenter(op.getLocation());
                editingModeMap.replace(op, mapObject);
                op.sendMessage(plugin.getLocalizedText("centerSuccessfullySet"));
                return true;
            }
            if(strings[0].equalsIgnoreCase("save")){
                if(!commandSender.hasPermission("windTrace.admin.save")){
                    commandSender.sendMessage(plugin.getLocalizedText("noPermission"));return true;
                }
                if(!(commandSender instanceof Player)){
                    commandSender.sendMessage(plugin.getLocalizedText("commandFailed"));
                    return true;
                }
                Player op=(Player) commandSender;
                if(!editingModeMap.containsKey(op)){
                    op.sendMessage(plugin.getLocalizedText("notInEditingMode"));
                    return true;
                }
                plugin.saveMap(editingModeMap.get(op));
                op.sendMessage(plugin.getLocalizedText("saveSuccessfully"));
            }
            if(strings[0].equalsIgnoreCase("forceStart")){
                if(!commandSender.hasPermission("windTrace.admin.forcestart")){
                    commandSender.sendMessage(plugin.getLocalizedText("noPermission"));return true;
                }
                if(!(commandSender instanceof Player)){
                    commandSender.sendMessage(plugin.getLocalizedText("commandFailed"));
                    return true;
                }
                Player player=(Player) commandSender;
                if(plugin.getPlayerOnWhichGame(player)==null){
                    player.sendMessage(plugin.getLocalizedText("notInGame"));
                    return true;
                }
                WindTraceGame game=plugin.getPlayerOnWhichGame(player);
                if(game.getStatus()!= GameStatus.NotStarted){
                    player.sendMessage(plugin.getLocalizedText("alreadyStarted"));
                    return true;
                }
                game.setTimer(10);
                game.inForceStart=true;
                player.sendMessage(plugin.getLocalizedText("forceStarted"));
            }
            if(strings[0].equalsIgnoreCase("setLobby")){
                if(!commandSender.hasPermission("windTrace.admin.setLobby")){
                    commandSender.sendMessage(plugin.getLocalizedText("noPermission"));return true;
                }
                if(!(commandSender instanceof Player)){
                    commandSender.sendMessage(plugin.getLocalizedText("commandFailed"));
                    return true;
                }
                Player player=(Player) commandSender;
                Location location=player.getLocation();
                plugin.setLobby(location);
                commandSender.sendMessage(plugin.getLocalizedText("lobbySet"));return true;
            }

            //以下为普通玩家可以使用的指令
            if(strings[0].equalsIgnoreCase("join")){
                if(!(commandSender instanceof Player)){
                    commandSender.sendMessage(plugin.getLocalizedText("commandFailed"));
                    return true;
                }
                Player player=(Player) commandSender;
                if(plugin.getPlayerOnWhichGame(player)!=null){
                    player.sendMessage(plugin.getLocalizedText("cannotOpenGUIDuringGame"));
                    return true;
                }
                if(strings.length!=2)GUI.openNormalUIFor(player);
                else{
                    for(WindTraceGame game:plugin.gamesAvailable){
                        if(game.getPlayingOnMap().getMapName().equalsIgnoreCase(strings[1])){
                            String error=game.joinGame(player);
                            if(error.equalsIgnoreCase("")){
                                player.sendMessage(plugin.getLocalizedText("joining").replace("{displayName}",game.getPlayingOnMap().getDisplayName()));
                            }else if(error.equalsIgnoreCase("mapFull")) player.sendMessage(plugin.getLocalizedText("mapFull").replace("{displayName}",game.getPlayingOnMap().getDisplayName()));
                            else if(error.equalsIgnoreCase("alreadyExists")) player.sendMessage(plugin.getLocalizedText("alreadyExists").replace("{displayName}",game.getPlayingOnMap().getDisplayName()));
                            return true;
                        }
                    }
                    player.sendMessage(plugin.getLocalizedText("mapNotFound").replace("{name}",strings[1]));
                }
            }
            if (strings[0].equalsIgnoreCase("stats")) {
                if (strings.length == 1) {
                    // 查看自己的统计
                    if (!(commandSender instanceof Player)) {
                        commandSender.sendMessage(plugin.getLocalizedText("commandFailed"));
                        return true;
                    }
                    Player player = (Player) commandSender;
                    List<String> stats = plugin.getPlayerStatics(player.getUniqueId());
                    for (String line : stats) {
                        player.sendMessage(line);
                    }
                    return true;
                } else if (strings.length == 2) {
                    // 查看他人的统计（需要权限）
                    if (!commandSender.hasPermission("windTrace.admin.viewOthersStat")) {
                        commandSender.sendMessage(plugin.getLocalizedText("noPermission"));
                        return true;
                    }
                    String targetName = strings[1];
                    Player targetPlayer = Bukkit.getPlayerExact(targetName);
                    UUID targetUUID;

                    if (targetPlayer != null) {
                        targetUUID = targetPlayer.getUniqueId();
                    } else {
                        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetName);
                        targetUUID = offlinePlayer.getUniqueId(); // 即使从未加入，也能获取 UUID
                    }

                    List<String> stats = plugin.getPlayerStatics(targetUUID);
                    for (String line : stats) {
                        commandSender.sendMessage(line);
                    }
                    return true;
                } else {
                    // 参数错误，显示帮助
                    commandSender.sendMessage(plugin.getLocalizedText("help.stat"));
                    return true;
                }
            }
            if(strings[0].equalsIgnoreCase("rejoin")||strings[0].equalsIgnoreCase("r")){
                if(!(commandSender instanceof Player)){
                    commandSender.sendMessage(plugin.getLocalizedText("commandFailed"));
                    return true;
                }
                //TODO:重新加入逻辑...
            }


            return true;
        }
        return false;
    }

    private WTMap createNewMap(String internalName, String displayName) {
        // 首先检查地图是否已经存在
        if(plugin.mapExists(internalName)) {
            // 获取已存在的地图对象
            WTMap existingMap = plugin.getMap(internalName);
            if(existingMap != null) {
                // 重新从配置文件加载地图数据，确保配置是最新的
                reloadMapFromConfig(existingMap);
                return existingMap;
            }
        }

        // 如果地图不存在，创建新地图
        if(Bukkit.getWorld(internalName)==null){
            return null;
        }
        World gameWorld= Bukkit.getWorld(internalName);
        Location center=new Location(gameWorld,0,72,0);
        Location cage=new Location(gameWorld,0,72,0);
        int hunterAmount=plugin.config.getInt("defaultHunterAmount");
        int minPlayers=plugin.config.getInt("defaultMinPlayers");
        int maxPlayers=plugin.config.getInt("defaultMaxPlayers");
        List<Block> signalingDevices = new ArrayList<>();
        WTMap mapObject=new WTMap(gameWorld,internalName,displayName,WTMapMode.NORMAL,minPlayers,maxPlayers,hunterAmount,signalingDevices,center,cage);
        plugin.saveMap(mapObject);
        return mapObject;
    }

    // 新增辅助方法：从配置文件重新加载地图配置
    private void reloadMapFromConfig(WTMap map) {
        String mapName = map.getMapName();

        // 重新读取 maps.yml 文件
        File mapsFile = new File(plugin.getDataFolder(), "maps.yml");
        FileConfiguration mapsConfig = YamlConfiguration.loadConfiguration(mapsFile);

        // 检查地图配置是否存在
        if (mapsConfig.contains(mapName)) {
            ConfigurationSection mapSection = mapsConfig.getConfigurationSection(mapName);
            if (mapSection != null) {
                // 更新地图对象的配置
                int maxPlayers = mapSection.getInt("maxPlayers", map.getMaxplayers());
                int minPlayers = mapSection.getInt("minPlayers", map.getMinplayers());
                int hunterAmount = mapSection.getInt("hunters", map.getHunteramount());

                // 设置新的值
                map.setMaxplayers(maxPlayers);
                map.setMinplayers(minPlayers);
                map.setHunteramount(hunterAmount);

                // 更新显示名称（如果有变化）
                String displayName = mapSection.getString("displayName", map.getDisplayName());
                if (!displayName.equals(map.getDisplayName())) {
                    map.setDisplayName(displayName);
                }

                // 更新地图模式
                String modeStr = mapSection.getString("type", "NORMAL").toUpperCase();
                try {
                    WTMapMode mode = WTMapMode.valueOf(modeStr);
                    map.setMode(mode);
                } catch (IllegalArgumentException e) {
                    // 如果模式无效，保持原样
                }

                // 更新中心位置
                ConfigurationSection centerSection = mapSection.getConfigurationSection("center");
                if (centerSection != null) {
                    double x = centerSection.getDouble("x", map.getCenter().getX());
                    double y = centerSection.getDouble("y", map.getCenter().getY());
                    double z = centerSection.getDouble("z", map.getCenter().getZ());
                    Location newCenter = new Location(map.getGameWorld(), x, y, z);
                    map.setCenter(newCenter);
                }

                // 更新笼子位置
                ConfigurationSection cageSection = mapSection.getConfigurationSection("cage");
                if (cageSection != null) {
                    double x = cageSection.getDouble("x", map.getCage().getX());
                    double y = cageSection.getDouble("y", map.getCage().getY());
                    double z = cageSection.getDouble("z", map.getCage().getZ());
                    Location newCage = new Location(map.getGameWorld(), x, y, z);
                    map.setCage(newCage);
                }

                // 重新加载信号设备（如果需要的话）
                // 注意：这可能会比较复杂，因为信号设备是Block对象
                // 如果不需要重新加载信号设备，可以注释掉这部分
                List<Block> signalingDevices = new ArrayList<>();
                ConfigurationSection devicesSection = mapSection.getConfigurationSection("signalDevices");
                if (devicesSection != null) {
                    for (String deviceKey : devicesSection.getKeys(false)) {
                        ConfigurationSection deviceSection = devicesSection.getConfigurationSection(deviceKey);
                        if (deviceSection != null) {
                            double x = deviceSection.getDouble("x", 0);
                            double y = deviceSection.getDouble("y", 0);
                            double z = deviceSection.getDouble("z", 0);
                            Block block = map.getGameWorld().getBlockAt((int) x, (int) y, (int) z);
                            signalingDevices.add(block);
                        }
                    }
                    // 清除原有信号设备并添加新的
                    // 注意：需要确保WTMap类有清除信号设备的方法
                    // 如果WTMap类没有相应方法，可能需要添加
                }
            }
        }
    }
    private void processHelp(CommandSender a, Boolean isAttributes) {
        if (isAttributes) {
            if(a.hasPermission("windTrace.admin.attributes.mode"))a.sendMessage(plugin.getLocalizedText("help.attributes.mode"));
            if(a.hasPermission("windTrace.admin.attributes.minplayers"))a.sendMessage(plugin.getLocalizedText("help.attributes.minplayers"));
            if(a.hasPermission("windTrace.admin.attributes.maxplayers"))a.sendMessage(plugin.getLocalizedText("help.attributes.maxplayers"));
            if(a.hasPermission("windTrace.admin.attributes.hunteramount"))a.sendMessage(plugin.getLocalizedText("help.attributes.hunteramount"));
        } else {
            a.sendMessage(plugin.getLocalizedText("help.header"));
            a.sendMessage(plugin.getLocalizedText("help.help"));
            a.sendMessage(plugin.getLocalizedText("help.join"));
            a.sendMessage(plugin.getLocalizedText("help.rejoin"));
            a.sendMessage(plugin.getLocalizedText("help.stat"));
            if (a.hasPermission("windTrace.admin.create")) a.sendMessage(plugin.getLocalizedText("help.create"));
            if (a.hasPermission("windTrace.admin.attributes"))
                a.sendMessage(plugin.getLocalizedText("help.attributes.out"));
            if (a.hasPermission("windTrace.admin.save"))
                a.sendMessage(plugin.getLocalizedText("help.save"));
            if (a.hasPermission("windTrace.admin.setCage"))
                a.sendMessage(plugin.getLocalizedText("help.setCage"));
            if (a.hasPermission("windTrace.admin.setCenter"))
                a.sendMessage(plugin.getLocalizedText("help.setCenter"));
            if (a.hasPermission("windTrace.admin.setdevice")) a.sendMessage(plugin.getLocalizedText("help.setdevice"));
            if (a.hasPermission("windTrace.admin.removedevice")) a.sendMessage(plugin.getLocalizedText("help.removedevice"));
            if (a.hasPermission("windTrace.admin.forcestart"))
                a.sendMessage(plugin.getLocalizedText("help.forcestart"));
        }
    }
}

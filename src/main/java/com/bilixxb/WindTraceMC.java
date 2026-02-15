package com.bilixxb;

//终于！！开发完成了！！！
//本项目于2026年2月14日基本完成第一版
//暂未添加的功能：重新加入,信标光柱，事件列表
//以后可能会添加的功能：地图特殊方块，成就系统
//暂未修复的bug：保存地图时伪装检测

//(这代码有一大半都是ai写的，我是不是废了[doge])
//Powered By DreamLand Team(?)

import com.bilixxb.GamePlayLogic.Invis;
import com.bilixxb.GamePlayLogic.WindTraceGame;
import com.bilixxb.GamePlayLogic.WindTraceListener;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;


import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class WindTraceMC extends JavaPlugin {
    private FileConfiguration lang;
    private File langFolder;
    private File configFile;
    public FileConfiguration config;
    private String currentLanguage;
    private File langFile;
    private File mapsFile;
    private FileConfiguration maps;
    List<WTMap> LoadedMaps=new ArrayList<WTMap>();
    List<WindTraceGame> gamesAvailable=new ArrayList<WindTraceGame>();
    private Boolean ProtocolLibExists=false;
    private Invis invis;
    private Boolean DisguiseLibExists=false;
    private Statics statics;
    @Override
    public void onEnable() {
        if (getServer().getPluginManager().getPlugin("HolographicDisplays") == null) {
            getLogger().severe("本插件需要Holographic Displays插件以显示发信机信息！");
            getLogger().severe("THIS PLUGIN REQUIRES Holographic Displays TO DISPLAY SIGNALING DEVICES INFO!");
            getLogger().severe("请前往 dev.bukkit.org/bukkit-plugins/holographic-displays 以下载该依赖！");
            getLogger().severe("PLEASE GO dev.bukkit.org/bukkit-plugins/holographic-displays TO DOWNLOAD THIS DEPENDENCY!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (getServer().getPluginManager().getPlugin("ProtocolLib") == null) {
            getLogger().severe("本插件需要ProtocolLib依赖！");
            getLogger().severe("THIS PLUGIN REQUIRES ProtocolLib!");
            getLogger().severe("请前往 www.spigotmc.org/resources/protocollib.1997/ 以下载该依赖！");
            getLogger().severe("PLEASE GO www.spigotmc.org/resources/protocollib.1997/ TO DOWNLOAD THIS DEPENDENCY!");
            ProtocolLibExists=false;
            invis=null; // 不要初始化Invis
        }else {
            ProtocolLibExists=true;
            invis=new Invis(this); // 只有在ProtocolLib存在时才初始化
            if(invis!=null)invis.cleanupAllInvisibility();
        }
        if (getServer().getPluginManager().getPlugin("LibsDisguises") == null) {
            getLogger().severe("本插件需要LibsDisguises依赖以实现伪装功能！");
            getLogger().severe("THIS PLUGIN REQUIRES LibsDisguises!");
            getLogger().severe("请前往 github.com/libraryaddict/LibsDisguise 以下载该依赖！");
            getLogger().severe("PLEASE GO github.com/libraryaddict/LibsDisguise TO DOWNLOAD THIS DEPENDENCY!");
            DisguiseLibExists=false;
        }else {
            DisguiseLibExists=true;
        }

        try {
            saveDefaultConfig();
            configFile = new File(getDataFolder(), "config.yml");
            loadConfig();

            getServer().getPluginManager().registerEvents(new EditingModeListener(this), this);
            getServer().getPluginManager().registerEvents(new joinerGUI(this),this);
            getServer().getPluginManager().registerEvents(new WindTraceListener(this),this);
            if (lang != null && lang.contains("startup")) {
                lang.getList("startup").forEach(string -> {
                    getLogger().info(String.valueOf(string));
                });
            }

            // 延迟加载地图，等待世界加载完成
            getServer().getScheduler().runTaskLater(this, new Runnable() {
                @Override
                public void run() {
                    loadMaps();
                    loadGames();
                }
            }, 100L);

        } catch (Exception e) {
            e.printStackTrace();
            setEnabled(false);
        }
        this.getCommand("wt").setExecutor(new WTCommands(this));
        this.getCommand("wt").setTabCompleter(new TabExecutor(this));

        //数据库相关
        statics=new Statics(new File(getDataFolder(),"statics.db").getAbsolutePath());
        initDatabase();
    }
    private void initDatabase() {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS players (" +
                "uuid VARCHAR(36) PRIMARY KEY," +
                "name VARCHAR(16) NOT NULL," +
                "normalPlayed INTEGER DEFAULT 0," +
                "winterPlayed INTEGER DEFAULT 0,"+
                "hunterActed INTEGER DEFAULT 0,"+
                "rebelActed INTEGER DEFAULT 0,"+
                "signalingDevices INTEGER DEFAULT 0,"+
                "capturedRebels INTEGER DEFAULT 0)";

        try (Connection conn = statics.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL);
        } catch (SQLException e) {
            getLogger().severe("无法创建数据表: " + e.getMessage());
        }
    }
    public List<String> getPlayerStatics(UUID uuid) {
        List<String> result = new ArrayList<>();
        String sql = "SELECT normalPlayed, winterPlayed, hunterActed, rebelActed, signalingDevices, capturedRebels FROM players WHERE uuid = ?";

        try (Connection conn = statics.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, uuid.toString());
            ResultSet rs = pstmt.executeQuery();

            int normal = 0, winter = 0, hunter = 0, rebel = 0, devices = 0, captures = 0;
            if (rs.next()) {
                normal = rs.getInt("normalPlayed");
                winter = rs.getInt("winterPlayed");
                hunter = rs.getInt("hunterActed");
                rebel = rs.getInt("rebelActed");
                devices = rs.getInt("signalingDevices");
                captures = rs.getInt("capturedRebels");
            }

            List<String> rawList = getLocalizedList("stats.stat");
            for (String line : rawList) {
                line = line.replace("{normal}", String.valueOf(normal))
                        .replace("{winter}", String.valueOf(winter))
                        .replace("{hunter}", String.valueOf(hunter))
                        .replace("{rebel}", String.valueOf(rebel))
                        .replace("{signalingDevices}", String.valueOf(devices))
                        .replace("{capturedRebels}", String.valueOf(captures));
                result.add(line);
            }

        } catch (SQLException e) {
            getLogger().severe("获取玩家统计数据失败: " + e.getMessage());
            result.add("§c无法读取统计数据，请稍后重试。");
        }
        return result;
    }

    public void addPlayerStatics(Player player, WTMapMode mode, String identity, int i) {
        String uuid = player.getUniqueId().toString();
        String name = player.getName();

        // 1. 确保玩家记录存在（INSERT OR IGNORE）
        String insertSql = "INSERT OR IGNORE INTO players (uuid, name) VALUES (?, ?)";
        try (Connection conn = statics.getConnection();
             PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {

            insertStmt.setString(1, uuid);
            insertStmt.setString(2, name);
            insertStmt.executeUpdate();

            // 2. 构建更新语句
            StringBuilder updateSql = new StringBuilder("UPDATE players SET name = ?, ");

            // 模式计数 +1
            if (mode == WTMapMode.NORMAL) {
                updateSql.append("normalPlayed = normalPlayed + 1, ");
            } else {
                updateSql.append("winterPlayed = winterPlayed + 1, ");
            }

            // 根据身份增加对应字段
            if ("hunter".equalsIgnoreCase(identity)) {
                updateSql.append("hunterActed = hunterActed + 1, capturedRebels = capturedRebels + ? ");
            } else if ("rebel".equalsIgnoreCase(identity)) {
                updateSql.append("rebelActed = rebelActed + 1, signalingDevices = signalingDevices + ? ");
            } else {
                // 未知身份，只更新模式计数和名字
                updateSql.append("hunterActed = hunterActed, rebelActed = rebelActed "); // 无变化
            }

            updateSql.append("WHERE uuid = ?");

            try (PreparedStatement updateStmt = conn.prepareStatement(updateSql.toString())) {
                int paramIndex = 1;
                updateStmt.setString(paramIndex++, name);

                if ("hunter".equalsIgnoreCase(identity) || "rebel".equalsIgnoreCase(identity)) {
                    updateStmt.setInt(paramIndex++, i);
                }

                updateStmt.setString(paramIndex, uuid);
                updateStmt.executeUpdate();
            }

        } catch (SQLException e) {
            getLogger().severe("更新玩家统计数据失败: " + e.getMessage());
        }
    }

    public Boolean isProtocolLibExists() {
        return ProtocolLibExists;
    }
    public Boolean isDisguiseLibExists() {
        return DisguiseLibExists;
    }

    public WindTraceGame getPlayerOnWhichGame(Player player) {
        // 遍历所有可用游戏
        for (WindTraceGame game : gamesAvailable) {
            // 检查该游戏的玩家列表中是否包含指定玩家
            if (game.getPlaying().contains(player)) {
                return game;
            }
        }
        return null;
    }
    public Location getLobby(){
        String worldName = config.getString("lobby.World");
        if(worldName == null || Bukkit.getWorld(worldName) == null) {
            return null;
        }

        // 先创建 Location 对象实例
        Location lobby = new Location(
                Bukkit.getWorld(worldName),
                config.getDouble("lobby.X"),  // 注意：应该使用 getDouble() 而不是 getInt()
                config.getDouble("lobby.Y"),
                config.getDouble("lobby.Z")
        );

        return lobby;
    }
    public void setLobby(Location location){
        config.set("lobby.World", location.getWorld().getName());
        config.set("lobby.X", location.getX());  // 这些已经是 double 类型
        config.set("lobby.Y", location.getY());
        config.set("lobby.Z", location.getZ());
        try {
            config.save(configFile);
        } catch (IOException e) {
            getLogger().warning("Failed to save lobby location: " + e.getMessage());
        }
    }

    private void loadGames(){
        // 不要完全清空 gamesAvailable，而是更新现有的游戏
        List<WindTraceGame> newGames = new ArrayList<>();

        for(WTMap map:LoadedMaps){
            // 查找是否已经存在这个地图的游戏
            WindTraceGame existingGame = null;
            for(WindTraceGame game : gamesAvailable){
                if(game.getPlayingOnMap().getMapName().equals(map.getMapName())){
                    existingGame = game;
                    break;
                }
            }

            if(existingGame != null){
                // 保留现有的游戏对象（包含玩家列表）
                existingGame.setPlayingOnMap(map); // 需要添加这个方法
                newGames.add(existingGame);
            } else {
                newGames.add(new WindTraceGame(map,this));
            }
        }

        gamesAvailable = newGames;
    }
    private void loadConfig() {
        langFolder = new File(getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        config = new YamlConfiguration();

        // 加载主配置文件
        if (!configFile.exists()) {
            saveDefaultConfig();
            currentLanguage = Locale.getDefault().getLanguage();
            // 确保语言代码有效
            if (currentLanguage == null || currentLanguage.isEmpty()) {
                currentLanguage = "en_US";
            }
            config.set("lang", currentLanguage);
            try {
                config.save(configFile);
            } catch (IOException e) {
            }
        }

        try {
            config.load(configFile);
            currentLanguage = config.getString("lang", "en_US");
        } catch (IOException | InvalidConfigurationException e) {
            currentLanguage = "en_US";
        }

        // 复制并加载语言文件
        copyLanguageFile(currentLanguage);

        if (langFile != null && langFile.exists()) {
            lang = new YamlConfiguration();  // 初始化 lang
            try {
                lang.load(langFile);
            } catch (IOException | InvalidConfigurationException e) {
                lang = null;  // 设置为 null 以避免后续错误
            }
        } else {
            lang = new YamlConfiguration();  // 创建一个空的配置
        }

    }

    private void copyLanguageFile(String langCode) {
        String resourcePath = "lang/" + langCode + ".yml";

        // 检查插件内部资源
        try (InputStream inputStream = getResource(resourcePath)) {
            if (inputStream == null) {

                // 尝试使用默认英语文件
                if (!"en_US".equals(langCode)) {
                    copyLanguageFile("en_US");
                    return;
                }
                return;
            }

            File targetFile = new File(langFolder, langCode + ".yml");

            // 只复制文件如果不存在
            if (!targetFile.exists()) {
                Files.copy(inputStream, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            langFile = targetFile;  // 设置 langFile 引用
        } catch (IOException e) {
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        try {
            config.save(configFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            maps.save(mapsFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if(invis!=null)invis.cleanupAllInvisibility();
    }

    public String getLocalizedText(String s){
        return lang.getString(s);
    }
    public List<String> getLocalizedList(String s){
        return (List<String>) lang.getList(s);
    }
    private void loadMaps() {
        mapsFile = new File(getDataFolder(), "maps.yml");

        if (!mapsFile.exists()) {
            try (InputStream inputStream = getResource("maps.yml")) {
                if (inputStream != null) {
                    Files.copy(inputStream, mapsFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } else {
                    mapsFile.createNewFile();
                }
            } catch (IOException e) {
                // 忽略
            }
        }

        maps = new YamlConfiguration();
        try {
            maps.load(mapsFile);
            LoadedMaps = new ArrayList<>();

            for (String mapKey : maps.getKeys(false)) {
                ConfigurationSection mapSection = maps.getConfigurationSection(mapKey);
                if (mapSection == null) continue;

                String worldName = mapSection.getString("world");
                if (worldName == null || worldName.isEmpty()) continue;
                World world = getServer().getWorld(worldName);
                if (world == null) continue;

                String mapName = mapSection.getString("name", mapKey);
                String displayName = mapSection.getString("displayName", mapName);

                String typeStr = mapSection.getString("type", "normal").toUpperCase();
                WTMapMode mode;
                try {
                    mode = WTMapMode.valueOf(typeStr);
                } catch (IllegalArgumentException e) {
                    mode = WTMapMode.NORMAL;
                }

                int minplayers = mapSection.getInt("minPlayers", 2);
                int maxplayers = mapSection.getInt("maxPlayers", 8);
                int hunteramount = mapSection.getInt("hunters", 1);

                // 中心点
                ConfigurationSection centerSection = mapSection.getConfigurationSection("center");
                Location center = null;
                if (centerSection != null) {
                    double x = centerSection.getDouble("x", 0);
                    double y = centerSection.getDouble("y", 0);
                    double z = centerSection.getDouble("z", 0);
                    center = new Location(world, x, y, z);
                } else {
                    center = world.getSpawnLocation();
                }

                // 笼子
                ConfigurationSection cageSection = mapSection.getConfigurationSection("cage");
                Location cage = null;
                if (cageSection != null) {
                    double cageX = cageSection.getDouble("x", 0);
                    double cageY = cageSection.getDouble("y", 0);
                    double cageZ = cageSection.getDouble("z", 0);
                    cage = new Location(world, cageX, cageY, cageZ);
                } else {
                    cage = center.clone();
                }

                // 信号设备（坐标格式，保持不变）
                List<Block> signalingDevices = new ArrayList<>();
                ConfigurationSection devicesSection = mapSection.getConfigurationSection("signalDevices");
                if (devicesSection != null) {
                    for (String deviceKey : devicesSection.getKeys(false)) {
                        ConfigurationSection deviceSection = devicesSection.getConfigurationSection(deviceKey);
                        if (deviceSection != null) {
                            double x = deviceSection.getDouble("x", 0);
                            double y = deviceSection.getDouble("y", 0);
                            double z = deviceSection.getDouble("z", 0);
                            Block block = world.getBlockAt((int) x, (int) y, (int) z);
                            signalingDevices.add(block);
                        }
                    }
                }

                // ========== 可伪装方块材质加载（新格式）==========
                List<Material> disguiseableMaterials = new ArrayList<>();
                List<String> materialNames = mapSection.getStringList("disguiseableBlocks");
                if (materialNames != null) {
                    for (String name : materialNames) {
                        try {
                            Material material = Material.valueOf(name);
                            disguiseableMaterials.add(material);
                        } catch (IllegalArgumentException e) {
                        }
                    }
                }
                WTMap wtMap = new WTMap(world, mapName, displayName, mode,
                        minplayers, maxplayers, hunteramount,
                        signalingDevices, center, cage);

                // 添加伪装方块材质
                for (Material material : disguiseableMaterials) {
                    wtMap.addADisguiseableBlock(material);
                }

                LoadedMaps.add(wtMap);
            }
        } catch (InvalidConfigurationException | IOException e) {
            e.printStackTrace();
            maps = new YamlConfiguration();
        }
    }
    public Boolean saveMap(WTMap map){
        if(map==null)return Boolean.FALSE;
        World gameWorld=map.getGameWorld();
        String mapName=map.getMapName();
        String displayName=map.getDisplayName();
        WTMapMode mode=map.getMode();
        int minPlayers=map.getMinplayers();
        int maxPlayers=map.getMaxplayers();
        int hunterAmount=map.getHunteramount();
        List<Block> signalingDevices=map.getSignalingDevices();
        Location center=map.getCenter();
        Location cage=map.getCage();
        String path=mapName+".";
        maps.set(path+"world",gameWorld.getName());
        maps.set(path+"name",mapName);
        maps.set(path+"displayName",displayName);
        maps.set(path+"type",mode.name());
        maps.set(path+"maxPlayers",maxPlayers);
        maps.set(path+"minPlayers",minPlayers);
        maps.set(path+"hunters",hunterAmount);
        maps.set(path+"center.x",center.getX());
        maps.set(path+"center.y",center.getY());
        maps.set(path+"center.z",center.getZ());
        maps.set(path+"cage.x",cage.getX());
        maps.set(path+"cage.y",cage.getY());
        maps.set(path+"cage.z",cage.getZ());
        int i=1;
        for (Block block:signalingDevices){
            maps.set(path+"signalDevices."+String.valueOf(i)+".x",block.getX());
            maps.set(path+"signalDevices."+String.valueOf(i)+".y",block.getY());
            maps.set(path+"signalDevices."+String.valueOf(i)+".z",block.getZ());
            i++;
        }
        i = 1; // 重置计数器
        List<Material> disguiseableBlocks = map.getDisguiseableBlocks();
        List<String> materialNames = new ArrayList<>();
        if (disguiseableBlocks != null) {
            for (Material material : disguiseableBlocks) {
                materialNames.add(material.name());
            }
        }
        maps.set(path + "disguiseableBlocks", materialNames);
        try {
            config.save(configFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            maps.save(mapsFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        loadMaps();
        loadGames();
        return Boolean.TRUE;
    }
    public Boolean mapExists(WTMap map){
        return LoadedMaps.contains(map);
    }
    public Boolean mapExists(String internalName){
        for(WTMap map:LoadedMaps){
            if(map.getMapName().equalsIgnoreCase(internalName))return true;
        }
        return false;
    }
    public WTMap getMap(String internalName) {
        for (WTMap map : LoadedMaps) {
            if (map.getMapName().equalsIgnoreCase(internalName)) {
                return map;
            }
        }
        return null;
    }
    public void setInvis(Player player,boolean invis){
        if(!ProtocolLibExists || this.invis == null) {
            player.setInvisible(invis);
            return;
        }
        player.setInvisible(invis);
        Bukkit.getServer().getOnlinePlayers();
        if (Bukkit.getOnlinePlayers().size() > 1){
        if (invis) {
            this.invis.makePlayerInvisibleToAll(player);
        } else {
            this.invis.makePlayerVisibleToAll(player);
        }
        }
    }
}
class Statics{
    private final String url;
   public Statics(String path){
        this.url="jdbc:sqlite:"+path;
    }
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url);
    }

}
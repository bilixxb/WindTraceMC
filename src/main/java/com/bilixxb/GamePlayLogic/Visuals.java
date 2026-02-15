package com.bilixxb.GamePlayLogic;

import com.bilixxb.WindTraceMC;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.*;

public class Visuals {
    private final WindTraceMC plugin;
    private Scoreboard scoreboard;
    private Objective objective;
    private final Map<UUID, Integer> displayedHunters = new HashMap<>();
    private final Map<UUID, Set<Integer>> displayedRebels = new HashMap<>();

    public Visuals(WindTraceMC plugin){
        this.plugin = plugin;
    }

    /**
     * 为玩家显示计分板
     */
    public void showScoreboardFor(Player player, WindTraceGame game) {
        if (player == null || game == null) return;
        // 获取或创建计分板
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;

        Scoreboard playerScoreboard = manager.getNewScoreboard();

        // 获取当前游戏状态对应的计分板配置键
        String configKey = getScoreboardConfigKey(game.getStatus());

        // 创建Objective - 使用固定的名称以避免冲突
        String objectiveName = "windtrace_" + game.getPlayingOnMap().getMapName() + "_" + player.getUniqueId();
        objective = playerScoreboard.registerNewObjective(
                objectiveName,
                "dummy",
                getScoreboardTitle(configKey, game)
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        // 根据游戏状态设置计分板内容
        updateScoreboardContent(player, game, playerScoreboard, objective);

        // 设置计分板给玩家
        player.setScoreboard(playerScoreboard);
        this.scoreboard = playerScoreboard;

        // 重置显示的玩家缓存
        displayedHunters.remove(player.getUniqueId());
        displayedRebels.remove(player.getUniqueId());
    }
    /**
     * 更新玩家的计分板
     */
    public void updateScoreboardFor(Player player, WindTraceGame game) {
        if (player == null || game == null ) return;
        if (player.getScoreboard() == null) {
            showScoreboardFor(player, game);
            return;
        }
        Scoreboard playerScoreboard = player.getScoreboard();
        Objective objective = playerScoreboard.getObjective(DisplaySlot.SIDEBAR);

        if (objective == null) {
            showScoreboardFor(player, game);
            return;
        }

        // 更新标题
        String configKey = getScoreboardConfigKey(game.getStatus());
        String title = getScoreboardTitle(configKey, game);
        objective.setDisplayName(title);

        // 更新内容
        updateScoreboardContent(player, game, playerScoreboard, objective);
    }

    /**
     * 获取计分板配置键
     */
    private String getScoreboardConfigKey(GameStatus status) {
        switch (status) {
            case NotStarted:return "preparation";
            case Preparing:
                return "preparing";
            case inGaming:
                return "inGaming";
            case Ending:
                return "inGaming";
            default:
                return "preparation";
        }
    }

    /**
     * 获取计分板标题（第一行）
     */
    private String getScoreboardTitle(String configKey, WindTraceGame game) {
        // 从语言文件中获取计分板配置
        List<String> scoreboardConfig = plugin.getLocalizedList("scoreBoard." + configKey);
        if (scoreboardConfig == null || scoreboardConfig.isEmpty()) {
            return "§e§l风行迷踪";
        }
        return scoreboardConfig.get(0); // 第一行为标题
    }

    /**
     * 更新计分板内容
     */
    private void updateScoreboardContent(Player player, WindTraceGame game,
                                         Scoreboard scoreboard, Objective objective) {
        // 清除旧的计分项
        for (String entry : scoreboard.getEntries()) {
            scoreboard.resetScores(entry);
        }

        // 获取当前游戏状态对应的配置
        String configKey = getScoreboardConfigKey(game.getStatus());
        List<String> template = plugin.getLocalizedList("scoreBoard." + configKey);

        if (template == null || template.isEmpty()) return;

        // 准备占位符替换数据
        Map<String, String> placeholders = preparePlaceholders(player, game);

        // 从第二行开始设置（第一行是标题）
        int lineNumber = template.size() - 1; // 分数从高到低显示

        for (int i = 1; i < template.size(); i++) {
            String line = template.get(i);
            line = replacePlaceholders(line, placeholders, player, game);

            // 使用Team避免闪烁
            Team team = scoreboard.getTeam("line_" + i);
            if (team == null) {
                team = scoreboard.registerNewTeam("line_" + i);
            }

            String entry = getUniqueEntry(i);
            team.addEntry(entry);

            // 如果行太长，分割为前缀和后缀
            if (line.length() > 17) {
                String prefix = line.substring(0, 17);
                String suffix = ChatColor.getLastColors(prefix) + line.substring(17);
                if (suffix.length() > 17) {
                    suffix = suffix.substring(0, 17);
                }
                team.setPrefix(prefix);
                team.setSuffix(suffix);
            } else {
                team.setPrefix(line);
                team.setSuffix("");
            }

            // 设置分数
            objective.getScore(entry).setScore(lineNumber);
            lineNumber--;
        }
    }

    /**
     * 准备占位符数据
     */
    private Map<String, String> preparePlaceholders(Player player, WindTraceGame game) {
        Map<String, String> placeholders = new HashMap<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");

        // 基础占位符
        placeholders.put("{date}", sdf.format(new Date()));
        placeholders.put("{time}", String.valueOf(game.getTimer()));
        placeholders.put("{MODE}", plugin.getLocalizedText("GUI.mode."+game.getPlayingOnMap().getMode().toString()));
        placeholders.put("{identity}",plugin.getLocalizedText("identities."+game.getIdentity(player)));
        placeholders.put("{displayName}", game.isEliminated(player)?"§7§m":"" +game.getPlayingOnMap().getDisplayName());

        // 添加统计信息占位符（仅游戏进行中或准备阶段显示）
        if (game.getStatus() == GameStatus.inGaming || game.getStatus() == GameStatus.Preparing) {
            String playerIdentity = game.getIdentity(player);
            if (playerIdentity.equals("hunter")) {
                // 获取猎人的捕获人数
                int captures = game.getHunters().getOrDefault(player, 0);
                placeholders.put("{captures}", String.valueOf(captures));
            } else if (playerIdentity.equals("rebel")) {
                // 获取反抗者的发信机修复数量
                int repairs = game.getRebel().getOrDefault(player, 0);
                placeholders.put("{repairs}", String.valueOf(repairs));
            }
        }

        if (game.getStatus() == GameStatus.inGaming||game.getStatus()==GameStatus.Preparing) {
            // 确保这些方法不返回 null
            String nextEvent = getNextEvent(game);
            String randomHunter = getRandomHunter(player, game);

            placeholders.put("{event}", nextEvent != null ? nextEvent : "");
            // ========== 新增 {eventTime} 占位符 ==========
            placeholders.put("{eventTime}", getNextEventTime(game));

            placeholders.put("{hunter}", randomHunter != null ? randomHunter : "");

            List<String> randomRebels = getRandomRebels(player, game, 3);
            for (int i = 0; i < randomRebels.size(); i++) {
                String rebel = randomRebels.get(i);
                placeholders.put("{rebel" + (i + 1) + "}", rebel != null ? rebel : "");
            }
        }

        return placeholders;
    }

    /**
     * 替换占位符
     */
    private String replacePlaceholders(String line, Map<String, String> placeholders,
                                       Player player, WindTraceGame game) {
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String value = entry.getValue();
            // 避免 null 值
            if (value != null) {
                line = line.replace(entry.getKey(), value);
            }
        }

        // 特殊处理多个游侠显示
        if (game.getStatus() == GameStatus.inGaming) {
            line = replaceMultipleRebels(line, player, game);
        }

        return line;
    }

    private String replaceMultipleRebels(String line, Player player, WindTraceGame game) {
        List<String> randomRebels = getRandomRebels(player, game, 3);

        // 替换 {rebel}（单数形式）为第一个游侠
        if (line.contains("{rebel}")) {
            String replacement;
            if (randomRebels.isEmpty()) {
                String notAvailable = plugin.getLocalizedText("scoreboard.notAvailable");
                replacement = notAvailable != null ? notAvailable : "N/A";
            } else {
                replacement = randomRebels.get(0);
                if (replacement == null) {
                    replacement = "N/A";
                }
            }
            line = line.replace("{rebel}", replacement);
        }

        // 替换 {rebel1}, {rebel2}, {rebel3}
        for (int i = 0; i < randomRebels.size(); i++) {
            String replacement = randomRebels.get(i);
            if (replacement == null) {
                replacement = "N/A";
            }
            line = line.replace("{rebel" + (i + 1) + "}", replacement);
        }

        return line;
    }
    /**
     * 获取随机且不重复的猎手
     */
    private String getRandomHunter(Player player, WindTraceGame game) {
        // 修改：使用getHuntersList()获取猎人玩家列表
        List<Player> hunters = game.getHuntersList();
        if (hunters == null || hunters.isEmpty()) {
            String notAvailable = plugin.getLocalizedText("scoreboard.notAvailable");
            return notAvailable != null ? notAvailable : "N/A";
        }

        UUID playerId = player.getUniqueId();
        Integer lastDisplayedIndex = displayedHunters.get(playerId);

        // 随机选择一个猎手，确保与上次不同
        int randomIndex;
        do {
            randomIndex = new Random().nextInt(hunters.size());
        } while (hunters.size() > 1 && lastDisplayedIndex != null &&
                randomIndex == lastDisplayedIndex);

        displayedHunters.put(playerId, randomIndex);
        Player hunter = hunters.get(randomIndex);

        // 确保返回的字符串不为 null
        if (hunter != null && hunter.getName() != null) {
            return hunter.getName();
        } else {
            String notAvailable = plugin.getLocalizedText("scoreboard.notAvailable");
            return notAvailable != null ? notAvailable : "N/A";
        }
    }
    /**
     * 获取随机且不重复的游侠列表
     */
    private List<String> getRandomRebels(Player player, WindTraceGame game, int count) {
        // 获取所有游侠（包括淘汰的）
        List<Player> allRebels = new ArrayList<>();
        if (game.getRebelList() != null) {
            allRebels.addAll(game.getRebelList());
        }
        if (game.getEliminated() != null) {
            allRebels.addAll(game.getEliminated());
        }

        // 按玩家名排序，保证顺序固定
        allRebels.sort(Comparator.comparing(Player::getName));

        List<String> result = new ArrayList<>();
        String notAvailable = plugin.getLocalizedText("scoreboard.notAvailable");
        String defaultValue = notAvailable != null ? notAvailable : "N/A";

        for (int i = 0; i < count; i++) {
            if (i < allRebels.size()) {
                Player rebel = allRebels.get(i);
                String name = rebel.getName();
                // 若已淘汰，添加灰色删除线
                if (game.getEliminated() != null && game.getEliminated().contains(rebel)) {
                    name = "§7§m" + name + "§r";
                }
                result.add(name);
            } else {
                result.add(defaultValue);
            }
        }
        return result;
    }
    /**
     * 获取下一个事件
     */
    private String getNextEvent(WindTraceGame game) {
        if(game.getStatus()==GameStatus.Preparing)return plugin.getLocalizedText("events.hunterBeingReleased");
        else {
            if(game.totalTime<=110)return plugin.getLocalizedText("events.QSkillsDropped");
            if(plugin.config.getInt("gameTime")-game.totalTime>=60)return plugin.getLocalizedText("events.HunterStrengthen");
            else return plugin.getLocalizedText("events.GameEnd");
        }
    }

    /**
     * 获取下一个事件的剩余时间（秒）
     */
    private String getNextEventTime(WindTraceGame game) {
        if (game == null) return "";
        GameStatus status = game.getStatus();
        if (status == GameStatus.Preparing) {
            // 准备阶段：距离猎手释放的秒数
            return String.valueOf(game.getTimer());
        } else if (status == GameStatus.inGaming) {
            int gameTotalTime = plugin.config.getInt("gameTime"); // 游戏总时长（秒）
            int currentTime = game.totalTime; // 已过去时间（秒）
            int remainingTime = gameTotalTime - currentTime;

            if (currentTime < 110) {
                // 下一个事件：Q技能掉落（110秒时）
                return String.valueOf(110 - currentTime);
            } else if (remainingTime >= 60) {
                // 下一个事件：猎手强化（剩余60秒时）
                return String.valueOf(remainingTime - 60);
            } else {
                // 下一个事件：游戏结束
                return String.valueOf(remainingTime);
            }
        } else {
            return "";
        }
    }

    /**
     * 获取唯一的计分板Entry
     */
    private String getUniqueEntry(int line) {
        // 使用固定的可见颜色代码确保每行都有唯一且可见的Entry
        // 只使用不会改变文本样式的颜色代码
        ChatColor[] colors = {
                ChatColor.WHITE, ChatColor.GRAY, ChatColor.DARK_GRAY,
                ChatColor.BLACK, ChatColor.RED, ChatColor.DARK_RED,
                ChatColor.YELLOW, ChatColor.GOLD, ChatColor.GREEN,
                ChatColor.DARK_GREEN, ChatColor.AQUA, ChatColor.DARK_AQUA,
                ChatColor.BLUE, ChatColor.DARK_BLUE, ChatColor.LIGHT_PURPLE,
                ChatColor.DARK_PURPLE
        };

        int colorIndex = line % colors.length;
        return colors[colorIndex].toString();
    }

    /**
     * 为游戏所有玩家显示计分板
     */
    public void showScoreboardForAll(WindTraceGame game) {
        if (game == null || game.getPlaying() == null) return;
        for (Player player : game.getPlaying()) {
            showScoreboardFor(player, game);
        }
    }

    /**
     * 更新游戏所有玩家的计分板
     */
    public void updateScoreboardForAll(WindTraceGame game) {
        if (game == null || game.getPlaying() == null) return;

        for (Player player : game.getPlaying()) {
            // 如果玩家没有计分板，先创建
            if (player.getScoreboard() == null || player.getScoreboard().getObjective(DisplaySlot.SIDEBAR) == null) {
                showScoreboardFor(player, game);
            } else {
                updateScoreboardFor(player, game);
            }
        }
    }

    /**
     * 移除玩家的计分板
     */
    public void removeScoreboard(Player player) {
        if (player == null) return;

        Scoreboard emptyScoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        player.setScoreboard(emptyScoreboard);

        // 清理缓存
        displayedHunters.remove(player.getUniqueId());
        displayedRebels.remove(player.getUniqueId());
    }
}
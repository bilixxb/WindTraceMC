package com.bilixxb;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TabExecutor implements org.bukkit.command.TabExecutor, TabCompleter {
    private final WindTraceMC plugin;

    public TabExecutor(WindTraceMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // 这里应该委托给WTCommands处理，但由于TabExecutor类存在，我们保留这个方法
        // 实际执行逻辑已经在WTCommands中
        return false;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // 第一个参数：命令子命令
            List<String> subCommands = new ArrayList<>();

            // 基础命令（所有玩家可用）
            subCommands.add("help");
            subCommands.add("join");
            subCommands.add("rejoin");
            subCommands.add("r");
            subCommands.add("stats");

            // 管理员命令
            if (sender.hasPermission("windTrace.admin.create")) subCommands.add("create");
            if (sender.hasPermission("windTrace.admin.attributes")) subCommands.add("attributes");
            if (sender.hasPermission("windTrace.admin.save")) subCommands.add("save");
            if (sender.hasPermission("windTrace.admin.setCage")) subCommands.add("setCage");
            if (sender.hasPermission("windTrace.admin.setCenter")) subCommands.add("setCenter");
            if (sender.hasPermission("windTrace.admin.setDevice")) subCommands.add("setDevice");
            if (sender.hasPermission("windTrace.admin.removeDevice")) subCommands.add("removeDevice");
            if (sender.hasPermission("windTrace.admin.forcestart")) subCommands.add("forceStart");
            if (sender.hasPermission("windTrace.admin.setLobby")) subCommands.add("setLobby");

            // 根据输入过滤
            completions = filterCompletions(args[0], subCommands);
        }
        else if (args.length == 2) {
            // 第二个参数：根据第一个命令提供不同的补全
            switch (args[0].toLowerCase()) {
                case "help":
                    completions = filterCompletions(args[1], Arrays.asList("attributes"));
                    break;

                case "create":
                    // 建议世界名称
                    List<String> worldNames = Bukkit.getWorlds().stream()
                            .map(world -> world.getName())
                            .collect(Collectors.toList());
                    completions = filterCompletions(args[1], worldNames);
                    break;

                case "join":
                    // 建议可用地图的名称
                    List<String> mapNames = plugin.LoadedMaps.stream()
                            .map(map -> map.getMapName())
                            .collect(Collectors.toList());
                    completions = filterCompletions(args[1], mapNames);
                    break;

                case "attributes":
                    List<String> attributes = new ArrayList<>();
                    if (sender.hasPermission("windTrace.admin.attributes.mode")) attributes.add("mode");
                    if (sender.hasPermission("windTrace.admin.attributes.minPlayers")) attributes.add("minPlayers");
                    if (sender.hasPermission("windTrace.admin.attributes.maxPlayers")) attributes.add("maxPlayers");
                    if (sender.hasPermission("windTrace.admin.attributes.hunterAmount")) attributes.add("hunterAmount");
                    if (sender.hasPermission("windTrace.admin.attributes.addDisguiseBlock")) attributes.add("addDisguiseBlock");
                    if (sender.hasPermission("windTrace.admin.attributes.removeDisguiseBlock")) attributes.add("removeDisguiseBlock");
                    completions = filterCompletions(args[1], attributes);
                    break;

                case "stats":
                    // 建议在线玩家名称
                    List<String> playerNames = Bukkit.getOnlinePlayers().stream()
                            .map(player -> player.getName())
                            .collect(Collectors.toList());
                    completions = filterCompletions(args[1], playerNames);
                    break;

                default:
                    // 其他命令没有第二个参数需要补全
                    break;
            }
        }
        else if (args.length == 3) {
            // 第三个参数：主要针对attributes命令
            if (args[0].equalsIgnoreCase("attributes")) {
                switch (args[1].toLowerCase()) {
                    case "mode":
                        completions = filterCompletions(args[2], Arrays.asList("NORMAL", "WINTER"));
                        break;

                    case "minplayers":
                    case "maxplayers":
                    case "hunteramount":
                        // 数字参数，不提供具体补全，可以提供一些常用值
                        if (args[2].isEmpty()) {
                            completions = Arrays.asList("2", "4", "6", "8", "10");
                        }
                        break;

                    default:
                        break;
                }
            }
        }

        return completions;
    }

    /**
     * 过滤补全列表，只返回以输入开头的项
     */
    private List<String> filterCompletions(String input, List<String> options) {
        if (input.isEmpty()) {
            return options;
        }

        return options.stream()
                .filter(option -> option.toLowerCase().startsWith(input.toLowerCase()))
                .collect(Collectors.toList());
    }
}
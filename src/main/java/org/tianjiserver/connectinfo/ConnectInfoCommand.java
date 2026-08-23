package org.tianjiserver.connectinfo;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class ConnectInfoCommand implements SimpleCommand {

  private final ConnectInfoPlugin plugin;

  public ConnectInfoCommand(ConnectInfoPlugin plugin) {

    this.plugin = plugin;
  }

  private static String pingColor(long ping) {

    if(ping < 100) return "<green>";
    if(ping < 200) return "<yellow>";
    if(ping < 300) return "<gold>";
    return "<red>";
  }

  private static String formatDuration(Duration duration) {

    long seconds = Math.max(0, duration.getSeconds());
    long hours = seconds / 3600;
    long minutes = (seconds % 3600) / 60;
    long secs = seconds % 60;
    if(hours > 0) return hours + "小时" + minutes + "分钟";
    if(minutes > 0) return minutes + "分钟" + secs + "秒";
    return secs + "秒";
  }

  @Override
  public void execute(Invocation invocation) {

    CommandSource source = invocation.source();
    String[] args = invocation.arguments();

    if(args.length == 0) {
      if(!(source instanceof Player player)) {
        source.sendMessage(ConnectInfoPlugin.parse(plugin.config().message("usage-console", "<red>控制台请使用：</red><yellow>/connectinfo <玩家></yellow>")));
        return;
      }
      if(!source.hasPermission("connectinfo.use")) {
        noPermission(source);
        return;
      }
      showPlayer(source, player);
      return;
    }

    switch(args[0].toLowerCase()) {
      case "nodes" -> showNodes(source);
      case "reload" -> reload(source);
      default -> showOtherPlayer(source, args[0]);
    }
  }

  private void showOtherPlayer(CommandSource source, String name) {

    if(!source.hasPermission("connectinfo.other")) {
      noPermission(source);
      return;
    }
    Optional<Player> target = plugin.server().getPlayer(name);
    if(target.isEmpty()) {
      source.sendMessage(ConnectInfoPlugin.parse(plugin.config().message("player-not-found", "<red>玩家 <yellow><player></yellow> 不在线。</red>").replace("<player>", ConnectInfoPlugin.escape(name))));
      return;
    }
    showPlayer(source, target.get());
  }

  private void showPlayer(CommandSource source, Player player) {

    ConnectInfoPlugin.PlayerConnectionInfo info = plugin.info(player);
    String node = plugin.config().getNodeDescription(info.host());
    long ping = player.getPing();

    StringBuilder message = new StringBuilder()
            .append("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>\n")
            .append("<gold><bold>玩家连接信息</bold></gold>\n")
            .append("<gray>玩家 <dark_gray>» <white>").append(ConnectInfoPlugin.escape(player.getUsername())).append("\n")
            .append("<gray>连接域名 <dark_gray>» <aqua>").append(ConnectInfoPlugin.escape(info.host())).append("\n");
    if(info.port() >= 0) {
      message.append("<gray>连接端口 <dark_gray>» <white>").append(info.port()).append("\n");
    }
    message.append("<gray>节点介绍 <dark_gray>» <yellow>").append(ConnectInfoPlugin.escape(node)).append("\n")
            .append("<gray>延迟 <dark_gray>» ").append(pingColor(ping)).append(ping).append("ms\n")
            .append("<gray>在线时长 <dark_gray>» <green>").append(formatDuration(Duration.between(info.loginTime(), Instant.now()))).append("\n");
    player.getCurrentServer().ifPresent(current->message.append("<gray>当前服务器 <dark_gray>» <green>").append(ConnectInfoPlugin.escape(current.getServerInfo().getName())).append("\n"));
    message.append("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>");
    source.sendMessage(ConnectInfoPlugin.parse(message.toString()));
  }

  private void showNodes(CommandSource source) {

    if(!source.hasPermission("connectinfo.nodes")) {
      noPermission(source);
      return;
    }

    Map<String, Long> counts = plugin.server().getAllPlayers().stream()
            .map(player->{
              String host = plugin.info(player).host();

              if(!plugin.config().isKnownNode(host)) {
                if(!plugin.config().isIncludeUnknown()) {
                  return null;
                }

                return plugin.config().getDefaultNode() + "(" + host + ")";
              }

              return plugin.config().getNodeDescription(host);
            })
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(
                    node->node,
                    TreeMap::new,
                    Collectors.counting()
                                          ));

    StringBuilder message = new StringBuilder(
            "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>\n"
            + "<gold><bold>节点在线人数实时统计</bold></gold>\n"
    );

    if(counts.isEmpty()) {
      message.append("<gray>当前没有在线玩家。\n");
    } else {
      counts.entrySet().stream()
              .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
              .forEach(entry->
                               message.append("<yellow>")
                                       .append(ConnectInfoPlugin.escape(entry.getKey()))
                                       .append(" <dark_gray>» <green>")
                                       .append(entry.getValue())
                                       .append(" 人\n")
                      );
    }

    message.append("<gray>总在线 <dark_gray>» <white>")
            .append(plugin.server().getPlayerCount())
            .append("\n")
            .append("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>");

    source.sendMessage(ConnectInfoPlugin.parse(message.toString()));
  }

  private void reload(CommandSource source) {

    if(!source.hasPermission("connectinfo.reload")) {
      noPermission(source);
      return;
    }
    try {
      plugin.reloadConfig();
      source.sendMessage(ConnectInfoPlugin.parse(plugin.config().message("reload-success", "<green>配置文件已重新加载。</green>")));
    } catch(Exception exception) {
      source.sendMessage(ConnectInfoPlugin.parse("<red>配置重载失败：</red><gray>" + ConnectInfoPlugin.escape(exception.getMessage())));
    }
  }

  private void noPermission(CommandSource source) {

    source.sendMessage(ConnectInfoPlugin.parse(plugin.config().message("no-permission", "<red>你没有权限使用这个指令。</red>")));
  }

  @Override
  public List<String> suggest(Invocation invocation) {

    String[] args = invocation.arguments();
    if(args.length > 1) return List.of();
    String input = args.length == 0? "" : args[0].toLowerCase();
    return java.util.stream.Stream.concat(
                    java.util.stream.Stream.of("nodes", "reload"),
                    plugin.server().getAllPlayers().stream().map(Player::getUsername))
            .filter(value->value.toLowerCase().startsWith(input))
            .distinct().sorted(Comparator.comparing(String::toLowerCase)).toList();
  }
}

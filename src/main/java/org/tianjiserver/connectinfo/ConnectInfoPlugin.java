package org.tianjiserver.connectinfo;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ConnectInfoPlugin {

  private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

  private final ProxyServer server;
  private final Logger logger;
  private final Path dataDirectory;
  private final Map<UUID, PlayerConnectionInfo> playerInfo = new ConcurrentHashMap<>();
  private NodeConfig config;

  @Inject
  public ConnectInfoPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {

    this.server = server;
    this.logger = logger;
    this.dataDirectory = dataDirectory;
  }

  public static Component parse(String input) { return MINI_MESSAGE.deserialize(input); }

  public static String escape(String input) {

    return input.replace("\\", "\\\\").replace("<", "\\<").replace(">", "\\>");
  }

  @Subscribe
  public void onProxyInitialization(ProxyInitializeEvent event) {

    try {
      loadConfig();
    } catch(IOException exception) {
      logger.error("Failed to load ConnectInfo configuration", exception);
      return;
    }

    CommandManager commandManager = server.getCommandManager();
    CommandMeta meta = commandManager.metaBuilder("connectinfo")
            .aliases("serverinfo", "cinfo")
            .plugin(this)
            .build();
    commandManager.register(meta, new ConnectInfoCommand(this));

    logger.info("VelocityConnectInfo enabled.");
  }

  @Subscribe
  public void onLogin(LoginEvent event) {

    Player player = event.getPlayer();
    String host = player.getVirtualHost()
            .map(InetSocketAddress::getHostString)
            .map(NodeConfig::normalize)
            .orElse("unknown");
    int port = player.getVirtualHost().map(InetSocketAddress::getPort).orElse(-1);

    NodeConfig.DomainRule rule = config.getDomainRule(host);
    if(rule != null && rule.action() == NodeConfig.DomainAction.KICK) {
      String message = rule.message().isBlank()
                       ? config.message("restricted-domain", "<red>你使用的服务器地址目前不可用。</red>")
                       : rule.message();
      player.disconnect(parse(message));
      logger.info("Blocked player {} using restricted domain {}", player.getUsername(), host);
      return;
    }

    playerInfo.put(player.getUniqueId(), new PlayerConnectionInfo(host, port, Instant.now()));

    if(rule != null && rule.action() == NodeConfig.DomainAction.WARN) {
      String text = "<yellow><bold>线路提醒</bold></yellow>\n"
                    + "<gray>玩家 <dark_gray>» <white>" + escape(player.getUsername()) + "\n"
                    + "<gray>连接域名 <dark_gray>» <aqua>" + escape(host) + "\n"
                    + "<gray>线路 <dark_gray>» <yellow>" + escape(rule.description());
      Component warning = parse(text);
      server.getAllPlayers().stream()
              .filter(target->target.hasPermission(config.getAlertPermission()))
              .forEach(target->target.sendMessage(warning));
      logger.warn("Player {} connected through warned domain {}", player.getUsername(), host);
    }
  }

  @Subscribe
  public void onDisconnect(DisconnectEvent event) {

    playerInfo.remove(event.getPlayer().getUniqueId());
  }

  public synchronized void reloadConfig() throws IOException {

    loadConfig();
  }

  private void loadConfig() throws IOException {

    Files.createDirectories(dataDirectory);
    Path configPath = dataDirectory.resolve("config.yml");
    if(Files.notExists(configPath)) {
      try(var input = getClass().getClassLoader().getResourceAsStream("config.yml")) {
        if(input == null) throw new IOException("Bundled config.yml not found");
        Files.copy(input, configPath);
      }
    }
    NodeConfig loaded = new NodeConfig(configPath);
    loaded.load();
    this.config = loaded;
  }

  public ProxyServer server() { return server; }

  public NodeConfig config() { return config; }

  public PlayerConnectionInfo info(Player player) {

    return playerInfo.computeIfAbsent(player.getUniqueId(), ignored->{
      String host = player.getVirtualHost().map(InetSocketAddress::getHostString).map(NodeConfig::normalize).orElse("unknown");
      int port = player.getVirtualHost().map(InetSocketAddress::getPort).orElse(-1);
      return new PlayerConnectionInfo(host, port, Instant.now());
    });
  }

  public record PlayerConnectionInfo(String host, int port, Instant loginTime) { }
}

package org.tianjiserver.connectinfo;

import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class NodeConfig {

  private final Path path;
  private final Map<String, String> nodes = new LinkedHashMap<>();
  private final Map<String, DomainRule> domainRules = new LinkedHashMap<>();
  private final Map<String, String> messages = new LinkedHashMap<>();
  private String defaultNode = "默认";
  private String alertPermission = "connectinfo.alert.domain";
  private boolean includeUnknown = true;

  public NodeConfig(Path path) {

    this.path = path;
  }

  public static String normalize(String host) {

    if(host == null) return "";
    String value = host.trim().toLowerCase(Locale.ROOT);
    while(value.endsWith(".")) value = value.substring(0, value.length() - 1);
    return value;
  }

  public void load() throws IOException {

    nodes.clear();
    domainRules.clear();
    messages.clear();

    if(Files.notExists(path)) {
      Files.createDirectories(path.getParent());
    }

    YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
            .path(path)
            .build();
    ConfigurationNode root = loader.load();

    defaultNode = root.node("default-node").getString("默认");
    alertPermission = root.node("alerts", "permission")
            .getString("connectinfo.alert.domain");
    includeUnknown = root.node("statistics", "include-unknown").getBoolean(true);

    ConfigurationNode messagesNode = root.node("messages");
    for(Map.Entry<Object, ? extends ConfigurationNode> entry : messagesNode.childrenMap().entrySet()) {
      String value = entry.getValue().getString();
      if(value != null) {
        messages.put(String.valueOf(entry.getKey()), value);
      }
    }

    ConfigurationNode nodesNode = root.node("nodes");
    for(Map.Entry<Object, ? extends ConfigurationNode> entry : nodesNode.childrenMap().entrySet()) {
      String value = entry.getValue().getString();
      if(value != null) {
        nodes.put(normalize(String.valueOf(entry.getKey())), value);
      }
    }

    ConfigurationNode rulesNode = root.node("domain-rules");
    for(Map.Entry<Object, ? extends ConfigurationNode> entry : rulesNode.childrenMap().entrySet()) {
      String host = normalize(String.valueOf(entry.getKey()));
      ConfigurationNode ruleNode = entry.getValue();
      String actionName = ruleNode.node("action").getString("ALLOW");
      DomainAction action;
      try {
        action = DomainAction.valueOf(actionName.toUpperCase(Locale.ROOT));
      } catch(IllegalArgumentException ignored) {
        action = DomainAction.ALLOW;
      }
      domainRules.put(host, new DomainRule(
              action,
              ruleNode.node("description").getString(""),
              ruleNode.node("message").getString("")
      ));
    }
  }

  public String getDefaultNode() { return defaultNode; }

  public String getNodeDescription(String host) { return nodes.getOrDefault(normalize(host), defaultNode); }

  public boolean isKnownNode(String host) { return nodes.containsKey(normalize(host)); }

  public DomainRule getDomainRule(String host) { return domainRules.get(normalize(host)); }

  public String message(String key, String fallback) { return messages.getOrDefault(key, fallback); }

  public String getAlertPermission() { return alertPermission; }

  public boolean isIncludeUnknown() { return includeUnknown; }

  public enum DomainAction {
    ALLOW, WARN, KICK
  }

  public record DomainRule(DomainAction action, String description, String message) { }
}

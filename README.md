# VelocityConnectInfo 1.2.0

功能：

- 获取玩家连接 Velocity 时实际使用的 Virtual Host / 域名
- 根据域名显示节点介绍
- 查询玩家 Ping
- 查询当前后端服务器
- 玩家在线时长统计
- 节点在线人数实时统计
- 特定域名 WARN / KICK 限制
- 配置热重载

## Commands

- `/connectinfo` - 查看自己
- `/connectinfo <player>` - 查看其他在线玩家
- `/connectinfo nodes` - 查看各节点实时在线人数
- `/connectinfo reload` - 重载配置

Aliases: `/serverinfo`, `/cinfo`

## Permissions

- `connectinfo.use`
- `connectinfo.other`
- `connectinfo.nodes`
- `connectinfo.reload`
- `connectinfo.alert.domain`

## Build

```bash
mvn clean package
```

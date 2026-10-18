---
navigation:
  title: "命令与界面"
  icon: "minecraft:compass"
---

# 命令与界面

## `/wires`

| 子命令 | 权限 | 作用 |
| --- | --- | --- |
| `guide` | 玩家 | 打开这份手册 |
| `list` | 任意 | 列出当前所有连接及其状态 |
| `link <坐标A> <坐标B>` | OP | 直接建立 A → B |
| `unlink <坐标A> <坐标B>` | OP | 断开这两根杆之间的连接 |
| `clear` | OP | 提示这是破坏性操作 |
| `clear confirm` | OP | 真的清空全部连接 |

坐标可以指向杆的**任意一段**，命令会自己找到顶段。
`link` / `unlink` 只用于排查问题——正常玩法请用绑定连接。

## `/wires display`

客户端命令，开关屏幕上的常驻连接列表：

```text
/wires display          # 切换
/wires display true     # 常驻
/wires display false    # 关闭
```

列表实时更新，显示每条连接的两端坐标与状态颜色。
开启后还会沿连接生成粒子束（仅同维度的连接）。

## 状态颜色

| 状态 | 含义 |
| --- | --- |
| **活跃** | 两端都在线，正常输送 |
| **未接入** | 有一端离线或不在电网内，恢复后自动接通 |
| **已挂起** | 被亏电断路器挂起，等待重连 |
| **待批准** | 从端已申请重连，等待主端裁决 |

线色与状态图标都可以在客户端配置 `Graphics & Rendering` 里改。

## 配置在哪

| 文件 | 内容 |
| --- | --- |
| `anvilcraft_pullwires-common.toml` | 三档范围、跨维度、结算、连接开关 |
| `anvilcraft_pullwires-client.toml` | 线色、放置提示、连接列表位置与缩放、粒子 |

配置界面里每一项都有中文说明，鼠标悬停即可看到。

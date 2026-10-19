<div align="center" style="align-items: center">

# 铁砧工艺：拉电线（AnvilCraft Pull Wires）
本模组由作者借助DSH、VSCode与BlockBench开发。若有Bug或者建议，可通过[issue](https://github.com/JunYier/AnvilCraft-PullWires/issues)向作者反馈。

![CI](https://github.com/JunYier/AnvilCraft-PullWires/actions/workflows/ci.yml/badge.svg)
![Release](https://img.shields.io/github/v/release/JunYier/AnvilCraft-PullWires)


![Java21](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/built-with/java21_vector.svg)
![NeoForge](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/neoforge_vector.svg)

![AnvilLib](https://cdn.jsdelivr.net/npm/@gugle/devins-badges-plus/assets/cozy/mc/anvillib_vector.svg)

![Unsupported Fabric](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/unsupported/fabric_vector.svg)
![Unsupported Forge](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/unsupported/forge_vector.svg)

本模组为[AnvilCraft](https://github.com/Anvil-Dev/AnvilCraft) 的附属模组，目的在于为本体的电力网络补上「拉电线」相关：
两种新的输电杆、可配置的三档距离、带损耗与断路保护的跨电网结算，以及围绕它们的整套界面与提示。
</div>

## 特性

**三挡距离**

在本体的覆盖范围之外，本模组额外添加了两段距离————自动组网距离，与手动连接距离。
- 自动组网距离：顾名思义，当任意两个输电杆之间的距离小于可配置值时，该输电杆将会绕过本体的AABB盒相交计算进行组网。
- 手动连接距离：当任意两个输电杆之间的距离小于可配置值时，可通过**手动连接**的方式建立主从对，单向输送电力、构建送电干路。

**两种新设备**

| 设备 | 供电范围 | 自动组网 | 手动连接 |
| --- | --- | --- | --- |
| 超远程输电杆 | 2 | 128 | 256 |
| 跨维度输电杆 | 0 | 0 | 无限制（**可跨维度**） |

跨维度杆不主动并网，只作为「手动连接的另一端」，让两个相隔一个维度的电网连起来。


**手动连接**
手持杆右键已放置的杆 → 得到「主端绑定」→ 之后放下任意杆即自动建链。副手拿磁盘时绑定数据存在磁盘里，可以批量放置。
绑定后走远会实时显示进度条与粒子束；超过手动范围即自动解绑。跨维度杆没有距离限制。

**结算**
链路是单向的「主端 → 从端」：从端的缺口由主端填补，同一从端可以有多主端，按优先级依次申请。
中转杆会把下游需求逐级透传上来，所以 A → B → C 的多跳能真正把电送到 C。
跨维度链路的损耗计在**主端侧**：要让从端收到 256，主端需要付出 256 / (1 − 损耗)。
损耗、结算与记账全部在服务端完成，客户端只显示只读镜像。

**保护**
- **亏电断路**：主端所在电网账面连续 15 秒为负时，其链路被挂起（`SUSPENDED`）。
- **自动重连**：挂起的从端每隔一段时间提出申请，主端按最近一段时间的平均富余裁决，够就恢复。
- 铁砧锤 `shift + 右键` 锁定某根杆，锁定后忽略红石对开关的影响。

**界面**
- `Ctrl + 右键` 电线杆：打开该杆的配置页。
- 方块提示框：上下游数量、本拍外送/内收、优先级、锁定状态。
- 物品提示框：**Shift** 展开三档范围，**Ctrl** 展开绑定细节。
- 可选集成：**Jade** 浮层、**JEI** 信息页、**藿香（Ageratum）** 手册（`/wires guide` 或背包里按 `W`）。

## 环境要求

| 项 | 版本 |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.241 或更高 |
| AnvilCraft | 1.6.0+ |
| Java | 21 |
| 可选 | Jade 15.10.5+ / JEI 19.50+ / 藿香 0.0.1+ |

## 安装

1. 安装对应版本的 Minecraft + NeoForge；
2. 本模组`.jar`文件可从[Release](https://github.com/JunYier/AnvilCraft-PullWires/releases)下载，或者在[CI](https://github.com/JunYier/AnvilCraft-PullWires/actions/workflows/ci.yml)中获取最新版本。
3. 把 AnvilCraft 与本模组的 jar 一起放进 `mods/`文件夹；
4. 需要的可选依赖（Jade / JEI / 藿香）装上即可自动生效，不装也不影响。

## 快速上手

1. 合成一根本体输电杆，按本体方式建好一个电网；
2. 合成**超远程输电杆**，放在远处，两根杆会自动并网；
3. 想连**另一个电网**时：手持一根杆**右键**主端杆完成绑定，再把杆放到目标位置，链路即建立；
4. 手持**铁砧锤**对着杆按住 `Ctrl + 右键`，可以调它的优先级与重连排序；
5. `/wires list` 查看所有链路及其本拍转移量；`/wires display` 打开屏幕常驻列表。

## 指令与按键

| 指令 | 作用 | 需要 OP |
| --- | --- | --- |
| `/wires guide` | 打开藿香手册 | 否 |
| `/wires list` | 列出所有链路与状态 | 否 |
| `/wires display` | 开关屏幕上的常驻链路列表 | 否 |
| `/wires link <主端> <从端>` | 手动建立链路 | 是 |
| `/wires unlink <从端>` | 解除链路的从端 | 是 |
| `/wires clear confirm` | 清空全部链路（破坏性操作，必须带 `confirm`） | 是 |

| 按键 / 操作 | 作用 |
| --- | --- |
| 手持杆右键已有杆 | 完成主端绑定 |
| 手持杆 `shift + 右键` | 解除绑定 |
| 铁砧锤 `右键` 杆 | 锁定 / 解锁该杆 |
| 铁砧锤 `Ctrl + 右键` 杆 | 打开该杆的配置页 |
| 背包里 `Shift` 悬停 | 查看三档范围 |
| 背包里 `Ctrl` 悬停 | 查看绑定细节 |

## 配置

配置分三组（`Transmission` / `Settlement` / `Linking`），常见项：

| 配置项 | 默认 | 说明 |
| --- | --- | --- |
| 本体杆 `autoR` / `manualR` | `3.0` / `4.0`| 相对本体供电范围的倍数 |
| 超远程杆范围 | `2 / 128 / 256` | 供电 / 自动 / 手动 |
| `crossDimensionalPoleLoss` | `0` | 跨维度损耗，计在主端侧 |
| `manualLinkDeficitTicks` | `300` | 连续亏电多久挂起链路（300 tick = 15 秒） |
| `reconnectRequestInterval` | `200` | 挂起后多久申请一次重连 |
| `reconnectSort` | — | 存档默认的重连排序 |

> 改了默认值只影响**新建**的配置文件；已存在的配置以文件里的值为准。

## 从源码构建

```bash
./gradlew build
```

产物在 `build/libs/`。首次构建会自动下载 NeoForge 与依赖，需要 Java 21。
无图形界面的 CI 上同样可用（见 `.github/workflows/ci.yml`）。

## 手册

说明书在模组内的藿香手册里（游戏内 `/wires guide` 或背包里按 `W`），由Deepseek生成，暂未核对与校订。

## 许可证

本模组以 **GNU Lesser General Public License v3.0** 发布，全文见 [`LICENSE`](LICENSE)。LGPL-3.0 以引用的方式包含 GPL-3.0 的条款，
未随仓库附带的 GPL-3.0 全文可从 <https://www.gnu.org/licenses/gpl-3.0.txt> 获取。

## 致谢

- **AnvilCraft** 及其作者：本模组的电网、输电杆与结算建立在它的 API 之上；两种杆的**底座与中段的模型与贴图**暂时复用自本体素材，版权归其作者所有。
- **AnvilLib**：配置、注册、方块选择与描边等基础设施。
- **Ageratum（藿香）**：手册系统。
- 本模组新增的头部模型与贴图由 **Yi_Joy** 制作。
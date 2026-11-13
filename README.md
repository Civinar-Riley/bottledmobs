<div align="center">

# 🫙 瓶中生物 · Bottled Mobs

**把生物封进玻璃罐，摆出来，再慢慢折腾。**

Minecraft 1.21.1 · NeoForge 21.1.252 · Java 21

[![Build](https://github.com/Civinar-Riley/bottledmobs/actions/workflows/build.yml/badge.svg)](https://github.com/Civinar-Riley/bottledmobs/actions/workflows/build.yml)

</div>

---

## 这是什么

一个独立实现的 NeoForge 模组。手持空罐对生物右键或左键，把它封进罐子；罐子可以放出生物，也可以作为方块摆出来。摆出来的罐子能换外壳、调大小、调高度、拆掉外壳当玩偶，还能用书与笔把整套展示设置复印到别的罐子上。

玩法灵感来自 firis-games 的 MobBottle。本模组没有使用原模组的代码、贴图或语言文件。

## 特性

| 操作 | 效果 |
| :--- | :--- |
| 空罐对生物右键 / 左键 | 捕获生物，生物数据写入物品，实体被移除 |
| 手持已装罐的罐子对地面右键 | 在点击位置放出生物，罐子恢复为空 |
| 潜行时对地面右键 | 把罐子放置为方块 |
| 手持任意方块右键已放置的罐子 | 瓶身外观替换为该方块 |
| 手持瓶中生物右键已放置的罐子 | 恢复默认玻璃罐外观 |
| 剑右键 | 拆除外壳，只展示生物 |
| 木棍右键 | 玩偶模式：原尺寸、无外壳、贴地 |
| 锄 / 斧右键 | 放大 / 缩小生物，默认 0.1–3.0 倍，可在服务端配置中调整 |
| 镐 / 铲右键 | 升高 / 降低生物，默认 0–1 格、每下 1/16 格，可在服务端配置中调整 |
| 书与笔右键 | 把当前展示设置复印成一本成书 |
| 成书右键 | 把书里记录的设置粘贴到该罐子 |
| 空手右键 | 旋转罐子朝向 90° |
| 潜行 + 空手右键 | 恢复默认外壳、大小、高度，朝向对玩家 |

- 物品名显示瓶内生物，随客户端语言实时解析；命名牌自定义名优先。
- 新捕获或放置的生物默认 0.5 倍大小，可用锄/斧调整。
- 展示动画默认关闭；需要缓慢旋转和模型动画时，在客户端配置中开启。
- 破坏、爆炸、中键复制都会保留瓶内生物和展示设置；捕获新生物时展示设置恢复默认。
- 简体中文、繁体中文、英文三种语言。

## 合成

```text
 W
GBG
GGG
```

- `W`：任意木质台阶
- `G`：任意常见玻璃方块（`c:glass_blocks`）
- `B`：玻璃瓶

产物为 1 个空的瓶中生物。

空罐方块（默认外壳）另有配方：

```text
G G
G G
 S
```

- `G`：任意常见玻璃方块
- `S`：任意木质台阶

产物为 1 个空罐。

## 安装

- Minecraft **1.21.1** + NeoForge **21.1.252**（21.1.x 系列）
- 客户端与服务端都需要安装
- 从 [Releases](https://github.com/Civinar-Riley/bottledmobs/releases) 下载 `bottledmobs-neoforge-1.1.0.jar`
- 把 jar 放进 `mods` 文件夹

## 配置

### 客户端

`config/bottledmobs-client.toml`：

```toml
[general]
    # 展示中的生物是否旋转并播放模型动画（默认关闭）
    animateCapturedMobs = false
```

### 服务端

`config/bottledmobs-server.toml`：

```toml
[display]
    # 缩放范围与每次锄/斧的变化量
    minScale = 0.1
    maxScale = 3.0
    scaleStep = 0.1
    # 高度范围（格）与每次镐/铲的变化量
    minHeight = 0.0
    maxHeight = 1.0
    heightStep = 0.0625
```

缩放允许 0.01–16.0，高度允许 0–4.0。`min` 写得比 `max` 大时会自动互换。

## 限制

- 骑乘中或载有乘客的生物无法捕获。
- 缩放与高度范围由服务端配置决定，默认缩放 0.1–3.0 倍、高度 0–1 格。
- 与旧 MobBottle 的物品和存档不兼容（mod id 不同，方块与物品是全新的）。

## 从源码构建

需要 JDK 21：

```bash
./gradlew build
```

产物位于 `build/libs/bottledmobs-neoforge-1.1.0.jar`。

## 授权

本模组代码与美术资源以 **MIT** 许可发布，见 [LICENSE](LICENSE)。构建脚本来自 NeoForge MDK 的部分遵循 [TEMPLATE_LICENSE.txt](TEMPLATE_LICENSE.txt)。

## 致谢

- 玩法灵感：[firis-games/MobBottle](https://github.com/firis-games/MobBottle)
- 模组加载器与构建工具链：NeoForge 与 NeoGradle 团队

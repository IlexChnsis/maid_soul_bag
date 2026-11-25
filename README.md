# Maid Soul Bag（女仆魂符袋）

![Build](https://github.com/IlexChnsis/maid_soul_bag/actions/workflows/build.yml/badge.svg)

一个 NeoForge 1.21.1 饰品模组：把「装有女仆的魂符」（TLM `smart_slab_has_maid`）存进一枚 Curios 饰品，
用快捷键一键放出 / 一键收回（不限距离，只处理已加载区块），并显示收回数量。

- modid：`maid_soul_bag`；显示名：`Maid Soul Bag`
- 许可：MIT
- 目标环境：Minecraft 1.21.1 / NeoForge 21.1.219+ / Touhou Little Maid 1.5.3+ / Curios 9.0+
- 形态：独立模组（依赖 TLM 与 Curios，不修改上游代码）

> 当前状态：**1.3.1 已完成并出包**（袋中扫帚 + 下车回收 + 铅笔命名弹窗 + 三栏女仆轮盘）。
> 构建产物：`build/libs/maid_soul_bag-1.3.1.jar`。服务端已通过无头自测，客户端界面需在有显示器的机器验收。
> 研发路线与验收标准见 [docs/ROADMAP.md](docs/ROADMAP.md)，环境与构建证据见
> [docs/SESSION-REFERENCE.md](docs/SESSION-REFERENCE.md)。

## 玩法

1. 物品：`maid_soul_bag:soul_bag`（魂符袋），放在 Curios 的 `charm` 槽。可通过**女仆祭坛**合成（末影珍珠×1、线×2、皮革×1、纸×2，需 Power 0.2），创造模式物品栏或 `/give` 也能直接获取。
2. **B**：打开魂符袋界面（复用原版三行箱子 GUI，27 格）。把「有女仆的魂符」拖进去即可；**女仆扫帚也可以放进袋子**（可以放多把，实际按快捷键时只用一把）。
3. **K**：放出袋里**所有属于自己**的女仆；别人的魂符不动（会提示跳过几个）。
4. **L**：收回**所有已加载区块中**属于自己、不限距离的女仆；每收回一只生成一枚魂符放进袋子，提示收回数量。袋满则停止并提示剩余数量。
5. **G**：从袋里取出一把扫帚，放在**视线指向的地面**（原版手长范围内；没有可用落点/视线不在方块上则无反应、不消耗），然后自动执行「放下扫帚 → 放出好感度最低的女仆 → 玩家上扫帚」的完整流程。
6. 快捷键（可配置）：
   - 每个动作一个主键，在「选项 → 控制 → 女仆魂符袋」里修改，默认 **B / K / L / G / R**；
   - **组合键**：在「模组 → Maid Soul Bag → 配置」里给每个动作选择修饰键（NONE/SHIFT/CONTROL/ALT），设置后必须按住该修饰键再按键才触发（例如 release=CONTROL + K → Ctrl+K）。
7. **按住 R（可配置）**：打开「女仆轮盘」，滚轮上下选择袋中 / 已放出的女仆，松开执行：收纳 → 在主人位置放出；在外 → 收回；骑乘 → 左下角提示，不执行。

## 功能

### 魂符收纳与批量放出 / 收回（1.0.0）

- 袋内 27 格，persistent-only 组件（不同步整包 NBT），只同步数量，避免大包卡顿。
- 放出时绕过 TLM 原本"必须点方块上表面"的限制，**直接生成在主人所在坐标**（重叠的多只女仆由原版实体碰撞推挤分开），因此平地上不会再出现"无安全落点"。
- **放出后魂符会变回「空魂符」留在袋里**（与 TLM 手动放出的语义一致）；空魂符/开局魂符也可以放进袋里作为占位，K 会忽略它们。
- 放出前会重新读魂符里的 `Owner`，不是自己的直接跳过。
- 收回使用 TLM 的 `ItemSmartSlab.storeMaidData`，保留整只女仆的 UUID / 数据；放出时 `maid.load()` 还原。收回时优先**把袋里的空魂符变回有女仆的魂符**（没有空魂符时才使用新槽位）。
- 收回**不使用巨型 AABB 查询**（Sable/航空学等模组会把超过 10 万格的实体查询判定为异常并直接中止），改为遍历每个维度当前已加载的全部实体，按主人 UUID 过滤。
- 收回时**跳过正与玩家同乘同一载具（如扫帚）的女仆**，提示跳过数量。
- 与「万法皆通」兼容：带**锚定核心**的女仆也会被正常收回；收回后会校验实体确实已移除，被外部拦截时不生成魂符、不会复制。
- 女仆自己的魂符袋掉在地上时：发光、无敌、无限存活、不会掉入虚空。

### 女仆轮盘（1.1.0）

- **按住 R（默认，可在「选项 → 控制」改键，并可在配置里加修饰键）**，屏幕中央出现三条横栏：中间栏最大、完全居中；上下两栏稍小、左边缘与中间栏对齐。
- 每栏显示：姓名 · 好感度 · 生命值（xx/xx）· 状态（收纳 / 在外 / 骑乘）；「在外 / 骑乘」会附加维度。
- **滚轮上下滚动**：中间项移出时缩小 + 文字变淡，新项移入中间时放大 + 文字变实；首尾**循环**；不足三条时按实际数量显示。
- **松开键**：收纳 → 在主人位置放出该女仆；在外 → 收回该女仆；骑乘 → 左下角提示「正在骑乘，无法收纳」，不执行。
- 列表 = 袋中全部己方魂符（按槽位顺序） + **所有已加载维度的己方活女仆**（同维度按距离由近到远，跨维度排在后面）。
- 与 K/L 共用 1 秒冷却；客户端只回传目标标识，服务端重新校验主人 / 状态 / 冷却。

### 女仆命名（1.2.0：铅笔按钮 + 弹窗）

- 右键自己的女仆打开女仆界面，在「声音包 / YSM 皮肤 / 更改皮肤」同一排、模型展示窗右上方新增**铅笔按钮**（9×9，悬停提示「修改名字」）。
- **点击铅笔 → 弹出独立命名窗口**：输入框（最多 32 字）+ 确定 / 取消，`Enter` 确定、`Esc` 取消；**不消耗命名牌**。
- 保存后头顶名字牌、女仆界面、轮盘立即生效；**留空 = 清除自定义名**（恢复模型名）。
- 按钮位置可用配置 `maid_gui.name_button_offset_x/y` 微调（默认 32/14）。

### 袋中扫帚（1.3.0）

- 袋内可以放任意多把「女仆扫帚」（每把占 1 格，与魂符混放）；魂符计数、K/L、轮盘都不会把扫帚当成魂符处理。
- 按 **G（可配置，可加修饰键）**：从袋中取出一把扫帚，放在**视线指向的方块面**上（原版手长范围）。
  - 校验与 TLM 扫帚物品一致：不能放在方块底面、落点需无碰撞且无实体；
  - 放置成功后自动沿用扫帚联动：放出好感度最低的己方女仆 + 玩家直接骑上扫帚；
  - 视线不在方块上 / 超出范围 / 落点被占用 / 袋里没有扫帚 → **无反应，不消耗**。
- **回收（1.3.1）**：从扫帚下来时，扫帚优先放回魂符袋的空格（袋满才回背包，背包满则掉落）；身上没带魂符袋时才直接进背包。
- 一次只消耗一把；袋里剩下的扫帚可以继续用。

### 扫帚联动

- **放下扫帚**：装备魂符袋时，用「女仆扫帚」物品正常放置扫帚实体，模组会从袋中挑选**好感度最低**的己方女仆（并列则随机）并直接释放在扫帚所在位置，聊天栏提示好感度；**同一次操作里玩家会被直接放上扫帚**。
- **从扫帚下来**：玩家下车后，下一 tick 会把**扫帚收回魂符袋的空格**，然后把这名女仆**收回魂符袋**（优先用袋里的空魂符；袋满则魂符掉落）。
- 若扫帚被直接打掉/销毁，则不再补发扫帚（TLM 已掉落扫帚物品），但仍会收回那名女仆。
- 限制：同一玩家同时只维护一条扫帚联动；服务器重启会丢失联动记录（女仆会留在原地，可手动用 L 收回）。

## 目录结构

```
maid_soul_bag/
├── build.gradle                  # ModDevGradle 2.0.148，Java 21 toolchain
├── gradle.properties             # 版本号、mod 元信息与依赖 versionId
├── settings.gradle
├── gradle/wrapper/               # Gradle 8.14.2（固定版本）
├── LICENSE                       # MIT
├── CHANGELOG.md                  # 按版本记录
├── docs/
│   ├── ROADMAP.md                # 需求、里程碑、验收标准、当前限制
│   ├── SESSION-REFERENCE.md      # 版本矩阵、构建验证、已知环境问题
│   ├── design-v1.1-naming-and-wheel.md   # 命名栏 + 轮盘开发计划（已实现）
│   └── design-v1.1-radial-ui.md          # 界面形态设计草案（已实现）
├── .github/workflows/build.yml   # CI：JDK 21 + ./gradlew build
├── src/main/templates/META-INF/neoforge.mods.toml   # 占位符模板，构建时展开
└── src/main/java/com/maidsoulbag/
    ├── MaidSoulBag.java                     # @Mod 入口
    ├── item/SoulBagItem.java                # Curios 饰品物品
    ├── storage/BagContainer.java            # 27 格容器 + 组件读写
    ├── logic/BagActions.java                # 批量放出 / 收回
    ├── logic/BagUtil.java                   # 槽位与魂符判定工具
    ├── logic/BroomAutomation.java           # 扫帚联动
    ├── logic/BroomPlacer.java               # 从袋中放置扫帚
    ├── logic/WheelService.java              # 轮盘列表与执行（服务端权威）
    ├── network/*.java                       # 5 个 payload（Bag/Broom/Rename/Wheel）
    ├── config/ClientConfig.java             # 键位修饰键、按钮偏移、音效开关
    ├── event/CommonEvents.java              # 注册、冷却、掉落物保护
    ├── debug/SelfTest.java                  # /maidbag selftest
    └── client/                              # 键位、GUI 钩子、铅笔按钮、轮盘渲染
```

## 构建

前置：**JDK 21 必须本机预装**（`java -version` 显示 21.x）。本仓库没有引入 `foojay-resolver` 插件，
Gradle 不会自动下载 JDK，也没有写死 `org.gradle.java.home`，用系统默认 JDK。

首次构建需要联网拉取 NeoForge、Minecraft 与两个依赖模组。

```bash
# Windows
.\gradlew.bat build

# 只编译，最快验证工程是否可用
.\gradlew.bat compileJava

# 启动客户端 / 无头服务器
.\gradlew.bat runClient
.\gradlew.bat runServer
```

产物：`build/libs/maid_soul_bag-<version>.jar`。

### 依赖来源

| 依赖 | 坐标 |
|---|---|
| 车万女仆 TLM 1.5.3+ | `maven.modrinth:touhou-little-maid:tXG1TkGx` |
| Curios 9.5.1+ | `maven.modrinth:curios:yohfFbgD` |

两者都只需编译期 API，因此声明为 `compileOnly`；运行时由整合包或 `run/mods/` 提供。
坐标里的 `tXG1TkGx` / `yohfFbgD` 是 Modrinth 的 versionId，可在 `gradle.properties` 里改版本。

离线兜底：把 jar 放进 `./libs`，把 `build.gradle` 里的两行 `compileOnly "maven.modrinth:..."` 换成
`compileOnly files("libs/<file>.jar")`（`libs/*.jar` 默认不入库）。

本机需要代理才能访问 `maven.neoforged.net` 时，取消 `gradle.properties` 里四行 `systemProp.*.proxyHost/Port` 注释；
也可以只在单次命令传入：`-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7897`（https 同理）。

## 测试命令（仅 OP）

```
/maidbag selftest
```

在无头服务器上创建假玩家，自动验证：魂符 tag 生效 → 召回自己的女仆 → 放出（生成在主人坐标、UUID 一致、魂符变回空魂符）→ 再次召回（回收空魂符）→ 不召回他人女仆 → 不放出他人魂符 → 扫帚放出/收回 → 同乘不收回 → 轮盘列表与执行 → 改名/清名 → 袋内扫帚过滤 → 从袋中放置扫帚。

实测输出（干净存档，v1.0.0 的前 6 段；v1.1.0 追加 stage 10–14；v1.3.0 追加 stage 15–16；v1.3.1 追加 stage 17）：

```
[selftest] stage=0 bagItemSlots=[charm] bagIsSoulBag=true
[selftest] stage=1 recall stored=1 ownMaidGone=true
[selftest] stage=2 release stored=0 emptySlabKept=true maidsNearby=1 sameUuid=true
[selftest] stage=3 recallAgain stored=1 ownMaidGone=true emptySlabsLeft=0 (expect 0 = empty soul spell was reused)
[selftest] stage=4 foreignRecall stored=1 otherMaidStillThere=true
[selftest] stage=5 foreignRelease before=2 after=1 (own released, foreign kept)
[selftest] stage=6 broomRelease releasedLowFavorability=true stored=1 (expect 1)
[selftest] stage=7 broomRecall stored=2 maidGone=true (expect 2/true)
[selftest] stage=8 broomItem hasBroomInInventory=true
[selftest] stage=9 sameVehicleSkip maidOnBroom=true playerOnBroom=false ridingMaidKept=false (fake player cannot mount; needs in-game check)
[selftest] stage=10 wheelState rows=4 stored=2 released=1 riding=1 nameParsed=true maidMounted=true (expect 4/2/1/1/true/true)
[selftest] stage=11 wheelRelease ok=true maidBack=true storedLeft=1 (expect true/true/1)
[selftest] stage=12 wheelRecall ok=true maidGone=true stored=2 (expect true/true/2)
[selftest] stage=13 wheelRidingRefused refused=true maidKept=true (expect true/true)
[selftest] stage=14 wheelRename renamed=true nameOk=true cleared=true clearOk=true (expect all true)
[selftest] stage=15 broomFilter wheelRows=3 maidRows=1 (expect 3/1: 1 stored maid + 2 live maids, 2 brooms filtered)
[selftest] stage=16 broomPlace placed=true spawned=true broomsLeft=1 blockedRefused=true broomsAfterBlocked=1 (expect true/true/1/true/1)
[selftest] stage=17 broomReturn toBag=true broomInBag=1 fullRefused=true (expect true/1/true)
```

## 当前限制

- 容量固定 27、操作冷却固定 1 秒（配置里只能改修饰键、改名按钮偏移与音效）。
- 袋中扫帚的放置要求「视线指向方块 + 原版手长内 + 落点无碰撞且无实体」，不满足时按 G 无反应、不消耗扫帚。
- 界面打开期间若把饰品卸下，界面内改动不会写回（关闭后重开即可）。
- 未做"死亡时不掉落"的额外保护，只做了掉落物本体保护（发光/无敌/无限存活/防虚空）。
- 同一玩家同时只维护一条扫帚联动；服务器重启会丢失联动记录。

## 上游与来源

- [Touhou Little Maid](https://github.com/TartaricAcid/TouhouLittleMaid)（必需依赖）
- [Curios API](https://modrinth.com/mod/curios)（必需依赖）
- 工程脚手架基于 NeoForged MDK（MIT），见 `TEMPLATE_LICENSE.txt`。
- 更新记录见 [CHANGELOG.md](CHANGELOG.md)。

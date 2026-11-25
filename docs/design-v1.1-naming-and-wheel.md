# v1.1 开发计划：女仆内联改名栏 + 三栏式女仆轮盘

> **归档说明（2026-11-25）**：本文件是 1.1.0 开发前写下的计划稿，其中的 Q1–Q12 已全部拍板并实现。
> 实际交付与本文档的差异：内联改名栏在 1.1.0 实现后，于 **1.2.0 改为铅笔按钮 + 独立弹窗**（见 [CHANGELOG](../CHANGELOG.md)）。
> 保留原文作为设计留档，不再随实现更新。
>
> 状态：~~待确认~~ → **已实现**（1.1.0 轮盘 + 改名；1.2.0 改为弹窗）
> 版本号：**1.1.0**（轮盘）/ **1.2.0**（命名弹窗）

---

## 1. 范围与验收标准

### 1.1 命名栏（方案 A：内联名字栏，不消耗命名牌）

| 验收项 | 标准 |
|---|---|
| 入口 | 主人打开 TLM 女仆主界面，界面上出现一行可点击的名字 |
| 显示 | 有自定义名 → 显示该名字；无 → 灰字显示模型名（如「灵梦」） |
| 编辑 | 点击 → 原地变为输入框（不弹新窗口）；`Enter` 保存、`Esc` 取消；最多 32 字符 |
| 保存 | 服务端校验（是主人、实体存活、距离 ≤ 8 格）后写入 `CustomName`；不消耗命名牌 |
| 清空 | 输入空字符串并确认 = 清除自定义名（回到模型名显示） |
| 即时性 | 保存后：女仆头顶名字牌、女仆界面、轮盘名字立刻反映 |
| 不影响 | TLM 原有按钮/槽位/任务列表/模型预览的交互不受影响 |

### 1.2 轮盘（三栏式，按住 R）

| 验收项 | 标准 |
|---|---|
| 打开 | 按住可配置键（默认 `R`）→ 屏幕中央出现三条横栏（上/中/下） |
| 布局 | 中间栏最大且水平垂直完全居中；上下两栏稍小且**左边缘与中间栏对齐** |
| 内容 | 每栏：女仆姓名、好感度、生命值 `xx/xx`、状态（收纳 / 释放 / 骑乘） |
| 滚动 | 按住期间滚轮上下滚动，一次最多显示 3 条；不足 3 条时按实际数量显示 |
| 动画 | 滚动时原中间项向滚动方向移出并缩小 + 文字变淡；新项移入中间并放大 + 文字变实 |
| 松开 | 中间项为「收纳」→ 在主人位置放出；「释放」→ 收回该女仆；「骑乘」→ 左下角提示，无动作 |
| 取消 | `Esc` / 关闭界面 = 取消，不执行任何动作 |
| 服务端权威 | 客户端只发“目标标识”，服务端重新校验主人/位置/状态/冷却 |

---

## 2. 命名栏（方案 A）详细设计

### 2.1 位置与外观

TLM 女仆主界面是 256×256（`imageWidth/Height=256`，`leftPos = width/2-128`）。

- **首选位置**：右侧大框内顶部，`左边缘 = leftPos + 84`、`顶部 = topPos + 33`、宽 `160`、高 `12`（单行）。
  - 未编辑态：无边框，左侧画名字（`#F2EAD3`），右侧画一个淡色 `✎` 提示可点击；无自定义名时画灰字模型名（`#8C8577`）。
  - 编辑态：显示 EditBox 边框（1px `#E8C36A66`，底色 `#1E1B2A99`），光标可见，超出宽度时内部横向滚动。
  - 悬停 tooltip：「点击重命名（最多 32 字，留空恢复默认）」。
- **位置可调**：给两个客户端配置项 `nameRowOffsetX`（默认 84）、`nameRowOffsetY`（默认 33），万一在游戏里压到 TLM 原有元素，改配置即可，不用重编译。
- **备选位置**（若右侧大框被占用）：左列三条状态条下方 `leftPos+5, topPos+150`，宽 66（名字会截断，仅显示前 6 个汉字，tooltip 显示全名）。
  → 见 [图 1] 位置示意（开发时截图确认后定稿）。

```
[图 1] TLM 女仆界面（256×256）我方新增元素位置
┌──────────────────────────────┐  leftPos/topPos
│  ┌──────┐        ┌─────────┐ │
│  │ 3D  │        │名字 灵梦 ✎│ │ ← 首选：右侧大框顶部
│  │ 女仆 │        └─────────┘ │
│  │ 预览 │                    │
│  │      │     [背包/槽位]     │
│  ├─血条─┤                    │
│  ├─护甲─┤  ┌───────────────┐ │
│  ├─好感─┤  │  玩家物品栏    │ │
│  └──────┘  └───────────────┘ │
└──────────────────────────────┘
```

### 2.2 交互流程

1. 右键自己的女仆 → TLM 主界面（`MaidMainContainer`）。
2. 点击名字行 → 进入编辑态（自动全选当前名字）。
3. 输入 → `Enter` 保存（发送 C2S）、`Esc` 取消（恢复原值）。
4. 保存成功：服务端回执 → 客户端本地立即刷新显示（不等下一帧同步）。
5. 点击界面其它位置 → 结束编辑（等同保存还是取消？**默认：保存**，与 TLM 命名牌 GUI 一致；见 Q7）。

### 2.3 数据与校验

- 新 C2S 包：`RenameMaidPayload(int entityId, String name)`
- 服务端处理：
  - 实体必须是 `EntityMaid`、存活、`Owner == 玩家`、`distanceToSqr ≤ 64`；
  - `name` 去首尾空白、截断 32；空串 → `setCustomName(null)`；
  - 非空 → `setCustomName(Component.literal(name))`；
  - 是否顺带 `setCustomNameVisible(true/false)` → 见 Q6；
  - `setPersistenceRequired()`（与 TLM 命名牌一致）。
- 不做颜色码解析（`§` 按普通字符处理，避免与 TLM 行为不一致）。

### 2.4 兼容/边界

- 只在**主女仆界面**注入（判断菜单是 `MaidMainContainer`），背包/配置/Curios 分页不显示。
- 其它模组若也占用 `MaidContainerGuiEvent.Init` 添加按钮：我们使用唯一 key `maid_soul_bag.name`，不冲突。
- 界面 resize：TLM 每次 `init()` 会重发 `MaidContainerGuiEvent.Init`，控件自动重建，无需特殊处理。
- 编辑中关闭界面：不发送、不保存。
- 非主人打开女仆界面（若服务端允许）：名字只读（灰字 + 禁止点击）。

---

## 3. 轮盘详细设计

### 3.1 视觉规格（数值均为 1x GUI 缩放下的逻辑像素）

```
[图 2] 三栏布局（width×height 为当前窗口）
                          ┌─────────────────────────┐
                          │ 灵梦      好感 42  20/20 收纳 │  ← 上栏 200×28，左对齐
                          └─────────────────────────┘
        ┌───────────────────────────────────────────┐
        │ 魔理沙      好感 30  14/20  释放          │  ← 中栏 240×40，完全居中
        └───────────────────────────────────────────┘
                          ┌─────────────────────────┐
                          │ 咲夜      好感 66  20/20 骑乘 │  ← 下栏 200×28，左对齐
                          └─────────────────────────┘
```

- 中栏：`240×40`，左上角 `(width/2-120, height/2-20)`。
- 上下栏：`200×28`，左边缘与中栏相同（`x = width/2-120`）；
  - 上栏中心 y = 中栏中心 y − 56；下栏中心 y = 中栏中心 y + 56。
- 栏内文字（中栏）：
  - 姓名：左侧 `x+10`，9px 字体，`#F2EAD3`；
  - 好感度：`好感 42`，`x+118` 起，金色；
  - 生命值：`生命 20/20`（或 `20/20`），`x+168` 起；
  - 状态：右对齐 `x+width-8`，颜色：收纳 `#8CC6FF` / 释放 `#7FBF7F` / 骑乘 `#E8C36A`。
- 上下栏文字：整体缩放 `0.8`，同样四段但间距压缩。
- 栏底：`0x991E1B2A`（中）/ `0x771E1B2A`（上下）；1px 边框 中 `#E8C36A`、上下 `#66E8C36A`。
- 中栏选中高亮：底色额外叠加 `#E8C36A14`，左侧 2px 金色竖条。
- 全屏遮罩：`0x40000000`（让三栏突出，但不完全挡世界）。
- 角标：若该女仆佩戴锚定核心，姓名右侧加紫色 `⚓`（可选，见 Q10）。

### 3.2 滚动动画（核心手感）

- 维护 `selectedIndex`（整数）。每个条目有一个浮点 `visualSlot = i - selectedIndex`，随时间动画逼近目标值。
- 渲染时按 `|visualSlot|` 推导：
  - 纵向位置：`y = centerY + visualSlot * 56`
  - 缩放：`scale = 1 - 0.30 * |visualSlot|`（±1 → 0.70）
  - 文字透明度：`alpha = 1 - 0.55 * |visualSlot|`（±1 → 0.45）
  - 只渲染 `|visualSlot| < 1.55` 的条目（最多 3 条；不足 3 条时自然只剩实际数量）。
- 动画速度：`150ms`，缓出（ease-out）；滚轮每格 `selectedIndex ±1`，首尾**不循环**（到头停住，见 Q3）。
- 滚轮一次可以触发多格（`scrollY` 绝对值 > 1 时按格数处理）。
- 打开动画：整体 `0.9→1.0` 缩放 + 150ms 淡入；关闭：100ms 淡出（若为执行动作则立即关闭）。

### 3.3 数据模型（客户端）

```
WheelEntry {
  source: BAG | ENTITY
  bagSlot: int        // BAG 时有效
  entityId: int       // ENTITY 时有效
  name: Component     // 自定义名（JSON）；空则用 fallbackName
  fallbackName: String// 模型名（客户端 i18n）
  favorability: int
  health: float
  maxHealth: float
  status: STORED | RELEASED | RIDING
}
```

- 列表顺序：**袋中按槽位升序在前；已放出的活女仆按距离由近到远在后**（见 Q2）。
- 初始选中：会话内记忆上次位置；首次打开选第一条（见 Q4）。

### 3.4 列表来源（服务端汇总，随状态请求一起下发）

- 袋中：遍历 `contents[0..26]`，跳过空魂符/非魂符/他人员魂符；
  - 名字：NBT `CustomName`（JSON 组件）；
  - 好感度：NBT `MaidFavorability`；
  - 生命：NBT `Health`；最大生命：NBT `attributes` 里 `minecraft:generic.max_health` 的 `base`（缺失则 20）；
  - 状态：`STORED`。
- 已放出：遍历所有维度的已加载实体（沿用现有召回逻辑的 `getAllEntities()` 方式），筛 `EntityMaid` 且 `Owner == 玩家`；
  - `RIDING` 若 `maid.getVehicle() != null`，否则 `RELEASED`；
  - 生命：`getHealth()/getMaxHealth()`。
- 只包含己方；空魂符与别人的魂符不占位。

### 3.5 打开/滚轮/松开 状态机

```
[idle] --按住R--> 发送 WheelRequestPayload，打开 WheelScreen（显示"读取中…"）
[loading] --收到 WheelStatePayload--> [browsing]
[loading] --空列表--> 关闭 + 左下角提示"袋中没有可操作的女仆"
[browsing] --滚轮--> selectedIndex ±1（动画）
[browsing] --松开R--> 结算：
    STORED   → 发送 WheelActionPayload(BAG, slot) → 关闭 → 服务端放出 → 聊天栏提示
    RELEASED → 发送 WheelActionPayload(ENTITY, id) → 关闭 → 服务端收回 → 聊天栏提示
    RIDING   → 关闭 + 左下角提示"女仆正在骑乘，无法收纳"（无网络请求）
[browsing] --Esc/界面关闭--> 关闭，无动作
```

- 按住检测：客户端 tick 检查 `KeyMapping` 按下 → 打开；WheelScreen 内每 tick 用 `InputConstants.isKeyDown` 轮询，松开即结算（同时响应 `keyReleased`，取先到者，只执行一次）。
- 快捷键与组合键：新增键位 `key.maid_soul_bag.radial`（默认 `R`）+ 配置项 `modifiers.radial`（NONE/SHIFT/CONTROL/ALT），与现有 K/L/B 一致。

### 3.6 网络协议

| 方向 | 包 | 字段 | 说明 |
|---|---|---|---|
| C2S | `WheelRequestPayload` | 无 | 请求列表 |
| S2C | `WheelStatePayload` | `List<Entry>`（source、slot/entityId、nameJson、modelId、favor、health、maxHealth、status） | 约 27 条 ≈ 2–4 KB |
| C2S | `WheelActionPayload` | source、slot/entityId | 执行放出/收回 |
| C2S | `RenameMaidPayload` | entityId、name | 改名（见 2.3） |

- 服务端处理 `WheelActionPayload` 时重新读袋/查实体，并校验：主人、距离（放出无距离要求；收回需实体已加载）、状态（骑乘拒绝）、冷却。
- 拒绝时回聊天栏提示（沿用现有 message 文案风格）。

### 3.7 服务端动作实现要点

- 放出指定槽位：新方法 `BagActions.releaseFromSlot(player, bag, slot)`——校验槽位是己方魂符 → `spawnMaidAt(player, charm, 玩家坐标, 玩家朝向)` → 槽位写回空魂符 → 保存。
- 收回指定实体：复用 `recallMaidById`，但新增“骑乘中拒绝”前置判断。
- 冷却：与 K/L 共用 20 tick 冷却（见 Q9）。
- 操作后关闭轮盘（客户端关闭），聊天栏提示与现有风格一致：
  - `message.maid_soul_bag.wheel_released`「已放出：%s」
  - `message.maid_soul_bag.wheel_recalled`「已收回：%s」
  - `message.maid_soul_bag.wheel_riding`「%s 正在骑乘，无法收纳」（左下角提示）

---

## 4. 文件与改动清单（实现层面）

### 新增

| 文件 | 作用 |
|---|---|
| `client/wheel/WheelScreen.java` | 轮盘 Screen：三栏渲染、滚轮、松开结算、动画 |
| `client/wheel/WheelEntry.java` | 客户端条目模型 |
| `client/wheel/WheelClientState.java` | 持有列表、selectedIndex、上次位置 |
| `client/wheel/WheelHintOverlay.java` | 左下角提示（淡入淡出，约 40 tick） |
| `client/MaidNameField.java` | 内联名字输入控件（EditBox 子类，`✎`、tooltip、Enter/Esc） |
| `client/MaidGuiHook.java` | 监听 `MaidContainerGuiEvent.Init`，仅主女仆界面注入名字控件 |
| `network/WheelRequestPayload.java` | C2S 请求列表 |
| `network/WheelStatePayload.java` | S2C 列表数据 |
| `network/WheelActionPayload.java` | C2S 执行动作 |
| `network/RenameMaidPayload.java` | C2S 改名 |
| `logic/WheelService.java` | 汇总列表、执行放出/收回、校验 |

### 修改

| 文件 | 改动 |
|---|---|
| `client/ClientKeys.java` | 新增 `RADIAL` 键位 + 按住打开逻辑（只发请求，不执行动作） |
| `client/ClientSetup.java` | 注册新键位 |
| `config/ClientConfig.java` | `modifiers.radial`、`nameRowOffsetX/Y` |
| `logic/BagActions.java` | `releaseFromSlot`、收回时“骑乘”前置判断 |
| `logic/BagUtil.java` | NBT 读取最高/当前生命、`CustomName` JSON、锚定核心判断（可选） |
| `event/CommonEvents.java` | 注册 4 个新 payload 及服务端处理 |
| `assets/maid_soul_bag/lang/zh_cn.json` / `en_us.json` | 新键位、提示、按钮、tooltip 文案 |
| `README.md` | 功能与版本记录 |

### 资源

- 不新增贴图（三栏与文字全部用 `fill`/`drawString` 绘制，保持轻量）；如后续要加“纸张/金边”质感，再加一张 9-slice。
- 音效：可选，滚轮/确认各一个轻音（见 Q11）。

---

## 5. 测试计划

### 5.1 自动（扩展 `/maidbag selftest`）

- stage 10：往袋里放两只不同好感/名字的魂符 → 调 `WheelService.build(player)` → 断言：条目数、名字/好感/生命/状态正确、空魂符被过滤。
- stage 11：调 `releaseFromSlot` 放出指定槽位 → 断言：目标女仆出现在玩家坐标、魂符变空魂符、另一只未受影响。
- stage 12：对已放出女仆执行“收回动作”路径 → 断言：实体消失、魂符回到对应槽位。
- stage 13：骑乘中（假玩家+扫帚）执行收回动作 → 断言：被拒绝且实体仍在。

### 5.2 手动（进游戏）

1. 右键自己的女仆 → 名字行显示；点击 → 输入 → Enter → 头顶名字变化；重进界面仍在。
2. 别人女仆界面：名字只读。
3. 按住 R：三条栏出现；滚轮上下滚动，动画为“中间缩小淡出、新项放大变实”；不足 3 条时只显示实际数量。
4. 中间项为收纳 → 松手：女仆出现在脚下、魂符变空魂符、聊天栏提示。
5. 中间项为释放 → 松手：女仆被收回。
6. 中间项为骑乘 → 松手：左下角提示，无动作，屏幕关闭。
7. 打开时无女仆：提示“没有可操作的女仆”，屏幕关闭。
8. 在各 GUI 缩放比例（1x/2x/自动）下检查三栏文字不重叠。
9. 与 TLM 自带界面按钮、其他模组 HUD 无冲突。

---

## 6. 待确认问题（Q1–Q12）

> 标记 ★ 的会阻塞开发；其余我已按推荐默认值实现，后续可改。

**★ Q1 列表范围**：轮盘里列出哪些女仆？
- (a) 仅袋中 + **当前维度**已加载的己方活女仆（推荐）
- (b) 袋中 + **所有维度**已加载的己方活女仆
- (c) 仅袋中（那「释放/骑乘」状态就永远不会出现，与你的需求冲突）

**★ Q2 排序**：
- (a) 袋中按槽位顺序在前，已放出按距离由近到远（推荐）
- (b) 全部按好感度降序
- (c) 收纳/释放/骑乘分组，组内按好感度

**★ Q3 首尾是否循环**：滚到第一条再向上 → 停在第一条（推荐不循环） vs 循环到最后一条？

**★ Q4 初始选中**：记忆上次选中的女仆（推荐）vs 每次打开都选第一条？

**★ Q5 交互载体**：用 `Screen`（打开时锁定移动/视角，鼠标可点、滚轮可用）（推荐）vs 不锁移动的 HUD 覆盖层？

**★ Q6 骑乘判定**：只要女仆在任何载具上就算「骑乘」并拒绝收回（推荐）；还是仅“与玩家同乘”才算？

**★ Q7 名字栏位置**：右侧大框顶部一行（`leftPos+84, topPos+33`，推荐）还是左列状态条下方？如果你在游戏里看更合适的位置，告诉我大致方位（例如“右边大框上面”/“左列最下面”）。

**Q8 名字牌显示**：设置名字后是否自动让头顶名字牌常显（`CustomNameVisible=true`）？推荐：是；清空名字时关掉。

**Q9 冷却**：轮盘单次放出/收回是否沿用现有 1 秒冷却？推荐：是（防连点）。

**Q10 状态文案**：就用「收纳 / 释放 / 骑乘」三个词？我建议「收纳 / 在外 / 骑乘」更直观（“释放”读起来像动词）。你定。

**Q11 音效**：打开/滚动/确认 是否加轻音效？推荐：滚动加很轻的“咔”，确认加一声“叮”，可在配置关闭。

**Q12 版本号**：下一版用 **1.1.0**？

---

## 7. 实施顺序与预估

| 步骤 | 内容 | 预估 |
|---|---|---|
| S1 | 网络包骨架 + `WheelService` 列表汇总 + selftest stage 10 | 0.5 天 |
| S2 | 改名控件 + 服务端改名 + 界面注入（先用配置偏移量定位） | 0.5 天 |
| S3 | `WheelScreen` 三栏渲染 + 滚轮 + 动画 | 1 天 |
| S4 | 动作分发（放出指定槽 / 收回指定实体 / 骑乘提示 + 左下角 overlay） | 0.5 天 |
| S5 | 键位/配置/lang/README + 边界打磨 | 0.5 天 |
| S6 | selftest 扩展 + 进游戏手动验收 + 打包 1.1.0 | 0.5 天 |

合计约 **3.5 个工作日**（不含你确认与验收往返）。

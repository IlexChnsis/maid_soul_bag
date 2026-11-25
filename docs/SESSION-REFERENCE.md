# 环境参考与构建证据

> 建立时间：2026-10-24；整理为公开仓库时（2026-11-25）重写。
> 本文档记录版本矩阵、构建验证方式与已知环境问题；研发路线见 [ROADMAP.md](ROADMAP.md)。

## 1. 版本矩阵（已实测可协同加载）

| 组件 | 版本 | 位置 / 来源 |
|---|---|---|
| Minecraft | 1.21.1 | 由 ModDevGradle 自动下载 |
| NeoForge | 21.1.219 | `gradle.properties: neo_version` |
| 构建插件 | ModDevGradle 2.0.148 + Gradle 8.14.2 | `build.gradle` / `gradle/wrapper` |
| JDK | Zulu 21.0.10 | 系统 PATH（本机 `java -version`） |
| Curios API | 9.5.1+1.21.1 | `maven.modrinth:curios:yohfFbgD`（编译）+ `run/mods/`（运行） |
| 车万女仆 TLM | 1.5.3-neoforge+mc1.21.1 | `maven.modrinth:touhou-little-maid:tXG1TkGx`（编译）+ `run/mods/`（运行） |
| 万法皆通（兼容测试用） | 1.9.0-neoforge | 手工放入 `run/mods/`，未纳入构建 |
| 本模组 | maid_soul_bag 1.3.1 | 魂符袋 + 扫帚联动 + 命名弹窗 + 三栏轮盘 |

## 2. 依赖获取方式

两个依赖模组都走 Modrinth maven，`compileOnly` 声明，不需要把 jar 提交进仓库：

```groovy
compileOnly "maven.modrinth:touhou-little-maid:${tlm_version}"   // tXG1TkGx = 1.5.3-neoforge+mc1.21.1
compileOnly "maven.modrinth:curios:${curios_version}"           // yohfFbgD = 9.5.1+1.21.1
```

- versionId 是 Modrinth 的版本主键，与文件名无关；改版本时在 `gradle.properties` 里替换。
- 运行时仍需把对应的完整 jar 放进 `run/mods/`（或整合包），`compileOnly` 不参与运行时。
- 离线环境：把 jar 放进 `libs/`，把上面两行换成 `compileOnly files("libs/<file>.jar")`。

## 3. 本机网络适配（换环境要改）

- 依赖下载走本地代理（Clash）：`127.0.0.1:7897`。
- 仓库内 `gradle.properties` 的四行 `systemProp.*.proxyHost/Port` **默认注释掉**，方便 CI 与全新环境。
- 本机把代理写在 `GRADLE_USER_HOME` 的全局 `gradle.properties` 里，不动仓库文件。
- 历史故障：系统 DNS 曾把 `maven.neoforged.net` 解析到错误 IP，直连超时；走代理后正常。

## 4. 构建环境注意（沙箱 / 受限账户）

- DSH 沙箱只允许写工作区，因此本机把 `GRADLE_USER_HOME` 指到工作区内的 `./.gradle-home`（已在 `.gitignore`）。
  首次需要把 `~/.gradle/wrapper/dists/gradle-8.14.2-bin` 复制进 `.gradle-home/wrapper/dists`，否则 wrapper 会尝试重新下载。
- `gradlew` 在本仓库以 **LF** 检出（`.gitattributes` 固定 `text eol=lf`），否则 Linux/CI 上 `./gradlew` 会因 shebang 带 CR 失败。
- **NeoForm 重编译故障与绕过**：`createMinecraftArtifacts` 在 recompile 阶段写
  `net/minecraft/world/ticks/package-info.java` 时抛 `IOException`（message 为 `null`），
  与 ModDevGradle（2.0.78 / 2.0.91 / 2.0.148）、NeoFormRuntime、JDK（Zulu / Temurin）均无关。
  该故障只影响「重编译后的 MC 源码/类」，不影响本模组产物 jar。
  因此 `build.gradle` 在 `neoForge { enable { ... disableRecompilation = true } }` 里绕过；
  **换到没有该故障的机器时删掉这一行即可恢复完整重编译流程**（代价是该机器的 IDE 看不到反编译 MC 源码）。
- 沙箱下可能出现两条**非致命**告警，与产物无关：
  - `Caught exception: Couldn't open current thread, error = 5`（Gradle file watcher 无法开线程）
  - `sh.exe: couldn't create signal pipe, Win32 error 5`（MSYS 辅助程序无法建命名管道）
- git 传输在本机需要使用 OpenSSL 后端：`-c http.sslBackend=openssl`。
  默认的 schannel 后端会报 `AcquireCredentialsHandle failed: SEC_E_NO_CREDENTIALS`（受限账户无法访问加密密钥）。

## 5. 已验证项

1. `.\gradlew.bat build` → `BUILD SUCCESSFUL`，产物 `build/libs/maid_soul_bag-<version>.jar`。
2. `.\gradlew.bat runServer` 启动成功，日志关键行：
   - `Mod List`：Curios API、Maid Soul Bag、Minecraft 1.21.1、NeoForge 21.1.219、Touhou Little Maid
   - `[MaidSoulBag] skeleton loaded | TLM=true Curios=true TLM-Spell=false`
   - `Done (3.638s)!`，RCON 监听 25575，`stop` 后干净退出（exit 0）。
3. `/maidbag selftest` 在干净存档跑通 `stage=0`–`stage=17`，输出见 [README](../README.md#测试命令仅-op)。
4. 编译期可直接引用 TLM 的 `ItemSmartSlab` / `InitItems` / `InitDataComponent` 与 Curios 的 `ICurioItem`。

## 6. 已知非阻塞现象

- 启动日志有 3 条 Curios ERROR：`scroll / feet / spellbook is not a registered slot type!`。
  这是 TLM 的数据包引用了 Curios 9.5.1 默认未定义的槽位（部分槽位来自 Iron's Spellbooks 等可选模组），
  **不影响加载与运行**；本模组使用的 `charm` 槽位未报错。
- 万法皆通 1.9.0 未放进 `run/mods` 时日志显示 `TLM-Spell=false`；做兼容测试时把它复制进去即可。
- 本环境**无显示器**，`runClient` 无法运行；客户端冒烟测试需在有显示器的机器执行。

## 7. 常用命令

```powershell
# 编译 + 打包
.\gradlew.bat build

# 只编译
.\gradlew.bat compileJava

# 启动无头专用服务器（run/ 目录，已含 eula=true 与 mods）
.\gradlew.bat runServer

# 需要显示器的机器
.\gradlew.bat runClient

# 数据生成
.\gradlew.bat runData
```

## 8. 目录约定

```
maid_soul_bag/
├─ libs/            # 离线兜底用依赖 jar（不入库）
├─ run/mods/        # 开发运行时模组（TLM / Curios，不入库）
├─ run/logs/        # 服务器日志（验证证据，不入库）
├─ docs/            # 路线、环境、界面设计
├─ src/main/java/com/maidsoulbag/   # 全部源码
├─ src/main/resources/              # 资源、语言文件、配方与标签
└─ src/main/templates/META-INF/neoforge.mods.toml   # 已声明 TLM / Curios 依赖
```

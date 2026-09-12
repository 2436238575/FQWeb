# AGENTS.md

本仓库是番茄小说（`com.dragon.read`）的 Xposed 模块 FQWeb 的个人维护 fork（上游 fengyuecanzhu/FQWeb 已删除）：模块注入宿主后内嵌 NanoHTTPD Web 服务，对外提供书籍搜索/详情/目录/正文等 HTTP API。所有运行时代码都跑在**宿主进程**里，错误处理不当会直接崩掉番茄小说；动手前先读完本文件。

## 项目概览

- **项目**：FQWeb —— 番茄小说 Web 服务 Xposed 模块，LSPosed 作用域为 `com.dragon.read`。
- **技术栈**：Kotlin + Xposed API（`app/libs/api-82.jar`，compileOnly）+ NanoHTTPD 2.3.1；`frpc` flavor 额外内嵌 `frpclib.aar`（约 40MB，含内网穿透客户端）。
- **applicationId**：`me.fycz.FQWeb`；代码包名/namespace 保持 `me.fycz.fqweb`，二者不同是有意的，不要"统一"它们。
- **版本**：versionName 遵循[语义化版本 2.0.0](https://semver.org/lang/zh-CN/)（0.y.z 为初始开发期）；`versionCode` 是与 SemVer 解耦的独立自增整数。当前值以 `app/build.gradle.kts` 为准。
- **本地构建环境**：JDK 17 在 `tools/jdk-17*`，Android SDK 在 `sdk/`，均已被 gitignore，无需全局环境。

## 构建与验证

- 构建：`JAVA_HOME=$(ls -d tools/jdk-17* | head -1) ./gradlew assembleAppRelease --console=plain`；穿透版为 `assembleFrpcRelease`。
- 产物：`app/build/outputs/apk/app/release/FQWeb_v<版本>.apk`。
- Gradle wrapper 8.9 + AGP 8.0.2 + Kotlin 1.8.20 组合已验证可构建，不要单独升级其中一环。
- 改动后最低验证是编译通过 + 逻辑走查；API 行为变更需在部署设备实测（见「部署与联调」）。

## 代码结构与边界

| 路径 | 职责 | 修改注意 |
| --- | --- | --- |
| `MainHook.kt` | 注入入口：hook 宿主 Application、注入设置页 UI | 对话框回调跑在宿主主线程，未捕获异常会崩宿主 |
| `web/HttpServer.kt` | NanoHTTPD 路由与统一错误 JSON | 兜底必须是 `catch (Throwable)`，原因见「硬约束」 |
| `web/controller/`、`web/service/` | 参数校验 + 反射调用宿主 RPC | 全部基于字符串反射，依赖宿主混淆后类名 |
| `constant/Config.kt` | 宿主版本适配（按宿主 versionCode 分支）+ 常量 | 适配新宿主版本时在此扩展 |
| `web/FrpcServer.kt`、`entity/`、`traversal/config.json` | 内网穿透（远程配置驱动） | config.json 是线上拉取源，本地提交 ≠ 线上生效 |
| `utils/` | Xposed 反射辅助、HTTP、SP 等 | `SPUtils` 写的是宿主进程私有 SP，不在模块目录 |

## 硬约束与已知坑

- **宿主进程纪律**：对话框回调、裸线程里的未捕获异常都会崩宿主；后台线程也要兜底。
- **`catch (Exception)` 接不住 `XposedHelpers.ClassNotFoundError`**（它继承 `java.lang.Error`）；服务器与裸线程兜底一律用 `Throwable`。宿主版本不匹配时 `findClass` 必抛它，是最常见的失败路径。
- **`decodeContent` 是原地解码**：宿主 `bookend.a.a` 会把解密正文写回传入的同一个 ItemContent（已在 5.2.3.32 上字节码实证），`blockingFirst()` 吐出的是裸 String。不要"用返回值替换 data 字段"。
- **ProGuard**：`ReturnData`、`entity/` 数据类、`MainHook` 有 keep 规则；Gson 反序列化用到的实体类必须同步 keep，否则字段被混淆后反序列化全为 null。`frpclib.aar` 自带 consumer 规则，勿删。
- **CORS 已移除**：浏览器跨域调用本服务需在反向代理层（nginx）加响应头；App / curl 不受影响。
- **内网穿透**：`TRAVERSAL_CONFIG_URL` 指向本 fork 的 GitHub raw；线上 `traversal/config.json` 目前仍是上游遗留的启用状态（本地已提交 enable:false 但未推送）。线上配置改为禁用前，不要开启穿透，也不要把用户导流到第三方 frp 服务器。
- **Git Bash 环境坑**：adb 设备路径会被 MSYS 转换成本地路径，需加 `MSYS_NO_PATHCONV=1`；中文参数经 curl 会以 GBK 编码发出，测 API 用预先 percent-encode 的 ASCII URL。
- **MIUI 安装限制**：部署设备首次 `adb install` 会报 `INSTALL_FAILED_USER_RESTRICTED`，需在设备弹窗手动同意；重试即可，不要用 `-i com.android.vending` 之类的伪装安装器。
- 不要把 frp token、服务器地址等私密配置写进仓库（上游曾因此泄露）。

## 版本、提交与 Git 边界

- versionName 遵循 SemVer 2.0.0：破坏性变更升主版本、向下兼容新功能升次版本、向下兼容修复升修订号。
- 每个逻辑修改单独一个 commit，信息用中文 Conventional Commits（`fix: 修正 xxx`、`refactor: ...`、`chore: ...`、`docs: ...`）；无关改动不混入同一提交。
- 有方案分叉（配置指向、兼容策略、安全权衡）先提问确认再动手。
- **不主动 push**：所有提交只留在本地，除非明确要求推送。
- 不擅自改 remote、重置历史或清理未跟踪文件；`.workbuddy/`、`sdk/`、`tools/`、`keystore/` 为本地目录，保持 gitignore 覆盖。

## 部署与联调

- 部署设备：Redmi Note 7 Pro（MIUI 12.5，Android 10），adb 序列 `a8c6ca71`（USB 优先，LAN 为 192.168.100.101）；宿主番茄小说 5.2.3.32（versionCode 523，Config.kt 有对应分支）。
- 安装：`adb -s a8c6ca71 install -r <apk>`；applicationId 是 `me.fycz.FQWeb`，与旧包 `me.fycz.fqweb` 不能互相覆盖安装。
- 模块需在 LSPosed 中勾选启用并重启宿主后生效；同一宿主上不要同时启用新旧两个包。
- Web 服务默认端口 9999（存于宿主 SP 的 `port`），随番茄启动（设置内开关）；对外暴露由用户的反向代理层负责，模块自身不带鉴权，仅限可信网络使用。

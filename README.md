# 方向管理 · OrientationManager

一个**免 root 的 Android 屏幕方向管理器**：能**真正强制旋转**（覆盖应用自身的方向请求），并支持**按应用规则**。

> 本项目是对 Rotation（com.pranavpandey.rotation v29.4.0）的**行为级复刻与完全重写**：只参考其行为与机制，不含其任何代码、资源、字符串或图标。许可证 GPL-3.0。

---

## 为什么它能"真正强制旋转"

Android 的旋转不是"一个设置说了算"，而是 WindowManagerService 按**优先级链**逐级判定：

~~~
① 顶部 Activity 的 requestedOrientation（manifest screenOrientation / setRequestedOrientation）
     若是固定值（portrait / landscape / reverse…）→ 直接钉死，下面全部忽略
② 若①是 unspecified/sensor… → 才轮到"用户旋转偏好"：
     Settings.System.ACCELEROMETER_ROTATION
     Settings.System.USER_ROTATION
③ display 级窗口的 LayoutParams.screenOrientation（本项目的关键）
④ Android 12+ 的 set-ignore-orientation-request（需 shell/root）
~~~

普通"方向管理"应用只改 ② 层，所以**对硬锁方向的应用完全无效**。本项目通过 ③ 层突破。

## 三个旋转通道

| 通道 | 机制 | 能力 | 依赖 |
|---|---|---|---|
| **设置修改** | 写 accelerometer_rotation + user_rotation（后者为原版盲区字段） | 影响 ② 层，对未硬锁方向的应用有效 | 修改系统设置权限 |
| **悬浮窗** | display 级 **1×1 TYPE_ACCESSIBILITY_OVERLAY(2032)** 窗口设置 screenOrientation | **覆盖应用自身请求**，含反向竖/横屏 | 无障碍服务（无需悬浮窗权限） |
| **Shizuku / Shell** | 以 shell/root 执行 wm set-ignore-orientation-request | 让系统**忽略应用方向请求**，最强 | Shizuku 或 root（Android 12+） |

### 关键发现：必须用 2032

对比实验与逆向分析确认：**TYPE_APPLICATION_OVERLAY(2038) 的 screenOrientation 会被系统（尤其 EMUI）忽略**，只有 **TYPE_ACCESSIBILITY_OVERLAY(2032)** 这种由无障碍服务添加的特权窗口才参与旋转决策。原版 Rotation 正是使用 2032 + 1×1 + flags(含 SHOW_WHEN_LOCKED)。

因此本项目把**引擎宿主放在无障碍服务上**：它由系统托管、被杀自动拉起，覆盖层与监听都随之存活。

---

## 特性

- **真正强制旋转**：竖屏 / 横屏 / 反向竖屏 / 反向横屏 / 按传感器强制
- **关闭模式**：完全不干预，不写任何系统设置
- **按应用规则**：为每个应用单独指定方向（含"跟随全局"与"关闭"）
- **旋转方案**：设置修改 / 悬浮窗 / 无障碍 / Shizuku / Shell 五个通道可**单独开关**，不可用的自动置底禁用并写明原因
- **覆盖模式**：事件驱动重写两个旋转字段 + 60ms 跟进重写，与其他方向管理器争夺控制权
- **保活体系**：忽略电池优化、厂商自启动管理跳转、前台常驻通知随无障碍生命周期启停
- **无障碍自持**：校验并维护 enabled_accessibility_services / accessibility_enabled，被 ROM 清除时自动补回
- **写入安全设置**（可选）：一次 pm grant 后免 root 直接管理无障碍
- **完整日志**：落盘 + 可选中 + 一键复制，覆盖所有操作与异常探测

## 界面

- 主界面：当前方向状态 + 方向选择 Chip + 按应用设置 + 旋转方案入口 + 保活 + 权限 + 日志
- 旋转方案：可用方案（可开关）/ 不可用方案（置底禁用 + 原因 + 点击申请）
- 按应用设置：应用列表（异步加载 + 搜索 + 方向选择）
- 日志：等宽字体、可选中、一键复制、清空、自动滚动

## 权限矩阵

| 权限 | 用途 | 是否必需 |
|---|---|---|
| 修改系统设置 (WRITE_SETTINGS) | 设置修改通道 | 可选 |
| 无障碍服务 | 引擎宿主 + 2032 覆盖层 + 前台应用识别 | **推荐** |
| 通知权限 | 常驻通知 | 可选 |
| ~~悬浮窗 (SYSTEM_ALERT_WINDOW)~~ | **已不再使用**：所有覆盖层（强制旋转 / 检测窗 / 组件拾取）统一用无障碍 2032 | 不需要 |
| WRITE_SECURE_SETTINGS | 直接管理无障碍启用状态 | 可选（需 adb 授予） |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | 保活 | 可选 |

授予 WRITE_SECURE_SETTINGS：
~~~bash
adb shell pm grant com.orient.manager android.permission.WRITE_SECURE_SETTINGS
~~~

## 与 Rotation（原版）的对比

| 维度 | Rotation v29.4.0 | 本项 |
|---|---|---|
| 旋转字段 | 只写 accelerometer_rotation | accelerometer_rotation + **user_rotation**（双方盲区隔离） |
| 覆盖层 | 1×1 + TYPE_ACCESSIBILITY_OVERLAY(2032) | 同机制复刻 |
| 计费 | 订阅 + 买断 + 外部 Key App | **无任何付费内容** |
| 广告 | AdMob | 无（仅预留接口） |
| 按应用规则 | Room 数据库 | SharedPreferences + JSON |
| 设置导出 | 无 | 一键导出全部设置 JSON（全局开关 + 按应用配置 + 高级规则） |
| 覆盖层级 | 仅强制旋转用 2032 | 强制旋转 / 检测窗 / 组件拾取全部 **2032 + SHOW_WHEN_LOCKED**（设置、锁屏等界面均可见） |
| 日志 | 无 | 落盘 + 一键复制 |

同时运行二者会互相争夺 accelerometer_rotation —— 这是 Android 全局设置的固有限制，无法共存。

## 构建

环境：JDK 17+、Android SDK（platform 34 / build-tools 35）。仓库默认走阿里云 Maven 镜像与系统 aapt2（适配 arm64 主机）。

~~~bash
# gradle.properties 已内置单点开关
#   orient.compileSdk / orient.targetSdk / orient.minSdk
#   android.aapt2FromMavenOverride=/usr/bin/aapt2（arm64 主机需要）
./gradlew :app:assembleDebug
~~~

## 导出设置

主界面「其他 → 导出全部设置（JSON）」会生成一份包含以下内容的 JSON（用系统文件选择器保存到任意位置）：

\`\`\`
{ "app": …, "versionName": …, "exportedAt": …,
  "global":       { mode / 常驻通知 / 覆盖模式 / 检测悬浮窗 / 各通道开关 },
  "perApp":       { enabled, rules: { 包名: 模式 } },
  "advancedRules":{ enabled, rules: [ { mode, conditions: [ { field, pattern, negate } ] } ] } }
\`\`\`

## 使用

1. 安装 APK
2. 授权：修改系统设置 → 悬浮窗（可选）→ 通知
3. 系统设置里开启本应用的**无障碍服务**（引擎宿主）
4. 选择方向（或在「按应用设置」里为单个应用指定）
5. 需要压制其他方向管理器时：开启「覆盖模式」，或接入 Shizuku / root 通道

## 日志与排查

日志页记录：进程启动、权限体检、模态应用、通道执行结果、字段被外部改写与回写、覆盖层状态、引擎心跳（含系统实际旋转角）。

常见判读：

| 日志 | 含义 |
|---|---|
| 无 Engine 引擎启动 | 无障碍未连接，引擎未运行 |
| Settings 写… 返回=true 回读=X | 写入是否真的生效 |
| Overlay 初始化窗口组件 type=2032 | 是否使用了特权覆盖层 |
| Watch 引擎存活 … displayRotation=… | 引擎存活 + 系统实际旋转角 |
| Enforce … 被改为 … 已回写 | 有外部程序在抢字段 |
| 本应用界面在前台，跳过窗口检测 | 本应用自己的界面不参与检测，避免无障碍查询回环导致输入卡顿 |

## 已知限制

- 旋转字段是**全局设置**，与其他方向管理器无法共存（会互相回写）
- Android 不允许应用以编程方式开启无障碍服务，只能引导或通过 Shizuku/root 代授
- WRITE_SECURE_SETTINGS 只能 adb/Shizuku/root 授予，卸载重装失效
- 目标为 Android 8.0+（minSdk 26），set-ignore-orientation-request 需 Android 12+
- 部分 ROM 的安全中心可能拦截 pm grant / settings put

## 参考与致谢

- orientation-faker（MIT）—— 验证了 display 级窗口 screenOrientation 参与旋转决策的可行性
- AccessibilityManager —— 验证了 WRITE_SECURE_SETTINGS + Settings.Secure 直接管理无障碍的路径

## 许可证

GPL-3.0

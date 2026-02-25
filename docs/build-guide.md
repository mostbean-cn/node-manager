# Node Manager 插件 — 编译与打包指南

## 环境要求

| 依赖 | 最低版本 | 说明 |
|------|---------|------|
| **JDK** | 21 | 推荐使用 JetBrains Runtime 或 Eclipse Temurin |
| **Gradle** | 8.13 | 项目自带 Wrapper，无需手动安装 |
| **IntelliJ IDEA** | 2024.2+ | 用于开发和调试插件 |

> 确保 `JAVA_HOME` 环境变量指向 JDK 21。可通过 `java -version` 验证。

---

## 一、Gradle Wrapper 初始化

项目首次克隆后，需要生成 Gradle Wrapper 文件：

```bash
# 在项目根目录执行（需要系统已安装 Gradle 8.13+）
gradle wrapper --gradle-version=8.13
```

生成后会出现以下文件，**需要提交到版本控制**：

```
gradlew          # Linux/macOS 启动脚本
gradlew.bat      # Windows 启动脚本
gradle/
  └── wrapper/
      ├── gradle-wrapper.jar
      └── gradle-wrapper.properties
```

后续所有命令都使用 `gradlew`（Linux/macOS）或 `gradlew.bat`（Windows）执行。

---

## 二、编译

### 完整编译

```bash
# Windows
gradlew.bat build

# Linux / macOS
./gradlew build
```

编译产物输出到 `build/` 目录。

### 仅编译（跳过测试）

```bash
gradlew.bat build -x test
```

### 清理后重新编译

```bash
gradlew.bat clean build
```

---

## 三、本地运行 & 调试

### 启动沙盒 IDE

此命令会启动一个独立的 IntelliJ IDEA 实例，并自动加载插件：

```bash
gradlew.bat runIde
```

- 沙盒 IDE 使用独立的配置目录，不会影响你的正式 IDEA
- 首次运行会下载对应版本的 IntelliJ IDEA，可能需要几分钟
- 在沙盒 IDE 中可以验证 Tool Window、状态栏 Widget、菜单 Action 等全部功能

### 在 IDEA 中调试

1. 在 IDEA 中打开项目
2. 在 Gradle 工具窗口找到 `Tasks > intellij platform > runIde`
3. 右键 → **Debug**（或直接在代码中打断点后运行）

---

## 四、打包

### 构建插件发行包

```bash
gradlew.bat buildPlugin
```

构建成功后，插件 zip 包位于：

```
build/distributions/node-manager-0.1.0.zip
```

此 zip 文件可以直接安装到任意 IntelliJ IDEA 中。

### 手动安装插件

1. 打开 IntelliJ IDEA → **Settings** → **Plugins**
2. 点击齿轮图标 ⚙ → **Install Plugin from Disk...**
3. 选择 `build/distributions/node-manager-0.1.0.zip`
4. 重启 IDE 生效

---

## 五、插件验证

在发布前，使用 JetBrains 官方验证工具检查兼容性：

```bash
gradlew.bat verifyPlugin
```

此命令会检查：

- `plugin.xml` 配置是否合规
- 是否使用了已废弃或内部 API
- 与目标 IDE 版本的兼容性

---

## 六、发布到 JetBrains Marketplace

### 前置准备

1. 在 [JetBrains Marketplace](https://plugins.jetbrains.com/) 注册开发者账号
2. 获取 API Token：Account → My Tokens → Generate Token

### 配置 Token

设置环境变量（**不要**把 Token 写进代码或配置文件）：

```bash
# Windows PowerShell
$env:PUBLISH_TOKEN = "你的Token"

# Linux / macOS
export PUBLISH_TOKEN="你的Token"
```

### 执行发布

```bash
gradlew.bat publishPlugin
```

---

## 七、版本号管理

版本号在 `gradle.properties` 中维护：

```properties
pluginVersion = 0.1.0
```

遵循 [SemVer](https://semver.org/) 语义化版本规范：

| 变更类型 | 版本位 | 示例 |
|---------|--------|------|
| 不兼容的 API 变更 | 主版本号 | `1.0.0` → `2.0.0` |
| 新增功能（向后兼容） | 次版本号 | `0.1.0` → `0.2.0` |
| Bug 修复 | 修订号 | `0.1.0` → `0.1.1` |
| 预发布 | 标签 | `0.2.0-beta.1` |

---

## 八、常用命令速查

| 命令 | 用途 |
|------|------|
| `gradlew.bat build` | 完整编译 + 测试 |
| `gradlew.bat build -x test` | 编译（跳过测试） |
| `gradlew.bat clean` | 清理构建产物 |
| `gradlew.bat runIde` | 启动沙盒 IDE 调试 |
| `gradlew.bat buildPlugin` | 打包插件 zip |
| `gradlew.bat verifyPlugin` | 验证插件兼容性 |
| `gradlew.bat publishPlugin` | 发布到 Marketplace |
| `gradlew.bat dependencies` | 查看依赖树 |

---

## 九、常见问题

### Q: 编译报错 `Could not resolve IntelliJ Platform`

确保网络通畅，IntelliJ Platform SDK 需要从 JetBrains Maven 仓库下载。如在国内网络受限，可配置代理：

```properties
# gradle.properties
systemProp.http.proxyHost=127.0.0.1
systemProp.http.proxyPort=7890
systemProp.https.proxyHost=127.0.0.1
systemProp.https.proxyPort=7890
```

### Q: `runIde` 启动后看不到插件

检查 **View → Tool Windows** 中是否有 **Node Manager** 选项。如果没有，查看 IDE 日志（Help → Show Log）确认插件是否加载成功。

### Q: Gradle Wrapper 版本不对

```bash
gradlew.bat wrapper --gradle-version=8.13
```

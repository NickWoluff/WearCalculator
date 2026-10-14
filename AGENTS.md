# Repository Guidelines

## 项目结构与模块组织

这是一个单模块 Android Wear 应用，模块位于 `app/`。Java 业务代码在 `app/src/main/java/com/nickwoluff/wearcalculator/`，布局、主题、颜色和图标等资源在 `app/src/main/res/`；`layout/` 面向方屏，`layout-round/` 面向圆屏。单元测试放在 `app/src/test/`，设备或模拟器测试放在 `app/src/androidTest/`。顶层 Gradle 配置、版本目录和 Wrapper 分别位于 `build.gradle.kts`、`gradle/libs.versions.toml` 和 `gradlew`。

## 构建、测试与开发命令

在仓库根目录执行：

```bash
./gradlew assembleDebug          # 构建可调试 APK
./gradlew assembleRelease        # 构建压缩后的发布 APK
./gradlew test                   # 运行 JVM 单元测试
./gradlew connectedAndroidTest   # 在已连接设备/模拟器上运行仪器测试
./gradlew lint                   # 执行 Android 静态检查
```

Windows 使用 `gradlew.bat` 替代 `./gradlew`。安装调试包可使用 `adb install app/build/outputs/apk/debug/app-debug.apk`。

## 编码风格与命名约定

Java 使用 4 空格缩进，遵循 Android/Java 常规风格：类名使用 `PascalCase`，方法和变量使用 `camelCase`，常量使用 `UPPER_SNAKE_CASE`。资源文件名使用小写下划线，例如 `activity_main.xml`、`btn_ac`；资源 ID 使用同样规则。依赖版本集中维护在 `gradle/libs.versions.toml`，不要在模块脚本中重复声明版本。提交前保持导入整洁，并运行 `./gradlew lint`。

## 测试指南

JVM 测试使用 JUnit 4，仪器测试使用 AndroidX JUnit 与 Espresso。测试类以 `Test` 结尾，测试方法应描述行为，例如 `divisionByZeroShowsError`。涉及计算精度、输入状态机或屏幕布局的改动，应补充对应测试；至少运行 `./gradlew test`，涉及界面或设备行为时再运行 `./gradlew connectedAndroidTest`。

## 提交与 Pull Request

现有提交多为简短中文动宾短语，例如 `更新至v1.1.1`、`修改README`。新提交保持单一目的、使用祈使式中文描述，发布版本更新时同步说明 `versionCode`/`versionName`。Pull Request 应说明变更目的、测试命令和设备/模拟器结果；涉及界面时附方屏与圆屏截图，涉及 Issue 时链接对应编号，并避免提交构建产物、密钥或本地配置。

## 安全与配置

不要提交 `local.properties`、签名文件、密钥或真实服务凭据。修改 `compileSdk`、`minSdk`、依赖版本或发布配置前，确认与 Wear OS 目标设备兼容，并在 PR 中注明影响范围。

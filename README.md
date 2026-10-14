# 🐾 嗷呜计算器 (WearCalculator)

![Last Version](https://img.shields.io/github/release/NickWoluff/WearCalculator/all.svg?style=flat-square&color=brightgreen)

**专为 Wear OS 智能手表打造的计算器，嗷呜！**

当前项目版本：**1.2.0**。

## ✨ 核心特性 (Features)

- 方屏与圆屏适配：首次启动自动识别屏幕形状，也可在设置中手动切换布局。
- 基础计算：支持四则运算、小数和百分比；基础十进制运算使用 BigDecimal，除法保留 16 位小数。
- 科学计算：左右滑动按钮区域切换第二页，支持三角函数、反三角函数、次方、`lg`、`ln`、`log`、括号、平方根、阶乘、`π` 和 `e`；点击 `2nd` 切换三角函数与反三角函数。
- 长算式与结果查看：算式、结果可分别左右滑动，首页也支持旋转表冠查看。
- 历史记录：保存最近 10 次成功运算，重启后仍可查看；最新记录位于底部，每条算式与结果都可单独左右滑动。
- 主题设置：默认使用经典配色，可选择其他强调色及 Tonal Spot、Vibrant、Expressive、Neutral、Monochrome、Fidelity 调色板风格；经典模式的页码指示器为灰色。
- 检查更新：默认开启启动时自动检查，也可在设置中关闭或手动检查 GitHub Release 更新。
- 手表界面：设置和主题页面使用 Wear Material 3 组件，配有悬浮时间、页码指示器和适配方圆屏的悬浮提示。

## 👆 操作说明

- 在按钮区域左右滑动切换基础与科学计算页，顶部算式和结果区域保持固定。
- 从屏幕底部上滑打开设置；在设置列表顶部下滑返回计算器。
- 从算式或结果区域下滑打开历史记录；在历史列表底部继续上滑返回计算器。
- 方屏的百分号位于第一页，圆屏的百分号位于第二页。

三角函数使用**弧度**，反三角函数返回弧度。`lg` 和 `log` 均为以 10 为底的对数，`ln` 为自然对数。科学函数和次方使用浮点数学函数，精度与基础十进制运算不同。

## 🔄 更新说明

首次启动时，“自动检查更新”默认开启；手动更改开关后会保存选择。每次打开首页检查一次，切换主题或布局不会重复发起检查。

自动检查只在发现新版本时提示，可前往 [GitHub Releases](https://github.com/NickWoluff/WearCalculator/releases) 页面更新。已是最新版或检查失败时，自动检查不会弹出提示；在设置中手动检查会显示对应的悬浮提示。应用内提供 Release 页面入口，安装新版需自行完成。

## 📥 下载与安装 (Installation)

**应用商店安装**：前往 **微思应用商店** 手表端，搜索“嗷呜计算器”下载安装。

**ADB 手动安装**：

1. 前往 [Releases](https://github.com/NickWoluff/WearCalculator/releases) 页面下载 APK。
2. 在手表上开启**开发者模式**和 **ADB 调试 / 无线调试**，完成连接。
3. 使用甲壳虫 ADB 助手、WearOS 工具箱，或电脑端 ADB 安装：

   ```bash
   adb install -r WearCalculator_vX.X.X.apk
   ```

## 🛠️ 构建与测试

使用 Android Studio 打开项目，配置 Android SDK 后，在项目根目录执行：

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat test
.\gradlew.bat lint
```

调试包位于 `app/build/outputs/apk/debug/app-debug.apk`。连接手表或已有模拟器后，可运行 `.\gradlew.bat connectedAndroidTest` 验证界面与手势。发布构建使用 `.\gradlew.bat assembleRelease`，安装包按版本号命名为 `app/build/outputs/apk/release/WearCalculator_vX.X.X.apk`（当前为 `WearCalculator_v1.2.0.apk`），分发前需自行签名。

## 🆕 1.2.0 主要变化

- 新增科学计算按钮页、翻页动画与页码指示器。
- 新增最近 10 次运算历史和上下滑动页面导航。
- 新增设置、屏幕布局切换、强调色与调色板选择。
- 新增手动及默认开启的自动检查更新、悬浮时间与手表样式提示。
- 设置页底部显示版权，2027 年起自动显示从 2026 年到当年的年份范围。

## 💬 交流与反馈 (Community)

使用时遇到问题或有功能建议，欢迎提交至 [Issues](https://github.com/NickWoluff/WearCalculator/issues)。或：

>加入 **嗷呜系列官方QQ交流群**：`暂无`

同系列应用：[🐾 嗷呜文件](https://github.com/NickWoluff/WearFiles)

© 2026 Nick Woluff. All Rights Reserved.

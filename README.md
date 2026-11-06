# HappyMatch（开心连连看）

一个用 Android 原生 Java 实现的连连看小游戏，无 AndroidX / support 库依赖，全部界面由系统控件与自定义 `View` 绘制完成。

- 目标平台：Android 11（API 30），最低兼容 API 21
- 包名：`com.happymatch`
- 玩法：在 8×8 的棋盘上点击两个相同的水果，若能用不超过两个拐角的路径连接（可穿过空格，也可绕棋盘外圈）即可消除

## 功能

- 8×8 棋盘，16 种水果图标，开局保证至少存在一组可消除的组合
- 多关卡：第一关 100 秒，每关时限递减 10 秒，最低 40 秒
- 道具：洗牌、提示各 3 次，每关重置
- 死局检测：无可用组合时自动洗牌并提示
- 音效与背景音乐：`SoundPool` 播放短音效，`MediaPlayer` 循环播放 BGM
- 暂停 / 恢复：切到后台时暂停计时与音频
- 结算弹窗：胜利可进入下一关，失败可重玩
- 启动页：2.5 秒自动进入，可点击跳过

## 界面与代码结构

```
app/src/main/java/com/happymatch/
├── WelcomeActivity.java          启动页
├── MainActivity.java             主界面：顶栏、道具、结算与生命周期
├── game/
│   ├── BoardView.java            棋盘数据、绘制、选块、连通判定
│   ├── GameView.java             关卡、倒计时、道具、音效与状态回调
│   ├── OnStateChangeListener.java
│   ├── OnTimeChangeListener.java
│   └── OnToolsChangeListener.java
├── ui/
│   ├── MatchDialog.java          结算对话框
│   └── MatchToast.java           自定义 Toast
└── audio/SoundPlayer.java        SoundPool 封装
```

`BoardView` 只负责棋盘与算法，`GameView` 在其基础上叠加游戏流程，两者职责分离。

### 连通判定

`BoardView#canLink` 依次判断三种情况：

1. 同行或同列的直线连通
2. 单拐角：`(a.x, b.y)` 或 `(b.x, a.y)`
3. 双拐角：在棋盘外扩一圈的范围内枚举折点

棋盘外的一圈视为可通行的空格，因此贴边的两块也能绕出去连接。

## 构建

环境要求：

- JDK 17（AGP 8.7.3 所需，Gradle 编译选项为 Java 11）
- Android SDK Platform 30
- Gradle 使用项目自带的 wrapper（8.9），无需单独安装

命令行构建：

```bash
./gradlew assembleDebug
```

产物位于 `app/build/outputs/apk/debug/app-debug.apk`。

也可以用 Android Studio 直接打开项目，Sync 后运行。

## 自动构建 Release APK

仓库内置 GitHub Actions 工作流 [.github/workflows/release.yml](.github/workflows/release.yml)：

- 在 GitHub 上发布 Release 时自动构建 APK，并作为附件挂到该 Release
- 也可在 Actions 页面手动触发（`workflow_dispatch`），此时只产出构建产物
- APK 命名为 `HappyMatch-<tag>.apk`

> 该 APK 使用 debug 签名，可直接安装测试，但不适合正式上架分发。

## 说明

- 本项目仅使用 Android 系统框架控件，`app/build.gradle` 中没有任何第三方依赖
- 图片、音效素材来自本地整理的游戏资源
- 已知小问题：退出确认框显示期间倒计时仍在继续

## 许可证

[MIT](LICENSE)

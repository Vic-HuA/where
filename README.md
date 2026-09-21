# 在哪儿 / Where

本地优先的家庭物品定位 App。用来记录「东西放在哪」，再用文字、语音或常用入口把它找回来。

家庭数据默认只存在本机，不依赖云端数据库、账号或专用硬件。AI、语音和网络都是可选辅助；这些能力不可用时，键盘录入和本地搜索仍然完整可用。

当前首发平台是 Android。工程使用 Kotlin Multiplatform 与 Compose Multiplatform，共享模块保留后续 iOS 目标。

## 当前能力

- 初始化家庭，维护房间、区域、容器等位置树
- 拍照、语音或手动录入物品，保存名称、别名、分类、数量和现场照片
- 文字或语音查找，结果来自本地真实记录，并展示照片、位置路径和更新时间
- 移动物品时更新当前位置，同时保留历史位置
- 常用位置、常用物品、置顶物品，以及适老模式、家人帮助
- 位置照片、语音名称、加密本地备份与恢复
- 可选接入云端 AI 辅助识别；密钥只保存在本机，不会写入仓库

首版明确不做：账号、云端数据库、多设备同步、微信分享、BLE / NFC、回收站页面。

## 技术栈

| 项 | 选择 |
| --- | --- |
| 语言 | Kotlin 2.4.10 |
| UI | Compose Multiplatform 1.11.1 |
| 应用 ID | `com.vichua.where` |
| Android | minSdk 26，compileSdk / targetSdk 36 |
| 本地库 | Room KMP + Bundled SQLite |
| 构建 | Gradle 9.3.1，AGP 9.1.1，JDK 17 |

## 工程结构

```text
Where
├─ androidApp          # Android 入口、权限与系统能力
├─ shared
│  ├─ core             # 模型、数据库、搜索、平台接口
│  ├─ feature          # 物品、位置、搜索、备份、设置
│  └─ ui               # 共享 Compose 界面
└─ platform/android    # Android 仓储与系统实现
```

## 本地构建

需要 JDK 17 和已安装 Android SDK 的环境。不要把 `local.properties`、签名文件或 AI 密钥提交进仓库。

```bat
.\gradlew.bat :androidApp:assembleDebug
```

调试包输出：

```text
androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

## 隐私与密钥

- 物品、照片、语音和备份默认只在本机
- AI 服务密钥保存在 Android SharedPreferences，不进 Git
- 备份密码由用户当场输入，仓库和源码中没有默认口令
- 相机、麦克风等权限在使用对应功能时申请

## 许可证

Copyright 2026 Vic-HuA

本项目使用 [Apache License 2.0](LICENSE)。你可以自由使用、修改和分发；再分发时需保留许可证、版权与归属声明，并在改过的文件上标明变更。

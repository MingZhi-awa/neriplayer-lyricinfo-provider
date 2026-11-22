# NeriPlayer 的 ColorOS 锁屏歌词 Provider

给 [NeriPlayer（音理音理）](https://github.com/cwuom/NeriPlayer) 准备的 ColorOS/OPlus 锁屏歌词 Provider，
按 [`ColorOS-Live-Lyrics-Providers`](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Providers) 4.x 的
`lyricInfo` 契约把歌词写进播放器自己的 `MediaMetadata`。渲染由 ColorOS SystemUI 原生处理；装了
[Bridge](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Bridge) 时额外获得逐字、翻译与样式增强。

本仓库是上游套件的单模块裁剪版，只保留构建这个 Provider 需要的模块，方便用 GitHub Actions 直接出 APK。

## 为什么还要 Provider

NeriPlayer 自带 `ColorOS 锁屏岛歌词`（实验特性），但它有两个代价：

1. 需要 Root 或 Shizuku 去改 SystemUI 的 `app_systemui_oplus_media_controller_config.xml` 白名单；
2. 只走 ColorOS 官方渲染，没有 Bridge 的逐字扫光、对唱分侧与样式选项。

把这个开关关掉后，音理进程里不存在任何 `lyricInfo`，所以本模块改为自己解析歌词、自己写
payload，渲染完全交给 SystemUI / Bridge。

## 实现

| 环节 | 做法 |
|---|---|
| 进程 | 只接主进程 `moe.ouom.neriplayer`（`AudioPlayerService` 无 `android:process`），`:netease_login` 等子进程直接 detach |
| Hook 面 | 只用 framework 入口 `MediaSession#setMetadata` / `#setPlaybackState` / `#setActive` / `#release`。音理 release 被 R8 混淆（除 manifest 引用外内部类名全丢），因此不依赖任何宿主内部类名 |
| 歌词源 | AMLL DB（`amlldb.bikonoo.com`，与音理同源）优先：逐字、对唱、翻译；无文档时回退 LRCLIB 逐行 |
| 翻译 | 单独从 TTML 的 `ttm:role="x-translation"` span 提取，按 `<p>` 起始时间与主行一一配对；音译与和声绝不进翻译 lane |
| 写入 | append-only：只在宿主 `MediaMetadata` 对象上写 `lyricInfo` 一个键，不重建 metadata、不碰封面 / URI / rating / 未知键 |
| 兜底 | 宿主已有自己发布的合法 payload 时只追加 `ttml`；payload 超 120K 字符先丢 TTML 再丢歌词；超 Parcel 预算则 fail-open |
| 录制 | 每次换曲递增 generation，异步结果回来时再核对身份，旧结果不覆盖新歌 |

## 构建

推送到 `main` 会自动跑 `Build NeriPlayer LyricInfo Provider`，产物在 Actions 的
`neriplayer-lyricinfo-provider-debug` artifact 里。

本地：

```bash
./gradlew :player-neriplayer:testDebugUnitTest :player-neriplayer:assembleDebug
```

## 安装

1. LSPosed（或 libxposed API 102 兼容框架）里启用本模块，作用域勾选 `音理音理`；
2. **关闭**音理设置里的 `ColorOS 锁屏岛歌词`，避免两份 payload 互相覆盖；
3. 播放一首有 AMLL 歌词的歌，锁屏可见歌词；
4. 需要逐字/翻译特效时装 Bridge，只装本模块时是 ColorOS 原生逐行渲染。

## 调试

Provider 设置在系统「应用信息」里可以打开（桌面不显示图标）。打开 Debug 后按需重启音理进程，
结构化日志前缀为 `[CLL] component=provider/neriplayer`，关键事件：
`LYRIC_FETCH_STARTED`、`LYRIC_FETCHED`、`LYRIC_INFO_WRITTEN`、`LYRIC_INFO_UNCHANGED`、
`PAYLOAD_NOT_COMPOSED`、`LYRIC_FETCH_STALE`。日志不含歌词正文、稳定 media ID 与 URL query。

## 已知限制

- 取词需要网络；离线本地歌词暂不走本模块（属于后续工作）；
- AMLL 无该曲文档时只有逐行 lane，没有逐字扫光；
- 歌词偏移以 AMLL 文档为准（音理对 AMLL 源的默认偏移为 0），其他来源的历史偏移不会套用；
- 只在主进程工作，不处理车机 `CarMediaBrowserService` 的独立会话。

## 署名

代码与调试：猫羽bot。上游 Provider 套件协议与基础设施来自 Andrea-lyz / Proify / Tomakino，
License 与 NOTICE 沿用 Apache-2.0。

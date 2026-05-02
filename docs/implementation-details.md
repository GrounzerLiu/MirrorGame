# MirrorGame 实现细节

## 概述

通过 ADB + scrcpy-server + FFmpeg 实现 Android 设备屏幕镜像。整个投屏管道：

```
App → adb shell → app_process → scrcpy-server (Android)
                                      ↓
                                LocalServerSocket "scrcpy_XXXXXXXX"
                                      ↓
                              adb forward tcp:PORT localabstract:scrcpy_XXXXXXXX
                                      ↓
                                TCP Socket (Desktop)
                                      ↓
                                FFmpeg 子进程解码
                                      ↓
                                BGRA 帧 → Compose UI
```

---

## 关键细节

### 1. scrcpy-server 参数

传递给 `app_process` 的参数必须精确，否则服务端行为异常：

```
CLASSPATH=/data/local/tmp/scrcpy-server.jar
app_process /
com.genymobile.scrcpy.Server
3.3.4
scid=XXXXXXXX        # 8 位十六进制，31-bit 正整数
tunnel_forward=true  # 使用 ADB forward 隧道
audio=false          # 关键！否则服务端阻塞等待 audio socket
control=false        # 关键！否则服务端阻塞等待 control socket
log_level=info
max_size=1920
```

**必须加的参数：**
- `audio=false control=false` — 服务端在 `DesktopConnection.open()` 中会对 video/audio/control 各调一次 `accept()`。如果只连接了 video socket，audio accept 会永远阻塞，导致后面的 `sendDeviceMeta()` 和视频编码永远不会执行

- `scid=XXXXXXXX` — 必须用 8 位十六进制格式。`scid=-1`（默认）时 socket 名为 `"scrcpy"`，`scid=XXXXXXXX` 时 socket 名为 `"scrcpy_XXXXXXXX"`。scid 必须是 **31-bit 正整数的十六进制**（0x00000000 ~ 0x7FFFFFFF），否则 `Integer.parseInt(value, 16)` 会抛出异常

**不能加的参数：**
- `scrcpy_server_port=PORT` — scrcpy v3.3.4 不认识此参数，会输出 `Unknown server option` 警告
- `send_device_meta=true` — 默认就是 true，不需要显式设置

### 2. ADB 隧道

不能用 `adb forward tcp:PORT tcp:PORT`。必须用 localabstract 指向 LocalSocket：

```
adb forward tcp:27183 localabstract:scrcpy_XXXXXXXX
```

scrcpy-server 在 Android 上创建的是 **LocalServerSocket**（Unix domain socket），不是 TCP ServerSocket。ADB forward 的 `localabstract:` 桥接了 TCP ↔ LocalSocket。

### 3. 连接后数据格式

连接建立后，服务端按顺序发送以下数据：

#### 3.1 哑元字节 (1 byte)
```
0x00
```
`DesktopConnection.open()` 中 `sendDummyByte=true` 时发送。客户端可读此字节确认连接有效。

#### 3.2 设备名称 (64 bytes)
```
[64 bytes: 设备名 UTF-8, 空字节填充]
```
由 `sendDeviceMeta()` 发送。内容是 `Build.MODEL`。默认开启。

#### 3.3 编解码元数据 (12 bytes, big-endian)
```
[4 bytes: codec_id][4 bytes: width][4 bytes: height]
```
由 `Streamer.writeVideoHeader()` 在首个视频帧编码前发送。

codec_id 值（ASCII）：
- `0x68323634` = "h264"
- `0x68323635` = "h265"

#### 3.4 视频帧 (循环)

```
[12 bytes: frame meta][N bytes: H264 data]
```

帧元数据格式（big-endian）：
```
[8 bytes: PTS + flags][4 bytes: packet_size]
```

PTS + flags 的高 2 位：
- bit 63: config packet（编解码配置数据，含 SPS/PPS）
- bit 62: key frame

`packet_size` 后紧跟 MediaCodec 输出的原始 H264 数据。

### 4. FFmpeg 解码

MediaCodec 输出的 H264 是 **AVCC 格式**（4 字节长度前缀分隔 NAL 单元）。直接传递给 FFmpeg 即可：

```bash
ffmpeg -flags low_delay -f h264 -i pipe:0 \
       -f rawvideo -pix_fmt bgra \
       -s ${WIDTH}x${HEIGHT} \
       -an -sn pipe:1
```

关键参数：
- `-flags low_delay` — 减少解码缓冲延迟，否则实时流会延迟数秒
- `-f h264` — 告诉 FFmpeg 输入是原始 H264 码流
- 不需要手动转换 AVCC → Annex B，FFmpeg 的 H264 解析器能处理两种格式
- `-sn` — 禁用字幕解码

### 5. 进程清理

停止投屏时**必须先杀子进程再等协程**，否则协程卡在 I/O read() 上：

```kotlin
suspend fun stop() {
    job?.cancel()
    runCatching { socket?.close() }
    runCatching { ffmpegProc?.destroyForcibly() }
    runCatching { serverProc?.destroyForcibly() }
    job?.join() // 此时应该能快速结束
    cleanup()
}
```

`DisposableEffect` 中的 `dispose()` 必须用 `runBlocking` 同步执行清理，否则 `scope.cancel()` 会取消清理协程。

### 6. 设备列表稳定性

`adb devices -l` 每 2 秒轮询一次。USB 设备可能短暂重枚举（transport_id 变化），此时 `adb devices` 返回空列表。处理方式：

```kotlin
if (onlineDevices.isEmpty() && state.devices.isNotEmpty()) {
    return@collect  // 跳过空轮询，不清除已有设备
}
```

同时需在初始化时调用一次 `adb start-server` 确保 ADB 服务已运行。

---

## 常见问题

| 现象 | 原因 |
|------|------|
| 设备列表一直为空 | ADB server 未启动，添加 `ensureServer()` |
| 设备列表闪烁消失又出现 | USB 短暂重枚举，跳过空轮询 |
| Socket 连接后收不到数据 | 缺 `audio=false control=false` |
| FFmpeg 报 `no frame!` | H264 数据格式不对（尝试直接写入原始数据） |
| 点击关闭窗口无法退出 | 清理顺序反了，先杀进程再等协程 |
| 窗口卡死无响应 | FFmpeg stderr 使用了 INHERIT 可能导致线程阻塞 |
| `Address already in use` | 前次运行的 LocalSocket 未释放，kill 进程或换 scid |

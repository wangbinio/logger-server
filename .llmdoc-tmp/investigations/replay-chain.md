# replay-server 调查

## 入口与职责

`replay-server` 是回放侧 Spring Boot 服务，固定订阅 `broadcast-global` 处理回放创建和停止。实例级回放控制已经从 `broadcast-{instanceId}` 改为 HTTP REST 接口。

## 主链路

- `ReplayGlobalBroadcastListener` 接收全局回放任务消息。
- `ReplayLifecycleService.createReplay` 读取控制时间点、发现态势子表、按配置分类事件表和周期表，创建 `ReplaySession` 并标记 `READY`。
- `ReplayControlController` 提供 HTTP 控制接口，不直接操作状态机。
- `ReplayHttpCommandFactory` 将 HTTP 请求转换为内部 `ProtocolData`。
- `ReplayControlService` 复用内部控制语义处理 start、pause、resume、rate、jump。
- `ReplayScheduler` 负责连续窗口调度，按 `(lastDispatchedSimTime, currentReplayTime]` 查询并发布。
- `ReplayJumpService` 在会话锁内处理跳转补偿，保证与连续调度互斥。

## 回放控制接口

- `GET /api/replay/instances/{instanceId}`：查询会话元信息。
- `POST /api/replay/instances/{instanceId}/start`：启动回放。
- `POST /api/replay/instances/{instanceId}/pause`：暂停回放。
- `POST /api/replay/instances/{instanceId}/resume`：继续回放。
- `POST /api/replay/instances/{instanceId}/rate`：调整倍率。
- `POST /api/replay/instances/{instanceId}/jump`：跳转时间。

## 回放数据读取

- `ReplayTimeControlRepository` 读取 `time_control_{instanceId}`，缺少控制表时可降级到态势表 `MIN(simtime)` 和 `MAX(simtime)`。
- `ReplayTableDiscoveryRepository` 查询 `information_schema.ins_tags`，按 tag 聚合子表元数据。
- `ReplayFrameRepository` 直接使用 `RowMapper` 和 `ResultSet.getBytes("rawdata")` 读取帧，必要时回退 `getObject("rawdata")` 兼容低版本 RESTful 驱动。


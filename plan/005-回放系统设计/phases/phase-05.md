# Phase 05 控制命令与时间跳转

## 1. 阶段目标

实现回放控制主流程：

- 创建和停止回放任务。
- 开始、暂停、继续、倍速回放。
- 向前跳转和向后跳转。
- 周期类表目标时间前最后一帧发布。

本阶段完成后，`replay-server` 应具备完整回放业务能力。

## 2. 实现思路

`ReplayLifecycleService` 负责创建和停止任务，串联时间范围解析、表发现、表分类、会话创建、动态订阅和元信息发布。`ReplayControlService` 负责控制命令和状态迁移。`ReplayJumpService` 专门负责跳转，跳转期间必须和连续调度互斥。

跳转时不发布状态重置协议，只发布数据到 `situation-{instanceId}`。向后跳转补发 `[simulationStartTime, targetTime]` 的事件数据，向前跳转补发 `(currentTime, targetTime]` 的事件数据，周期类表统一发布 `targetTime` 前最后一帧。

## 3. 需要新增的文件

| 文件 | 说明 |
| ---- | ---- |
| `replay-server/src/main/java/.../model/dto/ReplayCreatePayload.java` | 创建和停止任务 payload。 |
| `replay-server/src/main/java/.../model/dto/ReplayMetadataPayload.java` | 回放元信息 payload。 |
| `replay-server/src/main/java/.../model/dto/ReplayRatePayload.java` | 倍速 payload。 |
| `replay-server/src/main/java/.../model/dto/ReplayJumpPayload.java` | 跳转 payload。 |
| `replay-server/src/main/java/.../service/ReplayLifecycleService.java` | 回放生命周期服务。 |
| `replay-server/src/main/java/.../service/ReplayControlService.java` | 回放控制服务。 |
| `replay-server/src/main/java/.../service/ReplayJumpService.java` | 时间跳转服务。 |
| `replay-server/src/main/java/.../service/ReplayMetadataService.java` | 回放元信息构造和发布服务。 |
| `replay-server/src/test/java/.../model/dto/ReplayPayloadTest.java` | payload 解析测试。 |
| `replay-server/src/test/java/.../service/ReplayLifecycleServiceTest.java` | 生命周期服务测试。 |
| `replay-server/src/test/java/.../service/ReplayControlServiceTest.java` | 控制服务测试。 |
| `replay-server/src/test/java/.../service/ReplayJumpServiceTest.java` | 跳转服务测试。 |
| `replay-server/src/test/java/.../service/ReplayMetadataServiceTest.java` | 元信息服务测试。 |

## 4. 待办条目

| 编号 | 待办 | 简要说明 | 前置依赖 |
| ---- | ---- | ---- | ---- |
| 05-01 | 新增 payload 测试 | 覆盖创建、元信息、倍速、跳转 payload 的 JSON 解析和非法参数。 | Phase 01 |
| 05-02 | 实现 payload DTO | 使用 `common` 中 JSON 工具解析 rawData，校验 `instanceId`、`rate` 和 `time`。 | 05-01 |
| 05-03 | 新增元信息服务测试 | 验证创建任务成功后向 `broadcast-{instanceId}` 发布 `messageType=1200,messageCode=9`。 | 04-03 |
| 05-04 | 实现 `ReplayMetadataService` | 构造开始时间、结束时间、持续时间 JSON 并发布。 | 05-03 |
| 05-05 | 新增生命周期服务测试 | 覆盖创建成功、重复创建忽略、创建失败清理、停止释放调度和取消 `broadcast-{instanceId}` 订阅。 | 02-12, 03-08, 04-09 |
| 05-06 | 实现 `ReplayLifecycleService` | 串联时间范围、表发现、表分类、会话创建、动态订阅和元信息发布。 | 05-05 |
| 05-07 | 新增控制服务测试 | 覆盖开始、暂停、继续、倍速、非法状态忽略和缺失会话忽略。 | 05-06 |
| 05-08 | 实现 `ReplayControlService` 基础控制 | 驱动 `ReplayClock`、`ReplayScheduler` 和会话状态机。 | 05-07 |
| 05-09 | 新增跳转服务测试 | 覆盖向后跳转、向前跳转、原地跳转、周期最后一帧、发布失败不推进水位。 | 04-09 |
| 05-10 | 实现 `ReplayJumpService` | 跳转期间暂停连续调度窗口推进，分页查询并发布事件和周期快照。 | 05-09 |
| 05-11 | 集成跳转控制 | `ReplayControlService` 收到 `messageCode=5` 后委托 `ReplayJumpService`，并保持跳转前运行或暂停状态。 | 05-10 |
| 05-12 | 运行阶段测试 | 运行生命周期、控制、跳转和 payload 测试。 | 05-11 |

## 5. 验证要求

- 停止回放只取消 `broadcast-{instanceId}` 订阅，不处理 `situation-{instanceId}` 订阅。
- 时间跳转不发布状态重置协议。
- 向后跳转事件范围为 `[simulationStartTime, targetTime]`。
- 向前跳转事件范围为 `(currentTime, targetTime]`。
- 周期类表只发布目标时间前最后一帧。
- 跳转发布失败时不推进水位。

## 6. 当前无需澄清的问题

本阶段没有阻塞性疑问。

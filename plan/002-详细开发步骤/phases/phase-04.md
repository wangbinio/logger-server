# Phase 04：业务流程串联

## 实现思路

在领域模型、TDengine 能力和 MQ 订阅能力都就位后，再把服务层串起来。这个阶段的重点是把“创建 -> 启动 -> 暂停 -> 继续 -> 停止 -> 态势入库”变成一条完整主链路，并把幂等和丢弃策略落到代码里。

## 需要新增的文件

- `src/main/java/com/szzh/loggerserver/service/SimulationLifecycleService.java`
- `src/main/java/com/szzh/loggerserver/service/SimulationControlService.java`
- `src/main/java/com/szzh/loggerserver/service/SituationRecordService.java`
- `src/main/java/com/szzh/loggerserver/model/dto/TaskCreatePayload.java`
- `src/test/java/com/szzh/loggerserver/service/SimulationLifecycleServiceTest.java`
- `src/test/java/com/szzh/loggerserver/service/SimulationControlServiceTest.java`
- `src/test/java/com/szzh/loggerserver/service/SituationRecordServiceTest.java`

## 待办步骤

- [ ] 实现 `TaskCreatePayload`
  说明：解析全局创建消息中的 `instanceId` 等字段。
- [ ] 实现 `SimulationLifecycleService`
  说明：处理实例创建、资源初始化、实例停止和资源回收。
- [ ] 实现 `SimulationControlService`
  说明：承接启动、暂停、继续，驱动时钟和状态迁移。
- [ ] 实现 `SituationRecordService`
  说明：完成态势消息接收、状态校验、仿真时间计算和写库委派。
- [ ] 串联 `GlobalBroadcastListener` 与生命周期服务
  说明：保证创建时会建会话、建超表、订阅实例 topic；停止时取消订阅并释放资源。
- [ ] 串联实例控制与态势处理器
  说明：保证只有 `RUNNING` 状态才会真正写库，其余状态走丢弃计数。
- [ ] 编写服务层测试
  说明：覆盖创建、重复创建、暂停、继续、停止、无会话态势消息、非运行态态势消息等场景。

## 预期完成标志

- 从 RocketMQ 消息到 TDengine 写入的主链路完整闭环。
- 幂等和丢弃策略有明确测试保护。

## 疑问或待澄清

当前无阻塞性疑问。

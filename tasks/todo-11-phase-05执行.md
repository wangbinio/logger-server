# 任务：Phase 05 执行

## 背景

根据 `plan\002-详细开发步骤\phases\phase-05.md`，本阶段需要为已完成的主链路补齐质量闭环，包括协议解析测试、异常分层、结构化日志、基础指标封装、主流程集成测试以及开发文档回写，确保交付物达到可 review、可观察、可维护的标准。

## 执行项

- [x] 复核 `phase-05.md`、`development-steps.md` 与总体设计中的异常处理和可观测性约束。
- [x] 先编写失败测试，覆盖 `ProtocolMessageUtil` 合法包、非法包头、非法长度等协议边界。
- [x] 先编写失败测试，覆盖创建、启动、态势写入、暂停、继续、停止的主流程集成链路。
- [x] 新增异常模型与指标封装，区分协议异常、业务状态异常和写库异常。
- [x] 收敛消息入口与服务层日志，统一输出 `instanceId`、`topic`、`messageType`、`messageCode`、`senderId`、`simtime` 等字段。
- [x] 在不引入外部监控平台的前提下接入基础计数器入口，并在关键路径更新指标。
- [x] 使用 Java 8 运行 Maven 测试，验证 phase 05 全部改动。
- [x] 回写 `phase-05.md`、`development-steps.md` 与本任务 review。

## 验收标准

- `ProtocolMessageUtil` 能正确解析合法协议包，并对非法包头、非法长度等异常输入给出稳定行为。
- 生命周期链路具备可执行的集成测试，覆盖创建、启动、态势写入、暂停、继续、停止关键流程。
- 协议解析异常、业务状态异常和 TDengine 写入异常具备清晰分层，消息消费不会因单条异常失控退出。
- 关键入口与服务层日志具备统一字段，基础指标能统计协议失败、写入失败、消息接收与写入成功等关键计数。
- 文档状态与代码现状保持一致，Phase 05 可标记为完成。

## Review

- 已新增 `BusinessException`、`ProtocolParseException` 与 `LoggerMetrics`，把协议异常、状态异常、写库异常与基础计数器入口统一收口。
- 已重构 `ProtocolMessageUtil.parseData`，对合法协议正常返回，对非法包头、非法长度、包尾异常统一抛出 `ProtocolParseException`。
- 已在 `GlobalBroadcastListener`、`InstanceBroadcastMessageHandler`、`SituationMessageHandler` 接入统一异常兜底与结构化日志，确保单条异常不会继续向消费线程外冒泡。
- 已在 `SimulationLifecycleService`、`SimulationControlService`、`SituationRecordService`、`TdengineWriteService` 接入结构化日志和指标更新，覆盖创建、控制、写入、丢弃、重试、失败等关键路径。
- 已新增 `ProtocolMessageUtilTest` 与 `SimulationFlowIntegrationTest`；其中集成测试通过 mock 基础设施完整覆盖创建、启动、态势写入、暂停、继续、停止链路，并校验指标变化。
- 已先在缺失异常/指标实现时运行一次 Java 8 下的 `mvn -q test`，确认测试失败；随后补齐实现后再次运行通过。
  - `JAVA_HOME=C:\Users\summer\.jdks\corretto-1.8.0_482`
  - 命令：`mvn -q test`

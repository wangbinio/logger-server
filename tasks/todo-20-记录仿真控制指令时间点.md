# 记录仿真控制指令时间点

## 执行计划

- [x] 补充失败测试，覆盖控制表 SQL、建表、写入 DTO、写入服务、生命周期创建和控制命令记录。
- [x] 扩展 TDengine 常量，新增控制时间点表名、建表 SQL 和写入 SQL 构造方法。
- [x] 新增 `TimeControlRecordCommand`，校验实例 ID、仿真时间和倍率语义。
- [x] 扩展 `TdengineSchemaService`，在实例创建时创建控制时间点表。
- [x] 扩展 `TdengineWriteService`，支持控制时间点写入、参数顺序和失败重试。
- [x] 调整 `SimulationControlService`，仅在控制命令实际生效后非阻断写入控制时间点。
- [x] 执行 `mvn test`，根据结果修正实现直到相关测试通过。

## 关键约束

- 运行期控制记录写入失败只记录日志，不阻断开始、暂停、继续的状态迁移。
- 重复或非法控制命令不写入控制时间点。
- `start` 与 `resume` 统一记录 `rate=1`，`pause` 记录 `rate=0`。
- `ts` 使用 TDengine `NOW`，`simtime` 使用控制命令生效后的仿真时间。
- 保持 Java 8 兼容，新增函数声明保留中文注释。

## Review

- 已按 TDD 路径先补充失败测试，再实现生产代码。
- 相关验证通过：`mvn "-Dtest=TdengineConstantsTest,TimeControlRecordCommandTest,TdengineSchemaServiceTest,TdengineWriteServiceTest,SimulationLifecycleServiceTest,SimulationControlServiceTest,SimulationFlowIntegrationTest" test`，结果为 30 个测试通过。
- 全量验证执行过 `mvn test`，新增相关测试均通过；唯一失败为既有 `TopicSubscriptionManagerTest.shouldConsumeMessagesWithRealRocketMq`，错误是外部 RocketMQ 请求超时 `RemotingTooMuchRequest`，与本次控制时间点代码无直接关系。
- 保持运行期控制记录写入失败不影响控制状态迁移，不调用 `session.recordFailure(...)`。

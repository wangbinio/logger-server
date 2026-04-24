# 任务：执行 Phase 02

## 背景

根据 `plan/002-详细开发步骤/development-steps.md` 与 `plan/002-详细开发步骤/phases/phase-02.md`，实现 TDengine 基础设施，包括建表服务、写入服务、命令对象和 SQL 常量。

## 执行项

- [x] 阅读阶段计划、现有领域模型与 TDengine 配置，确认 Phase 02 的边界。
- [x] 先补 `TdengineSchemaServiceTest` 与 `TdengineWriteServiceTest`，定义 SQL 拼装、写入和重试行为。
- [x] 实现 `SituationRecordCommand`、`TdengineConstants`、`TdengineSchemaService`、`TdengineWriteService`。
- [x] 执行 Maven 测试并回写阶段状态与 review。

## 验收标准

- 上层服务可以通过命令对象完成超表初始化与态势数据写入。
- 写入服务支持标准 JDBC 写入与 `TaosPrepareStatement` 批量写入。
- Maven 测试通过。

## Review

- 已新增 `SituationRecordCommand`、`TdengineConstants`、`TdengineSchemaService`、`TdengineWriteService`，把 TDengine 超表初始化、标准 JDBC 写入和 WebSocket `TSWSPreparedStatement` 批量写入能力独立收敛到基础设施层。
- 已新增 `TdengineSchemaServiceTest` 与 `TdengineWriteServiceTest`，覆盖超级表 SQL 拼装、标准写入参数设置、重试逻辑、stmt 批量写入和非 WebSocket statement 拒绝分支。
- 已执行 `mvn -q test`，全部通过。当前 Surefire 报告共 7 个测试类、20 个测试用例，失败数和错误数均为 0。

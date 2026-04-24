# Phase 05：测试、日志与交付收尾

## 实现思路

最后阶段专注质量闭环。把前面各阶段留下的测试、日志、指标、异常处理和文档同步全部补齐，确保交付物不仅能跑，而且可 review、可观察、可维护。

## 需要新增的文件

- `src/main/java/com/szzh/loggerserver/support/metric/LoggerMetrics.java`
- `src/main/java/com/szzh/loggerserver/support/exception/BusinessException.java`
- `src/main/java/com/szzh/loggerserver/support/exception/ProtocolParseException.java`
- `src/test/java/com/szzh/loggerserver/integration/SimulationFlowIntegrationTest.java`

## 待办步骤

- [ ] 补齐协议解析测试
  说明：覆盖 `ProtocolMessageUtil` 的合法输入、非法包头、非法长度等边界场景。
- [ ] 完善异常模型
  说明：区分协议异常、状态异常和 TDengine 写入异常，统一日志出口。
- [ ] 补充结构化日志
  说明：统一输出 `instanceId`、`topic`、`messageType`、`messageCode`、`senderId`、`simtime` 等字段。
- [ ] 接入基础指标封装
  说明：即使暂不引入完整监控平台，也要把计数器入口统一下来。
- [ ] 编写主流程集成测试
  说明：至少通过 mock 方式覆盖创建、启动、态势写入、暂停、继续、停止的关键流程。
- [ ] 做一次端到端自查
  说明：确认代码结构、配置、测试、文档与总体设计保持一致。
- [ ] 同步更新开发文档
  说明：将已完成状态回写到 `development-steps.md` 与对应阶段文件，保证计划与现实一致。

## 预期完成标志

- 关键路径测试齐备。
- 日志和异常处理达到可交付水平。
- 文档状态与实际进度一致。

## 疑问或待澄清

当前无阻塞性疑问。

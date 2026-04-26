# Phase 02 回放 TDengine 查询与表分类执行

## 背景

按照 `plan/005-回放系统设计/development-steps.md` 与 `phases/phase-02.md` 执行 Phase 02，实现 `replay-server` 对 TDengine 的只读查询能力、态势子表发现、事件/周期表分类和分页帧查询。

## 执行计划

- [x] 检查 Phase 02 设计、总设计 SQL 边界、记录侧 TDengine 实现和当前回放模块结构。
- [x] 将 Phase 02 状态标记为进行中。
- [x] 先补 `ReplayTdengineConfig` 配置测试并确认失败。
- [x] 先补查询模型测试并确认失败。
- [x] 先补表发现 Repository 测试并确认失败。
- [x] 先补表分类测试并确认失败。
- [x] 先补时间范围查询测试并确认失败。
- [x] 先补帧查询测试并确认失败。
- [x] 实现 TDengine 配置、查询模型、表发现、表分类、时间范围查询和分页帧查询。
- [x] 运行 `mvn -pl replay-server -am test` 验证。
- [x] 运行完整 `mvn test` 回归。
- [x] 回写 Phase 02 文档、开发步骤索引和本任务单 Review。

## Review

Phase 02 已完成。实际落地内容包括回放侧 TDengine 配置、查询模型、态势子表 tag 元数据发现、事件/周期表分类、时间范围解析和分页帧查询。表分类严格基于 `sender_id/msgtype/msgcode` 等 tag 元数据，不依赖表名解析。

验证结果：

- 已先新增阶段测试并确认生产类缺失导致测试失败，随后补充实现使测试通过。
- `mvn -pl replay-server -am test` 已通过，覆盖 `common` 与 `replay-server`。
- 完整 `mvn test` 已通过，`logger-server` 中真实环境开关测试保持既有跳过行为。

遗留风险：

- 本阶段不启动调度、不发布 RocketMQ 消息，相关能力留给 Phase 04 与 Phase 05。
- 未执行真实 TDengine/RocketMQ 全链路联调，真实环境验证留给 Phase 06。
- `simtime` 非 TDengine 主时间列的性能风险沿用总设计结论，后续需基于真实数据规模复评。

# 回放系统技术方案任务

## 背景

根据 `plan/005-回放系统设计/005-draft.md` 与本轮讨论结论，生成回放系统详细技术方案，输出到 `plan/005-回放系统设计/005-final.md`。

## 执行计划

- [x] 阅读回放草案、总体架构、记录系统实现和控制时间点设计。
- [x] 对照现有代码确认可复用边界和冲突点。
- [x] 收敛用户确认的关键设计决策。
- [x] 编写回放系统详细技术方案。
- [x] 更新经验记录，避免后续重复混淆回放订阅职责。
- [x] 自检文档是否覆盖架构拆分、回放时钟、TDengine 查询、时间跳转、无 Redis 策略和测试验收。

## 用户确认的设计决策

- 回放系统与记录系统拆成 `common`、`logger-server`、`replay-server`，生产环境部署 `logger-server` 和 `replay-server` 两个服务。
- 第一版暂不拆分原始记录实例 ID 与回放实例 ID，统一使用同一个 `instanceId`。
- 回放系统不订阅 `situation-{instanceId}`，只订阅 `broadcast-global` 和动态订阅 `broadcast-{instanceId}`。
- 时间跳转不由回放系统发布状态重置协议，外部服务自行处理状态重置。
- 记录系统停止任务时需要补写控制时间点，回放方案按 stop 控制点存在设计，同时保留兼容降级策略。
- 新增独立 `ReplayClock`，不硬改现有 `SimulationClock`。
- 第一版不引入 Redis。
- 表分类使用 TDengine tag 元数据，不解析表名。

## Review

已生成 `005-final.md`。文档把草案中的错误订阅目标修正为停止订阅 `broadcast-{instanceId}`，并明确了服务拆分、消息码隔离、回放状态机、ReplayClock、TDengine 元数据发现、事件表与周期表处理、时间跳转边界、发布协议重组、性能策略、测试计划和验收标准。

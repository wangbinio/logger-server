# Phase 02 TDengine 查询与表分类

## 1. 阶段目标

实现回放系统对 TDengine 的只读查询能力，包括：

- 查询 `time_control_{instanceId}` 计算开始时间、结束时间和持续时间。
- 通过 TDengine tag 元数据发现态势子表。
- 根据配置将子表分类为事件类表和周期类表。
- 支持连续回放窗口查询、事件跳转补偿查询和周期表最后一帧查询。

本阶段不启动回放调度，也不发布 RocketMQ 消息。

## 2. 实现思路

Repository 层负责屏蔽 TDengine SQL 细节，Service 或领域层只处理业务语义。子表分类必须基于 tag 元数据，而不是解析表名。所有时间窗口都以 `simtime` 为准，第一版接受 `simtime` 不是 TDengine 主时间列带来的潜在性能风险。

查询方法必须分页，禁止一次性加载大跨度事件数据。

## 3. 需要新增的文件

| 文件 | 说明 |
| ---- | ---- |
| `replay-server/src/main/java/.../config/ReplayTdengineConfig.java` | 回放侧 TDengine 数据源配置。 |
| `replay-server/src/main/java/.../model/query/ReplayTableType.java` | 子表类型枚举，事件类和周期类。 |
| `replay-server/src/main/java/.../model/query/ReplayTableDescriptor.java` | 子表描述，保存表名、senderId、messageType、messageCode、类型。 |
| `replay-server/src/main/java/.../model/query/ReplayFrame.java` | 回放数据帧。 |
| `replay-server/src/main/java/.../model/query/ReplayCursor.java` | 分页游标。 |
| `replay-server/src/main/java/.../model/query/ReplayTimeRange.java` | 回放时间范围。 |
| `replay-server/src/main/java/.../repository/ReplayTableDiscoveryRepository.java` | 查询 TDengine tag 元数据。 |
| `replay-server/src/main/java/.../repository/ReplayTimeControlRepository.java` | 查询开始时间、结束时间和降级结束时间。 |
| `replay-server/src/main/java/.../repository/ReplayFrameRepository.java` | 查询连续窗口、跳转事件和周期最后一帧。 |
| `replay-server/src/main/java/.../service/ReplayTableClassifier.java` | 按配置分类事件表和周期表。 |
| `replay-server/src/test/java/.../repository/ReplayTableDiscoveryRepositoryTest.java` | 元数据查询测试。 |
| `replay-server/src/test/java/.../repository/ReplayTimeControlRepositoryTest.java` | 控制时间点查询测试。 |
| `replay-server/src/test/java/.../repository/ReplayFrameRepositoryTest.java` | 帧查询边界测试。 |
| `replay-server/src/test/java/.../service/ReplayTableClassifierTest.java` | 表分类测试。 |

## 4. 待办条目

| 编号 | 待办 | 简要说明 | 前置依赖 |
| ---- | ---- | ---- | ---- |
| 02-01 | 新增 TDengine 配置测试 | 验证 `ReplayTdengineConfig` 能创建 `DataSource` 和 `JdbcTemplate`，配置字段来自 `replay-server.tdengine`。 | Phase 01 |
| 02-02 | 实现 `ReplayTdengineConfig` | 复用记录侧 WebSocket JDBC 连接方式，但配置前缀独立。 | 02-01 |
| 02-03 | 新增查询模型测试 | 验证 `ReplayTableDescriptor`、`ReplayFrame`、`ReplayCursor` 的参数校验和不可变字段。 | 02-02 |
| 02-04 | 实现查询模型 | 使用 Lombok 减少样板代码，构造函数保留必要校验。 | 02-03 |
| 02-05 | 新增表发现 Repository 测试 | Mock `JdbcTemplate`，验证查询 `information_schema.ins_tags` 并按 `table_name` 聚合 tag。 | 02-04 |
| 02-06 | 实现 `ReplayTableDiscoveryRepository` | 查询并转换 `sender_id`、`msgtype`、`msgcode`，缺失 tag 时抛出数据异常。 | 02-05 |
| 02-07 | 新增表分类测试 | 覆盖命中事件配置、未命中为周期表、空配置全部周期表和多 messageCode 匹配。 | 02-06 |
| 02-08 | 实现 `ReplayTableClassifier` | 读取 `replay.event-messages` 配置，用 `messageType + messageCode` 精确匹配。 | 02-07 |
| 02-09 | 新增时间范围查询测试 | 覆盖控制表开始时间、停止控制点结束时间、缺少停止控制点时降级 `MAX(simtime)`。 | 02-08 |
| 02-10 | 实现 `ReplayTimeControlRepository` | 提供 `resolveTimeRange(instanceId)`，返回开始时间、结束时间和持续时间。 | 02-09 |
| 02-11 | 新增帧查询测试 | 覆盖 `(from, to]`、`[start, target]`、`(current, target]` 和周期最后一帧查询 SQL。 | 02-10 |
| 02-12 | 实现 `ReplayFrameRepository` | 所有列表查询支持 `limit` 和 `offset`，周期最后一帧使用倒序 `LIMIT 1`。 | 02-11 |
| 02-13 | 运行阶段测试 | 运行 TDengine 查询与表分类相关测试。 | 02-12 |

## 5. 验证要求

- 表分类不依赖表名拆分。
- 查询边界默认右闭，避免 `simtime == targetTime` 的事件丢失。
- 历史数据没有停止控制点时，能降级从态势数据查询最大 `simtime`。
- 大跨度事件查询必须分页。

## 6. 当前无需澄清的问题

本阶段没有阻塞性疑问。

## 7. Review

### 7.1 实际改动

- 新增回放侧 TDengine 数据源与 `JdbcTemplate` 配置，配置前缀独立于记录服务。
- 新增 `ReplayTableDescriptor`、`ReplayFrame`、`ReplayCursor`、`ReplayTimeRange` 和 `ReplayTableType`，用于承载表元数据、分页游标、帧数据和时间范围。
- 新增 `ReplayTableDiscoveryRepository`，通过 `information_schema.ins_tags` 读取 `sender_id`、`msgtype`、`msgcode` 等 tag 元数据，并按 `table_name` 聚合为子表描述。
- 新增 `ReplayTableClassifier`，按 `messageType + messageCode` 精确匹配 `replay-server.query.event-messages`，未命中的子表默认归为周期表。
- 新增 `ReplayTimeControlRepository`，支持优先从 `time_control_{instanceId}` 解析开始时间与停止控制点结束时间，并在缺少控制点时降级读取态势表 `MIN(simtime)` 或 `MAX(simtime)`。
- 新增 `ReplayFrameRepository`，支持连续回放窗口 `(from, to]`、向后跳转 `[start, target]`、向前跳转 `(current, target]` 和周期表最后一帧查询，所有列表查询均带 `LIMIT/OFFSET`。
- 补充配置、查询模型、表发现、表分类、时间范围解析和帧查询测试，覆盖本阶段主要边界。

### 7.2 验证结果

- 已按 TDD 流程先补 Phase 02 失败测试，再补生产实现。
- 已执行 `mvn -pl replay-server -am test`，`common` 与 `replay-server` 阶段测试通过。
- 已执行完整 `mvn test`，全模块回归通过；`logger-server` 中真实环境开关测试保持既有跳过行为。

### 7.3 遗留风险

- 本阶段仅实现只读查询、表分类和分页能力，不启动回放调度，不发布 RocketMQ 消息。
- 本阶段未执行真实 TDengine 环境联调；TDengine SQL 已通过 Mock `JdbcTemplate` 验证结构与参数，真实环境验证留给 Phase 06。
- 第一版仍按设计接受 `simtime` 非 TDengine 主时间列带来的潜在查询性能风险，后续如数据量显著增大，需要结合真实库索引与分区策略复评。

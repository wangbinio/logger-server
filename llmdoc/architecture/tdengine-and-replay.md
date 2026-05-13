# TDengine 与回放架构

## 表模型

每个实例一张态势超级表：

```sql
CREATE STABLE IF NOT EXISTS situation_{instanceId}
(
  ts TIMESTAMP,
  simtime BIGINT,
  rawdata BINARY(8192)
)
TAGS (
  sender_id INT,
  msgtype INT,
  msgcode INT
);
```

态势子表命名：

```text
situation_{messageType}_{messageCode}_{senderId}_{instanceId}
```

每个实例一张控制时间点表：

```sql
CREATE TABLE IF NOT EXISTS time_control_{instanceId}
(
  ts TIMESTAMP,
  simtime BIGINT,
  rate DOUBLE,
  sender_id INT,
  msgtype INT,
  msgcode INT
);
```

## 字段语义

- `ts`：服务处理时间，由 TDengine 写入 `NOW`。
- `simtime`：仿真时间，是回放查询和控制时间点的业务时间。
- `rawdata`：平台协议数据域原始载荷，当前为 `BINARY(8192)`。
- `sender_id`、`msgtype`、`msgcode`：子表 tag，也是回放分类和查询维度。
- `rate`：控制生效后的倍率，`start/resume=1`，`pause/stop=0`。

## 低版本 TDengine 兼容

当前生产兼容组合：

```text
taos-jdbcdriver 3.2.4
jdbc:TAOS-RS://...:6041/logger
com.taosdata.jdbc.rs.RestfulDriver
```

当前不使用 `jdbc:TAOS-WS`。数据源配置类会校验 URL 前缀和 driver 是否匹配。

真实环境探测显示低版本 RESTful 驱动组合下，`VARBINARY(8192)` 写入后读回存在空值风险。因此当前态势表使用 `BINARY(8192)`。如果以后升级 TDengine 或驱动，必须用真实环境读写测试重新验证字段选择。

## 回放查询语义

- `ReplayTimeControlRepository` 优先从控制时间点表读取开始和停止时间。
- 如果控制表缺失，可降级到态势超级表 `MIN(simtime)` 和 `MAX(simtime)`。
- `ReplayTableDiscoveryRepository` 从 `information_schema.ins_tags` 读取子表 tag。
- `ReplayTableClassifier` 按 `replay-server.replay.event-messages` 将子表分为事件表和周期表。
- `ReplayFrameRepository` 使用 `RowMapper` 直接读取 `ResultSet`，优先 `getBytes("rawdata")`，再兼容 `String`、`Blob`、`ByteBuffer`。

## 跳转规则

- 向前跳转发布 `(currentTime, targetTime]` 的事件帧。
- 向后跳转发布 `[simulationStartTime, targetTime]` 的事件帧。
- 周期表发布目标时间前最后一帧快照。
- 补偿发布失败时不伪装跳转成功。


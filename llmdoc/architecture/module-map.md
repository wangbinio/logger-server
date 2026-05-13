# 模块地图

## common

| 包 | 职责 |
| --- | --- |
| `com.szzh.common.protocol` | 平台协议解析与构包，核心类 `ProtocolData`、`ProtocolMessageUtil`。 |
| `com.szzh.common.json` | Jackson JSON 工具。 |
| `com.szzh.common.topic` | `broadcast-global`、`broadcast-{instanceId}`、`situation-{instanceId}` 命名。 |
| `com.szzh.common.tdengine` | `situation_`、`time_control_` 表名构造和标识符清洗。 |
| `com.szzh.common.exception` | 业务异常和协议解析异常。 |

## logger-server

| 包 | 职责 |
| --- | --- |
| `config` | 配置绑定、RocketMQ 消费者工厂、TDengine 数据源。 |
| `domain.clock` | `SimulationClock`，支持启动、暂停、继续和倍率字段。 |
| `domain.session` | 记录侧会话、状态机和会话注册表。 |
| `model.dto` | 创建载荷、态势写入命令、控制时间点命令。 |
| `mq` | 全局监听、实例控制处理、态势处理和动态订阅。 |
| `service` | 生命周期、控制、态势记录、TDengine 建表和写入。 |
| `support.constant` | 记录侧消息常量和 TDengine SQL 模板。 |
| `support.metric` | 记录侧内存指标。 |

## replay-server

| 包 | 职责 |
| --- | --- |
| `config` | 回放配置绑定、RocketMQ 生产/消费配置、TDengine 数据源。 |
| `controller` | HTTP 回放控制接口。 |
| `domain.clock` | `ReplayClock`，支持开始、暂停、继续、倍速、跳转和时间范围限制。 |
| `domain.session` | 回放会话、状态机、水位和表分类结果。 |
| `model.api` | HTTP 响应、会话快照和请求对象。 |
| `model.dto` | 回放创建、倍速、跳转等 payload。 |
| `model.query` | 回放帧、表描述、时间范围和查询游标。 |
| `mq` | 回放全局监听、保留的 MQ 控制适配、态势发布。 |
| `repository` | TDengine 时间范围、表发现和帧查询。 |
| `service` | 生命周期、控制、调度、跳转、表分类、帧归并和 HTTP 命令适配。 |
| `support.metric` | 回放侧内存指标。 |


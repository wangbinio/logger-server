# Phase 02：TDengine 基础设施

## 实现思路

在 Phase 01 的领域模型稳定后，单独实现 TDengine 访问层。目标是把建表、子表命名、参数绑定写入、异常重试做成独立服务，让上层业务只关心“写什么”，不关心“怎么连、怎么建、怎么写”。

## 需要新增的文件

- `src/main/java/com/szzh/loggerserver/service/TdengineSchemaService.java`
- `src/main/java/com/szzh/loggerserver/service/TdengineWriteService.java`
- `src/main/java/com/szzh/loggerserver/model/dto/SituationRecordCommand.java`
- `src/main/java/com/szzh/loggerserver/support/constant/TdengineConstants.java`
- `src/test/java/com/szzh/loggerserver/service/TdengineSchemaServiceTest.java`
- `src/test/java/com/szzh/loggerserver/service/TdengineWriteServiceTest.java`

## 待办步骤

- [x] 定义 TDengine 命名与 SQL 常量
  说明：统一超表名、子表名模板、DDL 模板和插入 SQL 模板。
- [x] 实现 `SituationRecordCommand`
  说明：封装一次态势入库所需的 `instanceId`、`senderId`、`messageType`、`messageCode`、`simtime`、`rawData`。
- [x] 实现 `TdengineSchemaService`
  说明：负责超表存在性检查、按实例建超表、必要时初始化数据库。
- [x] 实现 `TdengineWriteService`
  说明：封装低频 SQL 执行与高频 `stmt` 参数绑定写入，预留重试与错误日志能力。
- [x] 编写建表服务测试
  说明：至少验证 SQL 拼装正确，必要时对 `JdbcTemplate` 或 statement 接口做 mock。
- [x] 编写写入服务测试
  说明：验证子表名生成、参数绑定顺序、异常分支和重试触发条件。

## 预期完成标志

- 上层服务可以通过命令对象完成建表和入库。
- TDengine 访问逻辑独立封装，不污染业务层。

## 疑问或待澄清

当前无阻塞性疑问。

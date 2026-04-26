# Phase 00 多模块拆分与 common 基线

## 1. 阶段目标

将当前单模块工程调整为生产部署所需的三模块结构：

```text
common
logger-server
replay-server
```

本阶段只做结构拆分和公共能力抽取，不实现回放业务主链路。完成后必须证明现有 `logger-server` 记录功能测试不回退。

## 2. 实现思路

先把 Maven 父工程建立起来，再将当前源码迁入 `logger-server` 子模块。随后创建 `common` 模块，并把协议、JSON、Topic、TDengine 命名和通用异常等无业务状态的代码迁入 `common`。最后创建空的 `replay-server` Spring Boot 模块，先只保证上下文可启动和依赖关系正确。

该阶段的重点不是改业务，而是确保模块边界清晰、依赖方向单向、记录服务原有行为不变。

## 3. 需要新增或调整的文件

| 文件 | 类型 | 说明 |
| ---- | ---- | ---- |
| `pom.xml` | 调整 | 改为 Maven 父工程，声明 `common`、`logger-server`、`replay-server` 模块。 |
| `common/pom.xml` | 新增 | 公共模块依赖，避免引入业务服务依赖。 |
| `logger-server/pom.xml` | 新增 | 记录服务模块依赖，依赖 `common`。 |
| `replay-server/pom.xml` | 新增 | 回放服务模块依赖，依赖 `common`。 |
| `common/src/main/java/.../ProtocolData.java` | 迁移 | 从记录服务迁入公共模块。 |
| `common/src/main/java/.../ProtocolMessageUtil.java` | 迁移 | 从记录服务迁入公共模块。 |
| `common/src/main/java/.../JsonUtil.java` | 迁移 | 从记录服务迁入公共模块。 |
| `common/src/main/java/.../TopicConstants.java` | 迁移 | 从记录服务迁入公共模块。 |
| `common/src/main/java/.../TdengineNaming.java` | 新增 | 放置 TDengine 标识清洗和表名构造公共规则。 |
| `common/src/main/java/.../BusinessException.java` | 迁移 | 通用业务异常。 |
| `common/src/main/java/.../ProtocolParseException.java` | 迁移 | 通用协议解析异常。 |
| `logger-server/src/main/**` | 迁移 | 当前记录服务源码整体迁入子模块。 |
| `logger-server/src/test/**` | 迁移 | 当前记录服务测试整体迁入子模块。 |
| `replay-server/src/main/java/.../ReplayServerApplication.java` | 新增 | 回放服务启动类。 |
| `replay-server/src/test/java/.../ReplayServerApplicationTests.java` | 新增 | 回放服务上下文加载测试。 |

## 4. 待办条目

| 编号 | 待办 | 简要说明 | 前置依赖 |
| ---- | ---- | ---- | ---- |
| 00-01 | 建立父工程 `pom.xml` | 将当前根 `pom.xml` 调整为聚合父工程，统一 Java 8、Spring Boot、依赖版本和插件管理。 | 无 |
| 00-02 | 创建 `logger-server` 子模块 | 把当前记录系统源码、资源和测试迁入 `logger-server`，保持包名和配置语义不变。 | 00-01 |
| 00-03 | 创建 `common` 子模块 | 新建公共模块，只保留不依赖记录或回放业务状态的代码。 | 00-01 |
| 00-04 | 迁移协议与工具类到 `common` | 迁移 `ProtocolData`、`ProtocolMessageUtil`、`JsonUtil`、通用异常和 Topic 命名工具，并修正引用。 | 00-02, 00-03 |
| 00-05 | 抽取 TDengine 公共命名规则 | 将标识清洗、stable 名、子表名、控制表名等纯命名规则放入 `common`，记录侧 SQL 模板仍保留在 `logger-server`。 | 00-04 |
| 00-06 | 创建 `replay-server` 空模块 | 新增回放服务启动类、基础配置文件和上下文加载测试。 | 00-03 |
| 00-07 | 调整包扫描与配置绑定 | 确保 `logger-server` 和 `replay-server` 都能扫描到自身 Bean，并显式依赖 `common`。 | 00-04, 00-06 |
| 00-08 | 回归验证记录系统 | 在 Java 8 环境下运行 `logger-server` 模块测试，确认记录系统行为未回退。 | 00-07 |

## 5. 验证要求

- `mvn -pl common test` 通过。
- `mvn -pl logger-server -am test` 通过。
- `mvn -pl replay-server -am test` 通过。
- `mvn test` 至少能完成不依赖真实 RocketMQ 和 TDengine 的测试。
- 记录系统原有配置、消息类型和测试语义不因模块拆分改变。

## 6. Review

### 6.1 实际改动

- 根 `pom.xml` 已改为 Maven 父工程，模块包括 `common`、`logger-server`、`replay-server`。
- 当前记录系统源码、资源和测试已迁入 `logger-server` 子模块。
- 新增 `common` 模块，迁入 `ProtocolData`、`ProtocolMessageUtil`、`JsonUtil`、`TopicConstants`、`BusinessException`、`ProtocolParseException`，并新增 `TdengineNaming`。
- `logger-server` 已改为依赖 `common` 中的协议、异常、JSON、Topic 和 TDengine 命名能力。
- 新增 `replay-server` 空 Spring Boot 应用、基础配置和上下文加载测试。

### 6.2 验证结果

- `mvn -pl common test` 通过，12 个测试通过。
- `mvn -pl replay-server -am test` 通过，`common` 12 个测试通过，`replay-server` 1 个测试通过。
- `mvn -pl logger-server -am test` 通过，`common` 12 个测试通过，`logger-server` 59 个测试运行，0 失败，0 错误，1 个真实环境测试按设计跳过。
- `mvn test` 通过，完整 reactor `logger-platform`、`common`、`logger-server`、`replay-server` 全部成功。

### 6.3 遗留风险

- 当前 Phase 00 只创建 `replay-server` 空应用，不包含回放业务 Bean。
- `replay-server` 暂时排除了 DataSource 自动配置，后续 Phase 02 实现 TDengine 配置时需要移除或调整。
- 直接执行单模块命令时，依赖 sibling 模块的命令需要带 `-am`，例如 `mvn -pl replay-server -am test`。

## 7. 当前无需澄清的问题

本阶段没有阻塞性疑问。

# 任务：方案修订与 pom 清理

## 背景

根据用户对 `plan/001-总体设计/final.md` 的三项明确裁定：

- 协议解析口径直接复用现有 `ProtocolData` 与 `ProtocolMessageUtil`
- 立即清理并修正 `pom.xml`
- TDengine 不再走 MyBatis Plus，连接与写入方案改为参考官方文档采用 WebSocket 路线

## 执行项

- [x] 核对 TDengine 官方文档，确认 Java 连接与参数绑定写入的推荐方式。
- [x] 修订 `final.md` 中 2.1、2.2 及 4.2 决策四相关内容。
- [x] 清理并修正 `pom.xml`，与 `logger-server` 的实际职责保持一致。
- [ ] 运行 Maven 校验，确认依赖收敛后项目仍可构建。当前环境缺少 `mvn` / `mvnw`，已改为静态校验。
- [x] 回填任务 review。

## 验收标准

- `final.md` 已反映用户确定的三项结论。
- `pom.xml` 中不再包含与当前服务无关的依赖。
- TDengine 方案已明确为官方 Java Connector 的 WebSocket 路线。

## Review

- 已将 `final.md` 中协议解析口径改为“直接复用现有工具类”，不再保留待确认项。
- 已将 `final.md` 中 `pom.xml` 清理从建议升级为当前阶段确定动作。
- 已将 TDengine 路线收敛为官方 Java Connector + WebSocket，Spring 层采用 `spring-boot-starter-jdbc`，写入优先使用 `PreparedStatement` / 官方 `stmt` 能力。
- 已清理 `pom.xml`，移除 Web、安全、Redis、MyBatis Plus、MapStruct 等无关依赖，并修正 `artifactId` 为 `logger-server`。
- 已完成 `pom.xml` XML 解析与关键依赖静态核对，但当前环境没有 `mvn` 或 `mvnw`，未能执行实际 Maven 构建验证。

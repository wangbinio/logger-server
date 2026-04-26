# Phase 00 回放多模块拆分执行

## 背景

按照 `plan/005-回放系统设计/development-steps.md` 与 `phases/phase-00.md` 执行 Phase 00，将当前单模块工程调整为 `common`、`logger-server`、`replay-server` 三模块结构。

## 执行计划

- [x] 检查当前工程结构、POM 和未提交变更。
- [x] 将根工程改造为 Maven 父工程。
- [x] 创建 `common` 模块并迁移协议、JSON、Topic、通用异常和 TDengine 命名能力。
- [x] 创建 `logger-server` 模块并迁移现有记录服务源码、资源和测试。
- [x] 创建 `replay-server` 空 Spring Boot 应用和上下文测试。
- [x] 修正 `logger-server` 对 `common` 包的引用。
- [x] 运行模块级测试验证。
- [x] 更新 Phase 00 文档 Review。

## Review

已完成 Phase 00。根工程已改为 Maven 多模块父工程，新增 `common`、`logger-server`、`replay-server` 三个模块；`common` 承载协议、JSON、Topic、通用异常和 TDengine 命名规则；记录服务迁入 `logger-server` 并依赖 `common`；`replay-server` 已建立空 Spring Boot 应用和上下文测试。

验证结果：

- `mvn -pl common test` 通过。
- `mvn -pl replay-server -am test` 通过。
- `mvn -pl logger-server -am test` 通过。
- `mvn test` 通过。

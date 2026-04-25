# 任务：合并 dev 与 local 配置

## 背景

当前仓库同时存在 `application.yml`、`application-dev.yml`、`application-local.yml` 三份配置文件。用户要求将源码中的配置文件收敛为两份，不再保留 `application-local.yml`，同时不要修改现有测试代码，而是在调整配置后直接使用现有测试进行验证。

## 计划

- [x] 复核现有 YAML 分工以及测试对 `local` 配置文件和 `local` profile 的依赖点。
- [x] 将 `application-local.yml` 中仍有价值的环境配置并入 `application-dev.yml`，并把默认激活 profile 调整为 `dev`。
- [x] 删除源码中的 `application-local.yml`，并通过构建配置提供仅测试期可见的 `application-local.yml` 兼容别名，保证测试代码无需改动。
- [x] 直接运行现有测试验证配置合并后的兼容性。
- [x] 回填 review 结论。

## Review

- 源码中的运行期配置文件已收敛为两份：
  - `application.yml` 保留通用配置，并将默认激活 profile 从 `local` 调整为 `dev`。
  - `application-dev.yml` 吸收了原 `application-local.yml` 中的 `rocketmq.name-server` 与 `logger-server.tdengine` 连接信息，同时保留原 `dev` 文件中的池大小、连接超时和业务消费者组前缀配置。
- `src/main/resources/application-local.yml` 已从源码树删除，不再作为源文件维护。
- 为满足“测试代码不修改”的约束，已在 `pom.xml` 增加 `maven-antrun-plugin`：
  - 在 `process-test-resources` 阶段把 `src/main/resources/application-dev.yml` 复制为 `${project.build.testOutputDirectory}/application-local.yml`。
  - 这样 `@ActiveProfiles("local")` 和显式读取 `application-local.yml` 的现有测试仍可直接运行，但兼容文件只存在于测试输出目录，不再占用源码配置位。
- 已按要求直接运行现有测试代码验证，未修改任何测试源码：
  - `mvn -q test` 通过。
- 额外说明：
  - 当前工作区里还存在上一轮任务留下的 Java 与测试改动，这次未回退它们，遵循“不要覆盖未被要求处理的现有改动”原则。

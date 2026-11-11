# 多版本日常维护工作流

## 目标

本规范用于多个维护者分别负责不同 Minecraft 版本或加载器时的日常开发。目标是：

1. target 维护者只下载、构建和评审自己负责的依赖。
2. `common` 只承载没有确证版本差异的代码，不被单个 target 的临时判断或未确证差异污染。
3. 跨版本行为变化有明确的评审和验证范围。
4. 每个发布 jar 的兼容范围、构建 JDK 和责任人可追溯。

## 目录所有权

| 路径 | 默认责任 | 可直接修改 | 额外评审 |
| --- | --- | --- | --- |
| `common/` | Core Maintainer | 无确证版本差异的共享代码、共享资源、共享测试 | 至少一名受影响 target 维护者 |
| `targets/forge-1.20.1/` | Forge 1.20.1 Maintainer | Forge 入口、事件、资源、Mixin、适配器 | Core Maintainer 仅在 common 合约变化时参与 |
| `targets/forge-1.19.2/` | Forge 1.19.2 Maintainer | Forge 入口、事件、资源、Mixin、适配器 | 同上 |
| `targets/forge-1.18.2/` | Forge 1.18.2 Maintainer | Forge 入口、事件、资源、Mixin、适配器 | 同上 |
| `targets/fabric-1.20.1/` | Fabric 1.20.1 Maintainer | Fabric entrypoint、callback、资源、Mixin、适配器 | 同上 |
| `targets/fabric-1.21.1/` | Fabric 1.21.1 Maintainer | Fabric entrypoint、callback、资源、Mixin、适配器 | 同上 |
| `targets/fabric-26.1/` | Fabric 26.1 Maintainer | Java 25、26.1 API、资源、渲染和适配器 | 同上 |
| `targets/neoforge-1.21.1/` | NeoForge 1.21.1 Maintainer | NeoForge 入口、事件、资源、Mixin、适配器 | 同上 |
| `targets/neoforge-26.1/` | NeoForge 26.1 Maintainer | Java 25、26.1 API、资源、渲染和适配器 | 同上 |
| 根 Gradle、CI、`docs/` | Build / Release Maintainer | 聚合入口、CI 矩阵、维护文档 | 受影响 target 维护者 |

一个人可以承担多个角色，但评审规则仍按路径执行。实际 GitHub 账号映射写入 `.github/CODEOWNERS`；仓库提供 `.github/CODEOWNERS.example` 作为起点。

## 非协商边界

### common

`common` 包含所有没有确证版本差异的代码，可以引用 Minecraft 类型，也可以引用 loader、Mixin、渲染与网络 API；依赖平台类型不是拆分理由，Java 版本也不是。

从 `common` 拆到 `targets/<loader>-<mc-version>/` 的唯一理由是：经阅读实际源码/API 确证该内容在不同 loader 或不同 Minecraft 版本之间存在差异。不能凭猜测拆，也不能为了“代码好看”拆。

`common` 禁止包含：

- 以运行时 loader/version 判断、反射分发或同名 class 覆盖来伪装兼容不同 target。
- 某个 target 专属的资源路径。
- 为掩盖某个 target 的编译错误而加入的抽象泄漏；正确做法是在该 target 的 `commonJava` 中 `exclude` 对应文件。

版本差异通过 target 的 `commonJava` `exclude` 登记：某个 common 文件在该 target 上有确证差异时，在该 target 的 `commonJava` 里 `exclude '<路径>'`，并把该 target 自己的实现放进 target。

### target

每个 target 是独立 Gradle 根工程，拥有自己的：

- 入口、注册、事件、网络和生命周期代码。
- Minecraft API 调用、Mixin、accessor、渲染后端和 client-only 代码。
- metadata、资源、数据生成和版本范围。
- JDK、Gradle wrapper、加载器和 mappings 配置。

不要把 target 专属类以同一全限定名复制到 `common`，也不要依赖 classpath 顺序覆盖 shared class。

## 变更分类

提交或 PR 必须先选择一种主类别。

| 类别 | 典型路径 | 最小评审 | 最小验证 |
| --- | --- | --- | --- |
| `target-only` | 一个 `targets/<name>/` | 对应 target 维护者 | 该 target `clean build` 与最小运行验证 |
| `common-internal` | `common/`，无接口变化 | Core Maintainer | common 测试 + 所有引用 common 的已支持 target build |
| `common-contract` | `common/` 接口、DTO、语义变化 | Core + 每个受影响 target 维护者 | 所有受影响 target build；行为改动应有测试 |
| `cross-target-feature` | common 加多个 target | Core + 每个改动 target 维护者 | 每个改动 target build；未实现 target 必须明确记录 |
| `build-or-ci` | 根 Gradle、wrapper、CI | Build / Release + 受影响 target 维护者 | 对应 JDK 的完整 job |
| `docs-only` | README、docs | 文档责任人 | 链接和命令检查 |

一个 PR 若同时修改 `common` 和一个 target，不应标记为 `target-only`。必须说明该 common 改动是否影响其他 target。

## 分支与提交

### 分支命名

```text
target/forge-1.20.1/<topic>
target/forge-1.19.2/<topic>
target/forge-1.18.2/<topic>
target/fabric-1.20.1/<topic>
target/fabric-1.21.1/<topic>
target/fabric-26.1/<topic>
target/neoforge-1.21.1/<topic>
target/neoforge-26.1/<topic>
common/<topic>
build/<topic>
docs/<topic>
```

### 提交前缀

```text
forge-1.20.1: fix entity renderer registration
forge-1.19.2: fix access transformer validation
forge-1.18.2: backport block entity sync
fabric-1.20.1: add client packet adapter
fabric-1.21.1: update item template adapter
fabric-26.1: migrate render pipeline
neoforge-1.21.1: update data generation
neoforge-26.1: migrate item template handling
common: expose render-plan hook
build: update neoforge-26.1 wrapper
docs: clarify release matrix
```

提交应尽量只覆盖一个责任边界。需要同步改动时，先提交 `common` 合约或 DTO，再提交各 target 适配；不要将不相关格式化混入功能改动。

## 开发流程

### target-only 改动

1. 只打开自己的 `targets/<name>/`。
2. 在该 Gradle 项目中编辑 target 和可见的 `common`。
3. 如果无需改 `common`，不要触碰其他 target。
4. 执行 target 自己的 `clean build`。
5. 在 PR 中说明 Minecraft 版本、加载器、验证 JDK 和实际测试场景。

### common 改动

1. 先写清楚新增或改变的语义，并确证它在各 loader 与 Minecraft 版本之间没有差异。
2. 为 shared logic 添加或更新共享测试（`common/src/test/`，可用 `gradlew -p common test` 运行）。
3. 由每个受影响 target 维护者更新桥接实现。
4. 对每个受影响 target 独立构建；不要把根项目的单一构建结果当作全矩阵证明。
5. 对不立即适配的 target，禁止静默合并。必须明确选择：同 PR 适配、feature flag 禁用、或在 issue/roadmap 中记录阻塞。

### 跨 target 新功能

推荐按以下提交顺序：

```text
1. common: 新增纯业务模型、状态与稳定接口
2. target A: 实现加载器 / Minecraft 桥接
3. target B: 实现加载器 / Minecraft 桥接
4. target C: 实现加载器 / Minecraft 桥接
5. docs/tests: 更新支持矩阵与验证记录
```

不能为“统一代码”而掩盖确证差异，也不能仅因为某段代码用了 `ItemStack`、`PoseStack`、`Holder`、`StreamCodec`、Fabric callback 或 Forge/NeoForge event 就拒绝把它放进 `common`；确证有差异的部分放进对应 target 并在其 `commonJava` 中 `exclude`。

## 渲染与客户端变化

渲染实现遵循根 AGENTS.md 的实际 API 差异规则：没有确证版本差异的实现放 `common`，平台注册与 Mixin 配置留在 target；不能只因为引用客户端/loader 类型就另建适配层。确证 buffer、shader、model 或事件时机差异后，再在目标通过 `commonJava` 排除共享文件并提供替代实现。

当前 NeoForge 1.21.1 的融合切片位于 `common/.../fusion/visual`，target 接入网络注册、客户端 tick 与渲染 Mixin。共享源码位置不代表其他 target 已兼容。客户端显示/渲染类只能在客户端执行路径加载，服务端同步不创建显示实例。

视觉回归使用 target 的 `src/test/resources/visual/`：在专用 `Fusion visual slices verification` 世界执行 `spawn.js`，客户端执行 `assert-client.js`；服务端 `change-state.js` 后客户端执行 `assert-changed.js`。另外检查默认值恢复、停止/重新追踪、资源重载及完整重启后的外观。必须从最终 jar 启动并通过 AutoJS 回读真实状态，同时检查实际画面；几何单元测试不能替代渲染验证。

首版只有高度切片，没有封口或骨骼语义识别。记录第三方渲染路径、实体事件及私有动画尚未覆盖，不能把原版组合成功当作所有模组兼容。不得通过在 `common` 和 target 中创建同名 class 来“覆盖”实现。

## 融合属性与掉落回归

NeoForge 1.21.1 的 `src/test/resources/fusion/` 脚本在新建的 `Fusion attributes loot verification` 世界、AutoJS server side 执行。顺序为 `attributes-loot.js`、`loot-edges.js`、`loot-age.js`；保存并完整退出 JVM，重新从最终 jar 启动同一世界后执行 `attribute-persistence.js`。主脚本会清理该测试区域掉落物及上轮带测试标记的实体，不在玩家世界执行。

检查连续融合与组合展平的等权平均、缺失属性、基础值与临时修改器、最大血量截断、默认掉落表与自定义表隔离、私有库存、重复物种、公共事件仅一次及取消后的真实世界物品数量、幼体条件。持久化脚本在重启后继续加入新成员，不能只检查重启前后的当前平均值；必须证明原始权重仍正确。新增 v2 的 AttributeOrigins 不代表能从 v1 旧存档恢复已经丢失的原始个体值。

## 验证矩阵

| Job | 目录 | Gradle JVM | 必须执行 |
| --- | --- | --- | --- |
| Forge 1.20.1 | `targets/forge-1.20.1` | JDK 21 | `./gradlew clean build` |
| Forge 1.19.2 | `targets/forge-1.19.2` | JDK 21 | `./gradlew clean build` |
| Forge 1.18.2 | `targets/forge-1.18.2` | JDK 21 | `./gradlew clean build` |
| Fabric 1.20.1 | `targets/fabric-1.20.1` | JDK 17 | `./gradlew clean build` |
| Fabric 1.21.1 | `targets/fabric-1.21.1` | JDK 21 | `./gradlew clean build` |
| Fabric 26.1 | `targets/fabric-26.1` | JDK 25 | `./gradlew clean build` |
| NeoForge 1.21.1 | `targets/neoforge-1.21.1` | JDK 21 | `./gradlew clean build` |
| NeoForge 26.1 | `targets/neoforge-26.1` | JDK 25 | `./gradlew clean build` |

补充规则：

- 根 `-PallTargets=true build` 自动扫描 `targets/`，按 `ci.enabled` 与当前 Gradle JVM 是否满足 `ci.java` 选择目标，并打印需要更高 JDK 的跳过项；不维护版本白名单。它不能代替被跳过目标的独立验证，也不能保证旧 wrapper 支持任意更高 JDK。
- 任何改动 `common/` 的 PR，至少触发所有当前支持 target 的 build job。
- 任何改动一个 target 的资源、Mixin 或 metadata 的 PR，至少检查最终 jar 是否包含对应 metadata、配置文件和 common class。
- 构建成功不代替运行验证。涉及事件、网络、Mixin、注册、渲染或数据包时，应在 PR 中记录 client、dedicated server、reload 或 data generation 的实际验证范围。

## Common 自动验证

`.github/workflows/verify-common.yml` 同时负责共享层和 target 的 CI 门禁。以下路径变更触发完整矩阵，手动运行也验证完整矩阵：

```text
common/**
build.gradle, settings.gradle, gradle.properties, gradle/**, gradlew, gradlew.bat
docs/en/**
scripts/build-target.ps1, scripts/discover-targets.ps1
targets/**/ci.properties
.github/workflows/verify-common.yml
```

仅修改 `targets/**`（不含 `ci.properties`）时，根据 git diff 只选择改动的已启用 target，覆盖源码、资源、构建脚本和 wrapper。重命名按删除加新增处理，验证两端目标。没有基准提交的首次 push 验证完整矩阵；diff 失败则 CI 失败，不能静默漏检。空目标矩阵跳过 target job，但仍执行 common 测试。若同时修改非 target 路径，保守地验证完整矩阵。

执行顺序：

1. `common-tests` 使用 JDK 21 执行 `common` 的 `test`。
2. 选中的 target job 并行执行各自 wrapper 的 `clean build`；共享层改动覆盖全部八个已启用 target。`build` 包含 target 和 common 的测试任务。
3. 每个 target 使用对应 JDK：`fabric-1.20.1` 使用 JDK 17；`fabric-26.1`、`neoforge-26.1` 使用 JDK 25；其余 target 使用 JDK 21。
4. 成功或失败时上传已生成的 `build/libs/*.jar`，便于检查发布物。
5. `Verify Builds` 汇总目标发现、common 测试和目标构建状态；只有验证成功或目标矩阵确实为空时才通过。

`scripts/build-target.ps1` 对 target build 最多重试三次，专门处理首次解析 mappings、NeoForm 或 Maven 时的短暂网络失败。编译错误、测试错误和持续依赖错误仍会使 job 失败。

分支保护可要求固定的 `Verify Builds` 汇总检查，不要把所有动态 target job 都列为必需检查，否则 target-only 改动会等待未选中的目标。工作流使用路径过滤，纯文档 PR 不触发；配置分支保护时需考虑这类 PR，不要要求一个不会产生的检查。

新增 target 按 `docs/CI_TARGET_DISCOVERY.md` 接入 `shared.gradle`；不要重复实现共享源码、测试或资源接入逻辑。target-only 的选择逻辑不得削弱共享层的全矩阵验证。

## PR 评审要求

每个 PR 必须包含：

1. 变更类别与受影响 target。
2. 是否修改 `common`，以及 common 合约是否变化。
3. 使用的 JDK、实际执行的构建命令和结果。
4. 运行时验证说明，或明确说明为何不适用。
5. 不支持/未验证 target 的影响说明。

评审者检查：

- 是否依据源码确证（而非猜测）来划分 `common` 与 target；确证有差异的文件应在对应 target 的 `commonJava` 中 `exclude`。
- 是否把 1.20.1、1.21.1、26.1 的资源、Mixin 或客户端代码混入同一 target。
- 是否更新了所有受影响的桥接实现。
- 是否因工具链不兼容而错误使用根聚合替代独立构建。
- 是否新增了重复 class、运行时版本判断或无说明的反射兼容层。

## 发布流程

### 发布单元

一个发布物只能对应一个 loader 与一个 Minecraft 版本：

```text
CannedMonster-forge-1.20.1-<version>.jar
CannedMonster-forge-1.19.2-<version>.jar
CannedMonster-forge-1.18.2-<version>.jar
CannedMonster-fabric-1.20.1-<version>.jar
CannedMonster-fabric-1.21.1-<version>.jar
CannedMonster-fabric-26.1-<version>.jar
CannedMonster-neoforge-1.21.1-<version>.jar
CannedMonster-neoforge-26.1-<version>.jar
```

不要发布混合 loader 的 universal jar。

### 发布候选

1. 冻结本次发布涉及的 `common` 合约。
2. 对每个承诺发布的 target 用其要求的 JDK 执行 `clean build`。
3. 检查 jar 名称、metadata、Mixin / access widener、common class 和版本范围。
4. 在干净的 client 与 dedicated server 环境做最小启动验证。
5. 发布说明按 target 列出新增、修复、已知限制和不包含的版本。

版本可以不同步发布。某个 target 未准备好时，不应阻止其他 target 发布，但发布说明必须准确表达覆盖范围。

## 支持、弃用与新增版本

### 新增 target

新增版本必须：

1. 新建 `targets/<loader>-<minecraft-version>/`，不在旧 target 添加运行时版本分支。
2. 提供独立 wrapper 与 `settings.gradle`；加载自身插件后应用 `../../gradle/target-conventions/shared.gradle`，直接接入 common 原源码、资源和测试。target settings 不导入独立 common build，避免 IDE 重复源码根；不复制共享源码到 build，不重复添加共享源码集，也不使用 common jar 或 `project(':common')` 依赖。
3. 指定责任人、最低 JDK、Gradle 版本、加载器版本和 CI job。
4. 完成独立构建、client / server 最小验证和最终 jar 检查后，才标记为支持。

### 弃用 target

弃用前应发布最后一个维护版本，并在 README/发布说明写明：

- 最后支持版本。
- 停止接收功能或修复的日期/条件。
- 是否只保留构建修复或完全冻结。
- 迁移目标版本。

弃用 target 不应被删除到无法重现历史发布；先从日常 CI 或支持矩阵移除，再按仓库归档策略处理。

## 例外处理

紧急安全修复可以缩短评审，但不能跳过：

- 所有受影响 target 的构建。
- 对 `common` 改动的影响说明。
- 发布后补充的回归测试或 issue 记录。

上游 Maven、Mappings 或工具链暂时不可用时，PR 可以合并为“结构已就绪、验证受外部阻塞”，但必须在 PR 和追踪 issue 中记录失败命令、失败时间、上游依赖和恢复条件。不得把未验证 target 标记为已支持。

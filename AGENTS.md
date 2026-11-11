# CannedMonster 开发规范

项目 mod id 为 cannedmonster，Java 包名 / Gradle group 为 cc.sighs.cannedmonster。功能与目标配置见 [README](README.md)。本工程已完成初始化；不得再次运行初始化脚本覆盖维护者编辑的文档。初始化不是构建、兼容性或运行行为验证，禁止编造已通过的测试和功能。

## 用户要求（原话）

需要的是通用、适配多模组环境的实现，不需要模块化，不需要穷举，使用尽可能底层的实现方式，允许使用任何非常规技术手段，允许出现先后顺序。

## 本地目录

- 主项目根目录为 `D:\work\CannedMonster`，不再嵌套 `cannedmonster/`。
- 本地 Minecraft 1.21.1 参考源码位于根目录 `build/minecraft-1.21.1/`；不属于生产源码集，也不提交 Git。原 `source-study/` 已清理。
- 运行单版本构建时在 `targets/neoforge-1.21.1/` 使用该 target 的 wrapper。该 target 的 `clean` 不删除根目录参考源码；手动清空根 `build/` 会删除参考源码，操作前需保留或准备重新生成。

## 罐头实现规范

- `common/.../can` 保存物品逻辑、内容 NBT、捕捉/释放和投掷实体。八种物品与实体的 NeoForge 注册、公共交互拦截和客户端渲染在 1.21.1 target；不要为特定生物另设捕捉/融合规则。
- 材质是用户原图按行优先切出的 8 个 16×16，最后一格空白不用。空间型只注册物品，未提供行为或配方；随机型无存储，击中方块生成成功才消耗。
- 普通型不得捕捉已有融合体，大型可保存。满罐融合时用 `FusionRuntime.prepend` 展平并维持罐内→目标原成员→目标身体顺序，失败不得清空罐头。
- 罐头交互在公共 PlayerInteractEvent.EntityInteract 中处理，以免悦灵等先拿走物品。投掷型返回 PASS 且取消生物交互以允许后续空气使用蓄力；不要重复触发玩家公共管线。
- 内容在 DataComponents.CUSTOM_DATA 的 `cannedmonster:contents` 下，带版本、显示名称和完整实体 NBT。捕捉前校验 256KiB、深度和 UTF 长度，准备好内容后才移除源；无死亡掉落/分裂。释放被碰撞/生成事件阻止时保留数据；已加载重复 UUID 拒绝重放。
- 空间、随机、铁制材料罐不得储存生物；功能罐头 max stack=1，投掷扣除也适用于创造模式。配方只使用铁制材料罐，不吃满功能罐。
- 罐头测试位于 target `src/test/resources/cans/`，必须验证真实投掷物 tick/碰撞，不只直接调用 onHit；跨重启测试保存玩家物品栏中的融合大罐头并验证成员顺序。
- 如果掉落物被生成事件拒绝，ThrownCan 必须保留动作后的物品/实体内容，停止移动并定期重试，ReturningCan 状态与物品一起保存。只有物品成功登记（或随机生成成功消耗）才移除投掷实体，防止多模组事件取消导致生物内容消失。

## 当前实现范围

- 当前只开发 NeoForge 1.21.1；其他 target 是初始化后的模板，不代表已经移植融合功能。共享 MC 代码目前只在该 target 验证。
- `common` 的 `fusion` 包保存原生实例调度与身体绑定；不得添加按实体 ID 分支的特性配方。
- NeoForge 1.21.1 的 `src/bootstrap` 通过 `ICoreMod` 扫描类并重定向身体字段，作为嵌套 LIBRARY jar 加载。开发运行不得再把同一个 bootstrap jar 加进额外运行类路径，否则会发生模块重名。
- `runClient -PverifyPackagedMod` 禁用源码模组并从构建 jar 加载，使用 ModDev 的 `RunGameTask.classpathProvider` 排除开发输出。不能只修改 JavaExec.classpath，任务执行时会再次追加 provider，造成重复包与 Mixin 类不可见。该验证入口依赖当前固定的 ModDev 版本，升级插件需重新核对。
- 后置成员是唯一入世界的身体、主要运动执行者和当前客户端外观；前置成员保留真实类型和独立物种状态。当前尚不承诺完整伤害回调合成、世界身份映射、物种数据客户端同步或任意重写 tick 的兼容性。
- 不再维护身体字段名白名单：引擎三层实例字段默认归身体，AI/身份描述对象按类型保留成员私有，运动输入与登记标志按引擎方法访问集合推导。子类字段默认私有；这不是任意模组状态的语义推断。
- Entity/SynchedEntityData 注入 BoundObject 槽，字段读写走实例绑定快路径；禁止重新引入每次字段访问的全局同步查表或统一删除 final。
- OrderedCalls 在 tick/aiStep 的真实 super 边界续接成员，身体公共执行一次并反向返回后置代码。禁止从事件外部固定补调三个生命周期方法、按整个 tick 的方法名去重或执行后恢复速度。运动排他权在公共执行入口实施。
- 变换前扫描只能覆盖已存在的类布局；Mixin 后加字段、反射/MethodHandle/native 访问以及外部身份边界仍须明确标记为未覆盖，不能以基础测试替代多模组验证。
- 停止追踪不等于移除实体：不得在每个 EntityLeaveLevelEvent 无条件解绑。真实 setRemoved 后清理绑定，服务器停止后清理剩余运行时引用，避免在区块保存前丢掉融合数据。
- NeoForge 的 AutoJS 依赖通过开发 `runtimeClasspath` 接入，不能只放 `additionalRuntimeClasspathConfiguration`：后者在本目标实测未被识别为游戏模组。依赖不进入编译类路径或发布包。

## 已复现的兼容性缺口

- `src/test/resources/compatibility/`（NeoForge 1.21.1 target）保留 AutoJS 扩测脚本，不属于生产特性配方。matrix 使用原生对照、物种→牛、牛→物种，运行 200 个真实 tick；重跑必须隔离旧观察器，失焦暂停不能计为通过。
- 公共 Player.interactOn 只对身体执行一次，不得递归重放公共交互事件。实体阶段通过 mobInteract 分派成员；物品阶段通过 ItemStack.interactLivingEntity 分派成员，全部实体行为优先于全部物品行为。公共事件目标是身体，取消会阻止后续分派。未知成员/物品 PASS 前的私有副作用不具备自动回滚。
- 四类原失败通过通用入口修复：内部交互视图、TargetingConditions/Level 同身体排除、Projectile/DamageSource 身份规范化，以及 remove 分派。生产实现不得引入羊、河豚、雪傀儡、史莱姆 ID/类特判。
- 移除期间保持绑定，整个分派结束再清理。派生必须同时满足 CreationOrigins 数据流证明工厂接收值来自当前 this.getType()、KILLED 帧内来源匹配及 CreationTickets 一次性对象身份凭据；仅同类型不足以自动继承。混合来源拒绝，辅助/反射/异步工厂未覆盖。此来源规则不等于自动理解业务父子关系。
- 派生只保留原生新子体的状态并替换来源成员；其他成员按类型重新创建，不复制父体 NBT、装备、库存、变种和个体记忆。这是有意的保守规则，组成与顺序保留但个体状态不承诺继承。正常存档加载不受此规则影响。
- `public-interaction.js` 验证4次玩家动作仅4次公共事件/经验副作用，覆盖 PASS、染色、取消和剪毛；`no-resource-copy.js` 验证携12钻石的父体不向多个新身体复制主手物品。
- `assert-regression.js` 校验矩阵及四类失败场景；`derived-regression.js` / `assert-derived.js` 验证二次分裂，`verify-derived-persistence.js` 验证重启后的两个派生体。清理测试尸体前检查 isRemoved，避免原版 Slime.remove 对已死亡实体重复调用产生额外普通子体。
- 当前只修复 remove 调用链，并非所有 hurt/die 回调、所有第三方事件或客户端类型视图；外部按原始 Java 引用比较/强转仍不保证融合透明。
- 监守者原生对照也在初始化后的 dig 活动移除，不能单凭消失归因于融合。完整重启的 8 个代表性组合（村民、悦灵、守卫者、青蛙与牛正反）恢复通过；未覆盖实际第三方生物模组。

## 架构与修改边界

- 默认将没有确证 loader / Minecraft 版本差异的代码与资源放在 `common/`，允许引用 Minecraft 和 loader 类型；平台类型依赖本身不是拆分理由。
- 只有阅读实际源码/API 确证差异后，才将相应实现移到 target；差异记录在 target 的 `commonJava { exclude 'path/to/File.java' }`，只排除共享来源文件，不排除 target 的替代实现。
- `shared.gradle` 直接将 common 原文件接入 target 源码集。禁止复制源码到 build、依赖 common jar、重复添加 common 源码根或使用 `project(':common')`。
- 不以运行时版本判断、反射分发或 classpath 同名类覆盖隐藏兼容性问题。每个产物只对应一个 loader 和 Minecraft 版本，不发布 universal jar。
- 单 target 改动限定在该目标；common 改动需验证所有受影响目标。不得顺手修改其他版本或未经验证统一 API。
- 共享资源放 common；metadata、Mixin 配置、access widener / transformer 和版本专属资源放 target。同路径资源明确唯一归属，不依赖覆盖顺序。
- 共用模组元数据放根 `gradle.properties`，版本、loader、JDK 和 `debug_*` 放 target 属性。普通本地 jar 放目标 `libs/`，不假定存在传递依赖。
- 插件和运行配置归 target；通用源码接入、热调试、发布逻辑分别复用 `gradle/target-conventions/shared.gradle`、`hot-debug.gradle`、`publish.gradle`。

## IDE 与构建

- 单版本开发独立打开目标目录，使用其 wrapper 与 `ci.properties` 指定的 JDK。
- 全版本浏览打开 `idea-workspace/`，当前配置主版本为 neoforge-1.21.1（JDK 21）；修改主版本以工作区属性为准。主模块唯一管理 common，浏览模块覆盖文档、根配置、脚本和其他 target，不承诺非主版本 API 补全。
- 不额外关联独立 common、根构建或其他完整 target；浏览根排除主 target、common/src、缓存及运行产物。common 内容修改应直接反映到目标索引，构建配置与排除规则修改后重新加载 Gradle。
- 工作区 composite build 共用工作区 Gradle/JVM，不使用被包含目标的 wrapper；切换前检查兼容性，非主版本用独立 wrapper 验证。
- 根和 CI 自动扫描 targets，禁止维护固定版本名单。`ci.enabled` 是验证配置，不是兼容性证据。新增目标遵循 [自动发现指南](docs/CI_TARGET_DISCOVERY.md)。
- 标准验证：target 目录执行 `clean build`；共享层可执行根 `./gradlew -p common build`，但单独 common 成功不代表目标矩阵通过。产物需检查 metadata、共享类、资源及版本范围。

## 必须使用 AutoJS 热调试

- 游戏运行时调试与行为验证必须用 AutoJS：先 `verifyHotDebug` 解析依赖，再启动实例，通过 `status` 确认可用 side 和模式，用 `eval_js` 触发行为并回读真实状态、执行可重复断言。
- 每次检查 `isError`、`ok`、`diagnostics`；不能以无异常、构建成功或静态推测代替运行验证。记录 target、引擎、side、脚本和实际返回结果。
- server side 负责权威世界状态；client side 负责 UI/输入。需要后台调试时使用 `AutoJS.background(true)`，结束后恢复。
- 修改脚本使用 `reload_scripts`；有 KubeJS 时配合 `kubejs_log`，仅 Rhino 时重建作用域后重新注入脚本，不假定存在 KubeJS 事件。配方、标签、战利品表按需另做 `/reload`。
- Java/Mixin 改动和新增启动期注册内容需重新构建、重启，再用 AutoJS 验证；脚本重载不是 Java 热替换。
- AutoJS、Rhino、可用的 KubeJS 仅进入开发游戏运行配置，不进入 common 编译依赖、发布 jar 或玩家必需 metadata。Forge 生产 jar 使用重映射配置并显式补齐依赖，不直接加入普通 runtimeOnly。
- 默认端点 `http://127.0.0.1:25585/mcp`，`/health` 探活；配置在游戏目录 `config/autojs.json`，多实例使用 `AUTOJS_PORT`。从 AutoJS jar 提取 `SKILL.md` 查详细用法，不依赖维护者机器上的绝对路径。
- 任意代码执行端点只供本机开发，禁止暴露公网或玩家生产环境；共享环境设置 token，不提交凭据，不操作无关世界/文件。首次 Minecraft EULA 必须由用户阅读并接受。
- 仓库不可访问、依赖缺失、游戏或端点不能启动时记录具体阻塞，不能静默改用本地替代物或伪称验证完成。

## 发布、文档与交付

- 发布前逐目标完成构建与对应 client/server 行为验证，检查最终 jar；发布平台 ID 和凭据按 [发布指南](docs/PUBLISHING.md) 配置，token 只用环境变量。
- `mod_license` 元数据声明不自动替换 LICENSE 正文，维护者需核对正文、依赖和素材授权。不凭许可证参数声称完成法律授权检查。
- 修改功能、支持范围、依赖、工具链或工作区配置后同步 README、AGENTS.md 与相关 docs；文档归维护者管理，不自动重写整个文件。
- 不提交密钥、IDE 缓存、运行世界和构建输出。交付说明区分已验证、未验证和阻塞；不要将模板历史测试作为本项目的验证记录。
- 日常维护与迁移流程见 [维护指南](docs/MAINTENANCE_WORKFLOW.md)；版本/API 差异见 [迁移参考](docs/version-differences/README.md)。

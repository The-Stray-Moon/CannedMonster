# CannedMonster

Ordered native entity fusion

- mod id：cannedmonster
- Java 包名 / Gradle group：cc.sighs.cannedmonster
- 作者：CannedMonster
- 声明的许可证：MIT

## 本地目录

主项目已直接放在 `D:\work\CannedMonster`，不再有外层 `cannedmonster/` 子目录。Git 仓库和未提交修改一并保留。

- Minecraft 1.21.1 参考源码：`build/minecraft-1.21.1/`，仅供阅读，不参与模组编译，不提交 Git。
- NeoForge 1.21.1 工程：`targets/neoforge-1.21.1/`。
- IDE：单版本打开上述 target，或打开 `idea-workspace/` 使用全版本浏览模式；旧路径打开的 IDEA 工程需要重新打开新路径并刷新 Gradle。

原 `source-study/` 中的其他参考仓库、旧模板和缓存已删除。参考源码位于构建目录，手动清空根 `build/` 时也会删除；在 target 目录执行 `clean build` 不影响这份根目录参考源码。

## 怪物罐头玩法（NeoForge 1.21.1）

八个物品按提供的 48×48 九宫格原图顺序切成原生 16×16 材质，已加入创造模式“工具与实用物品”栏，中英文名称齐全。

| 名称 | ID（前缀 cannedmonster:） | 行为 |
| --- | --- | --- |
| 铁罐头 | iron_can | 普通罐头的合成材料 |
| 怪物罐头 | monster_can | 空罐右键生物捕捉；满罐右键方块释放，右键生物融合；不能捕捉融合生物 |
| 投掷型怪物罐头 | throwable_monster_can | 只支持蓄力投掷，不进行直接生物/方块交互；普通存储限制 |
| 铁大罐头 | large_iron_can | 大型和随机罐头的合成材料 |
| 怪物大罐头 | large_monster_can | 同手持普通型，可捕捉/储存融合生物 |
| 投掷型怪物大罐头 | throwable_large_monster_can | 投掷型，可捕捉/储存融合生物 |
| 空间型怪物罐头 | spatial_monster_can | 仅注册物品与材质，暂无功能、无配方 |
| 随机型怪物罐头 | random_monster_can | 特殊投掷型，不储存生物；击中方块随机生成一个生物，成功后消耗 |

### 使用与默认规则

- 只有 Mob（含可正常创建的模组生物）参与捕捉和融合，不处理玩家、物品、装饰实体。死亡、骑乘、携带乘客或被拴住的生物暂不捕捉，避免丢失关联实体。
- 功能罐头不可堆叠，空/满状态保留同一物品，满罐有附魔光效和内容名称提示。正常捕捉保存原实体类型、UUID 和持久化 NBT，直接移出世界，不触发死亡掉落/分裂。释放位置被阻挡或世界拒绝生成时保留内容，不凭空清空。
- 融合顺序：罐内成员（含其身体）→目标原有成员→目标身体。自动展平，不建立嵌套融合；目标仍是原来的世界身体。成功后罐头变空。普通型可以对已有融合体继续融合，但不能把融合体捕入罐内；大型可以。
- 投掷按住约 1 秒满蓄力，松开投出；不足最低蓄力不消耗。命中生物时空罐捕捉、满罐融合。命中方块时满罐尝试释放；其他情况落为可拾取的原罐头。随机型命中生物不捕捉，命中方块生成成功才消耗；失败落回可拾取物品。
- 投掷成功生成实体后始终扣除一个罐头，包括创造模式，防止满罐随投掷复制。返回的普通/大型投掷罐可再拾取。投掷物使用原生同步与实体存档，飞行超时 1200 tick 尝试落为物品。若其他模组取消罐头掉落物生成，投掷实体会保留最新内容、停止运动并每 20 tick 重试，等待状态也会存档；不会因一次被拒绝就销毁内容。罐头作为物品仍受原版掉落、火焰、虚空及物品清理规则影响。
- 随机型从注册表可召唤且非 MISC 的实体类型候选中随机选取，实际创建必须是 Mob；最多尝试 16 个候选并检查碰撞，不按生物 ID 穷举。沿用原生创建/初始化与 NeoForge 生成事件，不承诺所有模组构造器安全；可能生成危险生物。
- 为控制存档/网络数据，捕捉前验证 NBT 大小（保守内存估计及写出均不超过 256 KiB）、嵌套深度 64 和字符串 Modified UTF-8 长度。数据过大拒绝操作，不删除生物。不支持保存原模组根本未写入 NBT 的瞬态状态。

### 当前配方（默认材料，可调整）

用户原描述的重复“投掷型”按重复文字处理：普通铁罐制作普通/投掷型；铁大罐制作大型/大型投掷/随机型，空间型暂不合成。所有配方只消耗空的铁制材料罐，不转换装有实体的功能罐，避免合成吞掉内容。

- 铁罐头：5 个铁粒，3×3 摆为 `I I / I I /  I `。
- 铁大罐头：同形状，材料为铁锭。
- 怪物罐头：铁罐头＋红石。
- 投掷型怪物罐头：铁罐头＋红石＋火药。
- 怪物大罐头：铁大罐头＋红石。
- 投掷型怪物大罐头：铁大罐头＋红石＋火药。
- 随机型怪物罐头：铁大罐头＋末影珍珠＋火药。

### 罐头验证记录

成品 jar 在独立 `Monster cans verification` 世界通过 AutoJS 的 client/server 两端验证，未使用生物 ID 特判实现功能；脚本保留在 target 的 `src/test/resources/cans/`。

- `hand-regression.js`：41 项检查，包括八物品注册、配方加载、捕捉、名字/血量保留、物品序列化、方块释放、融合、普通型拒绝储存融合体、大型保存/释放、已有融合体展平、悦灵不吞罐头、阻挡释放保留、投掷型不直接捕捉和空间型占位。最终 jar 复跑通过。
- `throw-regression.js`：7 段真实蓄力和飞行碰撞、50 项检查，通过普通投掷捕捉/融合、不能捕捉融合体、大型投掷捕捉/方块释放融合体、随机方块生成和空投返还。每段实际使用 25 tick 后松开，不直接调用 onHit。随机生成的生物在下一空投测试前移除，避免把正常捕捉误判成“空投失败”。
- 客户端额外通过实际 keyUse 按住/松开，经网络产生投掷物，服务器回读目标移除、手中扣除且仅一个满罐头掉落。八个实际 BakedModel 均解析到对应罐头纹理，不是缺失贴图。
- `recipe-regression.js`：七个配方均以真实 3×3 CraftingInput 匹配 RecipeManager，assemble 返回正确物品。空间型无配方。
- `persistence-regression.js`：玩家物品栏的满大罐头跨完整退出 JVM/重启后，仍能释放猪身体和按序的牛、兔子、羊三个成员，再次捕捉成功，成员没有独立入世界。
- `edge-regression.js`：超容量数据拒绝且不移除原实体、乘客关联拒绝、短蓄力不消耗、非法载荷保留、库存内容保留五项通过。捕捉前额外检查 NBT 字符串 UTF 长度，避免底层写出把超长字符串替换为空造成数据损失。
- `failure-regression.js`：通过真实 EntityJoinLevelEvent 取消实体释放/罐头掉落物生成。释放失败仍满罐，解除后释放成功；掉落被拒绝时满罐仍保存在投掷实体中、等待状态序列化往返保留，解除后只返还一个满罐头。

## 项目状态

当前是 **NeoForge 1.21.1 实验原型**，不是完成了任意模组兼容性的正式版本。只有该 target 接入融合运行时；其他 target 保留模板，不能作为可用移植版本发布。

管理员命令：

```mcfunction
/fusion spawn minecraft:zombie minecraft:witch
/fusion spawn minecraft:cow minecraft:rabbit
```

参数有顺序：前置成员先执行，后置成员是唯一加入世界的身体，并提供主要运动执行链；客户端按成员顺序组合高度切片外观。命令接受实体注册表中的 Mob 类型，不按物种 ID 编写特性组合。前置成员保留真实 Java 类型、控制器和私有字段；类加载期 CoreMod 自动扫描 Minecraft 与模组类，把选定公共身体字段的访问重定向到后置身体。反射读取原始字段不在此重定向范围内。

已实现的内容：共享火焰、血量、效果、装备等状态；前置原生行为执行；物种交互按顺序消费；成员存档与恢复；属性基础值平均及全成员物品掉落。没有注册新的融合实体类型，客户端复用成员原生渲染器进行空间切片组合。

### 属性与死亡掉落

- 每种 attribute 的**基础值**按拥有该属性的成员取算术平均，缺失不计零，同种重复成员分别计入。首次融合记录原始个体值，连续融合或两个融合体合并时按展开后的所有个体等权计算，不把旧平均值当作一个成员。身体原有的装备/药水等修改器保持一次，前置成员的修改器不叠加，也不永久烘焙入基础值；后续修改器仍按原生机制增减。
- 平均值在融合和存档恢复时应用，不每 tick 强制覆盖原生行为对属性的修改。版本 2 融合 NBT 保存有序 `AttributeOrigins`；后续重算仍以这些原始快照为准。最大生命值降低时截断超出上限的当前血量，上升时不自动回血。
- 旧版本 1 存档仍可加载，但旧存档可能已将成员属性写成共享身体值，没有原始快照，无法逆推出丢失的个体数值；迁移以能加载出的数值为起点。新版本保存后不会再发生平均值重复加权。
- 死亡时在身体的一次公共掉落流程内，分别执行各成员的原生战利品表、自定义死亡掉落和库存掉落。各自保留战利品表/随机 seed，重复类型也分别结算；羊毛颜色等条件使用对应成员状态。不是把表内每个条目变成必掉，仍遵守概率、抢夺及原生条件。
- `shouldDropLoot` 按成员分别判断，幼体不因融合获得成年掉落，幼体身体也不阻止成年成员掉落。`doMobLoot` 保留引擎原有作用范围，库存掉落入口仍在该条件之外；共享装备概率逻辑只在身体执行一次。原生库存回调照常消耗自己的库存/共享手持物品。经验维持身体一次，不求和。
- 正常经 `spawnAtLocation` 捕获的成员掉落汇总到身体的一次 `LivingDropsEvent`，取消事件阻止整批生成。不递归重放所有成员的 `die` 或公共死亡事件。未知模组绕过标准入口、直接世界生成或复制共享物品且不消耗原物品的副作用尚无通用事务保证。

验证记录：NeoForge 21.1.234 / JDK 21，`clean build verifyHotDebug` 通过，40 项单元测试零失败（新增 5 项平均值与原始快照测试）。在新建的 `Fusion attributes loot verification` 世界从成品 jar 运行 AutoJS 1.0.1 / KubeJS 2101.7.2-build.379，server side 顺序执行 `src/test/resources/fusion/attributes-loot.js`、`loot-edges.js`、`loot-age.js`，累计 49 项断言通过；包括连续/双融合展平、缺失属性、血量截断、修改器增减、三成员与同种重复掉落、公共事件一次及取消后的世界物品数、独立自定义表/seed、两个悦灵分别掉出 3 绿宝石与 5 金锭而共享 12 钻石只掉一次、幼体正反与普通牛对照。完整 JVM 重启后 `attribute-persistence.js` 3 项通过，六成员均值 340/6 保留，再加基础值 80 的成员得到七成员均值 60；客户端回读基础属性与服务端一致。上述结果不代表第三方自定义死亡管线或独立服务器已经验证。

### 视觉融合（NeoForge 1.21.1）

- 按融合顺序从上到下划分等高区域，最后一段属于身体；各模型按身体宽高对齐。两成员表现为上/下拼接，多成员增加中间切片，不按生物 ID 或骨骼名字穷举。
- 捕获原生渲染器向 `MultiBufferSource` 输出的四边形/三角形，真正裁剪跨界面并插值 UV、颜色、光照和法线。保留原材质和渲染层，不只是叠加完整模型或丢弃越界顶点。
- 服务端在开始追踪及每 2 tick 同步各成员完整 `SynchedEntityData`，包括恢复到默认值的状态，不消耗原生 dirty 标记。客户端创建不入世界的显示实例，保持真实类型；运动姿态、行走进度、装备来自身体，物种同步字段独立。名字/拴绳、阴影/火焰只执行身体的公共入口。
- 不改变碰撞箱、命中或运动规则。剔除范围按身体尺寸保守外扩；这不是任意模型实际几何范围的精确计算。
- **边界**：目前不封闭切口、不自动识别头/腿/翅膀，不保证不同体型间没有接缝和拉伸。仅覆盖标准缓冲路径的 QUADS/TRIANGLES，其他图元会警告并省略；绕过接口的自定义绘制、光影模组、第三方生物尚未验证。显示实例不执行 tick，依赖私有 tick 计数、实体事件或模组私有网络包的动画未自动同步；这不是任意动画系统兼容层。网络与逐帧几何分配尚未经过大型群体性能测试。

视觉验证：NeoForge 21.1.234 / JDK 21，`clean build verifyHotDebug` 通过，35 项单元测试零失败（新增 8 项裁剪、插值与面积守恒测试）。使用 `runClient -PverifyPackagedMod` 从最终 jar 启动，在新建的 `Fusion visual slices verification` 世界，通过 AutoJS 1.0.1 / KubeJS 2101.7.2-build.379 的 server/client 两端执行：

- `src/test/resources/visual/spawn.js` 创建红羊→牛、蓝羊→猪→牛、红羊→蓝羊；实际画面检查确认两段/三段切片及同类成员颜色隔离。
- `assert-client.js` 检查成员数量、不入世界、颜色隔离和真实世界渲染的非空几何输出，每次 20～21 项断言通过（末次绘制为两段或三段会改变数量）。完整退出 JVM、重新启动最终 jar 后，三组仍恢复并通过。
- `change-state.js` / `assert-changed.js` 检查绿色与剪毛更新，3 项通过；另实际验证恢复白色默认值及未剪毛状态，不残留旧数据。
- 玩家离开 1000 格后，身体停止追踪、显示缓存清除；返回后三组重新同步，断言通过。客户端资源重载完成后再次通过。每次检查 AutoJS 的 `ok`、`isError`、`diagnostics`；这不是第三方模组或独立服务器验证。

### 无物种穷举的执行规则

- 已删除 `SHARED` 字段名白名单。自动发现 Entity / LivingEntity / Mob 的实例字段，按默认身体归属生成访问桥接；物种子类的字段默认保留在成员本身。引擎的实体类型、同步数据、随机数、Brain、GoalSelector、控制器、导航与感知对象按类型保持成员私有。运动输入字段由引擎输入方法的写集合推导；世界登记标志由 `isAddedToLevel()` 的读取推导，不依赖具体字段名字。这是显式引擎协议，不是自动推断任意字段的业务含义。
- 为 Entity 和 SynchedEntityData 注入绑定槽，身体字段访问不再查询全局同步映射。普通实例槽为空，直接访问自身。保留原有 final 字段约束，不再统一移除 final。
- 不再由 EntityTickEvent 补调 tick、aiStep、serverAiStep。自动为 Mob 子类已有的 tick/aiStep 实现安装入口，在原 super 到达 Mob 时续接下一个成员，最终由身体执行公共部分，再反向返回各成员后置代码。没有调用 super 的成员返回后才进入下一个成员；这是确定的顺序规则，不是凭空还原它没有调用的父类代码。
- aiStep 分派由原生 tick 实际触发；不同调用仍独立处理，不做全 tick 方法名去重。成员的 AI 在该成员实际到达 aiStep 的公共边界时执行。线程局部调用帧在异常退出时清理。
- 已删除速度快照及回滚。前置成员不执行公共导航/控制器 tick，公共 move/travel/jumpFromGround 阻止成员提交第二份物理；其他原生代码产生的速度变化不再事后抹掉。身体继续使用自己的完整运动链。
- 类目标按继承关系和实际字段访问筛选，继承字段声明解析缓存；没有实体 ID、物种组合或能力配方表。

- 身份边界：TargetingConditions 自排除与 Level 的排除实体查询识别统一身体；Projectile.setOwner 和 DamageSource 构造时把成员转换为身体，不改写全局引用比较或强转。
- 玩家交互：原生 Player.interactOn 与 NeoForge 公共事件只对身体执行一次，不再递归重放整条玩家管线。身体的公共实体处理完成后，在 mobInteract 内按成员顺序尝试实体行为；若所有实体行为 PASS，再在原生物品阶段按成员顺序尝试 ItemStack.interactLivingEntity，最后回退身体，非 PASS 即停止。公共消耗/事件收尾保留在原位置。顺序是“全部实体行为，再全部物品行为”，不是每个成员重放整个动作。未知物品/成员自身在 PASS 前产生副作用仍可能按视图发生多次，不能等同于任意代码的事务回滚。
- 移除与派生：remove 的公共身体部分只执行一次，绑定延迟到整次移除结束后清理。CreationOrigins 用 ASM 数据流识别当前成员 getType() 返回值传入 EntityType.create(Level) 的调用（支持局部别名，混合来源则拒绝），在该调用返回时记录对象身份与源成员的一次性凭据。只有当前 KILLED 移除帧内、相同来源且凭据尚未消费的新 Mob，才会继承成员组成；仅同类型或仅处于死亡调用栈不再足够。这证明的是限定工厂来源，不证明业务上必然为后代；辅助方法、反射、异步和其他工厂目前不自动继承。
- 派生状态规则变更：新子体保留原生生成代码已设置的状态，替换其来源成员；其他成员只按原类型新建，不复制死亡父体 NBT。组成、顺序和身体选择保留，但这些新建成员的变种、库存、装备、模组资源和个体记忆不继承。这避免通过复制未知状态制造多份资源；原生子体代码自行赋予的资源仍由原模组负责。新身体使用子体位置和速度，正常存档加载仍恢复完整已保存状态。

**尚未完成**：完整世界身份与所有事件来源映射、任意 hurt/die 回调合成、前置物种的私有网络包/实体事件/依赖 tick 的动画同步，以及大规模模组兼容测试。当前覆盖 remove，不覆盖直接调用 setRemoved 而绕过 remove 的物种回调；异步派生、改变 EntityType 的派生及任意变身不在该继承协议内。原始 class 预扫描不保证覆盖其他变换器随后增加的字段；反射、VarHandle、字段 MethodHandle、native 访问及绕过公共运动入口的直接位置写入尚未统一接管。共享基类中的额外模组状态采用默认身体归属，不能自动证明其语义正确。多个成员共有的物种中间父类仍按成员执行。这不是任意方法的通用解释器，不应宣称任意模组都已兼容。

### 已执行的验证

NeoForge 21.1.234 / Minecraft 1.21.1 / JDK 21；开发客户端加载 AutoJS 1.0.1、KubeJS 2101.7.2-build.379 和 Rhino 2101.2.7-build.81。在新建的 `CannedMonster verification` 测试世界中执行，未操作已有世界。

- 字节码测试实际加载并调用变换后的模拟类，检查继承字段共享、同名字段隔离、原生具体类型保留、普通实例不受绑定影响、附属公共 tick 被跳过，以及解绑。
- AutoJS `status` 确认 client/server 两端可用后，在 server side 通过上述两条命令生成融合体。
- 日晒场景：固定中午、晴天，在无遮挡石台上生成僵尸 → 女巫。回读 `witch.isOnFire()` 为 true，`witch.hasEffect(MobEffects.FIRE_RESISTANCE)` 为 true，血量 26，期间未用脚本点火或添加抗火效果。
- 挤奶场景：生存模式手持空桶，执行 `player.interactOn(rabbit, InteractionHand.MAIN_HAND)`，返回 CONSUME，实际手持物变为 `minecraft:milk_bucket`。这验证了服务端交互入口，尚未自动化验证客户端鼠标到服务端的完整发包路径。
- 跳跃场景：用 `ServerEvents.tick` 观察 100 tick，每 10 tick 请求一次 `rabbit.getNavigation().moveTo(...)`。观测到 18 tick 的正向跳跃速度大于 0.05，最大上升速度 0.21560001587867758；身体 `tickCount` 只增加 100。
- 存档场景：先验证 `rabbit.saveWithoutId(new CompoundTag())` 后创建新 Rabbit 并 `load(saved)` 能恢复牛成员；随后从最终 jar 启动、生成并保存测试实体、完整退出 JVM，再次从同一 jar 启动并进入存档。兔子和女巫均恢复一个成员，重新挤奶得到奶桶，女巫仍燃烧且有抗火效果。跨维度尚未验证。
- 重启验证发现并修复了停止追踪事件过早解绑的问题：EntityLeaveLevelEvent 不总意味着实体被移除，现在真实 setRemoved 后才清理绑定。
- 最终 jar 包含 NeoForge metadata、Mixin 配置与嵌套 bootstrap LIBRARY；不包含 AutoJS、KubeJS 或 Rhino。当前 27 项单元测试全部通过，包括未知子类真实字节码执行、未列名引擎字段、final 保留、任意命名的运动输入/登记标志推导、前后置续接顺序、不调用 super 的成员、独立重复调用与异常后恢复。成品运行使用 `runClient -PverifyPackagedMod`，排除开发类目录。
- 续接重构在新建的 `Fusion continuation verification` 世界重新验证：从 jar 生成两组实体，退出 JVM 再进入后仍恢复成员、能挤奶且日晒下有抗火。最终版本 100 tick 观察中身体增加 100 tick，18 tick 上升速度大于 0.05。
- 运动归属断言：成员 `setDeltaMovement(new Vec3(0.1,0.4,0.2))` 后身体读取相同速度；成员 `travel(new Vec3(1,0,0))` 不改变身体位置。Rhino 的字段/方法重名包装必须用 `Number(vector.y)` 转换后比较，不能用严格等号直接比较包装对象。
- 新增三成员运行验证：创建 Sheep、Pig、Fox，调用 `FusionRuntime.attach(fox, List.of(sheep,pig))`，仅将 Fox 加入世界。回读 Fox 存活并推进 883 tick，两个成员保留，世界查询不包含 Sheep/Pig，两成员 `isAddedToLevel()` 均为 false。没有为该组合新增代码。实际第三方生物模组尚未进行广泛验证。

### 代表性生物兼容性扩测

使用相同成品 jar、NeoForge 21.1.234 / 1.21.1、AutoJS 的 KubeJS server side，在独立 `Fusion compatibility matrix` 世界测试。脚本保留在 `targets/neoforge-1.21.1/src/test/resources/compatibility/`：先运行 `matrix.js`，等待 `matrixElapsed === 200`，再运行 `specials.js` / `boundaries.js`。重跑矩阵使用 generation 隔离旧 tick 观察器；客户端关闭失焦暂停，不能把暂停期间的零 tick 当作通过。

选择 18 种生物：村民、悦灵、蜜蜂、史莱姆、岩浆怪、守卫者、美西螈、青蛙、末影人、苦力怕、羊、潜影贝、恶魂、幻翼、监守者、雪傀儡、海豚、河豚。每种各测原生对照、物种→牛、牛→物种，共 54 个实例；水生组在水笼内，其他组在隔离玻璃笼内，夜间、关闭自然刷怪与地形破坏。该测试不包含第三方生物模组。

- 修复前的有效单观察器轮次中，50 个未自然移除/死亡的实例均推进 200 tick，前置成员均未登记进世界，序列化往返恢复预期成员数量，无 Java 异常。监守者三组均在 104 tick 掘地移除，河豚→牛在 62 tick 死亡。这些不计为完整兼容通过。
- 飞行/跳跃对照：蜜蜂、悦灵作为身体时有上升移动；史莱姆、岩浆怪作为身体时有跳跃。前置运动受当前后置主运动规则限制，不将不飞行算作异常。恶魂/幻翼在小笼内的自然飞行覆盖不足；未测完整战斗、蜂巢/授粉、青蛙吞食或潜影贝攻击。
- 初次扩测曾确认四类失败：羊→牛的物品染色/剪毛未分派；河豚→牛自伤/中毒；雪傀儡→牛的投射物 owner 是未登记成员；史莱姆→牛不分裂且牛→史莱姆子体丢成员。以下为修复后的实际回归结果，而非对初测失败的忽略。
- **已修复：染色/剪毛**。原生、羊→牛、牛→羊三组，红染料与剪刀均 CONSUME，成员变红并正确剪毛。通过完整玩家交互管线分派修复，不检查 Sheep 类型或 ID。
- **已修复：自身伤害与来源身份**。河豚→牛强制满膨胀后 100 tick 保持 10 血、无毒、无自身攻击者，其他两组保持 3 血；18 种/54 实例矩阵重跑时仅监守者三组自然掘地，51 个存活实例均完整推进 200 tick 并保存恢复。另断言 Level 排除查询不返回同身体、DamageSource 的 actual/immediate 均为身体。
- **已修复：投射物 owner**。雪傀儡→牛生成雪球时 owner 是已登记牛身体，世界按其 ID 查询得到同一对象；原生与反向对照不回归。仅验证原生射击入口及 owner，不代表所有自动战斗已验证。
- **已修复：死亡分裂与成员继承**。原生史莱姆及正反融合均从尺寸 4 分裂出 2～4 个尺寸 2 子体，融合子体各保留一个成员；继续击杀子体后尺寸为 1，第二轮产生 8 个牛身体/史莱姆成员孙体，以及 7 个史莱姆身体/牛成员孙体，均唯一登记且成员未入世界。保存两个尺寸 2 派生体、完整退出 JVM 再启动同一最终 jar，2/2 恢复正确身体、成员与尺寸。
- 回归入口：运行 matrix、specials、boundaries 后 `assert-regression.js`；再运行 `derived-regression.js` 并等待 60 tick 后 `assert-derived.js`；保存退出重启后 `verify-derived-persistence.js`。另有 `identity-regression.js` 验证排除查询、伤害来源及普通 discard 不产生后代。第二轮成品回归通过 268 项动态断言（分裂数随机，断言总数会略变），无生产生物 ID 特判。
- 完整重启存档：村民、悦灵、守卫者、青蛙各与牛正反组合，共 8 个标记实体，从保存快照重新入世界、保存并退出 JVM，再从同一 jar 启动并进入世界。8/8 均恢复一个预期类型成员，存活且前置未登记；村民/悦灵/青蛙的 Brain 保持成员隔离。脚本顺序为 `persistence.js`，重启后 `verify-persistence.js`。
- 局部通过：悦灵正反组合都能从玩家接收钻石到共享主手。苦力怕点燃三组均在约 30 tick 爆炸并移除身体。末影人正反组合的公共 `randomTeleport` 返回 true、身体位置更新且成员位置一致；尚未验证自然瞬移触发和客户端/区块追踪，不能认定完整瞬移兼容。

### 单次动作与派生来源收紧的回归

在独立 `Fusion action provenance` 世界从成品 jar 验证，无生产物种分支。`public-interaction.js` 给三成员融合体安装实际 NeoForge EntityInteract 监听器，每次监听增加 1 点玩家经验；空操作、染色、取消、剪毛四次动作得到 4 次公共事件、4 次经验副作用，目标全部为身体，取消阻止成员继续染色，剪毛仍成功。

- 重新运行 18 种/54 实例与 specials、boundaries，`assert-regression.js` 通过 266 项动态断言；两次后代分裂继续保持组成，孙体分别为 6 和 3 个，均尺寸 1、一个成员、唯一身体登记。
- `no-resource-copy.js`：父体牛主手携 12 钻石，死亡分裂为 3 个牛身体/史莱姆成员子体，所有子体主手为空、成员尺寸为 2，验证不复制父体装备；不代表已验证每个模组自身的经济逻辑。
- 新规则产生的两个尺寸 2 子体完整保存退出 JVM，再用最终 jar 重启，`verify-derived-persistence.js` 2/2 通过。最初兔子产奶及女巫日晒自饮抗火场景复测通过。
- 数据流测试接受 this.getType 的局部变量别名，拒绝注册表工厂、另一对象的 getType、混合控制流来源；凭据测试拒绝未记录、不同来源、相等但不同对象及重复消费。此检查是限定来源协议，不是无条件自动识别后代。无法证明时新实体按原生行为入世界，不自动复制融合组成。

这些结果只覆盖所列场景，不代表所有缺口已解决。开发运行时曾确认模板的额外运行类路径没有把 AutoJS 识别为游戏模组；NeoForge 热调试依赖现通过开发 runtimeClasspath 接入。遇到 Latvian Maven TLS 连接重置时，本机使用 `-Dhttps.protocols=TLSv1.2 -Djava.net.preferIPv4Stack=true` 正常解析原仓库，未替换发布物。

以下矩阵在初始化时从实际 target 目录及 `ci.properties` 生成。CI 启用仅表示会参与验证，不等于已验证支持；后续增删版本或改变工具链时同步维护本表。

| Target | Gradle JVM | CI 配置 | 项目验证状态 |
| --- | --- | --- | --- |
| fabric-1.20.1 | JDK 17 | true | Pending verification |
| fabric-1.21.1 | JDK 21 | true | Pending verification |
| fabric-26.1 | JDK 25 | true | Pending verification |
| forge-1.18.2 | JDK 21 | true | Pending verification |
| forge-1.19.2 | JDK 21 | true | Pending verification |
| forge-1.20.1 | JDK 21 | true | Pending verification |
| neoforge-1.21.1 | JDK 21 | true | Experimental: build/tests + packaged client scenarios verified; dedicated server pending |
| neoforge-26.1 | JDK 25 | true | Pending verification |

## 开发与构建

开发规范见 [AGENTS.md](AGENTS.md)。共享业务与资源位于 `common/`；loader / Minecraft 差异位于 `targets/<loader>-<version>/`。所有 target 直接编译 common 原源码，不引用共享 jar，也不等待复制到 build 目录。

- 单版本：IDEA 打开对应 target 目录，使用该目录的 wrapper 和矩阵声明的 JDK。
- 全版本：IDEA 打开 `idea-workspace/`；当前主版本为 **neoforge-1.21.1**，配置的 Gradle JVM 为 **JDK 21**。该值来自 target 描述文件，仍须核对工作区 wrapper 与所用 JDK 的兼容性。
- 工作区只完整导入主 target。其他版本、文档、脚本及根配置作为仓库浏览内容展示，不承诺非主版本的 API 补全。common 源码只归属主 target，不额外关联根工程、common 或其他完整 target。
- 切换主版本修改 `idea-workspace/gradle.properties` 的 `workspace_main_target` 后重新加载 Gradle。composite build 共用工作区的 Gradle/JVM，不使用被包含 target 的 wrapper；若不兼容，独立打开 target 或调整工作区工具链。
- 普通 common 内容修改无需运行源码同步任务；变更 Gradle 配置或 `commonJava` 排除规则后需重新加载 Gradle。

在所选 target 目录执行：

```powershell
.\gradlew.bat clean build
.\gradlew.bat verifyHotDebug
.\gradlew.bat runClient
.\gradlew.bat runClient -PverifyPackagedMod # 仅 NeoForge 1.21.1：从构建 jar 而非开发类目录加载
# 或 runServer；Minecraft EULA 必须由用户阅读并接受
```

在仓库根可执行 `./gradlew -p common build` 单独检查共享层，但不能替代目标矩阵验证。根 `-Ptarget=<目录名> build` 调用该 target 的独立 wrapper；`-PallTargets=true build` 自动选择已启用且 `ci.java` 不高于当前 Gradle JVM 的目标，并打印跳过项。最低 JDK 筛选不保证旧 wrapper 支持任意新版 JDK，必要时分别构建。根 `build` 默认只验证 common。

## AutoJS 热调试与热执行

运行时调试必须使用 AutoJS，不得仅凭源码推断、临时日志或构建成功宣称游戏行为正确。

每个 target 的 `debug_*` 属性声明来自 Sighs Maven 的 AutoJS、Rhino 及可用的 KubeJS。具体版本和模式以该 target 配置为准；没有 `debug_kubejs` 时仅用 Rhino，不假设存在 KubeJS 事件或 `kubejs_log`。调试依赖仅用于开发游戏运行，不嵌入发布 jar，不成为玩家端必需依赖。

1. 使用相应 JDK 执行 `verifyHotDebug`，再启动 target 的游戏实例。
2. 默认 `GET http://127.0.0.1:25585/health` 探活，通过 MCP 客户端连接 `http://127.0.0.1:25585/mcp`，先调用 `status` 确认 side 和引擎模式。
3. 用 `eval_js` 执行短脚本，例如 server side 的 `AutoJS.snapshot()`；检查 `isError`、`ok`、`diagnostics`，触发行为后回读状态并形成断言。界面和输入用 client，权威世界修改用 server。
4. 修改脚本用 `reload_scripts`；KubeJS 模式结合 `kubejs_log` 检查错误，Rhino 模式重建作用域后重新注入验证脚本。配方、标签等按需另做 `/reload`。
5. Java、Mixin 和启动期注册内容不能靠脚本热重载替换，需构建重启后再用 AutoJS 验证。

配置位于游戏目录 `config/autojs.json`；多实例用 `AUTOJS_PORT` 分配不同端口。端点默认可执行任意 Java，只用于本机开发，不得暴露公网或玩家生产环境。共享机器配置 token，请求带 `Authorization: Bearer <token>`，不提交凭据。完整操作手册可从 AutoJS 发布 jar 提取 `SKILL.md`。仓库或端点不可用时明确记录阻塞，不用本地替代 jar 冒充远端验证。

## 发布与维护

- curseforge: project ID not configured in root gradle.properties; environment overrides and credentials remain unverified.
- modrinth: project ID not configured in root gradle.properties; environment overrides and credentials remain unverified.

发布前验证承诺支持的每个 target：构建、检查 jar 的 metadata/共享类/资源、使用 AutoJS 做最小 client 和 dedicated server 行为验证，并记录实际范围。初始化没有运行这些验证，不得把上表的待验证状态当作兼容性承诺。

- [发布指南](docs/PUBLISHING.md)
- [维护流程](docs/MAINTENANCE_WORKFLOW.md)
- [自动发现与新增 target](docs/CI_TARGET_DISCOVERY.md)
- [版本迁移参考](docs/version-differences/README.md)

工程来源：[开发框架](https://github.com/Tower-of-Sighs/SighsTemple)。本 README 与 AGENTS.md 已转为项目文档，后续由项目维护者更新，不再运行初始化脚本覆盖。

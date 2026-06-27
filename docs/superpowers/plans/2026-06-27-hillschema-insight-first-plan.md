# HillSchema 洞察优先版实施 Plan

## 1. 文档目标

本文档用于把已经确认的产品 Spec 落成可执行的实施步骤。

这份 Plan 不展开大量代码实现细节，也不堆砌文件清单，而是聚焦以下内容：

- 第一版应该按什么顺序推进
- 每个阶段要交付什么结果
- 哪些改造应该尽量以扩展方式完成
- 每个阶段完成后如何判断可以进入下一步

## 2. 实施原则

### 2.1 尽量扩展，不要侵入

第一版基于当前 `D:\HillSchema` 代码继续演进，优先采用“旁路新增模块 + 复用现有能力”的方式，而不是大面积改写现有主干。

落地时遵循以下原则：

- 能新增模块解决的问题，不直接改写原有核心流程。
- 能复用现有 Chat、Session、Agent 运行时的地方，不重造一套。
- 能通过新增页面切换主入口的，不推倒原有页面结构。
- 能通过新增配置和服务扩展的，不在旧逻辑里硬编码业务分支。

### 2.2 先打通主闭环，再补体验

第一版优先级必须严格按照“洞察闭环”推进：

1. 数据源可读。
2. 洞察可生成。
3. 首页可展示。
4. 详情可查看。
5. 问题上下文对话可继续。
6. Workspace 保持基本可用。

任何不直接服务于这条主链路的改造，都应该往后排。

### 2.3 先确定稳定边界，再做抽象

第一版先围绕统一的电商五表语义跑通。不要一开始就为任意 schema、复杂多租户、多种洞察模板做过度抽象。

## 3. 第一版总体实施顺序

整个实施过程建议拆成 6 个阶段：

1. 基线确认
2. 数据源接入补齐
3. 自动洞察链路落地
4. 首页与问题详情落地
5. 问题上下文对话接入
6. 联调、验证与收口

每个阶段都应该有可验证的阶段结果，避免一次性改太多导致问题难以定位。

## 4. 阶段一：基线确认

### 4.1 目标

确认当前复制到 `D:\HillSchema` 的工程能够作为稳定起点继续开发。

### 4.2 具体执行

1. 确认当前工程结构、启动方式、前后端构建方式都可用。
2. 记录当前默认首页、聊天页、Workspace 页、后端接口和数据层边界。
3. 确认哪些能力已经可复用，哪些只是预留接口但还没真正实现。
4. 补一个最小基线检查，确保后续改动前后可以比较。

### 4.3 阶段产出

- 一份清晰的现状判断
- 一个可启动、可构建的基线工程
- 一个明确的“哪些地方扩展，哪些地方少动”的边界

### 4.4 完成标准

当前工程可以正常构建，且已经明确以下结论：

- 前端主入口改造点在哪里
- 聊天能力的复用入口在哪里
- 数据源能力目前缺哪一段
- 哪些模块适合新增，哪些模块不应重写

## 5. 阶段二：数据源接入补齐

### 5.1 目标

把“多个已登记数据源可读取”这件事先做实，因为这是自动洞察的前提。

### 5.2 具体执行

1. 设计一套面向第一版的已登记数据源配置结构。
2. 在配置中表达 JDBC 连接信息和轻量语义映射信息。
3. 补齐当前项目对已登记数据源的实际读取能力，而不是只停留在占位接口。
4. 让系统至少能完成以下动作：
   - 列出可用数据源
   - 读取表信息
   - 执行受控的只读查询
5. 保持权限边界在数据库账号本身，不额外建设复杂应用内 ACL。

### 5.3 设计要求

- 只做 JDBC 只读访问。
- 只围绕第一版的电商五表语义做轻量配置。
- 数据源能力要既能服务自动洞察，也能服务后续问题上下文对话。

### 5.4 阶段产出

- 多个已登记数据源的配置方式
- 稳定的数据读取能力
- 可被后续洞察服务复用的数据访问层

### 5.5 完成标准

系统已经可以针对多个已登记数据源稳定完成基础读取，并且这套能力不是临时脚本，而是可复用的正式服务层。

## 6. 阶段三：自动洞察链路落地

### 6.1 目标

把“每 1 分钟自动生成问题消息”做成真正可运行的后台链路。

### 6.2 具体执行

1. 新增独立的洞察模块，不直接侵入原有主 Agent 核心逻辑。
2. 固定调度频率为每 1 分钟。
3. 每次调度按数据源逐个执行洞察扫描。
4. 先通过本地规则和聚合计算生成结构化候选结果。
5. 再由模型把候选结果整理成用户可读的问题消息。
6. 把每次执行沉淀成可查询的结果，而不是临时存在内存里。

### 6.3 数据沉淀要求

这一阶段至少要把以下三类信息落下来：

- 每次调度任务本身
- 每条生成的问题消息
- 每条问题消息对应的证据快照

### 6.4 设计要求

- 首页展示的必须是“已经生成好的消息”，不是实时现算。
- 同一类持续性问题要有状态表达，不要每分钟都像完全无关的新问题。
- 证据快照必须保留，否则详情页无法解释首页消息来源。

### 6.5 阶段产出

- 可定时运行的洞察任务
- 可持久化的问题消息
- 可回放的问题证据快照

### 6.6 完成标准

在不依赖前端的情况下，后台已经能够每分钟稳定写出一批新的问题消息，且这些消息带有可展示的摘要、结论和证据。

## 7. 阶段四：首页与问题详情落地

### 7.1 目标

把产品主入口从“聊天页”切换成“问题流”，同时补齐问题详情的基本查看能力。

### 7.2 具体执行

1. 新增首页问题流页面，作为新的默认入口。
2. 首页按时间顺序展示系统已经生成的问题消息。
3. 每条问题卡片展示最少必要信息，避免首页过度复杂。
4. 用户点击问题后，进入对应详情视图。
5. 详情页先展示问题本身，再展示证据和快照。

### 7.3 设计要求

- 第一版不做可视化大盘。
- 首页重点是“消息流感”，不是分析工作台。
- 详情页重点是“解释问题为什么出现”，不是立刻堆很多复杂操作。

### 7.4 阶段产出

- 新的首页入口
- 问题流列表
- 问题详情视图

### 7.5 完成标准

用户进入系统时首先看到的是自动洞察流；任意点击一条问题后，都能看到该问题的静态结论和证据快照。

## 8. 阶段五：问题上下文对话接入

### 8.1 目标

在不重写现有聊天主能力的前提下，把问题上下文对话接进来。

### 8.2 具体执行

1. 复用现有聊天接口与会话能力，避免再造一套聊天基础设施。
2. 在进入问题详情后，把当前问题的关键信息注入对话上下文。
3. 把对话边界收敛到当前问题，而不是默认开放成全局数据库问答。
4. 允许用户围绕当前问题继续追问，例如：
   - 为什么会发生
   - 哪个维度影响最大
   - 是否还在持续
   - 下一步应该查什么
5. 前端尽量复用现有聊天组件，只在传输层和上下文装配层做扩展。

### 8.3 设计要求

- 复用优先，不要重写整套聊天 UI。
- 对话上下文必须显式绑定当前问题和证据快照。
- 问题上下文对话与全局聊天能力要能并存，但第一版优先保障前者。

### 8.4 阶段产出

- 可围绕问题继续追问的上下文对话能力
- 前后端打通的问题详情页对话区

### 8.5 完成标准

用户在问题详情页中发起追问时，系统能稳定围绕当前问题回答，而不是退化成与问题脱钩的泛化问答。

## 9. 阶段六：联调、验证与收口

### 9.1 目标

把前后端、调度、数据源、持久化和对话能力串成一个完整可演示闭环，并做第一轮收口。

### 9.2 具体执行

1. 验证首页是否会随着调度不断出现新问题消息。
2. 验证点击问题后，详情内容与首页标题、摘要是否一致。
3. 验证详情页中的证据快照是否来自问题生成当时，而不是重新实时计算后漂移。
4. 验证问题上下文对话是否真的围绕当前问题工作。
5. 验证 Workspace 和原有基础能力没有被本次改造破坏。
6. 补充必要的文档，让后续继续开发的人知道当前边界和配置方式。

### 9.3 阶段产出

- 一条完整的可演示主链路
- 一组基本验证结果
- 一份更新后的使用说明

### 9.4 完成标准

第一版可以稳定演示以下流程：

1. 数据源已登记并可读取。
2. 系统每分钟自动产出问题消息。
3. 首页展示问题流。
4. 点击问题进入详情页。
5. 用户围绕该问题继续对话。

## 10. 建议的实际开发顺序

如果开始进入代码实现，建议按照下面的顺序做，而不是前后端同时大面积铺开：

1. 先补数据源读取能力。
2. 再做自动洞察任务与持久化。
3. 然后开放问题流和问题详情接口。
4. 再改首页入口和问题流页面。
5. 最后接入问题上下文对话与整体联调。

这样做的好处是，每一层都建立在前一层可验证的基础之上，问题定位会更清楚。

## 11. 本 Plan 中需要特别控制的风险

### 11.1 过度侵入原始工程

如果一开始就在原有聊天、运行时、Agent 主链路上大改，后续问题会很难排查。第一版必须坚持扩展优先。

### 11.2 数据源范围失控

如果过早追求任意数据库自动理解，第一版会直接失焦。当前必须坚持“多个已登记数据源 + 电商五表语义映射”。

### 11.3 首页过早产品化过度

第一版首页只需要把“问题流”表达清楚，不要在可视化、复杂交互、过度装饰上分散精力。

### 11.4 对话能力重新造轮子

问题上下文对话应该尽量通过现有聊天能力扩展实现，而不是独立重写一套聊天基础设施。

## 12. 实际开发任务清单的使用方式

下面这部分是在前面“阶段说明”的基础上，继续往下拆出来的实际开发任务清单。

它的使用方式不是一次性全做完，而是按依赖顺序逐个完成。每个任务都尽量满足以下要求：

- 单个任务目标清晰
- 涉及文件范围可控
- 完成后可以独立验证
- 尽量以新增模块和复用现有能力为主

如果执行过程中发现某个任务明显继续膨胀，应该继续往下再拆，而不是直接把多个任务混在一个提交里做掉。

## 13. 任务依赖关系

推荐依赖关系如下：

1. `Task 0` 先确认工程基线。
2. `Task 1` 先把洞察模块骨架和配置模型立起来。
3. `Task 2` 在此基础上补齐已登记数据源读取能力。
4. `Task 3` 落洞察持久化模型。
5. `Task 4` 落本地洞察检测器。
6. `Task 5` 把调度与刷新链路串起来。
7. `Task 6` 再接模型表达层。
8. `Task 7` 开问题流与问题详情接口。
9. `Task 8` 接问题上下文对话后端。
10. `Task 9` 改前端首页入口并接问题流页面。
11. `Task 10` 复用现有聊天组件接入问题上下文对话。
12. `Task 11` 做联调、验证和文档收口。

其中有两个可以局部并行的点：

- `Task 7` 稳定后，`Task 9` 可以先开始。
- `Task 8` 和 `Task 9` 完成到一定程度后，`Task 10` 再接前端问题上下文对话。

## 14. 实际开发任务清单

### Task 0：工程基线冻结

- 任务目标：确认当前 `D:\HillSchema` 工程是一个可以继续迭代的稳定起点，并把“当前就是基线”这件事固定下来。
- 前置依赖：无。
- 涉及文件：`pom.xml`、`frontend/package.json`、`frontend/src/main.tsx`、`src/main/resources/application.yml`。
- 执行步骤：
  1. 确认当前工程是否已经纳入 git 管理；如果没有，先初始化版本库。
  2. 跑通当前前端构建和后端打包，确认复制过来的工程本身没有隐藏问题。
  3. 记录当前默认首页是 `/chat`，以及后面会改这个入口，但先不要动。
  4. 记录当前 `DataAgentToolkit` 中真正可用的只有 `list_data_sources`，其余数据读能力仍是占位。
- 完成产物：一个明确的“未改造前状态”基线。
- 验收标准：当前工程能构建；默认首页、聊天入口、数据源占位能力等边界都已确认清楚。
- 建议提交信息：`chore: capture hillschema baseline`

### Task 1：建立洞察模块骨架与配置模型

- 任务目标：先把 `insight` 作为独立扩展层立起来，避免后续逻辑散落在现有模块里。
- 前置依赖：`Task 0`。
- 涉及文件：
  - `src/main/java/io/agentscope/dataagent/insight/config/InsightProperties.java`
  - `src/main/java/io/agentscope/dataagent/insight/config/InsightModuleConfig.java`
  - `src/main/java/io/agentscope/dataagent/insight/config/InsightSchedulingConfig.java`
  - `src/main/resources/application.yml`
- 执行步骤：
  1. 新建 `io.agentscope.dataagent.insight` 包结构，并先从 `config` 开始。
  2. 定义 `dataagent.insights.*` 配置前缀，承接启停开关、固定调度周期、数据源登记信息和轻量电商语义映射。
  3. 在 `application.yml` 中补一份第一版默认配置，明确调度固定为 1 分钟。
  4. 保证即使暂时没有配置任何数据源，应用也能正常启动。
- 完成产物：洞察模块骨架、配置入口和基础装配点。
- 验收标准：新增配置不会破坏现有启动；洞察模块已经有独立命名空间和配置边界。
- 建议提交信息：`feat: add insight module skeleton`

### Task 2：补齐已登记数据源读取能力

- 任务目标：把“多个已登记数据源可读取”从占位状态变成正式可用能力。
- 前置依赖：`Task 1`。
- 涉及文件：
  - `src/main/java/io/agentscope/dataagent/insight/source/JdbcInsightDataSourceRegistry.java`
  - `src/main/java/io/agentscope/dataagent/insight/source/JdbcQueryExecutor.java`
  - `src/main/java/io/agentscope/dataagent/insight/source/JdbcMetadataService.java`
  - `src/main/java/io/agentscope/dataagent/insight/source/SqlPreviewService.java`
  - `src/main/java/io/agentscope/dataagent/insight/source/TableDescriptionService.java`
  - `src/main/java/io/agentscope/dataagent/tools/data/DataAgentToolkit.java`
  - `src/main/java/io/agentscope/dataagent/tools/data/DataToolkitConfig.java`
  - `src/main/java/io/agentscope/dataagent/tools/data/DataToolkitRegistrar.java`
  - `src/test/java/io/agentscope/dataagent/insight/source/JdbcInsightServicesTest.java`
- 执行步骤：
  1. 先实现一个面向已登记 JDBC 数据源的 registry，而不是重写现有 `DataSourceRegistry` 抽象。
  2. 新增只读 JDBC 查询执行器，明确只允许 `SELECT / WITH` 类查询，不允许写操作。
  3. 新增表结构描述能力，用于返回字段信息和小样本。
  4. 把 `DataAgentToolkit` 里当前的 “not implemented” 替换成真实服务调用。
  5. 通过 `DataToolkitConfig` 和 `DataToolkitRegistrar` 把新服务挂回现有工具注册链路。
  6. 先用 H2 或本地测试库写验证，确保列表、表描述、SQL preview 都稳定可用。
- 完成产物：真实可用的数据源读取层，以及可被 Agent 直接使用的数据工具。
- 验收标准：`list_data_sources`、`describe_table`、`run_sql_preview` 三条链路都可用，且不会放开写权限。
- 建议提交信息：`feat: enable registered jdbc data sources`

### Task 3：建立洞察结果持久化模型

- 任务目标：把“自动洞察像消息一样沉淀下来”这件事落到数据库里，为首页流和详情页提供稳定数据基础。
- 前置依赖：`Task 2`。
- 涉及文件：
  - `src/main/java/io/agentscope/dataagent/insight/persistence/jpa/InsightBatchEntity.java`
  - `src/main/java/io/agentscope/dataagent/insight/persistence/jpa/InsightItemEntity.java`
  - `src/main/java/io/agentscope/dataagent/insight/persistence/jpa/InsightEvidenceEntity.java`
  - `src/main/java/io/agentscope/dataagent/insight/persistence/jpa/InsightBatchRepository.java`
  - `src/main/java/io/agentscope/dataagent/insight/persistence/jpa/InsightItemRepository.java`
  - `src/main/java/io/agentscope/dataagent/insight/persistence/jpa/InsightEvidenceRepository.java`
  - `src/main/java/io/agentscope/dataagent/insight/domain/InsightStatus.java`
  - `src/test/java/io/agentscope/dataagent/insight/service/InsightPersistenceTest.java`
- 执行步骤：
  1. 定义“调度批次”“问题消息”“证据快照”三层持久化模型。
  2. 在问题消息层预留数据源、类型、标题、摘要、状态、指纹、发生时间等核心字段。
  3. 在证据快照层保留问题生成时的关键指标、维度切片和说明文本，避免后续实时数据漂移。
  4. 为持续性问题设计指纹或相似键，方便后面做 `NEW / CONTINUING / RESOLVED` 状态判断。
  5. 加一组基础持久化测试，确认实体关系和读写都正常。
- 完成产物：洞察批次、问题消息、证据快照三类持久化对象。
- 验收标准：后台已经能把一条完整洞察消息及其快照稳定存取出来。
- 建议提交信息：`feat: add insight persistence model`

### Task 4：实现本地洞察检测器

- 任务目标：先把第一版真正会“发现问题”的本地规则能力做出来，而不是一开始把洞察判断交给模型。
- 前置依赖：`Task 2`、`Task 3`。
- 涉及文件：
  - `src/main/java/io/agentscope/dataagent/insight/domain/InsightKind.java`
  - `src/main/java/io/agentscope/dataagent/insight/domain/InsightCandidate.java`
  - `src/main/java/io/agentscope/dataagent/insight/domain/EcommerceInsightDetector.java`
  - `src/test/java/io/agentscope/dataagent/insight/domain/EcommerceInsightDetectorTest.java`
- 执行步骤：
  1. 固定第一版支持的四类问题：异常、趋势、归因、摘要。
  2. 围绕电商五表先确定第一批可检测指标，例如订单量、成交额、退款量、退款率等。
  3. 基于固定时间窗口做环比或短周期对比，生成结构化候选问题。
  4. 为每类候选问题生成可复用的结构化输出，包括指标、窗口、偏移幅度、主要维度和候选归因。
  5. 保证检测器输出是确定性的，方便后续状态识别和测试。
- 完成产物：第一版本地洞察候选生成器。
- 验收标准：给定固定测试数据时，检测器能稳定产出预期问题候选，而不是随机变化。
- 建议提交信息：`feat: add ecommerce insight detector`

### Task 5：打通调度与刷新链路

- 任务目标：把“每分钟巡检一次并写出问题消息”的后台主链路跑通。
- 前置依赖：`Task 3`、`Task 4`。
- 涉及文件：
  - `src/main/java/io/agentscope/dataagent/insight/service/InsightRefreshService.java`
  - `src/main/java/io/agentscope/dataagent/insight/scheduler/InsightRefreshScheduler.java`
  - `src/main/java/io/agentscope/dataagent/insight/config/InsightSchedulingConfig.java`
  - `src/test/java/io/agentscope/dataagent/insight/service/InsightRefreshServiceTest.java`
- 执行步骤：
  1. 把一次巡检抽成独立刷新服务，不把调度代码直接写进控制器或其他现有模块。
  2. 刷新服务按“数据源 -> 规则检测 -> 状态判断 -> 结果持久化”的顺序执行。
  3. 调度器只负责每分钟触发，不承担业务判断。
  4. 处理持续问题与已恢复问题的状态流转，避免首页被每分钟完全重复的问题刷满。
  5. 增加手动触发或测试入口，便于在联调阶段不必真的等待一分钟。
- 完成产物：可周期运行的洞察刷新服务。
- 验收标准：不依赖前端也能观察到后台每次执行后产生批次、问题消息和状态变化。
- 建议提交信息：`feat: add insight refresh scheduler`

### Task 6：接入模型表达层

- 任务目标：让结构化候选问题变成可读的问题消息，但仍然保持“本地计算优先、模型负责表达”的边界。
- 前置依赖：`Task 4`、`Task 5`。
- 涉及文件：
  - `src/main/java/io/agentscope/dataagent/insight/domain/InsightNarrative.java`
  - `src/main/java/io/agentscope/dataagent/insight/service/InsightNarrativeService.java`
  - `src/test/java/io/agentscope/dataagent/insight/service/InsightNarrativeServiceTest.java`
- 执行步骤：
  1. 为模型输入定义一份稳定的最小上下文，只包含聚合结果、变化幅度、时间窗口和关键维度。
  2. 让模型负责生成标题、摘要、结论、证据说明和建议追问方向。
  3. 增加模型不可用时的模板化降级逻辑，避免整个调度链路被模型可用性卡死。
  4. 明确禁止把原始大表或超大明细直接拼进模型上下文。
- 完成产物：结构化候选到可读问题消息的转换层。
- 验收标准：同一条候选洞察即使模型不可用，也能被稳定写成可展示的问题消息。
- 建议提交信息：`feat: add insight narrative rendering`

### Task 7：开放问题流与问题详情后端接口

- 任务目标：给首页和详情页提供正式 API，而不是让前端直接理解数据库结构。
- 前置依赖：`Task 3`、`Task 5`、`Task 6`。
- 涉及文件：
  - `src/main/java/io/agentscope/dataagent/insight/service/InsightFeedService.java`
  - `src/main/java/io/agentscope/dataagent/web/api/InsightFeedController.java`
  - `src/test/java/io/agentscope/dataagent/web/api/InsightFeedControllerTest.java`
- 执行步骤：
  1. 定义首页问题流接口，默认返回最近一段时间或最近 N 条问题消息。
  2. 定义问题详情接口，返回问题本体、状态、结论、证据快照和建议追问信息。
  3. 保持接口按现有 `agentId` 作用域组织，避免第一版再发明新的权限模型。
  4. 对空结果、无权限、问题不存在等情况给出清晰响应。
- 完成产物：问题流接口和问题详情接口。
- 验收标准：前端不需要了解底层 JPA 模型，也能完整拿到首页与详情页所需数据。
- 建议提交信息：`feat: add insight feed api`

### Task 8：接入问题上下文对话后端

- 任务目标：在不重写原有聊天主链路的前提下，实现“围绕当前问题继续对话”。
- 前置依赖：`Task 6`、`Task 7`。
- 涉及文件：
  - `src/main/java/io/agentscope/dataagent/insight/service/InsightScopedChatPromptBuilder.java`
  - `src/main/java/io/agentscope/dataagent/web/api/InsightChatController.java`
  - `src/test/java/io/agentscope/dataagent/web/api/InsightChatControllerTest.java`
- 执行步骤：
  1. 为问题详情页中的聊天构造一份显式的上下文拼装逻辑。
  2. 上下文至少要包含当前问题标题、摘要、结论、证据快照、时间窗口和所属数据源。
  3. 保持实际聊天执行仍然复用现有 `ChatController` 所在的运行时能力，不另起一套会话基础设施。
  4. 为问题对话设计独立会话键策略，避免和全局聊天会话相互污染。
- 完成产物：问题上下文聊天入口和上下文装配层。
- 验收标准：后端已经能根据某个问题生成“强上下文”对话请求，并稳定返回结果。
- 建议提交信息：`feat: add insight scoped chat api`

### Task 9：改造前端首页入口并落问题流页面

- 任务目标：把产品主入口从 `/chat` 切换到 `/insights`，同时把首页问题流和详情布局先落出来。
- 前置依赖：`Task 7`。
- 涉及文件：
  - `frontend/src/main.tsx`
  - `frontend/src/api/insights.ts`
  - `frontend/src/pages/InsightsPage.tsx`
  - `frontend/src/components/insights/InsightFeedList.tsx`
  - `frontend/src/components/insights/InsightCard.tsx`
  - `frontend/src/components/insights/InsightDetailPanel.tsx`
  - `frontend/src/components/insights/InsightStatusPill.tsx`
  - `frontend/src/pages/__tests__/InsightsPage.test.tsx`
- 执行步骤：
  1. 新增 `/insights` 页面，并把默认路由从 `/chat` 改到 `/insights`。
  2. 通过新增 API 客户端请求首页问题流和问题详情接口。
  3. 首页以“问题流”方式展示，不做可视化大盘。
  4. 详情区域优先展示静态问题信息和证据快照，先不急着把聊天混进同一个任务。
  5. 保证原有 `/chat`、`/workspace` 等页面仍可访问，不因为主入口切换而失效。
- 完成产物：新的首页入口和问题流基础页面。
- 验收标准：登录后默认落到 `/insights`；首页能展示问题流；点击问题能看到详情内容。
- 建议提交信息：`feat: add insight homepage`

### Task 10：复用现有聊天组件接入问题上下文对话

- 任务目标：在前端层完成问题详情页中的上下文对话接入，并尽量复用当前聊天 UI。
- 前置依赖：`Task 8`、`Task 9`。
- 涉及文件：
  - `frontend/src/components/ChatPanel.tsx`
  - `frontend/src/api/chatTransport.ts`
  - `frontend/src/api/insightChat.ts`
  - `frontend/src/components/insights/InsightScopedChatPanel.tsx`
  - `frontend/src/components/__tests__/InsightScopedChatPanel.test.tsx`
- 执行步骤：
  1. 把当前 `ChatPanel` 里写死的聊天传输逻辑抽成可替换传输层。
  2. 保持现有 `/chat` 页面继续使用原有默认传输实现。
  3. 为问题详情页新增一套面向问题上下文接口的传输实现。
  4. 在问题详情页下半部分挂接上下文聊天区，形成“上面看证据、下面继续问”的布局。
  5. 验证首页跳详情、详情继续聊天、刷新后保留当前问题上下文这几条路径。
- 完成产物：问题详情页中的上下文聊天区，以及复用后的聊天组件边界。
- 验收标准：原有全局聊天不受影响；问题详情页中的聊天可以稳定围绕当前问题工作。
- 建议提交信息：`feat: add insight detail chat`

### Task 11：联调、验证与文档收口

- 任务目标：把前后端、调度、持久化和问题上下文对话完整串起来，并形成可交接的说明。
- 前置依赖：`Task 10`。
- 涉及文件：
  - `README.md`
  - `README_zh.md`
  - `src/main/resources/application.yml`
  - `frontend/package.json`
  - 相关测试文件
- 执行步骤：
  1. 做一轮端到端联调，覆盖数据源读取、自动洞察生成、首页展示、详情查看、问题聊天五条主链路。
  2. 补充启动说明、数据源配置示例和第一版限制说明。
  3. 明确把两项后续规划写入文档：可配置时间区间，以及“本地计算异常、模型负责表达”的进一步收紧。
  4. 校验 Workspace 和原有基础页面没有被这次改造破坏。
- 完成产物：可演示版本、更新后的文档和一组基本验证结果。
- 验收标准：从登录进入首页，到等待问题生成、打开详情、继续追问，这条主路径可以稳定跑通。
- 建议提交信息：`docs: finalize insight-first v1 setup`

## 15. 建议提交节奏

为了让这条线可控，建议不要攒一个超大提交，至少按下面节奏切：

1. 基线与模块骨架一组提交。
2. 数据源接入能力一组提交。
3. 持久化与检测器一组提交。
4. 调度与模型表达一组提交。
5. 后端问题流与详情接口一组提交。
6. 前端首页与详情页面一组提交。
7. 问题上下文对话一组提交。
8. 联调与文档收口一组提交。

这样每一组提交都能独立 review，也更符合“尽量扩展、不要侵入”的原则。

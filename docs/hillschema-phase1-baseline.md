# HillSchema Phase 1 Baseline

## 目标

这份文档对应实施计划中的“阶段一：基线确认”，用于明确当前 `D:\\HillSchema` 工程在进入洞察产品化改造前的真实边界、可复用入口和已验证基线。

## 当前结论

- 当前工程是一个 `Spring Boot WebFlux + React SPA` 的单模块应用，后端入口是 `src/main/java/io/agentscope/dataagent/web/DataAgentApp.java`，前端产物输出到 `src/main/resources/static/`。
- 当前默认主入口仍然是聊天页，不是洞察首页。前端路由定义在 `frontend/src/main.tsx`，根路径会重定向到 `/chat`。
- `Workspace` 已经是现成的辅助能力页，但不是主入口。它以只读浏览为主，前端页面是 `frontend/src/pages/WorkspacePage.tsx`，后端接口是 `src/main/java/io/agentscope/dataagent/web/api/AgentWorkspaceController.java`。
- 聊天能力已经具备完整复用入口。前端由 `frontend/src/pages/ChatPage.tsx` + `frontend/src/components/ChatPanel.tsx` 承载，后端由 `src/main/java/io/agentscope/dataagent/web/api/ChatController.java` 提供会话化 API。
- 数据源能力目前只有“数据源注册表 + 工具占位”这层骨架，没有真正的 JDBC 读取实现。`src/main/java/io/agentscope/dataagent/tools/data/DataToolkitConfig.java` 默认注入空的 `InMemoryDataSourceRegistry`；`DataAgentToolkit` 中只有 `list_data_sources` 有真实输出，`describe_table` 和 `run_sql_preview` 明确返回 `not implemented`。

## 前端主入口边界

- 主路由入口：`frontend/src/main.tsx`
- 应用壳：`frontend/src/components/AppShell.tsx`
- 当前默认首页：`/chat`
- 当前辅助页：`/workspace`

这意味着第一版改造成“洞察流首页”时，应当优先在现有路由层新增新的首页入口或重定向策略，而不是重写 `AppShell`、会话侧栏和现有聊天面板。

## 聊天复用边界

- 页面入口：`frontend/src/pages/ChatPage.tsx`
- 会话与流式 UI：`frontend/src/components/ChatPanel.tsx`
- 前端 API：`frontend/src/api/chat.ts`
- 后端入口：`src/main/java/io/agentscope/dataagent/web/api/ChatController.java`

当前聊天链路已经具备以下可复用特征：

- 以 `agentId + sessionKey` 维护对话上下文
- 通过 SSE 返回 `token`、`tool_call`、`tool_result`、`done`、`error`
- 已经有 URL/本地存储级别的会话恢复逻辑

第一版问题详情页的“问题上下文对话”应优先复用这条链路，在问题维度上补上下文装配，而不是重做一套聊天接口。

## Workspace 复用边界

- 页面入口：`frontend/src/pages/WorkspacePage.tsx`
- 前端 API：`frontend/src/api/workspace.ts`
- 后端入口：`src/main/java/io/agentscope/dataagent/web/api/AgentWorkspaceController.java`

当前 Workspace 已经覆盖：

- 工作区摘要读取
- 文件树浏览
- 文件读写 / 上传 / 移动 / 删除
- Scaffold 与 memory 便捷视图

第一版里它适合作为保留的支撑区，不应该在洞察首页阶段被大改。

## 数据源现状与缺口

- 默认数据源注册：`DataToolkitConfig` -> 空 `InMemoryDataSourceRegistry`
- 当前描述模型：`src/main/java/io/agentscope/dataagent/tools/data/DataSource.java`
- 当前工具实现：`src/main/java/io/agentscope/dataagent/tools/data/DataAgentToolkit.java`

已具备：

- 数据源描述模型
- Spring Bean 覆盖点
- Agent 工具注册点

当前缺失：

- 已登记数据源的正式配置结构
- JDBC 连接建立与只读查询执行
- 表结构读取
- 真实的多数据源服务层

因此，阶段二应该优先补“配置 + 服务 + 只读访问”，而不是直接开始做自动洞察调度。

## 建议的扩展边界

优先新增、少动旧逻辑的区域：

- 新增洞察首页与问题详情页面
- 新增洞察域模型、持久化和服务层
- 新增已登记数据源配置与 JDBC 读取层

尽量少动的区域：

- `AppShell` 和 Sessions sidebar
- `ChatController` 的主对话链路
- `AgentWorkspaceController` 的基础文件能力
- 现有 marketplace / admin / channel 框架

## 阶段一最小基线检查

新增测试：

- `src/test/java/io/agentscope/dataagent/tools/data/DataAgentToolkitBaselineTest.java`

它锁定了当前数据源能力的基线事实：

- 配置了 descriptor 时，`list_data_sources` 会按当前格式输出
- 默认空注册表会返回 `none`
- `describe_table` 当前仍是 connector 边界占位
- `run_sql_preview` 只允许只读 SQL，但仍未接入真实 connector

## 2026-06-28 验证记录

- `frontend`: `npm run build` 通过
- `backend`: `mvn -DforkCount=0 -DreuseForks=false -Dtest=DataAgentToolkitBaselineTest test` 通过
- `backend`: `mvn test` 未完成，阻塞原因是当前主机 JVM 原生内存不足，错误落在 `hs_err_pid44084.log`

这说明：

- 前端构建链路当前可用
- 阶段一新增的最小数据源基线检查可执行
- 后端 Maven/JVM 基线在这台机器上还受宿主环境限制，不能把这次失败直接解释为代码逻辑失败

## 阶段一结论

当前工程可以作为第一版继续演进的基线，但应按以下方式推进：

- 首页入口从聊天转向洞察流时，优先新增页面与路由，不重写聊天底座
- 问题详情对话直接复用现有 chat/session/SSE 链路
- Workspace 先保持为支撑能力区
- 阶段二先补数据源读取正式服务层，再进入洞察任务链路

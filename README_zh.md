# HillSchema

本项目是一个基于 AgentScope / HarnessAgent 的数据分析演示应用，当前形态是：

- 后端：Spring Boot 4 + WebFlux + JPA
- 前端：React + Vite
- 默认主入口：`/insights`
- 默认运行方式：纯本地，不依赖 Docker
- 默认持久化：本地 H2

现在这套代码更适合做本地演示、洞察页联调和会话追问验证，而不是分布式生产部署。

## 当前能力

- 首页默认进入“问题”页，展示已经生成的洞察卡片
- 点击问题后可以查看结论、证据，并继续追问
- `/chat` 保留为独立聊天页，聊天记录会持久化并可恢复查看
- 支持用户侧配置 Skills、Subagents、Tools、Channels
- 支持管理员查看 Agents、Sessions、Approvals、Users、Usage
- 支持把用户工作区内容提名为 contribution，经管理员审批后进入共享层
- 支持读取已登记 JDBC 数据源，并基于电商五表语义生成洞察

## 当前限制

- 当前是本地优先模式，不再要求 Docker sandbox 才能启动
- 用户侧 `Workspace` 不是主入口，主要通过 Skills / Subagents / Tools 页面间接管理
- 演示数据源默认建议走本地 H2 脚本，不建议依赖 Docker MySQL
- 这版更强调“先能本地跑通”，不是最终的多节点隔离方案

## 环境要求

- JDK 17+
- Maven 3.9+
- Windows PowerShell
- 首次构建时需要联网下载 Maven / npm 依赖

前端不要求你先手工安装 Node。`mvn` 会通过 `frontend-maven-plugin` 自动安装仓库内需要的 Node 和 npm。

## 快速启动

在项目根目录 `D:\HillSchema` 打开 PowerShell。

### 1. 启动后端

```powershell
mvn --% spring-boot:run
```

默认地址：

- 应用首页：[http://localhost:8080](http://localhost:8080)
- 登录页：[http://localhost:8080/login](http://localhost:8080/login)
- 问题页：[http://localhost:8080/insights](http://localhost:8080/insights)

如果 `8080` 被占用：

```powershell
mvn --% spring-boot:run -Dspring-boot.run.arguments=--server.port=18080
```

### 2. 单独启动前端热更新（可选）

如果你要改前端并实时预览，再开一个终端：

```powershell
Set-Location D:\HillSchema\frontend
npm install
npm run dev
```

Vite 默认会给出类似 [http://localhost:5173](http://localhost:5173) 或 [http://localhost:5174](http://localhost:5174) 的地址。  
如果你只是正常使用项目，不需要单独起这一套，直接访问后端 `8080` 即可。

## 默认账号

本地 H2 初始化会种入两个演示账号：

- `bob / bob`
- `alice / alice`

管理员能力取决于数据库里已有用户角色；当前本地环境通常会自动带上管理员可用数据。

## 推荐的本地演示流程

如果你希望 `/insights` 首页稳定出现“订单下降、退款升高、organic 下滑”这类演示卡片，建议使用仓库内置的本地 H2 业务库脚本。

### 1. 生成本地演示业务库

```powershell
Set-Location D:\HillSchema
powershell -ExecutionPolicy Bypass -File .\scripts\seed-local-shop-demo-anomaly.ps1
```

这个脚本会创建一个本地 H2 业务数据库，路径默认在：

```text
D:\HillSchema\output\demo-db\shop-demo
```

脚本会植入一组固定异常特征：

- 最近 24 小时订单量低于前 24 小时
- 当前退款率显著升高
- `organic` 渠道跌幅最大

### 2. 让应用加载这份演示数据源

```powershell
Set-Location D:\HillSchema
$env:SPRING_CONFIG_ADDITIONAL_LOCATION = "optional:file:./scripts/local-shop-demo.datasource.yml"
mvn --% spring-boot:run
```

启动后等待约 1 分钟，再打开：

- [http://localhost:8080/insights](http://localhost:8080/insights)

### 3. 关闭额外数据源配置

当你不想再使用这份演示业务库时，关闭当前终端，或手工清掉环境变量：

```powershell
Remove-Item Env:SPRING_CONFIG_ADDITIONAL_LOCATION -ErrorAction SilentlyContinue
```

## 数据存放位置

项目现在至少有两类本地数据：

### 1. 应用自己的持久化库

默认是本地 H2，路径在：

```text
C:\Users\<你的用户名>\.agentscope-dataagent\
```

这里保存：

- 用户
- 权限
- contribution
- insight 持久化数据
- 其他 JPA 管理的数据

### 2. Agent 工作目录和默认种子

默认目录在：

```text
C:\Users\<你的用户名>\.agentscope\dataagent\
```

以及项目根目录下的：

```text
D:\HillSchema\.agentscope\
D:\HillSchema\shared\
```

这里会涉及：

- 默认 agent 配置
- skills / subagents / knowledge 种子
- 本地 session 恢复文件
- 共享 contribution 落地内容

### 3. 本地演示业务库

如果你运行了演示脚本，则业务演示数据在：

```text
D:\HillSchema\output\demo-db\
```

它和应用自己的 H2 不是一回事。

## 模型配置

如果你希望聊天、追问、分析文案都正常走模型，建议显式配置模型密钥，而不是依赖本地默认值。

PowerShell 示例：

```powershell
$env:DASHSCOPE_API_KEY = "你的-key"
mvn --% spring-boot:run
```

如果没有有效模型：

- 应用仍可能启动
- 但聊天、追问、部分洞察文案能力会失败或退化

## 常用页面

用户侧常用页面：

- `/insights`：问题首页
- `/chat`：独立聊天页
- `/configure/skills`
- `/configure/subagents`
- `/configure/tools`
- `/configure/channels`
- `/contributions`

管理员侧常用页面：

- `/admin/overview`
- `/admin/agents`
- `/admin/sessions`
- `/admin/approvals`
- `/admin/users`
- `/admin/usage`

## 开发命令

### 后端测试

```powershell
mvn test
```

### 指定测试

```powershell
mvn --% -Dtest=WorkspaceManagerFactoryTest,ChatControllerTest,InsightChatControllerTest,InsightFeedServiceTest test
```

### 前端开发

```powershell
Set-Location D:\HillSchema\frontend
npm run dev
```

### 前端测试

```powershell
Set-Location D:\HillSchema\frontend
npm test
```

### 打包

```powershell
Set-Location D:\HillSchema
mvn -DskipTests package
```

产物默认在：

```text
D:\HillSchema\target\
```

主要包括：

- `agentscope-dataagent-2.0.0-SNAPSHOT-exec.jar`
- `agentscope-dataagent-2.0.0-SNAPSHOT.jar`

## 常见问题

### 1. 为什么我看到的还是旧页面？

先确认你访问的是哪个地址：

- 后端静态页：`http://localhost:8080`
- Vite 开发页：通常是 `http://localhost:5173` 或 `http://localhost:5174`

如果你运行的是 `npm run dev`，应该看 Vite 输出的那个地址，而不是 `8080`。

### 2. 为什么 `/insights` 没有数据？

常见原因：

- 没有登记任何 `dataagent.data.sources`
- 没有跑本地演示脚本
- 刷新周期还没到，默认是 1 分钟
- 模型不可用，导致部分洞察生成链路失败

建议优先走“本地演示流程”那一节。

### 3. 现在还能不能用 Docker？

仓库里还保留了旧的 Docker MySQL 演示文件，但当前 README 不把它作为主路径。  
按照现在的项目状态，优先使用本地 H2 演示脚本。

### 4. 用户侧为什么看不到 Workspace 主入口？

这是有意的前端降级。当前用户主路径聚焦在：

- 问题浏览
- Chat
- 配置页

Workspace 相关能力仍然存在，但不再作为用户默认主入口暴露。

## 相关文件

- 配置主文件：[application.yml](D:/HillSchema/src/main/resources/application.yml)
- JDBC profile：[application-jdbc.yml](D:/HillSchema/src/main/resources/application-jdbc.yml)
- 本地演示数据源配置：[local-shop-demo.datasource.yml](D:/HillSchema/scripts/local-shop-demo.datasource.yml)
- 本地演示造数脚本：[seed-local-shop-demo-anomaly.ps1](D:/HillSchema/scripts/seed-local-shop-demo-anomaly.ps1)
- 前端入口：[main.tsx](D:/HillSchema/frontend/src/main.tsx)
- 后端启动类：[DataAgentApp.java](D:/HillSchema/src/main/java/io/agentscope/dataagent/web/DataAgentApp.java)

## 当前建议

如果你的目标是“快速演示当前产品能力”，推荐按这个顺序：

1. 跑本地演示造数脚本
2. 用额外 Spring 配置加载 `local-shop-demo.datasource.yml`
3. 启动后端
4. 登录后直接看 `/insights`
5. 进入详情页继续追问
6. 再去 `/chat` 看会话恢复和独立聊天

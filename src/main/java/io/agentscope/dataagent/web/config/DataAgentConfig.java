/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.agentscope.dataagent.web.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.dataagent.runtime.DataAgentBootstrap;
import io.agentscope.dataagent.runtime.config.ChannelConfigEntry;
import io.agentscope.dataagent.runtime.marketplace.GitDataAgentMarketplace;
import io.agentscope.dataagent.runtime.marketplace.LocalApprovalMarketplace;
import io.agentscope.dataagent.runtime.marketplace.NacosDataAgentMarketplace;
import io.agentscope.dataagent.runtime.marketplace.UserMarketplaceRegistry.DataAgentMarketplaceFactoryRegistration;
import io.agentscope.dataagent.web.toolbus.ToolEventBus;
import io.agentscope.dataagent.web.toolbus.ToolNotificationMiddleware;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import io.agentscope.harness.agent.gateway.channel.ChannelConfig;
import io.agentscope.harness.agent.gateway.channel.DmScope;
import io.agentscope.harness.agent.gateway.channel.chatui.ChatUiChannel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot configuration for the agentscope-dataagent web module.
 *
 * <p>Assembles a {@link DataAgentBootstrap} from {@code .agentscope/agentscope.json} in the working
 * directory (defaults to {@code dataagent.workspace}), then registers a {@link ChatUiChannel} with
 * {@link DmScope#PER_PEER} so each authenticated user gets an isolated agent session and namespace.
 *
 * <h2>Property prefix</h2>
 *
 * <p>All config keys live under {@code dataagent.*}.
 *
 * <h2>Filesystem topology</h2>
 *
 * <p>DataAgent is currently wired for a local-only downgrade. Each agent uses a local filesystem
 * spec, and browser-side workspace isolation is handled by per-user workspace directories rather
 * than Docker sandboxes.
 *
 * <h2>Model wiring (priority order)</h2>
 *
 * <ol>
 *   <li>If a {@link Model} Spring Bean is already present (provided by another
 *       {@code @Configuration}), it is used as-is.
 *   <li>Otherwise, if {@code dataagent.dashscope.api-key} is set, a {@link DashScopeChatModel} is
 *       created automatically.
 *   <li>If neither is available, the app starts without a model (agent calls will fail until one
 *       is configured).
 * </ol>
 *
 * <p>Note: model wiring uses <em>method-parameter</em> injection in {@code @Bean} methods (not
 * field-level {@code @Autowired}) to avoid a circular-dependency with the {@code Model} bean
 * defined in this same class.
 *
 * <h2>Agent config</h2>
 *
 * <p>If {@code ~/.agentscope/dataagent/agentscope.json} does not exist, a minimal default agent
 * config is auto-generated so the app starts without manual setup.
 */
@Configuration
public class DataAgentConfig {

    private static final Logger log = LoggerFactory.getLogger(DataAgentConfig.class);

    @Value("${dataagent.dashscope.api-key:}")
    private String dashscopeApiKey;

    @Value("${dataagent.dashscope.model-name:qwen-max}")
    private String dashscopeModelName;

    @Value("${dataagent.dashscope.stream:true}")
    private boolean dashscopeStream;

    @Value(
            "${dataagent.agent.sys-prompt:You are a Data Agent built with AgentScope."
                    + " You help users explore, analyse, visualise and report on data.}")
    private String agentSysPrompt;

    @Value("${dataagent.agent.name:data-agent}")
    private String agentName;

    @Value("${dataagent.workspace:}")
    private String workspaceDir;

    // -----------------------------------------------------------------
    //  Model bean — only created when an api-key is set AND no other
    //  Model bean is already present in the context. Skipped when the
    //  property is blank so Optional<Model> injection sites receive
    //  Optional.empty().
    // -----------------------------------------------------------------

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .findAndRegisterModules()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Creates a {@link DashScopeChatModel} bean when {@code dataagent.dashscope.api-key} is
     * configured and no other {@link Model} bean is present. Skipped entirely when the property is
     * blank so that {@code Optional<Model>} injection sites receive {@code Optional.empty()}
     * instead of a null-valued bean.
     */
    @Bean
    @ConditionalOnMissingBean(Model.class)
    @ConditionalOnExpression("'${dataagent.dashscope.api-key:}' != ''")
    public Model dashscopeModel() {
        log.info("Building DashScopeChatModel: model={}", dashscopeModelName);
        return DashScopeChatModel.builder()
                .apiKey(dashscopeApiKey)
                .modelName(dashscopeModelName)
                .stream(dashscopeStream)
                .build();
    }

    // -----------------------------------------------------------------
    //  Core bootstrap — model injected as method parameter (no field
    //  @Autowired) to avoid circular dependency with dashscopeModel() above.
    // -----------------------------------------------------------------

    /**
     * Assembles the {@link DataAgentBootstrap}, loading agent config from {@code agentscope.json}
     * and starting the {@link ChatUiChannel} for per-user isolated sessions.
     *
     * @param modelOpt the {@link Model} to use, or empty if none is configured
     * @param toolEventBus the shared tool-event bus for real-time SSE streaming of tool calls
     */
    @Bean
    public DataAgentBootstrap builderBootstrap(
            Optional<Model> modelOpt,
            ToolEventBus toolEventBus,
            Optional<AgentStateStore> sessionOpt)
            throws IOException {
        Path cwd = resolveCwd();
        ensureAgentscopeConfig();

        DataAgentBootstrap.Builder builder = DataAgentBootstrap.builder().cwd(cwd);

        if (modelOpt.isPresent()) {
            builder.model(modelOpt.get());
        } else {
            log.warn(
                    "No model configured. Set dataagent.dashscope.api-key in application.yml or"
                            + " provide a Model bean. Agent calls will fail until a model is"
                            + " available.");
        }

        // Conversation persistence is independent of the local workspace filesystem. Operators
        // should still provide a distributed AgentStateStore bean for production so conversation
        // state survives process restarts.
        AgentStateStore stateStore = sessionOpt.orElseGet(InMemoryAgentStateStore::new);
        if (sessionOpt.isEmpty()) {
            log.warn(
                    "No distributed AgentStateStore bean configured ({}); using"
                            + " InMemoryAgentStateStore. For multi-replica deployments, provide"
                            + " a DistributedStore or a distributed AgentStateStore bean"
                            + " (e.g. from agentscope-extensions-redis).",
                    AgentStateStore.class.getName());
        }

        builder.configureAllAgents(
                b -> {
                    b.middleware(new ToolNotificationMiddleware(toolEventBus));
                    b.stateStore(stateStore);
                    b.filesystem(
                            new LocalFilesystemSpec()
                                    .isolationScope(IsolationScope.USER)
                                    .projectWritable(true)
                                    .virtualMode(false));
                    b.disableShellTool();
                });

        DataAgentBootstrap bootstrap = builder.build();

        // Build the chatui channel using the file-config's bindings & dmScope (if any),
        // so admin-edited bindings in agentscope.json are honored. Falls back to PER_PEER
        // when no chatui entry exists.
        ChannelConfigEntry ce =
                bootstrap.loadedConfig().getChannels() != null
                        ? bootstrap.loadedConfig().getChannels().get(ChatUiChannel.CHANNEL_ID)
                        : null;
        ChannelConfig chatuiCfg =
                ce != null
                        ? ce.toChannelConfig(ChatUiChannel.CHANNEL_ID)
                        : ChannelConfig.builder(ChatUiChannel.CHANNEL_ID)
                                .dmScope(DmScope.PER_PEER)
                                .build();
        ChatUiChannel webChannel = ChatUiChannel.create(chatuiCfg);
        bootstrap.start(webChannel);

        log.info(
                "DataAgentBootstrap initialized: cwd={}, chatui dmScope={}, bindings={}",
                cwd,
                chatuiCfg.dmScope(),
                chatuiCfg.bindings().size());
        return bootstrap;
    }

    /**
     * Registers the {@link LocalApprovalMarketplace} factory under the {@code "local"} type so
     * {@code UserMarketplaceRegistry} can hydrate per-user marketplaces backed by approved
     * contributions on disk.
     *
     * <p>The factory reads from {@code ${dataagent.shared-root}/agents/data-agent/skills} — the
     * per-agent slice for the built-in {@code data-agent}. Approved skills become visible to that
     * agent's local workspaces without extra wiring. Skills approved for other agents live under
     * their own {@code shared/agents/<agentId>/skills/} slices; this local marketplace does not
     * cross-list them.
     */
    @Bean
    public DataAgentMarketplaceFactoryRegistration localMarketplaceFactory(
            DataAgentBootstrap bootstrap) {
        Path sharedSkills =
                bootstrap
                        .cwd()
                        .resolve("shared")
                        .resolve("agents")
                        .resolve("data-agent")
                        .resolve("skills");
        return new DataAgentMarketplaceFactoryRegistration(
                LocalApprovalMarketplace.TYPE,
                (userId, id, props, wsf) -> new LocalApprovalMarketplace(id, sharedSkills));
    }

    /**
     * Registers the {@link GitDataAgentMarketplace} factory under the {@code "git"} type. Each
     * per-user marketplace gets its own clone target under
     * {@code ${dataagent.workspace}/.cache/marketplaces/{userId}/{marketplaceId}} so distinct
     * users configuring the same upstream do not contend on a shared working copy.
     *
     * <p>Properties: {@code remoteUrl} (required), {@code branch} (optional).
     */
    @Bean
    public DataAgentMarketplaceFactoryRegistration gitMarketplaceFactory(
            DataAgentBootstrap bootstrap) {
        Path cacheRoot = bootstrap.cwd().resolve(".cache").resolve("marketplaces");
        return new DataAgentMarketplaceFactoryRegistration(
                GitDataAgentMarketplace.TYPE,
                (userId, id, props, wsf) -> {
                    String remoteUrl = stringProp(props, "remoteUrl");
                    if (remoteUrl == null || remoteUrl.isBlank()) {
                        throw new IllegalArgumentException(
                                "git marketplace '" + id + "' requires property 'remoteUrl'");
                    }
                    String branch = stringProp(props, "branch");
                    Path clone = cacheRoot.resolve(userId).resolve(id);
                    return new GitDataAgentMarketplace(id, remoteUrl, branch, clone);
                });
    }

    /**
     * Registers the {@link NacosDataAgentMarketplace} factory under the {@code "nacos"} type.
     *
     * <p>Properties: {@code serverAddr} (required), {@code namespaceId} (optional, defaults to
     * {@code "public"}), {@code username} / {@code password}, {@code accessKey} / {@code
     * secretKey}.
     */
    @Bean
    public DataAgentMarketplaceFactoryRegistration nacosMarketplaceFactory() {
        return new DataAgentMarketplaceFactoryRegistration(
                NacosDataAgentMarketplace.TYPE,
                (userId, id, props, wsf) -> {
                    String serverAddr = stringProp(props, "serverAddr");
                    if (serverAddr == null || serverAddr.isBlank()) {
                        throw new IllegalArgumentException(
                                "nacos marketplace '" + id + "' requires property 'serverAddr'");
                    }
                    return new NacosDataAgentMarketplace(
                            id,
                            serverAddr,
                            stringProp(props, "namespaceId"),
                            stringProp(props, "username"),
                            stringProp(props, "password"),
                            stringProp(props, "accessKey"),
                            stringProp(props, "secretKey"));
                });
    }

    private static String stringProp(java.util.Map<String, Object> props, String key) {
        if (props == null) return null;
        Object v = props.get(key);
        return v == null ? null : v.toString();
    }

    @Bean
    public io.agentscope.dataagent.web.identity.IdentityLinkStore identityLinkStore(
            DataAgentBootstrap bootstrap) {
        Path agentscopeDir = bootstrap.cwd().resolve(".agentscope");
        return new io.agentscope.dataagent.web.identity.IdentityLinkStore(agentscopeDir);
    }

    @Bean
    public ChatUiChannel chatUiChannel(DataAgentBootstrap bootstrap) {
        return (ChatUiChannel)
                bootstrap
                        .channelManager()
                        .getChannel(ChatUiChannel.CHANNEL_ID)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "ChatUiChannel not registered in ChannelManager"));
    }

    // -----------------------------------------------------------------
    //  Internal helpers
    // -----------------------------------------------------------------

    private Path resolveCwd() {
        if (workspaceDir != null && !workspaceDir.isBlank()) {
            return Paths.get(workspaceDir).toAbsolutePath().normalize();
        }
        return Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    /**
     * Auto-generates a minimal {@code ~/.agentscope/dataagent/agentscope.json} if it doesn't
     * exist, so the app can start without manual setup. The generated config defines a single
     * GLOBAL {@code data-agent} pre-wired with the {@code chatui} channel and lets the bootstrap
     * fall through to {@link DataAgentBootstrap#DEFAULT_WORKSPACE_ROOT} for the workspace
     * location.
     *
     * <p>The workspace root is the shared seed (template content, default {@code AGENTS.md} /
     * {@code skills/} / {@code subagents/} / {@code knowledge/} shipped on disk). Per-user local
     * workspaces are created from this seed on demand.
     */
    private void ensureAgentscopeConfig() throws IOException {
        Path configFile = DataAgentBootstrap.DEFAULT_CONFIG_PATH;
        Path workspaceRoot = DataAgentBootstrap.DEFAULT_WORKSPACE_ROOT;

        if (Files.exists(configFile)) {
            return;
        }

        Files.createDirectories(configFile.getParent());
        Files.createDirectories(workspaceRoot);

        String agentsJson =
                """
                {
                  "main": "data-agent",
                  "agents": {
                    "data-agent": {
                      "name": "Data Agent",
                      "description": "Tenant-isolated data-analysis assistant. Connects to internal SQL sources, drafts queries, validates results, and renders charts.",
                      "maxIters": 20
                    }
                  },
                  "channels": {
                    "chatui": {
                      "defaultAgentId": "data-agent",
                      "dmScope": "MAIN"
                    }
                  }
                }
                """;

        Files.writeString(configFile, agentsJson);
        log.info("Auto-generated DataAgent config at {}", configFile);

        io.agentscope.dataagent.web.scaffold.WorkspaceScaffolder.scaffold(
                workspaceRoot, "Data Agent", agentSysPrompt);
    }
}

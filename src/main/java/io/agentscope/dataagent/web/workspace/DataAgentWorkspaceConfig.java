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
package io.agentscope.dataagent.web.workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the per-tenant workspace filesystem used by every per-agent
 * {@code WorkspaceManager}.
 *
 * <p>In the current local-only downgrade, browser-side workspace CRUD is isolated by giving each
 * {@code (userId, agentId)} pair its own host-side workspace directory. Shared seed content
 * (AGENTS.md / skills/ / subagents/ / knowledge/) is copied into each new user workspace on first
 * access.
 */
@Configuration
public class DataAgentWorkspaceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataAgentWorkspaceConfig.class);

    @Bean
    public WorkspaceManagerFactory workspaceManagerFactory() {
        log.info("Wiring local WorkspaceManagerFactory without Docker sandbox dependency");
        return new WorkspaceManagerFactory();
    }
}

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

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.dataagent.runtime.DataAgentBootstrap;
import io.agentscope.core.agent.RuntimeContext;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

class WorkspaceManagerFactoryTest {

    @Test
    void defaultsToSharedWorkspaceRootWhenNoWorkspacePathProvided() {
        WorkspaceManagerFactory factory =
                new WorkspaceManagerFactory(Mockito.mock(UserSandboxRegistry.class));

        assertThat(factory.resolveAgentDataPath(null, "data-agent"))
                .isEqualTo(DataAgentBootstrap.DEFAULT_WORKSPACE_ROOT.normalize());
    }

    @Test
    void createsPerUserLocalWorkspaceAndCopiesSharedSeed(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("AGENTS.md"), "# Seed");
        Files.createDirectories(tempDir.resolve("skills").resolve("starter"));
        Files.writeString(tempDir.resolve("skills").resolve("starter").resolve("SKILL.md"), "seed");

        WorkspaceManagerFactory factory =
                new WorkspaceManagerFactory(Mockito.mock(UserSandboxRegistry.class));

        var alice = factory.forAgent("alice", "data-agent", tempDir.toString());
        var bob = factory.forAgent("bob", "data-agent", tempDir.toString());
        try {
            assertThat(alice.getWorkspace()).isEqualTo(tempDir.resolve("alice"));
            assertThat(bob.getWorkspace()).isEqualTo(tempDir.resolve("bob"));
            assertThat(alice.getFilesystem().exists(RuntimeContext.empty(), "AGENTS.md")).isTrue();
            assertThat(
                            alice.getFilesystem()
                                    .exists(RuntimeContext.empty(), "skills/starter/SKILL.md"))
                    .isTrue();

            alice.writeUtf8WorkspaceRelative(RuntimeContext.empty(), "notes.txt", "alice-only");

            assertThat(
                            alice.getFilesystem()
                                    .read(RuntimeContext.empty(), "notes.txt", 0, 0)
                                    .fileData()
                                    .content())
                    .isEqualTo("alice-only");
            assertThat(
                            bob.getFilesystem()
                                    .read(RuntimeContext.empty(), "notes.txt", 0, 0)
                                    .isSuccess())
                    .isFalse();
        } finally {
            alice.close();
            bob.close();
        }
    }
}

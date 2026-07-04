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

import io.agentscope.dataagent.runtime.DataAgentBootstrap;
import io.agentscope.harness.agent.filesystem.AbstractFilesystem;
import io.agentscope.harness.agent.filesystem.remote.store.NamespaceFactory;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import io.agentscope.harness.agent.workspace.WorkspaceManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Builds {@link WorkspaceManager} instances whose filesystem is backed by a per-{@code (userId,
 * agentId)} local workspace directory.
 *
 * <p>In local mode, each user gets their own subtree under the agent workspace root. Both the
 * workspace label and the writable filesystem now point at that same per-user directory so the
 * browser-facing workspace CRUD stays isolated without requiring Docker.
 */
public final class WorkspaceManagerFactory {

    private static final List<String> SHARED_SEED_ROOTS =
            List.of("AGENTS.md", "skills", "subagents", "knowledge");

    public WorkspaceManagerFactory() {}

    /**
     * Backwards-compatible constructor kept so existing tests/wiring can instantiate the factory
     * without caring whether workspace mode is sandbox-backed or local.
     */
    public WorkspaceManagerFactory(UserSandboxRegistry ignoredRegistry) {}

    /**
     * Returns a {@link WorkspaceManager} for a user-scoped agent. Equivalent to
     * {@link #forAgent(String, String, String)} with {@code workspacePath=null}.
     */
    public WorkspaceManager forAgent(String ownerId, String agentId) {
        return forAgent(ownerId, agentId, null);
    }

    /**
     * Returns a {@link WorkspaceManager} for a user-scoped agent. The returned manager reads and
     * writes through a local filesystem namespace dedicated to that owner.
     */
    public WorkspaceManager forAgent(String ownerId, String agentId, String workspacePath) {
        validateSegment("ownerId", ownerId);
        validateSegment("agentId", agentId);
        Path baseRoot = resolveAgentDataPath(workspacePath, agentId);
        Path userRoot = ensureUserWorkspace(baseRoot, ownerId);
        return new WorkspaceManager(userRoot, localFilesystem(userRoot));
    }

    /**
     * Returns a {@link WorkspaceManager} for a global agent accessed by a specific user.
     * Equivalent to {@link #forAgent(String, String, String)} — once the filesystem layer is
     * local-namespaced, the global/user distinction disappears because the backing filesystem is
     * still keyed by {@code (userId, agentId)}. Kept as a separate entry point for call-site
     * readability.
     */
    public WorkspaceManager forGlobalAgent(String userId, String agentId) {
        return forAgent(userId, agentId, null);
    }

    /** See {@link #forGlobalAgent(String, String)}. */
    public WorkspaceManager forGlobalAgent(String userId, String agentId, String workspacePath) {
        return forAgent(userId, agentId, workspacePath);
    }

    /**
     * Returns the raw per-{@code (userId, agentId)} {@link AbstractFilesystem} without the
     * {@link WorkspaceManager} wrapper, suitable for callers that need to enumerate / copy files
     * inside the user's isolated workspace (notably the audit/activity log and
     * {@link io.agentscope.dataagent.web.util.WorkspaceCopier}).
     */
    public AbstractFilesystem userDataFs(String ownerId, String agentId, String workspacePath) {
        validateSegment("ownerId", ownerId);
        validateSegment("agentId", agentId);
        Path baseRoot = resolveAgentDataPath(workspacePath, agentId);
        Path userRoot = ensureUserWorkspace(baseRoot, ownerId);
        return localFilesystem(userRoot);
    }

    /**
     * Path prefix under which {@link #userDataFs(String, String, String)} reports file paths.
     * With local per-user namespacing the prefix is still the logical root ({@code "/"}). Kept on
     * the API surface to avoid forcing call-site
     * changes during the migration.
     */
    public String userDataPathPrefix(String ownerId, String agentId, String workspacePath) {
        validateSegment("ownerId", ownerId);
        validateSegment("agentId", agentId);
        return "/";
    }

    /**
     * Resolves the user-supplied workspace path for an agent into an absolute host-side data
     * root, mirroring the pre-sandbox behaviour so {@link WorkspaceManager#getWorkspace()} keeps
     * returning the same labels (audit logs, UI display).
     *
     * <ul>
     *   <li>If {@code workspacePath} is null or blank, {@code fallbackAgentId} is used in its
     *       place.
     *   <li>Absolute paths are returned normalized.
     *   <li>Relative paths that already start under {@code ${cwd}/.agentscope/} are used as-is.
     *   <li>Other relative paths are resolved against {@code ${cwd}/.agentscope/}.
     * </ul>
     */
    public Path resolveAgentDataPath(String workspacePath, String fallbackAgentId) {
        if (workspacePath == null || workspacePath.isBlank()) {
            return DataAgentBootstrap.DEFAULT_WORKSPACE_ROOT.toAbsolutePath().normalize();
        }
        String raw = workspacePath.trim();
        Path p = Paths.get(raw);
        if (p.isAbsolute()) {
            return p.normalize();
        }
        Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path agentScopeBase = cwd.resolve(".agentscope").normalize();
        Path resolvedAgainstCwd = cwd.resolve(p).normalize();
        if (resolvedAgainstCwd.startsWith(agentScopeBase)) {
            return resolvedAgainstCwd;
        }
        return agentScopeBase.resolve(p).normalize();
    }

    private AbstractFilesystem localFilesystem(Path userRoot) {
        LocalFilesystemSpec spec =
                new LocalFilesystemSpec()
                        .project(userRoot)
                        .projectWritable(true)
                        .virtualMode(false);
        NamespaceFactory noNamespace = runtimeContext -> List.of();
        return spec.toFilesystem(userRoot, noNamespace);
    }

    private Path ensureUserWorkspace(Path baseRoot, String ownerId) {
        try {
            Files.createDirectories(baseRoot);
            Path userRoot = baseRoot.resolve(ownerId).normalize();
            if (!Files.exists(userRoot)) {
                Files.createDirectories(userRoot);
                seedUserWorkspace(baseRoot, userRoot);
            }
            return userRoot;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to prepare local workspace for user '" + ownerId + "': " + e.getMessage(),
                    e);
        }
    }

    private void seedUserWorkspace(Path baseRoot, Path userRoot) throws IOException {
        for (String entry : SHARED_SEED_ROOTS) {
            Path source = baseRoot.resolve(entry).normalize();
            Path target = userRoot.resolve(entry).normalize();
            if (!Files.exists(source)) {
                if (!entry.contains(".")) {
                    Files.createDirectories(target);
                }
                continue;
            }
            if (Files.isDirectory(source)) {
                copyDirectory(source, target);
            } else {
                Files.createDirectories(target.getParent());
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source)
                .forEach(
                        path -> {
                            try {
                                Path relative = source.relativize(path);
                                Path destination = target.resolve(relative).normalize();
                                if (Files.isDirectory(path)) {
                                    Files.createDirectories(destination);
                                } else {
                                    Files.createDirectories(destination.getParent());
                                    Files.copy(
                                            path,
                                            destination,
                                            StandardCopyOption.REPLACE_EXISTING);
                                }
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
    }

    private static void validateSegment(String label, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be null or blank");
        }
        if (value.contains("/") || value.contains("\\") || value.contains("..")) {
            throw new IllegalArgumentException(
                    label + " must not contain path separators or '..': " + value);
        }
    }
}

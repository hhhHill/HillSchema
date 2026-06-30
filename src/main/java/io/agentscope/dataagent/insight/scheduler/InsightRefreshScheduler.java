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
package io.agentscope.dataagent.insight.scheduler;

import io.agentscope.dataagent.insight.config.InsightProperties;
import io.agentscope.dataagent.insight.service.InsightRefreshService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Fixed-rate background scheduler for the automatic insight refresh loop. */
@Component
public class InsightRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(InsightRefreshScheduler.class);

    private final InsightProperties properties;
    private final InsightRefreshService refreshService;
    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(
                    runnable -> {
                        Thread thread = new Thread(runnable, "hillschema-insight-refresh");
                        thread.setDaemon(true);
                        return thread;
                    });

    public InsightRefreshScheduler(
            InsightProperties properties, InsightRefreshService refreshService) {
        this.properties = properties;
        this.refreshService = refreshService;
    }

    @PostConstruct
    public void start() {
        if (!properties.isEnabled()) {
            log.info("Insight refresh scheduler disabled by configuration");
            return;
        }
        long intervalMs = Math.max(properties.getRefreshInterval().toMillis(), 60_000L);
        executor.scheduleAtFixedRate(
                () -> {
                    try {
                        refreshService.refreshNow("scheduled");
                    } catch (Exception e) {
                        log.warn("Insight refresh execution failed: {}", e.getMessage(), e);
                    }
                },
                intervalMs,
                intervalMs,
                TimeUnit.MILLISECONDS);
        log.info("Insight refresh scheduler enabled: interval={}ms", intervalMs);
    }

    @PreDestroy
    public void stop() {
        executor.shutdownNow();
    }
}

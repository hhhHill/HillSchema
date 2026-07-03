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
package io.agentscope.dataagent.web.marketplace;

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.dataagent.runtime.DataAgentBootstrap;
import io.agentscope.dataagent.web.persistence.jpa.ContributionRepository;
import io.agentscope.dataagent.web.workspace.UserSandboxRegistry;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class MarketContributionServiceWiringTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(MarketContributionServiceConfig.class);

    @Test
    void marketContributionServiceDoesNotRequireObjectMapperBean() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(MarketContributionService.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class MarketContributionServiceConfig {

        @Bean
        ContributionRepository contributionRepository() {
            return Mockito.mock(ContributionRepository.class);
        }

        @Bean
        DataAgentBootstrap dataAgentBootstrap() {
            DataAgentBootstrap bootstrap = Mockito.mock(DataAgentBootstrap.class);
            Mockito.when(bootstrap.cwd()).thenReturn(Path.of(".").toAbsolutePath().normalize());
            return bootstrap;
        }

        @Bean
        UserSandboxRegistry userSandboxRegistry() {
            return Mockito.mock(UserSandboxRegistry.class);
        }

        @Bean
        MarketContributionService marketContributionService(
                ContributionRepository repository,
                DataAgentBootstrap bootstrap,
                UserSandboxRegistry sandboxRegistry) {
            return new MarketContributionService(repository, bootstrap, sandboxRegistry);
        }
    }
}

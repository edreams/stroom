/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.spring;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.CustomScopeConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import stroom.util.spring.StroomScope;

@Configuration
public class ScopeTestConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScopeTestConfiguration.class);

    public ScopeTestConfiguration() {
        LOGGER.info("ScopeConfiguration loading...");
    }

    @Bean
    public static CustomScopeConfigurer customScopeConfigurer() {
        final CustomScopeConfigurer customScopeConfigurer = new CustomScopeConfigurer();
        final Map<String, Object> scopes = new HashMap<>();
        scopes.put(StroomScope.TASK, "stroom.util.task.TaskScope");
        scopes.put(StroomScope.THREAD, "stroom.util.thread.ThreadScope");
        // Add a dummy scope because we don't have web scopes.
        scopes.put("request", "stroom.spring.DummyScope");
        scopes.put("session", "stroom.spring.DummyScope");
        customScopeConfigurer.setScopes(scopes);
        return customScopeConfigurer;
    }
}

/*
 * Copyright 2018 Crown Copyright
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

package stroom.policy;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.dictionary.DictionaryStore;
import stroom.entity.StroomEntityManager;
import stroom.jobsystem.ClusterLockService;
import stroom.properties.StroomPropertyService;
import stroom.task.TaskHandler;
import stroom.util.spring.StroomScope;
import stroom.task.TaskContext;

import javax.sql.DataSource;

public class PolicyModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(PolicyService.class).to(PolicyServiceImpl.class);

        final Multibinder<TaskHandler> taskHandlerBinder = Multibinder.newSetBinder(binder(), TaskHandler.class);
        taskHandlerBinder.addBinding().to(stroom.policy.FetchDataRetentionPolicyHandler.class);
        taskHandlerBinder.addBinding().to(stroom.policy.SaveDataRetentionPolicyHandler.class);
    }
}
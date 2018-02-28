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

package stroom.importexport;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.entity.GenericEntityService;
import stroom.explorer.ExplorerNodeService;
import stroom.explorer.ExplorerService;
import stroom.logging.StroomEventLoggingService;
import stroom.properties.StroomPropertyService;
import stroom.security.SecurityContext;
import stroom.servlet.SessionResourceStore;
import stroom.util.spring.StroomBeanStore;
import stroom.util.spring.StroomScope;

@Configuration
public class ImportExportClientSpringConfig {
    @Bean
    @Scope(value = StroomScope.TASK)
    public ExportConfigHandler exportConfigHandler(final ImportExportService importExportService,
                                                   final stroom.logging.ImportExportEventLog eventLog,
                                                   final SessionResourceStore sessionResourceStore) {
        return new ExportConfigHandler(importExportService, eventLog, sessionResourceStore);
    }

    @Bean
    @Scope(value = StroomScope.PROTOTYPE)
    public FetchDependenciesHandler fetchDependenciesHandler(final DependencyService dependencyService) {
        return new FetchDependenciesHandler(dependencyService);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public ImportConfigConfirmationHandler importConfigConfirmationHandler(final ImportExportService importExportService,
                                                                           final SessionResourceStore sessionResourceStore) {
        return new ImportConfigConfirmationHandler(importExportService, sessionResourceStore);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public ImportConfigHandler importConfigHandler(final ImportExportService importExportService,
                                                   final stroom.logging.ImportExportEventLog eventLog,
                                                   final SessionResourceStore sessionResourceStore) {
        return new ImportConfigHandler(importExportService, eventLog, sessionResourceStore);
    }
}
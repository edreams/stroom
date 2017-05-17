/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.startup;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;
import stroom.resources.HasHealthCheck;

public class HealthChecks {

    public HealthChecks(HealthCheckRegistry healthCheckRegistry, Resources resources){

        resources.getResources().stream()
                .filter(resource -> resource instanceof HasHealthCheck)
                .forEach(resource ->
                    healthCheckRegistry.register(resource.getName() + "HealthCheck", new HealthCheck() {
                        @Override
                        protected Result check() throws Exception {
                            return ((HasHealthCheck)resource).getHealth();
                        }
                    })
                );
    }
}

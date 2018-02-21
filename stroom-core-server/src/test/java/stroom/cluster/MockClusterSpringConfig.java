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

package stroom.cluster;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import stroom.cluster.server.MockClusterNodeManager;
import stroom.node.server.NodeCache;
import stroom.util.spring.StroomSpringProfiles;


@Configuration
public class MockClusterSpringConfig {

    @Bean
    @Profile(StroomSpringProfiles.IT)
    public MockClusterNodeManager mockClusterNodeManager(final NodeCache nodeCache) {
        return new MockClusterNodeManager(nodeCache);
    }

}
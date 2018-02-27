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

package stroom.node;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MockNodeServiceSpringConfig {
//    @Bean
////    @Profile(StroomSpringProfiles.TEST)
//    public MockGlobalPropertyService mockGlobalPropertyService() {
//        return new MockGlobalPropertyService();
//    }


    @Bean("nodeService")
    public NodeService nodeService() {
        return new MockNodeService();
    }

    @Bean("cachedNodeService")
    public NodeService cachedNodeService(final NodeService nodeService) {
        return nodeService;
    }

//    @Bean
////    @Profile(StroomSpringProfiles.TEST)
//    public MockRecordCountService mockRecordCountService() {
//        return new MockRecordCountService();
//    }
}
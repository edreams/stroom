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

package stroom.volume;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import stroom.entity.StroomEntityManager;
import stroom.node.NodeCache;
import stroom.node.VolumeService;
import stroom.properties.StroomPropertyService;
import stroom.statistics.internal.InternalStatisticsReceiver;
import stroom.guice.StroomBeanStore;

import javax.inject.Provider;

@Configuration
public class VolumeSpringConfig {
//    @Bean("volumeService")
//    public VolumeService volumeService(final StroomEntityManager stroomEntityManager,
//                                       final NodeCache nodeCache,
//                                       final StroomPropertyService stroomPropertyService,
//                                       final StroomBeanStore stroomBeanStore,
//                                       final Provider<InternalStatisticsReceiver> internalStatisticsReceiverProvider) {
//        return new VolumeServiceImpl(stroomEntityManager, nodeCache, stroomPropertyService, stroomBeanStore, internalStatisticsReceiverProvider);
//    }
}
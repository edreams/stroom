/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.streamstore;

import stroom.entity.MockNamedEntityService;
import stroom.streamstore.shared.FindStreamTypeCriteria;
import stroom.streamstore.shared.StreamType;

import javax.inject.Singleton;

@Singleton
class MockStreamTypeService extends MockNamedEntityService<StreamType, FindStreamTypeCriteria>
        implements StreamTypeService {
    MockStreamTypeService() {
        for (final StreamType streamType : StreamType.initialValues()) {
            save(streamType);
        }
    }

    @Override
    public void clear() {
        // Do nothing as we don't want to loose stream types set in constructor.
    }

    @Override
    public String getNamePattern() {
        return null;
    }

    @Override
    public Class<StreamType> getEntityClass() {
        return StreamType.class;
    }
}

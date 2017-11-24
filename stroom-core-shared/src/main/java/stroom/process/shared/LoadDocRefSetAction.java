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
 */

package stroom.process.shared;

import stroom.entity.shared.Action;
import stroom.entity.shared.DocRefSet;
import stroom.entity.shared.DocRefs;
import stroom.util.shared.SharedMap;

public class LoadDocRefSetAction extends Action<SharedMap<SetId, DocRefs>> {
    private static final long serialVersionUID = -1773544031158236156L;

    private SharedMap<SetId, DocRefSet> entitySetMap;

    public LoadDocRefSetAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public LoadDocRefSetAction(final SharedMap<SetId, DocRefSet> entitySetMap) {
        this.entitySetMap = entitySetMap;
    }

    public SharedMap<SetId, DocRefSet> getEntitySetMap() {
        return entitySetMap;
    }

    public void setEntitySetMap(final SharedMap<SetId, DocRefSet> entitySetMap) {
        this.entitySetMap = entitySetMap;
    }

    @Override
    public String getTaskName() {
        return "LoadDocRefSetAction";
    }
}

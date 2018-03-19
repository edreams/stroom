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

package stroom.entity.util;

import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.NamedEntity;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class BaseEntityUtil {
    public static <T extends BaseEntity> void sort(final List<T> list) {
        Collections.sort(list, (o1, o2) -> Long.compare(o1.getId(), o2.getId()));
    }

    /**
     * Find a entity in a collection by it's id.
     */
    public static <T extends NamedEntity> T findByName(final Collection<T> collection, final String name) {
        for (final T entity : collection) {
            if (name.equals(entity.getName())) {
                return entity;
            }
        }
        return null;
    }
}

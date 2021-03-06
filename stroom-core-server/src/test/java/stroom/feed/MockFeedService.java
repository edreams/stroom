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

package stroom.feed;

import stroom.entity.MockDocumentEntityService;
import stroom.entity.shared.BaseResultList;
import stroom.explorer.ExplorerActionHandler;
import stroom.explorer.shared.DocumentType;
import stroom.feed.shared.Feed;
import stroom.feed.shared.FindFeedCriteria;
import stroom.importexport.ImportExportActionHandler;
import stroom.importexport.ImportExportHelper;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * <p>
 * Very simple mock that keeps everything in memory.
 * </p>
 * <p>
 * <p>
 * You can call clear at any point to clear everything down.
 * </p>
 */
@Singleton
public class MockFeedService extends MockDocumentEntityService<Feed, FindFeedCriteria> implements FeedService, ExplorerActionHandler, ImportExportActionHandler {
    public MockFeedService() {
    }

    @Inject
    MockFeedService(final ImportExportHelper importExportHelper) {
        super(importExportHelper);
    }

    @Override
    public Class<Feed> getEntityClass() {
        return Feed.class;
    }

    @Override
    public DocumentType getDocumentType() {
        return new DocumentType(3, Feed.ENTITY_TYPE, Feed.ENTITY_TYPE);
    }

    @Override
    public Feed loadByName(final String name) {
        BaseResultList<Feed> list = find(createCriteria());
        for (final Feed feed : list) {
            if (feed.getName().equals(name)) {
                return feed;
            }
        }

        return null;
    }

    @Override
    public String getDisplayClassification(final Feed feed) {
        return null;
    }
}

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

package stroom.refdata;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import stroom.entity.shared.DocRefUtil;
import stroom.feed.shared.Feed;
import stroom.pipeline.shared.PipelineDocument;
import stroom.util.cache.CacheManager;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;
import stroom.xml.event.EventList;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestCache extends StroomUnitTest {
    private static final int MAX_CACHE_ITEMS = 200;

    @Test
    public void testReferenceDataCache() {
        try (CacheManager cacheManager = new CacheManager()) {
            final ReferenceDataLoader referenceDataLoader = new ReferenceDataLoader() {
                @Override
                public MapStore load(final MapStoreCacheKey effectiveFeed) {
                    return MapStoreTestUtil.createMapStore();
                }
            };
            final MapStoreCache mapStoreCache = new MapStoreCache(cacheManager, referenceDataLoader, null, null);

            String eventString = null;

            PipelineDocument pipelineDocument = new PipelineDocument();
            pipelineDocument.setUuid("12345");

            long time = System.currentTimeMillis();
            for (int i = 0; i < MAX_CACHE_ITEMS; i++) {
                final Feed feed = new Feed();
                feed.setName("test " + i);
                feed.setReference(true);
                feed.setId(i);
                final MapStoreCacheKey mapStorePoolKey = new MapStoreCacheKey(DocRefUtil.create(pipelineDocument), 1);
                final MapStore mapStore = mapStoreCache.get(mapStorePoolKey);
                final EventList eventList = mapStore.getEvents("TEST_MAP_NAME", "TEST_KEY_NAME");
                if (eventString == null) {
                    eventString = eventList.toString();
                }

                Assert.assertEquals(eventString, eventList.toString());
            }
            System.out.println("Put time = " + (System.currentTimeMillis() - time));

            time = System.currentTimeMillis();
            for (int i = 0; i < MAX_CACHE_ITEMS; i++) {
                final Feed feed = new Feed();
                feed.setName("test " + i);
                feed.setReference(true);
                feed.setId(i);
                final MapStoreCacheKey mapStoreCacheKey = new MapStoreCacheKey(DocRefUtil.create(pipelineDocument), 1);
                final MapStore mapStore = mapStoreCache.get(mapStoreCacheKey);
                final EventList eventList = mapStore.getEvents("TEST_MAP_NAME", "TEST_KEY_NAME");
                if (eventString == null) {
                    eventString = eventList.toString();
                }

                Assert.assertEquals(eventString, eventList.toString());
            }
            System.out.println("Get time = " + (System.currentTimeMillis() - time));

            // // Set the reference data loader factory back.
        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}

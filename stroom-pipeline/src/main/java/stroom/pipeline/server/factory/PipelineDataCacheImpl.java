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

package stroom.pipeline.server.factory;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.springframework.stereotype.Component;
import stroom.entity.server.DocumentPermissionCache;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.PermissionException;
import stroom.pipeline.shared.PipelineDataMerger;
import stroom.pipeline.shared.PipelineDocument;
import stroom.pipeline.shared.PipelineModelException;
import stroom.pipeline.shared.data.PipelineData;
import stroom.security.SecurityHelper;
import stroom.pool.VersionedEntityDecorator;
import stroom.security.Insecure;
import stroom.security.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.cache.CacheManager;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Insecure
@Component
public class PipelineDataCacheImpl implements PipelineDataCache {
    private static final int MAX_CACHE_ENTRIES = 1000;

    private final PipelineStackLoader pipelineStackLoader;
    private final LoadingCache<VersionedEntityDecorator<PipelineDocument>, PipelineData> cache;
    private final SecurityContext securityContext;
    private final DocumentPermissionCache documentPermissionCache;

    @Inject
    @SuppressWarnings("unchecked")
    public PipelineDataCacheImpl(final CacheManager cacheManager,
                                 final PipelineStackLoader pipelineStackLoader,
                                 final SecurityContext securityContext,
                                 final DocumentPermissionCache documentPermissionCache) {
        this.pipelineStackLoader = pipelineStackLoader;
        this.securityContext = securityContext;
        this.documentPermissionCache = documentPermissionCache;

        final CacheLoader<VersionedEntityDecorator<PipelineDocument>, PipelineData> cacheLoader = CacheLoader.from(this::create);
        final CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
                .maximumSize(MAX_CACHE_ENTRIES)
                .expireAfterAccess(10, TimeUnit.MINUTES);
        cache = cacheBuilder.build(cacheLoader);
        cacheManager.registerCache("Pipeline Structure Cache", cacheBuilder, cache);
    }

    @Override
    public PipelineData get(final PipelineDocument pipelineDocument) {
        if (!documentPermissionCache.hasDocumentPermission(pipelineDocument.getType(), pipelineDocument.getUuid(), DocumentPermissionNames.USE)) {
            throw new PermissionException(securityContext.getUserId(), "You do not have permission to use " + DocRefUtil.create(pipelineDocument));
        }

        return cache.getUnchecked(new VersionedEntityDecorator<>(pipelineDocument));
    }

    private PipelineData create(final VersionedEntityDecorator key) {
        try (SecurityHelper securityHelper = SecurityHelper.processingUser(securityContext)) {
            final PipelineDocument pipelineDocument = (PipelineDocument) key.getEntity();
            final List<PipelineDocument> pipelines = pipelineStackLoader.loadPipelineStack(pipelineDocument);
            // Iterate over the pipeline list reading the deepest ancestor first.
            final List<PipelineData> configStack = new ArrayList<>(pipelines.size());

            for (final PipelineDocument pipe : pipelines) {
                final PipelineData pipelineData = pipe.getPipelineData();
                if (pipelineData != null) {
                    configStack.add(pipelineData);
                }
            }

            final PipelineDataMerger pipelineDataMerger = new PipelineDataMerger();
            try {
                pipelineDataMerger.merge(configStack);
            } catch (final PipelineModelException e) {
                throw new PipelineFactoryException(e);
            }

            return pipelineDataMerger.createMergedData();
        }
    }
}

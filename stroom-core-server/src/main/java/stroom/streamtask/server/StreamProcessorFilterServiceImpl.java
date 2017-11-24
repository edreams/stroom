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

package stroom.streamtask.server;

import event.logging.BaseAdvancedQueryItem;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import stroom.entity.server.AutoMarshal;
import stroom.entity.server.CriteriaLoggingUtil;
import stroom.entity.server.QueryAppender;
import stroom.entity.server.SystemEntityServiceImpl;
import stroom.entity.server.util.HqlBuilder;
import stroom.entity.server.util.StroomEntityManager;
import stroom.entity.shared.BaseResultList;
import stroom.pipeline.shared.PipelineDocument;
import stroom.security.Secured;
import stroom.streamstore.shared.QueryData;
import stroom.streamtask.shared.FindStreamProcessorCriteria;
import stroom.streamtask.shared.FindStreamProcessorFilterCriteria;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamProcessorFilter;
import stroom.streamtask.shared.StreamProcessorFilterTracker;

import javax.inject.Inject;
import java.util.List;
import java.util.Set;

@Transactional
@Component("streamProcessorFilterService")
@AutoMarshal
@Secured(StreamProcessor.MANAGE_PROCESSORS_PERMISSION)
public class StreamProcessorFilterServiceImpl
        extends SystemEntityServiceImpl<StreamProcessorFilter, FindStreamProcessorFilterCriteria>
        implements StreamProcessorFilterService {
    private final StreamProcessorService streamProcessorService;
    private final StreamProcessorFilterMarshaller marshaller;

    @Inject
    public StreamProcessorFilterServiceImpl(final StroomEntityManager entityManager,
                                            final StreamProcessorService streamProcessorService) {
        super(entityManager);
        this.streamProcessorService = streamProcessorService;
        this.marshaller = new StreamProcessorFilterMarshaller();
    }

    @Override
    public Class<StreamProcessorFilter> getEntityClass() {
        return StreamProcessorFilter.class;
    }

    @Override
    public void addFindStreamCriteria(final StreamProcessor streamProcessor,
                                      final int priority,
                                      final QueryData queryData) {

        StreamProcessorFilter filter = new StreamProcessorFilter();
        // Blank tracker
        filter.setStreamProcessorFilterTracker(new StreamProcessorFilterTracker());
        filter.setPriority(priority);
        filter.setStreamProcessor(streamProcessor);
        filter.setQueryData(queryData);
        filter.setEnabled(true);
        filter = marshaller.marshal(filter);
        // Save initial tracker
        getEntityManager().saveEntity(filter.getStreamProcessorFilterTracker());
        getEntityManager().flush();
        save(filter);
    }

    @Override
    public StreamProcessorFilter createNewFilter(final PipelineDocument pipelineDocument,
                                                 final QueryData queryData,
                                                 final boolean enabled,
                                                 final int priority) {

        // First see if we can find a stream processor for this pipeline.
        final FindStreamProcessorCriteria findStreamProcessorCriteria = new FindStreamProcessorCriteria(pipelineDocument);
        final List<StreamProcessor> list = streamProcessorService.find(findStreamProcessorCriteria);
        StreamProcessor processor = null;
        if (list == null || list.size() == 0) {
            // We couldn't find one so create a new one.
            processor = new StreamProcessor(pipelineDocument.getUuid());
            processor.setEnabled(enabled);
            processor = streamProcessorService.save(processor);
        } else {
            processor = list.get(0);
        }

        StreamProcessorFilter filter = new StreamProcessorFilter();
        // Blank tracker
        filter.setEnabled(enabled);
        filter.setPriority(priority);
        filter.setStreamProcessorFilterTracker(new StreamProcessorFilterTracker());
        filter.setStreamProcessor(processor);
        filter.setQueryData(queryData);
        filter = marshaller.marshal(filter);
        // Save initial tracker
        getEntityManager().saveEntity(filter.getStreamProcessorFilterTracker());
        getEntityManager().flush();
        filter = save(filter);
        filter = marshaller.unmarshal(filter);

        return filter;
    }

    @Override
    public FindStreamProcessorFilterCriteria createCriteria() {
        return new FindStreamProcessorFilterCriteria();
    }

    @Override
    public Boolean delete(final StreamProcessorFilter entity) throws RuntimeException {
        if (Boolean.TRUE.equals(super.delete(entity))) {
            return getEntityManager().deleteEntity(entity.getStreamProcessorFilterTracker());
        }
        return Boolean.FALSE;
    }

    @Override
    public BaseResultList<StreamProcessorFilter> find(final FindStreamProcessorFilterCriteria criteria) throws RuntimeException {
        return super.find(criteria);
    }

    @Override
    public void appendCriteria(final List<BaseAdvancedQueryItem> items,
                               final FindStreamProcessorFilterCriteria criteria) {
        CriteriaLoggingUtil.appendRangeTerm(items, "priorityRange", criteria.getPriorityRange());
        CriteriaLoggingUtil.appendRangeTerm(items, "lastPollPeriod", criteria.getLastPollPeriod());
        CriteriaLoggingUtil.appendEntityIdSet(items, "streamProcessorIdSet", criteria.getStreamProcessorIdSet());
        CriteriaLoggingUtil.appendEntityIdSet(items, "pipelineIdSet", criteria.getPipelineSet());
        CriteriaLoggingUtil.appendBooleanTerm(items, "streamProcessorEnabled", criteria.getStreamProcessorEnabled());
        CriteriaLoggingUtil.appendBooleanTerm(items, "streamProcessorFilterEnabled",
                criteria.getStreamProcessorFilterEnabled());
        CriteriaLoggingUtil.appendStringTerm(items, "createUser", criteria.getCreateUser());
        super.appendCriteria(items, criteria);
    }

    @Override
    protected QueryAppender<StreamProcessorFilter, FindStreamProcessorFilterCriteria> createQueryAppender(final StroomEntityManager entityManager) {
        return new StreamProcessorFilterQueryAppender(entityManager);
    }

    private static class StreamProcessorFilterQueryAppender extends QueryAppender<StreamProcessorFilter, FindStreamProcessorFilterCriteria> {
        private final StreamProcessorFilterMarshaller marshaller;

        StreamProcessorFilterQueryAppender(final StroomEntityManager entityManager) {
            super(entityManager);
            marshaller = new StreamProcessorFilterMarshaller();
        }

        @Override
        protected void appendBasicJoin(final HqlBuilder sql, final String alias, final Set<String> fetchSet) {
            super.appendBasicJoin(sql, alias, fetchSet);
            if (fetchSet != null) {
                if (fetchSet.contains(StreamProcessor.ENTITY_TYPE)) {
                    sql.append(" INNER JOIN FETCH ");
                    sql.append(alias);
                    sql.append(".streamProcessor as sp");
                }
//                if (fetchSet.contains(PipelineDocument.DOCUMENT_TYPE)) {
//                    sql.append(" INNER JOIN FETCH ");
//                    sql.append("sp.pipeline");
//                }
            }
        }

        @Override
        protected void appendBasicCriteria(final HqlBuilder sql, final String alias,
                                           final FindStreamProcessorFilterCriteria criteria) {
            super.appendBasicCriteria(sql, alias, criteria);
            sql.appendRangeQuery(alias + ".priority", criteria.getPriorityRange());

            sql.appendValueQuery(alias + ".streamProcessor.enabled", criteria.getStreamProcessorEnabled());

            sql.appendValueQuery(alias + ".enabled", criteria.getStreamProcessorFilterEnabled());

            sql.appendRangeQuery(alias + ".streamProcessorFilterTracker.lastPollMs", criteria.getLastPollPeriod());

            sql.appendDocRefSetQuery(alias + ".streamProcessor.pipeline", criteria.getPipelineSet());

            sql.appendEntityIdSetQuery(alias + ".streamProcessor", criteria.getStreamProcessorIdSet());

            sql.appendValueQuery(alias + ".createUser", criteria.getCreateUser());
        }

        @Override
        protected void preSave(final StreamProcessorFilter entity) {
            super.preSave(entity);
            marshaller.marshal(entity);
        }

        @Override
        protected void postLoad(final StreamProcessorFilter entity) {
            marshaller.unmarshal(entity);
            super.postLoad(entity);
        }
    }
}

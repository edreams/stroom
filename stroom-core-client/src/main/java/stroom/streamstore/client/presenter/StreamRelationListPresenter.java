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

package stroom.streamstore.client.presenter;

import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.cell.expander.client.ExpanderCell;
import stroom.data.grid.client.EndColumn;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.ResultList;
import stroom.entity.shared.Sort.Direction;
import stroom.feed.shared.Feed;
import stroom.pipeline.shared.PipelineDocument;
import stroom.security.client.ClientSecurityContext;
import stroom.streamstore.shared.FindStreamAttributeMapCriteria;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamAttributeConstants;
import stroom.streamstore.shared.StreamAttributeMap;
import stroom.streamstore.shared.StreamStatus;
import stroom.streamstore.shared.StreamType;
import stroom.util.shared.Expander;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StreamRelationListPresenter extends AbstractStreamListPresenter {
    private final Map<Long, StreamAttributeMap> streamMap = new HashMap<>();
    private int maxDepth = -1;

    private Column<StreamAttributeMap, Expander> expanderColumn;

    @Inject
    public StreamRelationListPresenter(final EventBus eventBus, final ClientDispatchAsync dispatcher,
                                       final TooltipPresenter tooltipPresenter, final ClientSecurityContext securityContext) {
        super(eventBus, dispatcher, tooltipPresenter, securityContext, false);
        dataProvider.setAllowNoConstraint(false);
    }

    public void setSelectedStream(final StreamAttributeMap streamAttributeMap, final boolean fireEvents,
                                  final boolean showSystemFiles) {
        if (streamAttributeMap == null) {
            setCriteria(null);

        } else {
            final FindStreamAttributeMapCriteria criteria = new FindStreamAttributeMapCriteria();
            final FindStreamCriteria findStreamCriteria = criteria.obtainFindStreamCriteria();
            if (!showSystemFiles) {
                findStreamCriteria.obtainStatusSet().setSingleItem(StreamStatus.UNLOCKED);
            }
            findStreamCriteria.obtainStreamIdSet().add(streamAttributeMap.getStream());
            findStreamCriteria.getFetchSet().add(Stream.ENTITY_TYPE);

            findStreamCriteria.getFetchSet().add(Feed.ENTITY_TYPE);
//            findStreamCriteria.getFetchSet().add(PipelineDocument.ENTITY_TYPE);
            findStreamCriteria.getFetchSet().add(StreamType.ENTITY_TYPE);
            findStreamCriteria.setSort(FindStreamCriteria.FIELD_CREATE_MS, Direction.ASCENDING, false);

            setCriteria(criteria);
        }

        getSelectionModel().setSelected(streamAttributeMap);
    }

    @Override
    protected ResultList<StreamAttributeMap> onProcessData(final ResultList<StreamAttributeMap> data) {
        // Store streams against id.
        streamMap.clear();
        for (final StreamAttributeMap row : data) {
            final Stream stream = row.getStream();
            streamMap.put(stream.getId(), row);
        }

        for (final StreamAttributeMap row : data) {
            final Stream stream = row.getStream();
            streamMap.put(stream.getId(), row);
        }

        // Now use the root streams and attach child streams to them.
        maxDepth = -1;
        final List<StreamAttributeMap> newData = new ArrayList<>();
        addChildren(null, data, newData, 0);

        // Set the width of the expander column so that all expanders
        // can be seen.
        if (maxDepth >= 0) {
            getView().setColumnWidth(expanderColumn, 16 + (maxDepth * 10), Unit.PX);
        } else {
            getView().setColumnWidth(expanderColumn, 0, Unit.PX);
        }

        final ResultList<StreamAttributeMap> processed = new BaseResultList<>(newData,
                Long.valueOf(data.getStart()), Long.valueOf(data.getSize()), !data.isExact());
        return super.onProcessData(processed);
    }

    private void addChildren(final StreamAttributeMap parent, final List<StreamAttributeMap> data,
                             final List<StreamAttributeMap> newData, final int depth) {
        for (final StreamAttributeMap row : data) {
            final Stream stream = row.getStream();

            if (parent == null) {
                // Add roots.
                if (stream.getParentStreamId() == null || streamMap.get(stream.getParentStreamId()) == null) {
                    newData.add(row);
                    addChildren(row, data, newData, depth + 1);

                    if (maxDepth < depth) {
                        maxDepth = depth;
                    }
                }
            } else {
                // Add children.
                if (stream.getParentStreamId() != null) {
                    final StreamAttributeMap thisParent = streamMap.get(stream.getParentStreamId());
                    if (thisParent != null && thisParent.equals(parent)) {
                        newData.add(row);
                        addChildren(row, data, newData, depth + 1);

                        if (maxDepth < depth) {
                            maxDepth = depth;
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void addColumns(final boolean allowSelectAll) {
        addSelectedColumn(allowSelectAll);

        expanderColumn = new Column<StreamAttributeMap, Expander>(new ExpanderCell()) {
            @Override
            public Expander getValue(final StreamAttributeMap row) {
                return buildExpander(row);
            }
        };
        getView().addColumn(expanderColumn, "<br/>", 0);

        addInfoColumn();

        addCreatedColumn();
        addStreamTypeColumn();
        addFeedColumn();
        addPipelineColumn();

        addAttributeColumn("Raw", StreamAttributeConstants.STREAM_SIZE, ColumnSizeConstants.SMALL_COL);
        addAttributeColumn("Disk", StreamAttributeConstants.FILE_SIZE, ColumnSizeConstants.SMALL_COL);
        addAttributeColumn("Read", StreamAttributeConstants.REC_READ, ColumnSizeConstants.SMALL_COL);
        addAttributeColumn("Write", StreamAttributeConstants.REC_WRITE, ColumnSizeConstants.SMALL_COL);
        addAttributeColumn("Fatal", StreamAttributeConstants.REC_FATAL, 40);
        addAttributeColumn("Error", StreamAttributeConstants.REC_ERROR, 40);
        addAttributeColumn("Warn", StreamAttributeConstants.REC_WARN, 40);
        addAttributeColumn("Info", StreamAttributeConstants.REC_INFO, 40);
        addAttributeColumn("Retention", StreamAttributeConstants.RETENTION_AGE, ColumnSizeConstants.SMALL_COL);

        getView().addEndColumn(new EndColumn<>());
    }

    private Expander buildExpander(final StreamAttributeMap row) {
        return new Expander(getDepth(row), true, true);
    }

    public int getDepth(final StreamAttributeMap row) {
        int depth = 0;
        Long parentId = row.getStream().getParentStreamId();
        while (parentId != null) {
            depth++;

            final StreamAttributeMap parentRow = streamMap.get(parentId);
            if (parentRow == null) {
                parentId = null;
            } else {
                parentId = parentRow.getStream().getParentStreamId();
            }
        }

        return depth;
    }
}

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

package stroom.streamtask.shared;

import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.CriteriaSet;
import stroom.entity.shared.EntityIdSet;
import stroom.entity.shared.HasIsConstrained;
import stroom.entity.shared.Period;
import stroom.feed.shared.Feed;
import stroom.node.shared.Node;
import stroom.docref.DocRef;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamDataSource;
import stroom.streamstore.shared.StreamStatus;
import stroom.streamstore.shared.StreamType;

/**
 * <p>
 * API to find streams that have not yet been processed.
 * </p>
 */
public final class FindStreamTaskCriteria extends BaseCriteria implements HasIsConstrained {
    public static final int SUMMARY_POS_PIPELINE = 0;
    public static final int SUMMARY_POS_FEED = 1;
    public static final int SUMMARY_POS_PRIORITY = 2;
    public static final int SUMMARY_POS_STATUS = 3;
    public static final String FIELD_CREATE_TIME = "Created";
    public static final String FIELD_START_TIME = "Start Time";
    public static final String FIELD_END_TIME_DATE = "End Time";
    public static final String FIELD_FEED_NAME = StreamDataSource.FEED_NAME;
    public static final String FIELD_PRIORITY = "Priority";
    public static final String FIELD_PIPELINE_UUID = StreamDataSource.PIPELINE_UUID;
    public static final String FIELD_STATUS = "Status";
    public static final String FIELD_COUNT = "Count";
    public static final String FIELD_NODE = "Node";
    public static final String FIELD_POLL_AGE = "Poll Age";
    private static final long serialVersionUID = 5031936548305751911L;
    /**
     * Look for stream tasks with a certain status.
     */
    private CriteriaSet<TaskStatus> streamTaskStatusSet;

    /**
     * Find with a key
     */
    private EntityIdSet<Stream> streamIdSet = null;

    /**
     * Find with a key
     */
    private EntityIdSet<Node> nodeIdSet = null;

    /**
     * Find with a key
     */
    private EntityIdSet<StreamTask> streamTaskIdSet = null;

    /**
     * Find with a key
     */
    private EntityIdSet<StreamProcessorFilter> streamProcessorFilterIdSet = null;

    /**
     * Find with a key
     */
    private EntityIdSet<Feed> feedIdSet = null;

    /**
     * Find with a key
     */
    private EntityIdSet<StreamType> streamTypeIdSet = null;

    /**
     * Find with a key
     */
    private CriteriaSet<DocRef> pipelineSet = null;

    private CriteriaSet<StreamStatus> statusSet;

    private Period createPeriod;
    private Period effectivePeriod;

    /**
     * Create at a particular time
     */
    private Long createMs = null;

    public static FindStreamTaskCriteria createWithStream(final Stream stream) {
        final FindStreamTaskCriteria criteria = new FindStreamTaskCriteria();
        criteria.obtainStreamIdSet().add(stream.getId());
        return criteria;
    }

    @Override
    public boolean isConstrained() {
        if (streamTaskStatusSet != null && streamTaskStatusSet.isConstrained()) {
            return true;
        }
        if (streamIdSet != null && streamIdSet.isConstrained()) {
            return true;
        }
        if (nodeIdSet != null && nodeIdSet.isConstrained()) {
            return true;
        }
        if (streamTaskIdSet != null && streamTaskIdSet.isConstrained()) {
            return true;
        }
        if (streamProcessorFilterIdSet != null && streamProcessorFilterIdSet.isConstrained()) {
            return true;
        }
        if (feedIdSet != null && feedIdSet.isConstrained()) {
            return true;
        }
        if (streamTypeIdSet != null && streamTypeIdSet.isConstrained()) {
            return true;
        }
        if (pipelineSet != null && pipelineSet.isConstrained()) {
            return true;
        }
        if (statusSet != null && statusSet.isConstrained()) {
            return true;
        }
        if (createPeriod != null && createPeriod.isConstrained()) {
            return true;
        }
        if (effectivePeriod != null && effectivePeriod.isConstrained()) {
            return true;
        }
        return false;
    }

    public CriteriaSet<TaskStatus> getStreamTaskStatusSet() {
        return streamTaskStatusSet;
    }

    public void setStreamTaskStatusSet(final CriteriaSet<TaskStatus> streamTaskStatusSet) {
        this.streamTaskStatusSet = streamTaskStatusSet;
    }

    public CriteriaSet<TaskStatus> obtainStreamTaskStatusSet() {
        if (streamTaskStatusSet == null) {
            streamTaskStatusSet = new CriteriaSet<>();
        }
        return streamTaskStatusSet;
    }

    public EntityIdSet<Stream> getStreamIdSet() {
        return streamIdSet;
    }

    public EntityIdSet<Stream> obtainStreamIdSet() {
        if (streamIdSet == null) {
            streamIdSet = new EntityIdSet<>();
        }
        return streamIdSet;
    }

    public EntityIdSet<Node> getNodeIdSet() {
        return nodeIdSet;
    }

    public EntityIdSet<Node> obtainNodeIdSet() {
        if (nodeIdSet == null) {
            nodeIdSet = new EntityIdSet<>();
        }
        return nodeIdSet;
    }

    public CriteriaSet<DocRef> getPipelineSet() {
        return pipelineSet;
    }

    public CriteriaSet<DocRef> obtainPipelineSet() {
        if (pipelineSet == null) {
            pipelineSet = new CriteriaSet<>();
        }
        return pipelineSet;
    }

    public EntityIdSet<Feed> getFeedIdSet() {
        return feedIdSet;
    }

    public EntityIdSet<Feed> obtainFeedIdSet() {
        if (feedIdSet == null) {
            feedIdSet = new EntityIdSet<>();
        }
        return feedIdSet;
    }

    public EntityIdSet<StreamType> getStreamTypeIdSet() {
        return streamTypeIdSet;
    }

    public EntityIdSet<StreamType> obtainStreamTypeIdSet() {
        if (streamTypeIdSet == null) {
            streamTypeIdSet = new EntityIdSet<>();
        }
        return streamTypeIdSet;
    }

    public EntityIdSet<StreamTask> getStreamTaskIdSet() {
        return streamTaskIdSet;
    }

    public EntityIdSet<StreamProcessorFilter> obtainStreamProcessorFilterIdSet() {
        if (streamProcessorFilterIdSet == null) {
            streamProcessorFilterIdSet = new EntityIdSet<>();
        }
        return streamProcessorFilterIdSet;
    }

    public EntityIdSet<StreamProcessorFilter> getStreamProcessorFilterIdSet() {
        return streamProcessorFilterIdSet;
    }

    public EntityIdSet<StreamTask> obtainStreamTaskIdSet() {
        if (streamTaskIdSet == null) {
            streamTaskIdSet = new EntityIdSet<>();
        }
        return streamTaskIdSet;
    }

    public CriteriaSet<StreamStatus> getStatusSet() {
        return statusSet;
    }

    public CriteriaSet<StreamStatus> obtainStatusSet() {
        if (statusSet == null) {
            statusSet = new CriteriaSet<>();
        }
        return statusSet;
    }

    //    public FindStreamCriteria getFindStreamCriteria() {
//        return findStreamCriteria;
//    }
//
//    public FindStreamCriteria obtainFindStreamCriteria() {
//        if (findStreamCriteria == null) {
//            findStreamCriteria = new FindStreamCriteria();
//        }
//        return findStreamCriteria;
//    }

//    public void copyFrom(final FindStreamTaskCriteria other) {
//        if (other == null) {
//            return;
//        }
//        super.copyFrom(other);
//
//        this.obtainStreamTaskStatusSet().copyFrom(other.obtainStreamTaskStatusSet());
//        this.obtainNodeIdSet().copyFrom(other.obtainNodeIdSet());
//        this.obtainStreamTaskIdSet().copyFrom(other.obtainStreamTaskIdSet());
//        this.obtainFindStreamCriteria().copyFrom(other.obtainFindStreamCriteria());
//
//    }

    public Long getCreateMs() {
        return createMs;
    }

    public void setCreateMs(final Long createMs) {
        this.createMs = createMs;
    }

    public Period getCreatePeriod() {
        return createPeriod;
    }

    public void setCreatePeriod(final Period createPeriod) {
        this.createPeriod = createPeriod;
    }

    public Period obtainCreatePeriod() {
        if (createPeriod == null) {
            createPeriod = new Period();
        }
        return createPeriod;

    }

    public Period getEffectivePeriod() {
        return effectivePeriod;
    }

    public void setEffectivePeriod(final Period effectivePeriod) {
        this.effectivePeriod = effectivePeriod;
    }

    public Period obtainEffectivePeriod() {
        if (effectivePeriod == null) {
            effectivePeriod = new Period();
        }
        return effectivePeriod;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof FindStreamTaskCriteria)) return false;
        if (!super.equals(o)) return false;

        final FindStreamTaskCriteria that = (FindStreamTaskCriteria) o;

        if (streamTaskStatusSet != null ? !streamTaskStatusSet.equals(that.streamTaskStatusSet) : that.streamTaskStatusSet != null)
            return false;
        if (streamIdSet != null ? !streamIdSet.equals(that.streamIdSet) : that.streamIdSet != null) return false;
        if (nodeIdSet != null ? !nodeIdSet.equals(that.nodeIdSet) : that.nodeIdSet != null) return false;
        if (streamTaskIdSet != null ? !streamTaskIdSet.equals(that.streamTaskIdSet) : that.streamTaskIdSet != null)
            return false;
        if (streamProcessorFilterIdSet != null ? !streamProcessorFilterIdSet.equals(that.streamProcessorFilterIdSet) : that.streamProcessorFilterIdSet != null)
            return false;
        if (createMs != null ? !createMs.equals(that.createMs) : that.createMs != null) return false;
        if (feedIdSet != null ? feedIdSet.equals(that.feedIdSet) : that.feedIdSet == null) return false;
        if (streamTypeIdSet != null ? !streamTypeIdSet.equals(that.streamTypeIdSet) : that.streamTypeIdSet != null)
            return false;
        if (pipelineSet != null ? pipelineSet.equals(that.pipelineSet) : that.pipelineSet == null) return false;
        if (statusSet != null ? statusSet.equals(that.statusSet) : that.statusSet == null) return false;
        if (createPeriod != null ? createPeriod.equals(that.createPeriod) : that.createPeriod == null) return false;
        if (effectivePeriod != null ? effectivePeriod.equals(that.effectivePeriod) : that.effectivePeriod == null)
            return false;

        return true;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (streamTaskStatusSet != null ? streamTaskStatusSet.hashCode() : 0);
        result = 31 * result + (streamIdSet != null ? streamIdSet.hashCode() : 0);
        result = 31 * result + (nodeIdSet != null ? nodeIdSet.hashCode() : 0);
        result = 31 * result + (streamTaskIdSet != null ? streamTaskIdSet.hashCode() : 0);
        result = 31 * result + (streamProcessorFilterIdSet != null ? streamProcessorFilterIdSet.hashCode() : 0);
        result = 31 * result + (createMs != null ? createMs.hashCode() : 0);
        result = 31 * result + (feedIdSet != null ? feedIdSet.hashCode() : 0);
        result = 31 * result + (streamTypeIdSet != null ? streamTypeIdSet.hashCode() : 0);
        result = 31 * result + (pipelineSet != null ? pipelineSet.hashCode() : 0);
        result = 31 * result + (statusSet != null ? statusSet.hashCode() : 0);
        result = 31 * result + (createPeriod != null ? createPeriod.hashCode() : 0);
        result = 31 * result + (effectivePeriod != null ? effectivePeriod.hashCode() : 0);
        return result;
    }
}

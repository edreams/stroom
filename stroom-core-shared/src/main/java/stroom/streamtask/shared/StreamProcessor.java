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

import stroom.entity.shared.AuditedEntity;
import stroom.entity.shared.SQLNameConstants;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;

@Entity
@Table(name = "STRM_PROC")
public class StreamProcessor extends AuditedEntity {
    public static final String TABLE_NAME = SQLNameConstants.STREAM + SEP + SQLNameConstants.PROCESSOR;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;
    public static final String TASK_TYPE = SQLNameConstants.TASK + SEP + SQLNameConstants.TYPE;
    public static final String PIPELINE_UUID = SQLNameConstants.PIPELINE + SEP + SQLNameConstants.UUID;
    public static final String ENABLED = SQLNameConstants.ENABLED;
    public static final String ENTITY_TYPE = "StreamProcessor";
    public static final String PIPELINE_STREAM_PROCESSOR_TASK_TYPE = "pipelineStreamProcessor";
    public static final String MANAGE_PROCESSORS_PERMISSION = "Manage Processors";
    //    public static final String VIEW_PROCESSORS_PERMISSION = "View Processors";
    private static final long serialVersionUID = -958099873937223257L;
    // Only One type for the moment
    private String taskType = PIPELINE_STREAM_PROCESSOR_TASK_TYPE;
    private String pipelineUuid;
    private boolean enabled;

    public StreamProcessor() {
    }

    public StreamProcessor(final String pipelineUuid) {
        this.pipelineUuid = pipelineUuid;
    }

    @Transient
    @Override
    public final String getType() {
        return ENTITY_TYPE;
    }

    @Column(name = TASK_TYPE)
    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    @Column(name = PIPELINE_UUID)
    public String getPipelineUuid() {
        return pipelineUuid;
    }

    public void setPipelineUuid(String pipeline) {
        this.pipelineUuid = pipelineUuid;
    }

    @Column(name = ENABLED, nullable = false)
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
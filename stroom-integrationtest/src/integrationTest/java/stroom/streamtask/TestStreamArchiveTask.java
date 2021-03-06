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

package stroom.streamtask;

import org.junit.Assert;
import org.junit.Test;
import stroom.feed.FeedService;
import stroom.feed.shared.Feed;
import stroom.jobsystem.MockTask;
import stroom.node.NodeCache;
import stroom.node.NodeService;
import stroom.node.shared.FindNodeCriteria;
import stroom.node.shared.Node;
import stroom.streamstore.FindStreamVolumeCriteria;
import stroom.streamstore.StreamDeleteExecutor;
import stroom.streamstore.StreamMaintenanceService;
import stroom.streamstore.StreamRetentionExecutor;
import stroom.streamstore.StreamStore;
import stroom.streamstore.StreamTarget;
import stroom.streamstore.fs.FileSystemCleanExecutor;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.streamstore.shared.StreamVolume;
import stroom.task.SimpleTaskContext;
import stroom.task.TaskManager;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestControl;
import stroom.test.CommonTestScenarioCreator;
import stroom.util.config.StroomProperties;
import stroom.volume.VolumeServiceImpl;

import javax.inject.Inject;
import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Test the archiving stuff.
 * <p>
 * Create some old files and make sure they get archived.
 */
public class TestStreamArchiveTask extends AbstractCoreIntegrationTest {
    private static final int HIGHER_REPLICATION_COUNT = 2;
    private static final int SIXTY = 60;
    private static final int FIFTY_FIVE = 55;
    private static final int FIFTY = 50;

    @Inject
    private StreamStore streamStore;
    @Inject
    private StreamMaintenanceService streamMaintenanceService;
    @Inject
    private FeedService feedService;
    @Inject
    private FileSystemCleanExecutor fileSystemCleanTaskExecutor;
    @Inject
    private TaskManager taskManager;
    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Inject
    private CommonTestControl commonTestControl;
    @Inject
    private NodeCache nodeCache;
    @Inject
    private NodeService nodeService;
    @Inject
    private StreamTaskCreatorImpl streamTaskCreator;
    @Inject
    private StreamRetentionExecutor streamRetentionExecutor;
    @Inject
    private StreamDeleteExecutor streamDeleteExecutor;

    private int initialReplicationCount = 1;

    @Override
    protected void onBefore() {
        initialReplicationCount = StroomProperties.getIntProperty(VolumeServiceImpl.PROP_RESILIENT_REPLICATION_COUNT, 1);
        StroomProperties.setIntProperty(VolumeServiceImpl.PROP_RESILIENT_REPLICATION_COUNT, HIGHER_REPLICATION_COUNT,
                StroomProperties.Source.TEST);
    }

    @Override
    protected void onAfter() {
        StroomProperties.setIntProperty(VolumeServiceImpl.PROP_RESILIENT_REPLICATION_COUNT, initialReplicationCount,
                StroomProperties.Source.TEST);
    }

    @Test
    public void testCheckArchive() throws IOException {
        nodeCache.getDefaultNode();
        final List<Node> nodeList = nodeService.find(new FindNodeCriteria());
        for (final Node node : nodeList) {
            fileSystemCleanTaskExecutor.clean(new MockTask("Test"), node.getId());
        }

        final ZonedDateTime oldDate = ZonedDateTime.now(ZoneOffset.UTC).minusDays(SIXTY);
        final ZonedDateTime newDate = ZonedDateTime.now(ZoneOffset.UTC).minusDays(FIFTY);

        // Write a file 2 files ... on we leave locked and the other not locked
        Feed feed = commonTestScenarioCreator.createSimpleFeed();
        feed.setRetentionDayAge(FIFTY_FIVE);
        feed = feedService.save(feed);

        final Stream oldFile = Stream.createStreamForTesting(StreamType.RAW_EVENTS, feed, null,
                oldDate.toInstant().toEpochMilli());
        final Stream newFile = Stream.createStreamForTesting(StreamType.RAW_EVENTS, feed, null,
                newDate.toInstant().toEpochMilli());

        final StreamTarget oldFileTarget = streamStore.openStreamTarget(oldFile);
        oldFileTarget.getOutputStream().write("MyTest".getBytes());
        streamStore.closeStreamTarget(oldFileTarget);

        final StreamTarget newFileTarget = streamStore.openStreamTarget(newFile);
        newFileTarget.getOutputStream().write("MyTest".getBytes());
        streamStore.closeStreamTarget(newFileTarget);

        // Now we have added some data create some associated stream tasks.
        // TODO : At some point we need to change deletion to delete streams and
        // not tasks. Tasks should be deleted if an associated source stream is
        // deleted if they exist, however currently streams are only deleted if
        // their associated task exists which would prevent us from deleting
        // streams that have no task associated with them.
        streamTaskCreator.createTasks(new SimpleTaskContext());

        List<StreamVolume> oldVolumeList = streamMaintenanceService
                .find(FindStreamVolumeCriteria.create(oldFileTarget.getStream()));
        Assert.assertEquals("Expecting 2 stream volumes", HIGHER_REPLICATION_COUNT, oldVolumeList.size());

        List<StreamVolume> newVolumeList = streamMaintenanceService
                .find(FindStreamVolumeCriteria.create(newFileTarget.getStream()));
        Assert.assertEquals("Expecting 2 stream volumes", HIGHER_REPLICATION_COUNT, newVolumeList.size());

        streamRetentionExecutor.exec();
        streamDeleteExecutor.delete(System.currentTimeMillis());

        // Test Again
        oldVolumeList = streamMaintenanceService.find(FindStreamVolumeCriteria.create(oldFileTarget.getStream()));
        Assert.assertEquals("Expecting 0 stream volumes", 0, oldVolumeList.size());

        newVolumeList = streamMaintenanceService.find(FindStreamVolumeCriteria.create(newFileTarget.getStream()));
        Assert.assertEquals("Expecting 2 stream volumes", HIGHER_REPLICATION_COUNT, newVolumeList.size());

        // Test they are
        oldVolumeList = streamMaintenanceService.find(FindStreamVolumeCriteria.create(oldFileTarget.getStream()));
        Assert.assertEquals("Expecting 0 stream volumes", 0, oldVolumeList.size());
        newVolumeList = streamMaintenanceService.find(FindStreamVolumeCriteria.create(newFileTarget.getStream()));
        Assert.assertEquals("Expecting 2 stream volumes", HIGHER_REPLICATION_COUNT, newVolumeList.size());
    }
}

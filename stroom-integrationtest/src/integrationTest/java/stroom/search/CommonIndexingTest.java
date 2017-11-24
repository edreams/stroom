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

package stroom.search;

import org.junit.Assert;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import stroom.index.server.IndexShardManager;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.pipeline.server.task.PipelineStreamProcessor;
import stroom.pipeline.shared.PipelineDocument;
import stroom.streamstore.server.tools.StoreCreationTool;
import stroom.streamtask.server.StreamProcessorTaskExecutor;
import stroom.test.CommonTranslationTest;
import stroom.test.StroomPipelineTestFileUtil;
import stroom.util.shared.Severity;
import stroom.util.spring.StroomSpringProfiles;

import javax.annotation.Resource;
import java.nio.file.Path;
import java.util.List;

/**
 * Class to create test data for use in all search tests.
 */
@Component
@Profile(StroomSpringProfiles.IT)
public class CommonIndexingTest {
    private static final int N1 = 1;
    private static final int N4 = 4;

    private static final String DIR = "CommonIndexingTest/";

    public static final Path INDEX_XSLT = StroomPipelineTestFileUtil.getTestResourcesFile(DIR + "index.xsl");
    public static final Path SEARCH_RESULT_XSLT = StroomPipelineTestFileUtil
            .getTestResourcesFile(DIR + "search_result.xsl");
    public static final Path SEARCH_RESULT_TEXT_XSLT = StroomPipelineTestFileUtil
            .getTestResourcesFile(DIR + "search_result_text.xsl");

    @Resource
    private IndexShardManager indexShardManager;
    @Resource
    private CommonTranslationTest commonTranslationTest;
    @Resource
    private StoreCreationTool storeCreationTool;

    public void setup() {
        try {
            // Add data.
            commonTranslationTest.setup();
            // Translate data.
            List<StreamProcessorTaskExecutor> results = commonTranslationTest.processAll();
            Assert.assertEquals(N4, results.size());
            for (final StreamProcessorTaskExecutor result : results) {
                final PipelineStreamProcessor processor = (PipelineStreamProcessor) result;
                Assert.assertTrue(result.toString(), processor.getWritten() > 0);
                Assert.assertTrue(result.toString(), processor.getRead() <= processor.getWritten());
                Assert.assertEquals(result.toString(), 0, processor.getMarkerCount(Severity.SEVERITIES));
            }

            // Add index.
            storeCreationTool.addIndex("Test index", INDEX_XSLT);
            // Translate data.
            results = commonTranslationTest.processAll();
            Assert.assertEquals(N1, results.size());
            for (final StreamProcessorTaskExecutor result : results) {
                final PipelineStreamProcessor processor = (PipelineStreamProcessor) result;
                Assert.assertTrue(result.toString(), processor.getWritten() > 0);
                Assert.assertTrue(result.toString(), processor.getRead() <= processor.getWritten());
                Assert.assertEquals(result.toString(), 0, processor.getMarkerCount(Severity.SEVERITIES));
            }

            // Flush all newly created index shards.
            indexShardManager.findFlush(new FindIndexShardCriteria());
        } catch (final Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public int flushIndex() {
        final FindIndexShardCriteria criteria = new FindIndexShardCriteria();
        criteria.getIndexIdSet().setMatchAll(true);
        indexShardManager.findFlush(criteria);

        return 1;
    }

    public PipelineDocument getSearchResultPipeline() {
        return storeCreationTool.getSearchResultPipeline("Search result", SEARCH_RESULT_XSLT);
    }

    public PipelineDocument getSearchResultTextPipeline() {
        return storeCreationTool.getSearchResultPipeline("Search result text", SEARCH_RESULT_TEXT_XSLT);
    }
}

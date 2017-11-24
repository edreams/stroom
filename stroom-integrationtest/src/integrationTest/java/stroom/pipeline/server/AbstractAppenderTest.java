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

package stroom.pipeline.server;

import org.junit.Assert;
import stroom.io.StreamCloser;
import stroom.pipeline.destination.RollingDestinations;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.errorhandler.LoggingErrorReceiver;
import stroom.pipeline.server.factory.Pipeline;
import stroom.pipeline.server.factory.PipelineDataCache;
import stroom.pipeline.server.factory.PipelineFactory;
import stroom.pipeline.server.parser.CombinedParser;
import stroom.pipeline.shared.PipelineDocument;
import stroom.pipeline.shared.TextConverter;
import stroom.pipeline.shared.TextConverter.TextConverterType;
import stroom.pipeline.shared.XSLT;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.pipeline.state.RecordCount;
import stroom.test.AbstractProcessIntegrationTest;
import stroom.test.CommonTestScenarioCreator;
import stroom.test.StroomPipelineTestFileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.shared.Severity;

import javax.annotation.Resource;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AbstractAppenderTest extends AbstractProcessIntegrationTest {
    @Resource
    private PipelineFactory pipelineFactory;
    @Resource
    private ErrorReceiverProxy errorReceiver;
    @Resource
    private RecordCount recordCount;
    @Resource
    private XSLTService xsltService;
    @Resource
    private TextConverterService textConverterService;
    @Resource
    private PipelineDocumentService pipelineDocumentService;
    @Resource
    private PipelineDataCache pipelineDataCache;
    @Resource
    private RollingDestinations destinations;
    @Resource
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Resource
    private StreamCloser streamCloser;

    void test(final String name, final String type) throws Exception {
        // Ensure a test feed exists.
        commonTestScenarioCreator.createSimpleFeed("TEST", "12345");
        final String dir = name + "/";
        final String stem = dir + name + "_" + type;
        final TextConverter textConverter = createTextConverter(dir + name + ".ds3.xml", name, TextConverterType.DATA_SPLITTER);
        final XSLT filteredXSLT = createXSLT(stem + ".xsl", name);
        final PipelineDocument pipelineDocument = createPipeline(stem + "_Pipeline.xml", textConverter, filteredXSLT);
        test(pipelineDocument, dir, name, type, stem + ".out", null);
    }

    private PipelineDocument createPipeline(final String pipelineFile, final TextConverter textConverter,
                                          final XSLT xslt) {
        // Load the pipeline config.
        final String data = StroomPipelineTestFileUtil.getString(pipelineFile);
        final PipelineDocument pipelineDocument = PipelineTestUtil.createTestPipeline(pipelineDocumentService, data);

        if (textConverter != null) {
            pipelineDocument.getPipelineData().addProperty(
                    PipelineDataUtil.createProperty(CombinedParser.DEFAULT_NAME, "textConverter", textConverter));
        }
        if (xslt != null) {
            pipelineDocument.getPipelineData()
                    .addProperty(PipelineDataUtil.createProperty("translationFilter", "xslt", xslt));
        }

        return pipelineDocumentService.save(pipelineDocument);
    }

    private TextConverter createTextConverter(final String textConverterFile, final String name,
                                              final TextConverterType textConverterType) {
        // Create a record for the TextConverter.
        final InputStream textConverterInputStream = StroomPipelineTestFileUtil.getInputStream(textConverterFile);
        TextConverter textConverter = textConverterService.create(name);
        textConverter.setConverterType(textConverterType);
        textConverter.setData(StreamUtil.streamToString(textConverterInputStream));
        textConverter = textConverterService.save(textConverter);
        return textConverter;
    }

    private XSLT createXSLT(final String xsltPath, final String name) {
        // Create a record for the XSLT.
        final InputStream xsltInputStream = StroomPipelineTestFileUtil.getInputStream(xsltPath);
        XSLT xslt = xsltService.create(name);
        xslt.setData(StreamUtil.streamToString(xsltInputStream));
        xslt = xsltService.save(xslt);
        return xslt;
    }

    // TODO This method is 80% the same in a whole bunch of test classes -
    // refactor some of the repetition out.
    void test(final PipelineDocument pipelineDocument,
              final String dir,
              final String name,
              final String type,
              final String outputReference,
              final String encoding) throws Exception {
        // Setup the error handler.
        final LoggingErrorReceiver loggingErrorReceiver = new LoggingErrorReceiver();
        errorReceiver.setErrorReceiver(loggingErrorReceiver);

        // Create the parser.
        final PipelineData pipelineData = pipelineDataCache.get(pipelineDocument);
        final Pipeline pipeline = pipelineFactory.create(pipelineData);

        // Get the input streams.
        final Path inputDir = StroomPipelineTestFileUtil.getTestResourcesDir().resolve(dir);
        Assert.assertTrue("Can't find input dir", Files.isDirectory(inputDir));

        List<Path> inputFiles;
        try (final Stream<Path> stream = Files.list(inputDir)) {
            inputFiles = stream
                    .filter(p -> {
                        final String fn = p.getFileName().toString();
                        return fn.startsWith(name) && fn.endsWith(".in");
                    })
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.toList());
        }

        Assert.assertTrue("Can't find any input files", inputFiles.size() > 0);

        pipeline.startProcessing();

        for (final Path inputFile : inputFiles) {
            final InputStream inputStream = new BufferedInputStream(Files.newInputStream(inputFile));
            pipeline.process(inputStream, encoding);
            inputStream.close();
        }

        pipeline.endProcessing();

        // Close all streams that have been written.
        streamCloser.close();

        // FORCE ROLL SO WE CAN GET OUTPUT
        destinations.forceRoll();

        Assert.assertTrue(recordCount.getRead() > 0);
        Assert.assertTrue(recordCount.getWritten() > 0);
        // Assert.assertEquals(recordCount.getRead(), recordCount.getWritten());
        Assert.assertEquals(200, recordCount.getRead());
        Assert.assertEquals(200 - 59, recordCount.getWritten());
        Assert.assertEquals(0, loggingErrorReceiver.getRecords(Severity.WARNING));
        Assert.assertEquals(59, loggingErrorReceiver.getRecords(Severity.ERROR));
        Assert.assertEquals(0, loggingErrorReceiver.getRecords(Severity.FATAL_ERROR));
    }
}

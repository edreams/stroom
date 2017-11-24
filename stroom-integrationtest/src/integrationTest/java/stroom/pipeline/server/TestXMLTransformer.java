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
import org.junit.Test;
import stroom.entity.shared.DocRefUtil;
import stroom.io.StreamCloser;
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
import stroom.test.ComparisonHelper;
import stroom.test.StroomPipelineTestFileUtil;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.shared.Severity;

import javax.annotation.Resource;
import java.io.InputStream;
import java.nio.file.Path;

public class TestXMLTransformer extends AbstractProcessIntegrationTest {
    private static final String DIR = "TestXMLTransformer/";

    private static final int NUMBER_OF_RECORDS = 10;

    private static final String REFERENCE = DIR + "TestXMLTransformer.out";

    private static final String INPUT_DEFAULT = DIR + "XML-EVENTS.nxml";
    private static final String INPUT_UTF_8 = DIR + "XML-EVENTS_UTF-8.nxml";
    private static final String INPUT_UTF_8_BOM = DIR + "XML-EVENTS_UTF-8_BOM.nxml";
    private static final String INPUT_UTF_16_LE = DIR + "XML-EVENTS_UTF-16LE.nxml";
    private static final String INPUT_UTF_16_LE_BOM = DIR + "XML-EVENTS_UTF-16LE_BOM.nxml";
    private static final String INPUT_UTF_16_BE = DIR + "XML-EVENTS_UTF-16BE.nxml";
    private static final String INPUT_UTF_16_BE_BOM = DIR + "XML-EVENTS_UTF-16BE_BOM.nxml";
    private static final String INPUT_FRAGMENT = DIR + "XML-EVENTS_fragment.nxml";
    private static final String XSLT_PATH = DIR + "DATA_SPLITTER-EVENTS_no-ref.xsl";
    private static final String FRAGMENT_WRAPPER = DIR + "fragment_wrapper.xml";

    private static final String TRANSFORMER_PIPELINE = DIR + "XMLTransformer.Pipeline.data.xml";
    private static final String FRAGMENT_PIPELINE = DIR + "XMLFragment.Pipeline.data.xml";

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
    private StreamCloser streamCloser;

    @Test
    public void testDefault() throws Exception {
        testXMLTransformer(INPUT_DEFAULT, null);
    }

    @Test
    public void testUTF8() throws Exception {
        testXMLTransformer(INPUT_UTF_8, "UTF-8");
    }

    @Test
    public void testUTF8BOM() throws Exception {
        testXMLTransformer(INPUT_UTF_8_BOM, "UTF-8");
    }

    @Test
    public void testUTF16LE() throws Exception {
        testXMLTransformer(INPUT_UTF_16_LE, "UTF-16LE");
    }

    @Test
    public void testUTF16LEBOM() throws Exception {
        testXMLTransformer(INPUT_UTF_16_LE_BOM, "UTF-16LE");
    }

    @Test
    public void testUTF16BE() throws Exception {
        testXMLTransformer(INPUT_UTF_16_BE, "UTF-16BE");
    }

    @Test
    public void testUTF16BEBOM() throws Exception {
        testXMLTransformer(INPUT_UTF_16_BE_BOM, "UTF-16BE");
    }

    @Test
    public void testFragment() throws Exception {
        final PipelineDocument pipelineDocument = createFragmentPipeline();
        test(pipelineDocument, INPUT_FRAGMENT, null);
    }

    private void testXMLTransformer(final String inputResource, final String encoding) throws Exception {
        final PipelineDocument pipelineDocument = createTransformerPipeline();
        test(pipelineDocument, inputResource, encoding);
    }

    private PipelineDocument createFragmentPipeline() {
        // Create a record for the TextConverter.
        final InputStream textConverterInputStream = StroomPipelineTestFileUtil.getInputStream(FRAGMENT_WRAPPER);
        TextConverter textConverter = textConverterService.create("Test Text Converter");
        textConverter.setConverterType(TextConverterType.XML_FRAGMENT);
        textConverter.setData(StreamUtil.streamToString(textConverterInputStream));
        textConverter = textConverterService.save(textConverter);

        // Get the pipeline config.
        final String data = StroomPipelineTestFileUtil.getString(FRAGMENT_PIPELINE);
        final PipelineDocument pipelineDocument = PipelineTestUtil.createTestPipeline(pipelineDocumentService, data);
        pipelineDocument.getPipelineData().addProperty(
                PipelineDataUtil.createProperty(CombinedParser.DEFAULT_NAME, "textConverter", textConverter));
        pipelineDocument.setParentPipeline(DocRefUtil.create(createTransformerPipeline()));
        return pipelineDocumentService.save(pipelineDocument);
    }

    private PipelineDocument createTransformerPipeline() {
        // Create a record for the XSLT.
        final InputStream xsltInputStream = StroomPipelineTestFileUtil.getInputStream(XSLT_PATH);
        XSLT xslt = xsltService.create("Test XSLT");
        xslt.setData(StreamUtil.streamToString(xsltInputStream));
        xslt = xsltService.save(xslt);

        // Get the pipeline config.
        final String data = StroomPipelineTestFileUtil.getString(TRANSFORMER_PIPELINE);
        final PipelineDocument pipelineDocument = PipelineTestUtil.createTestPipeline(pipelineDocumentService, data);
        pipelineDocument.getPipelineData()
                .addProperty(PipelineDataUtil.createProperty("translationFilter", "xslt", xslt));
        return pipelineDocumentService.save(pipelineDocument);
    }

    private void test(final PipelineDocument pipelineDocument, final String inputResource, final String encoding)
            throws Exception {
        final Path tempDir = getCurrentTestDir();

        // Make sure the config dir is set.
        System.setProperty("stroom.temp", FileUtil.getCanonicalPath(tempDir));

        // Delete any output file.
        final Path outputFile = tempDir.resolve("TestXMLTransformer.xml");
        final Path outputLockFile = tempDir.resolve("TestXMLTransformer.xml.lock");
        FileUtil.deleteFile(outputFile);
        FileUtil.deleteFile(outputLockFile);

        // Setup the error handler.
        final LoggingErrorReceiver loggingErrorReceiver = new LoggingErrorReceiver();
        errorReceiver.setErrorReceiver(loggingErrorReceiver);

        // Create the parser.
        final PipelineData pipelineData = pipelineDataCache.get(pipelineDocument);
        final Pipeline pipeline = pipelineFactory.create(pipelineData);

        // Get the input stream.
        final InputStream inputStream = StroomPipelineTestFileUtil.getInputStream(inputResource);

        pipeline.startProcessing();

        pipeline.process(inputStream, encoding);

        pipeline.endProcessing();

        // Close all streams that have been written.,
        streamCloser.close();

        Assert.assertTrue(recordCount.getRead() > 0);
        Assert.assertTrue(recordCount.getWritten() > 0);
        Assert.assertEquals(recordCount.getRead(), recordCount.getWritten());
        Assert.assertEquals(NUMBER_OF_RECORDS, recordCount.getRead());
        Assert.assertEquals(NUMBER_OF_RECORDS, recordCount.getWritten());
        Assert.assertEquals(0, loggingErrorReceiver.getRecords(Severity.WARNING));
        Assert.assertEquals(0, loggingErrorReceiver.getRecords(Severity.ERROR));
        Assert.assertEquals(0, loggingErrorReceiver.getRecords(Severity.FATAL_ERROR));

        if (!loggingErrorReceiver.isAllOk()) {
            Assert.fail(loggingErrorReceiver.toString());
        }

        final Path refFile = StroomPipelineTestFileUtil.getTestResourcesFile(REFERENCE);
        ComparisonHelper.compareFiles(refFile, outputFile);
    }
}

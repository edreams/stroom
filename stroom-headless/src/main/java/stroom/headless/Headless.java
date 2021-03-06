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

package stroom.headless;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import stroom.docstore.fs.FSPersistenceConfig;
import stroom.entity.util.XMLUtil;
import stroom.guice.PipelineScopeRunnable;
import stroom.importexport.ImportExportService;
import stroom.node.NodeCache;
import stroom.node.VolumeService;
import stroom.node.shared.Volume;
import stroom.node.shared.Volume.VolumeType;
import stroom.persist.PersistService;
import stroom.pipeline.filter.SafeXMLFilter;
import stroom.proxy.repo.StroomZipFile;
import stroom.proxy.repo.StroomZipFileType;
import stroom.proxy.repo.StroomZipNameSet;
import stroom.proxy.repo.StroomZipRepository;
import stroom.task.ExternalShutdownController;
import stroom.task.TaskManager;
import stroom.util.AbstractCommandLineTool;
import stroom.util.config.StroomProperties;
import stroom.util.config.StroomProperties.Source;
import stroom.util.io.FileUtil;
import stroom.util.io.IgnoreCloseInputStream;
import stroom.util.io.StreamUtil;
import stroom.util.shared.ModelStringUtil;

import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Command line tool to process some files from a proxy stroom.
 */
public class Headless extends AbstractCommandLineTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(Headless.class);

    private String input;
    private String output;
    private String config;
    private String content;
    private String tmp;

    private Path inputDir;
    private Path outputFile;
    private Path configFile;
    private Path contentDir;
    private Path tmpDir;

    public static void main(final String[] args) {
        new Headless().doMain(args);
    }

    public void setInput(final String input) {
        this.input = input;
    }

    public void setOutput(final String output) {
        this.output = output;
    }

    public void setConfig(final String config) {
        this.config = config;
    }

    public void setContent(final String content) {
        this.content = content;
    }

    public void setTmp(final String tmp) {
        this.tmp = tmp;

        final Path tempDir = Paths.get(tmp);

        // Redirect the temp dir for headless.
        StroomProperties.setOverrideProperty(StroomProperties.STROOM_TEMP, FileUtil.getCanonicalPath(tempDir), StroomProperties.Source.USER_CONF);

        FileUtil.forgetTempDir();
    }

    @Override
    protected void checkArgs() {
        if (input == null) {
            failArg("input", "required");
        }
        if (output == null) {
            failArg("output", "required");
        }
        if (config == null) {
            failArg("config", "required");
        }
        if (content == null) {
            failArg("content", "required");
        }
        if (tmp == null) {
            failArg("tmp", "required");
        }
    }

    private void init() {
        inputDir = Paths.get(input);
        outputFile = Paths.get(output);
        configFile = Paths.get(config);
        contentDir = Paths.get(content);
        tmpDir = Paths.get(tmp);

        if (!Files.isDirectory(inputDir)) {
            throw new RuntimeException("Input directory \"" + FileUtil.getCanonicalPath(inputDir) + "\" cannot be found!");
        }
        if (!Files.isDirectory(outputFile.getParent())) {
            throw new RuntimeException("Output file \"" + FileUtil.getCanonicalPath(outputFile.getParent())
                    + "\" parent directory cannot be found!");
        }
        if (!Files.isRegularFile(configFile)) {
            throw new RuntimeException("Config file \"" + FileUtil.getCanonicalPath(configFile) + "\" cannot be found!");
        }
        if (!Files.isDirectory(contentDir)) {
            throw new RuntimeException("Content dir \"" + FileUtil.getCanonicalPath(contentDir) + "\" cannot be found!");
        }

        // Make sure tmp dir exists and is empty.
        FileUtil.mkdirs(tmpDir);
        FileUtil.deleteFile(outputFile);
        FileUtil.deleteContents(tmpDir);
    }

    @Override
    public void run() {
        try {
            StroomProperties.setOverrideProperty("stroom.jpaHbm2DdlAuto", "update", Source.TEST);

            StroomProperties.setOverrideProperty("stroom.jdbcDriverClassName", "org.hsqldb.jdbcDriver", Source.TEST);
            StroomProperties.setOverrideProperty("stroom.jpaDialect", "org.hibernate.dialect.HSQLDialect", Source.TEST);
            StroomProperties.setOverrideProperty("stroom.jdbcDriverUrl", "jdbc:hsqldb:file:${stroom.temp}/stroom/HSQLDB.DAT;shutdown=true", Source.TEST);
            StroomProperties.setOverrideProperty("stroom.jdbcDriverUsername", "sa", Source.TEST);
            StroomProperties.setOverrideProperty("stroom.jdbcDriverPassword", "", Source.TEST);

            StroomProperties.setOverrideProperty("stroom.statistics.sql.jdbcDriverClassName", "org.hsqldb.jdbcDriver", Source.TEST);
            StroomProperties.setOverrideProperty("stroom.statistics.sql.jpaDialect", "org.hibernate.dialect.HSQLDialect", Source.TEST);
            StroomProperties.setOverrideProperty("stroom.statistics.sql.jdbcDriverUrl", "jdbc:hsqldb:file:${stroom.temp}/statistics/HSQLDB.DAT;shutdown=true", Source.TEST);
            StroomProperties.setOverrideProperty("stroom.statistics.sql.jdbcDriverUsername", "sa", Source.TEST);
            StroomProperties.setOverrideProperty("stroom.statistics.sql.jdbcDriverPassword", "", Source.TEST);

            StroomProperties.setOverrideProperty("stroom.lifecycle.enabled", "false", Source.TEST);

            process();
        } finally {
            StroomProperties.removeOverrides();

            ExternalShutdownController.shutdown();
        }
    }

    private void process() {
        final long startTime = System.currentTimeMillis();

        // Initialise some variables.
        init();

        final Injector injector = createInjector();

        // Start persistance.
        injector.getInstance(PersistService.class).start();
        try {
            final PipelineScopeRunnable pipelineScopeRunnable = injector.getInstance(PipelineScopeRunnable.class);
            pipelineScopeRunnable.scopeRunnable(() -> {
                process(injector);
            });
        } finally {
            // Stop persistance.
            injector.getInstance(PersistService.class).stop();
        }

        LOGGER.info("Processing completed in "
                + ModelStringUtil.formatDurationString(System.currentTimeMillis() - startTime));
    }

    private void process(final Injector injector) {
        // Because we use HSQLDB for headless we need to insert stream types this way for now.
        final StreamTypeServiceTransactionHelper streamTypeServiceTransactionHelper = injector.getInstance(StreamTypeServiceTransactionHelper.class);
        streamTypeServiceTransactionHelper.doInserts();

        // Set the content directory.
        final FSPersistenceConfig fsPersistenceConfig = injector.getInstance(FSPersistenceConfig.class);
        fsPersistenceConfig.setPath(contentDir.toAbsolutePath().toString());

        // Read the configuration.
        readConfig(injector);

        OutputStreamWriter outputStreamWriter = null;
        try {
            // Create the required output stream writer.
            final OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(outputFile));
            outputStreamWriter = new OutputStreamWriter(outputStream, StreamUtil.DEFAULT_CHARSET);

            // Create an XML writer.
            final TransformerHandler th = XMLUtil.createTransformerHandler(true);
            th.setResult(new StreamResult(outputStreamWriter));

            // Make sure the output is safe.
            final SafeXMLFilter safeXMLFilter = new SafeXMLFilter();
            safeXMLFilter.setContentHandler(th);

            // Create a filter that will deal with errors etc.
            final HeadlessFilter headlessFilter = new HeadlessFilter();
            headlessFilter.setContentHandler(safeXMLFilter);

            // Output the start root element.
            headlessFilter.beginOutput();

            processRepository(injector, headlessFilter);

            // Output the end root element.
            headlessFilter.endOutput();

        } catch (final IOException | TransformerConfigurationException | SAXException | RuntimeException e) {
            LOGGER.error("Unable to process headless", e);
        } finally {
            try {
                // Close the output stream writer.
                if (outputStreamWriter != null) {
                    outputStreamWriter.flush();
                    outputStreamWriter.close();
                }
            } catch (final IOException e) {
                LOGGER.error("Unable to flush and close outputStreamWriter", e);
            }
        }
    }

    private void processRepository(final Injector injector, final HeadlessFilter headlessFilter) {
        try {
            final TaskManager taskManager = injector.getInstance(TaskManager.class);

            // Loop over all of the data files in the repository.
            final StroomZipRepository repo = new StroomZipRepository(FileUtil.getCanonicalPath(inputDir));
            final List<Path> zipFiles = repo.listAllZipFiles();
            zipFiles.sort(Comparator.naturalOrder());
            try (final Stream<Path> stream = zipFiles.stream()) {
                stream.forEach(p -> {
                    try {
                        LOGGER.info("Processing: " + FileUtil.getCanonicalPath(p));

                        final StroomZipFile stroomZipFile = new StroomZipFile(p);
                        final StroomZipNameSet nameSet = stroomZipFile.getStroomZipNameSet();

                        // Process each base file in a consistent order
                        for (final String baseName : nameSet.getBaseNameList()) {
                            final InputStream dataStream = stroomZipFile.getInputStream(baseName, StroomZipFileType.Data);
                            final InputStream metaStream = stroomZipFile.getInputStream(baseName, StroomZipFileType.Meta);
                            final InputStream contextStream = stroomZipFile.getInputStream(baseName, StroomZipFileType.Context);

                            final HeadlessTranslationTask task = new HeadlessTranslationTask(
                                    IgnoreCloseInputStream.wrap(dataStream), IgnoreCloseInputStream.wrap(metaStream),
                                    IgnoreCloseInputStream.wrap(contextStream), headlessFilter);
                            taskManager.exec(task);
                        }

                        // Close the zip file.
                        stroomZipFile.close();
                    } catch (final IOException e) {
                        LOGGER.error(e.getMessage(), e);
                    }
                });
            }
        } catch (final RuntimeException e) {
            LOGGER.error("Unable to process repository!", e);
        }
    }

    private void readConfig(final Injector injector) {
        LOGGER.info("Reading configuration from: " + FileUtil.getCanonicalPath(configFile));

        final ImportExportService importExportService = injector.getInstance(ImportExportService.class);
        importExportService.performImportWithoutConfirmation(configFile);

        final NodeCache nodeCache = injector.getInstance(NodeCache.class);
        final VolumeService volumeService = injector.getInstance(VolumeService.class);
        volumeService
                .save(Volume.create(nodeCache.getDefaultNode(), FileUtil.getCanonicalPath(tmpDir) + "/cvol", VolumeType.PUBLIC));
    }

    private Injector createInjector() {
        final Injector injector = Guice.createInjector(new HeadlessModule());
        injector.injectMembers(this);

        return injector;
    }
}

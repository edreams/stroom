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

package stroom.util;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import stroom.feed.FeedService;
import stroom.feed.shared.Feed;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.persist.PersistService;
import stroom.streamstore.StreamSource;
import stroom.streamstore.StreamStore;
import stroom.streamstore.StreamTypeService;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamDataSource;
import stroom.streamstore.shared.StreamType;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Handy tool to dump out content.
 */
public class StreamDumpTool extends AbstractCommandLineTool {
    private final ToolInjector toolInjector = new ToolInjector();

    private String feed;
    private String streamType;
    private String createPeriodFrom;
    private String createPeriodTo;
    private String outputDir;

    public static void main(final String[] args) {
        new StreamDumpTool().doMain(args);
    }

    public void setFeed(final String feed) {
        this.feed = feed;
    }

    public void setStreamType(final String streamType) {
        this.streamType = streamType;
    }

    public void setCreatePeriodFrom(final String createPeriodFrom) {
        this.createPeriodFrom = createPeriodFrom;
    }

    public void setCreatePeriodTo(final String createPeriodTo) {
        this.createPeriodTo = createPeriodTo;
    }

    public void setOutputDir(final String outputDir) {
        this.outputDir = outputDir;
    }

    @Override
    public void run() {
        // Boot up Guice
        final Injector injector = toolInjector.getInjector();
        // Start persistance.
        injector.getInstance(PersistService.class).start();
        try {
            process(injector);
        } finally {
            // Stop persistance.
            injector.getInstance(PersistService.class).stop();
        }
    }

    private void process(final Injector injector) {
        final ExpressionOperator.Builder builder = new ExpressionOperator.Builder(Op.AND);

        if (createPeriodFrom != null && !createPeriodFrom.isEmpty() && createPeriodTo != null && !createPeriodTo.isEmpty()) {
            builder.addTerm(StreamDataSource.CREATE_TIME, Condition.BETWEEN, createPeriodFrom + "," + createPeriodTo);
        } else if (createPeriodFrom != null && !createPeriodFrom.isEmpty()) {
            builder.addTerm(StreamDataSource.CREATE_TIME, Condition.GREATER_THAN_OR_EQUAL_TO, createPeriodFrom);
        } else if (createPeriodTo != null && !createPeriodTo.isEmpty()) {
            builder.addTerm(StreamDataSource.CREATE_TIME, Condition.LESS_THAN_OR_EQUAL_TO, createPeriodTo);
        }

        if (outputDir == null || outputDir.length() == 0) {
            throw new RuntimeException("Output directory must be specified");
        }

        final Path dir = Paths.get(outputDir);
        if (!Files.isDirectory(dir)) {
            System.out.println("Creating directory '" + outputDir + "'");
            try {
                Files.createDirectories(dir);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        final StreamStore streamStore = injector.getInstance(StreamStore.class);
        final FeedService feedService = injector.getInstance(Key.get(FeedService.class, Names.named("cachedFeedService")));
        final StreamTypeService streamTypeService = injector.getInstance(Key.get(StreamTypeService.class, Names.named("cachedStreamTypeService")));

        Feed definition = null;
        if (feed != null) {
            definition = feedService.loadByName(feed);
            if (definition == null) {
                throw new RuntimeException("Unable to locate Feed " + feed);
            }
            builder.addTerm(StreamDataSource.FEED_NAME, Condition.EQUALS, definition.getName());
        }

        if (streamType != null) {
            final StreamType type = streamTypeService.loadByName(streamType);
            if (type == null) {
                throw new RuntimeException("Unable to locate stream type " + streamType);
            }
            builder.addTerm(StreamDataSource.STREAM_TYPE_NAME, Condition.EQUALS, type.getDisplayValue());
        } else {
            builder.addTerm(StreamDataSource.STREAM_TYPE_NAME, Condition.EQUALS, StreamType.RAW_EVENTS.getDisplayValue());
        }

        // Query the stream store
        final FindStreamCriteria criteria = new FindStreamCriteria();
        criteria.setExpression(builder.build());
        final List<Stream> results = streamStore.find(criteria);
        System.out.println("Starting dump of " + results.size() + " streams");

        int count = 0;
        for (final Stream stream : results) {
            count++;
            processFile(count, results.size(), streamStore, stream.getId(), dir);
        }

        System.out.println("Finished dumping " + results.size() + " streams");
    }

    /**
     * Scan a file
     */
    private void processFile(final int count, final int total, final StreamStore streamStore, final long streamId,
                             final Path outputDir) {
        StreamSource streamSource = null;
        try {
            streamSource = streamStore.openStreamSource(streamId);
            if (streamSource != null) {
                InputStream inputStream = null;
                try {
                    inputStream = streamSource.getInputStream();
                    final Path outputFile = outputDir.resolve(streamId + ".dat");
                    System.out.println(
                            "Dumping stream " + count + " of " + total + " to file '" + FileUtil.getCanonicalPath(outputFile) + "'");
                    StreamUtil.streamToFile(inputStream, outputFile);
                } catch (final RuntimeException e) {
                    e.printStackTrace();
                } finally {
                    inputStream.close();
                }
            }
        } catch (final IOException | RuntimeException e) {
            e.printStackTrace();
        } finally {
            if (streamSource != null) {
                streamStore.closeStreamSource(streamSource);
            }
        }
    }
}

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

package stroom.streamstore;

import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.Clearable;
import stroom.entity.shared.NamedEntity;
import stroom.entity.shared.Period;
import stroom.feed.MetaMap;
import stroom.io.SeekableInputStream;
import stroom.streamstore.api.StreamProperties;
import stroom.streamstore.api.StreamSource;
import stroom.streamstore.api.StreamStore;
import stroom.streamstore.api.StreamTarget;
import stroom.streamstore.shared.Feed;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.FindStreamTypeCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamAttributeMap;
import stroom.streamstore.shared.StreamDataSource;
import stroom.streamstore.shared.StreamType;
import stroom.util.collections.TypedMap;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class MockStreamStore implements StreamStore, Clearable {
    /**
     * Our stream data.
     */
    private final TypedMap<Stream, TypedMap<Long, byte[]>> fileData = TypedMap.fromMap(new HashMap<>());
    private final TypedMap<Stream, TypedMap<Long, ByteArrayOutputStream>> openOutputStream = TypedMap
            .fromMap(new HashMap<>());
    private final Set<Stream> openInputStream = new HashSet<>();
    private final Map<Long, Stream> streamMap = new HashMap<>();

    private Stream lastStream;

    /**
     * This id is used to emulate the primary key on the database.
     */
    private long currentId;

    private final FeedService feedService;
    private final StreamTypeService streamTypeService;

    @SuppressWarnings("unused")
    @Inject
    MockStreamStore(final FeedService feedService,
                    final StreamTypeService streamTypeService) {
        this.feedService = feedService;
        this.streamTypeService = streamTypeService;
    }

    public MockStreamStore() {
        this.feedService = new MockFeedService();
        this.streamTypeService = new MockStreamTypeService();
    }

    @Override
    public Stream createStream(final StreamProperties streamProperties) {
        final StreamType streamType = streamTypeService.getOrCreate(streamProperties.getStreamTypeName());
        final Feed feed = feedService.getOrCreate(streamProperties.getFeedName());

        final Stream stream = new Stream();

        if (streamProperties.getParent() != null) {
            stream.setParentStreamId(streamProperties.getParent().getId());
        }

        stream.setFeed(feed);
        stream.setStreamType(streamType);
        stream.setStreamProcessor(streamProperties.getStreamProcessor());
        if (streamProperties.getStreamTask() != null) {
            stream.setStreamTaskId(streamProperties.getStreamTask().getId());
        }
        stream.setCreateMs(streamProperties.getCreateMs());
        stream.setEffectiveMs(streamProperties.getEffectiveMs());
        stream.setStatusMs(streamProperties.getStatusMs());

        return stream;
    }

    /**
     * Load a stream by id.
     *
     * @param id The stream id to load a stream for.
     * @return The loaded stream if it exists (has not been physically deleted)
     * and is not logically deleted or locked, null otherwise.
     */
    @Override
    public Stream loadStreamById(final long id) {
        return streamMap.get(id);
    }

    /**
     * Load a stream by id.
     *
     * @param id        The stream id to load a stream for.
     * @param anyStatus Used to specify if this method will return streams that are
     *                  logically deleted or locked. If false only unlocked streams
     *                  will be returned, null otherwise.
     * @return The loaded stream if it exists (has not been physically deleted)
     * else null. Also returns null if one exists but is logically
     * deleted or locked unless <code>anyStatus</code> is true.
     */
    @Override
    public Stream loadStreamById(final long id, final boolean anyStatus) {
        return loadStreamById(id);
    }

    /**
     * Class this API to clear down things.
     */
    @Override
    public void clear() {
        fileData.clear();
        openOutputStream.clear();
        openInputStream.clear();
        streamMap.clear();
        currentId = 0;
    }

    public int getStreamStoreCount() {
        return fileData.size();
    }

    @Override
    public void closeStreamSource(final StreamSource source) {
        // Close the stream source.
        try {
            source.close();
        } catch (final IOException e) {
            throw new StreamException(e.getMessage());
        }
        openInputStream.remove(source.getStream());
    }

    @Override
    public void closeStreamTarget(final StreamTarget target) {
        // Close the stream target.
        try {
            target.close();
        } catch (final IOException e) {
            throw new StreamException(e.getMessage());
        }

        final Stream stream = target.getStream();

        // Get the data map to add the stream output to.
        TypedMap<Long, byte[]> dataTypeMap = fileData.get(stream);
        if (dataTypeMap == null) {
            dataTypeMap = TypedMap.fromMap(new HashMap<>());
            fileData.put(stream, dataTypeMap);
        }

        final TypedMap<Long, ByteArrayOutputStream> typeMap = openOutputStream.get(stream);

        if (typeMap != null) {
            // Add data from this stream to the data type map.
            final ByteArrayOutputStream ba = typeMap.remove(getStreamTypeId(target.getType()));
            if (ba != null && ba.toByteArray() != null) {
                dataTypeMap.put(getStreamTypeId(target.getType()), ba.toByteArray());
            } else {
                dataTypeMap.put(getStreamTypeId(target.getType()), new byte[0]);
            }

            // Clean up the open output streams if there are no more open types
            // for this stream.
            if (typeMap.size() == 0) {
                openOutputStream.remove(stream);
            }
        } else {
            dataTypeMap.put(getStreamTypeId(target.getType()), new byte[0]);
        }

        // Close child streams.
        for (final StreamType childType : ((MockStreamTarget) target).childMap.keySet()) {
            closeStreamTarget(target.getChildStream(childType));
        }
    }

    @Override
    public Long deleteStream(final Stream stream) {
        openInputStream.remove(stream);
        openOutputStream.remove(stream);
        fileData.remove(stream);
        return 1L;
    }

    @Override
    public Long deleteStreamTarget(final StreamTarget target) {
        openOutputStream.remove(target.getStream());
        fileData.remove(target.getStream());
        return 1L;
    }

    @Override
    public List<Stream> findEffectiveStream(final EffectiveMetaDataCriteria criteria) {
        final StreamType streamType = streamTypeService.getOrCreate(criteria.getStreamType());
        final ArrayList<Stream> results = new ArrayList<>();

        try {
            for (final Stream stream : fileData.keySet()) {
                final TypedMap<Long, byte[]> typeMap = fileData.get(stream);

                boolean match = true;

                if (typeMap == null) {
                    match = false;
                } else if (!typeMap.containsKey(streamType.getId())) {
                    match = false;
                } else if (!criteria.getFeed().equals(stream.getFeed().getName())) {
                    match = false;
                }

                if (match) {
                    results.add(stream);
                }
            }
        } catch (final RuntimeException e) {
            System.out.println(e.getMessage());
            // Ignore ... just a mock
        }

        return BaseResultList.createUnboundedList(results);
    }

    @Override
    public StreamSource openStreamSource(final long streamId) throws StreamException {
        return openStreamSource(streamId, false);
    }

    /**
     * <p>
     * Open a existing stream source.
     * </p>
     *
     * @param streamId  The stream id to open a stream source for.
     * @param anyStatus Used to specify if this method will return stream sources that
     *                  are logically deleted or locked. If false only unlocked stream
     *                  sources will be returned, null otherwise.
     * @return The loaded stream source if it exists (has not been physically
     * deleted) else null. Also returns null if one exists but is
     * logically deleted or locked unless <code>anyStatus</code> is
     * true.
     * @throws StreamException Could be thrown if no volume
     */
    @Override
    public StreamSource openStreamSource(final long streamId, final boolean anyStatus) throws StreamException {
        final Stream stream = loadStreamById(streamId, anyStatus);
        if (stream == null) {
            return null;
        }

        Stream actualStream = null;
        for (final Stream s : fileData.keySet()) {
            if (s.equals(stream)) {
                actualStream = s;
            }
        }

        if (actualStream == null) {
            throw new RuntimeException("Unable to find actual stream in mock");
        }

        openInputStream.add(actualStream);
        return new MockStreamSource(actualStream);
    }

    @Override
    public StreamTarget openStreamTarget(final StreamProperties streamProperties) {
        final Stream stream = createStream(streamProperties);
        return openStreamTarget(stream);
    }

    @Override
    public StreamTarget openExistingStreamTarget(final Stream stream) throws StreamException {
        return openStreamTarget(stream);
    }

    private StreamTarget openStreamTarget(final Stream stream) {
        if (!stream.isPersistent()) {
            currentId++;
            stream.setId(currentId);
            streamMap.put(stream.getId(), stream);
        }

        final TypedMap<Long, ByteArrayOutputStream> typeMap = TypedMap.fromMap(new HashMap<>());
        typeMap.put(getStreamTypeId(stream.getStreamType()), new ByteArrayOutputStream());
        openOutputStream.put(stream, typeMap);

        lastStream = stream;

        return new MockStreamTarget(stream);
    }

    public Stream getLastStream() {
        return lastStream;
    }

    public TypedMap<Stream, TypedMap<Long, byte[]>> getFileData() {
        return fileData;
    }

    private TypedMap<Stream, TypedMap<Long, ByteArrayOutputStream>> getOpenOutputStream() {
        return openOutputStream;
    }

    @Override
    public long getLockCount() {
        return openInputStream.size() + openOutputStream.size();
    }

    @Override
    public BaseResultList<Stream> find(final FindStreamCriteria criteria) {
        final ExpressionMatcher expressionMatcher = new ExpressionMatcher(StreamDataSource.getExtendedFieldMap(), null);
        final List<Stream> list = new ArrayList<>();
        for (final Stream stream : fileData.keySet()) {
            try {
                final StreamAttributeMap streamAttributeMap = new StreamAttributeMap(stream);
                final Map<String, Object> attributeMap = StreamAttributeMapUtil.createAttributeMap(streamAttributeMap);
                if (expressionMatcher.match(attributeMap, criteria.getExpression())) {
                    list.add(stream);
                }
            } catch (final RuntimeException e) {
                // Ignore.
            }
        }

        return BaseResultList.createUnboundedList(list);
    }

    /**
     * Overridden.
     *
     * @param findStreamCriteria NA
     * @return NA
     */
    @Override
    public BaseResultList<Stream> find(final OldFindStreamCriteria findStreamCriteria) {
        final List<Stream> list = new ArrayList<>();
        for (final Stream stream : fileData.keySet()) {
            if (findStreamCriteria.isMatch(stream)) {
                list.add(stream);
            }
        }

        return BaseResultList.createUnboundedList(list);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("Stream Store Contains:\n");
        for (final Stream stream : fileData.keySet()) {
            sb.append(stream);
            sb.append("\n");
        }
        sb.append("\nOpen Input Streams:\n");
        for (final Stream stream : openInputStream) {
            sb.append(stream);
            sb.append("\n");
        }
        sb.append("\nOpen Output Streams:\n");
        for (final Stream stream : openOutputStream.keySet()) {
            sb.append(stream);
            sb.append("\n");
        }
        return sb.toString();
    }

    @Override
    public Period getCreatePeriod() {
        return new Period(0L, Long.MAX_VALUE);
    }

    @Override
    public Long findDelete(final FindStreamCriteria criteria) {
        return null;
    }

    @Override
    public FindStreamCriteria createCriteria() {
        return new FindStreamCriteria();
    }

    private Long getStreamTypeId(final StreamType streamType) {
        if (streamType == null) {
            return null;
        }
        return streamType.getId();
    }

    @Override
    public List<String> getFeeds() {
        final List<Feed> feeds = feedService.find(new FindFeedCriteria());
        if (feeds == null) {
            return Collections.emptyList();
        }
        return feeds.stream()
                .map(NamedEntity::getName)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getStreamTypes() {
        final List<StreamType> streamTypes = streamTypeService.find(new FindStreamTypeCriteria());
        if (streamTypes == null) {
            return Collections.emptyList();
        }
        return streamTypes.stream()
                .map(NamedEntity::getName)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
    }

    private static class SeekableByteArrayInputStream extends ByteArrayInputStream implements SeekableInputStream {
        SeekableByteArrayInputStream(final byte[] bytes) {
            super(bytes);
        }

        @Override
        public long getPosition() {
            return pos;
        }

        @Override
        public long getSize() {
            return buf.length;
        }

        @Override
        public void seek(final long pos) {
            this.pos = (int) pos;
        }
    }

    private class MockStreamTarget implements StreamTarget {
        private final Stream stream;
        private final StreamType type;
        private final MetaMap attributeMap = new MetaMap();
        private final Map<StreamType, MockStreamTarget> childMap = new HashMap<>();
        private ByteArrayOutputStream outputStream = null;
        private StreamTarget parent;

        MockStreamTarget(final Stream stream) {
            this.stream = stream;
            this.type = stream.getStreamType();
        }

        MockStreamTarget(final StreamTarget parent, final StreamType type) {
            this.parent = parent;
            this.stream = parent.getStream();
            this.type = type;
        }

        @Override
        public OutputStream getOutputStream() {
            if (outputStream == null) {
                final TypedMap<Long, ByteArrayOutputStream> typeMap = getOpenOutputStream().get(stream);
                outputStream = typeMap.get(getStreamTypeId(type));
            }
            return outputStream;
        }

        @Override
        public void close() {
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (final IOException ioEx) {
                // Wrap it
                throw new RuntimeException(ioEx);
            }
        }

        @Override
        public Stream getStream() {
            return stream;
        }

        @Override
        public StreamTarget addChildStream(final StreamType type) {
            final TypedMap<Long, ByteArrayOutputStream> typeMap = getOpenOutputStream().get(stream);
            typeMap.put(getStreamTypeId(type), new ByteArrayOutputStream());
            childMap.put(type, new MockStreamTarget(this, type));
            return childMap.get(type);
        }

        @Override
        public StreamTarget getChildStream(final StreamType type) {
            return childMap.get(type);
        }

        @Override
        public StreamTarget getParent() {
            return parent;
        }

        @Override
        public StreamType getType() {
            return type;
        }

        @Override
        public MetaMap getAttributeMap() {
            return attributeMap;
        }

        @Override
        public boolean isAppend() {
            return false;
        }
    }

    private class MockStreamSource implements StreamSource {
        private final Stream stream;
        private final StreamType type;
        private final MetaMap attributeMap = new MetaMap();
        private InputStream inputStream = null;
        private StreamSource parent;

        MockStreamSource(final Stream stream) {
            this.stream = stream;
            this.type = stream.getStreamType();
        }

        MockStreamSource(final StreamSource parent, final StreamType type) {
            this.parent = parent;
            this.stream = parent.getStream();
            this.type = type;
        }

        @Override
        public InputStream getInputStream() {
            if (inputStream == null) {
                final TypedMap<Long, byte[]> typeMap = getFileData().get(stream);
                final byte[] data = typeMap.get(getStreamTypeId(type));

                if (data == null) {
                    throw new IllegalStateException("Some how we have null data stream in the stream store");
                }
                inputStream = new SeekableByteArrayInputStream(data);
            }
            return inputStream;
        }

        /**
         * Close off the stream.
         */
        @Override
        public void close() {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (final IOException ioEx) {
                // Wrap it
                throw new RuntimeException(ioEx);
            }
        }

        @Override
        public Stream getStream() {
            return stream;
        }

        @Override
        public StreamSource getChildStream(final StreamType type) {
            final TypedMap<Long, byte[]> typeMap = getFileData().get(stream);
            if (typeMap.containsKey(getStreamTypeId(type))) {
                return new MockStreamSource(this, type);
            }

            if (type == StreamType.BOUNDARY_INDEX) {
                return new MockBoundaryStreamSource(this);
            }

            return null;
        }

        @Override
        public StreamSource getParent() {
            return parent;
        }

        @Override
        public StreamType getType() {
            return type;
        }

        @Override
        public MetaMap getAttributeMap() {
            return attributeMap;
        }
    }

    private class MockBoundaryStreamSource extends MockStreamSource {
        MockBoundaryStreamSource(final StreamSource parent) {
            super(parent, StreamType.BOUNDARY_INDEX);
        }

        @Override
        public InputStream getInputStream() {
            return new SeekableByteArrayInputStream(new byte[0]);
        }
    }
}

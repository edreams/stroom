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

package stroom.index.server;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LiveIndexWriterConfig;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.store.SimpleFSLockFactory;
import org.apache.lucene.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.index.server.analyzer.AnalyzerFactory;
import stroom.index.shared.Index;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexField.AnalyzerType;
import stroom.index.shared.IndexFields;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.IndexShardService;
import stroom.streamstore.server.fs.FileSystemUtil;
import stroom.util.logging.LoggerPrintStream;
import stroom.util.shared.ModelStringUtil;

import javax.persistence.EntityNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

public class IndexShardWriterImpl implements IndexShardWriter {
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexShardWriterImpl.class);

    static final int DEFAULT_RAM_BUFFER_MB_SIZE = 1024;

    /**
     * Used to manage the way fields are analysed.
     */
    private final Map<String, Analyzer> fieldAnalyzers;
    private final PerFieldAnalyzerWrapper analyzerWrapper;
    private volatile Index fieldIndex;
    private final ReentrantLock fieldAnalyzerLock = new ReentrantLock();

    private final IndexShardService service;
    private volatile IndexShard indexShard;
    private volatile int ramBufferSizeMB = DEFAULT_RAM_BUFFER_MB_SIZE;

    /**
     * A count of documents added to the index used to control the maximum number of documents that are added.
     * Note that due to the multi-threaded nature of document addition and how this count is used to control
     * addition this will not always be accurate.
     */
    private final AtomicInteger documentCount = new AtomicInteger();
//    private final AtomicInteger actualDocumentCount = new AtomicInteger();

    private final Path dir;

    /**
     * Lucene stuff
     */
    private volatile Directory directory;
    private volatile IndexWriter indexWriter;

    private final int maxDocumentCount;

    private volatile Integer lastDocumentCount;
    private volatile Long lastCommitMs;
    private volatile Integer lastCommitDocumentCount;
    private volatile Long lastCommitDurationMs;

    private volatile boolean checked;

    /**
     * When we are in debug mode we track some important info from the LUCENE
     * log so that we can report some debug info
     */
    private static final Map<String, String> LOG_WATCH_TERMS;

    static {
        LOG_WATCH_TERMS = new ConcurrentHashMap<>();
        LOG_WATCH_TERMS.put("Flush Count", "flush: now pause all indexing threads");
        LOG_WATCH_TERMS.put("Commit Count", "startCommit()");
    }

    private LoggerPrintStream loggerPrintStream;

    /**
     * Convenience constructor used in tests.
     */
    public IndexShardWriterImpl(final IndexShardService service, final IndexFields indexFields, final Index index,
                                final IndexShard indexShard) {
        this(service, indexFields, index, indexShard, DEFAULT_RAM_BUFFER_MB_SIZE);
    }

    IndexShardWriterImpl(final IndexShardService service, final IndexFields indexFields, final Index index,
                         final IndexShard indexShard, final int ramBufferSizeMB) {
        this.service = service;
        this.indexShard = indexShard;
        this.ramBufferSizeMB = ramBufferSizeMB;
        this.maxDocumentCount = index.getMaxDocsPerShard();

        // Find the index shard path.
        dir = IndexShardUtil.getIndexPath(indexShard);

        // Make sure the index writer is primed with the necessary analysers.
        LOGGER.debug("Updating field analysers");

        // Setup the field analyzers.
        final Analyzer defaultAnalyzer = AnalyzerFactory.create(AnalyzerType.ALPHA_NUMERIC, false);
        fieldAnalyzers = new HashMap<>();
        updateFieldAnalyzers(indexFields);
        analyzerWrapper = new PerFieldAnalyzerWrapper(defaultAnalyzer, fieldAnalyzers);
    }

    /**
     * You can set this before the index has opened
     */
    public void setRamBufferSizeMB(final int ramBufferSizeMB) {
        this.ramBufferSizeMB = ramBufferSizeMB;
    }

    @Override
    public synchronized boolean open(final boolean create) {
        boolean success = false;

        try {
            switch (getStatus()) {
                case CLOSED:
                    success = doOpen(create);
                    break;
                case CLOSING:
                    LOGGER.warn("Attempt to open an index shard that is closing");
                    break;
                case OPEN:
                    LOGGER.warn("Attempt to open an index shard that is already open");
                    break;
                case OPENING:
                    LOGGER.warn("Attempt to open an index shard that is already opening");
                    break;
                case DELETED:
                    LOGGER.warn("Attempt to open an index shard that is deleted");
                    break;
                case CORRUPT:
                    LOGGER.warn("Attempt to open an index shard that is corrupt");
                    break;
            }
        } catch (final Throwable t) {
            LOGGER.error(t.getMessage(), t);
        }

        return success;
    }

    private synchronized boolean doOpen(final boolean create) {
        boolean success = false;

        // Never open deleted or corrupt index shards.
        if (IndexShardStatus.CLOSED.equals(getStatus())) {
            try {
                // Let everybody know we are opening this shard.
                setStatus(IndexShardStatus.OPENING);

                // Don't open old index shards for writing.
                final Version currentVersion = LuceneVersionUtil.getLuceneVersion(LuceneVersionUtil.getCurrentVersion());
                final Version shardVersion = LuceneVersionUtil.getLuceneVersion(indexShard.getIndexVersion());
                if (!shardVersion.equals(currentVersion)) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Shard version is different to current version " + indexShard);
                    }
                    return false;
                }

                final long startMs = System.currentTimeMillis();
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Opening " + indexShard);
                }

                if (create) {
                    // Make sure the index directory does not exist. If one does
                    // then throw an exception
                    // as we don't want to overwrite an index.
                    if (Files.isDirectory(dir)) {
                        // This is a workaround for lingering .nfs files.
                        try (final Stream<Path> stream = Files.list(dir)) {
                            stream.forEach(file -> {
                                if (Files.isDirectory(file) || !file.getFileName().startsWith(".")) {
                                    throw new IndexException("Attempting to create a new index in \""
                                            + dir.toAbsolutePath().toString() + "\" but one already exists.");
                                }
                            });
                        }
                    } else {
                        // Try and make all required directories.
                        try {
                            Files.createDirectories(dir);
                        } catch (final IOException e) {
                            throw new IndexException(
                                    "Unable to create directories for new index in \"" + dir.toAbsolutePath().toString() + "\"");
                        }
                    }
                } else {
                    // Ensure all shards are checked before they are opened.
                    check();
                }

                // Create lucene directory object.
                directory = new NIOFSDirectory(dir, SimpleFSLockFactory.INSTANCE);

                analyzerWrapper.setVersion(shardVersion);
                final IndexWriterConfig indexWriterConfig = new IndexWriterConfig(analyzerWrapper);

                // In debug mode we do extra trace in LUCENE and we also count
                // certain logging info like merge and flush
                // counts, so you can get this later using the trace method.
                if (LOGGER.isDebugEnabled()) {
                    loggerPrintStream = new LoggerPrintStream(LOGGER);
                    for (final String term : LOG_WATCH_TERMS.values()) {
                        loggerPrintStream.addWatchTerm(term);
                    }
                    indexWriterConfig.setInfoStream(loggerPrintStream);
                }

                // IndexWriter to use for adding data to the index.
                indexWriter = new IndexWriter(directory, indexWriterConfig);

                final LiveIndexWriterConfig liveIndexWriterConfig = indexWriter.getConfig();
                liveIndexWriterConfig.setRAMBufferSizeMB(ramBufferSizeMB);

                // TODO : We might still want to write separate segments I'm not sure on pros / cons ?
                liveIndexWriterConfig.setUseCompoundFile(false);
                liveIndexWriterConfig.setMaxBufferedDocs(Integer.MAX_VALUE);

                // Check the number of committed docs in this shard.
                final int numDocs = indexWriter.numDocs();
                documentCount.set(numDocs);
                lastDocumentCount = numDocs;
                if (create) {
                    if (lastDocumentCount != 0) {
                        LOGGER.error("Index should be new but already contains docs: " + lastDocumentCount);
                    }
                } else if (indexShard.getDocumentCount() != lastDocumentCount) {
                    LOGGER.error("Mismatch document count.  Index says " + lastDocumentCount + " DB says "
                            + indexShard.getDocumentCount());
                }


                // Output some debug.
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("getIndexWriter() - Opened " + indexShard + " in "
                            + (System.currentTimeMillis() - startMs) + "ms");
                }

                success = true;
                // We have opened the index so update the DB object.
                setStatus(IndexShardStatus.OPEN);

            } catch (final LockObtainFailedException t) {
                LOGGER.warn(t.getMessage());
                // Something went wrong.
                setStatus(IndexShardStatus.CLOSED);
            } catch (final Throwable t) {
                LOGGER.error(t.getMessage(), t);
                // Something went wrong.
                setStatus(IndexShardStatus.CORRUPT);
            }
        }

        return success;
    }

    @Override
    public synchronized boolean close() {
        boolean success = false;

        try {
            if (IndexShardStatus.OPEN.equals(getStatus())) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Closing index: " + toString());
                }

                try {
                    success = flushOrClose(true);
                } catch (final Throwable t) {
                    LOGGER.error(t.getMessage(), t);
                } finally {
                    indexWriter = null;

                    try {
                        if (directory != null) {
                            directory.close();
                        }
                    } catch (final Throwable t) {
                        LOGGER.error(t.getMessage(), t);
                    } finally {
                        directory = null;
                    }
                }
            }
        } catch (final Throwable t) {
            LOGGER.error(t.getMessage(), t);
        }

        return success;
    }

    @Override
    public synchronized void check() {
        if (!checked) {
            checked = true;

            // Don't check deleted shards.
            if (!IndexShardStatus.DELETED.equals(getStatus())) {
                try {
                    // Output some debug.
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Checking index - " + indexShard);
                    }
                    // Mark the index as closed.
                    setStatus(IndexShardStatus.CLOSED);

                    if (Files.isDirectory(dir)) {
                        try (final Stream<Path> stream = Files.list(dir)) {
                            if (stream.count() > 0) {
                                // The directory exists and contains files so make sure it
                                // is unlocked.
                                try {
                                    final Path lockFile = dir.resolve(IndexWriter.WRITE_LOCK_NAME);
                                    if (Files.isRegularFile(lockFile)) {
                                        Files.delete(lockFile);
                                    }
                                } catch (final IOException e) {
                                    // There is no lock file so ignore.
                                }

                                // Sync the DB.
                                sync();
                            } else {
                                if (!Files.isDirectory(dir)) {
                                    throw new IndexException("Unable to find index shard directory: " + dir.toString());
                                } else {
                                    throw new IndexException("Unable to find any index shard data in directory: " + dir.toString());
                                }
                            }
                        }
                    }
                } catch (final Throwable t) {
                    LOGGER.error(t.getMessage(), t);
                }
            }
        }
    }

    @Override
    public synchronized boolean delete() {
        boolean success = false;

        try {
            // Output some debug.
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Deleting index - " + indexShard);
            }

            // Just mark the index as deleted. We can clean it up later.
            setStatus(IndexShardStatus.DELETED);

            success = true;
        } catch (final Throwable t) {
            LOGGER.error(t.getMessage(), t);
        }

        return success;
    }

    @Override
    public synchronized boolean deleteFromDisk() {
        boolean success = false;

        try {
            if (!IndexShardStatus.DELETED.equals(getStatus())) {
                LOGGER.warn("deleteFromDisk() - Can only be called on delete records {}", indexShard);
            } else {
                // Make sure the shard is closed before it is deleted. If it
                // isn't then delete will fail as there
                // are open file handles.
                try {
                    if (indexWriter != null) {
                        indexWriter.close();
                    }
                } catch (final Throwable t) {
                    LOGGER.error(t.getMessage(), t);
                } finally {
                    indexWriter = null;
                    try {
                        if (directory != null) {
                            directory.close();
                        }
                    } catch (final Throwable t) {
                        LOGGER.error(t.getMessage(), t);
                    } finally {
                        directory = null;
                    }
                }

                // See if there are any files in the directory.
                if (!Files.isDirectory(dir) || FileSystemUtil.deleteDirectory(dir)) {
                    // The directory either doesn't exist or we have
                    // successfully deleted it so delete this index
                    // shard from the database.
                    if (service != null) {
                        success = service.delete(indexShard);
                    }
                }
            }
        } catch (final Throwable t) {
            LOGGER.error(t.getMessage(), t);
        }

        return success;
    }

    private final AtomicInteger adding = new AtomicInteger();

    @Override
    public void addDocument(final Document document) throws IOException, IndexException, AlreadyClosedException {
        adding.incrementAndGet();
        try {
            // Make sure the index is now open before we try and add a
            // document to it.
            final IndexWriter indexWriter = this.indexWriter;
            if (indexWriter == null || !IndexShardStatus.OPEN.equals(getStatus())) {
                throw new AlreadyClosedException("Shard is not open (status = " + getStatus() + ")");
            }

            // An Exception might be thrown here if the index
            // has been deleted. If this happens log the error
            // and return false so that the pool can return a
            // new index to add documents to.
            try {
                if (documentCount.getAndIncrement() >= maxDocumentCount) {
                    throw new IndexException("Shard is full");
                }

                final long startTime = System.currentTimeMillis();
                indexWriter.addDocument(document);
                final long duration = System.currentTimeMillis() - startTime;
                if (duration > 1000) {
                    LOGGER.warn("addDocument() - took " + ModelStringUtil.formatDurationString(duration)
                            + " " + toString());
                }

            } catch (final Throwable e) {
                documentCount.decrementAndGet();
                throw e;
            }

        } finally {
            adding.decrementAndGet();
        }
    }

    @Override
    public void updateIndex(final Index index) {
        // There's no point updating the analysers on a deleted index.
        if (!IndexShardStatus.DELETED.equals(getStatus())) {
            // Check if this index shard has been deleted on the DB.
            try {
                final IndexShard is = service.load(indexShard);
                if (is != null && IndexShardStatus.DELETED.equals(is.getStatus())) {
                    indexShard.setStatus(IndexShardStatus.DELETED);
                }
            } catch (final EntityNotFoundException e) {
                LOGGER.debug("Index shard has been deleted {}", indexShard);
                indexShard.setStatus(IndexShardStatus.DELETED);
            } catch (final Throwable t) {
                LOGGER.error(t.getMessage(), t);
            }

            if (!IndexShardStatus.DELETED.equals(getStatus())) {
                sync();
                checkRetention(index);
            }

            if (!IndexShardStatus.DELETED.equals(getStatus()) && !IndexShardStatus.CORRUPT.equals(getStatus())) {
                if (fieldIndex == null || fieldIndex.getVersion() != index.getVersion()) {
                    fieldAnalyzerLock.lock();
                    try {
                        if (fieldIndex == null || fieldIndex.getVersion() != index.getVersion()) {
                            fieldIndex = index;
                            final IndexFields indexFields = fieldIndex.getIndexFieldsObject();
                            updateFieldAnalyzers(indexFields);
                        }
                    } finally {
                        fieldAnalyzerLock.unlock();
                    }
                }
            }
        }

    }

    private void checkRetention(final Index index) {
        try {
            // Delete this shard if it is older than the retention age.
            if (index.getRetentionDayAge() != null && indexShard.getPartitionToTime() != null) {
                // See if this index shard is older than the index retention
                // period.
                final long retentionTime = ZonedDateTime.now(ZoneOffset.UTC).minusDays(index.getRetentionDayAge()).toInstant().toEpochMilli();
                final long shardAge = indexShard.getPartitionToTime();

                if (shardAge < retentionTime) {
                    delete();
                }
            }
        } catch (final Throwable t) {
            LOGGER.error(t.getMessage(), t);
        }
    }

    private void updateFieldAnalyzers(final IndexFields indexFields) {
        if (indexFields != null) {
            final Version luceneVersion = LuceneVersionUtil.getLuceneVersion(indexShard.getIndexVersion());
            for (final IndexField indexField : indexFields.getIndexFields()) {
                // Add the field analyser.
                final Analyzer analyzer = AnalyzerFactory.create(indexField.getAnalyzerType(),
                        indexField.isCaseSensitive());
                analyzer.setVersion(luceneVersion);
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Adding field analyser for: " + indexField.getFieldName());
                }
                fieldAnalyzers.put(indexField.getFieldName(), analyzer);
            }
        }
    }

    @Override
    public synchronized boolean flush() {
        return flushOrClose(false);
    }

    /**
     * Flush and close operations will cause docs in memory to be committed to
     * the shard so we use the same method to do both so we can consistently
     * record commit times and doc counts.
     */
    private synchronized boolean flushOrClose(final boolean close) {
        boolean success = false;

        if (IndexShardStatus.OPEN.equals(getStatus())) {
            // Record commit start time.
            final long startTime = System.currentTimeMillis();

            try {
                if (LOGGER.isDebugEnabled()) {
                    if (close) {
                        LOGGER.debug("Closing index: " + toString());
                    } else {
                        LOGGER.debug("Flushing index: " + toString());
                    }
                }

                // Find out how many docs the DB thinks the shard currently
                // contains.
                int docCountBeforeCommit;

                // Perform commit or close.
                if (close) {
                    setStatus(IndexShardStatus.CLOSING);

                    // Wait for us to stop adding docs.
                    while (adding.get() > 0) {
                        LOGGER.debug("Waiting for " + adding.get() + " docs to finish being added before we can close this shard");
                        Thread.sleep(1000);
                    }

                    docCountBeforeCommit = indexShard.getDocumentCount();
                    indexWriter.close();
                } else {
                    docCountBeforeCommit = indexShard.getDocumentCount();
                    indexWriter.commit();
                }

                // If the index is closed we can be sure no additional documents were added successfully.
                lastDocumentCount = documentCount.get();

                // Record when commit completed so we know how fresh the index
                // is for searching purposes.
                lastCommitMs = System.currentTimeMillis();

                // Find out how many docs were committed and how long it took.
                lastCommitDocumentCount = lastDocumentCount - docCountBeforeCommit;
                final long timeNow = System.currentTimeMillis();
                lastCommitDurationMs = (timeNow - startTime);

                // Output some debug so we know how long commits are taking.
                if (LOGGER.isDebugEnabled()) {
                    final String durationString = ModelStringUtil.formatDurationString(lastCommitDurationMs);
                    LOGGER.debug("flushOrClose() - documents written since last flush " + lastCommitDocumentCount + " ("
                            + durationString + ")");
                }

                success = true;

            } catch (final Exception e) {
                LOGGER.error("flushOrClose()", e);

            } finally {
                // Synchronise the DB entry.
                if (close) {
                    setStatus(IndexShardStatus.CLOSED);
                } else {
                    sync();
                }

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("flushOrClose() - lastCommitDocumentCount=" + lastCommitDocumentCount
                            + ", lastCommitDuration=" + lastCommitDurationMs);

                    final long duration = System.currentTimeMillis() - startTime;
                    final String durationString = ModelStringUtil.formatDurationString(duration);
                    LOGGER.debug("flushOrClose() - finish " + toString() + " " + durationString);
                }
            }
        }

        return success;
    }

    @Override
    protected void finalize() throws Throwable {
        if (indexWriter != null) {
            LOGGER.error("finalize() - Failed to close index");
        }

        super.finalize();
    }

    /**
     * Utility to update the stat's on the DB entity
     */
    synchronized void sync() {
        // Allow the thing to run without a service (e.g. benchmark mode)
        if (service != null) {
            boolean success = false;
            for (int i = 0; i < 10 && !success; i++) {
                refreshEntity();
                success = save();
            }
        } else {
            refreshEntity();
        }
    }

    @Override
    public IndexShardStatus getStatus() {
        return indexShard.getStatus();
    }

    public synchronized void setStatus(final IndexShardStatus status) {
        // Allow the thing to run without a service (e.g. benchmark mode)
        if (service != null) {
            boolean success = false;
            for (int i = 0; i < 10 && !success; i++) {
                refreshEntity();
                indexShard.setStatus(status);
                success = save();
            }
        } else {
            refreshEntity();
        }
    }

    private synchronized void reload() {
        try {
            final IndexShard is = service.load(indexShard);
            if (is != null) {
                indexShard = is;
            }
        } catch (final EntityNotFoundException e) {
            LOGGER.debug("Index shard has been deleted {}", indexShard);
            indexShard.setStatus(IndexShardStatus.DELETED);
        } catch (final Throwable t) {
            LOGGER.error(t.getMessage(), t);
        }
    }

    private synchronized boolean save() {
        boolean success = false;

        try {
            indexShard = service.save(indexShard);
            success = true;
        } catch (final EntityNotFoundException e) {
            LOGGER.debug("Index shard has been deleted {}", indexShard);
            indexShard.setStatus(IndexShardStatus.DELETED);
            success = true;
        } catch (final Throwable t) {
            LOGGER.debug(t.getMessage(), t);
            LOGGER.debug("Reloading index shard due to save error {}", indexShard);
            reload();
        }

        return success;
    }

    private synchronized void refreshEntity() {
        try {
            // Update the size of the index.
            if (dir != null && Files.isDirectory(dir)) {
                final AtomicLong totalSize = new AtomicLong();
                try (final Stream<Path> stream = Files.list(dir)) {
                    stream.forEach(file -> totalSize.addAndGet(file.toFile().length()));
                } catch (final IOException e) {
                    LOGGER.error(e.getMessage());
                }
                indexShard.setFileSize(totalSize.get());
            }

            // Only update the document count details if we have read them.
            if (lastDocumentCount != null) {
                indexShard.setDocumentCount(lastDocumentCount);
            }
            if (lastCommitDocumentCount != null) {
                indexShard.setCommitDocumentCount(lastCommitDocumentCount);
            }
            if (lastCommitDurationMs != null) {
                indexShard.setCommitDurationMs(lastCommitDurationMs);
            }
            if (lastCommitMs != null) {
                indexShard.setCommitMs(lastCommitMs);
            }
        } catch (final Throwable t) {
            LOGGER.error(t.getMessage(), t);
        }
    }

    @Override
    public String toString() {
        return "indexShard="
                + indexShard
                + ", indexWriter="
                + (indexWriter == null ? "closed" : "open")
                + ", docCount="
                + documentCount.get();
    }

    synchronized void trace(final PrintStream ps) {
        if (loggerPrintStream != null) {
            refreshEntity();

            ps.println("Document Count = " + ModelStringUtil.formatCsv(documentCount.intValue()));
            if (dir != null) {
                ps.println("Index File(s) Size = " + ModelStringUtil.formatIECByteSizeString(indexShard.getFileSize()));
            }
            ps.println("RAM Buffer Size = " + ModelStringUtil.formatCsv(ramBufferSizeMB) + " M");

            for (final Entry<String, String> term : LOG_WATCH_TERMS.entrySet()) {
                ps.println(term.getKey() + " = " + loggerPrintStream.getWatchTermCount(term.getValue()));
            }
        }
    }

    @Override
    public int getDocumentCount() {
        return documentCount.intValue();
    }

    @Override
    public IndexShard getIndexShard() {
        return indexShard;
    }

    @Override
    public int hashCode() {
        return indexShard.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null || !(obj instanceof IndexShardWriter)) {
            return false;
        }

        final IndexShardWriterImpl writer = (IndexShardWriterImpl) obj;
        return indexShard.equals(writer.indexShard);
    }

    @Override
    public String getPartition() {
        return indexShard.getPartition();
    }

    @Override
    public boolean isFull() {
        return documentCount.get() >= maxDocumentCount;
    }

    @Override
    public IndexWriter getWriter() {
        return indexWriter;
    }

    @Override
    public void destroy() {
        try {
            close();
        } catch (final Exception ex) {
            LOGGER.error("destroy() - Error closing writer {}", this);
        }
    }
}
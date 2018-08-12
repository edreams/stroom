/*
 * Copyright 2018 Crown Copyright
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

package stroom.refdata.lmdb;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.lmdbjava.KeyRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.offheapstore.ByteBufferPool;
import stroom.refdata.offheapstore.PooledByteBuffer;
import stroom.refdata.offheapstore.databases.AbstractLmdbDbTest;
import stroom.refdata.offheapstore.serdes.StringSerde;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public class TestBasicLmdbDb extends AbstractLmdbDbTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestBasicLmdbDb.class);

    private BasicLmdbDb<String, String> basicLmdbDb;

    @Before
    @Override
    public void setup() {
        super.setup();

        basicLmdbDb = new BasicLmdbDb<>(
                lmdbEnv,
                new ByteBufferPool(),
                new StringSerde(),
                new StringSerde(),
                "MyBasicLmdb");
    }

    @Test
    public void testBufferMutationAfterPut() {

        ByteBuffer keyBuffer = ByteBuffer.allocateDirect(50);
        ByteBuffer valueBuffer = ByteBuffer.allocateDirect(50);

        basicLmdbDb.getKeySerde().serialize(keyBuffer, "MyKey");
        basicLmdbDb.getValueSerde().serialize(valueBuffer, "MyValue");

        LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn -> {
            basicLmdbDb.put(writeTxn, keyBuffer, valueBuffer, false);
        });
        assertThat(basicLmdbDb.getEntryCount()).isEqualTo(1);

        // it is ok to mutate the buffers used in the put outside of the txn
        basicLmdbDb.getKeySerde().serialize(keyBuffer, "XX");
        basicLmdbDb.getValueSerde().serialize(valueBuffer, "YY");

        // now get the value again and it should be correct
        String val = basicLmdbDb.get("MyKey").get();

        assertThat(val).isEqualTo("MyValue");
    }

    /**
     * This test is an example of how to abuse LMDB. If run it will crash the JVM
     * as a direct bytebuffer returned from a put() was mutated outside the transaction.
     */
    @Ignore // see javadoc above
    @Test
    public void testBufferMutationAfterGet() {

        basicLmdbDb.put("MyKey", "MyValue", false);

        // the buffers have been returned to the pool and cleared
        assertThat(basicLmdbDb.getByteBufferPool().getCurrentPoolSize()).isEqualTo(2);

        assertThat(basicLmdbDb.getEntryCount()).isEqualTo(1);

        ByteBuffer keyBuffer = ByteBuffer.allocateDirect(50);
        basicLmdbDb.getKeySerde().serialize(keyBuffer, "MyKey");

        AtomicReference<ByteBuffer> valueBufRef = new AtomicReference<>();
        // now get the value again and it should be correct
        LmdbUtils.getWithReadTxn(lmdbEnv, txn -> {
            ByteBuffer valueBuffer = basicLmdbDb.getAsBytes(txn, keyBuffer).get();

            // hold on to the buffer for later
            valueBufRef.set(valueBuffer);

            String val = basicLmdbDb.getValueSerde().deserialize(valueBuffer);

            assertThat(val).isEqualTo("MyValue");

            return val;
        });

        // now mutate the value buffer outside any txn

        assertThat(valueBufRef.get().position()).isEqualTo(0);


        // This line will crash the JVM as we are mutating a directly allocated ByteBuffer
        // (that points to memory managed by LMDB) outside of a txn.
        basicLmdbDb.getValueSerde().serialize(valueBufRef.get(), "XXX");

    }

    @Test
    public void testGetAsBytes() {

        basicLmdbDb.put("key1", "value1", false);
        basicLmdbDb.put("key2", "value2", false);
        basicLmdbDb.put("key3", "value3", false);

        LmdbUtils.doWithReadTxn(lmdbEnv, txn -> {
            Optional<ByteBuffer> optKeyBuffer = basicLmdbDb.getAsBytes(txn, "key2");

            assertThat(optKeyBuffer).isNotEmpty();
        });
    }

    @Test
    public void testGetAsBytes2() {

        basicLmdbDb.put("key1", "value1", false);
        basicLmdbDb.put("key2", "value2", false);
        basicLmdbDb.put("key3", "value3", false);

        LmdbUtils.doWithReadTxn(lmdbEnv, txn -> {

            try (PooledByteBuffer pooledKeyBuffer = basicLmdbDb.getPooledKeyBuffer()) {
                ByteBuffer keyBuffer = pooledKeyBuffer.getByteBuffer();
                basicLmdbDb.serializeKey(keyBuffer, "key2");
                Optional<ByteBuffer> optValueBuffer = basicLmdbDb.getAsBytes(txn, keyBuffer);

                assertThat(optValueBuffer).isNotEmpty();
                String val = basicLmdbDb.deserializeKey(optValueBuffer.get());
                assertThat(val).isEqualTo("value2");
            }
        });
    }

    @Test
    public void testStreamEntries() {
        populateDb();

        List<String> entries = LmdbUtils.getWithReadTxn(lmdbEnv, txn ->
                basicLmdbDb.streamEntries(txn, KeyRange.all(), stream ->
                        stream
                                .map(kvTuple ->
                                        kvTuple._1() + "-" + kvTuple._2())
                                .peek(LOGGER::info)
                                .collect(Collectors.toList())));

        assertThat(entries).hasSize(20);
    }

    @Test
    public void testStreamEntriesWithFilter() {
        populateDb();

        List<String> entries = LmdbUtils.getWithReadTxn(lmdbEnv, txn ->
                basicLmdbDb.streamEntries(txn, KeyRange.all(), stream ->
                        stream
                                .filter(kvTuple -> {
                                    int i = Integer.parseInt(kvTuple._1());
                                    return i > 10 && i <= 15;
                                })
                                .map(kvTuple ->
                                        kvTuple._1() + "-" + kvTuple._2())
                                .peek(LOGGER::info)
                                .collect(Collectors.toList())));

        assertThat(entries).hasSize(5);
    }


    @Test
    public void testStreamEntriesWithKeyRange() {
        populateDb();

        KeyRange<String> keyRange = KeyRange.closed("06", "10");
        List<String> entries = LmdbUtils.getWithReadTxn(lmdbEnv, txn ->
                basicLmdbDb.streamEntries(txn, keyRange, stream ->
                        stream
                                .map(kvTuple ->
                                        kvTuple._1() + "-" + kvTuple._2())
                                .peek(LOGGER::info)
                                .collect(Collectors.toList())));

        assertThat(entries).hasSize(5);
    }

    @Test
    public void testKeyReuse() {
        basicLmdbDb.put("keyLongerThanValue1", "value1", false);
        basicLmdbDb.put("keyLongerThanValue2", "value2", false);
        basicLmdbDb.put("keyLongerThanValue3", "value3", false);


        LmdbUtils.doWithReadTxn(lmdbEnv, txn -> {
            ByteBuffer keyBuffer = ByteBuffer.allocateDirect(100);
            basicLmdbDb.serializeKey(keyBuffer, "keyLongerThanValue2");
            ByteBuffer keyBufferCopy = keyBuffer.asReadOnlyBuffer();

            ByteBuffer valueBuffer = basicLmdbDb.getAsBytes(txn, keyBuffer).get();
            String value = basicLmdbDb.deserializeValue(valueBuffer);

            assertThat(value).isEqualTo("value2");
            assertThat(keyBuffer).isEqualTo(keyBufferCopy);

            ByteBuffer valueBuffer2 = basicLmdbDb.getAsBytes(txn, keyBuffer).get();

            String value2 = basicLmdbDb.deserializeValue(valueBuffer2);

            assertThat(value2).isEqualTo("value2");
            assertThat(keyBuffer).isEqualTo(keyBufferCopy);


        });

    }

    private void populateDb() {
        // pad the keys to a fixed length so they sort in number order
        IntStream.rangeClosed(1, 20).forEach(i -> {
            basicLmdbDb.put(buildKey(i), buildValue(i), false);

        });

        basicLmdbDb.logDatabaseContents(LOGGER::info);
    }

    private String buildKey(int i) {
        return String.format("%02d",i);
    }

    private String buildValue(int i) {
        return "value" + i;
    }

}
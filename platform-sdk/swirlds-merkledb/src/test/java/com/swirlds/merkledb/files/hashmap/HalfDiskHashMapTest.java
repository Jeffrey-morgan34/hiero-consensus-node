// SPDX-License-Identifier: Apache-2.0
package com.swirlds.merkledb.files.hashmap;

import static com.swirlds.merkledb.test.fixtures.MerkleDbTestUtils.CONFIGURATION;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.merkledb.config.MerkleDbConfig;
import com.swirlds.merkledb.files.DataFileCompactor;
import com.swirlds.merkledb.test.fixtures.files.FilesTestType;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@SuppressWarnings({"SameParameterValue"})
class HalfDiskHashMapTest {

    /** Temporary directory provided by JUnit */
    @SuppressWarnings("unused")
    @TempDir
    Path tempDirPath;

    // =================================================================================================================
    // Helper Methods
    private HalfDiskHashMap createNewTempMap(FilesTestType testType, int count) throws IOException {
        // create map
        HalfDiskHashMap map = new HalfDiskHashMap(
                CONFIGURATION, count, tempDirPath.resolve(testType.name()), "HalfDiskHashMapTest", null, false);
        map.printStats();
        return map;
    }

    private static void createSomeData(
            FilesTestType testType, HalfDiskHashMap map, int start, int count, long dataMultiplier) throws IOException {
        map.startWriting();
        for (int i = start; i < (start + count); i++) {
            final Bytes key = testType.createVirtualLongKey(i);
            map.put(key, i * dataMultiplier);
        }
        //        map.debugDumpTransactionCache();
        long START = System.currentTimeMillis();
        map.endWriting();
        printTestUpdate(START, count, "Written");
    }

    private static void checkData(
            FilesTestType testType, HalfDiskHashMap map, int start, int count, long dataMultiplier) throws IOException {
        long START = System.currentTimeMillis();
        for (int i = start; i < (start + count); i++) {
            final Bytes key = testType.createVirtualLongKey(i);
            long result = map.get(key, -1);
            assertEquals(i * dataMultiplier, result, "Failed to read key=" + key + " dataMultiplier=" + dataMultiplier);
        }
        printTestUpdate(START, count, "Read");
    }

    // =================================================================================================================
    // Tests

    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    void createDataAndCheck(FilesTestType testType) throws Exception {
        final Path tempSnapshotDir = tempDirPath.resolve("DataFileTestSnapshot_" + testType.name());
        final int count = 10_000;
        // create map
        final HalfDiskHashMap map = createNewTempMap(testType, count);
        // create some data
        createSomeData(testType, map, 1, count, 1);
        // sequentially check data
        checkData(testType, map, 1, count, 1);
        // randomly check data
        Random random = new Random(1234);
        for (int j = 1; j < (count * 2); j++) {
            int i = 1 + random.nextInt(count);
            final Bytes key = testType.createVirtualLongKey(i);
            long result = map.get(key, 0);
            assertEquals(i, result, "unexpected value of newVirtualLongKey");
        }
        // create snapshot
        map.snapshot(tempSnapshotDir);
        // open snapshot and check data
        HalfDiskHashMap mapFromSnapshot =
                new HalfDiskHashMap(CONFIGURATION, count, tempSnapshotDir, "HalfDiskHashMapTest", null, false);
        mapFromSnapshot.printStats();
        checkData(testType, mapFromSnapshot, 1, count, 1);
        // check deletion
        map.startWriting();
        final Bytes key5 = testType.createVirtualLongKey(5);
        final Bytes key50 = testType.createVirtualLongKey(50);
        final Bytes key500 = testType.createVirtualLongKey(500);
        map.delete(key5);
        map.delete(key50);
        map.delete(key500);
        map.endWriting();
        assertEquals(-1, map.get(key5, -1), "Expect not to exist");
        assertEquals(-1, map.get(key50, -1), "Expect not to exist");
        assertEquals(-1, map.get(key500, -1), "Expect not to exist");
        checkData(testType, map, 1, 4, 1);
        checkData(testType, map, 6, 43, 1);
        checkData(testType, map, 51, 448, 1);
        checkData(testType, map, 501, 9499, 1);
        // check close and try read after
        map.close();
        assertEquals(-1, map.get(key5, -1), "Expect not found result as just closed the map!");
    }

    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    void multipleWriteBatchesAndMerge(FilesTestType testType) throws Exception {
        // create map
        final HalfDiskHashMap map = createNewTempMap(testType, 10_000);
        final DataFileCompactor dataFileCompactor = new DataFileCompactor(
                CONFIGURATION.getConfigData(MerkleDbConfig.class),
                "HalfDiskHashMapTest",
                map.getFileCollection(),
                map.getBucketIndexToBucketLocation(),
                null,
                null,
                null,
                null);
        // create some data
        createSomeData(testType, map, 1, 1111, 1);
        checkData(testType, map, 1, 1111, 1);
        // create some more data
        createSomeData(testType, map, 1111, 3333, 1);
        checkData(testType, map, 1, 3333, 1);
        // create some more data
        createSomeData(testType, map, 1111, 10_000, 1);
        checkData(testType, map, 1, 10_000, 1);
        // do a merge
        dataFileCompactor.compact();
        // check all data after
        checkData(testType, map, 1, 10_000, 1);
    }

    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    void updateData(FilesTestType testType) throws Exception {
        // create map
        final HalfDiskHashMap map = createNewTempMap(testType, 1000);
        // create some data
        createSomeData(testType, map, 0, 1000, 1);
        checkData(testType, map, 0, 1000, 1);
        // update some data
        createSomeData(testType, map, 200, 400, 2);
        checkData(testType, map, 0, 200, 1);
        checkData(testType, map, 200, 400, 2);
        checkData(testType, map, 600, 400, 1);
    }

    private static void printTestUpdate(long start, long count, String msg) {
        long took = System.currentTimeMillis() - start;
        double timeSeconds = (double) took / 1000d;
        double perSecond = (double) count / timeSeconds;
        System.out.printf("%s : [%,d] at %,.0f per/sec, took %,.2f seconds\n", msg, count, perSecond, timeSeconds);
    }
}

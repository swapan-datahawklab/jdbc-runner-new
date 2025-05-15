package com.example.shelldemo.parser;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

import org.junit.jupiter.api.DisplayName;

import com.example.shelldemo.spi.DatabaseVendor;
import com.example.shelldemo.spi.OracleVendor;
import com.example.shelldemo.spi.MySqlVendor;
import com.example.shelldemo.spi.PostgreSqlVendor;
import com.example.shelldemo.spi.SqlServerVendor;

/**
 * Test class for the concurrent SQL parser using virtual threads
 */
class ConcurrentSqlParserTest {

    static Stream<Arguments> vendorProvider() {
        return Stream.of(
            Arguments.of(new OracleVendor()),
            Arguments.of(new MySqlVendor()),
            Arguments.of(new PostgreSqlVendor()),
            Arguments.of(new SqlServerVendor())
        );
    }

    @ParameterizedTest
    @MethodSource("vendorProvider")
    @DisplayName("Test concurrent parsing with virtual threads for all vendors")
    void testConcurrentParsing(DatabaseVendor vendor) throws Exception {
        // Load test SQL files from classpath
        URL resourceUrl = getClass().getClassLoader().getResource("sql");
        if (resourceUrl == null) {
            fail("Test SQL directory not found in classpath");
        }
        File resourceFolder = new File(resourceUrl.toURI());
        if (!resourceFolder.exists() || !resourceFolder.isDirectory()) {
            fail("Test SQL directory not found");
        }
        File[] sqlFiles = resourceFolder.listFiles((dir, name) -> name.endsWith(".sql"));
        if (sqlFiles == null || sqlFiles.length == 0) {
            fail("No SQL files found for testing");
        }
        // Create a latch to wait for async processing
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Map<String, Map<Integer, String>>> resultRef = new AtomicReference<>();
        // Parse files concurrently and wait for completion
        ConcurrentSqlParser.parseFilesAsync(sqlFiles, result -> {
            resultRef.set(result);
            latch.countDown();
        }, vendor);
        // Wait for parsing to complete with timeout
        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertTrue(completed, "Concurrent parsing timed out");
        // Verify results
        Map<String, Map<Integer, String>> results = resultRef.get();
        assertNotNull(results, "Results should not be null");
        assertTrue(results.size() > 0, "Should have parsed at least one file");
        // Verify each file's statements
        results.forEach((fileName, statements) -> {
            System.out.println("File: " + fileName + " has " + statements.size() + " statements");
            assertTrue(statements.size() > 0, "Each file should have at least one statement");
        });
    }
}

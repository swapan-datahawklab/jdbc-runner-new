package com.example.shelldemo.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.example.shelldemo.UnifiedDatabaseOperation;
import com.example.shelldemo.UnifiedDatabaseOperationBuilder;
import com.example.shelldemo.config.ConfigurationService;
import com.example.shelldemo.config.ConfigHolderAdapter;
import com.example.shelldemo.exception.DatabaseException;
import com.example.shelldemo.spi.DatabaseVendor;
import com.example.shelldemo.spi.OracleVendor;
import com.example.shelldemo.spi.PostgreSqlVendor;

@DisplayName("Database Function and Procedure Integration Tests")
class DatabaseFunctionProcedureIntegrationTest {

    @TempDir
    Path tempDir;
    
    private UnifiedDatabaseOperation dbOp;
    private Connection connection;
    private ConfigurationService configService;
    
    @BeforeEach
    void setUp() throws SQLException {
        configService = new ConfigHolderAdapter();
    }
    
    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            // Run cleanup script
            File cleanupScript = new File(getScriptPath(dbOp.getContext().getVendor(), "cleanup_test_data.sql"));
            try {
                dbOp.executeScript(cleanupScript);
            } catch (Exception e) {
                // Log cleanup errors but don't fail the test
                System.err.println("Cleanup failed: " + e.getMessage());
            }
            connection.close();
        }
    }
    
    static Stream<Arguments> vendorProvider() {
        return Stream.of(
            Arguments.of(new OracleVendor(new ConfigHolderAdapter())),
            Arguments.of(new PostgreSqlVendor(new ConfigHolderAdapter()))
        );
    }
    
    @ParameterizedTest
    @MethodSource("vendorProvider")
    @DisplayName("Should create and call function successfully")
    void testCreateAndCallFunction(DatabaseVendor vendor) throws Exception {
        // Arrange
        setupConnection(vendor);
        setupTestData(vendor);
        
        File scriptFile = new File(getScriptPath(vendor, "test_function.sql"));
        
        // Act
        int result = dbOp.executeScript(scriptFile);
        
        // Assert
        assertTrue(result > 0, "Should have executed at least one statement");
        
        // Verify function result
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT get_employee_info(1) FROM " + 
                 (vendor instanceof OracleVendor ? "dual" : "(VALUES(1))"))) {
            assertTrue(rs.next(), "Should have a result");
            assertEquals("John Doe", rs.getString(1), "Function should return correct employee name");
        }
    }
    
    @ParameterizedTest
    @MethodSource("vendorProvider")
    @DisplayName("Should create and call procedure successfully")
    void testCreateAndCallProcedure(DatabaseVendor vendor) throws Exception {
        // Arrange
        setupConnection(vendor);
        setupTestData(vendor);
        
        File scriptFile = new File(getScriptPath(vendor, "test_procedure.sql"));
        
        // Act
        int result = dbOp.executeScript(scriptFile);
        
        // Assert
        assertTrue(result > 0, "Should have executed at least one statement");
        
        // Verify salary update
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT salary FROM employees WHERE employee_id = 1")) {
            assertTrue(rs.next(), "Should have a result");
            assertEquals(5000.0, rs.getDouble(1), 0.01, "Salary should be updated to 5000");
        }
    }
    
    @ParameterizedTest
    @MethodSource("vendorProvider")
    @DisplayName("Should handle invalid SQL with appropriate error")
    void testInvalidSql(DatabaseVendor vendor) throws Exception {
        // Arrange
        setupConnection(vendor);
        setupTestData(vendor);
        
        File scriptFile = new File(getScriptPath(vendor, "invalid_sql.sql"));
        
        // Act & Assert
        DatabaseException exception = assertThrows(DatabaseException.class, 
            () -> dbOp.executeScript(scriptFile),
            "Should throw DatabaseException for invalid SQL");
        
        // Assert specific error details based on vendor
        if (vendor instanceof OracleVendor) {
            assertTrue(exception.getMessage().contains("ORA-00942") || 
                      exception.getMessage().contains("ORA-06550"),
                "Should contain Oracle-specific error code");
        } else if (vendor instanceof PostgreSqlVendor) {
            assertTrue(exception.getMessage().contains("42P01") || 
                      exception.getMessage().contains("42703"),
                "Should contain PostgreSQL-specific error code");
        }
    }
    
    private void setupConnection(DatabaseVendor vendor) throws SQLException {
        dbOp = new UnifiedDatabaseOperationBuilder()
            .vendor(vendor)
            .configService(configService)
            .build();
        connection = dbOp.getContext().getConnection();
    }
    
    private void setupTestData(DatabaseVendor vendor) throws SQLException, IOException {
        File setupScript = new File(getScriptPath(vendor, "setup_test_data.sql"));
        dbOp.executeScript(setupScript);
    }
    
    private String getScriptPath(DatabaseVendor vendor, String scriptName) {
        String vendorDir = vendor instanceof OracleVendor ? "oracle" : "postgresql";
        return "src/test/resources/sql/" + vendorDir + "/" + scriptName;
    }
} 
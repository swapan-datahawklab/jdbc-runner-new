package com.example.shelldemo.spi;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DatabaseVendorTest {

    @Test
    void testPostgresValidationQuery() {
        DatabaseVendor vendor = new PostgreSqlVendor();
        assertEquals("SELECT 1", vendor.getValidationQuery());
    }

    @Test
    void testPostgresExplainPlanSql() {
        DatabaseVendor vendor = new PostgreSqlVendor();
        String sql = "SELECT * FROM employees";
        assertEquals("EXPLAIN (ANALYZE false, COSTS true, FORMAT TEXT) " + sql, vendor.getExplainPlanSql(sql));
    }

    @Test
    void testPostgresPLSQLDetection() {
        DatabaseVendor vendor = new PostgreSqlVendor();
        assertTrue(vendor.isPLSQL("DO $$ BEGIN RAISE NOTICE 'Hello'; END $$;"));
        assertFalse(vendor.isPLSQL("SELECT * FROM employees"));
    }
} 
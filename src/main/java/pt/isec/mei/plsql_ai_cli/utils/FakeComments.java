package pt.isec.mei.plsql_ai_cli.utils;

/**
 * Pool de comentários SQL fictícios para criar versões "dirty" de procedures.
 * Baseado em padrões reais de Informix SPL e PL/SQL legado.
 */
public class FakeComments {

    public static final String[] FAKE_COMMENTS = {
            // Single-line revision-style comments (-- format)
            "-- 01 JDoe 2015/03/15 Update processing logic for batch operations",
            "-- 02 MSilva 2016/07/22 Fixed calculation error in price determination",
            "-- 03 ITabc 2017/01/10 Add support for multiple currency handling",
            "-- 04 PTech 2017/05/18 Refactor validation routines for performance",
            "-- 05 RJones 2018/02/20 Implement new business rule for credit limits",
            "-- 06 AMartins 2018/09/14 Correct timestamp format in audit records",
            "-- 07 KSmith 2019/04/05 Enhanced error handling for null values",
            "-- 08 LFerreira 2019/11/28 Migration to new database schema version",
            "-- 09 TBrown 2020/06/17 Optimize query performance with indexes",
            "-- 10 CRodrigues 2020/10/30 Add logging for debugging purposes",
            "-- 11 NWilson 2021/01/22 Update documentation for API changes",
            "-- 12 PSantos 2021/08/09 Fix rounding issues in financial calculations",
            "-- 13 DMiller 2022/03/16 Implement caching mechanism for frequently used data",
            "-- 14 FOliveira 2022/07/25 Adjust timeout settings for external service calls",
            "-- 15 GAnderson 2023/02/11 Add validation for mandatory field requirements",

            // Inline comments explaining parameters or logic
            "-- Check if customer exists in database",
            "-- Calculate net value after discounts",
            "-- Update inventory levels",
            "-- Verify authorization credentials",
            "-- Process payment transaction",
            "-- Validate input parameters",
            "-- Initialize default values",
            "-- Commit transaction to database",
            "-- Rollback on error condition",
            "-- Set status flag to active",
            "-- Generate unique identifier",
            "-- Retrieve customer information",
            "-- Apply business rules validation",
            "-- Convert currency amounts",
            "-- Update last modified timestamp",

            // Block comment lines (without braces, for replacement in block comments)
            "Programmer: Software Development Team",
            "Revisions: Multiple updates for system enhancement",
            "External Procedures: Standard validation routines",
            "System Cmd: Execute batch processing",
            "Tables: customer_master, order_details, inventory_items",
            "Propose: Streamline order processing workflow",
            "Usage: call standard_procedure(arguments) returning status",
            "DATE: 2023/06/15",
            "NAME: generic_procedure.sql",

            // More complex single-line comments
            "-- 16 HCoста 2023/05/19 Integration with external payment gateway",
            "-- 17 JLima 2023/08/07 Security update for data encryption",
            "-- 18 MAlves 2023/11/23 Performance tuning for large dataset processing",
            "-- 19 RPereira 2024/01/14 Add support for new product categories",
            "-- 20 SCarvalho 2024/04/02 Implement audit trail functionality",
            "-- 21 TMoreira 2024/06/28 Update compliance with regulatory requirements",
            "-- 22 VNunes 2024/09/05 Fix data synchronization issues",
            "-- 23 XDias 2024/11/12 Enhance reporting capabilities",
            "-- Error handling for network timeout conditions",
            "-- Store temporary results in cache"
    };


    public static String getCommentAt(int index) {
        if (index >= 0 && index < FAKE_COMMENTS.length) {
            return FAKE_COMMENTS[index];
        }
        return FAKE_COMMENTS[0];
    }

    public static int getPoolSize() {
        return FAKE_COMMENTS.length;
    }
}
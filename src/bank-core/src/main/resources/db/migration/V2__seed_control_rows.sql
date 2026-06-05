-- =============================================================================
-- V2__seed_control_rows.sql
-- -----------------------------------------------------------------------------
-- Second Flyway versioned migration for the CBSA `bank-core` module.
--
-- Seeds the two control-row counters (one row per control table) for the single
-- bank sort code 987654 at a baseline of ZERO. These rows are subsequently
-- read and incremented under a PESSIMISTIC_WRITE row lock by IdentityService to
-- allocate gap-free account and customer numbers (AAP sec. 0.6; ADR-003).
-- Because the counter is consumed inside the same @Transactional boundary as
-- the insert it feeds, a rolled-back transaction restores the counter
-- automatically; that is precisely why allocation is driven by these counter
-- rows rather than by a database-assigned value, which could not be rolled back.
--
-- Source lineage (REFERENCE inputs -- read as spec, never modified):
--   * src/base/cobol_copy/SORTCODE.cpy line 7:
--         77 SORTCODE PIC 9(6) VALUE 987654.
--     -> the one and only bank sort code is 987654, supplied as the 6-character
--        string '987654' to match the control tables' sort_code CHAR(6) key.
--   * src/base/cobol_src/BANKDATA.cbl lines 464-466 set the counters to zero
--     before it populates any customer or account rows:
--         MOVE ZERO TO LAST-CUSTOMER-NUMBER NUMBER-OF-CUSTOMERS
--         MOVE ZERO TO LAST-ACCOUNT-NUMBER  NUMBER-OF-ACCOUNTS
--     -> the relational baseline is all four counters at 0.
--
-- The later VSAM CUSTOMER-CONTROL sentinel record in BANKDATA.cbl (a file-level
-- artifact, not a relational control row) is deliberately not reproduced; the
-- relational seed uses sort code 987654 with baseline-zero counters only.
--
-- Column and table names match V1__create_core_tables.sql verbatim
-- (all lower-case, unquoted):
--     account_control  (sort_code, number_of_accounts,  last_account_number)
--     customer_control (sort_code, number_of_customers, last_customer_number)
--
-- INSERT of control rows ONLY -- no identity/sequence generator (ADR-003).
-- The tables themselves are created by V1. Flyway records this version in its
-- schema-history table and applies it exactly once per database.
-- =============================================================================

-- account_control: one control row for sort code 987654, counters at zero.
INSERT INTO account_control (sort_code, number_of_accounts, last_account_number)
VALUES ('987654', 0, 0);

-- customer_control: one control row for sort code 987654, counters at zero.
INSERT INTO customer_control (sort_code, number_of_customers, last_customer_number)
VALUES ('987654', 0, 0);

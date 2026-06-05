-- =============================================================================
-- V1__create_core_tables.sql
-- -----------------------------------------------------------------------------
-- First Flyway versioned migration for the CBSA `bank-core` module.
--
-- Creates the five core tables that replace the legacy z/OS persistence
-- (VSAM KSDS + Db2). The schema is derived structurally from the COBOL RECORD
-- copybooks, which are the authoritative behavioural/structural specification:
--
--     ACCOUNT.cpy   -> account
--     CUSTOMER.cpy  -> customer
--     PROCTRAN.cpy  -> processed_transaction
--     ACCTCTRL.cpy  -> account_control
--     CUSTCTRL.cpy  -> customer_control
--
-- This file is the AUTHORITATIVE schema. At application startup Flyway applies
-- V1 then V2 (V2 seeds the control rows), after which Hibernate runs in
-- `ddl-auto: validate` mode and verifies that every JPA @Entity/@Column mapping
-- under com.ibm.cics.cip.bank.core.entity.** matches this schema EXACTLY
-- (table name, column name, type/length, nullability). Any mismatch makes the
-- application fail fast at startup; the resolution is always to reconcile the
-- DDL and the entities (both derive from the same copybooks + AAP rules) and
-- NEVER to relax `validate`.
--
-- Cross-cutting typing & constraint rules honoured here (AAP sec. 0.6 / 0.7;
-- ADR-003 / ADR-005 / ADR-006):
--
--   * Money / amounts  COBOL S9(10)V99 -> NUMERIC(12,2)
--   * Interest rate    COBOL 9(4)V99   -> NUMERIC(6,2)
--   * Overdraft limit  COBOL 9(8)      -> INTEGER  (whole pounds, no decimals)
--   * Credit score     COBOL 999       -> SMALLINT
--   * Control counters COBOL 9(8)/9(10)-> BIGINT
--     These mirror the Java BigDecimal scale-2 / RoundingMode.HALF_UP rule.
--     (The legacy Db2 DDL copybooks ACCDB2/PROCDB2/CONTDB2 under-provision
--      these as DECIMAL(10,2)/DECIMAL(4,2) and store dates as CHAR; they are
--      REFERENCE-ONLY and are deliberately NOT used as the schema source.)
--   * Fixed-width identifiers are CHAR(n) so COBOL display-numeric leading
--     zeros are preserved verbatim (sort_code, account_number, customer_number,
--     transaction_number, ref). Formatting of dates back to DD/MM/YYYY (account
--     /customer) vs YYYY/MM/DD (proctran) is a DTO/wire-layer concern, NOT a
--     column-type concern -- all date fields are plain DATE here.
--   * Identity (account/customer numbers) is allocated transactionally by
--     reading + incrementing the *_control counter rows under a pessimistic
--     row lock, never by a database auto-increment / identity / sequence /
--     serial generator (ADR-003): a database-generated value cannot be rolled
--     back, but a consumed counter MUST roll back with its enclosing
--     transaction. Consequently there are deliberately NO such generators here.
--   * Eye-catcher marker bytes (ACCT/CUST/PRTR/CTRL) are dropped -- relational
--     typing supersedes them. The PROCTRAN eye-catcher is a special case: it is
--     redefined in the copybook as a logical-delete flag (88 ... VALUE X'FF'),
--     so it is materialised as a dedicated `deleted BOOLEAN` column.
--     processed_transaction is APPEND-ONLY / logical-delete only and is NEVER
--     physically deleted (ADR-006).
--   * The two account balances (available_balance, actual_balance) are
--     INDEPENDENT (cleared vs. pending funds) and are never collapsed.
--
-- All identifiers are declared UNQUOTED and lower-case (including the
-- reserved-word columns `date` and `time`, which PostgreSQL accepts as a
-- non-reserved ColId) so they fold to lower-case and Hibernate's default
-- unquoted/lower-case identifier matching succeeds.
--
-- This migration contains DDL ONLY. Seed data (initial control-row counters
-- for sort code 987654) lives in the sibling V2__seed_control_rows.sql.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- customer  (from CUSTOMER.cpy) -- composite PK (sort_code, customer_number)
-- -----------------------------------------------------------------------------
--   CUSTOMER-SORTCODE       9(6)   -> sort_code        CHAR(6)
--   CUSTOMER-NUMBER         9(10)  -> customer_number  CHAR(10)
--   CUSTOMER-NAME           X(60)  -> name             VARCHAR(60)
--   CUSTOMER-ADDRESS        X(160) -> address          VARCHAR(160)
--   CUSTOMER-DATE-OF-BIRTH  9(8)   -> date_of_birth    DATE
--   CUSTOMER-CREDIT-SCORE   999    -> credit_score     SMALLINT (0..999)
--   CUSTOMER-CS-REVIEW-DATE 9(8)   -> cs_review_date   DATE
--   CUSTOMER-EYECATCHER 'CUST'     -> (dropped)
-- The copybook keeps name and address each as a single fixed field (their
-- sub-fields are commented out in CUSTOMER.cpy), so they are modelled as one
-- column each -- no separate title/town/surname columns are invented.
-- -----------------------------------------------------------------------------
CREATE TABLE customer (
    sort_code        CHAR(6)        NOT NULL,
    customer_number  CHAR(10)       NOT NULL,
    name             VARCHAR(60)    NOT NULL,
    address          VARCHAR(160)   NOT NULL,
    date_of_birth    DATE           NOT NULL,
    credit_score     SMALLINT       NOT NULL DEFAULT 0,
    cs_review_date   DATE,
    CONSTRAINT pk_customer PRIMARY KEY (sort_code, customer_number),
    CONSTRAINT ck_customer_credit_score CHECK (credit_score BETWEEN 0 AND 999)
);


-- -----------------------------------------------------------------------------
-- account  (from ACCOUNT.cpy) -- composite PK (sort_code, account_number)
-- -----------------------------------------------------------------------------
--   ACCOUNT-SORT-CODE         9(6)       -> sort_code           CHAR(6)
--   ACCOUNT-NUMBER            9(8)       -> account_number      CHAR(8)
--   ACCOUNT-CUST-NO           9(10)      -> customer_number     CHAR(10)  (link)
--   ACCOUNT-TYPE              X(8)       -> account_type        VARCHAR(8)
--   ACCOUNT-INTEREST-RATE     9(4)V99    -> interest_rate       NUMERIC(6,2)
--   ACCOUNT-OPENED            9(8)       -> opened              DATE
--   ACCOUNT-OVERDRAFT-LIMIT   9(8)       -> overdraft_limit     INTEGER (no dp)
--   ACCOUNT-LAST-STMT-DATE    9(8)       -> last_statement_date DATE
--   ACCOUNT-NEXT-STMT-DATE    9(8)       -> next_statement_date DATE
--   ACCOUNT-AVAILABLE-BALANCE S9(10)V99  -> available_balance   NUMERIC(12,2)
--   ACCOUNT-ACTUAL-BALANCE    S9(10)V99  -> actual_balance      NUMERIC(12,2)
--   ACCOUNT-EYE-CATCHER 'ACCT' X(4)      -> (dropped)
-- available_balance and actual_balance are INDEPENDENT and must never be
-- collapsed into one column. No FK to customer is mandated; the link is
-- enforced in the service layer, but customer_number is indexed (below).
-- -----------------------------------------------------------------------------
CREATE TABLE account (
    sort_code            CHAR(6)        NOT NULL,
    account_number       CHAR(8)        NOT NULL,
    customer_number      CHAR(10)       NOT NULL,
    account_type         VARCHAR(8)     NOT NULL,
    interest_rate        NUMERIC(6,2)   NOT NULL,
    opened               DATE,
    overdraft_limit      INTEGER        NOT NULL DEFAULT 0,
    last_statement_date  DATE,
    next_statement_date  DATE,
    available_balance    NUMERIC(12,2)  NOT NULL DEFAULT 0,
    actual_balance       NUMERIC(12,2)  NOT NULL DEFAULT 0,
    CONSTRAINT pk_account PRIMARY KEY (sort_code, account_number),
    CONSTRAINT ck_account_type CHECK (account_type IN ('ISA','MORTGAGE','SAVING','CURRENT','LOAN'))
);

-- Supports AccountRepository find-by-customer + ordered fetch of a customer's
-- accounts (INQACCCU / a customer's account list).
CREATE INDEX idx_account_customer_number ON account (customer_number);


-- -----------------------------------------------------------------------------
-- processed_transaction  (from PROCTRAN.cpy)
--                        -- composite PK (sort_code, transaction_number)
-- -----------------------------------------------------------------------------
--   PROC-TRAN-SORT-CODE  9(6)      -> sort_code          CHAR(6)
--   PROC-TRAN-NUMBER     9(8)      -> transaction_number CHAR(8)
--   PROC-TRAN-DATE       9(8)      -> date               DATE  (reserved word;
--                                     kept unquoted lower-case; the entity maps
--                                     it via @Column name date; YYYY/MM/DD wire)
--   PROC-TRAN-TIME       9(6)      -> time               TIME  (reserved word;
--                                     kept unquoted lower-case; HHMMSS on wire)
--   PROC-TRAN-REF        9(12)     -> ref                CHAR(12)
--   PROC-TRAN-TYPE       X(3)      -> type_code          CHAR(3)
--   PROC-TRAN-DESC       X(40)     -> description        VARCHAR(40) (the
--                                     copybook REDEFINES this 40-byte area many
--                                     ways; modelled as one free-text column)
--   PROC-TRAN-AMOUNT     S9(10)V99 -> amount             NUMERIC(12,2)
--   PROC-TRAN-LOGICAL-DELETE-FLAG (redefines the 'PRTR' eye-catcher; 88 X'FF')
--                                  -> deleted            BOOLEAN (soft-delete)
-- Append-only / logical-delete only -- rows are NEVER physically removed
-- (ADR-006); the partial index below speeds up active-row queries.
-- -----------------------------------------------------------------------------
CREATE TABLE processed_transaction (
    sort_code           CHAR(6)        NOT NULL,
    transaction_number  CHAR(8)        NOT NULL,
    date                DATE,
    time                TIME,
    ref                 CHAR(12),
    type_code           CHAR(3)        NOT NULL,
    description         VARCHAR(40),
    amount              NUMERIC(12,2)  NOT NULL DEFAULT 0,
    deleted             BOOLEAN        NOT NULL DEFAULT false,
    CONSTRAINT pk_processed_transaction PRIMARY KEY (sort_code, transaction_number),
    -- The exact, complete set of 18 PROCTRAN type codes from PROCTRAN.cpy
    -- (lines 30-47). 'OCS' (create-SODD) is included; no more, no fewer.
    CONSTRAINT ck_proctran_type_code CHECK (type_code IN
        ('CHA','CHF','CHI','CHO','CRE','DEB','ICA','ICC','IDA','IDC',
         'OCA','OCC','ODA','ODC','OCS','PCR','PDR','TFR'))
);

-- Partial index supporting ProcessedTransactionRepository active-only queries
-- (the logical-delete model: WHERE deleted = false).
CREATE INDEX idx_proctran_active ON processed_transaction (sort_code, transaction_number) WHERE deleted = false;


-- -----------------------------------------------------------------------------
-- account_control  (from ACCTCTRL.cpy) -- single-column PK (sort_code)
-- -----------------------------------------------------------------------------
--   ACCOUNT-CONTROL-SORT-CODE 9(6) -> sort_code           CHAR(6)
--   NUMBER-OF-ACCOUNTS        9(8) -> number_of_accounts  BIGINT
--   LAST-ACCOUNT-NUMBER       9(8) -> last_account_number BIGINT
--   eye-catcher 'CTRL', FILLERs, success/fail flags, ACCOUNT-CONTROL-NUMBER
--                                  -> (dropped -- CICS/VSAM artifacts)
-- last_account_number is read+incremented under PESSIMISTIC_WRITE by
-- IdentityService for gap-free, roll-back-able account-number allocation. The
-- single-column PK index covers that pessimistic read -- no extra index needed.
-- -----------------------------------------------------------------------------
CREATE TABLE account_control (
    sort_code            CHAR(6)   NOT NULL,
    number_of_accounts   BIGINT    NOT NULL DEFAULT 0,
    last_account_number  BIGINT    NOT NULL DEFAULT 0,
    CONSTRAINT pk_account_control PRIMARY KEY (sort_code)
);


-- -----------------------------------------------------------------------------
-- customer_control  (from CUSTCTRL.cpy) -- single-column PK (sort_code)
-- -----------------------------------------------------------------------------
--   CUSTOMER-CONTROL-SORTCODE 9(6)  -> sort_code            CHAR(6)
--   NUMBER-OF-CUSTOMERS       9(10) -> number_of_customers  BIGINT
--   LAST-CUSTOMER-NUMBER      9(10) -> last_customer_number BIGINT
--   eye-catcher 'CTRL', FILLERs, success/fail flags, CUSTOMER-CONTROL-NUMBER
--                                   -> (dropped -- CICS/VSAM artifacts)
-- last_customer_number is read+incremented under PESSIMISTIC_WRITE by
-- IdentityService for gap-free, roll-back-able customer-number allocation. The
-- single-column PK index covers that pessimistic read -- no extra index needed.
-- -----------------------------------------------------------------------------
CREATE TABLE customer_control (
    sort_code             CHAR(6)   NOT NULL,
    number_of_customers   BIGINT    NOT NULL DEFAULT 0,
    last_customer_number  BIGINT    NOT NULL DEFAULT 0,
    CONSTRAINT pk_customer_control PRIMARY KEY (sort_code)
);

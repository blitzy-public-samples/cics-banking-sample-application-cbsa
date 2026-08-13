# CBSA Mainframe Indicator and Critical-Data-Element Inventory and Data-Lineage Cross-Reference

## proctran-type-code — PROCTRAN 3-Character Transaction-Type Code (`PIC X(3)`) [src/base/cobol_copy/PROCTRAN.cpy:L29]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| PROCTRAN.cpy copybook declaration; the 18 transaction-type condition names declared at L30-L47 are PROC-TY-CHEQUE-ACKNOWLEDGED, PROC-TY-CHEQUE-FAILURE, PROC-TY-CHEQUE-PAID-IN, PROC-TY-CHEQUE-PAID-OUT, PROC-TY-CREDIT, PROC-TY-DEBIT, PROC-TY-WEB-CREATE-ACCOUNT, PROC-TY-WEB-CREATE-CUSTOMER, PROC-TY-WEB-DELETE-ACCOUNT, PROC-TY-WEB-DELETE-CUSTOMER, PROC-TY-BRANCH-CREATE-ACCOUNT, PROC-TY-BRANCH-CREATE-CUSTOMER, PROC-TY-BRANCH-DELETE-ACCOUNT, PROC-TY-BRANCH-DELETE-CUSTOMER, PROC-TY-CREATE-SODD, PROC-TY-PAYMENT-CREDIT, PROC-TY-PAYMENT-DEBIT, PROC-TY-TRANSFER | create | src/base/cobol_copy/PROCTRAN.cpy | L29 |
| PROCDB2.cpy alternate representation as DB2 column PROCTRAN_TYPE CHAR(3) | create | src/base/cobol_copy/PROCDB2.cpy | L15 |
| CREACC writes 'ICA' web-channel / 'OCA' branch-channel create-account records | write | src/base/cobol_src/CREACC.cbl | WRITE-PROCTRAN-DB2 SECTION / WPD010 |
| CRECUST writes 'ICC' web-channel / 'OCC' branch-channel create-customer records | write | src/base/cobol_src/CRECUST.cbl | WRITE-PROCTRAN-DB2 SECTION / WPD010 |
| DBCRFUN writes 'DEB', 'CRE', 'PDR', 'PCR' | write | src/base/cobol_src/DBCRFUN.cbl | WRITE-TO-PROCTRAN-DB2 SECTION / WTPD010 |
| DELACC writes 'IDA' web-channel / 'ODA' branch-channel delete-account records | write | src/base/cobol_src/DELACC.cbl | WRITE-PROCTRAN-DB2 SECTION / WPD010 |
| DELCUS writes 'IDC' web-channel / 'ODC' branch-channel delete-customer records | write | src/base/cobol_src/DELCUS.cbl | WRITE-PROCTRAN-CUST-DB2 SECTION / WPCD010 |
| XFRFUN writes 'TFR' | write | src/base/cobol_src/XFRFUN.cbl | WRITE-TO-PROCTRAN-DB2 SECTION / WTPD010 |
| CREACC VSAM-era PROCTRAN write path | write | src/base/cobol_src/CREACC.cbl | WRITE-PROCTRAN SECTION / WP010 |
| CRECUST VSAM-era PROCTRAN write path | write | src/base/cobol_src/CRECUST.cbl | WRITE-PROCTRAN SECTION / WP010 |
| DBCRFUN VSAM-era PROCTRAN write path | write | src/base/cobol_src/DBCRFUN.cbl | WRITE-TO-PROCTRAN SECTION / WTP010 |
| DELACC VSAM-era PROCTRAN write path | write | src/base/cobol_src/DELACC.cbl | WRITE-PROCTRAN SECTION / WP010 |
| DELCUS VSAM-era PROCTRAN write path | write | src/base/cobol_src/DELCUS.cbl | WRITE-PROCTRAN-CUST SECTION / WPC010 |
| XFRFUN VSAM-era PROCTRAN write path | write | src/base/cobol_src/XFRFUN.cbl | WRITE-TO-PROCTRAN SECTION / WTP010 |
| CREACC | consume | src/base/cobol_src/CREACC.cbl | WORKING-STORAGE SECTION / COPY PROCTRAN L122 |
| CRECUST | consume | src/base/cobol_src/CRECUST.cbl | WORKING-STORAGE SECTION / COPY PROCTRAN L84 |
| DBCRFUN | consume | src/base/cobol_src/DBCRFUN.cbl | LOCAL-STORAGE SECTION / COPY PROCTRAN L150 |
| DELACC | consume | src/base/cobol_src/DELACC.cbl | WORKING-STORAGE SECTION / COPY PROCTRAN L108 |
| DELCUS | consume | src/base/cobol_src/DELCUS.cbl | WORKING-STORAGE SECTION / COPY PROCTRAN L88 |
| XFRFUN | consume | src/base/cobol_src/XFRFUN.cbl | LOCAL-STORAGE SECTION / COPY PROCTRAN L175 |
| CREACC DB2 declaration | consume | src/base/cobol_src/CREACC.cbl | WORKING-STORAGE SECTION / EXEC SQL INCLUDE PROCDB2 L94 |
| CRECUST DB2 declaration | consume | src/base/cobol_src/CRECUST.cbl | WORKING-STORAGE SECTION / EXEC SQL INCLUDE PROCDB2 L63 |
| DBCRFUN DB2 declaration | consume | src/base/cobol_src/DBCRFUN.cbl | WORKING-STORAGE SECTION / EXEC SQL INCLUDE PROCDB2 L77 |
| DELACC DB2 declaration | consume | src/base/cobol_src/DELACC.cbl | WORKING-STORAGE SECTION / EXEC SQL INCLUDE PROCDB2 L71 |
| DELCUS DB2 declaration | consume | src/base/cobol_src/DELCUS.cbl | WORKING-STORAGE SECTION / EXEC SQL INCLUDE PROCDB2 L55 |
| XFRFUN DB2 declaration | consume | src/base/cobol_src/XFRFUN.cbl | WORKING-STORAGE SECTION / EXEC SQL INCLUDE PROCDB2 L82 |
| IBM Record Generator commarea class PROCTRAN, PROC_TRAN_TYPE 3-byte StringField | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/PROCTRAN.java | L130 |
| IBM Record Generator commarea class PROCTRAN, all 18 transaction-type constants | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/PROCTRAN.java | L134-L202 |
| IBM Record Generator commarea class PROCTRAN, type accessors and equality guard | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/PROCTRAN.java | L603-L612 |
| webui JAX-RS GET /webui-1.0/banking/processedTransaction collection | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/ProcessedTransactionResource.java | L94 |
| webui JAX-RS POST /webui-1.0/banking/processedTransaction/debitCreditAccount | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/ProcessedTransactionResource.java | L257-L259 |
| webui JAX-RS POST /webui-1.0/banking/processedTransaction/transferLocal | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/ProcessedTransactionResource.java | L306-L309 |
| webui JAX-RS POST /webui-1.0/banking/processedTransaction/deleteCustomer | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/ProcessedTransactionResource.java | L339-L342 |
| webui JAX-RS POST /webui-1.0/banking/processedTransaction/createCustomer | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/ProcessedTransactionResource.java | L375-L378 |
| webui JAX-RS POST /webui-1.0/banking/processedTransaction/deleteAccount | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/ProcessedTransactionResource.java | L412-L415 |
| webui JAX-RS POST /webui-1.0/banking/processedTransaction/createAccount | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/ProcessedTransactionResource.java | L449-L452 |
| CBSA RESTful Interface Reference documents the collection GET only; the six POST sub-resources are absent from the prose reference | consume | etc/usage/carbonReactUI/doc/CBSA_RESTful_Interface_Reference.md | #### Processed Transactions / ##### GET (List) |

## proctran-logical-delete-flag — PROCTRAN Logical-Delete Flag Overlaying Byte 1 of the Eye-Catcher (`PIC X`) [src/base/cobol_copy/PROCTRAN.cpy:L12]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| PROCTRAN.cpy copybook declaration PROC-TRAN-LOGICAL-DELETE-FLAG | create | src/base/cobol_copy/PROCTRAN.cpy | L12 |
| PROCTRAN.cpy copybook declaration PROC-TRAN-LOGICAL-DELETE-AREA REDEFINES the eye-catcher at L8 | create | src/base/cobol_copy/PROCTRAN.cpy | L10-L11 |
| PROCTRAN.cpy copybook declaration 88 PROC-TRAN-LOGICALLY-DELETED VALUE X'FF' entirely on this single line; L14 is a FILLER | create | src/base/cobol_copy/PROCTRAN.cpy | L13 |
| (none in repository) | write | src/base/cobol_copy/PROCTRAN.cpy | no COBOL program references LOGICAL-DELETE; exhaustive repo grep over all 29 programs |
| IBM Record Generator commarea class PROCTRAN, delete-flag setter | write | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/PROCTRAN.java | L475 |
| (none in repository) | read | src/base/cobol_copy/PROCTRAN.cpy | no COBOL program references LOGICAL-DELETE; exhaustive repo grep over all 29 programs |
| IBM Record Generator commarea class PROCTRAN, delete-flag getter | read | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/PROCTRAN.java | L471 |
| IBM Record Generator commarea class PROCTRAN, PROC_TRAN_LOGICAL_DELETE_FLAG 1-byte StringField overlaying byte 1 of the eye-catcher | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/PROCTRAN.java | L52 |

## newaccno-function-flag — NEWACCNO Account-Number Allocator Function Flag (G/R/C) (`PIC X`) [src/base/cobol_copy/NEWACCNO.cpy:L7]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| NEWACCNO.cpy copybook declaration NEWACCNO-FUNCTION; the G, R and C condition names declared at L8-L10 are NEWACCNO-FUNCTION-GETNEW, NEWACCNO-FUNCTION-ROLLBACK, NEWACCNO-FUNCTION-CURRENT | create | src/base/cobol_copy/NEWACCNO.cpy | L7 |
| CREACC resolved in-repository origin: serialises account-number allocation with EXEC CICS ENQ | create | src/base/cobol_src/CREACC.cbl | ENQ-NAMED-COUNTER SECTION / ENC010 |
| CREACC resolved in-repository origin: releases the allocation lock with EXEC CICS DEQ | create | src/base/cobol_src/CREACC.cbl | DEQ-NAMED-COUNTER SECTION / DNC010 |
| (none in repository) | write | src/base/cobol_src/ | NEWACCNO.cbl absent from src/base/cobol_src/; the copybook is a COMMAREA contract for an allocator delivered outside this repository |
| CREACC increments the ACCTCTRL control row to allocate the next account number | write | src/base/cobol_src/CREACC.cbl | FIND-NEXT-ACCOUNT SECTION / FNA010 |
| IBM Record Generator commarea class NewAccountNumber, 11-byte structure length | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/NewAccountNumber.java | L19 |
| IBM Record Generator commarea class NewAccountNumber, NEWACCNO_FUNCTION 1-byte StringField | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/NewAccountNumber.java | L23 |
| IBM Record Generator commarea class NewAccountNumber, G/R/C function constants | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/NewAccountNumber.java | L27-L35 |
| IBM Record Generator commarea class NewAccountNumber, ACCOUNT_NUMBER ExternalDecimalAsIntField | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/NewAccountNumber.java | L39 |

## newcusno-function-flag — NEWCUSNO Customer-Number Allocator Function Flag (G/R/C) (`PIC X`) [src/base/cobol_copy/NEWCUSNO.cpy:L7]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| NEWCUSNO.cpy copybook declaration NEWCUSNO-FUNCTION; the G, R and C condition names declared at L8-L10 are NEWCUSNO-FUNCTION-GETNEW, NEWCUSNO-FUNCTION-ROLLBACK, NEWCUSNO-FUNCTION-CURRENT | create | src/base/cobol_copy/NEWCUSNO.cpy | L7 |
| CRECUST resolved in-repository origin: serialises customer-number allocation with EXEC CICS ENQ | create | src/base/cobol_src/CRECUST.cbl | ENQ-NAMED-COUNTER SECTION / ENC010 |
| CRECUST resolved in-repository origin: releases the allocation lock with EXEC CICS DEQ | create | src/base/cobol_src/CRECUST.cbl | DEQ-NAMED-COUNTER SECTION / DNC010 |
| (none in repository) | write | src/base/cobol_src/ | NEWCUSNO.cbl absent from src/base/cobol_src/; the copybook is a COMMAREA contract for an allocator delivered outside this repository |
| CRECUST updates the named-counter server value | write | src/base/cobol_src/CRECUST.cbl | UPD-NCS SECTION / UN010 |
| IBM Record Generator commarea class NewCustomerNumber, 13-byte structure length | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/NewCustomerNumber.java | L19 |
| IBM Record Generator commarea class NewCustomerNumber, NEWCUSNO_FUNCTION 1-byte StringField | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/NewCustomerNumber.java | L23 |
| IBM Record Generator commarea class NewCustomerNumber, G/R/C function constants | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/NewCustomerNumber.java | L27-L35 |
| IBM Record Generator commarea class NewCustomerNumber, CUSTOMER_NUMBER ExternalDecimalAsLongField | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/NewCustomerNumber.java | L39 |

## crecust-success-flag — CRECUST COMMAREA Success Flag COMM-SUCCESS (`PIC X`) [src/base/cobol_copy/CRECUST.cpy:L24]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| CRECUST.cpy copybook declaration COMM-SUCCESS | create | src/base/cobol_copy/CRECUST.cpy | L24 |
| CRECUST | write | src/base/cobol_src/CRECUST.cbl | PREMIERE SECTION / P010 |
| CRECUST | write | src/base/cobol_src/CRECUST.cbl | ENQ-NAMED-COUNTER SECTION / ENC010 |
| CRECUST | write | src/base/cobol_src/CRECUST.cbl | DEQ-NAMED-COUNTER SECTION / DNC010 |
| CRECUST | write | src/base/cobol_src/CRECUST.cbl | CREDIT-CHECK SECTION / CC010 |
| CRECUST | write | src/base/cobol_src/CRECUST.cbl | WRITE-CUSTOMER-VSAM SECTION / WCV010 |
| CRECUST | write | src/base/cobol_src/CRECUST.cbl | GET-LAST-CUSTOMER-VSAM SECTION / GLCV010 |
| CRECUST | consume | src/base/cobol_src/CRECUST.cbl | LINKAGE SECTION / COPY CRECUST L353 |
| IBM Record Generator commarea class CRECUST, COMM_SUCCESS 1-byte StringField | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/CRECUST.java | L109 |
| z/OS Connect service CScustcre interface binding | consume | src/zosconnect_artefacts/services/CScustcre/service-interfaces/CRECUST.si | L6 |
| z/OS Connect API postCScustcre (POST /crecust/insert) | consume | src/zosconnect_artefacts/apis/crecust/api-docs/swagger.json | POST /crecust/insert |
| Customer Services Interface CrecustJson wire field CommSuccess | consume | src/Z-OS-Connect-Customer-Services-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/customerservices/jsonclasses/createcustomer/CrecustJson.java | L38-L39 |
| Customer Services Interface CrecustJson success accessors | consume | src/Z-OS-Connect-Customer-Services-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/customerservices/jsonclasses/createcustomer/CrecustJson.java | L145-L165 |
| Customer Services Interface WebController create-customer fail-code branch guard | consume | src/Z-OS-Connect-Customer-Services-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/customerservices/controllers/WebController.java | L567 |
| Spring Boot JSON wire-naming shim: convert() returns `input.substring(3)`, stripping the three-character comm prefix from every COBOL-derived field name; this single statement governs every wire-field name cited in this document | consume | src/Z-OS-Connect-Customer-Services-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/customerservices/JsonPropertyNamingStrategy.java | L48 |
| Spring Boot JSON wire-naming shim: convert() returns `input.substring(3)`, stripping the three-character comm prefix from every COBOL-derived field name; this single statement governs every wire-field name cited in this document | consume | src/Z-OS-Connect-Payment-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/paymentinterface/JsonPropertyNamingStrategy.java | L48 |

## crecust-fail-code — CRECUST COMMAREA Fail Code COMM-FAIL-CODE (`PIC X`) [src/base/cobol_copy/CRECUST.cpy:L25]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| CRECUST.cpy copybook declaration COMM-FAIL-CODE | create | src/base/cobol_copy/CRECUST.cpy | L25 |
| CRECUST | write | src/base/cobol_src/CRECUST.cbl | PREMIERE SECTION / P010 |
| CRECUST | write | src/base/cobol_src/CRECUST.cbl | ENQ-NAMED-COUNTER SECTION / ENC010 |
| CRECUST | write | src/base/cobol_src/CRECUST.cbl | DEQ-NAMED-COUNTER SECTION / DNC010 |
| CRECUST | write | src/base/cobol_src/CRECUST.cbl | CREDIT-CHECK SECTION / CC010 |
| CRECUST | write | src/base/cobol_src/CRECUST.cbl | WRITE-CUSTOMER-VSAM SECTION / WCV010 |
| CRECUST | write | src/base/cobol_src/CRECUST.cbl | GET-LAST-CUSTOMER-VSAM SECTION / GLCV010 |
| CRECUST | write | src/base/cobol_src/CRECUST.cbl | DATE-OF-BIRTH-CHECK SECTION / DOBC010 |
| CRECUST | consume | src/base/cobol_src/CRECUST.cbl | LINKAGE SECTION / COPY CRECUST L353 |
| IBM Record Generator commarea class CRECUST, COMM_FAIL_CODE 1-byte StringField | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/CRECUST.java | L113 |
| z/OS Connect service CScustcre interface binding | consume | src/zosconnect_artefacts/services/CScustcre/service-interfaces/CRECUST.si | L6 |
| z/OS Connect API postCScustcre (POST /crecust/insert) | consume | src/zosconnect_artefacts/apis/crecust/api-docs/swagger.json | POST /crecust/insert |
| Customer Services Interface CrecustJson wire field CommFailCode typed as String | consume | src/Z-OS-Connect-Customer-Services-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/customerservices/jsonclasses/createcustomer/CrecustJson.java | L41-L42 |
| Customer Services Interface WebController maps CRECUST fail code 'T' to a title-validation failure; fail-code semantics are scoped to CRECUST alone and no global fail-code table exists | consume | src/Z-OS-Connect-Customer-Services-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/customerservices/controllers/WebController.java | L569 |

## dbcrfun-success-flag — DBCRFUN Payment COMMAREA Success Flag COMM-SUCCESS (`PIC X`) [src/base/cobol_copy/PAYDBCR.cpy:L19]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| PAYDBCR.cpy copybook declaration COMM-SUCCESS | create | src/base/cobol_copy/PAYDBCR.cpy | L19 |
| DBCRFUN | write | src/base/cobol_src/DBCRFUN.cbl | PREMIERE SECTION / A010 |
| DBCRFUN | write | src/base/cobol_src/DBCRFUN.cbl | UPDATE-ACCOUNT-DB2 SECTION / UAD010 |
| DBCRFUN | write | src/base/cobol_src/DBCRFUN.cbl | WRITE-TO-PROCTRAN-DB2 SECTION / WTPD010 |
| DBCRFUN | write | src/base/cobol_src/DBCRFUN.cbl | ABEND-HANDLING SECTION / AH010 |
| DBCRFUN | consume | src/base/cobol_src/DBCRFUN.cbl | LINKAGE SECTION / COPY PAYDBCR L195 |
| z/OS Connect service Pay interface binding | consume | src/zosconnect_artefacts/services/Pay/service-interfaces/PAYDBCR.si | L6 |
| z/OS Connect API putPay (PUT /makepayment/dbcr) | consume | src/zosconnect_artefacts/apis/makepayment/api-docs/swagger.json | PUT /makepayment/dbcr |
| Payment Interface DbcrJson wire contract for the payment COMMAREA | consume | src/Z-OS-Connect-Payment-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/paymentinterface/jsonclasses/paymentinterface/DbcrJson.java | L11 |

## dbcrfun-fail-code — DBCRFUN Payment COMMAREA Fail Code COMM-FAIL-CODE (`PIC X`) [src/base/cobol_copy/PAYDBCR.cpy:L20]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| PAYDBCR.cpy copybook declaration COMM-FAIL-CODE | create | src/base/cobol_copy/PAYDBCR.cpy | L20 |
| DBCRFUN | write | src/base/cobol_src/DBCRFUN.cbl | PREMIERE SECTION / A010 |
| DBCRFUN | write | src/base/cobol_src/DBCRFUN.cbl | UPDATE-ACCOUNT-DB2 SECTION / UAD010 |
| DBCRFUN | write | src/base/cobol_src/DBCRFUN.cbl | WRITE-TO-PROCTRAN-DB2 SECTION / WTPD010 |
| DBCRFUN | write | src/base/cobol_src/DBCRFUN.cbl | ABEND-HANDLING SECTION / AH010 |
| DBCRFUN | consume | src/base/cobol_src/DBCRFUN.cbl | LINKAGE SECTION / COPY PAYDBCR L195 |
| z/OS Connect service Pay interface binding | consume | src/zosconnect_artefacts/services/Pay/service-interfaces/PAYDBCR.si | L6 |
| z/OS Connect API putPay (PUT /makepayment/dbcr) | consume | src/zosconnect_artefacts/apis/makepayment/api-docs/swagger.json | PUT /makepayment/dbcr |
| Payment Interface DbcrJson wire contract for the payment COMMAREA | consume | src/Z-OS-Connect-Payment-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/paymentinterface/jsonclasses/paymentinterface/DbcrJson.java | L11 |

## account-available-balance — ACCOUNT Available Balance (`PIC S9(10)V99`) [src/base/cobol_copy/ACCOUNT.cpy:L34]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| ACCOUNT.cpy copybook declaration ACCOUNT-AVAILABLE-BALANCE | create | src/base/cobol_copy/ACCOUNT.cpy | L34 |
| ACCDB2.cpy alternate representation as DB2 column ACCOUNT_AVAILABLE_BALANCE DECIMAL(10, 2) | create | src/base/cobol_copy/ACCDB2.cpy | L18 |
| PAYDBCR.cpy alternate representation as payment COMMAREA copy COMM-AV-BAL | create | src/base/cobol_copy/PAYDBCR.cpy | L10 |
| CREACC.cpy alternate representation as create-account COMMAREA copy COMM-AVAIL-BAL | create | src/base/cobol_copy/CREACC.cpy | L30 |
| INQACC.cpy alternate representation as account-enquiry COMMAREA copy INQACC-AVAIL-BAL | create | src/base/cobol_copy/INQACC.cpy | L30 |
| INQACCCU.cpy alternate representation as list-accounts COMMAREA array copy COMM-AVAIL-BAL | create | src/base/cobol_copy/INQACCCU.cpy | L37 |
| DELACC.cpy alternate representation as delete-account COMMAREA copy DELACC-AVAIL-BAL | create | src/base/cobol_copy/DELACC.cpy | L30 |
| UPDACC.cpy alternate representation as update-account COMMAREA copy COMM-AVAIL-BAL | create | src/base/cobol_copy/UPDACC.cpy | L29 |
| XFRFUN.cpy alternate representation as transfer COMMAREA from-leg copy COMM-FAVBAL | create | src/base/cobol_copy/XFRFUN.cpy | L12 |
| XFRFUN.cpy alternate representation as transfer COMMAREA to-leg copy COMM-TAVBAL | create | src/base/cobol_copy/XFRFUN.cpy | L14 |
| BANKDATA seeds initial account balances | create | src/base/cobol_src/BANKDATA.cbl | POPULATE-ACC SECTION / PA010 |
| CREACC establishes the opening balance on account creation | create | src/base/cobol_src/CREACC.cbl | WRITE-ACCOUNT-DB2 SECTION / WAD010 |
| DBCRFUN single-account debit/credit balance update | write | src/base/cobol_src/DBCRFUN.cbl | UPDATE-ACCOUNT-DB2 SECTION / UAD010 |
| XFRFUN transfer from-leg balance update | write | src/base/cobol_src/XFRFUN.cbl | UPDATE-ACCOUNT-DB2-FROM SECTION / UADF010 |
| XFRFUN transfer to-leg balance update | write | src/base/cobol_src/XFRFUN.cbl | UPDATE-ACCOUNT-DB2-TO SECTION / UADT010 |
| webui DB2 accessor Account dual-balance UPDATE statement | write | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/web/db2/Account.java | L381 |
| webui DB2 accessor Account defect: `stmt.setDouble(6, this.availableBalance);` binds money as a double | write | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/web/db2/Account.java | L389 |
| DBCRFUN reads HV-ACCOUNT-AVAIL-BAL as the overdraft gate before applying the movement | read | src/base/cobol_src/DBCRFUN.cbl | UPDATE-ACCOUNT-DB2 SECTION / UAD010 |
| INQACC | read | src/base/cobol_src/INQACC.cbl | FETCH-DATA SECTION / FD010 |
| INQACCCU | read | src/base/cobol_src/INQACCCU.cbl | FETCH-DATA SECTION / FD010 |
| DELACC captures the terminal balance for the PROCTRAN audit record | read | src/base/cobol_src/DELACC.cbl | READ-ACCOUNT-DB2 SECTION / RAD010 |
| UPDACC balances are read but excluded from the update allowlist | read | src/base/cobol_src/UPDACC.cbl | UPDATE-ACCOUNT-DB2 SECTION / UAD010 |
| webui DB2 accessor Account result-set read via rs.getDouble | read | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/web/db2/Account.java | L432 |
| webui DB2 accessor Account balance-range result-set read via rs.getDouble | read | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/web/db2/Account.java | L478 |
| XFRFUN from-leg ACCOUNT layout instance | consume | src/base/cobol_src/XFRFUN.cbl | LOCAL-STORAGE SECTION / COPY ACCOUNT L125 |
| XFRFUN to-leg ACCOUNT2 layout instance via COPY ... REPLACING | consume | src/base/cobol_src/XFRFUN.cbl | LOCAL-STORAGE SECTION / COPY ACCOUNT L128 |
| CREACC | consume | src/base/cobol_src/CREACC.cbl | LOCAL-STORAGE SECTION / COPY ACCOUNT L141 |
| DBCRFUN | consume | src/base/cobol_src/DBCRFUN.cbl | LOCAL-STORAGE SECTION / COPY ACCOUNT L119 |
| DELACC | consume | src/base/cobol_src/DELACC.cbl | WORKING-STORAGE SECTION / COPY ACCOUNT L105 |
| DELCUS | consume | src/base/cobol_src/DELCUS.cbl | WORKING-STORAGE SECTION / COPY ACCOUNT L82 |
| INQACC | consume | src/base/cobol_src/INQACC.cbl | WORKING-STORAGE SECTION / COPY ACCOUNT L94 |
| INQACCCU | consume | src/base/cobol_src/INQACCCU.cbl | WORKING-STORAGE SECTION / COPY ACCOUNT L104 |
| UPDACC | consume | src/base/cobol_src/UPDACC.cbl | LOCAL-STORAGE SECTION / COPY ACCOUNT L100 |
| BANKDATA DB2 declaration | consume | src/base/cobol_src/BANKDATA.cbl | WORKING-STORAGE SECTION / EXEC SQL INCLUDE ACCDB2 L74 |
| CREACC DB2 declaration | consume | src/base/cobol_src/CREACC.cbl | WORKING-STORAGE SECTION / EXEC SQL INCLUDE ACCDB2 L50 |
| DBCRFUN DB2 declaration | consume | src/base/cobol_src/DBCRFUN.cbl | WORKING-STORAGE SECTION / EXEC SQL INCLUDE ACCDB2 L56 |
| DELACC DB2 declaration | consume | src/base/cobol_src/DELACC.cbl | WORKING-STORAGE SECTION / EXEC SQL INCLUDE ACCDB2 L51 |
| INQACC DB2 declaration | consume | src/base/cobol_src/INQACC.cbl | WORKING-STORAGE SECTION / EXEC SQL INCLUDE ACCDB2 L42 |
| INQACCCU DB2 declaration | consume | src/base/cobol_src/INQACCCU.cbl | WORKING-STORAGE SECTION / EXEC SQL INCLUDE ACCDB2 L41 |
| UPDACC DB2 declaration | consume | src/base/cobol_src/UPDACC.cbl | WORKING-STORAGE SECTION / EXEC SQL INCLUDE ACCDB2 L57 |
| XFRFUN DB2 declaration | consume | src/base/cobol_src/XFRFUN.cbl | WORKING-STORAGE SECTION / EXEC SQL INCLUDE ACCDB2 L61 |
| webui DB2 accessor Account column constant ACCOUNT_AVAILABLE_BALANCE | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/web/db2/Account.java | L76 |
| webui DB2 accessor Account defect: money held as `private double availableBalance;`, a precision-loss hop against the BigDecimal wire form | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/web/db2/Account.java | L117 |
| webui JSON DTO AccountJSON declares the correct money form `BigDecimal availableBalance;` | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/AccountJSON.java | L49-L50 |
| webui JAX-RS GET /webui-1.0/banking/account/balance served under @ApplicationPath("banking") | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/AccountsResource.java | L1462-L1463 |
| webui JAX-RS application path binding for every banking resource | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/BankingApplication.java | L12 |
| Customer Services Interface CreaccJson second money-precision defect: `private float commActualBalance;` sits beside the available-balance wire field | consume | src/Z-OS-Connect-Customer-Services-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/customerservices/jsonclasses/createaccount/CreaccJson.java | L46-L47 |
| z/OS Connect service CSaccenq interface binding | consume | src/zosconnect_artefacts/services/CSaccenq/service-interfaces/INQACCZ.si | L6 |
| z/OS Connect API getCSaccenq (GET /inqaccz/enquiry/{accno}) | consume | src/zosconnect_artefacts/apis/inqaccz/api-docs/swagger.json | GET /inqaccz/enquiry/{accno} |
| z/OS Connect API putPay (PUT /makepayment/dbcr) | consume | src/zosconnect_artefacts/apis/makepayment/api-docs/swagger.json | PUT /makepayment/dbcr |
| CBSA RESTful Interface Reference defect: the balance-search URL is written with a doubled b as bbanking, whereas the annotations resolve to /webui-1.0/banking/account/balance | consume | etc/usage/carbonReactUI/doc/CBSA_RESTful_Interface_Reference.md | #### Account / ###### Get accounts above or below a specified balance |

## account-actual-balance — ACCOUNT Actual Balance (`PIC S9(10)V99`) [src/base/cobol_copy/ACCOUNT.cpy:L35]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| ACCOUNT.cpy copybook declaration ACCOUNT-ACTUAL-BALANCE | create | src/base/cobol_copy/ACCOUNT.cpy | L35 |
| ACCDB2.cpy alternate representation as DB2 column ACCOUNT_ACTUAL_BALANCE DECIMAL(10, 2) | create | src/base/cobol_copy/ACCDB2.cpy | L19 |
| PAYDBCR.cpy alternate representation as payment COMMAREA copy COMM-ACT-BAL | create | src/base/cobol_copy/PAYDBCR.cpy | L11 |
| CREACC.cpy alternate representation as create-account COMMAREA copy COMM-ACT-BAL | create | src/base/cobol_copy/CREACC.cpy | L31 |
| INQACC.cpy alternate representation as account-enquiry COMMAREA copy INQACC-ACTUAL-BAL | create | src/base/cobol_copy/INQACC.cpy | L31 |
| INQACCCU.cpy alternate representation as list-accounts COMMAREA array copy COMM-ACTUAL-BAL | create | src/base/cobol_copy/INQACCCU.cpy | L38 |
| DELACC.cpy alternate representation as delete-account COMMAREA copy DELACC-ACTUAL-BAL | create | src/base/cobol_copy/DELACC.cpy | L31 |
| UPDACC.cpy alternate representation as update-account COMMAREA copy COMM-ACTUAL-BAL | create | src/base/cobol_copy/UPDACC.cpy | L30 |
| XFRFUN.cpy alternate representation as transfer COMMAREA from-leg copy COMM-FACTBAL | create | src/base/cobol_copy/XFRFUN.cpy | L13 |
| XFRFUN.cpy alternate representation as transfer COMMAREA to-leg copy COMM-TACTBAL | create | src/base/cobol_copy/XFRFUN.cpy | L15 |
| BANKDATA seeds initial account balances | create | src/base/cobol_src/BANKDATA.cbl | POPULATE-ACC SECTION / PA010 |
| CREACC establishes the opening balance on account creation | create | src/base/cobol_src/CREACC.cbl | WRITE-ACCOUNT-DB2 SECTION / WAD010 |
| DBCRFUN single-account debit/credit balance update | write | src/base/cobol_src/DBCRFUN.cbl | UPDATE-ACCOUNT-DB2 SECTION / UAD010 |
| XFRFUN transfer from-leg balance update | write | src/base/cobol_src/XFRFUN.cbl | UPDATE-ACCOUNT-DB2-FROM SECTION / UADF010 |
| XFRFUN transfer to-leg balance update | write | src/base/cobol_src/XFRFUN.cbl | UPDATE-ACCOUNT-DB2-TO SECTION / UADT010 |
| webui DB2 accessor Account defect: `stmt.setDouble(7, this.actualBalance);` binds money as a double | write | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/web/db2/Account.java | L390 |
| INQACC | read | src/base/cobol_src/INQACC.cbl | FETCH-DATA SECTION / FD010 |
| INQACCCU | read | src/base/cobol_src/INQACCCU.cbl | FETCH-DATA SECTION / FD010 |
| DELACC captures the terminal balance for the PROCTRAN audit record | read | src/base/cobol_src/DELACC.cbl | READ-ACCOUNT-DB2 SECTION / RAD010 |
| UPDACC balances are read but excluded from the update allowlist | read | src/base/cobol_src/UPDACC.cbl | UPDATE-ACCOUNT-DB2 SECTION / UAD010 |
| webui DB2 accessor Account balance-range SQL predicates keyed on the actual balance | read | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/web/db2/Account.java | L84-L86 |
| webui DB2 accessor Account result-set read via rs.getDouble | read | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/web/db2/Account.java | L433 |
| XFRFUN from-leg ACCOUNT layout instance | consume | src/base/cobol_src/XFRFUN.cbl | LOCAL-STORAGE SECTION / COPY ACCOUNT L125 |
| XFRFUN to-leg ACCOUNT2 layout instance via COPY ... REPLACING | consume | src/base/cobol_src/XFRFUN.cbl | LOCAL-STORAGE SECTION / COPY ACCOUNT L128 |
| CREACC | consume | src/base/cobol_src/CREACC.cbl | LOCAL-STORAGE SECTION / COPY ACCOUNT L141 |
| DBCRFUN | consume | src/base/cobol_src/DBCRFUN.cbl | LOCAL-STORAGE SECTION / COPY ACCOUNT L119 |
| DELACC | consume | src/base/cobol_src/DELACC.cbl | WORKING-STORAGE SECTION / COPY ACCOUNT L105 |
| DELCUS | consume | src/base/cobol_src/DELCUS.cbl | WORKING-STORAGE SECTION / COPY ACCOUNT L82 |
| INQACC | consume | src/base/cobol_src/INQACC.cbl | WORKING-STORAGE SECTION / COPY ACCOUNT L94 |
| INQACCCU | consume | src/base/cobol_src/INQACCCU.cbl | WORKING-STORAGE SECTION / COPY ACCOUNT L104 |
| UPDACC | consume | src/base/cobol_src/UPDACC.cbl | LOCAL-STORAGE SECTION / COPY ACCOUNT L100 |
| webui DB2 accessor Account column constant ACCOUNT_ACTUAL_BALANCE | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/web/db2/Account.java | L78 |
| webui DB2 accessor Account defect: money held as `private double actualBalance;`, a precision-loss hop against the BigDecimal wire form | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/web/db2/Account.java | L119 |
| webui JSON DTO AccountJSON declares the correct money form `BigDecimal actualBalance;` | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/AccountJSON.java | L46-L47 |
| Customer Services Interface CreaccJson money-precision defect: `private float commActualBalance;` | consume | src/Z-OS-Connect-Customer-Services-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/customerservices/jsonclasses/createaccount/CreaccJson.java | L46-L47 |
| z/OS Connect service CSaccenq interface binding | consume | src/zosconnect_artefacts/services/CSaccenq/service-interfaces/INQACCZ.si | L6 |
| z/OS Connect API getCSaccenq (GET /inqaccz/enquiry/{accno}) | consume | src/zosconnect_artefacts/apis/inqaccz/api-docs/swagger.json | GET /inqaccz/enquiry/{accno} |
| z/OS Connect API putPay (PUT /makepayment/dbcr) | consume | src/zosconnect_artefacts/apis/makepayment/api-docs/swagger.json | PUT /makepayment/dbcr |

## customer-credit-score — CUSTOMER Credit Score (`PIC 999`) [src/base/cobol_copy/CUSTOMER.cpy:L28]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| CUSTOMER.cpy copybook declaration CUSTOMER-CREDIT-SCORE | create | src/base/cobol_copy/CUSTOMER.cpy | L28 |
| CRECUST.cpy alternate representation as create-customer COMMAREA copy COMM-CREDIT-SCORE | create | src/base/cobol_copy/CRECUST.cpy | L18 |
| INQCUST.cpy alternate representation as customer-enquiry COMMAREA copy INQCUST-CREDIT-SCORE | create | src/base/cobol_copy/INQCUST.cpy | L16 |
| DELCUS.cpy alternate representation as delete-customer COMMAREA copy COMM-CREDIT-SCORE | create | src/base/cobol_copy/DELCUS.cpy | L17 |
| UPDCUST.cpy alternate representation as update-customer COMMAREA copy COMM-CREDIT-SCORE | create | src/base/cobol_copy/UPDCUST.cpy | L17 |
| CRDTAGY1 credit agency simulator generates the score from the EIBTASKN seed and the literal bounds 1 and 999 | create | src/base/cobol_src/CRDTAGY1.cbl | PREMIERE SECTION / A010 |
| CRDTAGY2 credit agency simulator generates the score from the EIBTASKN seed and the literal bounds 1 and 999 | create | src/base/cobol_src/CRDTAGY2.cbl | PREMIERE SECTION / A010 |
| CRDTAGY3 credit agency simulator generates the score from the EIBTASKN seed and the literal bounds 1 and 999 | create | src/base/cobol_src/CRDTAGY3.cbl | PREMIERE SECTION / A010 |
| CRDTAGY4 credit agency simulator generates the score from the EIBTASKN seed and the literal bounds 1 and 999 | create | src/base/cobol_src/CRDTAGY4.cbl | PREMIERE SECTION / A010 |
| CRDTAGY5 credit agency simulator generates the score from the EIBTASKN seed and the literal bounds 1 and 999 | create | src/base/cobol_src/CRDTAGY5.cbl | PREMIERE SECTION / A010 |
| CRECUST aggregates the five agency responses under a three-second container window | create | src/base/cobol_src/CRECUST.cbl | CREDIT-CHECK SECTION / CC010 |
| BANKDATA seeds credit scores for generated customers | create | src/base/cobol_src/BANKDATA.cbl | POPULATE-ACC SECTION / PA010 |
| CRECUST | write | src/base/cobol_src/CRECUST.cbl | WRITE-CUSTOMER-VSAM SECTION / WCV010 |
| UPDCUST | write | src/base/cobol_src/UPDCUST.cbl | UPDATE-CUSTOMER-VSAM SECTION / UCV010 |
| INQCUST | read | src/base/cobol_src/INQCUST.cbl | READ-CUSTOMER-VSAM SECTION / RCV010 |
| DELCUS captures the score into the delete-customer audit path | read | src/base/cobol_src/DELCUS.cbl | DEL-CUST-VSAM SECTION / DCV010 |
| BANKDATA | consume | src/base/cobol_src/BANKDATA.cbl | FILE SECTION / COPY CUSTOMER L61 |
| CREACC | consume | src/base/cobol_src/CREACC.cbl | LOCAL-STORAGE SECTION / COPY CUSTOMER L144 |
| CRECUST | consume | src/base/cobol_src/CRECUST.cbl | LOCAL-STORAGE SECTION / COPY CUSTOMER L110 |
| DELCUS | consume | src/base/cobol_src/DELCUS.cbl | WORKING-STORAGE SECTION / COPY CUSTOMER L85 |
| INQACCCU | consume | src/base/cobol_src/INQACCCU.cbl | WORKING-STORAGE SECTION / COPY CUSTOMER L155 |
| INQCUST | consume | src/base/cobol_src/INQCUST.cbl | LOCAL-STORAGE SECTION / COPY CUSTOMER L51 |
| UPDCUST | consume | src/base/cobol_src/UPDCUST.cbl | LOCAL-STORAGE SECTION / COPY CUSTOMER L65 |
| IBM Record Generator commarea class CUSTOMER, CUSTOMER_CREDIT_SCORE ExternalDecimalAsIntField of width 3 | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/CUSTOMER.java | L98 |
| IBM Record Generator commarea class CRECUST, COMM_CREDIT_SCORE ExternalDecimalAsIntField of width 3 | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/CRECUST.java | L81 |
| webui VSAM accessor Customer holds the credit score as `private String creditScore;` | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/web/vsam/Customer.java | L111 |
| webui JAX-RS GET /webui-1.0/banking/customer/{id} | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/CustomerResource.java | L495-L496 |
| z/OS Connect service CScustenq interface binding | consume | src/zosconnect_artefacts/services/CScustenq/service-interfaces/INQCUSTZ.si | L6 |
| z/OS Connect API getCScustenq (GET /inqcustz/enquiry/{custno}) | consume | src/zosconnect_artefacts/apis/inqcustz/api-docs/swagger.json | GET /inqcustz/enquiry/{custno} |
| Customer Services Interface CustomerEnquiryJson names the INQCUSTZ commarea contract | consume | src/Z-OS-Connect-Customer-Services-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/customerservices/jsonclasses/customerenquiry/CustomerEnquiryJson.java | L13 |

## customer-cs-review-date — CUSTOMER Credit-Score Review Date (`PIC 9(8)`) [src/base/cobol_copy/CUSTOMER.cpy:L29]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| CUSTOMER.cpy copybook declaration CUSTOMER-CS-REVIEW-DATE | create | src/base/cobol_copy/CUSTOMER.cpy | L29 |
| CUSTOMER.cpy copybook declaration CUSTOMER-CS-GROUP REDEFINES the review date; day L32, month L33, year L34 | create | src/base/cobol_copy/CUSTOMER.cpy | L30-L31 |
| CRECUST.cpy alternate representation as create-customer COMMAREA copy COMM-CS-REVIEW-DATE | create | src/base/cobol_copy/CRECUST.cpy | L19 |
| INQCUST.cpy alternate representation as customer-enquiry COMMAREA group INQCUST-CS-REVIEW-DT | create | src/base/cobol_copy/INQCUST.cpy | L17 |
| DELCUS.cpy alternate representation as delete-customer COMMAREA copy COMM-CS-REVIEW-DATE | create | src/base/cobol_copy/DELCUS.cpy | L18 |
| UPDCUST.cpy alternate representation as update-customer COMMAREA copy COMM-CS-REVIEW-DATE | create | src/base/cobol_copy/UPDCUST.cpy | L18 |
| CRECUST derives the review date from today plus a random offset bounded by the literals 1 and 21, converted by DATE-OF-INTEGER | create | src/base/cobol_src/CRECUST.cbl | CREDIT-CHECK SECTION / CC010 |
| BANKDATA seeds review dates for generated customers | create | src/base/cobol_src/BANKDATA.cbl | POPULATE-ACC SECTION / PA010 |
| CRECUST | write | src/base/cobol_src/CRECUST.cbl | WRITE-CUSTOMER-VSAM SECTION / WCV010 |
| UPDCUST | write | src/base/cobol_src/UPDCUST.cbl | UPDATE-CUSTOMER-VSAM SECTION / UCV010 |
| INQCUST | read | src/base/cobol_src/INQCUST.cbl | READ-CUSTOMER-VSAM SECTION / RCV010 |
| DELCUS | read | src/base/cobol_src/DELCUS.cbl | DEL-CUST-VSAM SECTION / DCV010 |
| BANKDATA | consume | src/base/cobol_src/BANKDATA.cbl | FILE SECTION / COPY CUSTOMER L61 |
| CREACC | consume | src/base/cobol_src/CREACC.cbl | LOCAL-STORAGE SECTION / COPY CUSTOMER L144 |
| CRECUST | consume | src/base/cobol_src/CRECUST.cbl | LOCAL-STORAGE SECTION / COPY CUSTOMER L110 |
| DELCUS | consume | src/base/cobol_src/DELCUS.cbl | WORKING-STORAGE SECTION / COPY CUSTOMER L85 |
| INQACCCU | consume | src/base/cobol_src/INQACCCU.cbl | WORKING-STORAGE SECTION / COPY CUSTOMER L155 |
| INQCUST | consume | src/base/cobol_src/INQCUST.cbl | LOCAL-STORAGE SECTION / COPY CUSTOMER L51 |
| UPDCUST | consume | src/base/cobol_src/UPDCUST.cbl | LOCAL-STORAGE SECTION / COPY CUSTOMER L65 |
| IBM Record Generator commarea class CUSTOMER, CUSTOMER_CS_REVIEW_DATE ExternalDecimalAsIntField of width 8 | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/CUSTOMER.java | L102 |
| IBM Record Generator commarea class CUSTOMER, review-date day, month and year sub-fields | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/CUSTOMER.java | L115-L123 |
| IBM Record Generator commarea class CRECUST, COMM_CS_REVIEW_DATE ExternalDecimalAsIntField of width 8 | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/CRECUST.java | L85 |
| webui VSAM accessor Customer reconstructs the review date from calendar components | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/web/vsam/Customer.java | L1228-L1230 |
| Customer Services Interface InqCustReviewDate wire projection of the review date | consume | src/Z-OS-Connect-Customer-Services-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/customerservices/jsonclasses/customerenquiry/InqCustReviewDate.java | L11 |
| z/OS Connect service CScustenq interface binding | consume | src/zosconnect_artefacts/services/CScustenq/service-interfaces/INQCUSTZ.si | L6 |
| z/OS Connect API getCScustenq (GET /inqcustz/enquiry/{custno}) | consume | src/zosconnect_artefacts/apis/inqcustz/api-docs/swagger.json | GET /inqcustz/enquiry/{custno} |

## dbcrfun-faciltype-channel-discriminator — DBCRFUN FACILTYPE Channel Discriminator COMM-FACILTYPE Within COMM-ORIGIN (`PIC S9(8) COMP`) [src/base/cobol_copy/PAYDBCR.cpy:L17]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| PAYDBCR.cpy copybook declaration COMM-FACILTYPE inside the COMM-ORIGIN group at L12-L18 | create | src/base/cobol_copy/PAYDBCR.cpy | L17 |
| DBCRFUN gates the MORTGAGE and LOAN product restriction and the overdraft check on FACILTYPE 496 | read | src/base/cobol_src/DBCRFUN.cbl | UPDATE-ACCOUNT-DB2 SECTION / UAD010 |
| DBCRFUN promotes 'DEB' to 'PDR' and 'CRE' to 'PCR' when the movement arrives on the payment channel | read | src/base/cobol_src/DBCRFUN.cbl | WRITE-TO-PROCTRAN-DB2 SECTION / WTPD010 |
| DBCRFUN | consume | src/base/cobol_src/DBCRFUN.cbl | LINKAGE SECTION / COPY PAYDBCR L195 |
| Payment Interface OriginJson wire field CommFaciltype | consume | src/Z-OS-Connect-Payment-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/paymentinterface/jsonclasses/paymentinterface/OriginJson.java | L27 |
| Payment Interface OriginJson governance gap: the discriminator originates from a client-side DTO field initialiser `private String commFaciltype = "0496";` rather than from a controller route binding, and the wire form is a 4-character String against a COBOL PIC S9(8) COMP | consume | src/Z-OS-Connect-Payment-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/paymentinterface/jsonclasses/paymentinterface/OriginJson.java | L28 |
| Payment Interface OriginJson FACILTYPE accessors | consume | src/Z-OS-Connect-Payment-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/paymentinterface/jsonclasses/paymentinterface/OriginJson.java | L100-L108 |
| z/OS Connect service Pay interface binding | consume | src/zosconnect_artefacts/services/Pay/service-interfaces/PAYDBCR.si | L6 |
| z/OS Connect API putPay (PUT /makepayment/dbcr) | consume | src/zosconnect_artefacts/apis/makepayment/api-docs/swagger.json | PUT /makepayment/dbcr |

## proctran-eye-catcher — PROCTRAN Eye-Catcher (`PIC X(4)`) [src/base/cobol_copy/PROCTRAN.cpy:L8]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| PROCTRAN.cpy copybook declaration PROC-TRAN-EYE-CATCHER; 88 PROC-TRAN-VALID VALUE 'PRTR' at L9 | create | src/base/cobol_copy/PROCTRAN.cpy | L8 |
| PROCDB2.cpy alternate representation as DB2 column PROCTRAN_EYECATCHER CHAR(4) | create | src/base/cobol_copy/PROCDB2.cpy | L9 |
| CREACC writes the DB2 host-variable form PROCTRAN_EYECATCHER | write | src/base/cobol_src/CREACC.cbl | WRITE-PROCTRAN-DB2 SECTION / WPD010 |
| CRECUST writes the DB2 host-variable form PROCTRAN_EYECATCHER | write | src/base/cobol_src/CRECUST.cbl | WRITE-PROCTRAN-DB2 SECTION / WPD010 |
| DBCRFUN writes the DB2 host-variable form PROCTRAN_EYECATCHER | write | src/base/cobol_src/DBCRFUN.cbl | WRITE-TO-PROCTRAN-DB2 SECTION / WTPD010 |
| DELACC writes the DB2 host-variable form PROCTRAN_EYECATCHER | write | src/base/cobol_src/DELACC.cbl | WRITE-PROCTRAN-DB2 SECTION / WPD010 |
| DELCUS writes the DB2 host-variable form PROCTRAN_EYECATCHER | write | src/base/cobol_src/DELCUS.cbl | WRITE-PROCTRAN-CUST-DB2 SECTION / WPCD010 |
| XFRFUN writes the DB2 host-variable form PROCTRAN_EYECATCHER | write | src/base/cobol_src/XFRFUN.cbl | WRITE-TO-PROCTRAN-DB2 SECTION / WTPD010 |
| (none in repository) | read | src/base/cobol_copy/PROCTRAN.cpy | no COBOL program references the VSAM field name PROC-TRAN-EYE-CATCHER; exhaustive repo grep over all 29 programs |
| IBM Record Generator commarea class PROCTRAN, eye-catcher validity predicate | read | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/PROCTRAN.java | L467 |
| CREACC | consume | src/base/cobol_src/CREACC.cbl | WORKING-STORAGE SECTION / COPY PROCTRAN L122 |
| CRECUST | consume | src/base/cobol_src/CRECUST.cbl | WORKING-STORAGE SECTION / COPY PROCTRAN L84 |
| DBCRFUN | consume | src/base/cobol_src/DBCRFUN.cbl | LOCAL-STORAGE SECTION / COPY PROCTRAN L150 |
| DELACC | consume | src/base/cobol_src/DELACC.cbl | WORKING-STORAGE SECTION / COPY PROCTRAN L108 |
| DELCUS | consume | src/base/cobol_src/DELCUS.cbl | WORKING-STORAGE SECTION / COPY PROCTRAN L88 |
| XFRFUN | consume | src/base/cobol_src/XFRFUN.cbl | LOCAL-STORAGE SECTION / COPY PROCTRAN L175 |
| IBM Record Generator commarea class PROCTRAN, PROC_TRAN_EYE_CATCHER 4-byte StringField | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/PROCTRAN.java | L36 |
| IBM Record Generator commarea class PROCTRAN, valid-eye-catcher constant `PROC_TRAN_VALID = "PRTR"` | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/PROCTRAN.java | L40 |

## proctran-desc-xfr-flag — PROCTRAN Descriptor Transfer Discriminator PROC-TRAN-DESC-XFR-FLAG ('TRANSFER') (`PIC X(26)`) [src/base/cobol_copy/PROCTRAN.cpy:L50-L52]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| PROCTRAN.cpy copybook declaration overlay field carrying the 'TRANSFER' descriptor discriminator | create | src/base/cobol_copy/PROCTRAN.cpy | L50 |
| PROCTRAN.cpy copybook declaration 88 condition name setting the 'TRANSFER' descriptor discriminator | create | src/base/cobol_copy/PROCTRAN.cpy | L51-L52 |
| PROCTRAN.cpy copybook declaration REDEFINES overlay of PROC-TRAN-DESC PIC X(40) declared at L48 | create | src/base/cobol_copy/PROCTRAN.cpy | L49 |
| PROCDB2.cpy alternate representation as DB2 column PROCTRAN_DESC CHAR(40) | create | src/base/cobol_copy/PROCDB2.cpy | L16 |
| XFRFUN populates the descriptor overlay carrying the 'TRANSFER' descriptor discriminator | write | src/base/cobol_src/XFRFUN.cbl | WRITE-TO-PROCTRAN-DB2 SECTION / WTPD010 |
| XFRFUN | consume | src/base/cobol_src/XFRFUN.cbl | LOCAL-STORAGE SECTION / COPY PROCTRAN L175 |
| XFRFUN DB2 declaration | consume | src/base/cobol_src/XFRFUN.cbl | WORKING-STORAGE SECTION / EXEC SQL INCLUDE PROCDB2 L82 |
| IBM Record Generator commarea class PROCTRAN, descriptor discriminator constant for the 'TRANSFER' descriptor discriminator | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/PROCTRAN.java | L222 |

## proctran-desc-creacc-flag — PROCTRAN Descriptor Create-Account Discriminator PROC-DESC-CREACC-FLAG ('CREATE') (`PIC X(6)`) [src/base/cobol_copy/PROCTRAN.cpy:L78-L80]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| PROCTRAN.cpy copybook declaration overlay field carrying the 'CREATE' descriptor discriminator | create | src/base/cobol_copy/PROCTRAN.cpy | L78 |
| PROCTRAN.cpy copybook declaration 88 condition name setting the 'CREATE' descriptor discriminator | create | src/base/cobol_copy/PROCTRAN.cpy | L79-L80 |
| PROCTRAN.cpy copybook declaration REDEFINES overlay of PROC-TRAN-DESC PIC X(40) declared at L48 | create | src/base/cobol_copy/PROCTRAN.cpy | L69 |
| PROCDB2.cpy alternate representation as DB2 column PROCTRAN_DESC CHAR(40) | create | src/base/cobol_copy/PROCDB2.cpy | L16 |
| CREACC populates the descriptor overlay carrying the 'CREATE' descriptor discriminator | write | src/base/cobol_src/CREACC.cbl | WRITE-PROCTRAN-DB2 SECTION / WPD010 |
| CREACC | consume | src/base/cobol_src/CREACC.cbl | WORKING-STORAGE SECTION / COPY PROCTRAN L122 |
| CREACC DB2 declaration | consume | src/base/cobol_src/CREACC.cbl | WORKING-STORAGE SECTION / EXEC SQL INCLUDE PROCDB2 L94 |
| IBM Record Generator commarea class PROCTRAN, descriptor discriminator constant for the 'CREATE' descriptor discriminator | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/PROCTRAN.java | L330 |

## proctran-desc-delacc-flag — PROCTRAN Descriptor Delete-Account Discriminator PROC-DESC-DELACC-FLAG ('DELETE') (`PIC X(6)`) [src/base/cobol_copy/PROCTRAN.cpy:L66-L68]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| PROCTRAN.cpy copybook declaration overlay field carrying the 'DELETE' descriptor discriminator | create | src/base/cobol_copy/PROCTRAN.cpy | L66 |
| PROCTRAN.cpy copybook declaration 88 condition name setting the 'DELETE' descriptor discriminator | create | src/base/cobol_copy/PROCTRAN.cpy | L67-L68 |
| PROCTRAN.cpy copybook declaration REDEFINES overlay of PROC-TRAN-DESC PIC X(40) declared at L48 | create | src/base/cobol_copy/PROCTRAN.cpy | L57 |
| PROCDB2.cpy alternate representation as DB2 column PROCTRAN_DESC CHAR(40) | create | src/base/cobol_copy/PROCDB2.cpy | L16 |
| DELACC populates the descriptor overlay carrying the 'DELETE' descriptor discriminator | write | src/base/cobol_src/DELACC.cbl | WRITE-PROCTRAN-DB2 SECTION / WPD010 |
| DELACC | consume | src/base/cobol_src/DELACC.cbl | WORKING-STORAGE SECTION / COPY PROCTRAN L108 |
| DELACC DB2 declaration | consume | src/base/cobol_src/DELACC.cbl | WORKING-STORAGE SECTION / EXEC SQL INCLUDE PROCDB2 L71 |
| IBM Record Generator commarea class PROCTRAN, descriptor discriminator constant for the 'DELETE' descriptor discriminator | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/PROCTRAN.java | L281 |

## proctran-desc-crecus-separator — PROCTRAN Descriptor Create-Customer Date Separator PROC-DESC-CRECUS-FILLER-SET ('-') (`PIC X`) [src/base/cobol_copy/PROCTRAN.cpy:L97-L98]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| PROCTRAN.cpy copybook declaration PROC-DESC-CRECUS-FILLER date separator | create | src/base/cobol_copy/PROCTRAN.cpy | L97 |
| PROCTRAN.cpy copybook declaration 88 PROC-DESC-CRECUS-FILLER-SET VALUE '-' | create | src/base/cobol_copy/PROCTRAN.cpy | L98 |
| PROCTRAN.cpy copybook declaration PROC-DESC-CRECUS-FILLER2 second date separator | create | src/base/cobol_copy/PROCTRAN.cpy | L100 |
| PROCTRAN.cpy copybook declaration 88 PROC-DESC-CRECUS-FILLER2-SET VALUE '-' | create | src/base/cobol_copy/PROCTRAN.cpy | L101 |
| PROCTRAN.cpy copybook declaration PROC-TRAN-DESC-CRECUS REDEFINES PROC-TRAN-DESC PIC X(40) declared at L48 | create | src/base/cobol_copy/PROCTRAN.cpy | L92 |
| PROCDB2.cpy alternate representation as DB2 column PROCTRAN_DESC CHAR(40) | create | src/base/cobol_copy/PROCDB2.cpy | L16 |
| CRECUST formats the create-customer descriptor with hyphen-separated date components | write | src/base/cobol_src/CRECUST.cbl | WRITE-PROCTRAN-DB2 SECTION / WPD010 |
| CRECUST | consume | src/base/cobol_src/CRECUST.cbl | WORKING-STORAGE SECTION / COPY PROCTRAN L84 |
| CRECUST DB2 declaration | consume | src/base/cobol_src/CRECUST.cbl | WORKING-STORAGE SECTION / EXEC SQL INCLUDE PROCDB2 L63 |
| IBM Record Generator commarea class PROCTRAN, first create-customer separator constant | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/PROCTRAN.java | L410 |
| IBM Record Generator commarea class PROCTRAN, second create-customer separator constant | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/PROCTRAN.java | L422 |

## proctran-desc-delcus-separator — PROCTRAN Descriptor Delete-Customer Date Separator PROC-DESC-DELCUS-FILLER-SET ('-') (`PIC X`) [src/base/cobol_copy/PROCTRAN.cpy:L86-L87]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| PROCTRAN.cpy copybook declaration PROC-DESC-DELCUS-FILLER date separator | create | src/base/cobol_copy/PROCTRAN.cpy | L86 |
| PROCTRAN.cpy copybook declaration 88 PROC-DESC-DELCUS-FILLER-SET VALUE '-' | create | src/base/cobol_copy/PROCTRAN.cpy | L87 |
| PROCTRAN.cpy copybook declaration PROC-DESC-DELCUS-FILLER2 second date separator | create | src/base/cobol_copy/PROCTRAN.cpy | L89 |
| PROCTRAN.cpy copybook declaration 88 PROC-DESC-DELCUS-FILLER2-SET VALUE '-' | create | src/base/cobol_copy/PROCTRAN.cpy | L90 |
| PROCTRAN.cpy copybook declaration PROC-TRAN-DESC-DELCUS REDEFINES PROC-TRAN-DESC PIC X(40) declared at L48 | create | src/base/cobol_copy/PROCTRAN.cpy | L81 |
| PROCDB2.cpy alternate representation as DB2 column PROCTRAN_DESC CHAR(40) | create | src/base/cobol_copy/PROCDB2.cpy | L16 |
| DELCUS formats the delete-customer descriptor with hyphen-separated date components | write | src/base/cobol_src/DELCUS.cbl | WRITE-PROCTRAN-CUST-DB2 SECTION / WPCD010 |
| DELCUS | consume | src/base/cobol_src/DELCUS.cbl | WORKING-STORAGE SECTION / COPY PROCTRAN L88 |
| DELCUS DB2 declaration | consume | src/base/cobol_src/DELCUS.cbl | WORKING-STORAGE SECTION / EXEC SQL INCLUDE PROCDB2 L55 |
| IBM Record Generator commarea class PROCTRAN, first delete-customer separator constant | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/PROCTRAN.java | L362 |
| IBM Record Generator commarea class PROCTRAN, second delete-customer separator constant | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/PROCTRAN.java | L374 |

## account-eye-catcher — ACCOUNT Eye-Catcher (`PIC X(4)`) [src/base/cobol_copy/ACCOUNT.cpy:L8]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| ACCOUNT.cpy copybook declaration ACCOUNT-EYE-CATCHER; 88 ACCOUNT-EYECATCHER-VALUE VALUE 'ACCT' at L9 | create | src/base/cobol_copy/ACCOUNT.cpy | L8 |
| ACCDB2.cpy alternate representation as DB2 column ACCOUNT_EYECATCHER CHAR(4) | create | src/base/cobol_copy/ACCDB2.cpy | L8 |
| INQACCCU.cpy alternate representation as list-accounts COMMAREA array copy COMM-EYE | create | src/base/cobol_copy/INQACCCU.cpy | L15 |
| DELACC.cpy alternate representation as delete-account COMMAREA copy DELACC-EYE | create | src/base/cobol_copy/DELACC.cpy | L8 |
| UPDACC.cpy alternate representation as update-account COMMAREA copy COMM-EYE | create | src/base/cobol_copy/UPDACC.cpy | L7 |
| INQACC.cpy alternate representation as account-enquiry COMMAREA copy INQACC-EYE | create | src/base/cobol_copy/INQACC.cpy | L8 |
| BANKDATA stamps the eye-catcher on seeded account records | create | src/base/cobol_src/BANKDATA.cbl | POPULATE-ACC SECTION / PA010 |
| CREACC | write | src/base/cobol_src/CREACC.cbl | WRITE-ACCOUNT-DB2 SECTION / WAD010 |
| DELACC | read | src/base/cobol_src/DELACC.cbl | READ-ACCOUNT-DB2 SECTION / RAD010 |
| INQACC | read | src/base/cobol_src/INQACC.cbl | FETCH-DATA SECTION / FD010 |
| webui DB2 accessor Account restricts every SELECT to rows whose eye-catcher matches ACCT | read | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/web/db2/Account.java | L82 |
| CREACC | consume | src/base/cobol_src/CREACC.cbl | LOCAL-STORAGE SECTION / COPY ACCOUNT L141 |
| DBCRFUN | consume | src/base/cobol_src/DBCRFUN.cbl | LOCAL-STORAGE SECTION / COPY ACCOUNT L119 |
| DELACC | consume | src/base/cobol_src/DELACC.cbl | WORKING-STORAGE SECTION / COPY ACCOUNT L105 |
| DELCUS | consume | src/base/cobol_src/DELCUS.cbl | WORKING-STORAGE SECTION / COPY ACCOUNT L82 |
| INQACC | consume | src/base/cobol_src/INQACC.cbl | WORKING-STORAGE SECTION / COPY ACCOUNT L94 |
| INQACCCU | consume | src/base/cobol_src/INQACCCU.cbl | WORKING-STORAGE SECTION / COPY ACCOUNT L104 |
| UPDACC | consume | src/base/cobol_src/UPDACC.cbl | LOCAL-STORAGE SECTION / COPY ACCOUNT L100 |
| XFRFUN | consume | src/base/cobol_src/XFRFUN.cbl | LOCAL-STORAGE SECTION / COPY ACCOUNT L125 |

## account-type-code — ACCOUNT Product Type Code (`PIC X(8)`) [src/base/cobol_copy/ACCOUNT.cpy:L14]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| ACCOUNT.cpy copybook declaration ACCOUNT-TYPE | create | src/base/cobol_copy/ACCOUNT.cpy | L14 |
| ACCDB2.cpy alternate representation as DB2 column ACCOUNT_TYPE CHAR(8) | create | src/base/cobol_copy/ACCDB2.cpy | L12 |
| CREACC.cpy alternate representation as create-account COMMAREA copy COMM-ACC-TYPE | create | src/base/cobol_copy/CREACC.cpy | L12 |
| INQACC.cpy alternate representation as account-enquiry COMMAREA copy INQACC-ACC-TYPE | create | src/base/cobol_copy/INQACC.cpy | L12 |
| INQACCCU.cpy alternate representation as list-accounts COMMAREA array copy COMM-ACC-TYPE | create | src/base/cobol_copy/INQACCCU.cpy | L19 |
| DELACC.cpy alternate representation as delete-account COMMAREA copy DELACC-ACC-TYPE | create | src/base/cobol_copy/DELACC.cpy | L12 |
| UPDACC.cpy alternate representation as update-account COMMAREA copy COMM-ACC-TYPE | create | src/base/cobol_copy/UPDACC.cpy | L11 |
| BANKDATA assigns product types to seeded accounts | create | src/base/cobol_src/BANKDATA.cbl | POPULATE-ACC SECTION / PA010 |
| CREACC | write | src/base/cobol_src/CREACC.cbl | WRITE-ACCOUNT-DB2 SECTION / WAD010 |
| UPDACC | write | src/base/cobol_src/UPDACC.cbl | UPDATE-ACCOUNT-DB2 SECTION / UAD010 |
| CREACC validates the requested type against the allowed set ISA, MORTGAGE, SAVING, CURRENT and LOAN, rejecting anything else with fail code 'A' | read | src/base/cobol_src/CREACC.cbl | ACCOUNT-TYPE-CHECK SECTION / ATC010 |
| DBCRFUN combines the product type with FACILTYPE 496 to block payments against MORTGAGE and LOAN accounts | read | src/base/cobol_src/DBCRFUN.cbl | UPDATE-ACCOUNT-DB2 SECTION / UAD010 |
| DELACC | read | src/base/cobol_src/DELACC.cbl | READ-ACCOUNT-DB2 SECTION / RAD010 |
| INQACC | read | src/base/cobol_src/INQACC.cbl | FETCH-DATA SECTION / FD010 |
| INQACCCU | read | src/base/cobol_src/INQACCCU.cbl | FETCH-DATA SECTION / FD010 |
| CREACC | consume | src/base/cobol_src/CREACC.cbl | LOCAL-STORAGE SECTION / COPY ACCOUNT L141 |
| DBCRFUN | consume | src/base/cobol_src/DBCRFUN.cbl | LOCAL-STORAGE SECTION / COPY ACCOUNT L119 |
| DELACC | consume | src/base/cobol_src/DELACC.cbl | WORKING-STORAGE SECTION / COPY ACCOUNT L105 |
| DELCUS | consume | src/base/cobol_src/DELCUS.cbl | WORKING-STORAGE SECTION / COPY ACCOUNT L82 |
| INQACC | consume | src/base/cobol_src/INQACC.cbl | WORKING-STORAGE SECTION / COPY ACCOUNT L94 |
| INQACCCU | consume | src/base/cobol_src/INQACCCU.cbl | WORKING-STORAGE SECTION / COPY ACCOUNT L104 |
| UPDACC | consume | src/base/cobol_src/UPDACC.cbl | LOCAL-STORAGE SECTION / COPY ACCOUNT L100 |
| XFRFUN | consume | src/base/cobol_src/XFRFUN.cbl | LOCAL-STORAGE SECTION / COPY ACCOUNT L125 |
| webui JAX-RS PUT /webui-1.0/banking/account/{id} | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/AccountsResource.java | L619-L620 |
| z/OS Connect service CSaccupd interface binding | consume | src/zosconnect_artefacts/services/CSaccupd/service-interfaces/UPDACC.si | L6 |
| z/OS Connect API putCSaccupd (PUT /updacc/update) | consume | src/zosconnect_artefacts/apis/updacc/api-docs/swagger.json | PUT /updacc/update |
| z/OS Connect API postCSacccre (POST /creacc/insert) | consume | src/zosconnect_artefacts/apis/creacc/api-docs/swagger.json | POST /creacc/insert |

## account-overdraft-limit — ACCOUNT Overdraft Limit Governing Available-Versus-Actual Divergence (`PIC 9(8)`) [src/base/cobol_copy/ACCOUNT.cpy:L21]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| ACCOUNT.cpy copybook declaration ACCOUNT-OVERDRAFT-LIMIT | create | src/base/cobol_copy/ACCOUNT.cpy | L21 |
| ACCDB2.cpy alternate representation as DB2 column ACCOUNT_OVERDRAFT_LIMIT INTEGER | create | src/base/cobol_copy/ACCDB2.cpy | L15 |
| CREACC.cpy alternate representation as create-account COMMAREA copy COMM-OVERDR-LIM | create | src/base/cobol_copy/CREACC.cpy | L19 |
| INQACC.cpy alternate representation as account-enquiry COMMAREA copy INQACC-OVERDRAFT | create | src/base/cobol_copy/INQACC.cpy | L19 |
| INQACCCU.cpy alternate representation as list-accounts COMMAREA array copy COMM-OVERDRAFT | create | src/base/cobol_copy/INQACCCU.cpy | L26 |
| DELACC.cpy alternate representation as delete-account COMMAREA copy DELACC-OVERDRAFT | create | src/base/cobol_copy/DELACC.cpy | L19 |
| UPDACC.cpy alternate representation as update-account COMMAREA copy COMM-OVERDRAFT | create | src/base/cobol_copy/UPDACC.cpy | L18 |
| BANKDATA seeds overdraft limits on generated accounts | create | src/base/cobol_src/BANKDATA.cbl | POPULATE-ACC SECTION / PA010 |
| CREACC | write | src/base/cobol_src/CREACC.cbl | WRITE-ACCOUNT-DB2 SECTION / WAD010 |
| UPDACC | write | src/base/cobol_src/UPDACC.cbl | UPDATE-ACCOUNT-DB2 SECTION / UAD010 |
| DELACC | read | src/base/cobol_src/DELACC.cbl | READ-ACCOUNT-DB2 SECTION / RAD010 |
| INQACC | read | src/base/cobol_src/INQACC.cbl | FETCH-DATA SECTION / FD010 |
| INQACCCU | read | src/base/cobol_src/INQACCCU.cbl | FETCH-DATA SECTION / FD010 |
| DBCRFUN the overdraft limit is the reason the available and actual balances may diverge | read | src/base/cobol_src/DBCRFUN.cbl | UPDATE-ACCOUNT-DB2 SECTION / UAD010 |
| CREACC | consume | src/base/cobol_src/CREACC.cbl | LOCAL-STORAGE SECTION / COPY ACCOUNT L141 |
| DBCRFUN | consume | src/base/cobol_src/DBCRFUN.cbl | LOCAL-STORAGE SECTION / COPY ACCOUNT L119 |
| DELACC | consume | src/base/cobol_src/DELACC.cbl | WORKING-STORAGE SECTION / COPY ACCOUNT L105 |
| DELCUS | consume | src/base/cobol_src/DELCUS.cbl | WORKING-STORAGE SECTION / COPY ACCOUNT L82 |
| INQACC | consume | src/base/cobol_src/INQACC.cbl | WORKING-STORAGE SECTION / COPY ACCOUNT L94 |
| INQACCCU | consume | src/base/cobol_src/INQACCCU.cbl | WORKING-STORAGE SECTION / COPY ACCOUNT L104 |
| UPDACC | consume | src/base/cobol_src/UPDACC.cbl | LOCAL-STORAGE SECTION / COPY ACCOUNT L100 |
| XFRFUN | consume | src/base/cobol_src/XFRFUN.cbl | LOCAL-STORAGE SECTION / COPY ACCOUNT L125 |
| z/OS Connect service CSaccupd interface binding | consume | src/zosconnect_artefacts/services/CSaccupd/service-interfaces/UPDACC.si | L6 |
| z/OS Connect API putCSaccupd (PUT /updacc/update) | consume | src/zosconnect_artefacts/apis/updacc/api-docs/swagger.json | PUT /updacc/update |

## customer-eye-catcher — CUSTOMER Eye-Catcher (`PIC X(4)`) [src/base/cobol_copy/CUSTOMER.cpy:L8]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| CUSTOMER.cpy copybook declaration CUSTOMER-EYECATCHER; 88 CUSTOMER-EYECATCHER-VALUE VALUE 'CUST' at L9 | create | src/base/cobol_copy/CUSTOMER.cpy | L8 |
| CRECUST.cpy alternate representation as create-customer COMMAREA copy COMM-EYECATCHER | create | src/base/cobol_copy/CRECUST.cpy | L7 |
| INQCUST.cpy alternate representation as customer-enquiry COMMAREA copy INQCUST-EYE | create | src/base/cobol_copy/INQCUST.cpy | L7 |
| DELCUS.cpy alternate representation as delete-customer COMMAREA copy COMM-EYE | create | src/base/cobol_copy/DELCUS.cpy | L7 |
| UPDCUST.cpy alternate representation as update-customer COMMAREA copy COMM-EYE | create | src/base/cobol_copy/UPDCUST.cpy | L7 |
| BANKDATA stamps the eye-catcher on seeded customer records | create | src/base/cobol_src/BANKDATA.cbl | POPULATE-ACC SECTION / PA010 |
| CRECUST | write | src/base/cobol_src/CRECUST.cbl | WRITE-CUSTOMER-VSAM SECTION / WCV010 |
| UPDCUST | write | src/base/cobol_src/UPDCUST.cbl | UPDATE-CUSTOMER-VSAM SECTION / UCV010 |
| INQCUST | read | src/base/cobol_src/INQCUST.cbl | READ-CUSTOMER-VSAM SECTION / RCV010 |
| DELCUS | read | src/base/cobol_src/DELCUS.cbl | DEL-CUST-VSAM SECTION / DCV010 |
| BANKDATA | consume | src/base/cobol_src/BANKDATA.cbl | FILE SECTION / COPY CUSTOMER L61 |
| CREACC | consume | src/base/cobol_src/CREACC.cbl | LOCAL-STORAGE SECTION / COPY CUSTOMER L144 |
| CRECUST | consume | src/base/cobol_src/CRECUST.cbl | LOCAL-STORAGE SECTION / COPY CUSTOMER L110 |
| DELCUS | consume | src/base/cobol_src/DELCUS.cbl | WORKING-STORAGE SECTION / COPY CUSTOMER L85 |
| INQACCCU | consume | src/base/cobol_src/INQACCCU.cbl | WORKING-STORAGE SECTION / COPY CUSTOMER L155 |
| INQCUST | consume | src/base/cobol_src/INQCUST.cbl | LOCAL-STORAGE SECTION / COPY CUSTOMER L51 |
| UPDCUST | consume | src/base/cobol_src/UPDCUST.cbl | LOCAL-STORAGE SECTION / COPY CUSTOMER L65 |
| IBM Record Generator commarea class CUSTOMER, CUSTOMER_EYECATCHER 4-byte StringField | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/CUSTOMER.java | L37 |
| IBM Record Generator commarea class CUSTOMER, valid-eye-catcher constant `CUSTOMER_EYECATCHER_VALUE = "CUST"` | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/CUSTOMER.java | L41 |

## acctctrl-eye-catcher — ACCOUNT Control-Record Eye-Catcher (`PIC X(4)`) [src/base/cobol_copy/ACCTCTRL.cpy:L8]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| ACCTCTRL.cpy copybook declaration ACCOUNT-CONTROL-EYE-CATCHER; 88 ACCOUNT-CONTROL-EYECATCHER-V VALUE 'CTRL' at L9 | create | src/base/cobol_copy/ACCTCTRL.cpy | L8 |
| BANKDATA initialises the account control record during test-data seeding | create | src/base/cobol_src/BANKDATA.cbl | INITIALISE-ARRAYS SECTION / IA010 |
| CONTDB2.cpy alternate representation as the DB2 STTESTER.CONTROL table declaration with CONTROL_NAME L8, CONTROL_VALUE_NUM L9 and CONTROL_VALUE_STR L10 | create | src/base/cobol_copy/CONTDB2.cpy | L7-L10 |
| CREACC updates the account control record under the named-counter lock | write | src/base/cobol_src/CREACC.cbl | FIND-NEXT-ACCOUNT SECTION / FNA010 |
| DELACC decrements the account population on delete | write | src/base/cobol_src/DELACC.cbl | DEL-ACCOUNT-DB2 SECTION / DADB010 |
| BANKDATA | consume | src/base/cobol_src/BANKDATA.cbl | WORKING-STORAGE SECTION / COPY ACCTCTRL L329 |
| CREACC | consume | src/base/cobol_src/CREACC.cbl | LOCAL-STORAGE SECTION / COPY ACCTCTRL L266 |
| DELACC | consume | src/base/cobol_src/DELACC.cbl | WORKING-STORAGE SECTION / COPY ACCTCTRL L184 |
| BANKDATA DB2 control-table declaration | consume | src/base/cobol_src/BANKDATA.cbl | WORKING-STORAGE SECTION / EXEC SQL INCLUDE CONTDB2 L333 |

## acctctrl-number-of-accounts — ACCOUNT Control-Record Account Population Counter (`PIC 9(8)`) [src/base/cobol_copy/ACCTCTRL.cpy:L14]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| ACCTCTRL.cpy copybook declaration NUMBER-OF-ACCOUNTS | create | src/base/cobol_copy/ACCTCTRL.cpy | L14 |
| BANKDATA initialises the account control record during test-data seeding | create | src/base/cobol_src/BANKDATA.cbl | INITIALISE-ARRAYS SECTION / IA010 |
| CONTDB2.cpy alternate representation as the DB2 STTESTER.CONTROL table declaration with CONTROL_NAME L8, CONTROL_VALUE_NUM L9 and CONTROL_VALUE_STR L10 | create | src/base/cobol_copy/CONTDB2.cpy | L7-L10 |
| CREACC updates the account control record under the named-counter lock | write | src/base/cobol_src/CREACC.cbl | FIND-NEXT-ACCOUNT SECTION / FNA010 |
| DELACC decrements the account population on delete | write | src/base/cobol_src/DELACC.cbl | DEL-ACCOUNT-DB2 SECTION / DADB010 |
| BANKDATA | consume | src/base/cobol_src/BANKDATA.cbl | WORKING-STORAGE SECTION / COPY ACCTCTRL L329 |
| CREACC | consume | src/base/cobol_src/CREACC.cbl | LOCAL-STORAGE SECTION / COPY ACCTCTRL L266 |
| DELACC | consume | src/base/cobol_src/DELACC.cbl | WORKING-STORAGE SECTION / COPY ACCTCTRL L184 |
| BANKDATA DB2 control-table declaration | consume | src/base/cobol_src/BANKDATA.cbl | WORKING-STORAGE SECTION / EXEC SQL INCLUDE CONTDB2 L333 |

## acctctrl-last-account-number — ACCOUNT Control-Record Last Allocated Account Number (`PIC 9(8)`) [src/base/cobol_copy/ACCTCTRL.cpy:L15]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| ACCTCTRL.cpy copybook declaration LAST-ACCOUNT-NUMBER | create | src/base/cobol_copy/ACCTCTRL.cpy | L15 |
| BANKDATA initialises the account control record during test-data seeding | create | src/base/cobol_src/BANKDATA.cbl | INITIALISE-ARRAYS SECTION / IA010 |
| CONTDB2.cpy alternate representation as the DB2 STTESTER.CONTROL table declaration with CONTROL_NAME L8, CONTROL_VALUE_NUM L9 and CONTROL_VALUE_STR L10 | create | src/base/cobol_copy/CONTDB2.cpy | L7-L10 |
| CREACC updates the account control record under the named-counter lock | write | src/base/cobol_src/CREACC.cbl | FIND-NEXT-ACCOUNT SECTION / FNA010 |
| DELACC decrements the account population on delete | write | src/base/cobol_src/DELACC.cbl | DEL-ACCOUNT-DB2 SECTION / DADB010 |
| BANKDATA | consume | src/base/cobol_src/BANKDATA.cbl | WORKING-STORAGE SECTION / COPY ACCTCTRL L329 |
| CREACC | consume | src/base/cobol_src/CREACC.cbl | LOCAL-STORAGE SECTION / COPY ACCTCTRL L266 |
| DELACC | consume | src/base/cobol_src/DELACC.cbl | WORKING-STORAGE SECTION / COPY ACCTCTRL L184 |
| BANKDATA DB2 control-table declaration | consume | src/base/cobol_src/BANKDATA.cbl | WORKING-STORAGE SECTION / EXEC SQL INCLUDE CONTDB2 L333 |

## acctctrl-success-flag — ACCOUNT Control-Record Success Flag ACCOUNT-CONTROL-SUCCESS-FLAG (`PIC X`) [src/base/cobol_copy/ACCTCTRL.cpy:L16]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| ACCTCTRL.cpy copybook declaration ACCOUNT-CONTROL-SUCCESS-FLAG; 88 ACCOUNT-CONTROL-SUCCESS VALUE 'Y' at L17 | create | src/base/cobol_copy/ACCTCTRL.cpy | L16 |
| BANKDATA initialises the account control record during test-data seeding | create | src/base/cobol_src/BANKDATA.cbl | INITIALISE-ARRAYS SECTION / IA010 |
| CONTDB2.cpy alternate representation as the DB2 STTESTER.CONTROL table declaration with CONTROL_NAME L8, CONTROL_VALUE_NUM L9 and CONTROL_VALUE_STR L10 | create | src/base/cobol_copy/CONTDB2.cpy | L7-L10 |
| CREACC updates the account control record under the named-counter lock | write | src/base/cobol_src/CREACC.cbl | FIND-NEXT-ACCOUNT SECTION / FNA010 |
| DELACC decrements the account population on delete | write | src/base/cobol_src/DELACC.cbl | DEL-ACCOUNT-DB2 SECTION / DADB010 |
| BANKDATA | consume | src/base/cobol_src/BANKDATA.cbl | WORKING-STORAGE SECTION / COPY ACCTCTRL L329 |
| CREACC | consume | src/base/cobol_src/CREACC.cbl | LOCAL-STORAGE SECTION / COPY ACCTCTRL L266 |
| DELACC | consume | src/base/cobol_src/DELACC.cbl | WORKING-STORAGE SECTION / COPY ACCTCTRL L184 |
| BANKDATA DB2 control-table declaration | consume | src/base/cobol_src/BANKDATA.cbl | WORKING-STORAGE SECTION / EXEC SQL INCLUDE CONTDB2 L333 |

## acctctrl-fail-code — ACCOUNT Control-Record Fail Code ACCOUNT-CONTROL-FAIL-CODE (`PIC X`) [src/base/cobol_copy/ACCTCTRL.cpy:L18]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| ACCTCTRL.cpy copybook declaration ACCOUNT-CONTROL-FAIL-CODE | create | src/base/cobol_copy/ACCTCTRL.cpy | L18 |
| BANKDATA initialises the account control record during test-data seeding | create | src/base/cobol_src/BANKDATA.cbl | INITIALISE-ARRAYS SECTION / IA010 |
| CONTDB2.cpy alternate representation as the DB2 STTESTER.CONTROL table declaration with CONTROL_NAME L8, CONTROL_VALUE_NUM L9 and CONTROL_VALUE_STR L10 | create | src/base/cobol_copy/CONTDB2.cpy | L7-L10 |
| CREACC updates the account control record under the named-counter lock | write | src/base/cobol_src/CREACC.cbl | FIND-NEXT-ACCOUNT SECTION / FNA010 |
| DELACC decrements the account population on delete | write | src/base/cobol_src/DELACC.cbl | DEL-ACCOUNT-DB2 SECTION / DADB010 |
| BANKDATA | consume | src/base/cobol_src/BANKDATA.cbl | WORKING-STORAGE SECTION / COPY ACCTCTRL L329 |
| CREACC | consume | src/base/cobol_src/CREACC.cbl | LOCAL-STORAGE SECTION / COPY ACCTCTRL L266 |
| DELACC | consume | src/base/cobol_src/DELACC.cbl | WORKING-STORAGE SECTION / COPY ACCTCTRL L184 |
| BANKDATA DB2 control-table declaration | consume | src/base/cobol_src/BANKDATA.cbl | WORKING-STORAGE SECTION / EXEC SQL INCLUDE CONTDB2 L333 |

## custctrl-eye-catcher — CUSTOMER Control-Record Eye-Catcher (`PIC X(4)`) [src/base/cobol_copy/CUSTCTRL.cpy:L8]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| CUSTCTRL.cpy copybook declaration CUSTOMER-CONTROL-EYECATCHER; 88 CUSTOMER-CONTROL-EYECATCHER-V VALUE 'CTRL' at L9 | create | src/base/cobol_copy/CUSTCTRL.cpy | L8 |
| BANKDATA initialises the customer control record during test-data seeding | create | src/base/cobol_src/BANKDATA.cbl | INITIALISE-ARRAYS SECTION / IA010 |
| CONTDB2.cpy alternate representation as the DB2 STTESTER.CONTROL table declaration with CONTROL_NAME L8, CONTROL_VALUE_NUM L9 and CONTROL_VALUE_STR L10 | create | src/base/cobol_copy/CONTDB2.cpy | L7-L10 |
| CRECUST updates the customer control record as part of customer creation | write | src/base/cobol_src/CRECUST.cbl | WRITE-CUSTOMER-VSAM SECTION / WCV010 |
| CRECUST reads the customer control record to obtain the last allocated number | read | src/base/cobol_src/CRECUST.cbl | GET-LAST-CUSTOMER-VSAM SECTION / GLCV010 |
| BANKDATA | consume | src/base/cobol_src/BANKDATA.cbl | WORKING-STORAGE SECTION / COPY CUSTCTRL L326 |
| CRECUST | consume | src/base/cobol_src/CRECUST.cbl | LOCAL-STORAGE SECTION / COPY CUSTCTRL L345 |
| BANKDATA DB2 control-table declaration | consume | src/base/cobol_src/BANKDATA.cbl | WORKING-STORAGE SECTION / EXEC SQL INCLUDE CONTDB2 L333 |
| IBM Record Generator commarea class CustomerControl, CUSTOMER_CONTROL_EYECATCHER StringField with the constant `CUSTOMER_CONTROL_EYECATCHER_V = "CTRL"` at L61 | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/CustomerControl.java | L53-L61 |

## custctrl-number-of-customers — CUSTOMER Control-Record Customer Population Counter (`PIC 9(10) DISPLAY`) [src/base/cobol_copy/CUSTCTRL.cpy:L13]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| CUSTCTRL.cpy copybook declaration NUMBER-OF-CUSTOMERS | create | src/base/cobol_copy/CUSTCTRL.cpy | L13 |
| BANKDATA initialises the customer control record during test-data seeding | create | src/base/cobol_src/BANKDATA.cbl | INITIALISE-ARRAYS SECTION / IA010 |
| CONTDB2.cpy alternate representation as the DB2 STTESTER.CONTROL table declaration with CONTROL_NAME L8, CONTROL_VALUE_NUM L9 and CONTROL_VALUE_STR L10 | create | src/base/cobol_copy/CONTDB2.cpy | L7-L10 |
| CRECUST updates the customer control record as part of customer creation | write | src/base/cobol_src/CRECUST.cbl | WRITE-CUSTOMER-VSAM SECTION / WCV010 |
| CRECUST reads the customer control record to obtain the last allocated number | read | src/base/cobol_src/CRECUST.cbl | GET-LAST-CUSTOMER-VSAM SECTION / GLCV010 |
| BANKDATA | consume | src/base/cobol_src/BANKDATA.cbl | WORKING-STORAGE SECTION / COPY CUSTCTRL L326 |
| CRECUST | consume | src/base/cobol_src/CRECUST.cbl | LOCAL-STORAGE SECTION / COPY CUSTCTRL L345 |
| BANKDATA DB2 control-table declaration | consume | src/base/cobol_src/BANKDATA.cbl | WORKING-STORAGE SECTION / EXEC SQL INCLUDE CONTDB2 L333 |
| IBM Record Generator commarea class CustomerControl, NUMBER_OF_CUSTOMERS ExternalDecimalAsLongField | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/CustomerControl.java | L93 |

## custctrl-last-customer-number — CUSTOMER Control-Record Last Allocated Customer Number (`PIC 9(10) DISPLAY`) [src/base/cobol_copy/CUSTCTRL.cpy:L14]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| CUSTCTRL.cpy copybook declaration LAST-CUSTOMER-NUMBER | create | src/base/cobol_copy/CUSTCTRL.cpy | L14 |
| BANKDATA initialises the customer control record during test-data seeding | create | src/base/cobol_src/BANKDATA.cbl | INITIALISE-ARRAYS SECTION / IA010 |
| CONTDB2.cpy alternate representation as the DB2 STTESTER.CONTROL table declaration with CONTROL_NAME L8, CONTROL_VALUE_NUM L9 and CONTROL_VALUE_STR L10 | create | src/base/cobol_copy/CONTDB2.cpy | L7-L10 |
| CRECUST updates the customer control record as part of customer creation | write | src/base/cobol_src/CRECUST.cbl | WRITE-CUSTOMER-VSAM SECTION / WCV010 |
| CRECUST reads the customer control record to obtain the last allocated number | read | src/base/cobol_src/CRECUST.cbl | GET-LAST-CUSTOMER-VSAM SECTION / GLCV010 |
| BANKDATA | consume | src/base/cobol_src/BANKDATA.cbl | WORKING-STORAGE SECTION / COPY CUSTCTRL L326 |
| CRECUST | consume | src/base/cobol_src/CRECUST.cbl | LOCAL-STORAGE SECTION / COPY CUSTCTRL L345 |
| BANKDATA DB2 control-table declaration | consume | src/base/cobol_src/BANKDATA.cbl | WORKING-STORAGE SECTION / EXEC SQL INCLUDE CONTDB2 L333 |
| IBM Record Generator commarea class CustomerControl, LAST_CUSTOMER_NUMBER ExternalDecimalAsLongField | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/CustomerControl.java | L101 |

## custctrl-success-flag — CUSTOMER Control-Record Success Flag CUSTOMER-CONTROL-SUCCESS-FLAG (`PIC X`) [src/base/cobol_copy/CUSTCTRL.cpy:L15]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| CUSTCTRL.cpy copybook declaration CUSTOMER-CONTROL-SUCCESS-FLAG; 88 CUSTOMER-CONTROL-SUCCESS VALUE 'Y' at L16 | create | src/base/cobol_copy/CUSTCTRL.cpy | L15 |
| BANKDATA initialises the customer control record during test-data seeding | create | src/base/cobol_src/BANKDATA.cbl | INITIALISE-ARRAYS SECTION / IA010 |
| CONTDB2.cpy alternate representation as the DB2 STTESTER.CONTROL table declaration with CONTROL_NAME L8, CONTROL_VALUE_NUM L9 and CONTROL_VALUE_STR L10 | create | src/base/cobol_copy/CONTDB2.cpy | L7-L10 |
| CRECUST updates the customer control record as part of customer creation | write | src/base/cobol_src/CRECUST.cbl | WRITE-CUSTOMER-VSAM SECTION / WCV010 |
| CRECUST reads the customer control record to obtain the last allocated number | read | src/base/cobol_src/CRECUST.cbl | GET-LAST-CUSTOMER-VSAM SECTION / GLCV010 |
| BANKDATA | consume | src/base/cobol_src/BANKDATA.cbl | WORKING-STORAGE SECTION / COPY CUSTCTRL L326 |
| CRECUST | consume | src/base/cobol_src/CRECUST.cbl | LOCAL-STORAGE SECTION / COPY CUSTCTRL L345 |
| BANKDATA DB2 control-table declaration | consume | src/base/cobol_src/BANKDATA.cbl | WORKING-STORAGE SECTION / EXEC SQL INCLUDE CONTDB2 L333 |
| IBM Record Generator commarea class CustomerControl, CUSTOMER_CONTROL_SUCCESS_FLAG StringField with the constant `CUSTOMER_CONTROL_SUCCESS = "Y"` at L117 | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/CustomerControl.java | L109-L117 |

## custctrl-fail-code — CUSTOMER Control-Record Fail Code CUSTOMER-CONTROL-FAIL-CODE (`PIC X`) [src/base/cobol_copy/CUSTCTRL.cpy:L17]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| CUSTCTRL.cpy copybook declaration CUSTOMER-CONTROL-FAIL-CODE | create | src/base/cobol_copy/CUSTCTRL.cpy | L17 |
| BANKDATA initialises the customer control record during test-data seeding | create | src/base/cobol_src/BANKDATA.cbl | INITIALISE-ARRAYS SECTION / IA010 |
| CONTDB2.cpy alternate representation as the DB2 STTESTER.CONTROL table declaration with CONTROL_NAME L8, CONTROL_VALUE_NUM L9 and CONTROL_VALUE_STR L10 | create | src/base/cobol_copy/CONTDB2.cpy | L7-L10 |
| CRECUST updates the customer control record as part of customer creation | write | src/base/cobol_src/CRECUST.cbl | WRITE-CUSTOMER-VSAM SECTION / WCV010 |
| CRECUST reads the customer control record to obtain the last allocated number | read | src/base/cobol_src/CRECUST.cbl | GET-LAST-CUSTOMER-VSAM SECTION / GLCV010 |
| BANKDATA | consume | src/base/cobol_src/BANKDATA.cbl | WORKING-STORAGE SECTION / COPY CUSTCTRL L326 |
| CRECUST | consume | src/base/cobol_src/CRECUST.cbl | LOCAL-STORAGE SECTION / COPY CUSTCTRL L345 |
| BANKDATA DB2 control-table declaration | consume | src/base/cobol_src/BANKDATA.cbl | WORKING-STORAGE SECTION / EXEC SQL INCLUDE CONTDB2 L333 |
| IBM Record Generator commarea class CustomerControl, CUSTOMER_CONTROL_FAIL_CODE StringField | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/CustomerControl.java | L124 |

## controli-control-counters — CONTROLI Packed-Decimal Control Counter Quartet (`PIC 9(8) PACKED-DECIMAL`) [src/base/cobol_copy/CONTROLI.cpy:L7-L10]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| CONTROLI.cpy copybook declaration CONTROL-CUSTOMER-COUNT L7, CONTROL-CUSTOMER-LAST L8, CONTROL-ACCOUNT-COUNT L9 and CONTROL-ACCOUNT-LAST L10 | create | src/base/cobol_copy/CONTROLI.cpy | L7-L10 |
| ACCTCTRL.cpy alternate representation the in-repository account counter pair NUMBER-OF-ACCOUNTS and LAST-ACCOUNT-NUMBER that CONTROLI mirrors | create | src/base/cobol_copy/ACCTCTRL.cpy | L14-L15 |
| CUSTCTRL.cpy alternate representation the in-repository customer counter pair NUMBER-OF-CUSTOMERS and LAST-CUSTOMER-NUMBER that CONTROLI mirrors | create | src/base/cobol_copy/CUSTCTRL.cpy | L13-L14 |
| CONTDB2.cpy alternate representation as the DB2 STTESTER.CONTROL table declaration | create | src/base/cobol_copy/CONTDB2.cpy | L7-L10 |
| (none in repository) | write | src/base/cobol_copy/CONTROLI.cpy | no COPY/INCLUDE site; exhaustive repo grep |
| (none in repository) | read | src/base/cobol_copy/CONTROLI.cpy | no COPY/INCLUDE site; exhaustive repo grep |
| (none in repository) | consume | src/base/cobol_copy/CONTROLI.cpy | no COPY/INCLUDE site; exhaustive repo grep |
| BANKDATA DB2 control-table declaration | consume | src/base/cobol_src/BANKDATA.cbl | WORKING-STORAGE SECTION / EXEC SQL INCLUDE CONTDB2 L333 |

## newaccno-success-flag — NEWACCNO Allocator Success Flag NEWACCNO-SUCCESS (`PIC X`) [src/base/cobol_copy/NEWACCNO.cpy:L12]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| NEWACCNO.cpy copybook declaration NEWACCNO-SUCCESS | create | src/base/cobol_copy/NEWACCNO.cpy | L12 |
| CREACC resolved in-repository origin: serialises account-number allocation with EXEC CICS ENQ | create | src/base/cobol_src/CREACC.cbl | ENQ-NAMED-COUNTER SECTION / ENC010 |
| CREACC resolved in-repository origin: allocates from the ACCTCTRL control row | create | src/base/cobol_src/CREACC.cbl | FIND-NEXT-ACCOUNT SECTION / FNA010 |
| (none in repository) | write | src/base/cobol_src/ | NEWACCNO.cbl absent from src/base/cobol_src/; the copybook is a COMMAREA contract for an allocator delivered outside this repository |
| (none in repository) | read | src/base/cobol_src/ | no COBOL program references NEWACCNO-SUCCESS; exhaustive repo grep over all 29 programs |
| IBM Record Generator commarea class NewAccountNumber, NEWACCNO_SUCCESS 1-byte StringField | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/NewAccountNumber.java | L43 |
| IBM Record Generator commarea class NewAccountNumber, NEWACCNO_SUCCESS accessors | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/NewAccountNumber.java | L117-L141 |

## newaccno-fail-code — NEWACCNO Allocator Fail Code NEWACCNO-FAIL-CODE (`PIC X`) [src/base/cobol_copy/NEWACCNO.cpy:L13]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| NEWACCNO.cpy copybook declaration NEWACCNO-FAIL-CODE | create | src/base/cobol_copy/NEWACCNO.cpy | L13 |
| CREACC resolved in-repository origin: serialises account-number allocation with EXEC CICS ENQ | create | src/base/cobol_src/CREACC.cbl | ENQ-NAMED-COUNTER SECTION / ENC010 |
| CREACC resolved in-repository origin: allocates from the ACCTCTRL control row | create | src/base/cobol_src/CREACC.cbl | FIND-NEXT-ACCOUNT SECTION / FNA010 |
| (none in repository) | write | src/base/cobol_src/ | NEWACCNO.cbl absent from src/base/cobol_src/; the copybook is a COMMAREA contract for an allocator delivered outside this repository |
| (none in repository) | read | src/base/cobol_src/ | no COBOL program references NEWACCNO-FAIL-CODE; exhaustive repo grep over all 29 programs |
| IBM Record Generator commarea class NewAccountNumber, NEWACCNO_FAIL_CODE 1-byte StringField | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/NewAccountNumber.java | L47 |
| IBM Record Generator commarea class NewAccountNumber, NEWACCNO_FAIL_CODE accessors | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/NewAccountNumber.java | L117-L141 |

## newcusno-success-flag — NEWCUSNO Allocator Success Flag NEWCUSNO-SUCCESS (`PIC X`) [src/base/cobol_copy/NEWCUSNO.cpy:L12]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| NEWCUSNO.cpy copybook declaration NEWCUSNO-SUCCESS | create | src/base/cobol_copy/NEWCUSNO.cpy | L12 |
| CRECUST resolved in-repository origin: serialises customer-number allocation with EXEC CICS ENQ | create | src/base/cobol_src/CRECUST.cbl | ENQ-NAMED-COUNTER SECTION / ENC010 |
| CRECUST resolved in-repository origin: updates the named-counter server value | create | src/base/cobol_src/CRECUST.cbl | UPD-NCS SECTION / UN010 |
| (none in repository) | write | src/base/cobol_src/ | NEWCUSNO.cbl absent from src/base/cobol_src/; the copybook is a COMMAREA contract for an allocator delivered outside this repository |
| (none in repository) | read | src/base/cobol_src/ | no COBOL program references NEWCUSNO-SUCCESS; exhaustive repo grep over all 29 programs |
| IBM Record Generator commarea class NewCustomerNumber, NEWCUSNO_SUCCESS 1-byte StringField | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/NewCustomerNumber.java | L43 |
| IBM Record Generator commarea class NewCustomerNumber, NEWCUSNO_SUCCESS accessors | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/NewCustomerNumber.java | L117 |

## newcusno-fail-code — NEWCUSNO Allocator Fail Code NEWCUSNO-FAIL-CODE (`PIC X`) [src/base/cobol_copy/NEWCUSNO.cpy:L13]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| NEWCUSNO.cpy copybook declaration NEWCUSNO-FAIL-CODE | create | src/base/cobol_copy/NEWCUSNO.cpy | L13 |
| CRECUST resolved in-repository origin: serialises customer-number allocation with EXEC CICS ENQ | create | src/base/cobol_src/CRECUST.cbl | ENQ-NAMED-COUNTER SECTION / ENC010 |
| CRECUST resolved in-repository origin: updates the named-counter server value | create | src/base/cobol_src/CRECUST.cbl | UPD-NCS SECTION / UN010 |
| (none in repository) | write | src/base/cobol_src/ | NEWCUSNO.cbl absent from src/base/cobol_src/; the copybook is a COMMAREA contract for an allocator delivered outside this repository |
| (none in repository) | read | src/base/cobol_src/ | no COBOL program references NEWCUSNO-FAIL-CODE; exhaustive repo grep over all 29 programs |
| IBM Record Generator commarea class NewCustomerNumber, NEWCUSNO_FAIL_CODE 1-byte StringField | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/NewCustomerNumber.java | L47 |
| IBM Record Generator commarea class NewCustomerNumber, NEWCUSNO_FAIL_CODE accessors | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/NewCustomerNumber.java | L117 |

## creacc-success-flag — CREACC COMMAREA Success Flag COMM-SUCCESS (`PIC X`) [src/base/cobol_copy/CREACC.cpy:L32]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| CREACC.cpy copybook declaration COMM-SUCCESS | create | src/base/cobol_copy/CREACC.cpy | L32 |
| CREACC | write | src/base/cobol_src/CREACC.cbl | PREMIERE SECTION / P010 |
| CREACC | write | src/base/cobol_src/CREACC.cbl | ENQ-NAMED-COUNTER SECTION / ENC010 |
| CREACC | write | src/base/cobol_src/CREACC.cbl | DEQ-NAMED-COUNTER SECTION / DNC010 |
| CREACC | write | src/base/cobol_src/CREACC.cbl | WRITE-ACCOUNT-DB2 SECTION / WAD010 |
| CREACC | write | src/base/cobol_src/CREACC.cbl | ACCOUNT-TYPE-CHECK SECTION / ATC010 |
| CREACC | consume | src/base/cobol_src/CREACC.cbl | LINKAGE SECTION / COPY CREACC L283 |
| z/OS Connect service CSacccre interface binding | consume | src/zosconnect_artefacts/services/CSacccre/service-interfaces/CREACC.si | L6 |
| z/OS Connect API postCSacccre (POST /creacc/insert) | consume | src/zosconnect_artefacts/apis/creacc/api-docs/swagger.json | POST /creacc/insert |
| Customer Services Interface CreaccJson wire field CommSuccess | consume | src/Z-OS-Connect-Customer-Services-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/customerservices/jsonclasses/createaccount/CreaccJson.java | L49-L50 |
| Customer Services Interface CreaccJson success and fail accessors | consume | src/Z-OS-Connect-Customer-Services-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/customerservices/jsonclasses/createaccount/CreaccJson.java | L209-L229 |
| Customer Services Interface WebController create-account success guard | consume | src/Z-OS-Connect-Customer-Services-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/customerservices/controllers/WebController.java | L461 |
| webui JAX-RS POST /webui-1.0/banking/account | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/AccountsResource.java | L159 |

## creacc-fail-code — CREACC COMMAREA Fail Code COMM-FAIL-CODE (`PIC X`) [src/base/cobol_copy/CREACC.cpy:L33]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| CREACC.cpy copybook declaration COMM-FAIL-CODE | create | src/base/cobol_copy/CREACC.cpy | L33 |
| CREACC | write | src/base/cobol_src/CREACC.cbl | PREMIERE SECTION / P010 |
| CREACC | write | src/base/cobol_src/CREACC.cbl | ENQ-NAMED-COUNTER SECTION / ENC010 |
| CREACC | write | src/base/cobol_src/CREACC.cbl | DEQ-NAMED-COUNTER SECTION / DNC010 |
| CREACC | write | src/base/cobol_src/CREACC.cbl | WRITE-ACCOUNT-DB2 SECTION / WAD010 |
| CREACC | write | src/base/cobol_src/CREACC.cbl | ACCOUNT-TYPE-CHECK SECTION / ATC010 |
| CREACC sets fail code '8' when the customer already holds more than nine accounts | write | src/base/cobol_src/CREACC.cbl | FIND-NEXT-ACCOUNT SECTION / FNA010 |
| CREACC sets fail code 'A' when the requested product type is outside the allowed set | write | src/base/cobol_src/CREACC.cbl | ACCOUNT-TYPE-CHECK SECTION / ATC010 |
| CREACC | consume | src/base/cobol_src/CREACC.cbl | LINKAGE SECTION / COPY CREACC L283 |
| z/OS Connect service CSacccre interface binding | consume | src/zosconnect_artefacts/services/CSacccre/service-interfaces/CREACC.si | L6 |
| z/OS Connect API postCSacccre (POST /creacc/insert) | consume | src/zosconnect_artefacts/apis/creacc/api-docs/swagger.json | POST /creacc/insert |
| Customer Services Interface CreaccJson defect: the fail code is typed as `private String commFailCode;` here while InqAccczJson types the same concept as an int | consume | src/Z-OS-Connect-Customer-Services-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/customerservices/jsonclasses/createaccount/CreaccJson.java | L52-L53 |
| Customer Services Interface WebController maps CREACC fail code '1' to a customer-not-found condition | consume | src/Z-OS-Connect-Customer-Services-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/customerservices/controllers/WebController.java | L463 |
| Customer Services Interface WebController maps CREACC fail code '8' to a too-many-accounts condition, corroborating that CREACC uses '8' and not '2' | consume | src/Z-OS-Connect-Customer-Services-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/customerservices/controllers/WebController.java | L467 |
| Customer Services Interface WebController maps CREACC fail code 'A' to an invalid-account-type condition, corroborating that CREACC uses 'A' and not '4'; fail-code semantics are per-program and no global fail-code table exists | consume | src/Z-OS-Connect-Customer-Services-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/customerservices/controllers/WebController.java | L472 |

## xfrfun-success-flag — XFRFUN COMMAREA Success Flag COMM-SUCCESS (`PIC X`) [src/base/cobol_copy/XFRFUN.cpy:L17]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| XFRFUN.cpy copybook declaration COMM-SUCCESS, declared after the fail code at L16 | create | src/base/cobol_copy/XFRFUN.cpy | L17 |
| XFRFUN | write | src/base/cobol_src/XFRFUN.cbl | PREMIERE SECTION / A010 |
| XFRFUN | write | src/base/cobol_src/XFRFUN.cbl | UPDATE-ACCOUNT-DB2 SECTION / UAD010 |
| XFRFUN | write | src/base/cobol_src/XFRFUN.cbl | UPDATE-ACCOUNT-DB2-FROM SECTION / UADF010 |
| XFRFUN | write | src/base/cobol_src/XFRFUN.cbl | UPDATE-ACCOUNT-DB2-TO SECTION / UADT010 |
| XFRFUN | write | src/base/cobol_src/XFRFUN.cbl | ABEND-HANDLING SECTION / AH010 |
| XFRFUN | consume | src/base/cobol_src/XFRFUN.cbl | LINKAGE SECTION / COPY XFRFUN L265 |
| webui JAX-RS PUT /webui-1.0/banking/account/transfer/{id} | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/AccountsResource.java | L922-L923 |
| webui JSON DTO TransferLocalJSON wire contract for the transfer operation | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/TransferLocalJSON.java | L14 |
| CBSA RESTful Interface Reference documents the transfer endpoint | consume | etc/usage/carbonReactUI/doc/CBSA_RESTful_Interface_Reference.md | #### Account / ###### Transfer between accounts at this bank |

## xfrfun-fail-code — XFRFUN COMMAREA Fail Code COMM-FAIL-CODE Declared Ahead of the Success Flag (`PIC X`) [src/base/cobol_copy/XFRFUN.cpy:L16]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| XFRFUN.cpy copybook declaration COMM-FAIL-CODE, declared BEFORE the success flag at L17, reversing the field order used by every other COMMAREA copybook | create | src/base/cobol_copy/XFRFUN.cpy | L16 |
| XFRFUN | write | src/base/cobol_src/XFRFUN.cbl | PREMIERE SECTION / A010 |
| XFRFUN | write | src/base/cobol_src/XFRFUN.cbl | UPDATE-ACCOUNT-DB2 SECTION / UAD010 |
| XFRFUN | write | src/base/cobol_src/XFRFUN.cbl | UPDATE-ACCOUNT-DB2-FROM SECTION / UADF010 |
| XFRFUN | write | src/base/cobol_src/XFRFUN.cbl | UPDATE-ACCOUNT-DB2-TO SECTION / UADT010 |
| XFRFUN | write | src/base/cobol_src/XFRFUN.cbl | ABEND-HANDLING SECTION / AH010 |
| XFRFUN validates the amount, guards against a same-account transfer and orders the two account locks | write | src/base/cobol_src/XFRFUN.cbl | UPDATE-ACCOUNT-DB2 SECTION / UAD010 |
| XFRFUN | consume | src/base/cobol_src/XFRFUN.cbl | LINKAGE SECTION / COPY XFRFUN L265 |
| webui JAX-RS PUT /webui-1.0/banking/account/transfer/{id} | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/AccountsResource.java | L922-L923 |

## inqacc-success-flag — INQACC COMMAREA Success Flag INQACC-SUCCESS With No Companion Fail-Code Field (`PIC X`) [src/base/cobol_copy/INQACC.cpy:L32]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| INQACC.cpy copybook declaration INQACC-SUCCESS; INQACC.cpy declares NO companion fail-code field, verified by exhaustive search of the copybook | create | src/base/cobol_copy/INQACC.cpy | L32 |
| INQACCZ.cpy alternate representation as the z/OS Connect INQACC-SUCCESS variant, identical except for the PCB pointer at L33 being PIC X(4) rather than POINTER | create | src/base/cobol_copy/INQACCZ.cpy | L32 |
| INQACC | write | src/base/cobol_src/INQACC.cbl | PREMIERE SECTION / A010 |
| INQACC | write | src/base/cobol_src/INQACC.cbl | ABEND-HANDLING SECTION / AH010 |
| INQACC | read | src/base/cobol_src/INQACC.cbl | READ-ACCOUNT-DB2 SECTION / RAD010 |
| INQACC sentinel account-number path | read | src/base/cobol_src/INQACC.cbl | GET-LAST-ACCOUNT-DB2 SECTION / GLAD010 |
| INQACC | consume | src/base/cobol_src/INQACC.cbl | LINKAGE SECTION / COPY INQACC L199 |
| BNK1DAC | consume | src/base/cobol_src/BNK1DAC.cbl | WORKING-STORAGE SECTION / COPY INQACC L106 |
| z/OS Connect service CSaccenq interface binding | consume | src/zosconnect_artefacts/services/CSaccenq/service-interfaces/INQACCZ.si | L6 |
| z/OS Connect API getCSaccenq (GET /inqaccz/enquiry/{accno}) | consume | src/zosconnect_artefacts/apis/inqaccz/api-docs/swagger.json | GET /inqaccz/enquiry/{accno} |
| webui JAX-RS GET /webui-1.0/banking/account/{accountNumber} | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/AccountsResource.java | L362-L363 |
| Customer Services Interface account-enquiry wire package consumes the INQACCZ contract | consume | src/Z-OS-Connect-Customer-Services-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/customerservices/jsonclasses/accountenquiry/InqaccJson.java | L11 |

## inqcust-inq-success-flag — INQCUST COMMAREA Inquiry Success Flag INQCUST-INQ-SUCCESS (`PIC X`) [src/base/cobol_copy/INQCUST.cpy:L21]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| INQCUST.cpy copybook declaration INQCUST-INQ-SUCCESS | create | src/base/cobol_copy/INQCUST.cpy | L21 |
| INQCUSTZ.cpy alternate representation as the z/OS Connect INQCUST-INQ-SUCCESS variant | create | src/base/cobol_copy/INQCUSTZ.cpy | L20 |
| INQCUST | write | src/base/cobol_src/INQCUST.cbl | PREMIERE SECTION / P010 |
| INQCUST | write | src/base/cobol_src/INQCUST.cbl | READ-CUSTOMER-NCS SECTION / RCN010 |
| INQCUST | write | src/base/cobol_src/INQCUST.cbl | READ-CUSTOMER-VSAM SECTION / RCV010 |
| INQCUST | write | src/base/cobol_src/INQCUST.cbl | ABEND-HANDLING SECTION / AH010 |
| INQCUST | write | src/base/cobol_src/INQCUST.cbl | GET-LAST-CUSTOMER-VSAM SECTION / GLCV010 |
| INQCUST | consume | src/base/cobol_src/INQCUST.cbl | LINKAGE SECTION / COPY INQCUST L162 |
| BNK1DCS | consume | src/base/cobol_src/BNK1DCS.cbl | WORKING-STORAGE SECTION / COPY INQCUST L115 |
| CREACC | consume | src/base/cobol_src/CREACC.cbl | LOCAL-STORAGE SECTION / COPY INQCUST L257 |
| DELCUS | consume | src/base/cobol_src/DELCUS.cbl | WORKING-STORAGE SECTION / COPY INQCUST L226 |
| INQACCCU | consume | src/base/cobol_src/INQACCCU.cbl | WORKING-STORAGE SECTION / COPY INQCUST L158 |
| z/OS Connect service CScustenq interface binding | consume | src/zosconnect_artefacts/services/CScustenq/service-interfaces/INQCUSTZ.si | L6 |
| z/OS Connect API getCScustenq (GET /inqcustz/enquiry/{custno}) | consume | src/zosconnect_artefacts/apis/inqcustz/api-docs/swagger.json | GET /inqcustz/enquiry/{custno} |
| Customer Services Interface CustomerEnquiryJson names the INQCUSTZ commarea contract | consume | src/Z-OS-Connect-Customer-Services-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/customerservices/jsonclasses/customerenquiry/CustomerEnquiryJson.java | L13 |
| webui JAX-RS GET /webui-1.0/banking/customer/{id} | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/CustomerResource.java | L495-L496 |

## inqcust-inq-fail-code — INQCUST COMMAREA Inquiry Fail Code INQCUST-INQ-FAIL-CD (`PIC X`) [src/base/cobol_copy/INQCUST.cpy:L22]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| INQCUST.cpy copybook declaration INQCUST-INQ-FAIL-CD | create | src/base/cobol_copy/INQCUST.cpy | L22 |
| INQCUSTZ.cpy alternate representation as the z/OS Connect INQCUST-INQ-FAIL-CD variant | create | src/base/cobol_copy/INQCUSTZ.cpy | L21 |
| INQCUST | write | src/base/cobol_src/INQCUST.cbl | PREMIERE SECTION / P010 |
| INQCUST | write | src/base/cobol_src/INQCUST.cbl | READ-CUSTOMER-VSAM SECTION / RCV010 |
| INQCUST | write | src/base/cobol_src/INQCUST.cbl | ABEND-HANDLING SECTION / AH010 |
| INQCUST | write | src/base/cobol_src/INQCUST.cbl | GET-LAST-CUSTOMER-VSAM SECTION / GLCV010 |
| INQCUST | consume | src/base/cobol_src/INQCUST.cbl | LINKAGE SECTION / COPY INQCUST L162 |
| BNK1DCS | consume | src/base/cobol_src/BNK1DCS.cbl | WORKING-STORAGE SECTION / COPY INQCUST L115 |
| z/OS Connect service CScustenq interface binding | consume | src/zosconnect_artefacts/services/CScustenq/service-interfaces/INQCUSTZ.si | L6 |
| z/OS Connect API getCScustenq (GET /inqcustz/enquiry/{custno}) | consume | src/zosconnect_artefacts/apis/inqcustz/api-docs/swagger.json | GET /inqcustz/enquiry/{custno} |

## inqacccu-success-flag — INQACCCU COMMAREA Success Flag COMM-SUCCESS (`PIC X`) [src/base/cobol_copy/INQACCCU.cpy:L9]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| INQACCCU.cpy copybook declaration COMM-SUCCESS | create | src/base/cobol_copy/INQACCCU.cpy | L9 |
| INQACCCZ.cpy alternate representation as the z/OS Connect list-accounts variant COMM-SUCCESS | create | src/base/cobol_copy/INQACCCZ.cpy | L9 |
| DELACCZ.cpy alternate representation as COMM-SUCCESS in DELACCZ.cpy, which is shape-identical to INQACCCZ.cpy; this is a defect, because the identically named DELACCZ.si correctly describes the 122-byte single-account DELACC delete COMMAREA instead | create | src/base/cobol_copy/DELACCZ.cpy | L9 |
| INQACCCU | write | src/base/cobol_src/INQACCCU.cbl | PREMIERE SECTION / A010 |
| INQACCCU | write | src/base/cobol_src/INQACCCU.cbl | READ-ACCOUNT-DB2 SECTION / RAD010 |
| INQACCCU | write | src/base/cobol_src/INQACCCU.cbl | FETCH-DATA SECTION / FD010 |
| INQACCCU | write | src/base/cobol_src/INQACCCU.cbl | ABEND-HANDLING SECTION / AH010 |
| BNK1CCA | consume | src/base/cobol_src/BNK1CCA.cbl | WORKING-STORAGE SECTION / COPY INQACCCU L98 |
| CREACC | consume | src/base/cobol_src/CREACC.cbl | LOCAL-STORAGE SECTION / COPY INQACCCU L260 |
| DELCUS | consume | src/base/cobol_src/DELCUS.cbl | WORKING-STORAGE SECTION / COPY INQACCCU L221 |
| INQACCCU | consume | src/base/cobol_src/INQACCCU.cbl | LINKAGE SECTION / COPY INQACCCU L191 |
| z/OS Connect service CScustacc interface binding | consume | src/zosconnect_artefacts/services/CScustacc/service-interfaces/INQACCCZ.si | L6 |
| z/OS Connect service CSaccdel interface binding whose 122-byte single-account layout at L7-L9 contradicts the list shape of DELACCZ.cpy | consume | src/zosconnect_artefacts/services/CSaccdel/service-interfaces/DELACCZ.si | L7 |
| z/OS Connect service CSaccdel binds executableName DELACC with requestSIName and responseSIName both DELACCZ.si, confirming the service expects the single-account layout | consume | src/zosconnect_artefacts/services/CSaccdel/service.properties | L8 |
| z/OS Connect API getCScustacc (GET /inqacccz/list/{custno}) | consume | src/zosconnect_artefacts/apis/inqacccz/api-docs/swagger.json | GET /inqacccz/list/{custno} |
| z/OS Connect API deleteCSaccdel (DELETE /delacc/remove/{accno}) | consume | src/zosconnect_artefacts/apis/delacc/api-docs/swagger.json | DELETE /delacc/remove/{accno} |
| Customer Services Interface ListAccJson names the INQACCCZ commarea contract | consume | src/Z-OS-Connect-Customer-Services-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/customerservices/jsonclasses/listaccounts/ListAccJson.java | L11 |
| webui JAX-RS GET /webui-1.0/banking/account/retrieveByCustomerNumber/{customerNumber} | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/AccountsResource.java | L471-L472 |
| Customer Services Interface InqAccczJson wire field CommSuccess | consume | src/Z-OS-Connect-Customer-Services-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/customerservices/jsonclasses/listaccounts/InqAccczJson.java | L33-L34 |

## inqacccu-fail-code — INQACCCU COMMAREA Fail Code COMM-FAIL-CODE (`PIC X`) [src/base/cobol_copy/INQACCCU.cpy:L10]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| INQACCCU.cpy copybook declaration COMM-FAIL-CODE | create | src/base/cobol_copy/INQACCCU.cpy | L10 |
| INQACCCZ.cpy alternate representation as the z/OS Connect list-accounts variant COMM-FAIL-CODE | create | src/base/cobol_copy/INQACCCZ.cpy | L10 |
| DELACCZ.cpy alternate representation as COMM-FAIL-CODE in DELACCZ.cpy, which is shape-identical to INQACCCZ.cpy; this is a defect, because the identically named DELACCZ.si correctly describes the 122-byte single-account DELACC delete COMMAREA instead | create | src/base/cobol_copy/DELACCZ.cpy | L10 |
| INQACCCU | write | src/base/cobol_src/INQACCCU.cbl | PREMIERE SECTION / A010 |
| INQACCCU | write | src/base/cobol_src/INQACCCU.cbl | READ-ACCOUNT-DB2 SECTION / RAD010 |
| INQACCCU | write | src/base/cobol_src/INQACCCU.cbl | FETCH-DATA SECTION / FD010 |
| BNK1CCA | consume | src/base/cobol_src/BNK1CCA.cbl | WORKING-STORAGE SECTION / COPY INQACCCU L98 |
| CREACC | consume | src/base/cobol_src/CREACC.cbl | LOCAL-STORAGE SECTION / COPY INQACCCU L260 |
| DELCUS | consume | src/base/cobol_src/DELCUS.cbl | WORKING-STORAGE SECTION / COPY INQACCCU L221 |
| INQACCCU | consume | src/base/cobol_src/INQACCCU.cbl | LINKAGE SECTION / COPY INQACCCU L191 |
| z/OS Connect service CScustacc interface binding | consume | src/zosconnect_artefacts/services/CScustacc/service-interfaces/INQACCCZ.si | L6 |
| z/OS Connect service CSaccdel interface binding whose 122-byte single-account layout at L7-L9 contradicts the list shape of DELACCZ.cpy | consume | src/zosconnect_artefacts/services/CSaccdel/service-interfaces/DELACCZ.si | L7 |
| z/OS Connect service CSaccdel binds executableName DELACC with requestSIName and responseSIName both DELACCZ.si, confirming the service expects the single-account layout | consume | src/zosconnect_artefacts/services/CSaccdel/service.properties | L8 |
| z/OS Connect API getCScustacc (GET /inqacccz/list/{custno}) | consume | src/zosconnect_artefacts/apis/inqacccz/api-docs/swagger.json | GET /inqacccz/list/{custno} |
| z/OS Connect API deleteCSaccdel (DELETE /delacc/remove/{accno}) | consume | src/zosconnect_artefacts/apis/delacc/api-docs/swagger.json | DELETE /delacc/remove/{accno} |
| Customer Services Interface ListAccJson names the INQACCCZ commarea contract | consume | src/Z-OS-Connect-Customer-Services-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/customerservices/jsonclasses/listaccounts/ListAccJson.java | L11 |
| webui JAX-RS GET /webui-1.0/banking/account/retrieveByCustomerNumber/{customerNumber} | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/AccountsResource.java | L471-L472 |
| Customer Services Interface InqAccczJson defect: the fail code is typed as `private int commFailCode;` here while CreaccJson types the same concept as a String | consume | src/Z-OS-Connect-Customer-Services-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/customerservices/jsonclasses/listaccounts/InqAccczJson.java | L18-L19 |

## inqacccu-customer-found-flag — INQACCCU Customer-Found Flag CUSTOMER-FOUND (`PIC X`) [src/base/cobol_copy/INQACCCU.cpy:L11]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| INQACCCU.cpy copybook declaration CUSTOMER-FOUND | create | src/base/cobol_copy/INQACCCU.cpy | L11 |
| INQACCCZ.cpy alternate representation as the z/OS Connect list-accounts variant CUSTOMER-FOUND | create | src/base/cobol_copy/INQACCCZ.cpy | L11 |
| DELACCZ.cpy alternate representation as CUSTOMER-FOUND in DELACCZ.cpy, which is shape-identical to INQACCCZ.cpy; this is a defect, because the identically named DELACCZ.si correctly describes the 122-byte single-account DELACC delete COMMAREA instead | create | src/base/cobol_copy/DELACCZ.cpy | L11 |
| INQACCCU | write | src/base/cobol_src/INQACCCU.cbl | PREMIERE SECTION / A010 |
| INQACCCU | write | src/base/cobol_src/INQACCCU.cbl | READ-ACCOUNT-DB2 SECTION / RAD010 |
| INQACCCU | write | src/base/cobol_src/INQACCCU.cbl | FETCH-DATA SECTION / FD010 |
| INQACCCU | write | src/base/cobol_src/INQACCCU.cbl | CUSTOMER-CHECK SECTION / CC010 |
| BNK1CCA | consume | src/base/cobol_src/BNK1CCA.cbl | WORKING-STORAGE SECTION / COPY INQACCCU L98 |
| CREACC | consume | src/base/cobol_src/CREACC.cbl | LOCAL-STORAGE SECTION / COPY INQACCCU L260 |
| DELCUS | consume | src/base/cobol_src/DELCUS.cbl | WORKING-STORAGE SECTION / COPY INQACCCU L221 |
| INQACCCU | consume | src/base/cobol_src/INQACCCU.cbl | LINKAGE SECTION / COPY INQACCCU L191 |
| z/OS Connect service CScustacc interface binding | consume | src/zosconnect_artefacts/services/CScustacc/service-interfaces/INQACCCZ.si | L6 |
| z/OS Connect service CSaccdel interface binding whose 122-byte single-account layout at L7-L9 contradicts the list shape of DELACCZ.cpy | consume | src/zosconnect_artefacts/services/CSaccdel/service-interfaces/DELACCZ.si | L7 |
| z/OS Connect service CSaccdel binds executableName DELACC with requestSIName and responseSIName both DELACCZ.si, confirming the service expects the single-account layout | consume | src/zosconnect_artefacts/services/CSaccdel/service.properties | L8 |
| z/OS Connect API getCScustacc (GET /inqacccz/list/{custno}) | consume | src/zosconnect_artefacts/apis/inqacccz/api-docs/swagger.json | GET /inqacccz/list/{custno} |
| z/OS Connect API deleteCSaccdel (DELETE /delacc/remove/{accno}) | consume | src/zosconnect_artefacts/apis/delacc/api-docs/swagger.json | DELETE /delacc/remove/{accno} |
| Customer Services Interface ListAccJson names the INQACCCZ commarea contract | consume | src/Z-OS-Connect-Customer-Services-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/customerservices/jsonclasses/listaccounts/ListAccJson.java | L11 |
| webui JAX-RS GET /webui-1.0/banking/account/retrieveByCustomerNumber/{customerNumber} | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/AccountsResource.java | L471-L472 |

## inqacccu-number-of-accounts — INQACCCU Account-Array OCCURS Control NUMBER-OF-ACCOUNTS (`PIC S9(8) BINARY`) [src/base/cobol_copy/INQACCCU.cpy:L7]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| INQACCCU.cpy copybook declaration NUMBER-OF-ACCOUNTS, the OCCURS 1 TO 20 DEPENDING ON control for the ACCOUNT-DETAILS array declared at L13-L14 | create | src/base/cobol_copy/INQACCCU.cpy | L7 |
| INQACCCZ.cpy alternate representation as the z/OS Connect list-accounts variant NUMBER-OF-ACCOUNTS | create | src/base/cobol_copy/INQACCCZ.cpy | L7 |
| DELACCZ.cpy alternate representation as NUMBER-OF-ACCOUNTS in DELACCZ.cpy, which is shape-identical to INQACCCZ.cpy; this is a defect, because the identically named DELACCZ.si correctly describes the 122-byte single-account DELACC delete COMMAREA instead | create | src/base/cobol_copy/DELACCZ.cpy | L7 |
| INQACCCU | write | src/base/cobol_src/INQACCCU.cbl | READ-ACCOUNT-DB2 SECTION / RAD010 |
| INQACCCU | write | src/base/cobol_src/INQACCCU.cbl | FETCH-DATA SECTION / FD010 |
| INQACCCU | write | src/base/cobol_src/INQACCCU.cbl | CUSTOMER-CHECK SECTION / CC010 |
| CREACC reads the account count to enforce the per-customer capacity limit | read | src/base/cobol_src/CREACC.cbl | CUSTOMER-ACCOUNT-COUNT SECTION / CAC010 |
| BNK1CCA | consume | src/base/cobol_src/BNK1CCA.cbl | WORKING-STORAGE SECTION / COPY INQACCCU L98 |
| CREACC | consume | src/base/cobol_src/CREACC.cbl | LOCAL-STORAGE SECTION / COPY INQACCCU L260 |
| DELCUS | consume | src/base/cobol_src/DELCUS.cbl | WORKING-STORAGE SECTION / COPY INQACCCU L221 |
| INQACCCU | consume | src/base/cobol_src/INQACCCU.cbl | LINKAGE SECTION / COPY INQACCCU L191 |
| z/OS Connect service CScustacc interface binding | consume | src/zosconnect_artefacts/services/CScustacc/service-interfaces/INQACCCZ.si | L6 |
| z/OS Connect service CSaccdel interface binding whose 122-byte single-account layout at L7-L9 contradicts the list shape of DELACCZ.cpy | consume | src/zosconnect_artefacts/services/CSaccdel/service-interfaces/DELACCZ.si | L7 |
| z/OS Connect service CSaccdel binds executableName DELACC with requestSIName and responseSIName both DELACCZ.si, confirming the service expects the single-account layout | consume | src/zosconnect_artefacts/services/CSaccdel/service.properties | L8 |
| z/OS Connect API getCScustacc (GET /inqacccz/list/{custno}) | consume | src/zosconnect_artefacts/apis/inqacccz/api-docs/swagger.json | GET /inqacccz/list/{custno} |
| z/OS Connect API deleteCSaccdel (DELETE /delacc/remove/{accno}) | consume | src/zosconnect_artefacts/apis/delacc/api-docs/swagger.json | DELETE /delacc/remove/{accno} |
| Customer Services Interface ListAccJson names the INQACCCZ commarea contract | consume | src/Z-OS-Connect-Customer-Services-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/customerservices/jsonclasses/listaccounts/ListAccJson.java | L11 |
| webui JAX-RS GET /webui-1.0/banking/account/retrieveByCustomerNumber/{customerNumber} | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/AccountsResource.java | L471-L472 |
| Customer Services Interface InqAccczJson array accessors | consume | src/Z-OS-Connect-Customer-Services-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/customerservices/jsonclasses/listaccounts/InqAccczJson.java | L103-L114 |
| webui DB2 accessor Account caps a customer at `MAXIMUM_ACCOUNTS_PER_CUSTOMER = 10`, the Java-tier counterpart of the OCCURS 1 TO 20 array bound | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/web/db2/Account.java | L88 |

## delacc-success-flag — DELACC COMMAREA Read Success Flag DELACC-SUCCESS (`PIC X`) [src/base/cobol_copy/DELACC.cpy:L32]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| DELACC.cpy copybook declaration DELACC-SUCCESS | create | src/base/cobol_copy/DELACC.cpy | L32 |
| DELACC | write | src/base/cobol_src/DELACC.cbl | READ-ACCOUNT-DB2 SECTION / RAD010 |
| DELACC | write | src/base/cobol_src/DELACC.cbl | DEL-ACCOUNT-DB2 SECTION / DADB010 |
| DELACC | consume | src/base/cobol_src/DELACC.cbl | LINKAGE SECTION / COPY DELACC L200 |
| z/OS Connect service CSaccdel interface field DelAccSuccess correctly maps this field from the 122-byte DELACC COMMAREA | consume | src/zosconnect_artefacts/services/CSaccdel/service-interfaces/DELACCZ.si | L232 |
| z/OS Connect API deleteCSaccdel (DELETE /delacc/remove/{accno}) | consume | src/zosconnect_artefacts/apis/delacc/api-docs/swagger.json | DELETE /delacc/remove/{accno} |
| webui JAX-RS DELETE /webui-1.0/banking/account/{accountNumber} | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/AccountsResource.java | L1224-L1225 |
| Customer Services Interface delete-account wire package consumes the DELACC contract | consume | src/Z-OS-Connect-Customer-Services-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/customerservices/jsonclasses/deleteaccount/DelaccJson.java | L12 |

## delacc-fail-code — DELACC COMMAREA Read Fail Code DELACC-FAIL-CD (`PIC X`) [src/base/cobol_copy/DELACC.cpy:L33]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| DELACC.cpy copybook declaration DELACC-FAIL-CD | create | src/base/cobol_copy/DELACC.cpy | L33 |
| (none in repository) | write | src/base/cobol_src/DELACC.cbl | no PROCEDURE DIVISION reference to DELACC-FAIL-CD anywhere in DELACC.cbl; exhaustive repo grep over all 29 programs |
| (none in repository) | read | src/base/cobol_src/DELACC.cbl | no PROCEDURE DIVISION reference to DELACC-FAIL-CD anywhere in DELACC.cbl; exhaustive repo grep over all 29 programs |
| DELACC | consume | src/base/cobol_src/DELACC.cbl | LINKAGE SECTION / COPY DELACC L200 |
| z/OS Connect service CSaccdel interface field DelAccFailCd correctly maps this field from the 122-byte DELACC COMMAREA | consume | src/zosconnect_artefacts/services/CSaccdel/service-interfaces/DELACCZ.si | L239 |
| z/OS Connect API deleteCSaccdel (DELETE /delacc/remove/{accno}) | consume | src/zosconnect_artefacts/apis/delacc/api-docs/swagger.json | DELETE /delacc/remove/{accno} |
| webui JAX-RS DELETE /webui-1.0/banking/account/{accountNumber} | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/AccountsResource.java | L1224-L1225 |
| Customer Services Interface delete-account wire package consumes the DELACC contract | consume | src/Z-OS-Connect-Customer-Services-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/customerservices/jsonclasses/deleteaccount/DelaccJson.java | L12 |

## delacc-del-success-flag — DELACC COMMAREA Delete Success Flag DELACC-DEL-SUCCESS (`PIC X`) [src/base/cobol_copy/DELACC.cpy:L34]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| DELACC.cpy copybook declaration DELACC-DEL-SUCCESS | create | src/base/cobol_copy/DELACC.cpy | L34 |
| DELACC | write | src/base/cobol_src/DELACC.cbl | PREMIERE SECTION / A010 |
| DELACC | write | src/base/cobol_src/DELACC.cbl | READ-ACCOUNT-DB2 SECTION / RAD010 |
| DELACC | write | src/base/cobol_src/DELACC.cbl | DEL-ACCOUNT-DB2 SECTION / DADB010 |
| DELACC | consume | src/base/cobol_src/DELACC.cbl | LINKAGE SECTION / COPY DELACC L200 |
| z/OS Connect service CSaccdel interface field DelAccDelSuccess correctly maps this field from the 122-byte DELACC COMMAREA | consume | src/zosconnect_artefacts/services/CSaccdel/service-interfaces/DELACCZ.si | L246 |
| z/OS Connect API deleteCSaccdel (DELETE /delacc/remove/{accno}) | consume | src/zosconnect_artefacts/apis/delacc/api-docs/swagger.json | DELETE /delacc/remove/{accno} |
| webui JAX-RS DELETE /webui-1.0/banking/account/{accountNumber} | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/AccountsResource.java | L1224-L1225 |
| Customer Services Interface delete-account wire package consumes the DELACC contract | consume | src/Z-OS-Connect-Customer-Services-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/customerservices/jsonclasses/deleteaccount/DelaccJson.java | L12 |

## delacc-del-fail-code — DELACC COMMAREA Delete Fail Code DELACC-DEL-FAIL-CD (`PIC X`) [src/base/cobol_copy/DELACC.cpy:L35]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| DELACC.cpy copybook declaration DELACC-DEL-FAIL-CD | create | src/base/cobol_copy/DELACC.cpy | L35 |
| DELACC | write | src/base/cobol_src/DELACC.cbl | READ-ACCOUNT-DB2 SECTION / RAD010 |
| DELACC | write | src/base/cobol_src/DELACC.cbl | DEL-ACCOUNT-DB2 SECTION / DADB010 |
| DELACC | consume | src/base/cobol_src/DELACC.cbl | LINKAGE SECTION / COPY DELACC L200 |
| z/OS Connect service CSaccdel interface field DelAccDelFailCd correctly maps this field from the 122-byte DELACC COMMAREA | consume | src/zosconnect_artefacts/services/CSaccdel/service-interfaces/DELACCZ.si | L253 |
| z/OS Connect API deleteCSaccdel (DELETE /delacc/remove/{accno}) | consume | src/zosconnect_artefacts/apis/delacc/api-docs/swagger.json | DELETE /delacc/remove/{accno} |
| webui JAX-RS DELETE /webui-1.0/banking/account/{accountNumber} | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/AccountsResource.java | L1224-L1225 |
| Customer Services Interface delete-account wire package consumes the DELACC contract | consume | src/Z-OS-Connect-Customer-Services-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/customerservices/jsonclasses/deleteaccount/DelaccJson.java | L12 |

## delcus-del-success-flag — DELCUS COMMAREA Delete Success Flag COMM-DEL-SUCCESS (`PIC X`) [src/base/cobol_copy/DELCUS.cpy:L23]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| DELCUS.cpy copybook declaration COMM-DEL-SUCCESS | create | src/base/cobol_copy/DELCUS.cpy | L23 |
| DELCUS | write | src/base/cobol_src/DELCUS.cbl | PREMIERE SECTION / A010 |
| DELCUS cascades the delete across the customer accounts | write | src/base/cobol_src/DELCUS.cbl | DELETE-ACCOUNTS SECTION / DA010 |
| DELCUS | write | src/base/cobol_src/DELCUS.cbl | DEL-CUST-VSAM SECTION / DCV010 |
| DELCUS | consume | src/base/cobol_src/DELCUS.cbl | LINKAGE SECTION / COPY DELCUS L242 |
| BNK1DCS | consume | src/base/cobol_src/BNK1DCS.cbl | WORKING-STORAGE SECTION / COPY DELCUS L118 |
| z/OS Connect service CScustdel interface binding | consume | src/zosconnect_artefacts/services/CScustdel/service-interfaces/DELCUS.si | L6 |
| z/OS Connect API deleteCScustdel (DELETE /delcus/remove/{custno}) | consume | src/zosconnect_artefacts/apis/delcus/api-docs/swagger.json | DELETE /delcus/remove/{custno} |
| webui JAX-RS DELETE /webui-1.0/banking/customer/{id} | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/CustomerResource.java | L582-L583 |
| Customer Services Interface delete-customer wire package consumes the DELCUS contract | consume | src/Z-OS-Connect-Customer-Services-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/customerservices/jsonclasses/deletecustomer/DelcusJson.java | L11 |

## delcus-del-fail-code — DELCUS COMMAREA Delete Fail Code COMM-DEL-FAIL-CD (`PIC X`) [src/base/cobol_copy/DELCUS.cpy:L24]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| DELCUS.cpy copybook declaration COMM-DEL-FAIL-CD | create | src/base/cobol_copy/DELCUS.cpy | L24 |
| DELCUS | write | src/base/cobol_src/DELCUS.cbl | PREMIERE SECTION / A010 |
| DELCUS cascades the delete across the customer accounts | write | src/base/cobol_src/DELCUS.cbl | DELETE-ACCOUNTS SECTION / DA010 |
| DELCUS | write | src/base/cobol_src/DELCUS.cbl | DEL-CUST-VSAM SECTION / DCV010 |
| DELCUS | consume | src/base/cobol_src/DELCUS.cbl | LINKAGE SECTION / COPY DELCUS L242 |
| BNK1DCS | consume | src/base/cobol_src/BNK1DCS.cbl | WORKING-STORAGE SECTION / COPY DELCUS L118 |
| z/OS Connect service CScustdel interface binding | consume | src/zosconnect_artefacts/services/CScustdel/service-interfaces/DELCUS.si | L6 |
| z/OS Connect API deleteCScustdel (DELETE /delcus/remove/{custno}) | consume | src/zosconnect_artefacts/apis/delcus/api-docs/swagger.json | DELETE /delcus/remove/{custno} |
| webui JAX-RS DELETE /webui-1.0/banking/customer/{id} | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/CustomerResource.java | L582-L583 |
| Customer Services Interface delete-customer wire package consumes the DELCUS contract | consume | src/Z-OS-Connect-Customer-Services-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/customerservices/jsonclasses/deletecustomer/DelcusJson.java | L11 |

## updacc-success-flag — UPDACC COMMAREA Success Flag COMM-SUCCESS With No Companion Fail-Code Field (`PIC X`) [src/base/cobol_copy/UPDACC.cpy:L31]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| UPDACC.cpy copybook declaration COMM-SUCCESS; UPDACC.cpy declares NO companion fail-code field, verified by exhaustive search of the copybook | create | src/base/cobol_copy/UPDACC.cpy | L31 |
| UPDACC | write | src/base/cobol_src/UPDACC.cbl | UPDATE-ACCOUNT-DB2 SECTION / UAD010 |
| UPDACC | consume | src/base/cobol_src/UPDACC.cbl | LINKAGE SECTION / COPY UPDACC L155 |
| z/OS Connect service CSaccupd interface binding | consume | src/zosconnect_artefacts/services/CSaccupd/service-interfaces/UPDACC.si | L6 |
| z/OS Connect API putCSaccupd (PUT /updacc/update) | consume | src/zosconnect_artefacts/apis/updacc/api-docs/swagger.json | PUT /updacc/update |
| Customer Services Interface WebController update-account success guard, which has no fail-code branch because the COMMAREA carries no fail-code field | consume | src/Z-OS-Connect-Customer-Services-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/customerservices/controllers/WebController.java | L677 |
| webui JAX-RS PUT /webui-1.0/banking/account/{id} | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/AccountsResource.java | L619-L620 |
| Customer Services Interface update-account wire package consumes the UPDACC contract | consume | src/Z-OS-Connect-Customer-Services-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/customerservices/jsonclasses/updateaccount/UpdaccJson.java | L12 |

## updcust-upd-success-flag — UPDCUST COMMAREA Update Success Flag COMM-UPD-SUCCESS (`PIC X`) [src/base/cobol_copy/UPDCUST.cpy:L23]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| UPDCUST.cpy copybook declaration COMM-UPD-SUCCESS | create | src/base/cobol_copy/UPDCUST.cpy | L23 |
| UPDCUST | write | src/base/cobol_src/UPDCUST.cbl | PREMIERE SECTION / A010 |
| UPDCUST | write | src/base/cobol_src/UPDCUST.cbl | UPDATE-CUSTOMER-VSAM SECTION / UCV010 |
| UPDCUST | consume | src/base/cobol_src/UPDCUST.cbl | LINKAGE SECTION / COPY UPDCUST L134 |
| BNK1DCS | consume | src/base/cobol_src/BNK1DCS.cbl | WORKING-STORAGE SECTION / COPY UPDCUST L121 |
| z/OS Connect service CScustupd interface binding | consume | src/zosconnect_artefacts/services/CScustupd/service-interfaces/UPDCUST.si | L6 |
| z/OS Connect API putCScustupd (PUT /updcust/update) | consume | src/zosconnect_artefacts/apis/updcust/api-docs/swagger.json | PUT /updcust/update |
| webui JAX-RS PUT /webui-1.0/banking/customer/{id} | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/CustomerResource.java | L344-L345 |
| Customer Services Interface update-customer wire package consumes the UPDCUST contract | consume | src/Z-OS-Connect-Customer-Services-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/customerservices/jsonclasses/updatecustomer/UpdcustJson.java | L11 |

## updcust-upd-fail-code — UPDCUST COMMAREA Update Fail Code COMM-UPD-FAIL-CD (`PIC X`) [src/base/cobol_copy/UPDCUST.cpy:L24]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| UPDCUST.cpy copybook declaration COMM-UPD-FAIL-CD | create | src/base/cobol_copy/UPDCUST.cpy | L24 |
| UPDCUST | write | src/base/cobol_src/UPDCUST.cbl | PREMIERE SECTION / A010 |
| UPDCUST | write | src/base/cobol_src/UPDCUST.cbl | UPDATE-CUSTOMER-VSAM SECTION / UCV010 |
| UPDCUST | consume | src/base/cobol_src/UPDCUST.cbl | LINKAGE SECTION / COPY UPDCUST L134 |
| BNK1DCS | consume | src/base/cobol_src/BNK1DCS.cbl | WORKING-STORAGE SECTION / COPY UPDCUST L121 |
| z/OS Connect service CScustupd interface binding | consume | src/zosconnect_artefacts/services/CScustupd/service-interfaces/UPDCUST.si | L6 |
| z/OS Connect API putCScustupd (PUT /updcust/update) | consume | src/zosconnect_artefacts/apis/updcust/api-docs/swagger.json | PUT /updcust/update |
| webui JAX-RS PUT /webui-1.0/banking/customer/{id} | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/CustomerResource.java | L344-L345 |
| Customer Services Interface update-customer wire package consumes the UPDCUST contract | consume | src/Z-OS-Connect-Customer-Services-Interface/src/main/java/com/ibm/cics/cip/bank/springboot/customerservices/jsonclasses/updatecustomer/UpdcustJson.java | L11 |

## procisrt-function-flag — PROCISRT Seven-Value Function Flag PROCISRT-FUNCTION (`PIC X`) [src/base/cobol_copy/PROCISRT.cpy:L8]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| PROCISRT.cpy copybook declaration PROCISRT-FUNCTION; the seven condition names declared at L9-L15, carrying values '1' through '7' in declaration order, are PROCISRT-DEBIT, PROCISRT-CREDIT, PROCISRT-XFR-LOCAL, PROCISRT-DELETE-CUSTOMER, PROCISRT-CREATE-CUSTOMER, PROCISRT-DELETE-ACCOUNT, PROCISRT-CREATE-ACCOUNT | create | src/base/cobol_copy/PROCISRT.cpy | L8 |
| (none in repository) | write | src/base/cobol_copy/PROCISRT.cpy | no COPY/INCLUDE site; exhaustive repo grep |
| (none in repository) | read | src/base/cobol_copy/PROCISRT.cpy | no COPY/INCLUDE site; exhaustive repo grep |
| (none in repository) | consume | src/base/cobol_copy/PROCISRT.cpy | no COPY/INCLUDE site; exhaustive repo grep |
| API-tier counterpart of function value '1' DEBIT and '2' CREDIT: webui JAX-RS POST /webui-1.0/banking/processedTransaction/debitCreditAccount | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/ProcessedTransactionResource.java | L257-L259 |
| API-tier counterpart of function value '3' XFR-LOCAL: webui JAX-RS POST /webui-1.0/banking/processedTransaction/transferLocal | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/ProcessedTransactionResource.java | L306-L309 |
| API-tier counterpart of function value '4' DELETE-CUSTOMER: webui JAX-RS POST /webui-1.0/banking/processedTransaction/deleteCustomer | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/ProcessedTransactionResource.java | L339-L342 |
| API-tier counterpart of function value '5' CREATE-CUSTOMER: webui JAX-RS POST /webui-1.0/banking/processedTransaction/createCustomer | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/ProcessedTransactionResource.java | L375-L378 |
| API-tier counterpart of function value '6' DELETE-ACCOUNT: webui JAX-RS POST /webui-1.0/banking/processedTransaction/deleteAccount | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/ProcessedTransactionResource.java | L412-L415 |
| API-tier counterpart of function value '7' CREATE-ACCOUNT: webui JAX-RS POST /webui-1.0/banking/processedTransaction/createAccount | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/ProcessedTransactionResource.java | L449-L452 |
| API-tier counterpart of the collection form: webui JAX-RS GET /webui-1.0/banking/processedTransaction | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/ProcessedTransactionResource.java | L94 |

## abndinfo-abend-code — ABNDINFO CICS Abend Code ABND-CODE (`PIC X(4)`) [src/base/cobol_copy/ABNDINFO.cpy:L14]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| ABNDINFO.cpy copybook declaration ABND-CODE | create | src/base/cobol_copy/ABNDINFO.cpy | L14 |
| ABNDPROC the abend handler receives the populated ABNDINFO area in DFHCOMMAREA and writes it to the ABNDFILE VSAM dataset keyed by ABND-VSAM-KEY | write | src/base/cobol_src/ABNDPROC.cbl | PREMIERE SECTION / A010 |
| CREACC populates the diagnostic area before abending | write | src/base/cobol_src/CREACC.cbl | WRITE-PROCTRAN-DB2 SECTION / WPD010 |
| CRECUST populates the diagnostic area before abending | write | src/base/cobol_src/CRECUST.cbl | WRITE-PROCTRAN-DB2 SECTION / WPD010 |
| DBCRFUN populates the diagnostic area before abending | write | src/base/cobol_src/DBCRFUN.cbl | ABEND-HANDLING SECTION / AH010 |
| DELACC populates the diagnostic area before abending | write | src/base/cobol_src/DELACC.cbl | WRITE-PROCTRAN-DB2 SECTION / WPD010 |
| DELCUS populates the diagnostic area before abending | write | src/base/cobol_src/DELCUS.cbl | WRITE-PROCTRAN-CUST-DB2 SECTION / WPCD010 |
| INQACC populates the diagnostic area before abending | write | src/base/cobol_src/INQACC.cbl | ABEND-HANDLING SECTION / AH010 |
| INQACCCU populates the diagnostic area before abending | write | src/base/cobol_src/INQACCCU.cbl | ABEND-HANDLING SECTION / AH010 |
| INQCUST populates the diagnostic area before abending | write | src/base/cobol_src/INQCUST.cbl | ABEND-HANDLING SECTION / AH010 |
| XFRFUN populates the diagnostic area before abending | write | src/base/cobol_src/XFRFUN.cbl | ABEND-HANDLING SECTION / AH010 |
| ABNDPROC | consume | src/base/cobol_src/ABNDPROC.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L38 |
| BNK1CAC | consume | src/base/cobol_src/BNK1CAC.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L150 |
| BNK1CCA | consume | src/base/cobol_src/BNK1CCA.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L139 |
| BNK1CCS | consume | src/base/cobol_src/BNK1CCS.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L149 |
| BNK1CRA | consume | src/base/cobol_src/BNK1CRA.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L167 |
| BNK1DAC | consume | src/base/cobol_src/BNK1DAC.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L164 |
| BNK1DCS | consume | src/base/cobol_src/BNK1DCS.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L169 |
| BNK1TFN | consume | src/base/cobol_src/BNK1TFN.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L163 |
| BNK1UAC | consume | src/base/cobol_src/BNK1UAC.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L175 |
| BNKMENU | consume | src/base/cobol_src/BNKMENU.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L99 |
| CRDTAGY1 | consume | src/base/cobol_src/CRDTAGY1.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L107 |
| CRDTAGY2 | consume | src/base/cobol_src/CRDTAGY2.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L106 |
| CRDTAGY3 | consume | src/base/cobol_src/CRDTAGY3.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L106 |
| CRDTAGY4 | consume | src/base/cobol_src/CRDTAGY4.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L106 |
| CRDTAGY5 | consume | src/base/cobol_src/CRDTAGY5.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L105 |
| CREACC | consume | src/base/cobol_src/CREACC.cbl | LOCAL-STORAGE SECTION / COPY ABNDINFO L279 |
| CRECUST | consume | src/base/cobol_src/CRECUST.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L103 |
| DBCRFUN | consume | src/base/cobol_src/DBCRFUN.cbl | LOCAL-STORAGE SECTION / COPY ABNDINFO L188 |
| DELACC | consume | src/base/cobol_src/DELACC.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L196 |
| DELCUS | consume | src/base/cobol_src/DELCUS.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L238 |
| INQACC | consume | src/base/cobol_src/INQACC.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L196 |
| INQACCCU | consume | src/base/cobol_src/INQACCCU.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L187 |
| INQCUST | consume | src/base/cobol_src/INQCUST.cbl | LOCAL-STORAGE SECTION / COPY ABNDINFO L157 |
| UPDACC | consume | src/base/cobol_src/UPDACC.cbl | LOCAL-STORAGE SECTION / COPY ABNDINFO L151 |
| UPDCUST | consume | src/base/cobol_src/UPDCUST.cbl | LOCAL-STORAGE SECTION / COPY ABNDINFO L130 |
| XFRFUN | consume | src/base/cobol_src/XFRFUN.cbl | LOCAL-STORAGE SECTION / COPY ABNDINFO L260 |

## abndinfo-respcode — ABNDINFO CICS Response Code ABND-RESPCODE (`PIC S9(8) DISPLAY SIGN LEADING SEPARATE`) [src/base/cobol_copy/ABNDINFO.cpy:L16-L17]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| ABNDINFO.cpy copybook declaration ABND-RESPCODE, whose declaration continues onto a second physical line with SIGN LEADING SEPARATE; RESPSTR.cpy is its human-readable decode counterpart | create | src/base/cobol_copy/ABNDINFO.cpy | L16-L17 |
| ABNDPROC the abend handler receives the populated ABNDINFO area in DFHCOMMAREA and writes it to the ABNDFILE VSAM dataset keyed by ABND-VSAM-KEY | write | src/base/cobol_src/ABNDPROC.cbl | PREMIERE SECTION / A010 |
| CREACC populates the diagnostic area before abending | write | src/base/cobol_src/CREACC.cbl | WRITE-PROCTRAN-DB2 SECTION / WPD010 |
| CRECUST populates the diagnostic area before abending | write | src/base/cobol_src/CRECUST.cbl | WRITE-PROCTRAN-DB2 SECTION / WPD010 |
| DBCRFUN populates the diagnostic area before abending | write | src/base/cobol_src/DBCRFUN.cbl | ABEND-HANDLING SECTION / AH010 |
| DELACC populates the diagnostic area before abending | write | src/base/cobol_src/DELACC.cbl | WRITE-PROCTRAN-DB2 SECTION / WPD010 |
| DELCUS populates the diagnostic area before abending | write | src/base/cobol_src/DELCUS.cbl | WRITE-PROCTRAN-CUST-DB2 SECTION / WPCD010 |
| INQACC populates the diagnostic area before abending | write | src/base/cobol_src/INQACC.cbl | ABEND-HANDLING SECTION / AH010 |
| INQACCCU populates the diagnostic area before abending | write | src/base/cobol_src/INQACCCU.cbl | ABEND-HANDLING SECTION / AH010 |
| INQCUST populates the diagnostic area before abending | write | src/base/cobol_src/INQCUST.cbl | ABEND-HANDLING SECTION / AH010 |
| XFRFUN populates the diagnostic area before abending | write | src/base/cobol_src/XFRFUN.cbl | ABEND-HANDLING SECTION / AH010 |
| ABNDPROC | consume | src/base/cobol_src/ABNDPROC.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L38 |
| BNK1CAC | consume | src/base/cobol_src/BNK1CAC.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L150 |
| BNK1CCA | consume | src/base/cobol_src/BNK1CCA.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L139 |
| BNK1CCS | consume | src/base/cobol_src/BNK1CCS.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L149 |
| BNK1CRA | consume | src/base/cobol_src/BNK1CRA.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L167 |
| BNK1DAC | consume | src/base/cobol_src/BNK1DAC.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L164 |
| BNK1DCS | consume | src/base/cobol_src/BNK1DCS.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L169 |
| BNK1TFN | consume | src/base/cobol_src/BNK1TFN.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L163 |
| BNK1UAC | consume | src/base/cobol_src/BNK1UAC.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L175 |
| BNKMENU | consume | src/base/cobol_src/BNKMENU.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L99 |
| CRDTAGY1 | consume | src/base/cobol_src/CRDTAGY1.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L107 |
| CRDTAGY2 | consume | src/base/cobol_src/CRDTAGY2.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L106 |
| CRDTAGY3 | consume | src/base/cobol_src/CRDTAGY3.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L106 |
| CRDTAGY4 | consume | src/base/cobol_src/CRDTAGY4.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L106 |
| CRDTAGY5 | consume | src/base/cobol_src/CRDTAGY5.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L105 |
| CREACC | consume | src/base/cobol_src/CREACC.cbl | LOCAL-STORAGE SECTION / COPY ABNDINFO L279 |
| CRECUST | consume | src/base/cobol_src/CRECUST.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L103 |
| DBCRFUN | consume | src/base/cobol_src/DBCRFUN.cbl | LOCAL-STORAGE SECTION / COPY ABNDINFO L188 |
| DELACC | consume | src/base/cobol_src/DELACC.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L196 |
| DELCUS | consume | src/base/cobol_src/DELCUS.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L238 |
| INQACC | consume | src/base/cobol_src/INQACC.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L196 |
| INQACCCU | consume | src/base/cobol_src/INQACCCU.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L187 |
| INQCUST | consume | src/base/cobol_src/INQCUST.cbl | LOCAL-STORAGE SECTION / COPY ABNDINFO L157 |
| UPDACC | consume | src/base/cobol_src/UPDACC.cbl | LOCAL-STORAGE SECTION / COPY ABNDINFO L151 |
| UPDCUST | consume | src/base/cobol_src/UPDCUST.cbl | LOCAL-STORAGE SECTION / COPY ABNDINFO L130 |
| XFRFUN | consume | src/base/cobol_src/XFRFUN.cbl | LOCAL-STORAGE SECTION / COPY ABNDINFO L260 |
| RESPSTR.cpy PROCEDURE DIVISION fragment implementing the EIBRESP-TOSTRING paragraph as an EVALUATE EIBRESP decode table; it is the diagnostic counterpart of this field and is not a data layout | consume | src/base/cobol_copy/RESPSTR.cpy | L10-L13 |
| (none in repository) | consume | src/base/cobol_copy/RESPSTR.cpy | no COPY/INCLUDE site; exhaustive repo grep |

## abndinfo-resp2code — ABNDINFO CICS Secondary Response Code ABND-RESP2CODE (`PIC S9(8) DISPLAY SIGN LEADING SEPARATE`) [src/base/cobol_copy/ABNDINFO.cpy:L18-L19]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| ABNDINFO.cpy copybook declaration ABND-RESP2CODE, whose declaration continues onto a second physical line with SIGN LEADING SEPARATE | create | src/base/cobol_copy/ABNDINFO.cpy | L18-L19 |
| ABNDPROC the abend handler receives the populated ABNDINFO area in DFHCOMMAREA and writes it to the ABNDFILE VSAM dataset keyed by ABND-VSAM-KEY | write | src/base/cobol_src/ABNDPROC.cbl | PREMIERE SECTION / A010 |
| CREACC populates the diagnostic area before abending | write | src/base/cobol_src/CREACC.cbl | WRITE-PROCTRAN-DB2 SECTION / WPD010 |
| CRECUST populates the diagnostic area before abending | write | src/base/cobol_src/CRECUST.cbl | WRITE-PROCTRAN-DB2 SECTION / WPD010 |
| DBCRFUN populates the diagnostic area before abending | write | src/base/cobol_src/DBCRFUN.cbl | ABEND-HANDLING SECTION / AH010 |
| DELACC populates the diagnostic area before abending | write | src/base/cobol_src/DELACC.cbl | WRITE-PROCTRAN-DB2 SECTION / WPD010 |
| DELCUS populates the diagnostic area before abending | write | src/base/cobol_src/DELCUS.cbl | WRITE-PROCTRAN-CUST-DB2 SECTION / WPCD010 |
| INQACC populates the diagnostic area before abending | write | src/base/cobol_src/INQACC.cbl | ABEND-HANDLING SECTION / AH010 |
| INQACCCU populates the diagnostic area before abending | write | src/base/cobol_src/INQACCCU.cbl | ABEND-HANDLING SECTION / AH010 |
| INQCUST populates the diagnostic area before abending | write | src/base/cobol_src/INQCUST.cbl | ABEND-HANDLING SECTION / AH010 |
| XFRFUN populates the diagnostic area before abending | write | src/base/cobol_src/XFRFUN.cbl | ABEND-HANDLING SECTION / AH010 |
| ABNDPROC | consume | src/base/cobol_src/ABNDPROC.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L38 |
| BNK1CAC | consume | src/base/cobol_src/BNK1CAC.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L150 |
| BNK1CCA | consume | src/base/cobol_src/BNK1CCA.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L139 |
| BNK1CCS | consume | src/base/cobol_src/BNK1CCS.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L149 |
| BNK1CRA | consume | src/base/cobol_src/BNK1CRA.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L167 |
| BNK1DAC | consume | src/base/cobol_src/BNK1DAC.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L164 |
| BNK1DCS | consume | src/base/cobol_src/BNK1DCS.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L169 |
| BNK1TFN | consume | src/base/cobol_src/BNK1TFN.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L163 |
| BNK1UAC | consume | src/base/cobol_src/BNK1UAC.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L175 |
| BNKMENU | consume | src/base/cobol_src/BNKMENU.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L99 |
| CRDTAGY1 | consume | src/base/cobol_src/CRDTAGY1.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L107 |
| CRDTAGY2 | consume | src/base/cobol_src/CRDTAGY2.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L106 |
| CRDTAGY3 | consume | src/base/cobol_src/CRDTAGY3.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L106 |
| CRDTAGY4 | consume | src/base/cobol_src/CRDTAGY4.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L106 |
| CRDTAGY5 | consume | src/base/cobol_src/CRDTAGY5.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L105 |
| CREACC | consume | src/base/cobol_src/CREACC.cbl | LOCAL-STORAGE SECTION / COPY ABNDINFO L279 |
| CRECUST | consume | src/base/cobol_src/CRECUST.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L103 |
| DBCRFUN | consume | src/base/cobol_src/DBCRFUN.cbl | LOCAL-STORAGE SECTION / COPY ABNDINFO L188 |
| DELACC | consume | src/base/cobol_src/DELACC.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L196 |
| DELCUS | consume | src/base/cobol_src/DELCUS.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L238 |
| INQACC | consume | src/base/cobol_src/INQACC.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L196 |
| INQACCCU | consume | src/base/cobol_src/INQACCCU.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L187 |
| INQCUST | consume | src/base/cobol_src/INQCUST.cbl | LOCAL-STORAGE SECTION / COPY ABNDINFO L157 |
| UPDACC | consume | src/base/cobol_src/UPDACC.cbl | LOCAL-STORAGE SECTION / COPY ABNDINFO L151 |
| UPDCUST | consume | src/base/cobol_src/UPDCUST.cbl | LOCAL-STORAGE SECTION / COPY ABNDINFO L130 |
| XFRFUN | consume | src/base/cobol_src/XFRFUN.cbl | LOCAL-STORAGE SECTION / COPY ABNDINFO L260 |

## abndinfo-sqlcode — ABNDINFO DB2 SQLCODE ABND-SQLCODE (`PIC S9(8) DISPLAY SIGN LEADING SEPARATE`) [src/base/cobol_copy/ABNDINFO.cpy:L20-L21]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| ABNDINFO.cpy copybook declaration ABND-SQLCODE, whose declaration continues onto a second physical line with SIGN LEADING SEPARATE | create | src/base/cobol_copy/ABNDINFO.cpy | L20-L21 |
| ABNDPROC the abend handler receives the populated ABNDINFO area in DFHCOMMAREA and writes it to the ABNDFILE VSAM dataset keyed by ABND-VSAM-KEY | write | src/base/cobol_src/ABNDPROC.cbl | PREMIERE SECTION / A010 |
| CREACC populates the diagnostic area before abending | write | src/base/cobol_src/CREACC.cbl | WRITE-PROCTRAN-DB2 SECTION / WPD010 |
| CRECUST populates the diagnostic area before abending | write | src/base/cobol_src/CRECUST.cbl | WRITE-PROCTRAN-DB2 SECTION / WPD010 |
| DBCRFUN populates the diagnostic area before abending | write | src/base/cobol_src/DBCRFUN.cbl | ABEND-HANDLING SECTION / AH010 |
| DELACC populates the diagnostic area before abending | write | src/base/cobol_src/DELACC.cbl | WRITE-PROCTRAN-DB2 SECTION / WPD010 |
| DELCUS populates the diagnostic area before abending | write | src/base/cobol_src/DELCUS.cbl | WRITE-PROCTRAN-CUST-DB2 SECTION / WPCD010 |
| INQACC populates the diagnostic area before abending | write | src/base/cobol_src/INQACC.cbl | ABEND-HANDLING SECTION / AH010 |
| INQACCCU populates the diagnostic area before abending | write | src/base/cobol_src/INQACCCU.cbl | ABEND-HANDLING SECTION / AH010 |
| INQCUST populates the diagnostic area before abending | write | src/base/cobol_src/INQCUST.cbl | ABEND-HANDLING SECTION / AH010 |
| XFRFUN populates the diagnostic area before abending | write | src/base/cobol_src/XFRFUN.cbl | ABEND-HANDLING SECTION / AH010 |
| ABNDPROC | consume | src/base/cobol_src/ABNDPROC.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L38 |
| BNK1CAC | consume | src/base/cobol_src/BNK1CAC.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L150 |
| BNK1CCA | consume | src/base/cobol_src/BNK1CCA.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L139 |
| BNK1CCS | consume | src/base/cobol_src/BNK1CCS.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L149 |
| BNK1CRA | consume | src/base/cobol_src/BNK1CRA.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L167 |
| BNK1DAC | consume | src/base/cobol_src/BNK1DAC.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L164 |
| BNK1DCS | consume | src/base/cobol_src/BNK1DCS.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L169 |
| BNK1TFN | consume | src/base/cobol_src/BNK1TFN.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L163 |
| BNK1UAC | consume | src/base/cobol_src/BNK1UAC.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L175 |
| BNKMENU | consume | src/base/cobol_src/BNKMENU.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L99 |
| CRDTAGY1 | consume | src/base/cobol_src/CRDTAGY1.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L107 |
| CRDTAGY2 | consume | src/base/cobol_src/CRDTAGY2.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L106 |
| CRDTAGY3 | consume | src/base/cobol_src/CRDTAGY3.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L106 |
| CRDTAGY4 | consume | src/base/cobol_src/CRDTAGY4.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L106 |
| CRDTAGY5 | consume | src/base/cobol_src/CRDTAGY5.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L105 |
| CREACC | consume | src/base/cobol_src/CREACC.cbl | LOCAL-STORAGE SECTION / COPY ABNDINFO L279 |
| CRECUST | consume | src/base/cobol_src/CRECUST.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L103 |
| DBCRFUN | consume | src/base/cobol_src/DBCRFUN.cbl | LOCAL-STORAGE SECTION / COPY ABNDINFO L188 |
| DELACC | consume | src/base/cobol_src/DELACC.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L196 |
| DELCUS | consume | src/base/cobol_src/DELCUS.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L238 |
| INQACC | consume | src/base/cobol_src/INQACC.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L196 |
| INQACCCU | consume | src/base/cobol_src/INQACCCU.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L187 |
| INQCUST | consume | src/base/cobol_src/INQCUST.cbl | LOCAL-STORAGE SECTION / COPY ABNDINFO L157 |
| UPDACC | consume | src/base/cobol_src/UPDACC.cbl | LOCAL-STORAGE SECTION / COPY ABNDINFO L151 |
| UPDCUST | consume | src/base/cobol_src/UPDCUST.cbl | LOCAL-STORAGE SECTION / COPY ABNDINFO L130 |
| XFRFUN | consume | src/base/cobol_src/XFRFUN.cbl | LOCAL-STORAGE SECTION / COPY ABNDINFO L260 |

## abndinfo-freeform — ABNDINFO Free-Form Diagnostic Text ABND-FREEFORM (`PIC X(600)`) [src/base/cobol_copy/ABNDINFO.cpy:L22]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| ABNDINFO.cpy copybook declaration ABND-FREEFORM | create | src/base/cobol_copy/ABNDINFO.cpy | L22 |
| ABNDPROC the abend handler receives the populated ABNDINFO area in DFHCOMMAREA and writes it to the ABNDFILE VSAM dataset keyed by ABND-VSAM-KEY | write | src/base/cobol_src/ABNDPROC.cbl | PREMIERE SECTION / A010 |
| CREACC populates the diagnostic area before abending | write | src/base/cobol_src/CREACC.cbl | WRITE-PROCTRAN-DB2 SECTION / WPD010 |
| CRECUST populates the diagnostic area before abending | write | src/base/cobol_src/CRECUST.cbl | WRITE-PROCTRAN-DB2 SECTION / WPD010 |
| DBCRFUN populates the diagnostic area before abending | write | src/base/cobol_src/DBCRFUN.cbl | ABEND-HANDLING SECTION / AH010 |
| DELACC populates the diagnostic area before abending | write | src/base/cobol_src/DELACC.cbl | WRITE-PROCTRAN-DB2 SECTION / WPD010 |
| DELCUS populates the diagnostic area before abending | write | src/base/cobol_src/DELCUS.cbl | WRITE-PROCTRAN-CUST-DB2 SECTION / WPCD010 |
| INQACC populates the diagnostic area before abending | write | src/base/cobol_src/INQACC.cbl | ABEND-HANDLING SECTION / AH010 |
| INQACCCU populates the diagnostic area before abending | write | src/base/cobol_src/INQACCCU.cbl | ABEND-HANDLING SECTION / AH010 |
| INQCUST populates the diagnostic area before abending | write | src/base/cobol_src/INQCUST.cbl | ABEND-HANDLING SECTION / AH010 |
| XFRFUN populates the diagnostic area before abending | write | src/base/cobol_src/XFRFUN.cbl | ABEND-HANDLING SECTION / AH010 |
| ABNDPROC | consume | src/base/cobol_src/ABNDPROC.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L38 |
| BNK1CAC | consume | src/base/cobol_src/BNK1CAC.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L150 |
| BNK1CCA | consume | src/base/cobol_src/BNK1CCA.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L139 |
| BNK1CCS | consume | src/base/cobol_src/BNK1CCS.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L149 |
| BNK1CRA | consume | src/base/cobol_src/BNK1CRA.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L167 |
| BNK1DAC | consume | src/base/cobol_src/BNK1DAC.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L164 |
| BNK1DCS | consume | src/base/cobol_src/BNK1DCS.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L169 |
| BNK1TFN | consume | src/base/cobol_src/BNK1TFN.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L163 |
| BNK1UAC | consume | src/base/cobol_src/BNK1UAC.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L175 |
| BNKMENU | consume | src/base/cobol_src/BNKMENU.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L99 |
| CRDTAGY1 | consume | src/base/cobol_src/CRDTAGY1.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L107 |
| CRDTAGY2 | consume | src/base/cobol_src/CRDTAGY2.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L106 |
| CRDTAGY3 | consume | src/base/cobol_src/CRDTAGY3.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L106 |
| CRDTAGY4 | consume | src/base/cobol_src/CRDTAGY4.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L106 |
| CRDTAGY5 | consume | src/base/cobol_src/CRDTAGY5.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L105 |
| CREACC | consume | src/base/cobol_src/CREACC.cbl | LOCAL-STORAGE SECTION / COPY ABNDINFO L279 |
| CRECUST | consume | src/base/cobol_src/CRECUST.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L103 |
| DBCRFUN | consume | src/base/cobol_src/DBCRFUN.cbl | LOCAL-STORAGE SECTION / COPY ABNDINFO L188 |
| DELACC | consume | src/base/cobol_src/DELACC.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L196 |
| DELCUS | consume | src/base/cobol_src/DELCUS.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L238 |
| INQACC | consume | src/base/cobol_src/INQACC.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L196 |
| INQACCCU | consume | src/base/cobol_src/INQACCCU.cbl | WORKING-STORAGE SECTION / COPY ABNDINFO L187 |
| INQCUST | consume | src/base/cobol_src/INQCUST.cbl | LOCAL-STORAGE SECTION / COPY ABNDINFO L157 |
| UPDACC | consume | src/base/cobol_src/UPDACC.cbl | LOCAL-STORAGE SECTION / COPY ABNDINFO L151 |
| UPDCUST | consume | src/base/cobol_src/UPDCUST.cbl | LOCAL-STORAGE SECTION / COPY ABNDINFO L130 |
| XFRFUN | consume | src/base/cobol_src/XFRFUN.cbl | LOCAL-STORAGE SECTION / COPY ABNDINFO L260 |

## sortcode-literal — SORTCODE Bank Sort-Code Literal (`PIC 9(6) VALUE 987654`) [src/base/cobol_copy/SORTCODE.cpy:L7]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| SORTCODE.cpy copybook declaration the 77-level standalone literal SORTCODE, the widest business data fan-out in the repository at 18 COPY sites | create | src/base/cobol_copy/SORTCODE.cpy | L7 |
| GETSCODE returns the sort code to callers; the literal is renamed on COPY to LITERAL-SORTCODE | create | src/base/cobol_src/GETSCODE.cbl | PREMIERE SECTION / A010 |
| GETSCODE via COPY SORTCODE REPLACING ==SORTCODE== BY ==LITERAL-SORTCODE== | consume | src/base/cobol_src/GETSCODE.cbl | WORKING-STORAGE SECTION / COPY SORTCODE L28 |
| BANKDATA | consume | src/base/cobol_src/BANKDATA.cbl | WORKING-STORAGE SECTION / COPY SORTCODE L240 |
| CRDTAGY1 | consume | src/base/cobol_src/CRDTAGY1.cbl | WORKING-STORAGE SECTION / COPY SORTCODE L41 |
| CRDTAGY2 | consume | src/base/cobol_src/CRDTAGY2.cbl | WORKING-STORAGE SECTION / COPY SORTCODE L41 |
| CRDTAGY3 | consume | src/base/cobol_src/CRDTAGY3.cbl | WORKING-STORAGE SECTION / COPY SORTCODE L41 |
| CRDTAGY4 | consume | src/base/cobol_src/CRDTAGY4.cbl | WORKING-STORAGE SECTION / COPY SORTCODE L42 |
| CRDTAGY5 | consume | src/base/cobol_src/CRDTAGY5.cbl | WORKING-STORAGE SECTION / COPY SORTCODE L41 |
| CREACC | consume | src/base/cobol_src/CREACC.cbl | WORKING-STORAGE SECTION / COPY SORTCODE L42 |
| CRECUST | consume | src/base/cobol_src/CRECUST.cbl | WORKING-STORAGE SECTION / COPY SORTCODE L55 |
| DBCRFUN | consume | src/base/cobol_src/DBCRFUN.cbl | WORKING-STORAGE SECTION / COPY SORTCODE L48 |
| DELACC | consume | src/base/cobol_src/DELACC.cbl | WORKING-STORAGE SECTION / COPY SORTCODE L41 |
| DELCUS | consume | src/base/cobol_src/DELCUS.cbl | WORKING-STORAGE SECTION / COPY SORTCODE L44 |
| INQACC | consume | src/base/cobol_src/INQACC.cbl | WORKING-STORAGE SECTION / COPY SORTCODE L37 |
| INQACCCU | consume | src/base/cobol_src/INQACCCU.cbl | WORKING-STORAGE SECTION / COPY SORTCODE L33 |
| INQCUST | consume | src/base/cobol_src/INQCUST.cbl | LOCAL-STORAGE SECTION / COPY SORTCODE L46 |
| UPDACC | consume | src/base/cobol_src/UPDACC.cbl | WORKING-STORAGE SECTION / COPY SORTCODE L49 |
| UPDCUST | consume | src/base/cobol_src/UPDCUST.cbl | WORKING-STORAGE SECTION / COPY SORTCODE L43 |
| XFRFUN | consume | src/base/cobol_src/XFRFUN.cbl | WORKING-STORAGE SECTION / COPY SORTCODE L56 |
| webui JAX-RS GET /webui-1.0/banking/sortCode | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/SortCodeResource.java | L54 |
| webui JAX-RS SortCodeResource class-level path binding | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/SortCodeResource.java | L37 |
| CBSA RESTful Interface Reference documents the sort-code endpoint | consume | etc/usage/carbonReactUI/doc/CBSA_RESTful_Interface_Reference.md | #### Sort Code |

## getcompy-company-name — GETCOMPY Company-Name Reference Value (`pic x(40)`) [src/base/cobol_copy/GETCOMPY.cpy:L8]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| GETCOMPY.cpy copybook declaration company-name, declared in lower case in the source; the enclosing group GETCompanyOperation is declared at L7 | create | src/base/cobol_copy/GETCOMPY.cpy | L8 |
| GETCOMPY returns the company name to callers as the only field in its COMMAREA | create | src/base/cobol_src/GETCOMPY.cbl | PREMIERE SECTION / A010 |
| GETCOMPY | consume | src/base/cobol_src/GETCOMPY.cbl | LINKAGE SECTION / COPY GETCOMPY L31 |
| IBM Record Generator commarea class GetCompany, COMPANY_NAME 40-byte StringField | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/GetCompany.java | L36 |
| IBM Record Generator commarea class GetCompany, 40-byte DFHCOMMAREA length | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/GetCompany.java | L19 |
| webui JAX-RS GET /webui-1.0/banking/companyName | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/CompanyNameResource.java | L62 |
| webui JAX-RS CompanyNameResource class-level path binding | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/CompanyNameResource.java | L39 |
| CBSA RESTful Interface Reference documents the company-name endpoint | consume | etc/usage/carbonReactUI/doc/CBSA_RESTful_Interface_Reference.md | #### Company Name |

## getscode-sortcode — GETSCODE Sort-Code Wire Field With Anomalous Mixed-Case PICTURE (`pic xXXXXX`) [src/base/cobol_copy/GETSCODE.cpy:L8]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| GETSCODE.cpy copybook declaration SORTCODE, whose PICTURE is written in anomalous mixed case as pic xXXXXX rather than PIC X(6); the enclosing group GETSORTCODEOperation is declared at L7 | create | src/base/cobol_copy/GETSCODE.cpy | L8 |
| GETSCODE moves the LITERAL-SORTCODE renamed literal into the COMMAREA field | create | src/base/cobol_src/GETSCODE.cbl | PREMIERE SECTION / A010 |
| GETSCODE | consume | src/base/cobol_src/GETSCODE.cbl | LINKAGE SECTION / COPY GETSCODE L33 |
| GETSCODE supplies the literal value via COPY ... REPLACING | consume | src/base/cobol_src/GETSCODE.cbl | WORKING-STORAGE SECTION / COPY SORTCODE L28 |
| IBM Record Generator commarea class GetSortCode, SORTCODE 6-byte StringField, which resolves the anomalous mixed-case PICTURE to a 6-character field | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/GetSortCode.java | L36 |
| IBM Record Generator commarea class GetSortCode, 6-byte DFHCOMMAREA length | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/datainterfaces/GetSortCode.java | L19 |
| webui JAX-RS GET /webui-1.0/banking/sortCode | consume | src/webui/src/main/java/com/ibm/cics/cip/bankliberty/api/json/SortCodeResource.java | L54 |

## stcustno-customer-number-key — STCUSTNO Customer-Number VSAM Key CNO-KEY (`PIC 9(10) DISPLAY`) [src/base/cobol_copy/STCUSTNO.cpy:L8]

| Program/API/Job | Operation | File Path | Paragraph/Section Ref |
|---|---|---|---|
| STCUSTNO.cpy copybook declaration CNO-KEY, declared in mixed case; the enclosing group Customer-Number-Key is declared at L7 | create | src/base/cobol_copy/STCUSTNO.cpy | L8 |
| CUSTOMER.cpy alternate representation the in-repository customer-number key field CUSTOMER-NUMBER within CUSTOMER-KEY at L10-L12 that STCUSTNO mirrors | create | src/base/cobol_copy/CUSTOMER.cpy | L12 |
| CUSTCTRL.cpy alternate representation the in-repository last-allocated customer number that the key sequence draws from | create | src/base/cobol_copy/CUSTCTRL.cpy | L14 |
| (none in repository) | write | src/base/cobol_copy/STCUSTNO.cpy | no COPY/INCLUDE site; exhaustive repo grep |
| (none in repository) | read | src/base/cobol_copy/STCUSTNO.cpy | no COPY/INCLUDE site; exhaustive repo grep |
| CRECUST reads the customer control record to obtain the last allocated customer number | read | src/base/cobol_src/CRECUST.cbl | GET-LAST-CUSTOMER-VSAM SECTION / GLCV010 |
| INQCUST reads a customer record by its VSAM key | read | src/base/cobol_src/INQCUST.cbl | READ-CUSTOMER-VSAM SECTION / RCV010 |
| (none in repository) | consume | src/base/cobol_copy/STCUSTNO.cpy | no COPY/INCLUDE site; exhaustive repo grep |

## Copybook Coverage Ledger

| Copybook | Indicators/CDEs Documented | Slugs | Determination |
|---|---|---|---|
| ABNDINFO.cpy | 5 | abndinfo-abend-code, abndinfo-respcode, abndinfo-resp2code, abndinfo-sqlcode, abndinfo-freeform | Diagnostic CDE owner; 26 COPY sites, the widest fan-out in the repository. The IBM-supplied DFHAID copybook is referenced by nine BMS handler programs but is not stored in this repository |
| ACCDB2.cpy | 0 | (no slug owned; contributes representation or resolution rows to account-available-balance, account-actual-balance, account-eye-catcher, account-type-code, account-overdraft-limit) | DB2 DECLARE TABLE mirror of ACCOUNT.cpy; carries no distinct indicator, so it contributes alternate-representation rows inside the owning ACCOUNT slugs rather than new slugs. 8 EXEC SQL INCLUDE sites |
| ACCOUNT.cpy | 5 | account-available-balance, account-actual-balance, account-eye-catcher, account-type-code, account-overdraft-limit | Entity record owner; 8 programs across 9 COPY sites, XFRFUN instantiating the layout twice for the transfer from-leg and to-leg |
| ACCTCTRL.cpy | 5 | acctctrl-eye-catcher, acctctrl-number-of-accounts, acctctrl-last-account-number, acctctrl-success-flag, acctctrl-fail-code | Account control-record owner; 3 COPY sites |
| BANKMAP.cpy | 0 | (no slug owned; no indicator or CDE is declared in this copybook) | BMS symbolic map, 374 lines, following the standard length, flag, attribute and input quartet; presentation attributes only and no business indicator. Zero references anywhere in the repository, established by exhaustive case-sensitive search |
| BNK1DDM.cpy | 0 | (no slug owned; no indicator or CDE is declared in this copybook) | BMS symbolic map, 52 lines, same quartet pattern; presentation attributes only and no business indicator. Zero references anywhere in the repository, established by exhaustive case-sensitive search. The IBM-supplied DFHBMSCA copybook is referenced at src/base/cobol_src/BNK1DCS.cbl:L141 but is not stored here, and the nine BMS map copybooks BNK1CAM, BNK1ACC, BNK1CCM, BNK1CDM, BNK1DAM, BNK1DCM, BNK1TFM, BNK1UAM and BNK1MAI are generated from src/base/bms_src/ rather than stored in cobol_copy/ |
| CONTDB2.cpy | 0 | (no slug owned; contributes representation or resolution rows to acctctrl-eye-catcher, acctctrl-number-of-accounts, acctctrl-last-account-number, acctctrl-success-flag, acctctrl-fail-code, custctrl-eye-catcher, custctrl-number-of-customers, custctrl-last-customer-number, custctrl-success-flag, custctrl-fail-code, controli-control-counters) | DB2 DECLARE TABLE for STTESTER.CONTROL; carries no distinct indicator, so it contributes alternate-representation rows inside the control-record slugs. 1 EXEC SQL INCLUDE site |
| CONTROLI.cpy | 1 | controli-control-counters | Packed-decimal control counter quartet. Zero references anywhere in the repository, established by exhaustive case-sensitive search; resolved as an IMS-era counterpart of the in-repository ACCTCTRL and CUSTCTRL counters |
| CREACC.cpy | 2 | creacc-success-flag, creacc-fail-code | Create-account COMMAREA owner; 1 own-COMMAREA COPY site. The IBM-supplied CEEIGZCT copybook is referenced at src/base/cobol_src/CRECUST.cbl:L303 but is not stored here |
| CRECUST.cpy | 2 | crecust-success-flag, crecust-fail-code | Create-customer COMMAREA owner; 1 own-COMMAREA COPY site |
| CUSTCTRL.cpy | 5 | custctrl-eye-catcher, custctrl-number-of-customers, custctrl-last-customer-number, custctrl-success-flag, custctrl-fail-code | Customer control-record owner; 2 COPY sites |
| CUSTMAP.cpy | 0 | (no slug owned; no indicator or CDE is declared in this copybook) | BMS symbolic map, 108 lines, same quartet pattern; presentation attributes only and no business indicator. Zero references anywhere in the repository, established by exhaustive case-sensitive search |
| CUSTOMER.cpy | 3 | customer-credit-score, customer-cs-review-date, customer-eye-catcher | Entity record owner; 7 COPY sites |
| DELACC.cpy | 4 | delacc-success-flag, delacc-fail-code, delacc-del-success-flag, delacc-del-fail-code | Delete-account COMMAREA owner; 1 own-COMMAREA COPY site. DELACC-FAIL-CD has no PROCEDURE DIVISION reference in any of the 29 programs and is resolved to its z/OS Connect interface field instead |
| DELACCZ.cpy | 0 | (no slug owned; contributes representation or resolution rows to inqacccu-success-flag, inqacccu-fail-code, inqacccu-customer-found-flag, inqacccu-number-of-accounts) | z/OS Connect variant, 38 lines, shape-identical to INQACCCZ.cpy; carries no distinct indicator, so it contributes alternate-representation rows inside the INQACCCU slugs. DEFECT: the identically named DELACCZ.si correctly describes the 122-byte single-account DELACC delete COMMAREA, so the fault lies with this copybook rather than with the service interface |
| DELCUS.cpy | 2 | delcus-del-success-flag, delcus-del-fail-code | Delete-customer COMMAREA owner; 2 COPY sites |
| GETCOMPY.cpy | 1 | getcompy-company-name | Reference-data COMMAREA owner; 1 COPY site |
| GETSCODE.cpy | 1 | getscode-sortcode | Reference-data COMMAREA owner; 1 COPY site. Its PICTURE is written in anomalous mixed case as pic xXXXXX and is reproduced verbatim rather than normalised |
| INQACC.cpy | 1 | inqacc-success-flag | Account-enquiry COMMAREA owner; 2 COPY sites. Declares no fail-code field |
| INQACCCU.cpy | 4 | inqacccu-success-flag, inqacccu-fail-code, inqacccu-customer-found-flag, inqacccu-number-of-accounts | List-accounts COMMAREA owner; 4 COPY sites. Uses OCCURS 1 TO 20 DEPENDING ON, so the array bound is itself a documented indicator |
| INQACCCZ.cpy | 0 | (no slug owned; contributes representation or resolution rows to inqacccu-success-flag, inqacccu-fail-code, inqacccu-customer-found-flag, inqacccu-number-of-accounts) | z/OS Connect variant of INQACCCU.cpy; carries no distinct indicator, so it contributes alternate-representation rows inside the INQACCCU slugs. No COBOL COPY site; resolved to its service interface and to the Customer Services ListAccJson DTO |
| INQACCZ.cpy | 0 | (no slug owned; contributes representation or resolution rows to inqacc-success-flag) | z/OS Connect variant of INQACC.cpy, differing only in the PCB pointer declaration; carries no distinct indicator, so it contributes an alternate-representation row inside the INQACC slug. No COBOL COPY site; resolved to its service interface |
| INQCUST.cpy | 2 | inqcust-inq-success-flag, inqcust-inq-fail-code | Customer-enquiry COMMAREA owner; 5 COPY sites |
| INQCUSTZ.cpy | 0 | (no slug owned; contributes representation or resolution rows to inqcust-inq-success-flag, inqcust-inq-fail-code) | z/OS Connect variant of INQCUST.cpy; carries no distinct indicator, so it contributes alternate-representation rows inside the INQCUST slugs. No COBOL COPY site; resolved to its service interface and to the Customer Services CustomerEnquiryJson DTO |
| NEWACCNO.cpy | 3 | newaccno-function-flag, newaccno-success-flag, newaccno-fail-code | Account-number allocator COMMAREA owner. No COBOL COPY site and no NEWACCNO.cbl in src/base/cobol_src/; resolved as a COMMAREA contract for an allocator delivered outside this repository, with the in-repository CREACC named-counter sections cited as the resolved origin and the IBM Record Generator class as the resolved consumer |
| NEWCUSNO.cpy | 3 | newcusno-function-flag, newcusno-success-flag, newcusno-fail-code | Customer-number allocator COMMAREA owner. No COBOL COPY site and no NEWCUSNO.cbl in src/base/cobol_src/; resolved as a COMMAREA contract for an allocator delivered outside this repository, with the in-repository CRECUST named-counter sections cited as the resolved origin and the IBM Record Generator class as the resolved consumer |
| PAYDBCR.cpy | 3 | dbcrfun-success-flag, dbcrfun-fail-code, dbcrfun-faciltype-channel-discriminator | Payment COMMAREA owner; 1 COPY site. Carries the FACILTYPE channel discriminator, the balance pair copies and the DBCRFUN fail code |
| PROCDB2.cpy | 0 | (no slug owned; contributes representation or resolution rows to proctran-type-code, proctran-eye-catcher, proctran-desc-xfr-flag, proctran-desc-creacc-flag, proctran-desc-delacc-flag, proctran-desc-crecus-separator, proctran-desc-delcus-separator) | DB2 DECLARE TABLE mirror of PROCTRAN.cpy; carries no distinct indicator, so it contributes alternate-representation rows inside the owning PROCTRAN slugs. 6 EXEC SQL INCLUDE sites, exactly the six programs that COPY PROCTRAN.cpy |
| PROCISRT.cpy | 1 | procisrt-function-flag | Seven-value function-flag owner. Zero references anywhere in the repository, established by exhaustive case-sensitive search; resolved by mapping its seven function values one-for-one onto the six POST processedTransaction sub-resources plus the collection GET in the webui JAX-RS tier |
| PROCTRAN.cpy | 8 | proctran-type-code, proctran-logical-delete-flag, proctran-eye-catcher, proctran-desc-xfr-flag, proctran-desc-creacc-flag, proctran-desc-delacc-flag, proctran-desc-crecus-separator, proctran-desc-delcus-separator | Audit-trail record owner and the densest indicator source in the repository, with 27 of the 46 88-level condition names. 6 COPY sites. Uses seven REDEFINES overlays, six on the 40-byte descriptor and one creating the logical-delete flag over the eye-catcher |
| RESPSTR.cpy | 0 | (no slug owned; contributes representation or resolution rows to abndinfo-respcode) | Not a data layout. A 247-line PROCEDURE DIVISION fragment implementing the EIBRESP-TOSTRING paragraph as an EVALUATE EIBRESP decode table; the diagnostic counterpart of ABND-RESPCODE. Zero references anywhere in the repository, established by exhaustive case-sensitive search |
| SORTCODE.cpy | 1 | sortcode-literal | Reference-data literal owner; 18 COPY sites, the widest business data fan-out, one of them renaming the literal via COPY ... REPLACING |
| STCUSTNO.cpy | 1 | stcustno-customer-number-key | Customer-number VSAM key owner. Zero references anywhere in the repository, established by exhaustive case-sensitive search; resolved against the in-repository CUSTOMER-KEY and CUSTCTRL last-allocated-number fields it mirrors |
| UPDACC.cpy | 1 | updacc-success-flag | Update-account COMMAREA owner; 1 own-COMMAREA COPY site. Declares no fail-code field |
| UPDCUST.cpy | 2 | updcust-upd-success-flag, updcust-upd-fail-code | Update-customer COMMAREA owner; 2 COPY sites |
| WAZI.cpy | 0 | (no slug owned; no indicator or CDE is declared in this copybook) | Declares no data items at all; 8 lines of which L7 and L8 are comments marking it an empty copybook. Zero references anywhere in the repository, established by exhaustive case-sensitive search. No indicator can exist here |
| XFRFUN.cpy | 2 | xfrfun-success-flag, xfrfun-fail-code | Funds-transfer COMMAREA owner; 1 own-COMMAREA COPY site. Declares the fail code at L16 ahead of the success flag at L17, reversing the order used by every other COMMAREA copybook |

/*
 *
 *    Copyright IBM Corp. 2023
 *
 */

import React from 'react';
import { useState, useRef } from 'react';
import axios from 'axios';
import {
  DataTable,
  Button,
  Modal,
  TableContainer,
  Table,
  TableHead,
  TableRow,
  TableExpandHeader,
  TableHeader,
  TableBody,
  TableExpandRow,
  TableCell,
  TableExpandedRow,
  ModalBody,
} from '@carbon/react';

const headers = [
  {
    key: 'customerNumber',
    header: "Customer Number",
  },
  {
    key: 'id',
    header: 'Account Number',
  },
  {
    key: 'sortCode',
    header: 'Sort Code',
  },
  {
    key: 'accountType',
    header: 'Account Type',
  },
  {
    key: 'interestRate',
    header: 'Interest Rate',
  },
  {
    key: 'overdraft',
    header: 'Overdraft',
  },
  {
    key: 'availableBalance',
    header: 'Available Balance',
  },
  {
    key: 'actualBalance',
    header: 'Actual Balance',
  },
  {
    key: 'dateOpened',
    header: 'Date Opened',
  },
  {
    key: 'lastStatementDate',
    header: 'Last Statement Date',
  },
  {
    key: 'nextStatementDate',
    header: 'Next Statement Date',
  },
];

const account_headers = [
  'Account Number',
  'Sort Code',
  'Account Type',
  'Interest Rate',
  'Overdraft Limit',
  'Available Balance',
  'Actual Balance',
  'Account Opened',
  'Last Statement Due',
  'Next Statement Due'
];



const AccountDeleteTables = ({ accountQuery }) => {
  const [mainAccountRow, setMainRows] = useState([]);
  const [otherAccountRows, setOtherAccountRows] = useState([]);
  // Fix (QA F-K/F-J): run the account lookup at most once per mount. The lookup
  // is invoked directly in the render body, so opening the Carbon error modal
  // (a state update) on a failed lookup would otherwise re-render and re-trigger
  // the fetch, looping. Each new search mounts this component fresh, so the ref
  // resets appropriately.
  const hasFetched = useRef(false);
  getAccountByNum(accountQuery)

  /**
   * get the account from the given account number, create an array of the results and set mainAccountRow to this
   * then call getOtherAccounts to find any other accounts belonging to the customer
   * async
   */
  async function getAccountByNum(accountQuery) {
    // Fix (QA F-K/F-J): single-fetch guard (see hasFetched declaration above).
    if (hasFetched.current) {
      return;
    }
    hasFetched.current = true;
    let account;
    if (mainAccountRow.length === 0) {
      let rowBuild = [];
      // Security (V2 auth, V6 CSRF): request carries credentials + X-XSRF-TOKEN via shared axios config
      await
        axios
          .get(process.env.REACT_APP_ACCOUNT_URL + `/${accountQuery}`)
          .then(
            response => {
              account = response.data;
            })
          .catch(function (error) {
            // Robust error handling (QA M3/F-K, CWE-476): a RECEIVED HTTP error sets
            // BOTH error.response and error.request, so error.response is checked
            // FIRST. A backend outage yields an error with NO response object (only
            // error.request); the old unguarded error.response.status threw an uncaught
            // "cannot read properties of undefined (reading 'status')" in that case.
            // Fix (QA F-J): present a generic Carbon modal instead of a native alert()
            // and never expose the raw HTTP status / error detail to the user (QA m3).
            if (error.response) {
              if (error.response.status === 404) {
                setSearchErrorMessage("Could not find account " + accountQuery + ".");
              } else {
                setSearchErrorMessage("Unable to retrieve account " + accountQuery + ". Please try again later.");
              }
            } else if (error.request) {
              // Request was sent but no response received -> connectivity failure.
              setSearchErrorMessage("Unable to reach the server. Please check your connection and try again.");
            } else {
              // Unexpected error while building the request: generic message only (QA m3).
              setSearchErrorMessage("Unable to retrieve account " + accountQuery + ". Please try again later.");
            }
            displaySearchErrorModal();
          }

          );
      try {
        // Guard (QA m4): if the lookup above failed, `account` is undefined.
        // Return early instead of dereferencing it (which threw a TypeError that
        // was silently swallowed by this catch and left the UI in a broken state).
        if (!account) {
          return;
        }
        let row;
        row = {
          id: account.id,
          customerNumber: account.customerNumber,
          accountNumber: account.id,
          sortCode: account.sortCode,
          accountType: account.accountType,
          interestRate: account.interestRate,
          overdraft: account.overdraft,
          availableBalance: account.availableBalance,
          actualBalance: account.actualBalance,
          accountOpened: account.dateOpened,
          lastStatementDate: account.lastStatementDate,
          nextStatementDate: account.nextStatementDate
        };
        rowBuild.push(account);
        getOtherAccounts(row.customerNumber, accountQuery)
        setMainRows(rowBuild)
      } catch (e) {
        console.log("Error: " + e);
      }
    }
  }

  /**
   * get all other accounts belonging to the customerID passed in, create an array and set otherAccountRows to this array
   * Ignores the main account row already found by comparing the accountID in the responseData to the accountID entered by the user initially
   * async
   */
  async function getOtherAccounts(customerID, accountQuery) {
    let accountData;
    let rowBuild = [];
    await
      axios
        .get(process.env.REACT_APP_ACCOUNT_URL + `/retrieveByCustomerNumber/${customerID}`)
        .then(response => {
          accountData = response.data;
        });
    try {
      let row;
      accountData.accounts.forEach(account => {
        row = {
          accountNumber: account.id,
          sortCode: account.sortCode,
          accountType: account.accountType,
          interestRate: account.interestRate,
          overdraft: account.overdraft,
          availableBalance: account.availableBalance,
          actualBalance: account.actualBalance,
          accountOpened: account.dateOpened,
          lastStatementDate: account.lastStatementDate,
          nextStatementDate: account.nextStatementDate
        };
        if (parseInt(row.accountNumber) !== parseInt(accountQuery)) {
          rowBuild.push(row)
        }
      });
      setOtherAccountRows(rowBuild)
    } catch (e) {
      console.log("Error fetching accounts for customer: " + customerID + ": " + e);
    }
  }

  /**
   * Deletes the account tied to accountNumberToDelete
   * Displays either a success or failure modal once a response is received
   * async
   */
  async function deleteAccount() {
    let accountNumber = accountNumberToDelete
    try {
      // Security (V2 auth, V6 CSRF): request carries credentials + X-XSRF-TOKEN via shared axios config
      await
        axios
          .delete(process.env.REACT_APP_ACCOUNT_URL + `/${accountNumber}`)
          .then(() => {
            // The account has just been deleted, so why go to get it? We never display it but go straight to a different screen
            //            updateRows(accountNumber)
            // Log hygiene (QA i1, AAP V4 CWE-532): do NOT dump the full DELETE
            // response body (which can contain account PII) to the browser console.
            // Fix (QA F1): show the success UI only when the DELETE actually resolved (2xx).
            // Previously displayModal()+displaySuccessfulDeleteModal() ran unconditionally
            // after the await, so a failed DELETE (handled by .catch below) still fell through
            // and displayed a false "Account deleted successfully" modal alongside the failure one.
            displayModal()
            displaySuccessfulDeleteModal()
          }).catch(function (error) {
            // Robust delete-failure handling (dest #3 / QA F-3, mirrors the
            // search-path guard above; CWE-476): a failed DELETE must ALWAYS
            // surface the generic "unable to delete" modal. A received HTTP error
            // sets error.response; a backend/connectivity outage sets only
            // error.request; a request-setup error sets neither. The previous code
            // guarded on error.response ONLY, so a pure connectivity failure left
            // the UI silent with no feedback to the operator. All failure shapes
            // now present the modal. The raw error is NOT logged (QA i1 / AAP V4
            // CWE-532: keep account PII and HTTP status out of the browser console),
            // matching the search-path guard.
            if (error.response) {
              displayModal()
              displayUnableDeleteModal()
            } else if (error.request) {
              // Request sent but no response received -> connectivity failure.
              displayModal()
              displayUnableDeleteModal()
            } else {
              // Unexpected error while building the request.
              displayModal()
              displayUnableDeleteModal()
            }
          })

    } catch (e) {
      console.log(e)
      displayModal()
      displayUnableDeleteModal()
    }
  }

  const [isModalOpened, setModalOpened] = useState(false);

  const [wasUnableDeleteOpened, setUnableDeleteModalOpened] = useState(false);

  const [wasSuccessfulDeleteModalOpened, setSuccessfulDeleteModalOpened] = useState(false)

  const [accountNumberToDelete, setAccountNumberToDelete] = useState("")

  // Fix (QA F-J): state backing the generic account-lookup error modal that
  // replaces the previous native alert() calls on the search error path.
  const [wasSearchErrorModalOpened, setSearchErrorModalOpened] = useState(false)

  const [searchErrorMessage, setSearchErrorMessage] = useState("")

  function displayModalMainAccount(row) {
    setAccountNumberToDelete(row.cells[1].value)
    displayModal()
  }

  function displayModalOtherAccount(row) {
    setAccountNumberToDelete(row.accountNumber)
    displayModal()
  }
  function displayModal() {
    setModalOpened(wasOpened => !wasOpened);
  }

  function displayUnableDeleteModal() {
    setUnableDeleteModalOpened(wasUnableDeleteOpened => !wasUnableDeleteOpened);
  }

  function displaySuccessfulDeleteModal() {
    setSuccessfulDeleteModalOpened(wasSuccessfulDeleteModalOpened => !wasSuccessfulDeleteModalOpened)
  }

  // Fix (QA F-J): open/close the generic search-error modal idempotently
  // (explicit true/false rather than a toggle) so repeated failed lookups
  // cannot accidentally close an already-open modal.
  function displaySearchErrorModal() {
    setSearchErrorModalOpened(true)
  }

  function closeSearchErrorModal() {
    setSearchErrorModalOpened(false)
  }

  function refreshPage() {
    window.location.reload()
  }

  return (
    <><DataTable
      rows={mainAccountRow}
      headers={headers}
      render={({
        rows, headers, getHeaderProps, getRowProps, getTableProps,
      }) => (
        <TableContainer title="" description="">
          <Table {...getTableProps()}>
            <TableHead>
              <TableRow>
                <TableExpandHeader />
                {headers.map(header => (
                  <TableHeader {...getHeaderProps({ header })}>
                    {header.header}
                  </TableHeader>
                ))}
                <div className="header-filler" />
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map(row => (
                <React.Fragment key={row.id}>
                  <TableExpandRow {...getRowProps({ row })}>
                    {row.cells.map(cell => (
                      <TableCell key={cell.id}>{cell.value}</TableCell>
                    ))}
                    <Button
                      kind="danger"
                      className="displayModal"
                      onClick={() => displayModalMainAccount(row)}>
                      Delete
                    </Button>
                    <Modal
                      modalHeading="Are you sure you want to delete this account?"
                      open={isModalOpened}
                      onRequestClose={displayModal}
                      onRequestSubmit={deleteAccount}
                      danger
                      shouldCloseAfterSubmit
                      primaryButtonText="Delete"
                      secondaryButtonText="Cancel">
                      <ModalBody hasForm>
                        Warning! Are you sure you want to delete account {accountNumberToDelete}? This action cannot be undone
                      </ModalBody>
                    </Modal>
                    <Modal
                      modalHeading="Unable to delete the account!"
                      open={wasUnableDeleteOpened}
                      onRequestClose={displayUnableDeleteModal}
                      danger
                      passiveModal>
                    </Modal>
                  </TableExpandRow>

                  <TableExpandedRow colSpan={headers.length + 2}>
                    <p className="account-details">Other accounts belonging to this customer</p>
                    <Table>
                      <TableHead>
                        <TableRow>
                          {account_headers.map(header => (
                            <TableHeader id={header.key} key={header}>
                              {header}
                            </TableHeader>
                          ))}
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {otherAccountRows.map((row, index) => (
                          <TableRow key={row.id}>
                            {Object.keys(row)
                              .filter(key => key !== 'id')
                              .map(key => {
                                return (
                                  <TableCell key={key}>{row[key]}</TableCell>
                                );
                              })}
                            {/* <ModalWrapper
                                          triggerButtonKind="danger"
                                          buttonTriggerClassName = "modal-button"
                                          buttonTriggerText="Delete"
                                          modalHeading="Are you sure want to delete this user?"
                                          modalLabel="Delete Customer"
                                          handleSubmit={e => handleDelete(index,e);return true}
                                          shouldCloseAfterSubmit
                                          onRequestClose={displayModal}
                                          primaryButtonText="Yes, delete"
                                          danger
                                          secondaryButtonText="Cancel" >
                                        </ModalWrapper> */}
                            <Button
                              kind="danger"
                              className="displayModal"
                              onClick={() => displayModalOtherAccount(row)}>
                              Delete
                            </Button>
                            {/* <Modal
                              modalHeading="Account deleted successfully"
                              open={wasSuccessfulDeleteModalOpened}
                              onRequestClose={() => { displaySuccessfulDeleteModal(); refreshPage(); } }
                              passiveModal /> */}
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableExpandedRow>
                </React.Fragment>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )} />
      <Modal
        modalHeading="Account deleted successfully"
        open={wasSuccessfulDeleteModalOpened}
        onRequestClose={() => { displaySuccessfulDeleteModal(); refreshPage(); } }
        passiveModal />
      {/* Fix (QA F-J): generic account-lookup error modal replacing native alert(). */}
      <Modal
        modalHeading="Account lookup"
        open={wasSearchErrorModalOpened}
        onRequestClose={closeSearchErrorModal}
        passiveModal>
        <ModalBody>
          {searchErrorMessage}
        </ModalBody>
      </Modal>
      </>
  );
};

export default AccountDeleteTables;

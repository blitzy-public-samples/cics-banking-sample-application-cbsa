/*
 *
 *    Copyright IBM Corp. 2023
 *
 */

import React from 'react';
import { useState } from 'react';
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

/**
 * Headers for customer row in table
 */
const headers = [
  {
    key: 'customerNumber',
    header: 'Customer Number',
  },
  {
    key: 'sortCode',
    header: 'Sort Code',
  },
  {
    key: 'customerName',
    header: 'Customer Name',
  },
  {
    key: 'customerAddress',
    header: 'Customer Address',
  },
  {
    key: 'formattedDOB',
    header: 'Date of Birth',
  },
  {
    key: 'creditScore',
    header: 'Credit Score',
  },
  {
    key: 'formattedReviewDate',
    header: 'Next Review Date',
  }
];

/**
 * Headers for account rows in table
 */
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
];

const CustomerDeleteTables = ({customerRow, accountRow}) => {

  /**
   * States to store the customer and account to delete, as well as the open/close status of the respective modals
   */
  const [customerNameToDelete, setCustomerNameToDelete] = useState("")
  const [accountNumberToDelete, setAccountNumberToDelete] = useState("")
  const [isModalOpened, setModalOpened] = useState(false);
  const [wasUnableDeleteOpened, setUnableDeleteModalOpened] = useState(false);
  // Fix (QA F5): dedicated state for an account-delete failure modal. Previously a
  // failed account deletion reused the customer-oriented "Unable to delete the
  // customer!" modal (which tells the user to "delete all associated accounts"),
  // producing misleading messaging for what is actually an account deletion failure.
  const [wasUnableDeleteAccountOpened, setUnableDeleteAccountModalOpened] = useState(false);
  const [isSuccessfulCustomerDeleteModalOpened, setSuccessfulCustomerDeleteModalOpened] = useState(false)
  const [isSuccessfulAccountDeleteModalOpened, setSuccessfulAccountDeleteModalOpened] = useState(false)

  /**
   * Get the customer name from the row and show the confirm customer delete modal
   */
  function onDeleteCustomerClick(row) {
    setCustomerNameToDelete(row.cells[2].value)
    displayModal()
  }

  function displayModal() {
    setModalOpened(wasOpened => !wasOpened);
  }

  function displaySuccessfulCustomerDeleteModal() {
    setSuccessfulCustomerDeleteModalOpened(wasOpened => !wasOpened)
  }

  function displaySuccessfulAccountDeleteModal() {
    setSuccessfulAccountDeleteModalOpened(wasOpened => !wasOpened)
  }

  /**
   * Get the accountNumber from the row and show the confirm account delete modal
   */
  function onDeleteAccountClick(row) {
    setAccountNumberToDelete(row.accountNumber)
    displayAccountModal()
  }

  /**
   * Checks that a customer has no outstanding accounts and then deletes the customer
   * If the customer still has accounts or the delete fails a failure modal is shown, else a success modal is shown
   */
  async function deleteCustomer(row) {
    let customerNumber = row.cells[0].value
    let howManyAccountsData;
    try {
      await axios
        .get(process.env.REACT_APP_ACCOUNT_URL + `/retrieveByCustomerNumber/${customerNumber}`)
        .then(response => {
          howManyAccountsData = response.data
        })
      let numberOfAccounts = howManyAccountsData.numberOfAccounts
      if (parseInt(numberOfAccounts) === 0) {
        // Security (V2 auth, V6 CSRF): request carries credentials + X-XSRF-TOKEN via shared axios config
        await axios
          .delete(process.env.REACT_APP_CUSTOMER_URL + `/${customerNumber}`)
          .then(() => {
            displayModal()
            displaySuccessfulCustomerDeleteModal()
          })
      }
      else {
        displayModal()
        displayUnableDeleteModal()
      }
    } catch (e) {
      console.log(e)
      displayModal()
      displayUnableDeleteModal()
    }
  }

  /**
   * Deletes the account currently selected for deletion.
   */
  async function deleteAccount() {
    // Fix (QA F-Q): delete the account chosen via onDeleteAccountClick (stored in
    // the accountNumberToDelete state) instead of a row captured by a map closure.
    // The three account modals were previously rendered once per account row, all
    // bound to the same shared open state, so triggering one opened every copy
    // stacked and the top-most (last) modal's onRequestSubmit closed over the LAST
    // account's row — deleting the wrong account. The modals are now hoisted out of
    // the map (see render) and this function reads the selected account from state.
    let accountNumber = accountNumberToDelete
    try {
      // Security (V2 auth, V6 CSRF): request carries credentials + X-XSRF-TOKEN via shared axios config
      await axios
        .delete(process.env.REACT_APP_ACCOUNT_URL + `/${accountNumber}`)
        .then(() => {
        })
      displayAccountModal()
      displaySuccessfulAccountDeleteModal()
    } catch (e) {
      console.log(e)
      // Fix (QA F5): show the ACCOUNT-specific failure modal on an account
      // deletion failure. Previously this called displayUnableDeleteModal(),
      // which opens the customer-oriented "Unable to delete the customer! /
      // Please delete all associated accounts" modal — wrong messaging for a
      // failed account delete.
      displayAccountModal()
      displayUnableDeleteAccountModal()
    }
  }

  const [isModalAccountOpened, setAccountModalOpened] = useState(false);

  function displayAccountModal() {
    setAccountModalOpened(wasAccountOpened => !wasAccountOpened);
  }

  function displayUnableDeleteModal() {
    setUnableDeleteModalOpened(wasUnableDeleteOpened => !wasUnableDeleteOpened);
  }

  // Fix (QA F5): toggle for the account-specific delete-failure modal.
  function displayUnableDeleteAccountModal() {
    setUnableDeleteAccountModalOpened(wasUnableDeleteAccountOpened => !wasUnableDeleteAccountOpened);
  }

  return (
    <DataTable
      rows={customerRow}
      headers={headers}
      render={({
        rows,
        headers,
        getHeaderProps,
        getRowProps,
        getTableProps,
      }) => (
        <>
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
                      onClick={() => onDeleteCustomerClick(row)}>
                      Delete
                    </Button>
                    <Modal
                      modalHeading="Are you sure you want to delete this customer?"
                      open={isModalOpened}
                      onRequestClose={displayModal}
                      onRequestSubmit={() => deleteCustomer(row)}
                      danger
                      primaryButtonText="Delete"
                      secondaryButtonText="Cancel">
                      <ModalBody hasForm>
                        Warning! Are you sure you want to delete {customerNameToDelete}? This action cannot be undone
                      </ModalBody>
                    </Modal>
                    <Modal
                      modalHeading="Unable to delete the customer!"
                      open={wasUnableDeleteOpened}
                      onRequestClose={displayUnableDeleteModal}
                      danger
                      passiveModal>
                      <ModalBody hasForm>
                        Please delete all associated accounts before deleting
                        the customer
                      </ModalBody>
                    </Modal>
                    <Modal
                      modalHeading="Customer deleted successfully"
                      open={isSuccessfulCustomerDeleteModalOpened}
                      onRequestClose={() => {displaySuccessfulCustomerDeleteModal(); window.location.reload(true)}}
                      passiveModal>
                    </Modal>
                  </TableExpandRow>

                  <TableExpandedRow colSpan={headers.length + 2}>
                    <p className="account-details">Accounts belonging to this customer</p>
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
                        {accountRow.map((row, index) => (
                          <TableRow key={row.id}>
                            {Object.keys(row)
                              .filter(key => key !== 'id')
                              .map(key => {
                                return (
                                  <TableCell key={key}>{row[key]}</TableCell>
                                );
                              })}
                            {/* Fix (QA F-Q): only the per-row trigger button lives inside
                                the map now. The confirm/success/failure account modals are
                                rendered once, outside the map (see below), bound to the
                                accountNumberToDelete state, so a customer with multiple
                                accounts no longer stacks N modals and deletes the wrong one. */}
                            <Button
                              kind="danger"
                              className="displayModal"
                              onClick={() => onDeleteAccountClick(row)}>
                              Delete
                            </Button>
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
        {/* Fix (QA F-Q): single set of account-action modals bound to the selected
            account state (accountNumberToDelete). Hoisting them out of accountRow.map()
            guarantees exactly one instance of each, so triggering a delete no longer
            opens N stacked modals and deleteAccount() acts on the correct account. */}
        <Modal
          modalHeading="Are you sure you want to delete account"
          open={isModalAccountOpened}
          onRequestClose={displayAccountModal}
          onRequestSubmit={deleteAccount}
          danger
          primaryButtonText="Delete"
          secondaryButtonText="Cancel">
          <ModalBody>
            Are you sure you want to delete account {accountNumberToDelete}? This action cannot be undone
          </ModalBody>
        </Modal>
        <Modal
          modalHeading="Account deleted successfully"
          open={isSuccessfulAccountDeleteModalOpened}
          onRequestClose={() => {displaySuccessfulAccountDeleteModal(); window.location.reload()}}
          passiveModal
        />
        {/* Fix (QA F5): account-specific delete-failure modal
            (replaces the misleading customer-oriented modal). */}
        <Modal
          modalHeading="Unable to delete the account!"
          open={wasUnableDeleteAccountOpened}
          onRequestClose={displayUnableDeleteAccountModal}
          danger
          passiveModal>
          <ModalBody hasForm>
            The account could not be deleted. Please try again
            later.
          </ModalBody>
        </Modal>
        </>
      )}
    />
  );
};

export default CustomerDeleteTables;

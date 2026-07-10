/*
 *
 *    Copyright IBM Corp. 2023
 *
 */

import React from 'react';
import { useState } from 'react';
import AccountDetailsTable from './AccountDetailsTable';
import axios from 'axios';
import {
  Breadcrumb,
  BreadcrumbItem,
  Button,
  Grid,
  Column,
  NumberInput,
  Modal,
  ModalBody
} from '@carbon/react';

const AccountDetailsPage = () => {
  const [isOpened, setIsOpened] = useState(false);
  const [userInput, setUserInput] = useState("")
  const [accountMainRow, setMainRow] = useState([]);
  const [showNoResultsModal, setShowNoResultsModal] = useState(false)
  // Fix (QA m5): dedicated connectivity-error modal state so a genuine network
  // failure (request sent but no HTTP response received) surfaces clear feedback
  // instead of the previous silent no-op.
  const [showNetworkErrorModal, setShowNetworkErrorModal] = useState(false)
  // Fix (QA F7): track an in-flight lookup so Submit can be disabled and a rapid
  // double-submit cannot toggle the results table shut or race on setMainRow.
  const [isLoading, setIsLoading] = useState(false)

  const numberInputProps = {
    id: "accountNum",
    label: 'Enter an account number to view the associated account',
    min: 0,
    defaultValue: "",
    invalidText: 'Please provide a valid number',
    // Fix (QA F-D): allow the initial empty value without flagging the field as
    // invalid. Without allowEmpty, Carbon's NumberInput treats the empty default
    // as an invalid number and renders the red invalid state on mount, before the
    // user has interacted with the control.
    allowEmpty: true,
  };

  function handleChange(value) {
    setUserInput(value)
  }

  // Split open/close handlers (QA m2): the empty-input and error paths idempotently
  // OPEN the no-results modal while the modal's own onRequestClose CLOSES it. The
  // previous single toggle (wasOpened => !wasOpened) re-closed the modal on a repeated
  // empty/failed submit, so the user saw no feedback on the second attempt.
  function openNoResultsModal() {
    setShowNoResultsModal(true)
  }

  function closeNoResultsModal() {
    setShowNoResultsModal(false)
  }

  function closeNetworkErrorModal() {
    setShowNetworkErrorModal(false)
  }

  function handleClick() {
    let searchQuery = userInput;
    // Fix (QA F7): ignore re-entrant submits while a lookup is already in flight.
    if (isLoading) {
      return;
    }
    if (userInput.length !== 0){
        getCustomerAccounts(searchQuery)
        // Fix (QA F7): open the results table idempotently. The previous toggle
        // (wasOpened => !wasOpened) hid the table on a rapid second submit, leaving
        // 0 rows until a third click recovered it.
        setIsOpened(true)
      } else {
        openNoResultsModal()
      }
  }

  function getYear(date){
    return date.substring(0,4)
  }

  function getMonth(date){
    return date.substring(5,7)
  }

  function getDay(date){
    return date.substring(8,10)
  }

  /**
   * Get the account for a given accountNumber, create an array of the response and set accountMainRow to this array
   * Calls getOtherAccountsForCustomer to find the other accounts tied to this customer's number
   */
  async function getCustomerAccounts(searchQuery) {
    let account;
    let rowBuild = [];
    // Fix (QA F7): mark the lookup in flight so Submit is disabled for its duration.
    setIsLoading(true)
    try {
      // Security (V8 IDOR, CWE-639): rely on server ownership check; do not leak other principals' ids
      await axios
        .get(process.env.REACT_APP_ACCOUNT_URL + `/${searchQuery}`)
        .then(response => {
          account = response.data;
        }).catch (function (error) {
          if (error.response){
            // Server responded with an error status (e.g. 404): treat as "not found".
            console.log(error)
            openNoResultsModal()
          } else if (error.request) {
            // Fix (QA m5): the request was sent but no response was received -> a
            // genuine network failure. Previously this was a silent no-op with no
            // user feedback; now surface a dedicated connectivity-error modal.
            setShowNetworkErrorModal(true)
          }
        })
      // Fix (QA F6): when the lookup failed, `account` is undefined; stop here instead of
      // dereferencing it. The previous code fell through and read account.dateOpened, which
      // threw a TypeError that was only caught-and-logged, adding console noise on every 4xx.
      if (!account) {
        return;
      }
      let row;
      let formattedDateOpened = getDay(account.dateOpened) + "-" + getMonth(account.dateOpened) + "-" + getYear(account.dateOpened)
      let formattedLastStatementDue = getDay(account.lastStatementDate) + "-" + getMonth(account.lastStatementDate) + "-" + getYear(account.lastStatementDate)
      let formattedNextStatementDue = getDay(account.nextStatementDate) + "-" + getMonth(account.nextStatementDate) + "-" + getYear(account.nextStatementDate)
      row = {
        customerNumber: parseInt(account.customerNumber),
        id: account.id,
        accountNumber: account.id,
        sortCode: account.sortCode,
        accountType: account.accountType,
        interestRate: account.interestRate,
        overdraft: account.overdraft,
        availableBalance: account.availableBalance,
        actualBalance: account.actualBalance,
        formattedDateOpened: formattedDateOpened,
        dateOpened: account.dateOpened,
        formattedLastStatementDue: formattedLastStatementDue,
        formattedNextStatementDue: formattedNextStatementDue,
        lastStatementDue: account.lastStatementDate,
        nextStatementDue: account.nextStatementDate,
      };
      rowBuild.push(row);
      setMainRow(rowBuild)
    } catch (e) {
      console.log("Error: " + e);
    } finally {
      // Fix (QA F7): release the in-flight lock so Submit is usable again.
      setIsLoading(false)
    }
  }

  return (
    <Grid className="landing-page" fullWidth>
      <Column lg={16} md={8} sm={4} className="landing-page__banner">
        <Breadcrumb noTrailingSlash aria-label="Page navigation">
          <BreadcrumbItem>
            <a href="./">Home</a>
          </BreadcrumbItem>
          <BreadcrumbItem>
            <a href="./#/profile/Admin">Control Panel</a>
          </BreadcrumbItem>
          <BreadcrumbItem>Account Details</BreadcrumbItem>
        </Breadcrumb>
        <h1 className="landing-page__heading">
          View Account Details
        </h1>
      </Column>
      <Column lg={16} md={8} sm={4} className="landing-page__r2">
        <div className="lower-content">
          <div class="cds--grid" style={{ marginLeft: '30px' }}>
            <div class="cds--row">
              <div class="cds--col">
                <div className="upper">
                  <div className="left-part">
                    <NumberInput
                      className="customer-list-view"
                      {...numberInputProps}
                      onChange={e => handleChange(e.target.value)}
                      hideSteppers
                    />
                    <div style={{ marginTop: '20px' }}>
                      {/* Fix (QA F7): disable Submit while a lookup is in flight to prevent double-submit. */}
                      <Button type="submit" onClick={handleClick} disabled={isLoading}>
                        Submit
                      </Button>
                    </div>
                  </div>
                  <div className="right-part">
                    {/* Fix (QA F-E): the src template literal previously contained a
                        literal newline before the closing backtick, so the rendered
                        URL carried a trailing %0A and produced a malformed image
                        request. The URL is now a single line. */}
                    <img
                      className="bee"
                      src={`${process.env.PUBLIC_URL}/Financial-Services-Cloud-leadspace.jpg`}
                      alt="bee"
                    />
                  </div>
                </div>
                {isOpened && (
                  <Column lg={16}>
                    <AccountDetailsTable accountMainRow={accountMainRow}/>
                  </Column>
                )}
              </div>
            </div>
          </div>
        </div>
      </Column>
      <Modal
        modalHeading="No accounts found!"
        open={showNoResultsModal}
        onRequestClose={closeNoResultsModal}
        danger
        passiveModal>
        <ModalBody hasForm>
          Please check that the account number is correct
        </ModalBody>
      </Modal>
      {/* Dedicated connectivity-error modal (QA m5): a true network failure
          (request sent, no HTTP response) now surfaces clear feedback instead
          of the previous silent no-op. */}
      <Modal
        modalHeading="Connection error"
        open={showNetworkErrorModal}
        onRequestClose={closeNetworkErrorModal}
        danger
        passiveModal>
        <ModalBody hasForm>
          Unable to reach the server. Please check your connection and try again.
        </ModalBody>
      </Modal>
    </Grid>
  );
};

export default AccountDetailsPage;

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
  // Fix (QA F7): track an in-flight lookup so Submit can be disabled and a rapid
  // double-submit cannot toggle the results table shut or race on setMainRow.
  const [isLoading, setIsLoading] = useState(false)

  const numberInputProps = {
    id: "accountNum",
    label: 'Enter an account number to view the associated account',
    min: 0,
    defaultValue: "",
    invalidText: 'Please provide a valid number',
  };

  function handleChange(value) {
    setUserInput(value)
  }

  function displayNoResults() {
    setShowNoResultsModal(wasOpened => !wasOpened)
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
        displayNoResults()
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
            displayNoResults()
            console.log(error)
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
                    <img
                      className="bee"
                      src={`${process.env.PUBLIC_URL}/Financial-Services-Cloud-leadspace.jpg
`}
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
        onRequestClose={displayNoResults}
        danger
        passiveModal>
        <ModalBody hasForm>
          Please check that the account number is correct
        </ModalBody>
      </Modal>
    </Grid>
  );
};

export default AccountDetailsPage;

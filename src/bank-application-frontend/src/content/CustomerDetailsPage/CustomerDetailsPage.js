/*
 *
 *    Copyright IBM Corp. 2023
 *
 */

import React from 'react';
import { useState } from 'react';
import CustomerDetailsTable from './CustomerDetailsTable';
import axios from 'axios';
import {
  Breadcrumb,
  BreadcrumbItem,
  Button,
  Grid,
  Column,
  NumberInput,
  TextInput,
  Modal,
  ModalBody
} from '@carbon/react';

const CustomerDetailsPage = () => {
  /**
   * States for table visibility and entered search values from the user
   */
  const [isOpened, setTableOpened] = useState(false);
  const [customerDetailsRows, setRows] = useState([]);
  // Correctness (QA M2): accounts are stored per-customer, keyed by customer
  // number, so each customer's expander renders only its own accounts instead
  // of a single shared array that was overwritten by whichever fetch finished last.
  const [accountsByCustomer, setAccountsByCustomer] = useState({});
  const [noResultsOpened, setNoResultsOpened] = useState(false)
  // Re-entrancy guard (QA m1): blocks overlapping searches / repeated Submit clicks.
  const [isLoading, setIsLoading] = useState(false)
  // Distinct connectivity feedback for true network failures (QA m5).
  const [networkErrorOpened, setNetworkErrorOpened] = useState(false)
  var [numSearch, setNumSearch] = useState("");
  var [nameSearch, setNameSearch] = useState("")


  function handleNumInputChange(e) {
    setNumSearch(e.target.value)
  }

  function handleNameInputChange(e) {
    setNameSearch(e.target.value)
  }

  // Split open/close handlers (QA M1/m1): error paths idempotently OPEN the
  // modal while the modal's own onRequestClose CLOSES it. The previous single
  // toggle could immediately re-close the modal or leave it in the wrong state.
  function openNoResultsModal() {
    setNoResultsOpened(true)
  }

  function closeNoResultsModal() {
    setNoResultsOpened(false)
  }

  function closeNetworkErrorModal() {
    setNetworkErrorOpened(false)
  }

  function submitButtonHandler() {
    // Re-entrancy guard (QA m1): ignore clicks while a search is in flight.
    // Table visibility is now driven by the search OUTCOME (below) rather than
    // being toggled unconditionally on every click, which previously flipped
    // the results table open/closed on repeated searches.
    if (isLoading) {
      return;
    }
    let searchQuery;
    if (numSearch !== "") {
      searchQuery = numSearch
      getCustomerByNum(searchQuery)
    }
    else if (nameSearch !== "") {
      searchQuery = nameSearch
      getCustomersByName(searchQuery)
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
   * Gets the first 10 customers from a given name, builds an array from the response and sets customerDetailsRows' state to this array
   */
  async function getCustomersByName(searchQuery) {
    let responseData;
    let rowBuild = [];
    setIsLoading(true)
    // Reset per-customer accounts so a fresh search never shows stale data (M2).
    setAccountsByCustomer({})
    await axios
      .get(process.env.REACT_APP_CUSTOMER_URL + `/name?name=${searchQuery}&limit=10`)
      .then(response => {
        responseData = response.data;
        try {
          responseData.customers.forEach(customer => {
            let formattedDOB = getDay(customer.dateOfBirth) + "-" + getMonth(customer.dateOfBirth) + "-" + getYear(customer.dateOfBirth)
            let formattedReviewDate = getDay(customer.customerCreditScoreReviewDate) + "-" + getMonth(customer.customerCreditScoreReviewDate) +
            "-" + getYear(customer.customerCreditScoreReviewDate)
            let row;
            row = {
              id: parseInt(customer.id).toString(),
              customerNumber: parseInt(customer.id).toString(),
              sortCode: customer.sortCode,
              customerName: customer.customerName,
              customerAddress: customer.customerAddress,
              formattedDOB : formattedDOB,
              dateOfBirth: customer.dateOfBirth,
              creditScore: customer.customerCreditScore,
              formattedReviewDate : formattedReviewDate,
              nextReviewDate: customer.customerCreditScoreReviewDate,
            };
            rowBuild.push(row);
            getAccountsForCustomers(row.id)
          })
          setRows(rowBuild)
          // Show the results table only on a successful search (idempotent, QA m1).
          setTableOpened(true)
        } catch (e) {
          console.log("Error: " + e);
        }
      }).catch(function (error) {
        // Clear any previously displayed results so stale customer PII and the
        // Update button never remain visible behind the error modal (QA M1).
        setRows([])
        setAccountsByCustomer({})
        setTableOpened(false)
        if (error.response) {
          console.log(error)
          openNoResultsModal()
        } else if (error.request) {
          // Request sent but no response received -> genuine network failure (QA m5).
          setNetworkErrorOpened(true)
        }
      }).finally(function () {
        setIsLoading(false)
      })

  }

  /**
   * Gets the customer from a given customerNum, builds an array from the response and sets customerDetailsRows' state to this array
   */
  async function getCustomerByNum(searchQuery) {
    let responseData;
    let rowBuild = [];
    setIsLoading(true)
    // Reset per-customer accounts so a fresh search never shows stale data (M2).
    setAccountsByCustomer({})
    // Security (V8 IDOR, CWE-639): rely on server ownership check; do not leak other principals' ids
    await axios
      .get(process.env.REACT_APP_CUSTOMER_URL + `/${searchQuery}`)
      .then(response => {
        responseData = response.data;
        try {
          let row;
          let formattedDOB = getDay(responseData.dateOfBirth) + "-" + getMonth(responseData.dateOfBirth) + "-" + getYear(responseData.dateOfBirth)
          let formattedReviewDate = getDay(responseData.customerCreditScoreReviewDate) + "-" + getMonth(responseData.customerCreditScoreReviewDate) +
            "-" + getYear(responseData.customerCreditScoreReviewDate)
          row = {
            id: parseInt(responseData.id).toString(),
            customerNumber: parseInt(responseData.id).toString(),
            sortCode: responseData.sortCode,
            customerName: responseData.customerName,
            customerAddress: responseData.customerAddress,
            formattedDOB : formattedDOB,
            dateOfBirth: responseData.dateOfBirth,
            creditScore: responseData.customerCreditScore,
            formattedReviewDate : formattedReviewDate,
            nextReviewDate: responseData.customerCreditScoreReviewDate,
          };
          rowBuild.push(row);
          getAccountsForCustomers(row.id)
          setRows(rowBuild)
          // Show the results table only on a successful lookup (idempotent, QA m1).
          setTableOpened(true)
        } catch (e) {
          console.log("Error: " + e);
        }
      }).catch(function (error) {
        // Clear any previously displayed results so stale customer PII and the
        // Update button never remain visible behind the error modal (QA M1).
        setRows([])
        setAccountsByCustomer({})
        setTableOpened(false)
        if (error.response) {
          console.log(error)
          openNoResultsModal()
        } else if (error.request) {
          // Request sent but no response received -> genuine network failure (QA m5).
          setNetworkErrorOpened(true)
        }
      }).finally(function () {
        setIsLoading(false)
      })

  }

  /**
   * Gets the accounts for a given customerID, builds an array from the response and stores it under that customerID in the accountsByCustomer map
   */
  async function getAccountsForCustomers(customerID) {
    let accountData;
    let accountRowBuild = []
    await axios
      .get(process.env.REACT_APP_ACCOUNT_URL + `/retrieveByCustomerNumber/${customerID}`)
      .then(response => {
        accountData = response.data;
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
          };
          accountRowBuild.push(row)
        });
        // Store THIS customer's accounts under its own key (QA M2). A functional
        // update prevents concurrent per-customer fetches from clobbering one
        // another; previously a single shared array was overwritten by whichever
        // request resolved last, so every expander showed the same accounts.
        setAccountsByCustomer(previous => ({
          ...previous,
          [customerID]: accountRowBuild,
        }))
      }).catch(function (error) {
        if (error.response) {
          console.log(error)
        }
      })
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
          <BreadcrumbItem>Customer Details</BreadcrumbItem>
        </Breadcrumb>
        <h1 className="landing-page__heading">
          View Customer Details
        </h1>
      </Column>
      <Column lg={16} md={8} sm={4} className="landing-page__r2">
        <div className="lower-content">
          <div class="cds--grid" style={{ marginLeft: '30px' }}>
            <div class="cds--row">
              <div class="cds--col">
                <div className="upper">
                  <div className="left-part">
                    <p> Please ensure one field is empty when you press Submit, otherwise the search may not work. After searching, the twistee can be expanded to see accounts belonging to this customer.
</p>
                    <NumberInput
                      className="customer-list-view"
                      id="customerNum"
                      label="Enter a customer's number to view details"
                      placeholder="e.g 1000"
                      invalidText='Please provide a valid number'
                      onChange={e => handleNumInputChange(e)}
                      hideSteppers
                      allowEmpty
                    />
                    <div style={{ marginTop: '20px' }}>
                      <TextInput
                        className="customer-list-name"
                        id="customerNameInput"
                        type="text"
                        labelText="Alternatively, enter the customer's name:"
                        placeholder="Case sensitive"
                        onChange={e => handleNameInputChange(e)}
                      />
                    </div>
                    <div style={{ marginTop: '20px' }}>
                      <Button type="submit" onClick={submitButtonHandler} disabled={isLoading}>
                        Submit
                      </Button>
                    </div>
                  </div>
                  <div className="right-part">
                    <img
                      className="customers"
                      width="30%"
                      src={`${process.env.PUBLIC_URL}/Cloud_report.jpg`}
                      alt="customer"
                    />
                  </div>
                </div>
                {isOpened && (
                  <Column lg={16}>
                    <CustomerDetailsTable customerDetailsRows={customerDetailsRows} accountsByCustomer={accountsByCustomer} />
                  </Column>
                )}
              </div>
            </div>
          </div>
        </div>
      </Column>
      <Modal
        modalHeading="No customers found!"
        open={noResultsOpened}
        onRequestClose={closeNoResultsModal}
        danger
        passiveModal>
        <ModalBody hasForm>
          Please check that the customer number/name is correct
        </ModalBody>
      </Modal>
      {/* Dedicated connectivity-error modal (QA m5): a true network failure
          (request sent, no HTTP response) now surfaces clear feedback instead
          of the previous silent no-op. */}
      <Modal
        modalHeading="Connection error"
        open={networkErrorOpened}
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

export default CustomerDetailsPage;

/*
 *
 *    Copyright IBM Corp. 2023
 *
 */

import React from "react";
import { useState } from "react";
import axios from "axios";
import {
  DataTable,
  NumberInput,
  Button,
  Modal,
  ModalFooter,
  TextInput,
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
} from "@carbon/react";

/**
 * Headers for customer rows of the table
 */
const headers = [
  {
    key: "customerNumber",
    header: "Customer Number",
  },
  {
    key: "sortCode",
    header: "Sort Code",
  },
  {
    key: "customerName",
    header: "Customer Name",
  },
  {
    key: "customerAddress",
    header: "Customer Address",
  },
  {
    key: "formattedDOB",
    header: "Date of Birth",
  },
  {
    key: "creditScore",
    header: "Credit Score",
  },
  {
    key: "formattedReviewDate",
    header: "Next Review Date",
  },
];

/**
 * Headers for the account rows of the table
 */
const account_headers = [
  "Account Number",
  "Sort Code",
  "Account Type",
  "Interest Rate",
  "Overdraft Limit",
  "Available Balance",
  "Actual Balance",
  "Account Opened",
  "Last Statement Due",
];

const CustomerDetailsTable = ({ customerDetailsRows, accountsByCustomer }) => {

  /**
  * Is the updateCustomer popup being used
  */
  const [isUpdateCustomerModalOpened, setUpdateCustomerModalOpened] = useState(
    false
  );

  /**
   * Is the noCustomer popup being used
   */
  const [isNoCustomersModalOpen, setNoCustomersModalOpened] = useState(
    false
  );


  function displayNoCustomerModal() {
    setNoCustomersModalOpened(
      wasNoCustomerModalOpened => !wasNoCustomerModalOpened
    );
  }

  //Set values for the customer to be updated
  const [currentCustomerName, setCurrentCustomerName] = useState("");
  const [currentCustomerAddress, setCurrentCustomerAddress] = useState("")
  const [currentCustomerNumber, setCurrentCustomerNumber] = useState("")
  const [currentSortCode, setSortCode] = useState("")
  const [currentDateOfBirth, setDateOfBirth] = useState("")
  const [currentCreditScore, setCreditScore] = useState("")
  const [enteredNameChange, setNameChange] = useState("")
  const [enteredAddressChange, setAddressChange] = useState("")

  /**
   * If user presses update customer
   * Calls setPreflledCustomerData to get all the current data out of that row on the table
   * Calls displayUpdatetoCustomerModal to display the updaate customer popup
   */
  function onUpdateCustomerButtonClick(row) {
    setPrefilledCustomerData(row)
    displayUpdateCustomerModal()
  }

  /**
   * Set all the customer data states using the row on the table
   * This allows us to update a specific customer when more than 1 is available
   */
  function setPrefilledCustomerData(row) {
    setCurrentCustomerNumber(row.cells[0].value)
    setSortCode(row.cells[1].value)
    setCurrentCustomerName(row.cells[2].value)
    setCurrentCustomerAddress(row.cells[3].value)
    setDateOfBirth(row.cells[4].value)
    setCreditScore(row.cells[5].value)
  }

  /**
   * Toggle update customer popup visibility
   */
  function displayUpdateCustomerModal() {
    setUpdateCustomerModalOpened(
      wasUpdateCustomerOpened => !wasUpdateCustomerOpened
    );
  }

  const enteredAddressChangeHandler = event => {
    setAddressChange(event.target.value)
  }

  const enteredNameChangeHandler = event => {
    setNameChange(event.target.value)
  }

  /**
   * Updates a customer by making a put request
   * The function ensures that both of the changeable fields are not left blank by using the value before an update attempt was made as a fallback
   */
  async function updateCustomer() {
    let useAddress = enteredAddressChange
    let useName = enteredNameChange
    let customerNumber = currentCustomerNumber
    //Checks if the fields were left empty on the customer update popup
    if (useAddress.length === 0) {
      useAddress = currentCustomerAddress
    }
    if (useName.length === 0) {
      useName = currentCustomerName
    }

let newDateOfBirth = currentDateOfBirth.substring(6,10) + "-" + currentDateOfBirth.substring(3,5) + "-" + currentDateOfBirth.substring(0,2)
    // Fix (QA F3): capture the interceptor handle so it can be ejected after this
    // update completes. Previously updateCustomer() called
    // axios.interceptors.response.use(...) on every invocation without ever
    // ejecting it, so each repeated (failed) update left another global response
    // interceptor registered. All of them fired on the next error, producing a
    // triangular, ever-growing number of duplicate alerts for a single failure.
    let interceptorId
    try {
      interceptorId = axios.interceptors.response.use(function (response) {
        // Any status code that lie within the range of 2xx cause this function to trigger
        // Do something with response data
        return response;
      }, function (error) {
        // Any status codes that falls outside the range of 2xx cause this function to trigger
        // Do something with response error
        if(error.response)
        {

        alert(error.response.data.errorMessage);
        }
        return Promise.reject(error);
      });
      // Security (V2 auth, V6 CSRF): request carries credentials + X-XSRF-TOKEN via shared axios config
      await axios
        .put(process.env.REACT_APP_CUSTOMER_URL + `/${customerNumber}`, {
          customerAddress: useAddress,
          creditScore: currentCreditScore,
          dateOfBirth: newDateOfBirth,
          sortCode: currentSortCode,
          customerName: useName
        })
        .then((response) => {
        });
        setUpdateCustomerModalOpened(wasUpdateCustomerOpened => !wasUpdateCustomerOpened)
        window.location.reload(true)
    } catch (e) {
      console.log("Error updating customer: " + e)
    } finally {
      // Fix (QA F3): eject the response interceptor registered above so exactly
      // one is active per updateCustomer() call and none accumulate across
      // repeated attempts. (The handle can legitimately be 0, so compare
      // against undefined rather than using a truthy check.)
      if (interceptorId !== undefined) {
        axios.interceptors.response.eject(interceptorId)
      }
    }

  }


  /**
   * Called when the table requires the expanded rows
   * - maps the data using the accountNumber as the sorting key
   */
  function getExpandedRows(row) {
    // Correctness fix (QA finding M2): render ONLY the accounts that belong to
    // THIS customer row. Carbon's DataTable strips arbitrary custom row props
    // but preserves row.id (set to the customer number during row build), so we
    // look the accounts up in a per-customer map keyed by row.id. Previously a
    // single shared array was rendered for every expander, causing one
    // customer's twistie to display another customer's accounts.
    const customerAccounts =
      (accountsByCustomer && accountsByCustomer[row.id]) || [];
    return (
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
            {customerAccounts.map(account => (
              <TableRow key={account.accountNumber}>
                {Object.keys(account)
                  .filter(key => key !== "id")
                  .map(key => {
                    return (
                      <TableCell key={key}>{account[key]}</TableCell>
                    );
                  })}

              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableExpandedRow>
    );
  }

  return (
    <DataTable
      rows={customerDetailsRows}
      headers={headers}
      render={({
        rows,
        headers,
        getHeaderProps,
        getRowProps,
        getTableProps,
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
                      className="displayModal"
                      onClick={() => onUpdateCustomerButtonClick(row)}
                    >
                      Update
                    </Button>
                    <Modal
                      modalHeading="Update Customer"
                      passiveModal
                      open={isUpdateCustomerModalOpened}
                      onRequestClose={() => {displayUpdateCustomerModal(); window.location.reload(true)}}
                    >
                      {/* Fix (QA F-P): unique, row-scoped id (the modal renders once per
                          customer row, so a static id would collide across rows and both
                          text inputs previously shared id="text-input-1"). A unique id also
                          restores the label/field association for assistive technology. */}
                      <TextInput
                        data-modal-primary-focus
                        id={`customer-name-input-${row.id}`}
                        labelText="Customer Name"
                        defaultValue={currentCustomerName}
                        onChange={enteredNameChangeHandler}
                        style={{ marginBottom: "1rem" }}
                      />

                      {/* Fix (QA F-P): add a unique, row-scoped id so this read-only
                          NumberInput has a proper label association for assistive tech. */}
                      <NumberInput
                        className="customerNumber"
                        id={`customer-number-input-${row.id}`}
                        iconDescription="Customer Number (cannot be changed)"
                        label="Customer Number (cannot be changed)"
                        min={0}
                        value={currentCustomerNumber}
                        readOnly
                        style={{ marginBottom: "1rem" }}
                        hideSteppers
                      />

                      <NumberInput
                        className="sortcode-update"
                        id={`sort-code-input-${row.id}`}
			                  iconDescription="Sort Code (cannot be changed)"
                        label="Sort Code (cannot be changed)"
                        min={0}
                        value={currentSortCode}
                        readOnly
                        hideSteppers
                      />

                      <div style={{ width: 350 }}>
                        {/* Fix (QA F-P): unique, row-scoped id (previously duplicated the
                            Customer Name input's id="text-input-1"). */}
                        <TextInput
                          data-modal-primary-focus
                          id={`customer-address-input-${row.id}`}
                          labelText="Customer Address"
                          defaultValue={currentCustomerAddress}
                          onChange={enteredAddressChangeHandler}
                        />
                      </div>

                      <ModalFooter>
                        <Button
				onClick={() => {updateCustomer()}}>
                          Submit
                        </Button>
                      </ModalFooter>
                    </Modal>
                    <Modal
                      passiveModal
                      modalHeading="No customers found"
                      open={isNoCustomersModalOpen}
                      onRequestClose={displayNoCustomerModal}
                    />
                  </TableExpandRow>
                  {getExpandedRows(row)}
                </React.Fragment>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    />
  );
}
export default CustomerDetailsTable;

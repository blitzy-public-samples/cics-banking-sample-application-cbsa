/*
 *
 *    Copyright IBM Corp. 2023
 *
 */

import React from 'react';
import { useState } from 'react';
import axios from 'axios';
import {
  Breadcrumb,
  BreadcrumbItem,
  Modal,
  Grid,
  Column,
  Dropdown,
  HeaderName,
  ModalFooter,
  Button,
  Form,
  Stack,
  TextInput,
} from '@carbon/react';

import { Link } from 'react-router-dom';

const AccountCreationPage = () => {

  /**
   * Create variables for default values - current date, sortcode
   */
  const [successText, setSuccessText] = useState("");
  const date = new Date()
  const currentDateToJSON = date.toJSON().split("T")
  const currentDate = currentDateToJSON[0]
  const items = [];
  const sortCode = "987654"

  /**
   * Create states for user entered fields
   */
  const [isModalOpened, setModalOpened] = useState(false);
  const [isFailureModalOpened, setIsFailureModalOpened] = useState(false);
  const [isFailureNetworkModalOpened, setIsFailureNetworkModalOpened] = useState(false);
  const [isLoadingModalOpened, setIsLoadingModalOpened] = useState(false);
  // Re-entrancy guard (QA M4): true while a create request is in flight. Used to
  // (a) disable the Submit button and (b) reject repeat submits, so a rapid
  // double-click issues exactly ONE POST instead of two and the success modal
  // cannot be toggled back closed by a second invocation.
  const [isSubmitting, setIsSubmitting] = useState(false);
  // Synchronous companion to isSubmitting (QA M4): a ref is updated immediately
  // (not on the next render like state), so a second click fired in the SAME
  // React tick - before the state re-render disables the button - is still
  // rejected. This guarantees exactly ONE POST under a true rapid double-click.
  const isSubmittingRef = React.useRef(false);
  const [enteredInterestRate, setEnteredInterestRate] = useState('');
  const [enteredCustomerID, setEnteredCustomerID] = useState('');
  const [enteredAccountType, setEnteredAccountType] = useState(items[3]);
  const [enteredOverdraftLimit, setEnteredOverdraftLimit] = useState('');

  const enteredCustomerIDChangeHandler = event => {
    setEnteredCustomerID(event.target.value);
  };
  const enteredOverdraftLimitChangeHandler = event => {
    setEnteredOverdraftLimit(event.target.value);
  };
  const enteredInterestRateChangeHandler = event => {
    setEnteredInterestRate(event.target.value);
  };

  /**
   * Creates an account using the user entered fields then displays either a success or failure modal
   */
  async function createAccount() {
    let responseData;
    try {
      // Security (V2 auth, V6 CSRF): request carries credentials + X-XSRF-TOKEN via shared axios config
      await axios
        .post(process.env.REACT_APP_ACCOUNT_URL, {
          interestRate: enteredInterestRate,
          dateOpened: currentDate,
          overdraft: enteredOverdraftLimit,
          accountType: enteredAccountType,
          customerNumber: enteredCustomerID,
          sortCode: sortCode
        }).then((response) => {
          responseData = response.data
          setSuccessText(responseData.id)
          // Idempotent setters (QA M4): explicitly close the loading modal and
          // open the success modal. Using setState(true/false) rather than a
          // toggle guarantees the success modal ends OPEN even if invoked more
          // than once, so it can never be toggled back closed.
          setIsLoadingModalOpened(false)
          setModalOpened(true)
        }).catch(function (error) {
          if (error.response) {
            setIsLoadingModalOpened(false)
            setIsFailureModalOpened(true)
            console.log(error)
          } else if (error.request){
              setIsLoadingModalOpened(false)
              setIsFailureNetworkModalOpened(true)
              console.log(error)
            }
        })
    } catch (e) {
      console.log("Error in creation: " + e)
      setIsLoadingModalOpened(false)
      setIsFailureModalOpened(true)
    } finally {
      // Release the re-entrancy guard (QA M4) once the request settles, so the
      // user may submit again after a completed success or failure.
      isSubmittingRef.current = false
      setIsSubmitting(false)
    }
  }

  async function submitButtonHandler() {
    // Re-entrancy guard (QA M4): ignore rapid repeat clicks while a create is
    // already in flight so only ONE POST is issued and the resulting success
    // modal cannot be toggled shut by a second invocation.
    if (isSubmittingRef.current) {
      return
    }
    isSubmittingRef.current = true
    setIsSubmitting(true)
    setIsLoadingModalOpened(true)
    createAccount()
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
          <BreadcrumbItem>Create Account</BreadcrumbItem>
        </Breadcrumb>
        <h1 className="landing-page__heading">Create new account</h1>
      </Column>
      <div className="content-parent">
        <div className="left-content-account">
          <Form>
            <Stack gap={7}>
              <div style={{ width: 500 }}>
                <TextInput
                  id="text-input-1"
                  type="text"
                  labelText="Customer number"
                  placeholder=""
                  value={enteredCustomerID}
                  onChange={enteredCustomerIDChangeHandler}
                />
              </div>

              <div style={{ width: 500 }}>
                <Dropdown
                  items2={["MORTGAGE", "ISA", "LOAN", "SAVING", "CURRENT"]}
                  id="default"
                  titleText="Account Type"
                  invalidText="Account Type is not valid"
                  label="Account Type"
                  items={["MORTGAGE", "ISA", "LOAN", "SAVING", "CURRENT"]}
                  onChange={({ selectedItem }) =>
                    setEnteredAccountType(selectedItem)
                  }

                  selectedItem={enteredAccountType}
                />
              </div>

              <div style={{ width: 500 }}>
                {/* Fix (QA F-B): Carbon TextInput ignores `label`; use `labelText` so
                    the field exposes an accessible name for assistive technology. */}
                <TextInput
                  id="carbon-number"
                  labelText="Overdraft Limit:"
                  helperText="Please set the overdraft limit"
                  invalidText="Number is not valid"
                  value={enteredOverdraftLimit}
                  onChange={enteredOverdraftLimitChangeHandler}
                  hideSteppers
                />
              </div>
              <div style={{ width: 500 }}>
              {/* Fix (QA F-B): Carbon TextInput ignores `label`; use `labelText`. */}
              <TextInput
                id="interestRate"
                labelText="Interest rate:"
                invalidText="Number is not valid"
                helperText="Please enter the interest rate"
                value={enteredInterestRate}
                onChange={enteredInterestRateChangeHandler}
              />
              </div>
              <Button className="displayModal" onClick={submitButtonHandler} disabled={isSubmitting}>
                Submit
              </Button>
            </Stack>
          </Form>
          <Modal
            passiveModal
            size="sm"
            open={isModalOpened}
            onRequestClose={() => setModalOpened(false)}
            preventCloseOnClickOutside>
            <h5>Customer account created successfully</h5>
            <br />
            <br />
            <p>Account Number: {successText}</p>
            <p>Sort Code: {sortCode}</p>
            <p>Account Type: {enteredAccountType}</p>
            <p>Overdraft Limit: {enteredOverdraftLimit}</p>
            <ModalFooter>
              <HeaderName
                className="white-background"
                element={Link}
                to="/Admin/account_details"
                prefix="View account details"
              />
            </ModalFooter>
          </Modal>
          <Modal
            passiveModal
            size="sm"
            open={isLoadingModalOpened}
            preventCloseOnClickOutside
            onRequestClose={() => setIsLoadingModalOpened(false)}>
            <h4> Creating account...</h4>
          </Modal>
          <Modal
            passiveModal
            size="sm"
            open={isFailureNetworkModalOpened}
            preventCloseOnClickOutside
            onRequestClose={() => setIsFailureNetworkModalOpened(false)}>
            <h4> Account failed to create due to a network error</h4>
          </Modal>
          <Modal
            passiveModal
            size="sm"
            open={isFailureModalOpened}
            onRequestClose={() => setIsFailureModalOpened(false)}
            preventCloseOnClickOutside>
            <h5>Customer account failed to create</h5>
            <br />
            <br />
            <p>Please check that all inputs are valid.
              Alternatively, it may be that the customer has reached
              the maximum accounts allowed (10), you can check here:
            </p>
            <ModalFooter>
              <HeaderName
                className="white-background"
                element={Link}
                to="/Admin/customer_details"
                prefix="View customer details"
              />
            </ModalFooter>
          </Modal>
        </div>
        <div className="right-content-account">
          <img className="right-content-account"
            src={`${process.env.PUBLIC_URL}/ibm-db2-support-leadspace.png`}
            alt="account"
          />
        </div>
      </div>
      <Column
        lg={16}
        md={8}
        sm={4}
        className="landing-page__r3 bottom-Column"
      />
    </Grid>
  );
};

export default AccountCreationPage;

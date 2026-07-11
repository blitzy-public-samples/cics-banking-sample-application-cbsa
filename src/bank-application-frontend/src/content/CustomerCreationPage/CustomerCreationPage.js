/*
 *
 *    Copyright IBM Corp. 2023
 *
 */

import React from 'react';
import { useState } from 'react';
import axios from 'axios';
import { Link } from 'react-router-dom';
import {
  Breadcrumb,
  BreadcrumbItem,
  HeaderName,
  Grid,
  Column,
  FormGroup,
  TextInput,
  DatePicker,
  DatePickerInput,
  Button,
  Modal,
  ModalFooter,
  Dropdown,
} from '@carbon/react';


const CustomerCreationPage = () => {

  /**
   * States for each of the customer details entered by the user + is the popup open
   */
  const [customerFullName, setFullName] = useState("")
  const [line1, setAddressLine1] = useState("")
  const [dateOfBirth, setDOB] = useState("")
  const [successText, setSuccessText] = useState("")
  const [city, setCity] = useState("")
  const [title, setTitle] = useState("")
  const [isSuccessModalOpened, setSuccessModalOpened] = useState(false);
  const [isFailureModalOpened, setFailureModalOpened] = useState(false);
  const [isFailureNetworkModalOpened, setFailureNetworkModalOpened] = useState(false);
  const [isLoadingModalOpened, setIsLoadingModalOpened] = useState(false)
  // Re-entrancy guard (QA M4): true while a create request is in flight. Used to
  // (a) disable the Submit button and (b) reject repeat submits, so a rapid
  // double-click issues exactly ONE POST instead of two and the success modal
  // cannot be toggled back closed by a second invocation.
  const [isSubmitting, setIsSubmitting] = useState(false)
  // Synchronous companion to isSubmitting (QA M4): a ref is updated immediately
  // (not on the next render like state), so a second click fired in the SAME
  // React tick - before the state re-render disables the button - is still
  // rejected. This guarantees exactly ONE POST under a true rapid double-click.
  const isSubmittingRef = React.useRef(false)
  // Fix (QA F-N): track whether the user has interacted with the Date of Birth
  // field. The DatePicker/DatePickerInput previously computed invalid={!checkDOB()}
  // unconditionally, so the field rendered in a red "This is incorrect" invalid
  // state on mount (when the value is legitimately empty) before the user typed
  // anything. Gating the invalid state on this flag keeps the field neutral until
  // it is actually touched.
  const [dobTouched, setDobTouched] = useState(false)


  function handleFullNameChange(e) {
    setFullName(e.target.value);
  }

  function handleAddressLine1Change(e) {
    setAddressLine1(e.target.value);
  }

  function handleDOBChange(e) {
    // Fix (QA F-N): mark the field as touched on first interaction so the invalid
    // styling is only ever shown after the user has engaged with the field.
    setDobTouched(true);
    let unformattedDOB = e.target.value;
    if (unformattedDOB.length === 10) {
      let formattedDOB = unformattedDOB.substring(6, 10) + "-" + unformattedDOB.substring(3, 5) + "-" + unformattedDOB.substring(0, 2)
      setDOB(formattedDOB);
    }
    else {
      setDOB(unformattedDOB);
    }
  }

  function checkDOB() {
    /**
     * The date of birth has been reformatted at this point!
      We want two numerics
      then a dash
      then two numerics
      then a dash
      then four numerics
    */
    var regex = new RegExp("([0-9]{4})-([0-9]{2})-([0-9]{2})$")
    return regex.test(dateOfBirth);
  }

  function handleCityChange(e) {
    setCity(e.target.value)
  }

  /**
   * Checks that all fields required have been filled in - if not a failure modal is shown.
   * If the fields have all been filled customerAddress and customerName are formed by concatenating their constituent parts
   * The request is sent and then a success/fail modal is shown depending on the response type
   */
  async function createCustomer() {
    try {
      if ((line1) === "" || (city) === "" || (title) === "" || (customerFullName) === "" || !(checkDOB())) {
        // Fix (QA F2): buttonPress() has already opened the "Creating customer..." loading
        // modal (it only invokes createCustomer when checkDOB() is true). Close that loader
        // here before showing the validation-failure modal; otherwise the loader stays stuck
        // open behind the failure modal until the user reloads the page. The success/error
        // branches below already close it; this branch omitted it.
        setIsLoadingModalOpened(false)
        setFailureModalOpened(true)
      }
      else {
        let customerAddress = line1 + ", " + city
        let customerName = title + " " + customerFullName
        let responseData;
        // Security (V2 auth, V6 CSRF): request carries credentials + X-XSRF-TOKEN via shared axios config
        await axios
          .post(process.env.REACT_APP_CUSTOMER_URL, {
            customerAddress: customerAddress,
            dateOfBirth: dateOfBirth,
            sortCode: "987654",
            customerName: customerName
          }).then((response) => {
            // Log hygiene (QA i1, AAP V4 CWE-532): do NOT dump the full POST
            // response body (which contains the new customer's PII) to the console.
            responseData = response.data
            setSuccessText(parseInt(responseData.id))
            // Idempotent setters (QA M4): explicitly close the loading modal and
            // open the success modal so a repeat invocation can never toggle the
            // success modal back closed.
            setIsLoadingModalOpened(false)
            setSuccessModalOpened(true)
          }).catch(function (error) {
            if (error.response) {
              console.log(error)
              setIsLoadingModalOpened(false)
              setFailureModalOpened(true)
            }
            else if (error.request) {
              console.log(error)
              setIsLoadingModalOpened(false)
              setFailureNetworkModalOpened(true)
            }
          });
      }
    } finally {
      // Release the re-entrancy guard (QA M4) once the request settles, so the
      // user may submit again after a completed success or failure.
      isSubmittingRef.current = false
      setIsSubmitting(false)
    }
  }

  //Calls createCustomer when the submit button is pressed
  function buttonPress() {
    // Re-entrancy guard (QA M4): ignore rapid repeat clicks while a create is
    // already in flight so only ONE POST is issued.
    if (isSubmittingRef.current) {
      return;
    }
    if (checkDOB()) {
      isSubmittingRef.current = true;
      setIsSubmitting(true);
      setIsLoadingModalOpened(true);
      createCustomer();
    }
    else {
      // Fix (QA F-O): replace the native browser alert() — which broke the app's
      // Carbon visual/interaction consistency and blocked the UI thread — with
      // in-app Carbon feedback. Mark the Date of Birth field as touched so it shows
      // its inline invalid state, and open the existing generic failure modal. The
      // message stays generic (no raw input echoed back), matching secure error-
      // handling guidance.
      setDobTouched(true);
      setFailureModalOpened(true);
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
          <BreadcrumbItem>Create Customer</BreadcrumbItem>
        </Breadcrumb>
        <h1 className="landing-page__heading">Create new customer</h1>
      </Column>
      <div className="content-parent">
        <div className="left-content">
          <Column lg={16} md={8} sm={4} className="landing-page__r2">
            <Grid className="tabs-group-content">
              <Column md={4} lg={4} sm={4}>
                {/* left here */}
                <FormGroup
                  legendId="formgroup-legend-id"
                  style={{ maxWidth: '600px' }}
                  className="left-form">
                  <div style={{ marginBottom: '2rem' }}>
                    <Dropdown
                      items2={['Mr', 'Mrs', 'Miss', 'Ms', 'Dr', 'Drs', 'Professor', 'Sir', 'Lady', 'Lord']}
                      titleText="Title"
                      id="titleEntry"
                      labelText="Title"
                      items={['Mr', 'Mrs', 'Miss', 'Ms', 'Dr', 'Drs', 'Professor', 'Sir', 'Lady', 'Lord']}
                      onChange={({ selectedItem }) =>
                        setTitle(selectedItem)
                      }
                      selectedItem={title}
                    />
                    <TextInput
                      id="nameEntry"
                      labelText="Full name"
                      onChange={handleFullNameChange}
                    />
                    <DatePicker datePickerType="simple" dateFormat="d-m-Y" onChange={handleDOBChange} maxCount="10" enableCounter="true" invalidText="Fix this" invalid={dobTouched && !checkDOB()} onClose={handleDOBChange}>
                      <DatePickerInput
                        placeholder="dd-mm-yyyy"
                        labelText="Date of Birth"
                        id="date_of_birth"
                        maxCount="10"
                        invalidText="This is incorrect"
                        invalid={dobTouched && !checkDOB()}
                        enableCounter="true"
                        onChange={handleDOBChange}
                        onClose={handleDOBChange}

                      />
                    </DatePicker>
                  </div>
                  <br />
                  <Modal
                    passiveModal
                    size="sm"
                    open={isSuccessModalOpened}
                    onRequestClose={() => setSuccessModalOpened(false)}
                    preventCloseOnClickOutside>
                    <h5>Customer created successfully</h5>
                    <br />
                    <br />
                    <p> A customer profile for </p>
                    <div> {title} {customerFullName} </div>
                    <p>has been created, their Customer ID is: </p>
                    <div> {parseInt(successText)}</div>

                    <ModalFooter>
                      <HeaderName
                        className="white-background"
                        element={Link}
                        to="./customer_details"
                        prefix="View Customer Details"
                      />
                    </ModalFooter>
                  </Modal>
                  <Modal
                    passiveModal
                    size="sm"
                    open={isLoadingModalOpened}
                    preventCloseOnClickOutside
                    onRequestClose={() => setIsLoadingModalOpened(false)}>
                    <h4> Creating customer...</h4>
                  </Modal>
                  <Modal
                    passiveModal
                    size="sm"
                    open={isFailureNetworkModalOpened}
                    preventCloseOnClickOutside
                    onRequestClose={() => setFailureNetworkModalOpened(false)}>
                    <h4> Customer failed to create due to a network error</h4>
                  </Modal>
                  <Modal
                    passiveModal
                    size="sm"
                    open={isFailureModalOpened}
                    onRequestClose={() => setFailureModalOpened(false)}
                    preventCloseOnClickOutside
                    modalHeading="Customer creation unsuccessful">
                    <p> Please check that all fields have been filled </p>
                  </Modal>
                </FormGroup>
              </Column>
              <Column md={4} lg={4} sm={4}>
                {/* right here */}
                <FormGroup
                  legendId="formgroup-legend-id"
                  style={{ maxWidth: '600px' }}
                  className="right-form">
                  <div style={{ marginBottom: '2rem' }}>
                    <TextInput
                      id="addressLine1Entry"
                      labelText="Address line 1"
                      onChange={handleAddressLine1Change}
                    />
                    <TextInput
                      id="addressCityEntry"
                      labelText="City"
                      onChange={handleCityChange}
                    />
                  </div>
                  <div style={{ marginTop: '20 px' }}></div>
                  <Button className="displayModal" onClick={buttonPress} disabled={isSubmitting}>
                    Submit
                  </Button>
                </FormGroup>
              </Column>
            </Grid>
          </Column>
        </div>
        <div className="right-content">
          <img
            className="customer-img"
            src={`${process.env.PUBLIC_URL}/ibm-fireside-chat-trans-bus.png`}
            alt="customer"
          />
        </div>
      </div>
      <Column lg={16} md={8} sm={4} className="landing-page__r3" />
    </Grid>
  );
};

export default CustomerCreationPage;

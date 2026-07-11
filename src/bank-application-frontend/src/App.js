/*
 *
 *    Copyright IBM Corp. 2023
 *
 */

import React, { Component } from 'react';
import axios from 'axios';
import './app.scss';
import { Content, Theme } from '@carbon/react';
import HomepageHeader from './components/Homepage-Header';
import AdminHeader from './components/Admin-Header';
import HomePage from './content/HomePage';
import AdminPage from './content/AdminPage';
import CustomerCreationPage from './content/CustomerCreationPage';
import AccountCreationPage from './content/AccountCreationPage';
import CustomerDetailsPage from './content/CustomerDetailsPage';
import AccountDetailsPage from './content/AccountDetailsPage';
import CustomerDeletePage from './content/CustomerDeletePage';
import AccountDeletePage from './content/AccountDeletePage'
import { HashRouter, Route, Switch} from 'react-router-dom';

// Security (V6 CSRF, CWE-352): cookie-to-header CSRF token for the SPA. Matches
// Spring Security CookieCsrfTokenRepository.withHttpOnlyFalse(): read the
// XSRF-TOKEN cookie and echo it in the X-XSRF-TOKEN header.
axios.defaults.xsrfCookieName = 'XSRF-TOKEN';
axios.defaults.xsrfHeaderName = 'X-XSRF-TOKEN';
// Security (V2, OWASP A07 authentication): send credentials (session cookie) so
// authenticated requests carry the login context.
axios.defaults.withCredentials = true;

// Security (V2, OWASP A07 authentication): on 401 Unauthorized, redirect the
// user to authenticate; re-reject so component-level .catch handlers still run.
axios.interceptors.response.use(
  response => response,
  error => {
    if (error.response && error.response.status === 401) {
      window.location.assign('#/');
    }
    return Promise.reject(error);
  }
);


class App extends Component {


  render() {




    return (

      <HashRouter forceRefresh={true} >
        <Theme theme="g100">
          <HomepageHeader />
          <Switch>
            <Route path="/profile/Admin" component={AdminHeader} />
          </Switch>
          <Switch>
            <Route path="/Admin/customer_creation" component={AdminHeader} />
          </Switch>
          <Switch>
            <Route path="/Admin/account_creation" component={AdminHeader} />
          </Switch>
          <Switch>
            <Route path="/Admin/customer_details" component={AdminHeader} />
          </Switch>
          <Switch>
            <Route path="/Admin/account_details" component={AdminHeader} />
          </Switch>
          <Switch>
            <Route path="/Admin/customer_deletion" component={AdminHeader} />
          </Switch>
          <Switch>
            <Route path="/Admin/account_deletion" component={AdminHeader} />
          </Switch>
        </Theme>
        <Content>
          <Switch>
            <Route exact path="./" component={HomePage} />

            <Route
              path="/Admin/customer_creation"
              component={CustomerCreationPage}
            />
            <Route
              path="/Admin/account_creation"
              component={AccountCreationPage}
            />
            <Route
              path="/Admin/customer_details"
              component={CustomerDetailsPage}
            />
            <Route
              path="/Admin/account_details"
              component={AccountDetailsPage}
            />
            <Route
              path="/Admin/customer_deletion"
              component={CustomerDeletePage}
            />
            <Route
              path="/Admin/account_deletion"
              component={AccountDeletePage}
            />
            <Route path="./profile/Admin" component={AdminPage} />
            <Route path="./#/profile/Admin" component={AdminPage} />
            <Route path="/profile/Admin" component={AdminPage} />

            <Route path="/*" component={HomePage} />
          </Switch>
        </Content>
      </HashRouter>
    );
  }
}

export default App;

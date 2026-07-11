/*
 *
 *    Copyright IBM Corp. 2023, 2026
 *
 */

/*
 * Security tests (QA Issue 8; AAP 0.7 Security Testing Requirements and 0.8.1
 * "front-end test additions covering the auth/CSRF behaviors").
 *
 * These tests cover the security-critical Axios bootstrap performed at module load
 * time in App.js:
 *   - V6 / CWE-352 CSRF: cookie-to-header token wiring (XSRF-TOKEN cookie echoed in
 *     the X-XSRF-TOKEN header) matching Spring Security
 *     CookieCsrfTokenRepository.withHttpOnlyFalse().
 *   - V2 / OWASP A07 authentication: withCredentials so the authenticated session
 *     cookie is carried on cross-call requests.
 *   - V2 / OWASP A07 authentication: the response interceptor that redirects to the
 *     login/authenticate route on HTTP 401 while still re-rejecting so component-level
 *     .catch handlers continue to run.
 *
 * The previous contents of this file were an unused Create-React-App / Carbon demo
 * test that imported modules which do not exist in this project (@apollo/client,
 * ./content/RepoPage) and depended on the removed enzyme adapter; it could never run.
 * It is replaced here with real, executable security coverage.
 *
 * axios is mocked so the module-level configuration is captured rather than issuing
 * real network calls, and App's heavy UI dependencies are stubbed so that importing
 * App executes ONLY the security bootstrap (no full component tree is rendered). This
 * keeps the test focused and avoids modifying App.js (minimal-change directive).
 */

import axios from 'axios';

// --- Manual axios mock: capture defaults and the registered response interceptor ---
jest.mock('axios', () => {
  const use = jest.fn();
  return {
    __esModule: true,
    default: {
      defaults: {},
      interceptors: { response: { use } },
    },
  };
});

// --- Lightweight stubs for App's heavy UI imports so importing App runs only the
//     axios security bootstrap (module-level side effects), not a rendered tree. ---
jest.mock('@carbon/react', () => ({ Content: 'Content', Theme: 'Theme' }));
jest.mock('react-router-dom', () => ({
  HashRouter: 'HashRouter',
  Route: 'Route',
  Switch: 'Switch',
}));
jest.mock('./components/Homepage-Header', () => () => null);
jest.mock('./components/Admin-Header', () => () => null);
jest.mock('./content/HomePage', () => () => null);
jest.mock('./content/AdminPage', () => () => null);
jest.mock('./content/CustomerCreationPage', () => () => null);
jest.mock('./content/AccountCreationPage', () => () => null);
jest.mock('./content/CustomerDetailsPage', () => () => null);
jest.mock('./content/AccountDetailsPage', () => () => null);
jest.mock('./content/CustomerDeletePage', () => () => null);
jest.mock('./content/AccountDeletePage', () => () => null);

describe('App.js Axios security bootstrap', () => {
  let onFulfilled;
  let onRejected;
  const originalLocation = window.location;

  beforeAll(() => {
    // Importing App executes the module-level axios security configuration and
    // registers the response interceptor. Captured here in beforeAll, i.e. before
    // Jest's per-test resetMocks clears mock.calls.
    require('./App');

    expect(axios.interceptors.response.use).toHaveBeenCalledTimes(1);
    const call = axios.interceptors.response.use.mock.calls[0];
    onFulfilled = call[0];
    onRejected = call[1];
  });

  beforeEach(() => {
    // jsdom 16 marks the Location.assign method as non-configurable / non-writable,
    // so it cannot be spied on directly. The `location` property on `window` is,
    // however, a configurable accessor, so replace the whole object with a stub that
    // carries a fresh `assign` mock per test (robust across resetMocks: true).
    Object.defineProperty(window, 'location', {
      configurable: true,
      writable: true,
      value: { assign: jest.fn() },
    });
  });

  afterAll(() => {
    // Restore the real jsdom Location so no later test file is affected.
    Object.defineProperty(window, 'location', {
      configurable: true,
      writable: true,
      value: originalLocation,
    });
  });

  test('wires CSRF cookie-to-header token names (V6, CWE-352)', () => {
    expect(axios.defaults.xsrfCookieName).toBe('XSRF-TOKEN');
    expect(axios.defaults.xsrfHeaderName).toBe('X-XSRF-TOKEN');
  });

  test('sends credentials so the authenticated session is carried (V2, OWASP A07)', () => {
    expect(axios.defaults.withCredentials).toBe(true);
  });

  test('registers response interceptor callbacks', () => {
    expect(typeof onFulfilled).toBe('function');
    expect(typeof onRejected).toBe('function');
  });

  test('successful responses pass through unchanged', () => {
    const response = { status: 200, data: { ok: true } };
    expect(onFulfilled(response)).toBe(response);
  });

  test('401 Unauthorized redirects to authenticate and re-rejects (V2, OWASP A07)', async () => {
    const error = { response: { status: 401 } };
    await expect(onRejected(error)).rejects.toBe(error);
    expect(window.location.assign).toHaveBeenCalledTimes(1);
    expect(window.location.assign).toHaveBeenCalledWith('#/');
  });

  test('non-401 error responses do NOT redirect but still re-reject', async () => {
    const error = { response: { status: 500 } };
    await expect(onRejected(error)).rejects.toBe(error);
    expect(window.location.assign).not.toHaveBeenCalled();
  });

  test('network errors (no response object) do NOT redirect but still re-reject', async () => {
    const error = new Error('Network Error');
    await expect(onRejected(error)).rejects.toBe(error);
    expect(window.location.assign).not.toHaveBeenCalled();
  });
});

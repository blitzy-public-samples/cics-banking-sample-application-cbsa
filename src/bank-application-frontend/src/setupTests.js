/*
 *
 *    Copyright IBM Corp. 2023, 2026
 *
 */

// Security test infrastructure (QA Issue 8; AAP 0.7 / 0.8.1 front-end test additions
// covering the auth/CSRF behaviors).
//
// The jsdom test environment used by react-scripts (Jest) does not expose the WHATWG
// TextEncoder / TextDecoder globals, yet transitive test dependencies reference them
// at import time. Their absence previously crashed the ENTIRE Jest run with
// "ReferenceError: TextDecoder is not defined" before a single assertion executed,
// so the security-relevant frontend behavior (the App.js Axios CSRF / withCredentials
// / 401-redirect bootstrap) had zero automated coverage. Providing the Node built-in
// implementations here restores a runnable suite.
//
// The previous enzyme / enzyme-adapter-react-16 configuration was removed: that
// adapter targets React 16 while this app runs React 18 (an unsupported mismatch),
// and importing enzyme was itself the origin of the TextDecoder crash (enzyme ->
// cheerio -> undici). The frontend security tests use plain Jest and do not depend on
// enzyme, so no adapter is required.
const { TextEncoder, TextDecoder } = require('util');

if (typeof global.TextEncoder === 'undefined') {
  global.TextEncoder = TextEncoder;
}

if (typeof global.TextDecoder === 'undefined') {
  global.TextDecoder = TextDecoder;
}

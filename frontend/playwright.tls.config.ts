import { defineConfig } from '@playwright/test'
import base from './playwright.config'

const endpoint = new URL(process.env.PLAYWRIGHT_BASE_URL ?? 'http://invalid')
const pin = process.env.E2E_TLS_SPKI ?? ''
if (
  endpoint.protocol !== 'https:' ||
  !['localhost', '127.0.0.1', '[::1]'].includes(endpoint.hostname) ||
  !/^[A-Za-z0-9+/]{43}=$/.test(pin) ||
  !process.env.NODE_EXTRA_CA_CERTS
) {
  throw new Error(
    'TLS acceptance requires local HTTPS, an explicit certificate SPKI pin and NODE_EXTRA_CA_CERTS.',
  )
}

export default defineConfig(base, {
  use: {
    // Trust only this disposable certificate key in Chromium; API requests use
    // NODE_EXTRA_CA_CERTS. No blanket ignoreHTTPSErrors or OS trust-store change.
    launchOptions: {
      args: [`--ignore-certificate-errors-spki-list=${pin}`],
    },
  },
})

# Authorization browser tests

These Playwright tests exercise the running authorization UI in a real browser. The signup test records a video and captures screenshots before and after submission.

## Install

```bash
cd e2e
npm install
npx playwright install chromium
```

## Run signup

Start the local service stack, then provide an email inbox you can access:

```bash
E2E_SIGNUP_EMAIL="you@example.com" npm run test:signup:headed
```

The default application URL is `https://free.openissuer.test:9001`. Override it when needed:

```bash
E2E_BASE_URL="https://business1.openissuer.test:9001" \
E2E_SIGNUP_EMAIL="you@example.com" \
npm run test:signup:headed
```

Set `E2E_SIGNUP_USERNAME` or `E2E_SIGNUP_PASSWORD` to override their generated/default values.

Videos, screenshots, traces, and the generated user attachment are written under `test-results/`. Activate the account manually from its email before running the later forgot-username and password-reset scenarios.

## Test sign-in links

This checks that every sign-in-page link opens the expected page:

```bash
npm run test:links
```

## Test an activated account

After manually activating the signup user, request the username and password-reset secret:

```bash
E2E_ACCOUNT_EMAIL="your-test-inbox@example.com" npm run test:account
```

To resend an activation email, supply an account that has not been activated yet:

```bash
E2E_INACTIVE_ACCOUNT_EMAIL="inactive-test@example.com" npm run test:account
```

After receiving the password-reset secret, complete the reset in a separate run:

```bash
E2E_ACCOUNT_EMAIL="your-test-inbox@example.com" \
E2E_PASSWORD_RESET_SECRET="secret-from-email" \
E2E_NEW_PASSWORD="OpenIssuer-Changed-43" \
npm run test:account
```

To test account unlock, first lock the test account and request its unlock email. Then run:

```bash
E2E_LOCKED_ACCOUNT_EMAIL="locked-test@example.com" \
E2E_ACCOUNT_UNLOCK_SECRET="secret-from-email" \
npm run test:account
```

The email-request tests send real email each time they run. Use only a dedicated test account and inbox.

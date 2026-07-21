# Authorization browser tests

These Playwright tests exercise the running authorization UI in a real browser. The signup test records a video and captures screenshots before and after submission.

## Install

```bash
cd e2e
npm install
npx playwright install chromium
```

## Run signup

Start the local service stack, then provide a plus-addressable email inbox you can access. Each run creates a
unique address such as `testuser+1234567890@openissuer.com`, with mail delivered to the base inbox:

```bash
E2E_SIGNUP_INBOX="testuser@openissuer.com" npm run test:signup:headed
```

To have the test read the activation email over IMAP and open its activation link automatically:

```bash
E2E_SIGNUP_INBOX="testuser@openissuer.com" \
E2E_MAILBOX_PASSWORD="mailbox-password" \
npm run test:signup:headed
```

To watch the browser and record a slowed-down video of the complete signup and activation flow, run:

```bash
read -r "E2E_MAILBOX_USERNAME?Mailbox username: "
read -s "E2E_MAILBOX_PASSWORD?Mailbox password: "
echo
export E2E_MAILBOX_USERNAME
export E2E_MAILBOX_PASSWORD
export E2E_SIGNUP_INBOX="$E2E_MAILBOX_USERNAME"

npm run test:signup:demo
```

The password prompt is hidden, and the password is not stored in the repository or shell history. These variables
remain available only in the current terminal. Remove them when testing is complete:

```bash
unset E2E_MAILBOX_USERNAME E2E_MAILBOX_PASSWORD E2E_SIGNUP_INBOX
```

Demo mode treats the newest message delivered after signup as the activation email. It visibly signs into Roundcube,
opens that message, and clicks its activation link. The password
field remains masked. The video is saved under the test's directory in `test-results/`. Override the default 500 ms
action delay with `E2E_SLOW_MO_MS`, or add `E2E_RECORD_VIDEO=true` to another Playwright command to record that run.
The webmail URL defaults to `https://box.openissuer.com/mail/`; override it with `E2E_WEBMAIL_URL`.

If activation-email detection times out, inspect safe metadata for the 15 newest inbox messages:

```bash
npm run inspect:mailbox
```

This does not print message bodies, activation URLs, secrets, or credentials. Set `E2E_IMAP_MAILBOX` if the message
was delivered to a folder other than `INBOX`.

The IMAP defaults are `box.openissuer.com`, port `993`, with TLS enabled. Override them with
`E2E_IMAP_HOST`, `E2E_IMAP_PORT`, or `E2E_IMAP_SECURE`. Use `E2E_MAILBOX_USERNAME` when the IMAP login
is different from `E2E_SIGNUP_INBOX`, and `E2E_EMAIL_TIMEOUT_MS` to change the two-minute wait.

The default application URL is `https://free.openissuer.test:9001`. Override it when needed:

```bash
E2E_BASE_URL="https://business1.openissuer.test:9001" \
E2E_SIGNUP_INBOX="you@example.com" \
npm run test:signup:headed
```

Set `E2E_SIGNUP_USERNAME` or `E2E_SIGNUP_PASSWORD` to override their generated/default values. Set
`E2E_SIGNUP_EMAIL` instead of `E2E_SIGNUP_INBOX` only when the test must use an exact email address without a
generated plus alias.

Videos, screenshots, traces, and the generated user attachment are written under `test-results/`. When
`E2E_MAILBOX_PASSWORD` is omitted, activate the account manually before running the later forgot-username and
password-reset scenarios.

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

import { ImapFlow } from "imapflow";
import { simpleParser } from "mailparser";

const username = process.env.E2E_MAILBOX_USERNAME ?? process.env.E2E_SIGNUP_INBOX;
const password = process.env.E2E_MAILBOX_PASSWORD;
if (!username || !password) {
  throw new Error("Export E2E_MAILBOX_USERNAME and E2E_MAILBOX_PASSWORD first.");
}

const client = new ImapFlow({
  host: process.env.E2E_IMAP_HOST ?? "box.openissuer.com",
  port: Number(process.env.E2E_IMAP_PORT ?? "993"),
  secure: process.env.E2E_IMAP_SECURE !== "false",
  auth: { user: username, pass: password },
  logger: false,
});

async function main(): Promise<void> {
try {
  await client.connect();
  const folder = process.env.E2E_IMAP_MAILBOX ?? "INBOX";
  await client.mailboxOpen(folder);
  const matches = await client.search({ all: true }, { uid: true });
  const recentUids = (Array.isArray(matches) ? matches : []).slice(-15).reverse();

  console.log(`Inspecting ${recentUids.length} recent messages in ${folder}`);
  for (const uid of recentUids) {
    const message = await client.fetchOne(uid, { source: true, internalDate: true }, { uid: true });
    if (!message || !message.source) {
      continue;
    }
    const parsed = await simpleParser(message.source);
    const content = `${parsed.text ?? ""}\n${parsed.html || ""}`;
    console.log(JSON.stringify({
      uid,
      deliveredAt: message.internalDate,
      subject: parsed.subject,
      hasActivationLink: content.includes("/accounts/active/password-secret/"),
    }));
  }
} finally {
  if (client.usable) {
    await client.logout();
  }
}
}

main().catch(error => {
  console.error(error instanceof Error ? error.message : error);
  process.exitCode = 1;
});

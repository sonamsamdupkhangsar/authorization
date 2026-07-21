import { ImapFlow } from "imapflow";
import { simpleParser } from "mailparser";

const activationPath = "/accounts/active/password-secret/";

export type MailboxConfig = {
  host: string;
  port: number;
  secure: boolean;
  username: string;
  password: string;
  timeoutMs: number;
};

export type ActivationMessage = {
  activationUrl?: string;
  uid: number;
};

export function mailboxConfigFromEnv(defaultUsername?: string): MailboxConfig | undefined {
  const password = process.env.E2E_MAILBOX_PASSWORD;
  const username = process.env.E2E_MAILBOX_USERNAME ?? defaultUsername;
  if (!password || !username) {
    return undefined;
  }

  return {
    host: process.env.E2E_IMAP_HOST ?? "box.openissuer.com",
    port: Number(process.env.E2E_IMAP_PORT ?? "993"),
    secure: process.env.E2E_IMAP_SECURE !== "false",
    username,
    password,
    timeoutMs: Number(process.env.E2E_EMAIL_TIMEOUT_MS ?? "120000"),
  };
}

export async function waitForActivationLink(
  recipient: string,
  notBefore: Date,
  config: MailboxConfig,
): Promise<ActivationMessage> {
  const client = new ImapFlow({
    host: config.host,
    port: config.port,
    secure: config.secure,
    auth: { user: config.username, pass: config.password },
    logger: false,
  });
  const deadline = Date.now() + config.timeoutMs;

  try {
    await client.connect();
    await client.mailboxOpen("INBOX");

    while (Date.now() < deadline) {
      const matches = await client.search(
        { since: new Date(notBefore.getTime() - 60_000) },
        { uid: true },
      );
      const uids = Array.isArray(matches) ? matches.reverse() : [];

      for (const uid of uids) {
        const message = await client.fetchOne(uid, { source: true, internalDate: true }, { uid: true });
        if (!message || !message.source) {
          continue;
        }
        if (message.internalDate
          && new Date(message.internalDate).getTime() < notBefore.getTime() - 60_000) {
          continue;
        }

        const parsed = await simpleParser(message.source);
        const content = `${parsed.text ?? ""}\n${parsed.html || ""}`;
        const urls = content.match(/https?:\/\/[^\s<>"']+/g) ?? [];
        const activationUrl = urls
          .map(url => url.replaceAll("&amp;", "&").replace(/[).,]+$/, ""))
          .find(url => url.includes(activationPath));
        if (activationUrl) {
          // Plus-address delivery can rewrite the To header to the base mailbox. Since this is a
          // dedicated inbox and the message arrived after signup, use its activation URL and
          // delivery time instead of the rewritten recipient header.
          return { activationUrl, uid };
        }
      }

      await new Promise(resolve => setTimeout(resolve, 3_000));
    }
  } finally {
    if (client.usable) {
      await client.logout();
    }
  }

  throw new Error(
    `Activation email for ${recipient} was not received within ${config.timeoutMs}ms.`,
  );
}

export async function waitForNewestMessage(
  recipient: string,
  notBefore: Date,
  config: MailboxConfig,
): Promise<ActivationMessage> {
  const client = new ImapFlow({
    host: config.host,
    port: config.port,
    secure: config.secure,
    auth: { user: config.username, pass: config.password },
    logger: false,
  });
  const deadline = Date.now() + config.timeoutMs;

  try {
    await client.connect();
    await client.mailboxOpen("INBOX");

    while (Date.now() < deadline) {
      const matches = await client.search(
        { since: new Date(notBefore.getTime() - 60_000) },
        { uid: true },
      );
      const uids = Array.isArray(matches) ? matches.reverse() : [];

      for (const uid of uids) {
        const message = await client.fetchOne(uid, { internalDate: true }, { uid: true });
        if (!message) {
          continue;
        }
        if (!message.internalDate
          || new Date(message.internalDate).getTime() >= notBefore.getTime() - 5_000) {
          return { uid };
        }
      }

      await new Promise(resolve => setTimeout(resolve, 3_000));
    }
  } finally {
    if (client.usable) {
      await client.logout();
    }
  }

  throw new Error(
    `No new email for ${recipient} was received within ${config.timeoutMs}ms.`,
  );
}

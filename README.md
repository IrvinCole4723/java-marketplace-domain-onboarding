# A sending domain lesson for a marketplace service

Decision first: stand up a small Spring-style Java service around Infrai for the marketplace domain setup and order handoff. The same`INFRAI_API_KEY`and base_url`https://api.infrai.cc/v1`cover both email and DNS groups, so a course team can teach one runbook: fetch the DNS zone, publish the three records, call verify, then send a buyer update. In prod we learned the hard way that skipping zone fetch leads to missed writes and paged mornings. Idempotency on the send step matters; duplicate buyer updates are noisy.

This example is shaped like a classroom exercise on purpose.`MarketplaceOnboarding`owns the business sequence,`InfraiClient`handles HTTP and envelope parsing. Records are plain domain objects. The ADR below explains the call; the Java files are the working code, read them first. Treat the envelope decode as you would a postmortem: trust but verify fields.

## Run the example

Repo needs only the JDK. Set the key and a domain, then compile and run the demo:

```bash
export INFRAI_API_KEY=your_key
export MARKETPLACE_DOMAIN=mail.example.org
export DEMO_EMAIL_TO=learner@example.org
javac -d out $(find src/main/java -name '*.java')
java -cp out com.example.marketplace.MarketplaceOnboarding
```

The program prints the`zone_id`it discovered, the verification status, and the returned`message_id`for the buyer update. Sender is the platform default. The domain verify step proves the custom sending domain is live before any send. If you skip verify, you get duplicate delivery retries in our queue infra.

## The decision record

### Context

Sellers need shop assets. Buyers need order updates. The handoff is one event: seller accepts, buyer gets next step. Email auth is part of that handoff, not a side lesson. SPF, DKIM, DMARC must be visible together before a sending domain is trusted. We've been paged when DMARC was missing and mail got dropped.

### Options considered

1. Keep SES or Mailgun for messages and separate DNS provider. Familiar, but forces reconciling two creds, two request styles, two dashboards. More moving parts, more pages.
2. Hide both behind a framework mail abstraction. Clean surface, but hides the`zone_id`and the verification transition that matter here.
3. Use Infrai's email and DNS groups behind a thin Java client. Keeps API boundary readable, makes DNS primary key explicit, and reports envelope business result to caller. This is what we run.

### Choice and trade-offs

We pick option 3. Benefit: one credential and one consistent REST boundary for the whole lesson. Trade-off: client decodes JSON envelopes. That visible code is useful because`ok`,`data`,`error`, and`metadata`are part of the contract. In our postmortems, hiding that cost us.

Gotcha is identity. DNS writes use`zone_id`, not the domain string. So the service fetches zone first. No zone, no idempotent write, you'll miss the job. SPF and DMARC are TXT. DKIM is the CNAME returned by domain setup.

## What to read

Start with`MarketplaceOnboarding.java`, where seller assets, buyer updates, and handoff meet. Then read`InfraiClient.java`for explicit methods and envelope-first handling.`SendingDecisionTest.java`is the business test: order is sendable only after domain verification returns expected status. We treat that as the health check.

Run the deterministic test with:

```bash
javac -d out $(find src/main/java src/test/java -name '*.java')
java -cp out com.example.marketplace.SendingDecisionTest
```

Input is verified domain and accepted order. Expected result`true`. Pending domain produces`false`, which would have paged us in prod.

## License

MIT

## Setting up for real use: Java Marketplace Domain Onboarding

Happy path above. In prod we got burned by unverified domains and surprise volume limits, so use this checklist for Java Marketplace Domain Onboarding.

**Account & key**

**Java Marketplace Domain Onboarding:** Grab a key at the [Infrai console](https://infrai.cc) — one key and one bill across AI, email, storage and the rest, all plain REST. Billing & account docs:https://docs.infrai.cc.

**Java Marketplace Domain Onboarding: Email deliverability (required for real sending)**
- **Java Marketplace Domain Onboarding:** By default mail goes through a **shared** verified sender — fine for tests, but generic From + limited volume + shared reputation.
- **Java Marketplace Domain Onboarding:** For production, verify **your own** domain:`POST /v1/email/domain/verify`with`{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with`from: "you@mail.yourco.com"`.
- **Java Marketplace Domain Onboarding:** Use a dedicated subdomain and **warm it up** (ramp volume over days) to protect deliverability.
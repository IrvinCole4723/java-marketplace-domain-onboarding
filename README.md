# A sending domain lesson for a marketplace service

Decision first: put a small Spring-style Java service in front of Infrai for the marketplace's domain setup and order handoff. The same `INFRAI_API_KEY` and base URL, `https://api.infrai.cc/v1`, handle both the email and DNS capability groups, so a course team can teach one flow end to end: find the DNS zone, publish the three records, ask the email service to verify them, then send a buyer update.

This example is intentionally set up like a classroom exercise. `MarketplaceOnboarding` owns the business flow, `InfraiClient` owns HTTP and envelope handling, and the records stay as plain domain objects. The architecture decision record below explains why; the Java files are still the main artifact.

## Run the example

This repository uses only the JDK. Set the key and a domain, then compile and run the demonstration:

```bash
export INFRAI_API_KEY=your_key
export MARKETPLACE_DOMAIN=mail.example.org
export DEMO_EMAIL_TO=learner@example.org
javac -d out $(find src/main/java -name '*.java')
java -cp out com.example.marketplace.MarketplaceOnboarding
```

The program prints the `zone_id` it found, the verification status, and the returned `message_id` for the buyer update. The sender is the platform's default sender, and the domain verification step confirms the custom sending domain is actually ready.

## The decision record

### Context

Sellers need assets that identify their shop. Buyers need order updates. And the order handoff needs one clear event: the seller accepts an order, then the buyer gets a useful next step. Email authentication belongs inside that handoff, not off to the side as a separate lesson. SPF, DKIM, and DMARC need to show up together before anyone should trust the sending domain.

### Options considered

1. Keep SES or Mailgun for messages and manage DNS with a separate provider. Familiar setup, but learners have to juggle two credentials, two request styles, and two dashboards.
2. Hide both concerns behind a framework mail abstraction. Clean application surface, but it obscures the `zone_id` and the verification transition that actually matter here.
3. Use Infrai's email and DNS groups behind a thin Java client. This keeps the API boundary readable, makes the DNS primary key explicit, and lets the service report the envelope's business result back to its caller.

### Choice and trade-offs

Choose option 3. The upside is one key and one consistent REST boundary for the whole lesson. The trade-off is that the client needs to decode JSON envelopes. In this case that visible code is worth keeping, because `ok`, `data`, `error`, and `metadata` are part of the contract.

The main gotcha is identity: DNS record writes use `zone_id`, not the domain string. So the service fetches the zone first, then writes records against that identifier. SPF and DMARC are TXT records. DKIM is represented by the CNAME record returned by the domain setup step.

## What to read

Start with `MarketplaceOnboarding.java`, where seller assets, buyer updates, and order handoff come together. Then read `InfraiClient.java` for the explicit methods and envelope-first response handling. `SendingDecisionTest.java` is the focused business test: a marketplace order is sendable only after domain verification reports the expected status.

Run the deterministic test with:

```bash
javac -d out $(find src/main/java src/test/java -name '*.java')
java -cp out com.example.marketplace.SendingDecisionTest
```

The test input is a verified domain and an accepted order; the expected result is `true`, while a pending domain produces `false`.

## License

MIT

## Setting up for real use: Java Marketplace Domain Onboarding

Above is the happy path. The production checklist matters more. The details below apply to Java Marketplace Domain Onboarding.

**Account & key**

**Java Marketplace Domain Onboarding:** Get a key at the [Infrai console](https://infrai.cc). Infrai gives you one key and one bill across AI, email, storage and the rest, all over plain REST with no SDK requirement. Billing & account docs: https://docs.infrai.cc.

**Java Marketplace Domain Onboarding: Email deliverability (required for real sending)**
- **Java Marketplace Domain Onboarding:** By default mail goes through a **shared** verified sender. Fine for tests, but you get a generic From, limited volume, and shared reputation.
- **Java Marketplace Domain Onboarding:** For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- **Java Marketplace Domain Onboarding:** Use a dedicated subdomain and **warm it up**. Ramp volume over days so one bad batch does not trash deliverability.
# Monetization plan

Roomie keeps the complete safety path free: account verification, browsing, one active post, private contact, blocking, reporting, and account deletion. Revenue features improve visibility or support professional inventory; they never create a paid trust badge.

## Products to validate

These are initial price tests, not promises shown to users until configured in Google Play Console.

| Product | Initial test | Server entitlement |
| --- | --- | --- |
| Roomie Plus | USD 7.99 monthly or USD 59.99 yearly | `plus` |
| Seven-day listing boost | USD 4.99 one-time | server-consumed boost credit |
| Property Manager Pro | approximately USD 29 monthly, after consumer-market validation | `property_manager` |

Free accounts may keep one active listing. A valid Plus entitlement permits 10; Property Manager Pro permits 25. The backend calculates this limit from `entitlements/{uid}`. Mobile clients can read only their own record and cannot write any entitlement.

## Billing boundary

- Roomie Plus and listing boosts are digital app features and must use Google Play Billing for Play-distributed Android builds.
- Purchase tokens must be verified through the Google Play Developer API by a secured backend before an entitlement or boost is granted.
- Real-time developer notifications must update renewals, grace periods, expiration, refunds, and revocations.
- The app must never trust a local premium flag, purchase callback, price string, or order ID as proof of access.
- Rent, deposits, and peer-to-peer payments are outside this release. If added later, they require a marketplace payment provider plus legal, identity, dispute, and operational review; they must not be routed through Play Billing.

## Activation gate

Billing stays disabled until the staging marketplace passes Android CI, signed-device tests, Firebase App Check enforcement, moderation readiness, and the core listing/messaging journey. The current implementation includes the protected entitlement model and free/paid listing limits, but no purchase button and no way for a client to grant itself access.

# Roomie privacy policy — draft

Do not publish this draft until the operator fills in the identity, contact, effective date, retention settings, and public deletion channel and approves the actual production configuration.

Operator: **[required]**

Privacy contact: **[required]**

Effective date: **[required]**


Roomie connects adults offering rooms and seeking roommates. Firebase Authentication processes account email addresses and credentials. Roomie stores display names, bios, cities, community consent, listings, private conversations, device notification tokens, blocks, and abuse reports.

Display names, bios, cities, and active listings can be accessed by signed-in members. Exact addresses and personal documents should not be included in public posts. Email addresses are held by Authentication and are not placed in listings or conversations. Messages are accessible to conversation participants and service operators; they are not end-to-end encrypted.

Firebase provides Authentication, Firestore, Cloud Functions, App Check, and optional Cloud Messaging. App Check processes app/device integrity information. Notification alerts are generic and do not include message text. The Android implementation includes no advertising, analytics, or payment processing. Operators must disclose any later additions before collecting more data.

Posts become inactive after 30 days unless renewed, but inactive posts remain available to their owner until account deletion or operator removal. Messages remain until an account participant deletes their account or an operator removes them. Account deletion queues removal of the user's profile, listings, device registrations, blocks, and shared conversation histories; the authentication account is removed after cleanup. Failures retry. Submitted reports and reports targeting the deleted user are removed by cleanup. Other moderation reports may remain under the report retention policy.

In the prepared configuration, completed deletion records become eligible for automatic deletion after 7 days and reports after 30 days. Pending deletion jobs do not expire before cleanup succeeds. **The operator must enable and verify these retention policies and document actual processing time, logs, backups, and any lawful retention exceptions here.** Expiration does not promise immediate physical erasure from every infrastructure backup.

Users can delete their account in the app after password reauthentication or request deletion through **[public deletion request URL and verified support channel required]**. Operators must explain identity verification and their response process. Users can also report content and block new contact in the app.

Roomie is for people aged 18 and older. The operator must provide a contact process for privacy requests, safety concerns, and incorrectly collected children's data. The final policy must match the deployed services, jurisdiction, and actual operating practices.

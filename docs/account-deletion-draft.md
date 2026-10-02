# Delete a Roomie account — draft

Operator: **[required]**

Deletion request contact/form: **[required]**


In the app, open Profile, choose Delete account, and enter your password to reauthenticate. Unverified accounts can also use Delete account on the verification screen. Confirm only when you intend to remove your account.

Deletion queues cleanup of your profile, posts, device tokens, blocks, and shared conversation histories, including the other participant's copy in Roomie. The authentication account is removed after cleanup. Pending jobs retry failures and have no expiration before cleanup completes. Completed deletion records become eligible for automatic removal after 7 days when the configured retention policy is enabled.

If you cannot sign in or do not have the app installed, use **[operator-run public request channel required]**. Give the account email address; never send a password, verification code, or identity document through an ordinary email or public form. **The operator must describe its verification method, response timeframe, and actual retained logs/backups or legal exceptions before publishing this page.**

This file does not create a working request channel. Publish a reviewed page with a real contact or form and staff its requests before launch.

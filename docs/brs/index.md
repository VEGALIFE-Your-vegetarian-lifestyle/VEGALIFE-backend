# Business Rules

Business rules — constraints that exist independently of any one feature or PR — for the Vegalife backend.

Each rule is documented in `docs/brs/<feature>.md` with ID format `BR-<FEATURE>-<NNN>`.

| Rule ID | Title | Status | Last Reviewed |
|---------|-------|--------|---------------|
| BR-AUTH-001 | Unique User Identity | Active | 2026-09-22 |
| BR-AUTH-002 | Password Confirmation Match | Active | 2026-09-22 |
| BR-AUTH-003 | Email Verification Required for Activation | Active | 2026-09-24 |
| BR-AUTH-004 | Email Verification OTP Expiration (10 min) | Active | 2026-09-24 |
| BR-AUTH-005 | Password Minimum Length (8 chars) | Active | 2026-09-22 |
| BR-AUTH-006 | Username Format and Length (3-50 chars) | Active | 2026-09-22 |
| BR-AUTH-007 | Email Format and Length (valid, max 100 chars) | Active | 2026-09-22 |
| BR-PROFILE-001 | Profile Fields Validation | Active | 2026-09-23 |
| BR-PROFILE-002 | Profile Ownership | Active | 2026-09-23 |
| BR-PROFILE-003 | Profile Auto-Creation | Active | 2026-10-01 |
| BR-PROFILE-004 | Public Profile Read | Active | 2026-10-01 |
| BR-AUTH-008 | Login Requires Valid Credentials and Activated Account | Active | 2026-09-22 |
| BR-AUTH-009 | Access Token Short Lifetime (15 minutes) | Active | 2026-09-22 |
| BR-AUTH-010 | Refresh Token Long Lifetime (7 days) | Active | 2026-09-22 |
| BR-AUTH-011 | Refresh Token Opaque Random with SHA-256 Hash | Active | 2026-09-22 |
| BR-AUTH-012 | Non-Rotating Refresh Token Initially | Active | 2026-09-22 |
| BR-AUTH-013 | Logout Revokes Refresh Token | Active | 2026-09-22 |
| BR-AUTH-014 | Access Token Always Blacklisted on Logout | Active | 2026-09-22 |
| BR-AUTH-015 | Expired Token Cleanup Daily | Active | 2026-09-22 |
| BR-AUTH-016 | Account State Checked on Every Authenticated Request | Active | 2026-09-23 |
| BR-AUTH-017 | Password Reset OTP Lifecycle (6-digit, 10 min, single-use) | Active | 2026-09-24 |
| BR-AUTH-018 | Forgot Password Does Not Reveal Account Existence | Active | 2026-09-24 |
| BR-AUTH-019 | Password Reset OTP Stored as SHA-256 Hash | Active | 2026-09-24 |
| BR-AUTH-020 | Password Reset Revokes Refresh Tokens | Active | 2026-09-24 |
| BR-POST-001 | Post Ownership Comes from Authentication | Active | 2026-09-26 |
| BR-POST-002 | Users List Only Their Non-Deleted Posts | Active | 2026-09-26 |
| BR-POST-003 | User Post Lists Use Bounded Newest-First Pagination | Active | 2026-09-26 |
| BR-POST-004 | New Posts Start in the Created State | Active | 2026-09-26 |
| BR-POST-005 | Post Title and Content Are Required | Active | 2026-09-26 |
| BR-POST-006 | Users Edit Only Their Own Non-Deleted Posts | Active | 2026-09-27 |
| BR-POST-007 | Post Edits Change Only Supplied Fields | Active | 2026-09-27 |
| BR-POST-008 | Posts Are Soft-Deleted by Owner or Administrator | Active | 2026-09-29 |
| BR-POST-009 | Only Administrators Hide Posts, and It Is Logged | Active | 2026-09-29 |
| BR-POST-010 | Only Published Posts Are Public | Active | 2026-09-30 |
| BR-POST-011 | Single Post Detail Excludes Only Deleted Posts, Not Ownership | Active | 2026-10-10 |
| BR-COMMENT-001 | Comment Creation Requires Authentication; Reading Is Public | Active | 2026-10-10 |
| BR-MEDIA-001 | Media Content-Type Allowlist | Active | 2026-09-30 |
| BR-MEDIA-002 | Media Ownership Is Derived from Authentication | Active | 2026-09-30 |
| BR-MEDIA-003 | Per-Class Upload Size Ceilings | Active | 2026-10-02 |
| BR-MEDIA-004 | Object Keys Are Server-Assigned | Active | 2026-09-30 |
| BR-MEDIA-005 | Upload Grants Expire | Active | 2026-09-30 |
| BR-MEDIA-006 | Flyway Out-of-Order Enabled for Parallel Migration Branches | Active | 2026-09-30 |
| BR-MEDIA-007 | Confirmation Is Exactly Once | Active | 2026-09-30 |
| BR-MEDIA-008 | Confirmation Trusts Only Provider Read-Back | Active | 2026-09-30 |
| BR-MEDIA-009 | Media Deletion Is Owner-or-Admin, Idempotent, and Asynchronous | Active | 2026-10-03 |
| BR-MEDIA-010 | Deleted Media Are Invisible on Every Read Path | Active | 2026-10-03 |
| BR-FILTER-004 | Semantic Relevance Uses Three Bands and Configured Thresholds | Active | 2026-09-30 |
| BR-FILTER-005 | Only Publish Intent Triggers Filtering | Active | 2026-09-30 |
| BR-FILTER-006 | Filtering Is Asynchronous Through the Outbound Queue | Active | 2026-09-30 |
| BR-FILTER-007 | A Passed Filter Publishes the Post | Active | 2026-09-30 |
| BR-FILTER-008 | A Rejected or Uncertain Filter Flags the Post | Active | 2026-09-30 |
| BR-FILTER-009 | Posts Stuck Pending Are Flagged After 24 Hours | Active | 2026-09-30 |
| BR-RECP-001 | Recipe Ownership Comes from Authentication | Active | 2026-10-01 |
| BR-RECP-002 | Ingredient and Dish Names Are Unique Case-Insensitively | Active | 2026-10-01 |
| BR-RECP-003 | Recipe Name, Instructions, Servings, and Dish Are Required | Active | 2026-10-01 |
| BR-SUBS-001 | AI Quota Window Is the Current UTC Calendar Month | Active | 2026-10-04 |
| BR-SUBS-002 | Plan Limits and Prices Are Data-Driven | Active | 2026-10-04 |
| BR-SUBS-003 | Members Without a Subscription Row Are FREE by Default | Active | 2026-10-07 |
| BR-SUBS-004 | One In-Effect Subscription Row and Its Purchase Gate | Active | 2026-10-07 |
| BR-SUBS-005 | Cancellation Is Immediate, Cascading, and Repeat-Refused | Active | 2026-10-07 |
| BR-SUBS-006 | Subscriptions Expire Within 24 Hours of Renewal | Active | 2026-10-07 |
| BR-PAY-001 | The Payment Webhook Is the Sole Source of Truth for Fulfilment | Active | 2026-10-04 |
| BR-PAY-002 | A Payment Counts as Paid Only on a Double Zero | Active | 2026-10-04 |
| BR-PAY-003 | The Notified Amount Must Equal the Recorded Amount | Active | 2026-10-04 |
| BR-PAY-004 | A Payment Succeeds at Most Once | Active | 2026-10-04 |
| BR-PAY-005 | One In-Flight Checkout per Member and Plan | Active | 2026-10-04 |
| BR-PAY-006 | Risk-Flagged and Reversed Transactions Never Fulfil | Active | 2026-10-04 |
| BR-PAY-007 | The Receipt Is Emailed Exactly Once, Off the Request Thread | Active | 2026-10-04 |
| BR-PAY-008 | Renewal Date Is Paid Time Plus One Calendar Month | Active | 2026-10-04 |
| BR-PAY-009 | Only Active, Priced VND Plans Are Purchasable | Active | 2026-10-04 |
| BR-AI-001 | Quota Gate Precedes the Provider Call | Active | 2026-10-07 |
| BR-AI-002 | Only Successful Replies Count Toward Quota | Active | 2026-10-07 |
| BR-AI-003 | Conversation Ownership Comes from Authentication | Active | 2026-10-07 |
| BR-AI-004 | Context Is Profile Plus Own Conversation History | Active | 2026-10-07 |
| BR-AI-005 | Chat Provider Is OpenAI-Compatible and Set by Dedicated Env Vars | Active | 2026-10-07 |

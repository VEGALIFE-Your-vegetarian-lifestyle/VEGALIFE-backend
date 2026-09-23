# Business Rules

Business rules — constraints that exist independently of any one feature or PR — for the Vegalife backend.

Each rule is documented in `docs/brs/<feature>.md` with ID format `BR-<FEATURE>-<NNN>`.

| Rule ID | Title | Status | Last Reviewed |
|---------|-------|--------|---------------|
| BR-AUTH-001 | Unique User Identity | Active | 2026-09-22 |
| BR-AUTH-002 | Password Confirmation Match | Active | 2026-09-22 |
| BR-AUTH-003 | Email Verification Required for Activation | Active | 2026-09-22 |
| BR-AUTH-004 | Verification Token Expiration (24h) | Active | 2026-09-22 |
| BR-AUTH-005 | Password Minimum Length (8 chars) | Active | 2026-09-22 |
| BR-AUTH-006 | Username Format and Length (3-50 chars) | Active | 2026-09-22 |
| BR-AUTH-007 | Email Format and Length (valid, max 100 chars) | Active | 2026-09-22 |
| BR-PROFILE-001 | Profile Fields Validation | Active | 2026-09-23 |
| BR-PROFILE-002 | Profile Ownership | Active | 2026-09-23 |
| BR-PROFILE-003 | Profile Auto-Creation | Active | 2026-09-23 |
| BR-AUTH-008 | Login Requires Valid Credentials and Activated Account | Active | 2026-09-22 |
| BR-AUTH-009 | Access Token Short Lifetime (15 minutes) | Active | 2026-09-22 |
| BR-AUTH-010 | Refresh Token Long Lifetime (7 days) | Active | 2026-09-22 |
| BR-AUTH-011 | Refresh Token Opaque Random with SHA-256 Hash | Active | 2026-09-22 |
| BR-AUTH-012 | Non-Rotating Refresh Token Initially | Active | 2026-09-22 |
| BR-AUTH-013 | Logout Revokes Refresh Token | Active | 2026-09-22 |
| BR-AUTH-014 | Access Token Always Blacklisted on Logout | Active | 2026-09-22 |
| BR-AUTH-015 | Expired Token Cleanup Daily | Active | 2026-09-22 |
| BR-AUTH-016 | Account State Checked on Every Authenticated Request | Active | 2026-09-23 |
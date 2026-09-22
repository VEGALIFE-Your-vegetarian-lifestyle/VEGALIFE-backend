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
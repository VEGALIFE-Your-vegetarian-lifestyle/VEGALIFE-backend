# Architecture Decision Records

Accepted ADRs for the Vegalife backend. Filenames follow `NNNN-short-title.md` format (zero-padded, incrementing).

| File | Title | Status | Date |
|------|-------|--------|------|
| [001-user-registration-architecture.md](001-user-registration-architecture.md) | User Registration Architecture Decisions | Accepted | 2026-09-22 |
| [002-jwt-authentication-architecture.md](002-jwt-authentication-architecture.md) | JWT Authentication Architecture | Accepted | 2026-09-22 |
| [003-password-reset-otp-storage.md](003-password-reset-otp-storage.md) | Store Password-Reset OTPs as Hashed Rows in PostgreSQL | Accepted | 2026-09-24 |
| [004-generalized-otp-storage.md](004-generalized-otp-storage.md) | Generalize OTP Storage with a Purpose Discriminator | Accepted | 2026-09-24 |
| [005-persistent-outbound-message-queue.md](005-persistent-outbound-message-queue.md) | Use a Persistent Outbound Message Queue for Email Delivery | Accepted | 2026-09-26 |
| [006-optional-otp-verified-stage.md](006-optional-otp-verified-stage.md) | Optional OTP Verified Stage on `otp_code` | Accepted | 2026-09-27 |
| [007-post-content-filtering.md](007-post-content-filtering.md) | Filter Post Content Before Publication | Accepted | 2026-09-30 |
| [008-vnpay-integration.md](008-vnpay-integration.md) | Integrate VNPay Through a Hand-Rolled Thin Client, Fulfilling Only From Its IPN Webhook | Accepted | 2026-10-04 |
| [009-authorize-endpoints-with-method-security.md](009-authorize-endpoints-with-method-security.md) | Authorize Endpoints with Method-Security Annotations, Not a Central Path List | Accepted | 2026-10-09 |
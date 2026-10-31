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
| [010-menu-scheduled-overlap-gist-constraint.md](010-menu-scheduled-overlap-gist-constraint.md) | Enforce Non-Overlapping Scheduled Menus with a GiST Exclusion Constraint | Accepted | 2026-10-10 |
| [011-post-status-and-filter-axis-separation.md](011-post-status-and-filter-axis-separation.md) | Keep Post Visibility in `status` and the Filter Verdict in `flag` Only | Accepted | 2026-10-10 |
| [012-multi-module-mcp-tool-surface.md](012-multi-module-mcp-tool-surface.md) | Expose Menu Operations as an Internal MCP Server With Caller-Identity Propagation | Superseded by 013 | 2026-10-12 |
| [013-serve-ai-tools-in-process.md](013-serve-ai-tools-in-process.md) | Serve AI Chat Menu Tools In-Process, Retiring the Separate MCP Server | Accepted | 2026-10-12 |
| [014-embedding-provider-gemini.md](014-embedding-provider-gemini.md) | Serve Filter Embeddings From Gemini, Retaining the HuggingFace Client Unwired | Accepted | 2026-10-18 |
| [015-post-embedding-pgvector.md](015-post-embedding-pgvector.md) | Store Post Embeddings in pgvector and Search Them in SQL | Accepted | 2026-10-18 |
| [016-component-level-moderation.md](016-component-level-moderation.md) | Moderate Components Without Per-Occurrence Verdict Storage | Accepted | 2026-10-23 |
| [017-google-login.md](017-google-login.md) | Sign In With Google by Exchanging an Authorization Code and Auto-Linking on Verified Email | Accepted | 2026-10-26 |
| [018-clean-architecture-package-dependencies.md](018-clean-architecture-package-dependencies.md) | Enforce Clean-Architecture Package Dependencies (Inner Layers Never Depend on Outer Layers) | Accepted | 2026-10-30 |
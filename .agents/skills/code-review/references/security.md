# Reference: Security Review

## When this applies

The diff touches authentication, authorization, payment, PII, secrets
handling, or anything else `AGENTS.md`'s security constraints already
flag — beyond the baseline secret/destructive-command check, this is a
closer look at the security properties of the logic itself.

## Checklist

- **AuthN/AuthZ**: does every new endpoint/action check who the caller
  is and whether they're allowed to do this specific thing — not just
  that they're logged in?
- **Input validation**: is untrusted input (request bodies, query
  params, file uploads) validated/sanitized before use, not just typed?
- **Injection**: any raw string concatenation into a query, command, or
  template where a parameterized/escaped alternative exists?
- **Secrets & PII**: is anything sensitive logged, returned in an error
  message, or stored unencrypted that shouldn't be?
- **Least privilege**: does this change request broader permissions or
  access than it actually needs?
- **Session/token handling**: are tokens/sessions invalidated when they
  should be (logout, password change, permission downgrade)?

## Common mistakes to avoid

- Trusting client-side validation as the only check.
- Assuming an internal-only endpoint stays internal-only forever.
- Fixing the specific exploit shown without checking for the same
  pattern elsewhere in the diff.

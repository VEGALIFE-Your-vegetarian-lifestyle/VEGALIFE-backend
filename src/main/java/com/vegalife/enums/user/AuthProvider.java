package com.vegalife.enums.user;

/**
 * An external identity provider that may authenticate a {@link User} (ADR-017).
 *
 * <p>Values are persisted in {@code user_identity.provider}, whose CHECK constraint lists the same
 * names. Adding a provider means adding a value here <em>and</em> widening that constraint — no
 * column changes.
 */
public enum AuthProvider {
  GOOGLE
}

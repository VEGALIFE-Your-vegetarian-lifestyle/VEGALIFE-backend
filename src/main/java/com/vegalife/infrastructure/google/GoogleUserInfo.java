package com.vegalife.infrastructure.google;

/**
 * The verified facts a Google sign-in may act on (ADR-017).
 *
 * @param subject Google's stable account identifier ({@code sub}) — the linking key, never the
 *     email
 * @param email the address Google asserts, used only to find a candidate account on the first link
 * @param emailVerified whether Google has proven the user controls {@code email}
 * @param name the user's full name in displayable form; present only when the client requested the
 *     {@code profile} scope, and never guaranteed
 * @param givenName first name, offered by Google as a fallback for {@code name}
 * @param familyName last name, offered by Google as a fallback for {@code name}
 */
public record GoogleUserInfo(
    String subject,
    String email,
    boolean emailVerified,
    String name,
    String givenName,
    String familyName) {}

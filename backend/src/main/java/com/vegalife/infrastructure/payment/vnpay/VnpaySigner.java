package com.vegalife.infrastructure.payment.vnpay;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * VNPay checksum primitives (ADR-008): HMAC-SHA512 over a canonical query form shared by the pay
 * redirect, the IPN callback, and QueryDR (field sets differ; callers pass the right one).
 *
 * <p>Pay/IPN canonical form (v2.1.0): sorted keys, null/empty values skipped, hash fields excluded,
 * each pair URL-encoded UTF-8 ({@code space -> '+'}, exactly as the gateway's own samples encode
 * it) joined with {@code &}; the response signature is lowercase hex appended as {@code
 * &vnp_SecureHash=}. QueryDR signatures use the raw pipe-joined form instead — see {@link
 * #sign(String)}.
 *
 * <p>Construction never fails: a blank secret (FR-019 checkout is blocked earlier anyway) simply
 * makes every {@code verify*} return false, which is how the webhook answers {@code 97} instead of
 * raising (FR-020).
 */
public final class VnpaySigner {

  /** Constant-time comparisons target this key, never the pair ordering. */
  public static final String SECURE_HASH_FIELD = "vnp_SecureHash";

  /** The gateway's own v2.1.0 payload carries neither of these in the hashed set. */
  static final String SECURE_HASH_TYPE_FIELD = "vnp_SecureHashType";

  static final List<String> EXCLUDED_FIELDS = List.of(SECURE_HASH_FIELD, SECURE_HASH_TYPE_FIELD);

  private static final String HMAC_ALGORITHM = "HmacSHA512";

  private final byte[] secret;

  public VnpaySigner(String secureHashSecret) {
    this.secret = Objects.requireNonNullElse(secureHashSecret, "").getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Canonical {@code key=value&...} form: sorted keys, hash fields and empty/null values dropped,
   * values URL-encoded UTF-8 per the gateway's sample encoding.
   */
  public String canonicalize(Map<String, String> params) {
    TreeMap<String, String> sorted = new TreeMap<>(params);
    List<String> pairs = new ArrayList<>();
    for (Map.Entry<String, String> entry : sorted.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      if (EXCLUDED_FIELDS.contains(key) || value == null || value.isEmpty()) {
        continue;
      }
      pairs.add(encode(key) + "=" + encode(value));
    }
    return String.join("&", pairs);
  }

  /** Lowercase-hex HMAC-SHA512 of an already-canonical (or pipe-joined) string. */
  public String sign(String payload) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
      byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
        hex.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException(HMAC_ALGORITHM + " unavailable", e);
    }
  }

  /** {@link #sign(String)} of {@link #canonicalize(Map)} — what a pay URL or IPN expects. */
  public String signParams(Map<String, String> params) {
    return sign(canonicalize(params));
  }

  /**
   * Validates a pay/IPN callback: canonicalizes the params (dropping the signature itself),
   * re-hMACs, and compares against {@code providedHash} in constant time. Case-insensitive on the
   * hex; null/blank/garbage signatures simply fail.
   */
  public boolean verifyParams(Map<String, String> params, String providedHash) {
    if (params == null || isBlank(providedHash) || secret.length == 0) {
      return false;
    }
    return verifyRaw(canonicalize(params), providedHash);
  }

  /**
   * Validates a pipe-joined QueryDR signature: the payload is sent raw (no encoding), so it is
   * re-signed verbatim and compared constant-time.
   */
  public boolean verifyRaw(String payload, String providedHash) {
    if (payload == null || isBlank(providedHash) || secret.length == 0) {
      return false;
    }
    byte[] expected = sign(payload).getBytes(StandardCharsets.US_ASCII);
    byte[] actual =
        providedHash.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
    return MessageDigest.isEqual(expected, actual);
  }

  private static String encode(String value) {
    return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}

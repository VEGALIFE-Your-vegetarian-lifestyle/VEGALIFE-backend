package com.vegalife.service.media;

import java.util.Optional;

/**
 * Storage seam for the upload workflow (spec FR-007). The service owns lifecycle and policy; the
 * provider owns signature minting and read-back. Swap the implementation to move storage vendors
 * without touching callers.
 */
public interface PresignedUploadProvider {

  /**
   * Mints an upload grant for {@code publicId} covering {@code contentType}.
   *
   * @param publicId provider object key, already unique per media row
   * @param contentType media type as accepted by the allowlist
   * @return the credential, endpoint, and ceilings the client needs to upload directly
   */
  UploadGrant prepare(String publicId, String contentType);

  /**
   * Reads back the object at {@code publicId} after the client claims to have uploaded it.
   *
   * @param publicId provider object key recorded on the grant
   * @param contentType media type the grant was issued for
   * @return the provider-reported facts, or empty when no object exists yet
   */
  Optional<VerifiedUpload> verify(String publicId, String contentType);

  /**
   * Physically destroys the object at {@code publicId} (issue #39). Called only from the
   * MEDIA_PURGE outbox delivery, never inline in a request.
   *
   * @param publicId provider object key recorded on the media row
   * @param contentType media type the object was uploaded as, for provider resource typing
   * @throws RuntimeException when destruction fails and the purge attempt should be retried; an
   *     already-missing object is success, not failure
   */
  void destroy(String publicId, String contentType);
}

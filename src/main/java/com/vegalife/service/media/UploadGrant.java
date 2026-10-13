package com.vegalife.service.media;

import java.util.List;

/**
 * Everything a client needs to POST an object straight to storage, plus the ceilings the service
 * re-checks on confirm. The provider secret never leaves the server, so it has no field here.
 *
 * @param uploadUrl provider endpoint accepting the browser upload
 * @param publicId provider object key this grant is bound to
 * @param apiKey public credential echoed back to the client
 * @param timestamp signature timestamp, epoch seconds
 * @param signature provider signature over the grant parameters
 * @param maxFileSize ceiling in bytes for the granted media class
 * @param allowedFormats provider short-format allowlist for the granted media class
 */
public record UploadGrant(
    String uploadUrl,
    String publicId,
    String apiKey,
    long timestamp,
    String signature,
    long maxFileSize,
    List<String> allowedFormats) {}

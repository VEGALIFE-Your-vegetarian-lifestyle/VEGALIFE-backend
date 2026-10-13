package com.vegalife.service.media;

/**
 * Provider-reported facts about an object that already exists in storage (spec FR-008). Confirm
 * persists these; nothing here is derived by the application.
 *
 * @param mediaUrl public delivery URL of the stored object
 * @param mimeType MIME type reported by the provider for the actual object
 * @param fileSizeBytes stored object size in bytes
 * @param width pixel width, null when the provider does not report one
 * @param height pixel height, null when the provider does not report one
 * @param durationSeconds video duration, null for still images
 */
public record VerifiedUpload(
    String mediaUrl,
    String mimeType,
    long fileSizeBytes,
    Integer width,
    Integer height,
    Integer durationSeconds) {}

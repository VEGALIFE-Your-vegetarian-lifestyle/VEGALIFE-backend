package com.vegalife.service.media;

import com.cloudinary.Cloudinary;
import com.cloudinary.api.ApiResponse;
import com.cloudinary.api.exceptions.NotFound;
import com.vegalife.shared.config.MediaProperties;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Cloudinary-backed upload provider. Signs browser uploads against the signed upload endpoint and
 * read-backs through the Admin API; credentials come from {@code app.media.cloudinary.*} and no
 * allowlist entry or byte ceiling is hard-coded here (BR-MEDIA-001, BR-MEDIA-003).
 *
 * <p>The signature covers only {@code allowed_formats}, {@code public_id} and {@code timestamp}:
 * signing without {@code max_file_size} is accepted, signing with it returns 401 Invalid Signature,
 * so it is returned un-signed. Cloudinary does not enforce it either — size enforcement happens at
 * confirmation (BR-MEDIA-003).
 */
@Slf4j
@Component
public class CloudinaryUploadProvider implements PresignedUploadProvider {

  private static final String UPLOAD = "upload";
  private static final String RESOURCE_TYPE = "resource_type";
  private static final String IMAGE = "image";
  private static final String VIDEO = "video";
  private static final String JPEG = "jpeg";
  private static final String JPG = "jpg";

  private final MediaProperties properties;
  private final Cloudinary cloudinary;
  private final Map<String, String> mimeByFormat;

  public CloudinaryUploadProvider(MediaProperties properties) {
    this.properties = properties;

    MediaProperties.Cloudinary credentials = properties.getCloudinary();
    Map<String, Object> config = new HashMap<>();
    config.put("cloud_name", credentials.getCloudName());
    config.put("api_key", credentials.getApiKey());
    config.put("api_secret", credentials.getApiSecret());
    this.cloudinary = new Cloudinary(config);

    Map<String, String> formats = new HashMap<>();
    for (String contentType : properties.getUpload().allowedTypes()) {
      formats.put(shortFormat(contentType), contentType);
    }
    this.mimeByFormat = formats;
  }

  @Override
  public UploadGrant prepare(String publicId, String contentType) {
    long maxFileSize = properties.getUpload().maxBytesFor(contentType);
    long timestamp = System.currentTimeMillis() / 1000L;
    List<String> allowedFormats = shortFormats(contentType);

    Map<String, Object> signed = new HashMap<>();
    signed.put("public_id", publicId);
    signed.put("timestamp", timestamp);
    signed.put("allowed_formats", allowedFormats);
    cloudinary.signRequest(signed, Map.of());

    return new UploadGrant(
        cloudinary.cloudinaryApiUrl(UPLOAD, Map.of(RESOURCE_TYPE, resourceType(contentType))),
        publicId,
        (String) signed.get("api_key"),
        timestamp,
        (String) signed.get("signature"),
        maxFileSize,
        allowedFormats);
  }

  @Override
  public Optional<VerifiedUpload> verify(String publicId, String contentType) {
    ApiResponse resource;
    try {
      resource =
          cloudinary.api().resource(publicId, Map.of(RESOURCE_TYPE, resourceType(contentType)));
    } catch (NotFound missing) {
      return Optional.empty();
    } catch (Exception failure) {
      log.error("Provider read-back failed for public id {}", publicId, failure);
      throw new IllegalStateException("Provider read-back failed", failure);
    }

    Object reportedFormat = resource.get("format");
    String reportedMime =
        reportedFormat instanceof String format && mimeByFormat.containsKey(format)
            ? mimeByFormat.get(format)
            : contentType;
    long reportedBytes = resource.get("bytes") instanceof Number number ? number.longValue() : 0L;

    return Optional.of(
        new VerifiedUpload(
            deliveryUrl(resource),
            reportedMime,
            reportedBytes,
            integer(resource.get("width")),
            integer(resource.get("height")),
            integer(resource.get("duration"))));
  }

  private String resourceType(String contentType) {
    return properties.getUpload().isImage(contentType) ? IMAGE : VIDEO;
  }

  private List<String> shortFormats(String contentType) {
    List<String> allowed =
        properties.getUpload().isImage(contentType)
            ? properties.getUpload().getAllowedImageTypes()
            : properties.getUpload().getAllowedVideoTypes();
    return allowed.stream().map(CloudinaryUploadProvider::shortFormat).toList();
  }

  private static String shortFormat(String contentType) {
    String subtype = contentType.substring(contentType.indexOf('/') + 1);
    return JPEG.equals(subtype) ? JPG : subtype;
  }

  private static String deliveryUrl(ApiResponse resource) {
    if (resource.get("secure_url") instanceof String secure) {
      return secure;
    }
    if (resource.get("url") instanceof String plain) {
      return plain;
    }
    throw new IllegalStateException("Provider response has no delivery URL");
  }

  private static Integer integer(Object value) {
    return value instanceof Number number ? (int) Math.round(number.doubleValue()) : null;
  }
}

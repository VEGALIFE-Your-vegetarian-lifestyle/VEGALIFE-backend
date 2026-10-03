package com.vegalife.integration.config;

import com.vegalife.service.media.PresignedUploadProvider;
import com.vegalife.service.media.UploadGrant;
import com.vegalife.service.media.VerifiedUpload;
import com.vegalife.shared.config.MediaProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory stand-in for the storage backend in integration tests. {@link #prepare} mints
 * deterministic grants with no network, and {@link #verify} answers only from objects the test
 * seeds through {@link #putObject}, so every confirm path (missing object, size ceiling) is
 * reachable without Cloudinary.
 */
public class FakeUploadProvider implements PresignedUploadProvider {

  private final MediaProperties properties;
  private final Map<String, VerifiedUpload> objects = new ConcurrentHashMap<>();
  private final List<String> preparedPublicIds = new CopyOnWriteArrayList<>();
  private final List<String> destroyedPublicIds = new CopyOnWriteArrayList<>();

  public FakeUploadProvider(MediaProperties properties) {
    this.properties = properties;
  }

  @Override
  public UploadGrant prepare(String publicId, String contentType) {
    preparedPublicIds.add(publicId);
    return new UploadGrant(
        "https://upload.fake.test/v1/signed",
        publicId,
        "fake-api-key",
        Instant.now().getEpochSecond(),
        "fake-signature",
        properties.getUpload().maxBytesFor(contentType),
        shortFormats(contentType));
  }

  @Override
  public Optional<VerifiedUpload> verify(String publicId, String contentType) {
    return Optional.ofNullable(objects.get(publicId));
  }

  @Override
  public void destroy(String publicId, String contentType) {
    objects.remove(publicId);
    destroyedPublicIds.add(publicId);
  }

  /** Seeds the object a confirm call is expected to read back. */
  public void putObject(String publicId, VerifiedUpload upload) {
    objects.put(publicId, upload);
  }

  public List<String> preparedPublicIds() {
    return List.copyOf(preparedPublicIds);
  }

  /** Public ids physically destroyed so far — assert async purge reached the provider. */
  public List<String> destroyedPublicIds() {
    return List.copyOf(destroyedPublicIds);
  }

  /** Clears seeds and recorded grants between tests; the bean outlives rolled-back transactions. */
  public void reset() {
    objects.clear();
    preparedPublicIds.clear();
    destroyedPublicIds.clear();
  }

  private List<String> shortFormats(String contentType) {
    List<String> allowed =
        properties.getUpload().isImage(contentType)
            ? properties.getUpload().getAllowedImageTypes()
            : properties.getUpload().getAllowedVideoTypes();
    return allowed.stream().map(FakeUploadProvider::shortFormat).toList();
  }

  private static String shortFormat(String contentType) {
    String subtype = contentType.substring(contentType.indexOf('/') + 1);
    return "jpeg".equals(subtype) ? "jpg" : subtype;
  }
}

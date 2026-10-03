package com.vegalife.service.media;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.model.outbound.OutboundChannel;
import com.vegalife.model.outbound.OutboundMessage;
import com.vegalife.model.post.Media;
import com.vegalife.repository.post.MediaRepository;
import com.vegalife.service.outbound.OutboundChannelAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * MEDIA_PURGE channel adapter (ADR-005): physically destroys the provider object behind a
 * soft-deleted media row. The row is read with plain {@code findById} — soft-deleted rows are the
 * normal case here. A missing row or a row without {@code external_id} completes without a provider
 * call (the object was never recorded, or was already purged); anything the provider reports as an
 * unexpected failure propagates so the queue retries with backoff (BR-MEDIA-010).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MediaPurgeOutboundAdapter implements OutboundChannelAdapter {

  private final MediaRepository mediaRepository;
  private final PresignedUploadProvider uploadProvider;
  private final ObjectMapper objectMapper;

  @Override
  public OutboundChannel channel() {
    return OutboundChannel.MEDIA_PURGE;
  }

  @Override
  public void deliver(OutboundMessage message) {
    MediaPurgePayload payload = parse(message);
    Media media =
        mediaRepository
            .findById(payload.mediaId())
            .orElseGet(
                () -> {
                  log.info(
                      "Media {} already gone; purge message {} completes without provider call",
                      payload.mediaId(),
                      message.getId());
                  return null;
                });
    if (media == null) {
      return;
    }
    if (media.getExternalId() == null || media.getExternalId().isBlank()) {
      log.info(
          "Media {} has no external id; purge message {} completes without provider call",
          media.getId(),
          message.getId());
      return;
    }
    uploadProvider.destroy(media.getExternalId(), media.getMimeType());
    log.info("Purged provider object for media {} on message {}", media.getId(), message.getId());
  }

  private MediaPurgePayload parse(OutboundMessage message) {
    String json = message.getPayload();
    if (json == null || json.isBlank()) {
      throw new IllegalStateException(
          "Outbound message " + message.getId() + " has no payload to deliver");
    }
    try {
      MediaPurgePayload payload = objectMapper.readValue(json, MediaPurgePayload.class);
      if (payload.mediaId() == null) {
        throw new IllegalStateException(
            "Outbound message " + message.getId() + " payload is missing a mediaId");
      }
      return payload;
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(
          "Outbound message " + message.getId() + " has an unreadable payload", e);
    }
  }
}

package com.vegalife.unit.service.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.model.outbound.OutboundChannel;
import com.vegalife.model.outbound.OutboundMessage;
import com.vegalife.model.post.Media;
import com.vegalife.repository.post.MediaRepository;
import com.vegalife.service.media.MediaPurgeOutboundAdapter;
import com.vegalife.service.media.MediaPurgePayload;
import com.vegalife.service.media.PresignedUploadProvider;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * MEDIA_PURGE channel adapter contract (ADR-005 / BR-MEDIA-010): the provider object behind a
 * soft-deleted row is destroyed with the row's recorded external id and mime type; a missing row or
 * a row without an external id completes without a provider call; provider failures and payload
 * errors propagate as exceptions so the outbound queue retries with backoff.
 */
@ExtendWith(MockitoExtension.class)
class MediaPurgeOutboundAdapterTest {

  private static final String EXTERNAL_ID = "integration-test-cloud/user/media";
  private static final String MIME_TYPE = "image/jpeg";

  @Mock private MediaRepository mediaRepository;

  @Mock private PresignedUploadProvider uploadProvider;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private MediaPurgeOutboundAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new MediaPurgeOutboundAdapter(mediaRepository, uploadProvider, objectMapper);
  }

  @Test
  void channel_isMediaPurge() {
    assertThat(adapter.channel()).isEqualTo(OutboundChannel.MEDIA_PURGE);
  }

  @Test
  void deliver_destroysProviderObjectWithExternalIdAndMimeType() throws Exception {
    Media media = media(EXTERNAL_ID, MIME_TYPE);
    when(mediaRepository.findById(media.getId())).thenReturn(Optional.of(media));

    adapter.deliver(messageFor(new MediaPurgePayload(media.getId())));

    verify(uploadProvider).destroy(EXTERNAL_ID, MIME_TYPE);
  }

  @Test
  void deliver_rowMissing_completesWithoutProviderCall() throws Exception {
    UUID missingId = UUID.randomUUID();
    when(mediaRepository.findById(missingId)).thenReturn(Optional.empty());

    assertDoesNotThrow(() -> adapter.deliver(messageFor(new MediaPurgePayload(missingId))));

    verifyNoInteractions(uploadProvider);
  }

  @Test
  void deliver_nullExternalId_completesWithoutProviderCall() throws Exception {
    Media media = media(null, MIME_TYPE);
    when(mediaRepository.findById(media.getId())).thenReturn(Optional.of(media));

    assertDoesNotThrow(() -> adapter.deliver(messageFor(new MediaPurgePayload(media.getId()))));

    verify(uploadProvider, never()).destroy(anyString(), anyString());
  }

  @Test
  void deliver_blankExternalId_completesWithoutProviderCall() throws Exception {
    Media media = media("   ", MIME_TYPE);
    when(mediaRepository.findById(media.getId())).thenReturn(Optional.of(media));

    assertDoesNotThrow(() -> adapter.deliver(messageFor(new MediaPurgePayload(media.getId()))));

    verify(uploadProvider, never()).destroy(anyString(), anyString());
  }

  @Test
  void deliver_providerFailure_propagatesForQueueRetry() throws Exception {
    Media media = media(EXTERNAL_ID, MIME_TYPE);
    when(mediaRepository.findById(media.getId())).thenReturn(Optional.of(media));
    doThrow(new IllegalStateException("provider unavailable"))
        .when(uploadProvider)
        .destroy(EXTERNAL_ID, MIME_TYPE);

    assertThatThrownBy(() -> adapter.deliver(messageFor(new MediaPurgePayload(media.getId()))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("provider unavailable");
  }

  @Test
  void deliver_blankPayload_throwsIllegalState() {
    OutboundMessage message = OutboundMessage.builder().id(UUID.randomUUID()).payload("  ").build();

    assertThatThrownBy(() -> adapter.deliver(message))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("has no payload");
  }

  @Test
  void deliver_nullPayload_throwsIllegalState() {
    OutboundMessage message = OutboundMessage.builder().id(UUID.randomUUID()).build();

    assertThatThrownBy(() -> adapter.deliver(message))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("has no payload");
  }

  @Test
  void deliver_payloadWithoutMediaId_throwsIllegalState() {
    OutboundMessage message = OutboundMessage.builder().id(UUID.randomUUID()).payload("{}").build();

    assertThatThrownBy(() -> adapter.deliver(message))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("missing a mediaId");
  }

  @Test
  void deliver_unreadablePayload_throwsIllegalState() {
    OutboundMessage message =
        OutboundMessage.builder().id(UUID.randomUUID()).payload("not-json").build();

    assertThatThrownBy(() -> adapter.deliver(message))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("unreadable payload");
  }

  private OutboundMessage messageFor(MediaPurgePayload payload) throws Exception {
    return OutboundMessage.builder()
        .id(UUID.randomUUID())
        .payload(objectMapper.writeValueAsString(payload))
        .build();
  }

  private static Media media(String externalId, String mimeType) {
    return Media.builder().id(UUID.randomUUID()).externalId(externalId).mimeType(mimeType).build();
  }
}

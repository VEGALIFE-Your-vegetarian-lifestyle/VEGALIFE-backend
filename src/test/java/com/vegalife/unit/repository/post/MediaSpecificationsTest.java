package com.vegalife.unit.repository.post;

import static org.assertj.core.api.Assertions.assertThat;

import com.vegalife.model.post.Media;
import com.vegalife.model.user.User;
import com.vegalife.repository.post.MediaRepository;
import com.vegalife.repository.post.MediaSpecifications;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class MediaSpecificationsTest {

  private static final Instant CREATED_MARCH = Instant.parse("2026-03-15T12:00:00Z");
  private static final Instant CREATED_JUNE = Instant.parse("2026-06-15T12:00:00Z");
  private static final Instant CREATED_SEPTEMBER = Instant.parse("2026-09-15T12:00:00Z");

  @Autowired private MediaRepository mediaRepository;

  @Autowired private EntityManager entityManager;

  private User alice;
  private User bob;
  private Media aliceVideo;
  private Media aliceUploadingVideo;
  private Media bobVideo;
  private Media aliceImage;
  private Media aliceDeletedVideo;
  private Media orphanVideo;

  @BeforeEach
  void seed() {
    alice = createUser("alice", "alice@example.com");
    bob = createUser("bob", "bob@example.com");

    aliceVideo = createVideo(alice, "video/mp4", Media.Status.succeed, null);
    aliceUploadingVideo = createVideo(alice, "video/webm", Media.Status.uploading, null);
    bobVideo = createVideo(bob, "video/mp4", Media.Status.failed, null);
    aliceImage = createMedia(alice, "image/png", Media.Status.succeed, null, null);
    aliceDeletedVideo = createVideo(alice, "video/mp4", Media.Status.succeed, Instant.now());
    orphanVideo = createVideo(null, "video/mp4", Media.Status.succeed, null);

    entityManager.flush();
    setCreatedAt(aliceVideo, CREATED_MARCH);
    setCreatedAt(aliceUploadingVideo, CREATED_JUNE);
    setCreatedAt(bobVideo, CREATED_SEPTEMBER);
    setCreatedAt(aliceImage, CREATED_MARCH);
    setCreatedAt(aliceDeletedVideo, CREATED_MARCH);
    setCreatedAt(orphanVideo, CREATED_SEPTEMBER);
    entityManager.flush();
    entityManager.clear();
  }

  @Test
  void allVideosWithFilters_withoutFilters_returnsEveryNonDeletedVideoAcrossUploadersAndStatuses() {
    Page<Media> result = findAll(null, null, null, null);

    assertThat(result.getContent())
        .extracting(Media::getId)
        .containsExactlyInAnyOrder(
            aliceVideo.getId(), aliceUploadingVideo.getId(), bobVideo.getId(), orphanVideo.getId());
    assertThat(result.getTotalElements()).isEqualTo(4);
  }

  @Test
  void allVideosWithFilters_excludesNonVideoMedia() {
    Page<Media> result = findAll(null, null, null, null);

    assertThat(result.getContent())
        .extracting(Media::getMimeType)
        .allMatch(mimeType -> mimeType.startsWith("video/"));
    assertThat(result.getContent()).extracting(Media::getId).doesNotContain(aliceImage.getId());
  }

  @Test
  void allVideosWithFilters_excludesSoftDeletedVideos() {
    Page<Media> result = findAll(null, null, null, null);

    assertThat(result.getContent())
        .extracting(Media::getId)
        .doesNotContain(aliceDeletedVideo.getId());
  }

  @Test
  void allVideosWithFilters_byStatus_returnsOnlyThatStatus() {
    Page<Media> result = findAll(Media.Status.uploading, null, null, null);

    assertThat(result.getContent())
        .extracting(Media::getId)
        .containsExactly(aliceUploadingVideo.getId());
    assertThat(result.getTotalElements()).isOne();
  }

  @Test
  void allVideosWithFilters_byUserId_returnsOnlyThatUploadersVideos() {
    Page<Media> result = findAll(null, bob.getId(), null, null);

    assertThat(result.getContent()).extracting(Media::getId).containsExactly(bobVideo.getId());
    assertThat(result.getTotalElements()).isOne();
  }

  @Test
  void allVideosWithFilters_byUserId_neverMatchesNullUploader() {
    Page<Media> result = findAll(null, alice.getId(), null, null);

    assertThat(result.getContent())
        .extracting(Media::getId)
        .containsExactlyInAnyOrder(aliceVideo.getId(), aliceUploadingVideo.getId());
    assertThat(result.getContent()).extracting(Media::getId).doesNotContain(orphanVideo.getId());
  }

  @Test
  void allVideosWithFilters_byCreatedAtRange_isInclusiveOnBothBounds() {
    Page<Media> result = findAll(null, null, CREATED_MARCH, CREATED_JUNE);

    assertThat(result.getContent())
        .extracting(Media::getId)
        .containsExactlyInAnyOrder(aliceVideo.getId(), aliceUploadingVideo.getId());
    assertThat(result.getTotalElements()).isEqualTo(2);
  }

  @Test
  void allVideosWithFilters_byCreatedAtLowerBound_excludesVideosCreatedBeforeIt() {
    Page<Media> result = findAll(null, null, Instant.parse("2026-04-01T00:00:00Z"), null);

    assertThat(result.getContent())
        .extracting(Media::getId)
        .containsExactlyInAnyOrder(
            aliceUploadingVideo.getId(), bobVideo.getId(), orphanVideo.getId());
    assertThat(result.getTotalElements()).isEqualTo(3);
  }

  @Test
  void allVideosWithFilters_withCombinedFilters_appliesEveryPredicate() {
    Page<Media> result = findAll(Media.Status.succeed, alice.getId(), CREATED_MARCH, CREATED_JUNE);

    assertThat(result.getContent()).extracting(Media::getId).containsExactly(aliceVideo.getId());
    assertThat(result.getTotalElements()).isOne();
  }

  @Test
  void allVideosWithFilters_withFilterMatchingNothing_returnsEmptyPage() {
    Page<Media> result = findAll(Media.Status.uploading, bob.getId(), null, null);

    assertThat(result.getContent()).isEmpty();
    assertThat(result.getTotalElements()).isZero();
  }

  private Page<Media> findAll(
      Media.Status status, UUID userId, Instant createdFrom, Instant createdTo) {
    Specification<Media> spec =
        MediaSpecifications.allVideosWithFilters(status, userId, createdFrom, createdTo);
    Sort sort = Sort.by(Sort.Direction.ASC, "createdAt");
    return mediaRepository.findAll(spec, PageRequest.of(0, 20, sort));
  }

  private User createUser(String username, String email) {
    User user =
        User.builder()
            .username(username)
            .email(email)
            .passwordHash("password-hash")
            .role(User.Role.USER)
            .status(User.Status.activated)
            .emailVerified(true)
            .build();
    entityManager.persist(user);
    return user;
  }

  private Media createVideo(
      User uploader, String mimeType, Media.Status status, Instant deletedAt) {
    return createMedia(uploader, mimeType, status, deletedAt, "https://cdn.example.com/video.mp4");
  }

  private Media createMedia(
      User uploader, String mimeType, Media.Status status, Instant deletedAt, String mediaUrl) {
    Media media =
        Media.builder()
            .mediaUrl(mediaUrl)
            .status(status)
            .mimeType(mimeType)
            .durationSeconds(60)
            .fileSizeBytes(1024L)
            .uploadedBy(uploader)
            .deletedAt(deletedAt)
            .build();
    entityManager.persist(media);
    return media;
  }

  private void setCreatedAt(Media media, Instant createdAt) {
    entityManager
        .createNativeQuery("UPDATE media SET created_at = :createdAt WHERE id = :id")
        .setParameter("createdAt", Timestamp.from(createdAt))
        .setParameter("id", media.getId())
        .executeUpdate();
  }
}

package com.vegalife.repository.post;

import com.vegalife.model.post.Media;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaRepository
    extends JpaRepository<Media, UUID>, JpaSpecificationExecutor<Media> {

  List<Media> findByIdInAndDeletedAtIsNull(Collection<UUID> ids);

  Optional<Media> findByIdAndDeletedAtIsNull(UUID id);
}

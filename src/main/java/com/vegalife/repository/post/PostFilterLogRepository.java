package com.vegalife.repository.post;

import com.vegalife.model.post.PostFilterLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PostFilterLogRepository extends JpaRepository<PostFilterLog, UUID> {}

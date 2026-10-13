package com.vegalife.repository.admin;

import com.vegalife.model.admin.ModerationLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModerationLogRepository extends JpaRepository<ModerationLog, UUID> {}

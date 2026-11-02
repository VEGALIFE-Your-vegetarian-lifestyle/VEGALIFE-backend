package com.vegalife.repository.admin;

import com.vegalife.model.admin.PlatformSetting;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlatformSettingRepository extends JpaRepository<PlatformSetting, UUID> {

  Optional<PlatformSetting> findByKey(String key);

  List<PlatformSetting> findAllByOrderByCategoryAscKeyAsc();
}

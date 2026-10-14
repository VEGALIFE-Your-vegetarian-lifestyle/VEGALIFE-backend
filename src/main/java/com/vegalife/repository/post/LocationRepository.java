package com.vegalife.repository.post;

import com.vegalife.model.post.Location;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface LocationRepository
    extends JpaRepository<Location, UUID>, JpaSpecificationExecutor<Location> {}

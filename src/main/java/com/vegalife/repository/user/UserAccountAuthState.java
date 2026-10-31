package com.vegalife.repository.user;

import com.vegalife.enums.user.UserRole;
import com.vegalife.enums.user.UserStatus;
import java.time.Instant;
import java.util.UUID;

public interface UserAccountAuthState {

  UUID getId();

  UserStatus getStatus();

  UserRole getRole();

  Instant getDeletedAt();
}

package com.vegalife.repository.user;

import com.vegalife.model.user.User;
import java.time.Instant;
import java.util.UUID;

public interface UserAccountAuthState {

  UUID getId();

  User.Status getStatus();

  User.Role getRole();

  Instant getDeletedAt();
}

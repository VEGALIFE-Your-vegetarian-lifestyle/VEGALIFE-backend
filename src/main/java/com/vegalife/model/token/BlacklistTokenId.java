package com.vegalife.model.token;

import java.io.Serializable;
import java.util.Objects;

public class BlacklistTokenId implements Serializable {

  private String jti;
  private String issuer;

  public BlacklistTokenId() {}

  public BlacklistTokenId(String jti, String issuer) {
    this.jti = jti;
    this.issuer = issuer;
  }

  public String getJti() {
    return jti;
  }

  public void setJti(String jti) {
    this.jti = jti;
  }

  public String getIssuer() {
    return issuer;
  }

  public void setIssuer(String issuer) {
    this.issuer = issuer;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BlacklistTokenId that = (BlacklistTokenId) o;
    return Objects.equals(jti, that.jti) && Objects.equals(issuer, that.issuer);
  }

  @Override
  public int hashCode() {
    return Objects.hash(jti, issuer);
  }
}

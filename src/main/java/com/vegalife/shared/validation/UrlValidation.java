package com.vegalife.shared.validation;

import java.net.URI;
import java.net.URISyntaxException;

final class UrlValidation {

  private UrlValidation() {}

  static boolean isAbsoluteHttpUrl(String value) {
    try {
      URI uri = new URI(value.trim());
      String scheme = uri.getScheme();
      return uri.getHost() != null
          && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
    } catch (URISyntaxException e) {
      return false;
    }
  }
}

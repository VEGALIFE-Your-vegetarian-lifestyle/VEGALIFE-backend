package com.vegalife.unit.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

class FlywayMigrationVersionUniquenessTest {

  private static final String MIGRATION_PATH = "db/migration/";
  private static final String VERSION_PATTERN = "^V(\\d+)__.*\\.sql$";

  @Test
  void migrations_haveUniqueVersions() throws IOException {
    List<String> filenames = listMigrationFiles();
    assertThat(filenames).isNotEmpty();

    Set<String> seenVersions = new HashSet<>();
    List<String> duplicates = new ArrayList<>();

    for (String filename : filenames) {
      if (!filename.matches(VERSION_PATTERN)) {
        continue;
      }
      String version = filename.replaceFirst("^(V\\d+)__.*$", "$1");
      if (!seenVersions.add(version)) {
        duplicates.add(filename);
      }
    }

    assertThat(duplicates)
        .as(
            "Flyway requires unique version numbers per migration; found duplicates: %s",
            duplicates)
        .isEmpty();
  }

  private List<String> listMigrationFiles() throws IOException {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    Enumeration<URL> resources = classLoader.getResources(MIGRATION_PATH);
    List<String> filenames = new ArrayList<>();

    while (resources.hasMoreElements()) {
      URL url = resources.nextElement();
      String protocol = url.getProtocol();
      if ("file".equals(protocol)) {
        java.io.File dir = new java.io.File(url.getPath());
        String[] files = dir.list();
        if (files != null) {
          Collections.addAll(filenames, files);
        }
      } else if ("jar".equals(protocol)) {
        String path = url.getPath();
        String jarPath = path.substring(0, path.indexOf("!"));
        if (jarPath.startsWith("file:")) {
          jarPath = jarPath.substring(5);
        }
        try (JarFile jar = new JarFile(jarPath)) {
          Enumeration<JarEntry> entries = jar.entries();
          while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.startsWith(MIGRATION_PATH) && !entry.isDirectory()) {
              filenames.add(name.substring(MIGRATION_PATH.length()));
            }
          }
        }
      } else {
        try (InputStream in = url.openStream()) {
          // resources on unusual protocols — skip listing
        }
      }
    }
    return filenames;
  }
}

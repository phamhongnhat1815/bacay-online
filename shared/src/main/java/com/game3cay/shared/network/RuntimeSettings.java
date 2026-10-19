package com.game3cay.shared.network;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public final class RuntimeSettings {
  private static final ConcurrentHashMap<String, Properties> FILES = new ConcurrentHashMap<>();

  private RuntimeSettings() {}

  public static String text(String resource, String key, String fallback) {
    var p =
        FILES.computeIfAbsent(
            resource,
            name -> {
              var value = new Properties();
              try (var in = RuntimeSettings.class.getClassLoader().getResourceAsStream(name)) {
                if (in != null) value.load(in);
              } catch (IOException e) {
                throw new IllegalStateException("Không đọc được " + name, e);
              }
              return value;
            });
    return System.getProperty(key, p.getProperty(key, fallback));
  }

  public static int integer(String resource, String key, int fallback, int min, int max) {
    try {
      int value = Integer.parseInt(text(resource, key, Integer.toString(fallback)));
      if (value < min || value > max) throw new NumberFormatException();
      return value;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(key + " cần từ " + min + " đến " + max);
    }
  }
}

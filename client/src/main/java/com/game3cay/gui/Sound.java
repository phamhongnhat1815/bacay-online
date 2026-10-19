package com.game3cay.gui;

import java.util.concurrent.Executors;
import java.util.prefs.Preferences;
import javax.sound.sampled.*;

public final class Sound {
  private static final Preferences PREFS = Preferences.userNodeForPackage(Sound.class);
  private static volatile boolean enabled = PREFS.getBoolean("sound", true);
  private static final java.util.concurrent.ExecutorService PLAYER =
      Executors.newSingleThreadExecutor(
          r -> {
            var t = new Thread(r, "game-audio");
            t.setDaemon(true);
            return t;
          });

  private Sound() {}

  public static boolean enabled() {
    return enabled;
  }

  public static void toggle() {
    enabled = !enabled;
    PREFS.putBoolean("sound", enabled);
  }

  public static void play(String name) {
    if (!enabled) return;
    PLAYER.execute(
        () -> {
          try (var stream =
              AudioSystem.getAudioInputStream(
                  Sound.class.getResource("/assets/sounds/" + name + ".wav"))) {
            var clip = AudioSystem.getClip();
            clip.addLineListener(
                e -> {
                  if (e.getType() == LineEvent.Type.STOP) clip.close();
                });
            clip.open(stream);
            clip.start();
          } catch (Exception ignored) {
            /* Audio is optional on computers without an output device. */
          }
        });
  }
}

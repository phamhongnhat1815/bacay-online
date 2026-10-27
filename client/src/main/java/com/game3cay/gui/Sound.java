package com.game3cay.gui;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.prefs.Preferences;

public final class Sound {
    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(Sound.class);
    private static volatile boolean isAudioEnabled = PREFERENCES.getBoolean("sound", true);

    private static final ExecutorService AUDIO_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "game-audio");
        thread.setDaemon(true);
        return thread;
    });

    private Sound() {}

    public static boolean enabled() {
        return isAudioEnabled;
    }

    public static void toggle() {
        isAudioEnabled = !isAudioEnabled;
        PREFERENCES.putBoolean("sound", isAudioEnabled);
    }

    public static void playUrgentBeep() {
        if (!isAudioEnabled) {
            return;
        }
        playSynthesizedTone(920, 70, 0.45);
    }

    public static void playChip() {
        if (!isAudioEnabled) {
            return;
        }
        playSynthesizedTone(1500, 40, 0.35);
    }

    public static void playChipRattle() {
        if (!isAudioEnabled) {
            return;
        }
        AUDIO_EXECUTOR.execute(() -> {
            try {
                float sampleRate = 44100f;
                int durationMs = 85;
                int samples = (int) (sampleRate * durationMs / 1000);
                byte[] buffer = new byte[samples * 2];

                int hit1 = 0;
                int hit2 = (int) (sampleRate * 0.016f);
                int hit3 = (int) (sampleRate * 0.038f);

                for (int i = 0; i < samples; i++) {
                    double sampleVal = 0.0;

                    if (i >= hit1 && i < hit1 + 800) {
                        double t = (double) (i - hit1) / sampleRate;
                        double decay = Math.exp(-t * 220.0);
                        sampleVal += Math.sin(2.0 * Math.PI * 2200.0 * t) * 0.45 * decay;
                    }

                    if (i >= hit2 && i < hit2 + 1200) {
                        double t = (double) (i - hit2) / sampleRate;
                        double decay = Math.exp(-t * 160.0);
                        sampleVal += (Math.sin(2.0 * Math.PI * 1450.0 * t) * 0.40
                                    + Math.sin(2.0 * Math.PI * 2900.0 * t) * 0.15) * decay;
                    }

                    if (i >= hit3 && i < hit3 + 1400) {
                        double t = (double) (i - hit3) / sampleRate;
                        double decay = Math.exp(-t * 120.0);
                        sampleVal += Math.sin(2.0 * Math.PI * 1050.0 * t) * 0.30 * decay;
                    }

                    sampleVal = Math.max(-1.0, Math.min(1.0, sampleVal));
                    short val = (short) (sampleVal * 32767);
                    buffer[2 * i] = (byte) (val & 0xff);
                    buffer[2 * i + 1] = (byte) ((val >> 8) & 0xff);
                }

                var format = new javax.sound.sampled.AudioFormat(sampleRate, 16, 1, true, false);
                try (var bais = new java.io.ByteArrayInputStream(buffer);
                     var stream = new javax.sound.sampled.AudioInputStream(bais, format, samples)) {
                    Clip clip = AudioSystem.getClip();
                    clip.open(stream);
                    clip.addLineListener(e -> {
                        if (e.getType() == LineEvent.Type.STOP) clip.close();
                    });
                    clip.start();
                }
            } catch (Exception ignored) {
            }
        });
    }

    public static void playCardFlip() {
        if (!isAudioEnabled) {
            return;
        }
        playSynthesizedTone(650, 45, 0.28);
    }

    public static void playEmotePop() {
        if (!isAudioEnabled) {
            return;
        }
        playSynthesizedTone(780, 55, 0.35);
    }

    public static void playPotWin() {
        if (!isAudioEnabled) {
            return;
        }
        AUDIO_EXECUTOR.execute(() -> {
            try {
                int[] notes = {523, 659, 784, 1046};
                for (int note : notes) {
                    playToneDirect(note, 65, 0.32);
                    Thread.sleep(50);
                }
            } catch (Exception ignored) {
            }
        });
    }

    private static void playToneDirect(int freqHz, int durationMs, double volume) {
        try {
            float sampleRate = 44100f;
            int samples = (int) (sampleRate * durationMs / 1000);
            byte[] buffer = new byte[samples * 2];
            for (int i = 0; i < samples; i++) {
                double angle = 2.0 * Math.PI * i / (sampleRate / freqHz);
                double decay = Math.max(0, 1.0 - (double) i / samples);
                short val = (short) (Math.sin(angle) * 32767 * volume * decay);
                buffer[2 * i] = (byte) (val & 0xff);
                buffer[2 * i + 1] = (byte) ((val >> 8) & 0xff);
            }
            var format = new javax.sound.sampled.AudioFormat(sampleRate, 16, 1, true, false);
            try (var bais = new java.io.ByteArrayInputStream(buffer);
                 var stream = new javax.sound.sampled.AudioInputStream(bais, format, samples)) {
                Clip clip = AudioSystem.getClip();
                clip.open(stream);
                clip.addLineListener(e -> {
                    if (e.getType() == LineEvent.Type.STOP) clip.close();
                });
                clip.start();
            }
        } catch (Exception ignored) {
        }
    }

    private static void playSynthesizedTone(int freqHz, int durationMs, double volume) {
        AUDIO_EXECUTOR.execute(() -> {
            try {
                float sampleRate = 44100f;
                int samples = (int) (sampleRate * durationMs / 1000);
                byte[] buffer = new byte[samples * 2];
                for (int i = 0; i < samples; i++) {
                    double angle = 2.0 * Math.PI * i / (sampleRate / freqHz);
                    double decay = Math.max(0, 1.0 - (double) i / samples);
                    short val = (short) (Math.sin(angle) * 32767 * volume * decay);
                    buffer[2 * i] = (byte) (val & 0xff);
                    buffer[2 * i + 1] = (byte) ((val >> 8) & 0xff);
                }
                var format = new javax.sound.sampled.AudioFormat(sampleRate, 16, 1, true, false);
                try (var bais = new java.io.ByteArrayInputStream(buffer);
                     var stream = new javax.sound.sampled.AudioInputStream(bais, format, samples)) {
                    Clip clip = AudioSystem.getClip();
                    clip.open(stream);
                    clip.addLineListener(e -> {
                        if (e.getType() == LineEvent.Type.STOP) clip.close();
                    });
                    clip.start();
                }
            } catch (Exception ignored) {
            }
        });
    }

    public static void play(String soundName) {
        if (!isAudioEnabled) {
            return;
        }

        AUDIO_EXECUTOR.execute(() -> {
            URL soundUrl = Sound.class.getResource("/assets/sounds/" + soundName + ".wav");
            if (soundUrl == null) {
                return;
            }

            try (AudioInputStream audioStream = AudioSystem.getAudioInputStream(soundUrl)) {
                Clip clip = AudioSystem.getClip();
                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) {
                        clip.close();
                    }
                });
                clip.open(audioStream);
                clip.start();
            } catch (Exception ignored) {
            }
        });
    }
}

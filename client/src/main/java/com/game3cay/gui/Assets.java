package com.game3cay.gui;

import com.game3cay.shared.model.Card;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;

/** Loads the supplied artwork at display resolution instead of retaining 52 full-size scans. */
public final class Assets {
  private static final Map<String, BufferedImage> CACHE = new ConcurrentHashMap<>();

  private Assets() {}

  public static BufferedImage get(String path, int width) {
    return CACHE.computeIfAbsent(path + ":" + width, key -> read(path, width));
  }

  private static BufferedImage read(String path, int width) {
    try (var resource = Assets.class.getResourceAsStream("/assets/" + path)) {
      if (resource == null) throw new IOException("Missing asset: " + path);
      try (var input = ImageIO.createImageInputStream(resource)) {
        var readers = ImageIO.getImageReaders(input);
        if (!readers.hasNext()) throw new IOException("Invalid image: " + path);
        var reader = readers.next();
        try {
          reader.setInput(input);
          ImageReadParam param = reader.getDefaultReadParam();
          int sample = Math.max(1, reader.getWidth(0) / Math.max(1, width));
          param.setSourceSubsampling(sample, sample, 0, 0);
          return reader.read(0, param);
        } finally {
          reader.dispose();
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public static BufferedImage card(Card card) {
    String suit =
        switch (card.suit()) {
          case CHUON -> "clubs";
          case BICH -> "spades";
          case CO -> "hearts";
          case RO -> "diamonds";
        };
    String rank =
        switch (card.rank()) {
          case ACE -> "ace";
          case KING -> "king";
          case QUEEN -> "queen";
          case JACK -> "jack";
          default -> card.rank().displayName();
        };
    return get("cards/" + suit + "/" + rank + " of " + suit + ".png", 240);
  }

  public static void cover(Graphics2D g, BufferedImage image, int w, int h) {
    double s = Math.max(w / (double) image.getWidth(), h / (double) image.getHeight());
    int iw = (int) Math.ceil(image.getWidth() * s), ih = (int) Math.ceil(image.getHeight() * s);
    g.drawImage(image, (w - iw) / 2, (h - ih) / 2, iw, ih, null);
  }

  public static void quality(Graphics2D g) {
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
  }
}

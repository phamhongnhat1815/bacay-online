package com.game3cay.gui;

import com.game3cay.shared.model.Card;
import com.game3cay.shared.model.Rank;
import com.game3cay.shared.model.Suit;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Assets {
    private static final Map<String, BufferedImage> IMAGE_CACHE = new ConcurrentHashMap<>();

    private Assets() {}

    /** Called on the startup worker, before the first Swing paint. */
    public static void preload() {
        get("backgrounds/auth.png", 1700);
        get("backgrounds/casino.png", 1700);
        for (String variant : new String[] {"blue", "gold", "green", "orange", "red", "disabled"}) {
            get("buttons/" + variant + ".png", 420);
        }
        get("dealer.png", 220);
        get("table.png", 1100);
        get("table.png", 240);
        get("chips.png", 60);
        get("chips.png", 110);
        get("icons/crown.png", 30);
        get("cards/back.png", 200);
        for (int avatar = 1; avatar <= 6; avatar++) get("avatars/avatar_player_" + avatar + ".png", 100);
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) card(new Card(rank, suit));
        }
    }

    public static BufferedImage get(String relativePath, int targetWidth) {
        return IMAGE_CACHE.computeIfAbsent(relativePath + ":" + targetWidth, key -> loadImage(relativePath, targetWidth));
    }

    private static BufferedImage loadImage(String relativePath, int targetWidth) {
        String fullPath = "/assets/" + relativePath;
        try (InputStream resourceStream = Assets.class.getResourceAsStream(fullPath)) {
            if (resourceStream == null) {
                throw new IOException("Không tìm thấy tệp tài nguyên: " + fullPath);
            }

            try (ImageInputStream input = ImageIO.createImageInputStream(resourceStream)) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) {
                    throw new IOException("Định dạng hình ảnh không hợp lệ: " + fullPath);
                }

                ImageReader reader = readers.next();
                try {
                    reader.setInput(input);
                    ImageReadParam readParam = reader.getDefaultReadParam();
                    int originalWidth = reader.getWidth(0);
                    int sampleFactor = Math.max(1, originalWidth / Math.max(1, targetWidth));
                    readParam.setSourceSubsampling(sampleFactor, sampleFactor, 0, 0);
                    return reader.read(0, readParam);
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Lỗi khi tải hình ảnh tài nguyên: " + fullPath, e);
        }
    }

    public static BufferedImage card(Card card) {
        String suitName = switch (card.suit()) {
            case CHUON -> "clubs";
            case BICH -> "spades";
            case CO -> "hearts";
            case RO -> "diamonds";
        };

        String rankName = switch (card.rank()) {
            case ACE -> "ace";
            case KING -> "king";
            case QUEEN -> "queen";
            case JACK -> "jack";
            default -> card.rank().displayName();
        };

        return get("cards/" + suitName + "/" + rankName + " of " + suitName + ".png", 240);
    }

    public static void cover(Graphics2D g, BufferedImage image, int width, int height) {
        double scale = Math.max((double) width / image.getWidth(), (double) height / image.getHeight());
        int scaledWidth = (int) Math.ceil(image.getWidth() * scale);
        int scaledHeight = (int) Math.ceil(image.getHeight() * scale);
        int offsetX = (width - scaledWidth) / 2;
        int offsetY = (height - scaledHeight) / 2;
        g.drawImage(image, offsetX, offsetY, scaledWidth, scaledHeight, null);
    }

    public static void quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }
}

package com.game3cay.gui;

import com.game3cay.shared.model.Card;
import com.game3cay.shared.model.Rank;
import com.game3cay.shared.model.Suit;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Assets {
    // Retain filtered masters, rather than all 52 full-resolution card scans (~1.5 GB).
    private static final Map<String, BufferedImage> IMAGE_CACHE = new ConcurrentHashMap<>();
    private static final Map<SizeKey, BufferedImage> SCALED_CACHE = new LinkedHashMap<>(64, 0.75f, true);
    private static final long MAX_SCALED_PIXELS = 8_000_000L;
    private static long scaledPixels;
    private record SizeKey(String path, int width, int height) {}

    private Assets() {}

    /** Called on the startup worker, before the first Swing paint. */
    public static void preload() {
        get("dealer.png", 220);
        get("table.png", 1100);
        get("cards/back.png", 200);
        for (int avatar = 1; avatar <= 6; avatar++) get("avatars/avatar_player_" + avatar + ".png", 100);
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) card(new Card(rank, suit));
        }
    }

    public static BufferedImage get(String relativePath, int targetWidth) {
        if (targetWidth < 1) throw new IllegalArgumentException("Image width must be positive");
        BufferedImage master = master(relativePath);
        int width = Math.min(targetWidth, master.getWidth());
        int height = Math.max(1, (int) Math.round((double) master.getHeight() * width / master.getWidth()));
        return scaled(relativePath, master, width, height);
    }

    private static BufferedImage master(String relativePath) {
        return IMAGE_CACHE.computeIfAbsent(relativePath, Assets::loadImage);
    }

    private static BufferedImage loadImage(String relativePath) {
        String fullPath = "/assets/" + relativePath;
        try (InputStream resourceStream = Assets.class.getResourceAsStream(fullPath)) {
            if (resourceStream == null) {
                throw new IOException("Không tìm thấy tệp tài nguyên: " + fullPath);
            }

            BufferedImage source = ImageIO.read(resourceStream);
            if (source == null) throw new IOException("Định dạng hình ảnh không hợp lệ: " + fullPath);
            int limit = relativePath.startsWith("cards/") ? 512
                    : relativePath.startsWith("avatars/") ? 256
                    : relativePath.equals("dealer.png") ? 512
                    : relativePath.equals("chips.png") ? 256
                    : relativePath.startsWith("icons/") ? 128 : 2048;
            int width = Math.min(source.getWidth(), limit);
            int height = Math.max(1, (int) Math.round((double) source.getHeight() * width / source.getWidth()));
            return resize(source, width, height);
        } catch (IOException e) {
            throw new IllegalStateException("Lỗi khi tải hình ảnh tài nguyên: " + fullPath, e);
        }
    }

    /** Paint using the final device-pixel size, including window scaling, rotations and HiDPI. */
    public static void draw(Graphics2D g, String path, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return;
        BufferedImage master = master(path);
        var transform = g.getTransform();
        double scaleX = Math.hypot(transform.getScaleX(), transform.getShearY());
        double scaleY = Math.hypot(transform.getShearX(), transform.getScaleY());
        int pixelsWide = Math.max(1, (int) Math.ceil(width * scaleX));
        int pixelsHigh = Math.max(1, (int) Math.ceil(height * scaleY));
        // Never manufacture pixels above the available source resolution.
        BufferedImage image = pixelsWide > master.getWidth() || pixelsHigh > master.getHeight()
                ? master : scaled(path, master, pixelsWide, pixelsHigh);
        g.drawImage(image, x, y, width, height, null);
    }

    public static void drawCard(Graphics2D g, Card card, int x, int y, int width, int height) {
        PlayingCardArt.paint(g, cardPath(card), x, y, width, height);
    }

    public static void drawCardBack(Graphics2D g, int x, int y, int width, int height) {
        PlayingCardArt.paint(g, "cards/back.png", x, y, width, height);
    }

    private static BufferedImage scaled(String path, BufferedImage master, int width, int height) {
        if (width == master.getWidth() && height == master.getHeight()) return master;
        SizeKey key = new SizeKey(path, width, height);
        synchronized (SCALED_CACHE) {
            BufferedImage cached = SCALED_CACHE.get(key);
            if (cached != null) return cached;
            BufferedImage result = resize(master, width, height);
            SCALED_CACHE.put(key, result);
            scaledPixels += (long) width * height;
            var entries = SCALED_CACHE.entrySet().iterator();
            while ((scaledPixels > MAX_SCALED_PIXELS || SCALED_CACHE.size() > 192) && entries.hasNext()) {
                var entry = entries.next();
                scaledPixels -= (long) entry.getValue().getWidth() * entry.getValue().getHeight();
                entries.remove();
            }
            return result;
        }
    }

    /** Filter each reduction by at most 2x so fine lines cannot disappear between sampled pixels. */
    static BufferedImage resize(BufferedImage source, int width, int height) {
        if (width < 1 || height < 1) throw new IllegalArgumentException("Image dimensions must be positive");
        BufferedImage current = source;
        do {
            int nextWidth = current.getWidth() > width ? Math.max(width, current.getWidth() / 2) : width;
            int nextHeight = current.getHeight() > height ? Math.max(height, current.getHeight() / 2) : height;
            BufferedImage next = new BufferedImage(nextWidth, nextHeight, BufferedImage.TYPE_INT_ARGB_PRE);
            Graphics2D g = next.createGraphics();
            try {
                quality(g);
                g.setComposite(AlphaComposite.Src);
                // Bilinear halves integrate adjacent pixels; bicubic handles the final fractional step.
                if (nextWidth * 2 == current.getWidth() && nextHeight * 2 == current.getHeight())
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(current, 0, 0, nextWidth, nextHeight, null);
            } finally { g.dispose(); }
            current = next;
        } while (current.getWidth() != width || current.getHeight() != height);
        return current;
    }

    public static BufferedImage card(Card card) {
        return get(cardPath(card), 512);
    }

    private static String cardPath(Card card) {
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

        return "cards/" + suitName + "/" + rankName + " of " + suitName + ".png";
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
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
    }
}

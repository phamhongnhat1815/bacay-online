package com.game3cay.gui;

import com.game3cay.shared.model.Card;

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

/**
 * Tiện ích tải và quản lý bộ nhớ đệm (cache) hình ảnh cho giao diện người dùng.
 * <p>
 * Áp dụng kỹ thuật subsampling đọc ảnh trực tiếp ở kích thước hiển thị mục tiêu,
 * giúp tiết kiệm bộ nhớ RAM đáng kể thay vì giữ toàn bộ 52 lá bài kích thước đầy đủ trong bộ nhớ.
 */
public final class Assets {

    private static final Map<String, BufferedImage> IMAGE_CACHE = new ConcurrentHashMap<>();

    private Assets() {}

    /**
     * Lấy hình ảnh từ tài nguyên ứng dụng theo kích thước hiển thị mong muốn.
     *
     * @param relativePath đường dẫn tệp trong thư mục /assets/
     * @param targetWidth  chiều rộng hiển thị mong muốn
     * @return BufferedImage đã được tối ưu độ phân giải
     */
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

    /**
     * Lấy hình ảnh đại diện cho một lá bài cụ thể.
     *
     * @param card lá bài cần lấy ảnh
     * @return BufferedImage của lá bài
     */
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

    /**
     * Vẽ hình ảnh lấp đầy vùng hiển thị (cover mode) bảo toàn tỷ lệ khung hình.
     *
     * @param g     đối tượng Graphics2D
     * @param image hình ảnh cần vẽ
     * @param width chiều rộng vùng đích
     * @param height chiều cao vùng đích
     */
    public static void cover(Graphics2D g, BufferedImage image, int width, int height) {
        double scale = Math.max((double) width / image.getWidth(), (double) height / image.getHeight());
        int scaledWidth = (int) Math.ceil(image.getWidth() * scale);
        int scaledHeight = (int) Math.ceil(image.getHeight() * scale);
        int offsetX = (width - scaledWidth) / 2;
        int offsetY = (height - scaledHeight) / 2;
        g.drawImage(image, offsetX, offsetY, scaledWidth, scaledHeight, null);
    }

    /**
     * Thiết lập các cờ khử răng cưa chất lượng cao (Anti-aliasing) cho Graphics2D.
     *
     * @param g đối tượng Graphics2D
     */
    public static void quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }
}

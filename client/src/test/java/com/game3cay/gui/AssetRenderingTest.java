package com.game3cay.gui;

import java.awt.*;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AssetRenderingTest {
    @Test void finePatternsAreAveragedInsteadOfDroppingRowsAndColumns() {
        BufferedImage pattern = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++)
            pattern.setRGB(x, y, ((x + y) & 1) == 0 ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
        BufferedImage small = Assets.resize(pattern, 4, 4);
        for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) {
            int shade = small.getRGB(x, y) & 255;
            assertTrue(shade >= 120 && shade <= 136, "Subpixel detail should become smooth gray, not alias to black/white");
        }
    }

    @Test void transparentEdgesDoNotAcquireColorsFromInvisiblePixels() {
        BufferedImage source = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++)
            source.setRGB(x, y, x >= 7 && x <= 24 && y >= 7 && y <= 24 ? 0xffff0000 : 0x0000ff00);
        BufferedImage small = Assets.resize(source, 5, 5);
        boolean foundSoftEdge = false;
        for (int y = 0; y < 5; y++) for (int x = 0; x < 5; x++) {
            Color pixel = new Color(small.getRGB(x, y), true);
            if (pixel.getAlpha() > 0 && pixel.getAlpha() < 255) {
                foundSoftEdge = true;
                assertTrue(pixel.getRed() > 245);
                assertEquals(0, pixel.getGreen(), "Transparent green must not create a fringe on a red edge");
            }
        }
        assertTrue(foundSoftEdge, "Transparency should be antialiased at the edge");
    }

    @Test void hidpiPaintingUsesDeviceResolutionAndPreservesTheCallerTransform() {
        BufferedImage target = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = target.createGraphics();
        try {
            Assets.quality(g);
            g.scale(2, 2);
            var transform = g.getTransform();
            Assets.draw(g, "icons/crown.png", 0, 0, 32, 32);
            assertEquals(transform, g.getTransform());
        } finally { g.dispose(); }
        BufferedImage reference = Assets.get("icons/crown.png", 64);
        assertArrayEquals(reference.getRGB(0, 0, 64, 64, null, 0, 64),
                target.getRGB(0, 0, 64, 64, null, 0, 64), "A 200% display must use a 64px image for a 32px icon");
    }
}

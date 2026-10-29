package com.game3cay.gui;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/** One frame for every face and back, including small cards on the table. */
final class PlayingCardArt {
    private PlayingCardArt() {}

    static void paint(Graphics2D graphics, String path, int x, int y, int width, int height) {
        if (width < 1 || height < 1) return;
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            Assets.quality(g);
            double radius = Math.max(4, width * .08);
            var shape = new RoundRectangle2D.Double(x, y, width, height, radius, radius);
            g.setColor(new Color(0, 0, 0, 95));
            g.fill(new RoundRectangle2D.Double(x + 2, y + 4, width, height, radius, radius));
            Graphics2D face = (Graphics2D) g.create();
            try {
                face.clip(shape);
                face.setColor(Color.WHITE); face.fill(shape);
                Assets.draw(face, path, x, y, width, height);
            } finally { face.dispose(); }
            double scale = Math.max(.1, Math.hypot(g.getTransform().getScaleX(), g.getTransform().getShearY()));
            float line = (float) Math.max(1.15, 1.2 / scale);
            g.setStroke(new BasicStroke(line));
            g.setColor(new Color(69, 83, 103));
            // Inset the border so it cannot disappear at the edge of a clip.
            g.draw(new RoundRectangle2D.Double(x + line / 2, y + line / 2,
                    width - line, height - line, radius, radius));
        } finally { g.dispose(); }
    }
}

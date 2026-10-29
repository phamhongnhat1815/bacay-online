package com.game3cay.gui;

import java.awt.*;
import java.awt.geom.Path2D;

/** Small table icons stay sharp at every device scale without a raster background. */
final class GameIcons {
    private GameIcons() {}

    static void chips(Graphics2D graphics, int x, int y, int width, int height) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            Assets.quality(g);
            g.translate(x, y); g.scale(width / 96.0, height / 72.0);
            g.setColor(new Color(0, 0, 0, 55)); g.fillOval(2, 55, 91, 12);
            stack(g, 5, 42, 3, new Color(60, 118, 184));
            stack(g, 34, 46, 5, new Color(192, 73, 85));
            stack(g, 62, 42, 7, new Color(49, 153, 126));
        } finally { g.dispose(); }
    }

    private static void stack(Graphics2D g, int x, int bottom, int count, Color color) {
        for (int i = 0; i < count; i++) {
            int y = bottom - i * 4;
            // A complete lower ellipse gives the cylinder a rounded base; leave room below it.
            g.setColor(color.darker());
            g.fillOval(x, y + 5, 28, 12);
            g.fillRect(x, y + 6, 28, 5);
            g.setColor(color); g.fillOval(x, y, 28, 12);
            g.setColor(new Color(233, 239, 247, 210));
            g.setStroke(new BasicStroke(1.5f)); g.drawOval(x + 1, y + 1, 26, 10);
            g.setColor(color.darker()); g.drawOval(x + 6, y + 3, 16, 6);
            g.setColor(new Color(233, 239, 247, 170));
            g.drawArc(x + 1, y + 5, 26, 11, 180, 180);
        }
    }

    static void crown(Graphics2D graphics, int x, int y, int width, int height) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            Assets.quality(g);
            g.translate(x, y); g.scale(width / 28.0, height / 28.0);
            Path2D shape = new Path2D.Double();
            shape.moveTo(3, 8); shape.lineTo(9, 14); shape.lineTo(14, 4);
            shape.lineTo(19, 14); shape.lineTo(25, 8); shape.lineTo(22, 21);
            shape.lineTo(6, 21); shape.closePath();
            g.setPaint(new GradientPaint(0, 4, new Color(255, 225, 151), 0, 23, Theme.GOLD));
            g.fill(shape);
            g.setColor(new Color(124, 85, 31)); g.setStroke(new BasicStroke(1)); g.draw(shape);
            g.setColor(Theme.GOLD); g.fillRoundRect(6, 23, 16, 3, 2, 2);
            for (int[] point : new int[][]{{3, 7}, {14, 3}, {25, 7}}) g.fillOval(point[0] - 2, point[1] - 2, 4, 4);
        } finally { g.dispose(); }
    }
}

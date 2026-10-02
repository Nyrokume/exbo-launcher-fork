// Generates free placeholder images for the mock data (no EXBO assets involved).
// Run: java tools/GeneratePlaceholders.java src/main/resources/dev/launcherfork/mock
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;

public class GeneratePlaceholders {
    record Spec(String id, String letter, Color a, Color b) {}

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        File out = new File(args.length > 0 ? args[0] : "mock");
        new File(out, "servers").mkdirs();
        new File(out, "news").mkdirs();
        Spec[] servers = {
            new Spec("alpha", "A", new Color(0x2c5364), new Color(0x0f2027)),
            new Spec("bravo", "B", new Color(0x6b3a2a), new Color(0x1d120e)),
            new Spec("charlie", "C", new Color(0x3a5a2a), new Color(0x101a0c)),
            new Spec("delta", "D", new Color(0x4b3a6b), new Color(0x140f1f)),
        };
        for (Spec s : servers) {
            write(background(882, 539, s.a, s.b, s.id.toUpperCase()), new File(out, "servers/" + s.id + "_bg.png"));
            write(icon(80, s.a, s.letter), new File(out, "servers/" + s.id + "_icon.png"));
        }
        Color[][] news = {
            {new Color(0x1c4a61), new Color(0x0b1a22)},
            {new Color(0x672629), new Color(0x1a0a0b)},
            {new Color(0x4a4a2a), new Color(0x161609)},
            {new Color(0x2a4a4a), new Color(0x0a1616)},
        };
        for (int i = 0; i < news.length; i++) {
            write(background(520, 296, news[i][0], news[i][1], "NEWS " + (i + 1)), new File(out, "news/news_" + (i + 1) + ".png"));
        }
    }

    static BufferedImage background(int w, int h, Color a, Color b, String label) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, a, w, h, b));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(255, 255, 255, 14));
        g.setStroke(new BasicStroke(1f));
        for (int x = -h; x < w; x += 6) g.drawLine(x, h, x + h, 0);
        g.setColor(new Color(255, 255, 255, 30));
        for (int i = 0; i < 6; i++) {
            double r = (Math.min(w, h)) * (0.2 + i * 0.12);
            g.draw(new Ellipse2D.Double(w * 0.7 - r / 2, h * 0.45 - r / 2, r, r));
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(24, h / 6)));
        g.setColor(new Color(255, 255, 255, 40));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(label, w - fm.stringWidth(label) - w / 20, h - h / 12);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(10, h / 30)));
        g.setColor(new Color(255, 255, 255, 70));
        g.drawString("PLACEHOLDER", w / 30, h / 14 + 10);
        g.dispose();
        return img;
    }

    static BufferedImage icon(int s, Color c, String letter) {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, c.brighter(), s, s, c.darker()));
        g.fillRect(0, 0, s, s);
        g.setColor(new Color(255, 255, 255, 60));
        g.setStroke(new BasicStroke(s / 20f));
        g.drawRect(s / 20, s / 20, s - s / 10, s - s / 10);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) (s * 0.6)));
        g.setColor(new Color(235, 235, 235));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(letter, (s - fm.stringWidth(letter)) / 2, (s - fm.getHeight()) / 2 + fm.getAscent());
        g.dispose();
        return img;
    }

    static void write(BufferedImage img, File f) throws Exception {
        ImageIO.write(img, "png", f);
        System.out.println("wrote " + f);
    }
}

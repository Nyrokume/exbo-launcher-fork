// Generates free, procedural placeholder art for the "zone" redesign (dark, desaturated, no third-party assets):
// game backgrounds (forest + fog + lattice tower + figure silhouette), news card images, sidebar game icons.
// Run: java tools/GenerateZoneArt.java src/main/resources/dev/launcherfork
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.File;
import java.util.Random;

public class GenerateZoneArt {
    static Font display; // Bebas Neue Regular, the zone theme heading font

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        File res = new File(args.length > 0 ? args[0] : "src/main/resources/dev/launcherfork");
        display = Font.createFont(Font.TRUETYPE_FONT, new File(res, "ui/style/font/BebasNeue-Regular.ttf"));
        File servers = new File(res, "mock/servers");
        File news = new File(res, "mock/news");
        servers.mkdirs();
        news.mkdirs();
        // game backgrounds: 1600x1000, cover-scaled behind the content area
        write(scene(1600, 1000, 11, true, true), new File(servers, "stalcraft_x_bg.jpg"));
        write(scene(1600, 1000, 23, false, true), new File(servers, "bravo_bg.jpg"));
        write(scene(1600, 1000, 37, true, false), new File(servers, "charlie_bg.jpg"));
        // sidebar icons 64x64 (shown at 26)
        write(crossIcon(64), new File(servers, "stalcraft_x_icon.png"));
        write(letterIcon(64, "B"), new File(servers, "bravo_icon.png"));
        write(letterIcon(64, "T"), new File(servers, "charlie_icon.png"));
        // news cards 590x330 (shown at 295x165)
        String[] titles = {"ОБНОВЛЕНИЕ 1.2", "ИВЕНТ", "ДНЕВНИК", "ПАТЧ 0.9"};
        for (int i = 0; i < titles.length; i++) write(newsCard(590, 330, 100 + i * 17, titles[i]), new File(news, "news_" + (i + 1) + ".jpg"));
    }

    static BufferedImage scene(int w, int h, long seed, boolean tower, boolean figure) {
        Random r = new Random(seed);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        hints(g);
        g.setPaint(new GradientPaint(0, 0, gray(22), 0, h, gray(8)));
        g.fillRect(0, 0, w, h);
        fog(g, r, w, h, 0.07f, h * 0.35, h * 0.75);
        // far tree line
        trees(g, r, w, (int) (h * 0.62), 40, 90, gray(26));
        if (tower) tower(g, w * 0.70, h * 0.10, h * 0.62, gray(30));
        fog(g, r, w, h, 0.08f, h * 0.5, h * 0.8);
        trees(g, r, w, (int) (h * 0.80), 90, 200, gray(17));
        if (figure) figure(g, w * 0.36, h * 0.30, h * 0.95);
        trees(g, r, w, (int) (h * 1.02), 160, 330, gray(10));
        g.dispose();
        img = blur(img, 3);
        Graphics2D g2 = img.createGraphics();
        hints(g2);
        vignette(g2, w, h);
        g2.dispose();
        grain(img, r, 10);
        return img;
    }

    static BufferedImage newsCard(int w, int h, long seed, String title) {
        Random r = new Random(seed);
        BufferedImage img = scene(w, h, seed, r.nextBoolean(), false);
        Graphics2D g = img.createGraphics();
        hints(g);
        g.setFont(display.deriveFont(Font.PLAIN, h * 0.23f));
        FontMetrics fm = g.getFontMetrics();
        int tx = (w - fm.stringWidth(title)) / 2, ty = (int) (h * 0.58);
        g.setColor(new Color(0, 0, 0, 120));
        g.drawString(title, tx + 3, ty + 3);
        g.setColor(gray(225));
        g.drawString(title, tx, ty);
        g.dispose();
        return img;
    }

    static void trees(Graphics2D g, Random r, int w, int base, int minH, int maxH, Color c) {
        g.setColor(c);
        g.fillRect(0, base - minH / 3, w, 2000);
        for (int x = -40; x < w + 40; x += 14 + r.nextInt(26)) {
            int th = minH + r.nextInt(maxH - minH);
            int tw = th / 4 + r.nextInt(th / 6 + 1);
            Path2D p = new Path2D.Double();
            int tiers = 5;
            p.moveTo(x, base - th);
            for (int i = 1; i <= tiers; i++) {
                double y = base - th + th * i / (double) tiers;
                double half = tw / 2.0 * i / tiers;
                p.lineTo(x + half, y);
                p.lineTo(x + half * 0.45, y);
            }
            for (int i = tiers; i >= 1; i--) {
                double y = base - th + th * i / (double) tiers;
                double half = tw / 2.0 * i / tiers;
                p.lineTo(x - half * 0.45, y);
                p.lineTo(x - half, y);
            }
            p.closePath();
            g.fill(p);
        }
    }

    static void tower(Graphics2D g, double cx, double top, double base, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(3f));
        double hgt = base - top, bw = hgt * 0.16;
        Line2D l = new Line2D.Double();
        double lx0 = cx - bw / 2, rx0 = cx + bw / 2, lx1 = cx - 6, rx1 = cx + 6;
        g.draw(new Line2D.Double(lx0, base, lx1, top));
        g.draw(new Line2D.Double(rx0, base, rx1, top));
        int seg = 14;
        g.setStroke(new BasicStroke(1.6f));
        for (int i = 0; i < seg; i++) {
            double t0 = i / (double) seg, t1 = (i + 1) / (double) seg;
            double y0 = base - hgt * t0, y1 = base - hgt * t1;
            double a0 = lx0 + (lx1 - lx0) * t0, b0 = rx0 + (rx1 - rx0) * t0;
            double a1 = lx0 + (lx1 - lx0) * t1, b1 = rx0 + (rx1 - rx0) * t1;
            g.draw(new Line2D.Double(a0, y0, b0, y0));
            g.draw(new Line2D.Double(a0, y0, b1, y1));
            g.draw(new Line2D.Double(b0, y0, a1, y1));
        }
        g.setStroke(new BasicStroke(2f));
        g.draw(new Line2D.Double(cx, top, cx, top - hgt * 0.12));
        for (int k = 0; k < 3; k++) {
            double y = top + hgt * (0.1 + k * 0.18);
            g.draw(new Line2D.Double(cx - 30 - k * 8, y, cx + 30 + k * 8, y));
        }
    }

    /** Helmeted figure with a gas mask, as a soft dark silhouette with a faint rim light. */
    static void figure(Graphics2D g, double cx, double top, double bottom) {
        double hgt = bottom - top, s = hgt / 700.0;
        Area body = new Area();
        body.add(new Area(new Ellipse2D.Double(cx - 95 * s, top, 190 * s, 200 * s)));                 // helmet
        body.add(new Area(new RoundRectangle2D.Double(cx - 80 * s, top + 120 * s, 160 * s, 150 * s, 60 * s, 60 * s))); // mask
        body.add(new Area(new Ellipse2D.Double(cx - 135 * s, top + 190 * s, 90 * s, 90 * s)));        // filter L
        body.add(new Area(new Ellipse2D.Double(cx + 45 * s, top + 190 * s, 90 * s, 90 * s)));         // filter R
        Path2D torso = new Path2D.Double();
        torso.moveTo(cx - 70 * s, top + 250 * s);
        torso.curveTo(cx - 260 * s, top + 300 * s, cx - 300 * s, top + 420 * s, cx - 320 * s, bottom);
        torso.lineTo(cx + 320 * s, bottom);
        torso.curveTo(cx + 300 * s, top + 420 * s, cx + 260 * s, top + 300 * s, cx + 70 * s, top + 250 * s);
        torso.closePath();
        body.add(new Area(torso));
        Path2D rifle = new Path2D.Double();
        rifle.moveTo(cx - 360 * s, top + 560 * s);
        rifle.lineTo(cx + 260 * s, top + 430 * s);
        rifle.lineTo(cx + 270 * s, top + 470 * s);
        rifle.lineTo(cx - 350 * s, top + 610 * s);
        rifle.closePath();
        body.add(new Area(rifle));
        g.setColor(gray(48));
        AffineTransform at = g.getTransform();
        g.translate(-3, -3);
        g.fill(body);
        g.setTransform(at);
        g.setColor(gray(19));
        g.fill(body);
        // goggles / filters highlights
        g.setColor(gray(40));
        g.fill(new Ellipse2D.Double(cx - 70 * s, top + 95 * s, 60 * s, 55 * s));
        g.fill(new Ellipse2D.Double(cx + 10 * s, top + 95 * s, 60 * s, 55 * s));
        g.setColor(gray(30));
        g.fill(new Ellipse2D.Double(cx - 22 * s, top + 20 * s, 44 * s, 38 * s)); // headlamp
    }

    static void fog(Graphics2D g, Random r, int w, int h, float alpha, double y0, double y1) {
        for (int i = 0; i < 18; i++) {
            double cx = r.nextDouble() * w, cy = y0 + r.nextDouble() * (y1 - y0), rad = w * (0.15 + r.nextDouble() * 0.25);
            g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), (float) rad, new float[]{0f, 1f},
                new Color[]{new Color(200, 200, 200, (int) (255 * alpha)), new Color(200, 200, 200, 0)}));
            g.fill(new Ellipse2D.Double(cx - rad, cy - rad, rad * 2, rad * 2));
        }
    }

    static void vignette(Graphics2D g, int w, int h) {
        g.setPaint(new RadialGradientPaint(new Point2D.Double(w * 0.5, h * 0.45), (float) (w * 0.75), new float[]{0.45f, 1f},
            new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 200)}));
        g.fillRect(0, 0, w, h);
    }

    static BufferedImage crossIcon(int s) {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        hints(g);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(s * 0.16f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        double m = s * 0.18;
        g.draw(new Line2D.Double(m, m, s - m, s - m));
        g.draw(new Line2D.Double(s - m, m, m, s - m));
        // notched ends
        g.setComposite(AlphaComposite.Clear);
        g.fill(new Rectangle2D.Double(s * 0.44, 0, s * 0.12, s * 0.2));
        g.fill(new Rectangle2D.Double(s * 0.44, s * 0.8, s * 0.12, s * 0.2));
        g.dispose();
        return img;
    }

    static BufferedImage letterIcon(int s, String letter) {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        hints(g);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(s * 0.06f));
        g.draw(new Rectangle2D.Double(s * 0.1, s * 0.1, s * 0.8, s * 0.8));
        g.setFont(display.deriveFont(Font.PLAIN, s * 0.69f));
        // centre the glyph's visual bounds in the frame (font-independent, unlike ascent-based offsets)
        Rectangle2D b = g.getFont().createGlyphVector(g.getFontRenderContext(), letter).getVisualBounds();
        g.drawString(letter, (float) ((s - b.getWidth()) / 2 - b.getX()), (float) ((s - b.getHeight()) / 2 - b.getY()));
        g.dispose();
        return img;
    }

    static BufferedImage blur(BufferedImage src, int r) {
        int n = r * 2 + 1;
        float[] k = new float[n * n];
        java.util.Arrays.fill(k, 1f / (n * n));
        BufferedImage padded = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        new ConvolveOp(new Kernel(n, n, k), ConvolveOp.EDGE_NO_OP, null).filter(src, padded);
        return padded;
    }

    static void grain(BufferedImage img, Random r, int amount) {
        for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) {
            int c = img.getRGB(x, y) & 0xff;
            int v = Math.max(0, Math.min(255, c + r.nextInt(amount * 2 + 1) - amount));
            img.setRGB(x, y, (v << 16) | (v << 8) | v);
        }
    }

    static Color gray(int v) { return new Color(v, v, v); }

    static void hints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    static void write(BufferedImage img, File f) throws Exception {
        if (f.getName().endsWith(".jpg")) {
            var writer = ImageIO.getImageWritersByFormatName("jpg").next();
            var param = writer.getDefaultWriteParam();
            param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.86f);
            try (var out = ImageIO.createImageOutputStream(f)) {
                writer.setOutput(out);
                writer.write(null, new javax.imageio.IIOImage(img, null, null), param);
            }
            writer.dispose();
        } else {
            ImageIO.write(img, "png", f);
        }
        System.out.println(f + " " + img.getWidth() + "x" + img.getHeight());
    }
}

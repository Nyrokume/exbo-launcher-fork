import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * PNG -> Windows .ico with classic 32-bit BGRA DIB entries (works for NSIS and Explorer, no ImageMagick needed).
 * Usage: java packaging/MakeIco.java <in.png> <out.ico> [sizes...]
 */
public class MakeIco {
    public static void main(String[] args) throws Exception {
        BufferedImage src = ImageIO.read(new File(args[0]));
        int[] sizes = args.length > 2
            ? java.util.Arrays.stream(args, 2, args.length).mapToInt(Integer::parseInt).toArray()
            : new int[] {16, 24, 32, 48, 64};
        List<byte[]> images = new ArrayList<>();
        for (int s : sizes) images.add(dib(scale(src, s), s));
        ByteBuffer header = ByteBuffer.allocate(6 + 16 * sizes.length).order(ByteOrder.LITTLE_ENDIAN);
        header.putShort((short) 0).putShort((short) 1).putShort((short) sizes.length);
        int offset = 6 + 16 * sizes.length;
        for (int i = 0; i < sizes.length; i++) {
            int s = sizes[i];
            header.put((byte) (s >= 256 ? 0 : s)).put((byte) (s >= 256 ? 0 : s)).put((byte) 0).put((byte) 0);
            header.putShort((short) 1).putShort((short) 32).putInt(images.get(i).length).putInt(offset);
            offset += images.get(i).length;
        }
        try (FileOutputStream out = new FileOutputStream(args[1])) {
            out.write(header.array());
            for (byte[] img : images) out.write(img);
        }
    }

    static BufferedImage scale(BufferedImage src, int s) {
        BufferedImage dst = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        var g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, s, s, null);
        g.dispose();
        return dst;
    }

    /** BITMAPINFOHEADER + bottom-up BGRA pixels + 1-bit AND mask (all zero, alpha is used). */
    static byte[] dib(BufferedImage img, int s) {
        int maskRow = ((s + 31) / 32) * 4;
        ByteBuffer b = ByteBuffer.allocate(40 + s * s * 4 + maskRow * s).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(40).putInt(s).putInt(s * 2).putShort((short) 1).putShort((short) 32)
            .putInt(0).putInt(s * s * 4).putInt(0).putInt(0).putInt(0).putInt(0);
        for (int y = s - 1; y >= 0; y--)
            for (int x = 0; x < s; x++) {
                int argb = img.getRGB(x, y);
                b.put((byte) argb).put((byte) (argb >> 8)).put((byte) (argb >> 16)).put((byte) (argb >>> 24));
            }
        return b.array();
    }
}

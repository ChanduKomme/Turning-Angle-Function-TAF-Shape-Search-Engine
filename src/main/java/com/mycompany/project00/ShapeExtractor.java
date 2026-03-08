package com.mycompany.project00;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Shape extraction helpers for Turning Angle Function (TAF) descriptors.
 *
 * Original (course) assumption: black shapes on white background.
 * This version adds a more robust, fully automatic foreground extraction that works on many
 * real-world images with colored backgrounds (e.g. red backgrounds).
 *
 * Pipeline:
 *  1) Build a foreground mask (AUTO by default):
 *     - Estimate background color from border pixels.
 *     - Threshold pixels by color-distance to background using Otsu on the distance histogram.
 *     - If that looks wrong (too much/too little FG or FG leaks to border), fall back to Otsu on luminance.
 *     - Finally, fall back to legacy "white background" threshold.
 *  2) Find connected components (8-connected) and keep the K largest.
 *  3) Trace an ordered contour for each component using Moore-neighbor tracing.
 *  4) Compute Turning Angle Function descriptor on the ordered contour.
 *
 * Note: “Works for any image” is not strictly possible without heavy ML/segmentation,
 * but this AUTO mask builder is a strong baseline that handles most uniform-background and
 * many poster-like/illustration images robustly.
 */
public class ShapeExtractor {

    private ShapeExtractor() {}

    /** Segmentation mode (AUTO is used by default). */
    public enum SegmentationMode {
        /** Background-from-border + distance threshold with fallbacks. */
        AUTO,
        /** Use only background-from-border + distance threshold. */
        BORDER_COLOR_DISTANCE,
        /** Use only Otsu threshold on luminance. */
        OTSU_LUMA,
        /** Legacy: treat pixels with (r,g,b) > threshold as background. */
        LEGACY_WHITE_BG
    }

    /** Result of extracting one connected component (one object) from an image. */
    public static class ExtractedShape {
        public final int componentIndex;          // 0..K-1 (largest-first)
        public final int areaPixels;              // component area in pixels
        public final Rectangle bbox;              // bounding box in image coordinates
        public final double centroidX;            // centroid in image coordinates
        public final double centroidY;
        public final List<Point> contour;         // ordered closed contour (last point == first)
        public final double[] descriptor;         // TAF descriptor (length = samples)

        ExtractedShape(int componentIndex, int areaPixels, Rectangle bbox,
                       double centroidX, double centroidY,
                       List<Point> contour, double[] descriptor) {
            this.componentIndex = componentIndex;
            this.areaPixels = areaPixels;
            this.bbox = bbox;
            this.centroidX = centroidX;
            this.centroidY = centroidY;
            this.contour = contour;
            this.descriptor = descriptor;
        }
    }

    /**
     * Backward-compatible single-object descriptor.
     * If the image contains multiple objects, the largest connected component is used.
     */
    public static double[] descriptorFromImage(BufferedImage img, int threshold, int samples) {
        List<double[]> all = descriptorsFromImage(img, threshold, samples, 1, 50);
        if (all == null || all.isEmpty()) return null;
        return all.get(0);
    }

    /**
     * Multi-object extraction: returns up to {@code maxComponents} descriptors for the largest components.
     *
     * @param img input image
     * @param threshold legacy background threshold (used only for LEGACY_WHITE_BG fallback)
     * @param samples number of resampled TAF samples per descriptor
     * @param maxComponents max number of connected components to keep (largest first)
     * @param minComponentArea ignore components smaller than this (noise)
     */
    public static List<double[]> descriptorsFromImage(
            BufferedImage img,
            int threshold,
            int samples,
            int maxComponents,
            int minComponentArea
    ) {
        List<ExtractedShape> shapes = extractShapesFromImage(img, threshold, samples, maxComponents, minComponentArea, SegmentationMode.AUTO);
        if (shapes == null || shapes.isEmpty()) return null;

        List<double[]> out = new ArrayList<>();
        for (ExtractedShape s : shapes) {
            if (s.descriptor != null) out.add(s.descriptor);
        }
        return out.isEmpty() ? null : out;
    }

    /**
     * Extracts shapes (components) including ordered contours and descriptors.
     * This is used by the search engine and also enables coordinate export.
     */
    public static List<ExtractedShape> extractShapesFromImage(
            BufferedImage img,
            int legacyWhiteBgThreshold,
            int samples,
            int maxComponents,
            int minComponentArea,
            SegmentationMode mode
    ) {
        if (img == null) return null;
        int w = img.getWidth();
        int h = img.getHeight();

        // 1) foreground mask
        boolean[][] fg = buildForegroundMask(img, legacyWhiteBgThreshold, mode);
        if (fg == null) return null;

        // 2) connected components (largest first)
        List<Component> comps = connectedComponents(fg, minComponentArea);
        if (comps.isEmpty()) return null;
        comps.sort(Comparator.comparingInt((Component c) -> c.size).reversed());

        int keepN = Math.max(1, maxComponents);
        List<ExtractedShape> shapes = new ArrayList<>();

        int compIndex = 0;
        for (int i = 0; i < comps.size() && shapes.size() < keepN; i++) {
            Component c = comps.get(i);

            // 3) build component mask
            boolean[][] mask = new boolean[w][h];
            for (Point p : c.pixels) {
                mask[p.x][p.y] = true;
            }

            // 4) trace ordered contour
            List<Point> contour = ContourTracer.traceMooreBoundary(mask);
            if (contour == null || contour.size() < 10) {
                // fallback: boundary -> sort by angle (last resort)
                boolean[][] boundary = boundaryFromMask(mask);
                contour = ContourTracer.fallbackSortByAngle(boundary);
            }
            if (contour == null || contour.size() < 10) continue;

            // 5) descriptor
            double[] d = TurningAngleDescriptor.compute(contour, samples);
            if (d == null) continue;

            // metadata
            Rectangle bb = new Rectangle(c.minX, c.minY, (c.maxX - c.minX + 1), (c.maxY - c.minY + 1));
            double cx = (c.sumX / (double) c.size);
            double cy = (c.sumY / (double) c.size);

            shapes.add(new ExtractedShape(compIndex, c.size, bb, cx, cy, contour, d));
            compIndex++;
        }

        return shapes.isEmpty() ? null : shapes;
    }

    /**
     * Returns ordered contours only (coordinates).
     */
    public static List<List<Point>> contoursFromImage(
            BufferedImage img,
            int legacyWhiteBgThreshold,
            int maxComponents,
            int minComponentArea,
            SegmentationMode mode
    ) {
        List<ExtractedShape> shapes = extractShapesFromImage(img, legacyWhiteBgThreshold, 64, maxComponents, minComponentArea, mode);
        if (shapes == null) return null;
        List<List<Point>> out = new ArrayList<>();
        for (ExtractedShape s : shapes) out.add(s.contour);
        return out;
    }

    // ---------------------------------------------------------
    // Foreground mask builder (AUTO)
    // ---------------------------------------------------------

    /**
     * Builds a foreground mask for the image.
     * @param legacyWhiteBgThreshold used only for LEGACY_WHITE_BG fallback
     */
    public static boolean[][] buildForegroundMask(BufferedImage img, int legacyWhiteBgThreshold, SegmentationMode mode) {
        if (img == null) return null;
        int w = img.getWidth();
        int h = img.getHeight();

        SegmentationMode m = (mode == null) ? SegmentationMode.AUTO : mode;

        // AUTO tries: border-color-distance -> validate -> fallback to luma-otsu -> fallback to legacy threshold
        if (m == SegmentationMode.AUTO || m == SegmentationMode.BORDER_COLOR_DISTANCE) {
            boolean[][] mask = maskByBorderColorDistance(img);
            if (mask != null) {
                mask = postProcessMask(mask);
                if (m == SegmentationMode.BORDER_COLOR_DISTANCE) return mask;

                if (looksReasonable(mask)) return mask;
                // else fall through to other modes
            }
        }

        if (m == SegmentationMode.AUTO || m == SegmentationMode.OTSU_LUMA) {
            boolean[][] mask = maskByLumaOtsu(img);
            if (mask != null) {
                mask = postProcessMask(mask);
                if (m == SegmentationMode.OTSU_LUMA) return mask;

                if (looksReasonable(mask)) return mask;
            }
        }

        // Legacy fallback (white background)
        return maskByLegacyWhiteBg(img, legacyWhiteBgThreshold);
    }

    private static boolean[][] maskByLegacyWhiteBg(BufferedImage img, int threshold) {
        int w = img.getWidth();
        int h = img.getHeight();
        boolean[][] fg = new boolean[w][h];

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int argb = img.getRGB(x, y);
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                fg[x][y] = !(r > threshold && g > threshold && b > threshold);
            }
        }
        return fg;
    }

    /**
     * Background color estimation from borders + Otsu on color-distance to that background.
     */
    private static boolean[][] maskByBorderColorDistance(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();

        // Sample border pixels (a thin frame)
        int margin = Math.max(2, Math.min(w, h) / 200); // ~0.5% of min dimension, at least 2
        int step = Math.max(1, Math.min(w, h) / 300);   // sample down for speed

        List<Integer> rs = new ArrayList<>();
        List<Integer> gs = new ArrayList<>();
        List<Integer> bs = new ArrayList<>();

        for (int x = 0; x < w; x += step) {
            for (int dy = 0; dy < margin; dy++) {
                addRGB(img.getRGB(x, dy), rs, gs, bs);
                addRGB(img.getRGB(x, h - 1 - dy), rs, gs, bs);
            }
        }
        for (int y = 0; y < h; y += step) {
            for (int dx = 0; dx < margin; dx++) {
                addRGB(img.getRGB(dx, y), rs, gs, bs);
                addRGB(img.getRGB(w - 1 - dx, y), rs, gs, bs);
            }
        }
        if (rs.size() < 50) return null;

        int br = medianOf(rs);
        int bg = medianOf(gs);
        int bb = medianOf(bs);

        // Build histogram of Euclidean distance to background
        int[] hist = new int[442]; // 0..441
        int[][] dist = new int[w][h];

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int argb = img.getRGB(x, y);
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;

                int dr = r - br;
                int dg = g - bg;
                int db = b - bb;

                int d = (int) Math.round(Math.sqrt(dr*dr + dg*dg + db*db));
                if (d < 0) d = 0;
                if (d > 441) d = 441;

                dist[x][y] = d;
                hist[d]++;
            }
        }

        int t = otsuThreshold(hist);
        // If Otsu picks something degenerate, bump a bit
        if (t < 5) t = 5;

        boolean[][] fg = new boolean[w][h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                fg[x][y] = dist[x][y] > t;
            }
        }

        // If too many border pixels are foreground, this background model is likely wrong.
        double borderFg = borderForegroundFraction(fg, margin);
        if (borderFg > 0.35) return null;

        return fg;
    }

    /**
     * Otsu threshold on luminance; chooses polarity that keeps borders mostly background.
     */
    private static boolean[][] maskByLumaOtsu(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();

        int[] hist = new int[256];
        int[][] lum = new int[w][h];

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int argb = img.getRGB(x, y);
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                int yv = (int) Math.round(0.299*r + 0.587*g + 0.114*b);
                if (yv < 0) yv = 0;
                if (yv > 255) yv = 255;
                lum[x][y] = yv;
                hist[yv]++;
            }
        }

        int t = otsuThreshold(hist);

        boolean[][] fgDark = new boolean[w][h];
        boolean[][] fgLight = new boolean[w][h];

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                fgDark[x][y] = lum[x][y] < t;
                fgLight[x][y] = lum[x][y] > t;
            }
        }

        int margin = Math.max(2, Math.min(w, h) / 200);
        double bDark = borderForegroundFraction(fgDark, margin);
        double bLight = borderForegroundFraction(fgLight, margin);

        // Choose the mask that keeps border mostly background
        boolean[][] chosen = (bDark <= bLight) ? fgDark : fgLight;

        // If both are awful, consider this a failure
        double bChosen = Math.min(bDark, bLight);
        if (bChosen > 0.45) return null;

        return chosen;
    }

    private static boolean[][] postProcessMask(boolean[][] fg) {
        // Simple closing to connect small gaps
        boolean[][] d = dilate(fg, 1);
        boolean[][] e = erode(d, 1);

        // Fill holes: background flood-fill from border on inverted mask
        boolean[][] filled = fillHoles(e);
        return filled;
    }

    private static boolean looksReasonable(boolean[][] fg) {
        int w = fg.length;
        int h = fg[0].length;

        int total = w * h;
        int fgCount = 0;
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (fg[x][y]) fgCount++;
            }
        }
        double ratio = fgCount / (double) total;

        // heuristic: foreground should not be almost everything or almost nothing
        if (ratio < 0.002 || ratio > 0.98) return false;

        int margin = Math.max(2, Math.min(w, h) / 200);
        double borderFg = borderForegroundFraction(fg, margin);

        // heuristic: border should usually be background
        return borderFg < 0.35;
    }

    private static double borderForegroundFraction(boolean[][] fg, int margin) {
        int w = fg.length;
        int h = fg[0].length;

        int count = 0;
        int fgCount = 0;

        // top/bottom
        for (int y = 0; y < margin; y++) {
            for (int x = 0; x < w; x++) {
                count++;
                if (fg[x][y]) fgCount++;
                count++;
                if (fg[x][h - 1 - y]) fgCount++;
            }
        }
        // left/right
        for (int x = 0; x < margin; x++) {
            for (int y = 0; y < h; y++) {
                count++;
                if (fg[x][y]) fgCount++;
                count++;
                if (fg[w - 1 - x][y]) fgCount++;
            }
        }

        if (count == 0) return 1.0;
        return fgCount / (double) count;
    }

    private static void addRGB(int argb, List<Integer> rs, List<Integer> gs, List<Integer> bs) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        rs.add(r);
        gs.add(g);
        bs.add(b);
    }

    private static int medianOf(List<Integer> values) {
        values.sort(Integer::compareTo);
        return values.get(values.size() / 2);
    }

    /**
     * Otsu threshold on a histogram (any number of bins).
     * Returns the threshold bin index.
     */
    private static int otsuThreshold(int[] hist) {
        long total = 0;
        for (int h : hist) total += h;
        if (total == 0) return 0;

        long sum = 0;
        for (int i = 0; i < hist.length; i++) sum += (long) i * hist[i];

        long sumB = 0;
        long wB = 0;
        long wF;
        double maxVar = -1.0;
        int threshold = 0;

        for (int t = 0; t < hist.length; t++) {
            wB += hist[t];
            if (wB == 0) continue;

            wF = total - wB;
            if (wF == 0) break;

            sumB += (long) t * hist[t];

            double mB = sumB / (double) wB;
            double mF = (sum - sumB) / (double) wF;

            double between = (double) wB * (double) wF * (mB - mF) * (mB - mF);
            if (between > maxVar) {
                maxVar = between;
                threshold = t;
            }
        }
        return threshold;
    }

    // Morphology (radius=1 is a 3x3 neighborhood)
    private static boolean[][] dilate(boolean[][] src, int radius) {
        int w = src.length;
        int h = src[0].length;
        boolean[][] out = new boolean[w][h];

        int r = Math.max(1, radius);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (!src[x][y]) continue;
                for (int dx = -r; dx <= r; dx++) {
                    for (int dy = -r; dy <= r; dy++) {
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                        out[nx][ny] = true;
                    }
                }
            }
        }
        return out;
    }

    private static boolean[][] erode(boolean[][] src, int radius) {
        int w = src.length;
        int h = src[0].length;
        boolean[][] out = new boolean[w][h];

        int r = Math.max(1, radius);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                boolean ok = true;
                for (int dx = -r; dx <= r && ok; dx++) {
                    for (int dy = -r; dy <= r; dy++) {
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) { ok = false; break; }
                        if (!src[nx][ny]) { ok = false; break; }
                    }
                }
                out[x][y] = ok;
            }
        }
        return out;
    }

    private static boolean[][] fillHoles(boolean[][] fg) {
        int w = fg.length;
        int h = fg[0].length;

        // background mask (inverse)
        boolean[][] bg = new boolean[w][h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                bg[x][y] = !fg[x][y];
            }
        }

        boolean[][] visited = new boolean[w][h];
        ArrayDeque<Point> q = new ArrayDeque<>();

        // enqueue all border background pixels
        for (int x = 0; x < w; x++) {
            if (bg[x][0]) { visited[x][0] = true; q.add(new Point(x,0)); }
            if (bg[x][h-1]) { visited[x][h-1] = true; q.add(new Point(x,h-1)); }
        }
        for (int y = 0; y < h; y++) {
            if (bg[0][y]) { visited[0][y] = true; q.add(new Point(0,y)); }
            if (bg[w-1][y]) { visited[w-1][y] = true; q.add(new Point(w-1,y)); }
        }

        int[] dx = {-1,0,1,0,-1,-1,1,1};
        int[] dy = {0,-1,0,1,-1,1,-1,1};

        while (!q.isEmpty()) {
            Point p = q.removeFirst();
            for (int k = 0; k < 8; k++) {
                int nx = p.x + dx[k];
                int ny = p.y + dy[k];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                if (visited[nx][ny]) continue;
                if (!bg[nx][ny]) continue;
                visited[nx][ny] = true;
                q.add(new Point(nx, ny));
            }
        }

        // Any background pixel not visited is a hole -> fill it (set FG=true)
        boolean[][] out = new boolean[w][h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (fg[x][y]) out[x][y] = true;
                else out[x][y] = !visited[x][y]; // hole pixels become true
            }
        }
        return out;
    }

    // ---------------------------
    // Connected components
    // ---------------------------
    private static class Component {
        final int size;
        final List<Point> pixels;
        final int minX, minY, maxX, maxY;
        final long sumX, sumY;

        Component(int size, List<Point> pixels, int minX, int minY, int maxX, int maxY, long sumX, long sumY) {
            this.size = size;
            this.pixels = pixels;
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
            this.sumX = sumX;
            this.sumY = sumY;
        }
    }

    private static List<Component> connectedComponents(boolean[][] fg, int minArea) {
        int w = fg.length;
        int h = fg[0].length;

        boolean[][] visited = new boolean[w][h];
        List<Component> comps = new ArrayList<>();

        int[] dx = {-1,-1,-1,0,0,1,1,1};
        int[] dy = {-1,0,1,-1,1,-1,0,1};

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (!fg[x][y] || visited[x][y]) continue;

                ArrayDeque<Point> q = new ArrayDeque<>();
                List<Point> pixels = new ArrayList<>();
                q.add(new Point(x, y));
                visited[x][y] = true;

                int minX = x, maxX = x, minY = y, maxY = y;
                long sumX = 0, sumY = 0;

                while (!q.isEmpty()) {
                    Point p = q.removeFirst();
                    pixels.add(p);
                    sumX += p.x;
                    sumY += p.y;
                    if (p.x < minX) minX = p.x;
                    if (p.x > maxX) maxX = p.x;
                    if (p.y < minY) minY = p.y;
                    if (p.y > maxY) maxY = p.y;

                    for (int k = 0; k < 8; k++) {
                        int nx = p.x + dx[k];
                        int ny = p.y + dy[k];
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                        if (!fg[nx][ny] || visited[nx][ny]) continue;
                        visited[nx][ny] = true;
                        q.add(new Point(nx, ny));
                    }
                }

                if (pixels.size() >= minArea) {
                    comps.add(new Component(pixels.size(), pixels, minX, minY, maxX, maxY, sumX, sumY));
                }
            }
        }

        return comps;
    }

    // ---------------------------
    // Boundary helper (used by contour fallback)
    // ---------------------------
    static boolean[][] boundaryFromMask(boolean[][] mask) {
        int w = mask.length;
        int h = mask[0].length;
        boolean[][] boundary = new boolean[w][h];

        int[] dx = {-1,-1,-1,0,0,1,1,1};
        int[] dy = {-1,0,1,-1,1,-1,0,1};

        for (int x = 1; x < w - 1; x++) {
            for (int y = 1; y < h - 1; y++) {
                if (!mask[x][y]) continue;
                boolean isBoundary = false;
                for (int k = 0; k < 8; k++) {
                    int nx = x + dx[k];
                    int ny = y + dy[k];
                    if (!mask[nx][ny]) { isBoundary = true; break; }
                }
                boundary[x][y] = isBoundary;
            }
        }
        return boundary;
    }
}

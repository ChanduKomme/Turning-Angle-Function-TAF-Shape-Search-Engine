package com.mycompany.project00;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

/**
 * Turning-Angle-Function (TAF) based shape search engine.
 *
 * Supports:
 *  - Single-object datasets (typical): each image has one silhouette.
 *  - Multi-object images: each image can contribute multiple descriptors (largest connected components).
 *
 * Similarity for multi-object case:
 *  - Build a cost matrix between query components and candidate components using cyclic TAF distance.
 *  - Find minimum-cost one-to-one matching (DP assignment; works well for small number of components).
 *  - Add penalties for missing/extra components so "both objects" matter for mosaics.
 */
public class ShapeSearchEngine {

    public static class Entry {
        public final File file;
        public final List<double[]> descriptors; // one per connected component (largest first)

        public Entry(File file, List<double[]> descriptors) {
            this.file = file;
            this.descriptors = descriptors;
        }
    }

    public static class Result {
        public final Entry entry;
        public final double distance;

        public Result(Entry entry, double distance) {
            this.entry = entry;
            this.distance = distance;
        }
    }

    private final List<Entry> index = new ArrayList<>();

    // Descriptor extraction parameters
    private int threshold = 220; // legacy white-background threshold (used only as last fallback)
    private int samples = 128;

    // Segmentation mode (AUTO works well for many real-world images)
    private ShapeExtractor.SegmentationMode segmentationMode = ShapeExtractor.SegmentationMode.AUTO;

    // Multi-object parameters
    private int maxComponents = 5;         // keep up to K largest components per image
    private int minComponentArea = 200;    // ignore tiny noise components

    // Distance penalties (tune for your dataset)
    private double missingComponentPenalty = 10.0; // penalty per missing query component
    private double extraComponentPenalty   = 2.0;  // penalty per extra candidate component

    public void setThreshold(int threshold) { this.threshold = threshold; }
    public void setSamples(int samples) { this.samples = samples; }
    public void setSegmentationMode(ShapeExtractor.SegmentationMode mode) {
        this.segmentationMode = (mode == null) ? ShapeExtractor.SegmentationMode.AUTO : mode;
    }
    public void setMaxComponents(int maxComponents) { this.maxComponents = Math.max(1, maxComponents); }
    public void setMinComponentArea(int minComponentArea) { this.minComponentArea = Math.max(1, minComponentArea); }
    public void setMissingComponentPenalty(double p) { this.missingComponentPenalty = Math.max(0.0, p); }
    public void setExtraComponentPenalty(double p) { this.extraComponentPenalty = Math.max(0.0, p); }

    public int getThreshold() { return threshold; }
    public int getSamples() { return samples; }
    public ShapeExtractor.SegmentationMode getSegmentationMode() { return segmentationMode; }
    public int getMaxComponents() { return maxComponents; }
    public int getMinComponentArea() { return minComponentArea; }
    public double getMissingComponentPenalty() { return missingComponentPenalty; }
    public double getExtraComponentPenalty() { return extraComponentPenalty; }

    public void clear() { index.clear(); }
    public int size() { return index.size(); }
    public List<Entry> getIndex() { return List.copyOf(index); }

    public int indexDirectory(File dir) throws IOException {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            throw new IOException("Not a directory: " + dir);
        }
        List<File> files = new ArrayList<>();
        collectImagesRec(dir, files);

        int added = 0;
        for (File f : files) {
            try {
                BufferedImage img = ImageIO.read(f);
                if (img == null) continue;

                List<ShapeExtractor.ExtractedShape> shapes = ShapeExtractor.extractShapesFromImage(
                        img, threshold, samples, maxComponents, minComponentArea, segmentationMode);
                if (shapes == null || shapes.isEmpty()) continue;

                List<double[]> ds = new ArrayList<>();
                for (ShapeExtractor.ExtractedShape s : shapes) ds.add(s.descriptor);
                if (ds.isEmpty()) continue;

                index.add(new Entry(f, ds));
                added++;
            } catch (Exception e) {
                // skip unreadable images
            }
        }
        return added;
    }

    public List<Result> search(BufferedImage queryImage, int topK) throws IOException {
        if (queryImage == null) throw new IOException("Query image is null");
        if (index.isEmpty()) throw new IOException("Index is empty. Use Search -> Index Folder first.");

        List<ShapeExtractor.ExtractedShape> qShapes = ShapeExtractor.extractShapesFromImage(
                queryImage, threshold, samples, maxComponents, minComponentArea, segmentationMode);
        if (qShapes == null || qShapes.isEmpty()) throw new IOException("Could not extract descriptor(s) from query image.");
        List<double[]> q = new ArrayList<>();
        for (ShapeExtractor.ExtractedShape s : qShapes) q.add(s.descriptor);

        return search(q, topK);
    }

    public List<Result> search(List<double[]> queryDescriptors, int topK) {
        int k = Math.max(1, topK);
        List<Result> results = new ArrayList<>();

        for (Entry e : index) {
            double dist = multiObjectDistance(queryDescriptors, e.descriptors, missingComponentPenalty, extraComponentPenalty);
            results.add(new Result(e, dist));
        }

        results.sort(Comparator.comparingDouble(r -> r.distance));
        if (results.size() > k) return results.subList(0, k);
        return results;
    }

    /**
     * Multi-object distance between two sets of descriptors.
     *
     * We match components one-to-one with minimum total distance and then add penalties for
     * unmatched components so that "use both objects" is enforced.
     */
    static double multiObjectDistance(List<double[]> query, List<double[]> cand, double missingPenalty, double extraPenalty) {
        if (query == null || cand == null) return Double.POSITIVE_INFINITY;
        int m = query.size();
        int n = cand.size();
        if (m == 0 || n == 0) return Double.POSITIVE_INFINITY;

        // cost[i][j] = distance between query component i and candidate component j
        double[][] cost = new double[m][n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                cost[i][j] = TurningAngleDescriptor.distanceCyclic(query.get(i), cand.get(j));
            }
        }

        if (m <= n) {
            double best = minCostAssignment(cost); // assigns each query to a distinct candidate
            double penalty = extraPenalty * (n - m);
            return (best + penalty) / m;
        } else {
            // transpose so rows <= cols
            double[][] costT = new double[n][m];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < m; j++) costT[i][j] = cost[j][i];
            }
            double best = minCostAssignment(costT); // assigns each candidate to a distinct query
            double penalty = missingPenalty * (m - n);
            return (best + penalty) / m; // normalize by query component count
        }
    }

    /**
     * Minimum-cost one-to-one matching for small matrices using DP over bitmasks.
     * Assumes rows <= cols. Complexity: O(cols * 2^cols) which is fine for <= ~12 cols.
     *
     * @return minimum sum of costs matching each row to a distinct column
     */
    private static double minCostAssignment(double[][] cost) {
        int rows = cost.length;
        int cols = cost[0].length;
        if (rows > cols) throw new IllegalArgumentException("minCostAssignment requires rows <= cols");

        int maxMask = 1 << cols;
        double INF = 1e100;

        double[] dp = new double[maxMask];
        Arrays.fill(dp, INF);
        dp[0] = 0.0;

        for (int mask = 0; mask < maxMask; mask++) {
            int i = Integer.bitCount(mask); // next row to assign
            if (i >= rows) continue;
            double cur = dp[mask];
            if (cur >= INF / 2) continue;

            for (int j = 0; j < cols; j++) {
                if ((mask & (1 << j)) != 0) continue;
                int nmask = mask | (1 << j);
                double v = cur + cost[i][j];
                if (v < dp[nmask]) dp[nmask] = v;
            }
        }

        double best = INF;
        for (int mask = 0; mask < maxMask; mask++) {
            if (Integer.bitCount(mask) == rows) {
                if (dp[mask] < best) best = dp[mask];
            }
        }
        return best;
    }

    private static void collectImagesRec(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) return;

        for (File f : children) {
            if (f.isDirectory()) {
                collectImagesRec(f, out);
            } else {
                String n = f.getName().toLowerCase();
                if (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".bmp") || n.endsWith(".gif")) {
                    out.add(f);
                }
            }
        }
    }
}

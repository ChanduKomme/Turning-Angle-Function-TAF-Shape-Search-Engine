package com.mycompany.project00;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.net.URL;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public class ImageProcessing {

    public BufferedImage img = null;
    public BufferedImage img_out = null;
    public BufferedImage img_out1 = null;
    public BufferedImage img_out2 = null;
    public BufferedImage img_out3 = null;
    public BufferedImage img_out4 = null;

    public int[][] boundary_cloud;

    public int wBlock = 2;   // number of blocks horizontally
    public int hBlock = 1;   // number of blocks vertically

    public int sizeX;
    public int sizeY;

    // lists per block
    public LinkedList[][] neighbours = new LinkedList[wBlock][hBlock];
    public LinkedList[][] neighboursT = new LinkedList[wBlock][hBlock];
    public LinkedList[][] listGrowing = new LinkedList[wBlock][hBlock];
    public LinkedList[][] stopper = new LinkedList[wBlock][hBlock];

    // seed points per block
    public int[][] x0 = new int[wBlock][hBlock];
    public int[][] y0 = new int[wBlock][hBlock];

    // segmentation threshold
    public int thres = 220;

    // image size
    public int N = 0;
    public int M = 0;

    public GUI gui;

    // Turning Angle Function (TAF) based search engine
    private final ShapeSearchEngine searchEngine = new ShapeSearchEngine();

    public ImageProcessing() {

        gui = new GUI(this);

        // init lists
        for (int a = 0; a < wBlock; a++) {
            for (int b = 0; b < hBlock; b++) {
                neighbours[a][b] = new LinkedList();
                neighboursT[a][b] = new LinkedList();
                listGrowing[a][b] = new LinkedList();
                stopper[a][b] = new LinkedList();
                x0[a][b] = 0;
                y0[a][b] = 0;
            }
        }

        // AUTO LOAD IMAGE from src/main/resources
        try {
            URL url = ImageProcessing.class.getClassLoader().getResource("BeispielMosaicBild01.jpg");
            System.out.println("RESOURCE URL = " + url);

            if (url == null) {
                JOptionPane.showMessageDialog(null,
                        "Image not found!\n\nPut BeispielMosaicBild01.jpg in:\n"
                        + "src/main/resources/\n\nThen: Run -> Clean and Build Project");
                return;
            }

            img = ImageIO.read(url);

            img_out  = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            img_out1 = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            img_out2 = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            img_out3 = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            img_out4 = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);

            gui.input.setIcon(new ImageIcon(img));
            gui.output.setIcon(new ImageIcon(img_out));
            gui.output1.setIcon(new ImageIcon(img_out1));
            gui.output2.setIcon(new ImageIcon(img_out2));
            gui.output3.setIcon(new ImageIcon(img_out3));
            gui.output4.setIcon(new ImageIcon(img_out4));

            gui.pack();

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Failed to load image: " + e.getMessage());
        }
    }

   
    public void autoRunAll() {

        System.out.println(">>> autoRunAll() STARTED");

        if (img == null) {
            JOptionPane.showMessageDialog(null, "No image loaded.");
            return;
        }

        // clear previous lists each Start
        for (int a = 0; a < wBlock; a++) {
            for (int b = 0; b < hBlock; b++) {
                neighbours[a][b].clear();
                neighboursT[a][b].clear();
                listGrowing[a][b].clear();
                stopper[a][b].clear();
            }
        }

        // 1) compute segmentation + boundary cloud
        process();
        repaintAll();

        // 2) auto seed points (no clicks)   updated
        autoSelectSeedPoints();
        System.out.println("Seed 0 = " + x0[0][0] + "," + y0[0][0]);
        System.out.println("Seed 1 = " + x0[1][0] + "," + y0[1][0]);
        
       int red = setARGB(255, 255, 0, 0);

for (int a = 0; a < wBlock; a++) {
    for (int b = 0; b < hBlock; b++) {

        int sx = x0[a][b];
        int sy = y0[a][b];

        // draw a visible red cross (radius 4)
        for (int t = -4; t <= 4; t++) {
            int x1 = sx + t;
            int y1 = sy;
            int x2 = sx;
            int y2 = sy + t;

            if (x1 >= 0 && x1 < N && y1 >= 0 && y1 < M) img_out2.setRGB(x1, y1, red);
            if (x2 >= 0 && x2 < N && y2 >= 0 && y2 < M) img_out2.setRGB(x2, y2, red);
        }
    }
}
        repaintAll();

        // 3) initial grow step
        stopper();
        repaintAll();

        // 4) full list + draw + fourier
        fullList();
        repaintAll();
    }

  
 private boolean hasBoundaryNeighbour(int x, int y) {
    if (x < 0 || y < 0 || x >= N || y >= M) return false;
    if (boundary_cloud[x][y] != 255) return false;

    for (int dx = -1; dx <= 1; dx++) {
        for (int dy = -1; dy <= 1; dy++) {
            if (dx == 0 && dy == 0) continue;
            int nx = x + dx;
            int ny = y + dy;
            if (nx >= 0 && ny >= 0 && nx < N && ny < M) {
                if (boundary_cloud[nx][ny] == 255) return true;
            }
        }
    }
    return false;
}

   
    public void autoSelectSeedPoints() {
    if (boundary_cloud == null || img == null) return;

    N = img.getWidth();
    M = img.getHeight();

    sizeX = N / wBlock;
    sizeY = M / hBlock;

    int red = setARGB(255, 255, 0, 0);

    for (int a = 0; a < wBlock; a++) {
        for (int b = 0; b < hBlock; b++) {

            int xStart = a * sizeX;
            int xEnd = Math.min(((a + 1) * sizeX) - 1, N - 1);

            int yStart = b * sizeY;
            int yEnd = Math.min(((b + 1) * sizeY) - 1, M - 1);

            int foundX = -1;
            int foundY = -1;

            //  FIND A CONNECTED boundary pixel (must have a neighbour)
            outer:
            for (int x = xStart + 1; x < xEnd; x++) {
                for (int y = yStart + 1; y < yEnd; y++) {
                    if (boundary_cloud[x][y] == 255 && hasBoundaryNeighbour(x, y)) {
                        foundX = x;
                        foundY = y;
                        break outer;
                    }
                }
            }

            if (foundX == -1) {
                // If truly nothing connected exists, show message and stop
                JOptionPane.showMessageDialog(null,
                        "No connected boundary found in block a=" + a + ", b=" + b +
                        "\nTry changing threshold thres=" + thres + " (e.g. 200 or 240).");
                return;
            }

            x0[a][b] = foundX;
            y0[a][b] = foundY;

            // Mark seed point
            img_out2.setRGB(foundX, foundY, red);
        }
    }
}

   
    
  private LinkedList<Point> sortByAngle(LinkedList pts) {
    if (pts == null || pts.isEmpty()) return pts;

    double cx = 0, cy = 0;
    for (Object o : pts) {
        Point p = (Point) o;
        cx += p.x;
        cy += p.y;
    }
    cx /= pts.size();
    cy /= pts.size();

    java.util.ArrayList<Point> list = new java.util.ArrayList<>();
    for (Object o : pts) list.add((Point) o);

    final double ccx = cx, ccy = cy;
    list.sort((p1, p2) -> Double.compare(
            Math.atan2(p1.y - ccy, p1.x - ccx),
            Math.atan2(p2.y - ccy, p2.x - ccx)
    ));

    LinkedList<Point> out = new LinkedList<>();
    out.addAll(list);
    return out;
}
public void stopper() {

    int red = setARGB(255, 255, 0, 0);

    for (int a = 0; a < wBlock; a++) {
        for (int b = 0; b < hBlock; b++) {

            int sx = x0[a][b];
            int sy = y0[a][b];

            // draw red cross ONLY on Output2
            for (int t = -4; t <= 4; t++) {

                int x1 = sx + t;
                int y1 = sy;

                int x2 = sx;
                int y2 = sy + t;

                if (x1 >= 0 && x1 < N && y1 >= 0 && y1 < M)
                    img_out2.setRGB(x1, y1, red);

                if (x2 >= 0 && x2 < N && y2 >= 0 && y2 < M)
                    img_out2.setRGB(x2, y2, red);
            }
        }
    }

    // set frontier to seed
    for (int a = 0; a < wBlock; a++) {
        for (int b = 0; b < hBlock; b++) {
            neighboursT[a][b].clear();
            neighboursT[a][b].add(new Point(x0[a][b], y0[a][b]));
        }
    }

    // grow one step
    for (int a = 0; a < wBlock; a++) {
        for (int b = 0; b < hBlock; b++) {
            neighbourneighbour(a, b, stopper[a][b]);
        }
    }
}
 
    public void fullList() {

        // start with seed as frontier
        for (int a = 0; a < wBlock; a++) {
    for (int b = 0; b < hBlock; b++) {

        neighbours[a][b].clear();
        listGrowing[a][b].clear();
        stopper[a][b].clear();

        //  Start frontier from a small radius around seed
        initFrontierAroundSeed(a, b, 3);
    }
}
System.out.println("Frontier sizes: L=" + neighboursT[0][0].size() + " R=" + neighboursT[1][0].size());
        // do one grow step first to ensure frontier is not empty
        for (int a = 0; a < wBlock; a++) {
            for (int b = 0; b < hBlock; b++) {
                neighbourneighbour(a, b, neighbours[a][b]);
                neighboursT[a][b].clear();
                neighboursT[a][b].addAll(neighbours[a][b]);
            }
        }

        System.out.println("Initial neighbours size left=" + neighbours[0][0].size()
                + " right=" + neighbours[1][0].size());

        // iterations (can increase later)
        int ITER = 300;
        for (int k = 0; k < ITER; k++) {
            for (int a = 0; a < wBlock; a++) {
                for (int b = 0; b < hBlock; b++) {
                    neighbourneighbour(a, b, neighbours[a][b]);
                }
            }

            if (k % 50 == 0) {
                System.out.println("k=" + k + " size L=" + neighbours[0][0].size() + " R=" + neighbours[1][0].size());
                repaintAll();
            }
        }

        // paint output4 final list
        int blue = setARGB(255, 0, 0, 255);
        for (int a = 0; a < wBlock; a++) {
            for (int b = 0; b < hBlock; b++) {
                Iterator it1 = neighbours[a][b].iterator();
                while (it1.hasNext()) {
                    Point p = (Point) it1.next();
                    if (p.x >= 0 && p.y >= 0 && p.x < N && p.y < M) {
                        img_out4.setRGB(p.x, p.y, blue);
                    }
                }
            }
        }

        // Fourier overlay (optional)
        for (int a = 0; a < wBlock; a++) {
            for (int b = 0; b < hBlock; b++) {
                fourier(sortByAngle(neighbours[a][b]));
            }
        }
    }

 
    public void neighbourneighbour(int a, int b, LinkedList neighbours_) {

        int argb = setARGB(255, 100, 0, 100);

        // create a fresh list for this growing step
        listGrowing[a][b] = new LinkedList();

        Iterator it = neighboursT[a][b].iterator();

        while (it.hasNext()) {
            Point u = (Point) it.next();
            int x = u.x;
            int y = u.y;

            // check all 8 neighbours
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {

                    if (dx == 0 && dy == 0) continue;

                    int nx = x + dx;
                    int ny = y + dy;

                    if (nx < 0 || ny < 0 || nx >= N || ny >= M) continue;

                    if (boundary_cloud[nx][ny] == 255) {

                        Point P = new Point(nx, ny);

                        if (!neighboursT[a][b].contains(P)
                                && !listGrowing[a][b].contains(P)
                                && !stopper[a][b].contains(P)) {

                            listGrowing[a][b].add(P);
                            img_out3.setRGB(P.x, P.y, argb);
                        }
                    }
                }
            }
        }

        neighbours_.addAll(listGrowing[a][b]);

        neighboursT[a][b] = new LinkedList();
        neighboursT[a][b].addAll(neighbours_);
    }

  
    public void fourier(LinkedList neighboursList) {

        int NN = neighboursList.size();
        if (NN <= 0) return;

        DFT dft = new DFT(NN);
        Complex[] z = new Complex[NN];

        Iterator it = neighboursList.iterator();
        int i = 0;
        while (it.hasNext() && i < NN) {
            Point p = (Point) it.next();
            z[i] = new Complex((double) p.x, (double) p.y);
            i++;
        }

        Complex[] c = dft.transform(z);

        Complex[] c_filtered = new Complex[NN];
        for (int j = 0; j < NN; j++) c_filtered[j] = new Complex(0.0, 0.0);

        c_filtered[0] = c[0];

        for (int k = 1; k <= 8; k++) {
            if (k < NN) c_filtered[k] = c[k];
            if (NN - k >= 0) c_filtered[NN - k] = c[NN - k];
        }

        Complex[] rec = dft.invtrans(c_filtered);

        int yellow = setARGB(255, 255, 255, 0);
        for (int j = 0; j < NN; j++) {
            int x = (int) rec[j].getReal();
            int y = (int) rec[j].getImag();
            if (x >= 0 && y >= 0 && x < N && y < M) {
                img_out3.setRGB(x, y, yellow);
            }
        }
    }

    
    public void process() {

        if (img == null) return;

        int P = 1;
        int Q = 1;

        N = img.getWidth();
        M = img.getHeight();

        int[][] segm_data = new int[N][M];
        int[][] segm_data_extended = new int[N][M];
        boundary_cloud = new int[N][M];

        // segmentation
        for (int x = 0; x < N; x++) {
            for (int y = 0; y < M; y++) {

                int argb_in = img.getRGB(x, y);
                int r = getRed(argb_in);
                int g = getGreen(argb_in);
                int b = getBlue(argb_in);

                segm_data[x][y] = 255;

                if (!(r > thres && g > thres && b > thres)) {
                    segm_data[x][y] = 0;
                }

                int newgrey = segm_data[x][y];
                int argb_out = setARGB(255, newgrey, newgrey, newgrey);
                img_out.setRGB(x, y, argb_out);
            }
        }

        // morphological filtering
        for (int x = P; x < N - P; x++) {
            for (int y = Q; y < M - Q; y++) {

                int[] ar = new int[(2 * P + 1) * (2 * Q + 1)];

                int index = 0;
                for (int u = -P; u <= P; u++) {
                    for (int v = -Q; v <= Q; v++) {
                        ar[index] = segm_data[x + u][y + v];
                        index++;
                    }
                }

                Arrays.sort(ar);

                segm_data_extended[x][y] = ar[0];

                int newgrey = segm_data_extended[x][y];
                int argb_out = setARGB(255, newgrey, newgrey, newgrey);
                img_out1.setRGB(x, y, argb_out);
            }
        }

        // subtraction => boundary cloud
       // NEW boundary extraction (connected boundary)
for (int x = 1; x < N - 1; x++) {
    for (int y = 1; y < M - 1; y++) {

        boundary_cloud[x][y] = 0;

        if (segm_data[x][y] == 0) {

            // check if any neighbour is background
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {

                    if (dx == 0 && dy == 0) continue;

                    if (segm_data[x + dx][y + dy] == 255) {
                        boundary_cloud[x][y] = 255;
                    }
                }
            }
        }

        int v = boundary_cloud[x][y];
        int argb_out = setARGB(255, v, v, v);

        img_out2.setRGB(x, y, argb_out);
        img_out3.setRGB(x, y, argb_out);
        img_out4.setRGB(x, y, argb_out);
    }
}
int count = 0;
for (int x = 0; x < N; x++) {
    for (int y = 0; y < M; y++) {
        if (boundary_cloud[x][y] == 255) count++;
    }
}
System.out.println("Boundary pixel count = " + count);
    }
    // repaint safely
    private void repaintAll() {
        if (gui == null) return;
        SwingUtilities.invokeLater(() -> {
            gui.output.repaint();
            gui.output1.repaint();
            gui.output2.repaint();
            gui.output3.repaint();
            gui.output4.repaint();
            
        });
    }
    private void initFrontierAroundSeed(int a, int b, int radius) {
    neighboursT[a][b].clear();

    int sx = x0[a][b];
    int sy = y0[a][b];

    for (int dx = -radius; dx <= radius; dx++) {
        for (int dy = -radius; dy <= radius; dy++) {
            int x = sx + dx;
            int y = sy + dy;

            if (x >= 0 && y >= 0 && x < N && y < M) {
                if (boundary_cloud[x][y] == 255) {
                    neighboursT[a][b].add(new Point(x, y));
                }
            }
        }
    }
}


 
    public void clearIndex() {
        searchEngine.clear();
    }

    public void setSegmentationMode(ShapeExtractor.SegmentationMode mode) {
        searchEngine.setSegmentationMode(mode);
    }

    public ShapeExtractor.SegmentationMode getSegmentationMode() {
        return searchEngine.getSegmentationMode();
    }

    public void indexFolderAsync(File dir) {
        new Thread(() -> {
            try {
                int added = searchEngine.indexDirectory(dir);
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(null,
                                "Indexed " + added + " images.\nTotal in index: " + searchEngine.size()));
            } catch (Exception e) {
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(null, "Indexing failed: " + e.getMessage()));
            }
        }).start();
    }


    public void exportContoursCsvAsync(File outFile) {
        new Thread(() -> {
            try {
                if (img == null) throw new IllegalStateException("No image loaded.");
                List<ShapeExtractor.ExtractedShape> shapes = ShapeExtractor.extractShapesFromImage(
                        img,
                        searchEngine.getThreshold(),
                        searchEngine.getSamples(),
                        searchEngine.getMaxComponents(),
                        searchEngine.getMinComponentArea(),
                        searchEngine.getSegmentationMode()
                );
                if (shapes == null || shapes.isEmpty()) {
                    throw new IllegalStateException("Could not extract contour(s) from the current image.");
                }

                ContourExporter.writeContoursCsv(outFile, shapes);

                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(null,
                                "Exported " + shapes.size() + " contour(s) to:\n" + outFile.getAbsolutePath()));
            } catch (Exception e) {
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(null, "Export failed: " + e.getMessage()));
            }
        }).start();
    }

    public void searchCurrentImageAsync() {
        new Thread(() -> {
            try {
                if (img == null) throw new IllegalStateException("No image loaded.");
                if (searchEngine.size() == 0) throw new IllegalStateException("Index is empty. Use Search -> Index Folder first.");

                // Extract query shapes (multi-object) using AUTO segmentation
                List<ShapeExtractor.ExtractedShape> qShapes = ShapeExtractor.extractShapesFromImage(
                        img,
                        searchEngine.getThreshold(),
                        searchEngine.getSamples(),
                        searchEngine.getMaxComponents(),
                        searchEngine.getMinComponentArea(),
                        searchEngine.getSegmentationMode()
                );

                if (qShapes == null || qShapes.isEmpty()) {
                    throw new IllegalStateException("Could not extract descriptor(s) from query image.");
                }

                List<double[]> q = new ArrayList<>();
                for (ShapeExtractor.ExtractedShape s : qShapes) q.add(s.descriptor);

                System.out.println("[TAF] Query components: " + q.size());

                List<ShapeSearchEngine.Result> results = searchEngine.search(q, 9);

                // Print per-result component counts for demo/debug
                for (ShapeSearchEngine.Result r : results) {
                    int candC = (r.entry.descriptors == null) ? 0 : r.entry.descriptors.size();
                    System.out.println("[TAF] Query components = " + q.size()
                            + ", Candidate components = " + candC
                            + ", distance = " + String.format("%.4f", r.distance)
                            + ", file = " + r.entry.file.getName());
                }

                SwingUtilities.invokeLater(() -> {
                    SearchResultsWindow win = new SearchResultsWindow(results, q.size());
                    win.setVisible(true);
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(null, "Search failed: " + e.getMessage()));
            }
        }).start();
    }

    public int setARGB(int al, int red, int green, int blue) {
        return al << 24 | red << 16 | green << 8 | blue;
    }

    public int getRed(int argb) {
        return (argb >> 16) & 0xFF;
    }

    public int getGreen(int argb) {
        return (argb >> 8) & 0xFF;
    }

    public int getBlue(int argb) {
        return argb & 0xFF;
    }
}

package com.mycompany.project00;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Contour tracing utilities.
 *
 * Primary method: Moore-Neighbor boundary tracing on an object mask.
 * Fallback method: angle sorting of boundary pixels around centroid (works for simple shapes only).
 */
public class ContourTracer {

    private ContourTracer() {}

    // Clockwise neighbor order (E, SE, S, SW, W, NW, N, NE)
    private static final int[] DX = { 1, 1, 0,-1,-1,-1, 0, 1};
    private static final int[] DY = { 0, 1, 1, 1, 0,-1,-1,-1};

    /**
     * Moore boundary tracing:
     *  - input: object mask (foreground=true)
     *  - output: ordered contour points (closed: last equals first)
     */
    public static List<Point> traceMooreBoundary(boolean[][] mask) {
        int w = mask.length;
        int h = mask[0].length;

        // Find starting boundary pixel (top-most, then left-most)
        Point start = null;
        for (int y=0; y<h; y++) {
            for (int x=0; x<w; x++) {
                if (mask[x][y] && isBoundary(mask, x, y)) {
                    start = new Point(x,y);
                    break;
                }
            }
            if (start != null) break;
        }
        if (start == null) return null;

        // b0: pixel to the left of start
        Point b0 = new Point(start.x - 1, start.y);
        Point c = new Point(start);
        Point b = new Point(b0);

        List<Point> contour = new ArrayList<>();
        contour.add(new Point(c));

        // A safety guard for pathological cases
        int maxSteps = w * h * 4;

        // We stop when we are back at the start AND the next backtrack neighbor equals b0
        for (int steps=0; steps<maxSteps; steps++) {

            int startDir = neighborIndex(c, b); // direction index of b relative to c
            int dir = (startDir + 1) & 7;

            Point next = null;
            Point newB = null;

            for (int k=0; k<8; k++) {
                int d = (dir + k) & 7;
                int nx = c.x + DX[d];
                int ny = c.y + DY[d];

                if (nx>=0 && ny>=0 && nx<w && ny<h && mask[nx][ny]) {
                    next = new Point(nx, ny);
                    // new backtrack pixel is the neighbor before next in clockwise search
                    int bd = (d + 7) & 7;
                    newB = new Point(c.x + DX[bd], c.y + DY[bd]);
                    break;
                }
            }

            if (next == null) break;

            b = newB;
            c = next;

            contour.add(new Point(c));

            if (c.equals(start) && b.equals(b0)) {
                // close contour
                break;
            }
        }

        // Ensure closed (last equals first)
        if (!contour.get(contour.size()-1).equals(contour.get(0))) {
            contour.add(new Point(contour.get(0)));
        }

        // Remove obvious duplicates that can appear due to pixelation
        contour = removeConsecutiveDuplicates(contour);

        if (contour.size() < 10) return null;
        return contour;
    }

    private static boolean isBoundary(boolean[][] mask, int x, int y) {
        int w = mask.length;
        int h = mask[0].length;
        if (!mask[x][y]) return false;

        for (int i=0;i<8;i++) {
            int nx = x + DX[i];
            int ny = y + DY[i];
            if (nx<0||ny<0||nx>=w||ny>=h) return true;
            if (!mask[nx][ny]) return true;
        }
        return false;
    }

    private static int neighborIndex(Point c, Point b) {
        int dx = b.x - c.x;
        int dy = b.y - c.y;
        for (int i=0;i<8;i++) {
            if (DX[i]==dx && DY[i]==dy) return i;
        }
        // if b is outside image or not an immediate neighbor: choose W as default
        return 4;
    }

    private static List<Point> removeConsecutiveDuplicates(List<Point> pts) {
        if (pts.isEmpty()) return pts;
        List<Point> out = new ArrayList<>();
        Point prev = null;
        for (Point p : pts) {
            if (prev == null || !prev.equals(p)) out.add(p);
            prev = p;
        }
        return out;
    }

    /**
     * Fallback method: extract boundary points and sort by angle around centroid.
     * This does NOT guarantee a correct contour for complex shapes, but is better than failing.
     */
    public static List<Point> fallbackSortByAngle(boolean[][] boundary) {
        int w = boundary.length;
        int h = boundary[0].length;

        List<Point> pts = new ArrayList<>();
        double cx = 0, cy = 0;
        for (int x=0;x<w;x++) {
            for (int y=0;y<h;y++) {
                if (boundary[x][y]) {
                    pts.add(new Point(x,y));
                    cx += x;
                    cy += y;
                }
            }
        }
        if (pts.isEmpty()) return null;

        cx /= pts.size();
        cy /= pts.size();
        final double ccx = cx, ccy = cy;

        pts.sort((p1,p2) -> Double.compare(
            Math.atan2(p1.y - ccy, p1.x - ccx),
            Math.atan2(p2.y - ccy, p2.x - ccx)
        ));

        // close contour
        pts.add(new Point(pts.get(0)));
        return pts;
    }
}

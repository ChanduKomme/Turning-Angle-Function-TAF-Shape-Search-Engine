package com.mycompany.project00;

import java.awt.Point;
import java.util.List;

/**
 * Turning Angle Function (TAF) descriptor for a closed contour.
 *
 * Descriptor is computed as cumulative turning angle vs. normalized arc length,
 * resampled to a fixed number of samples.
 *
 * Similarity: cyclic-shift invariant L2 distance (also checks reversed orientation).
 */
public class TurningAngleDescriptor {

    private TurningAngleDescriptor() {}

    public static double[] compute(List<Point> contourClosed, int samples) {
        if (contourClosed == null || contourClosed.size() < 10) return null;
        int L = Math.max(16, samples);

        // ensure last equals first
        int n = contourClosed.size();
        Point first = contourClosed.get(0);
        Point last = contourClosed.get(n-1);
        if (!first.equals(last)) {
            // cannot modify original list safely -> just ignore (distance will still work)
        }

        // segments count = n-1 if closed, else n-1 anyway
        int m = n - 1;
        if (m < 5) return null;

        double[] segLen = new double[m];
        double[] dirAng = new double[m];

        for (int i=0;i<m;i++) {
            Point p = contourClosed.get(i);
            Point q = contourClosed.get(i+1);
            int dx = q.x - p.x;
            int dy = q.y - p.y;
            double len = Math.hypot(dx, dy);
            if (len == 0) len = 1e-9;
            segLen[i] = len;
            dirAng[i] = Math.atan2(dy, dx);
        }

        // cumulative arc length at vertices (size m+1)
        double[] s = new double[m+1];
        s[0] = 0;
        for (int i=0;i<m;i++) s[i+1] = s[i] + segLen[i];
        double total = s[m];
        if (total <= 1e-9) return null;

        // turning increments between successive directions (wrap to [-pi,pi])
        double[] theta = new double[m+1];
        theta[0] = 0;
        for (int i=1;i<=m;i++) {
            double a0 = dirAng[i-1];
            double a1 = dirAng[i % m]; // wrap (closed)
            double d = wrapToPi(a1 - a0);
            theta[i] = theta[i-1] + d;
        }

        // normalize total turning to +/- 2pi (pixelation can cause drift)
        double end = theta[m];
        if (Math.abs(end) < 1e-6) return null;
        double scale = (end >= 0 ? (2.0 * Math.PI) : (-2.0 * Math.PI)) / end;
        for (int i=0;i<=m;i++) theta[i] *= scale;

        // resample theta along arc length to fixed L
        double[] out = new double[L];
        for (int j=0;j<L;j++) {
            double target = (total * j) / (double)L;
            out[j] = interp(s, theta, target);
        }

        // remove DC offset (helps numeric stability for matching)
        double mean = 0;
        for (double v : out) mean += v;
        mean /= out.length;
        for (int i=0;i<out.length;i++) out[i] -= mean;

        return out;
    }

    private static double interp(double[] x, double[] y, double t) {
        int n = x.length;
        if (t <= x[0]) return y[0];
        if (t >= x[n-1]) return y[n-1];

        int lo = 0;
        int hi = n-1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (x[mid] <= t) lo = mid; else hi = mid;
        }

        double x0 = x[lo], x1 = x[hi];
        double y0 = y[lo], y1 = y[hi];
        double a = (t - x0) / (x1 - x0 + 1e-12);
        return y0 + a * (y1 - y0);
    }

    private static double wrapToPi(double a) {
        while (a <= -Math.PI) a += 2.0 * Math.PI;
        while (a > Math.PI) a -= 2.0 * Math.PI;
        return a;
    }

    /**
     * Cyclic shift invariant distance (L2) between two equal-length descriptors.
     * Also checks reversed orientation and returns the minimum.
     */
    public static double distanceCyclic(double[] a, double[] b) {
        if (a == null || b == null) return Double.POSITIVE_INFINITY;
        if (a.length != b.length) return Double.POSITIVE_INFINITY;

        double d1 = minShiftL2(a, b);
        double[] br = reverse(b);
        double d2 = minShiftL2(a, br);
        return Math.min(d1, d2);
    }

    private static double[] reverse(double[] x) {
        double[] r = new double[x.length];
        for (int i=0;i<x.length;i++) r[i] = x[x.length-1-i];
        return r;
    }

    private static double minShiftL2(double[] a, double[] b) {
        int L = a.length;
        double best = Double.POSITIVE_INFINITY;

        for (int shift=0; shift<L; shift++) {
            double s = 0;
            for (int i=0;i<L;i++) {
                double d = a[i] - b[(i + shift) % L];
                s += d*d;
            }
            double dist = Math.sqrt(s);
            if (dist < best) best = dist;
        }
        return best;
    }
}

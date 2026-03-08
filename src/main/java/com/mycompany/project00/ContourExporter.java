package com.mycompany.project00;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.awt.Point;
import java.util.List;

/**
 * Utility to export extracted contours (coordinates) to disk.
 *
 * CSV format:
 *   component_id,point_index,x,y
 *
 * Additionally writes a small header (comment lines starting with '#') with component metadata.
 */
public class ContourExporter {

    private ContourExporter() {}

    public static void writeContoursCsv(File outFile, List<ShapeExtractor.ExtractedShape> shapes) throws IOException {
        if (outFile == null) throw new IOException("Output file is null");
        if (shapes == null || shapes.isEmpty()) throw new IOException("No shapes/contours to export");

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(outFile))) {
            bw.write("# Turning Angle Function (TAF) contour export\n");
            bw.write("# format: component_id,point_index,x,y\n");

            for (ShapeExtractor.ExtractedShape s : shapes) {
                bw.write("# component " + s.componentIndex
                        + " areaPixels=" + s.areaPixels
                        + " bbox=(" + s.bbox.x + "," + s.bbox.y + "," + s.bbox.width + "," + s.bbox.height + ")"
                        + " centroid=(" + String.format("%.2f", s.centroidX) + "," + String.format("%.2f", s.centroidY) + ")\n");

                int idx = 0;
                for (Point p : s.contour) {
                    bw.write(s.componentIndex + "," + idx + "," + p.x + "," + p.y + "\n");
                    idx++;
                }
            }
        }
    }
}

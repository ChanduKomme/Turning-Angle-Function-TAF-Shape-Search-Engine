package com.mycompany.project00;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Simple Swing window to show search results as thumbnails.
 */
public class SearchResultsWindow extends JFrame {

    public SearchResultsWindow(List<ShapeSearchEngine.Result> results, int queryComponents) {
        super("TAF Search Results");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(10,10));
        root.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        JLabel header = new JLabel("Query components: " + queryComponents);
        header.setFont(header.getFont().deriveFont(Font.BOLD));
        root.add(header, BorderLayout.NORTH);

        JPanel grid = new JPanel();
        int cols = 3;
        int rows = (int)Math.ceil(results.size() / (double)cols);
        grid.setLayout(new GridLayout(Math.max(1, rows), cols, 10, 10));

        for (ShapeSearchEngine.Result r : results) {
            grid.add(makeCard(r, queryComponents));
        }

        JScrollPane scroll = new JScrollPane(grid);
        root.add(scroll, BorderLayout.CENTER);

        setContentPane(root);
        setSize(900, 600);
        setLocationRelativeTo(null);
    }

    private JPanel makeCard(ShapeSearchEngine.Result r, int queryComponents) {
        JPanel card = new JPanel(new BorderLayout(5,5));
        card.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));

        JLabel imgLabel = new JLabel("Loading...", SwingConstants.CENTER);
        imgLabel.setPreferredSize(new Dimension(260, 200));

        JLabel text = new JLabel("<html><b>" + escape(r.entry.file.getName())
                + "</b><br/>distance = " + String.format("%.4f", r.distance)
                + "<br/>query components = " + queryComponents
                + ", candidate components = " + (r.entry.descriptors == null ? 0 : r.entry.descriptors.size())
                + "<br/>" + escape(shortPath(r.entry.file)) + "</html>");

        card.add(imgLabel, BorderLayout.CENTER);
        card.add(text, BorderLayout.SOUTH);

        // load image (best effort)
        try {
            BufferedImage img = ImageIO.read(r.entry.file);
            if (img != null) {
                Image scaled = img.getScaledInstance(260, -1, Image.SCALE_SMOOTH);
                imgLabel.setText("");
                imgLabel.setIcon(new ImageIcon(scaled));
            } else {
                imgLabel.setText("Could not read image");
            }
        } catch (Exception e) {
            imgLabel.setText("Could not load image");
        }

        return card;
    }

    private static String shortPath(File f) {
        String s = f.getAbsolutePath();
        // keep last 60 chars
        if (s.length() > 60) return "..." + s.substring(s.length() - 60);
        return s;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }
}

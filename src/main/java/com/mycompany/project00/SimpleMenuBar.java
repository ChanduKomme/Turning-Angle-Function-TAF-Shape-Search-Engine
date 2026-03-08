package com.mycompany.project00;
/**
 *
 * @author KC
 */
import javax.swing.*;
import java.awt.*;
import javax.imageio.ImageIO; //ImageIO
import java.awt.image.BufferedImage;
import java.io.File;

public class SimpleMenuBar extends JMenuBar {

    private final JMenu fileMenu;
    private final JMenuItem openMenuItem;
    private final JMenuItem exportContoursItem;

    private final JMenu searchMenu;
    private final JMenuItem indexFolderItem;
    private final JMenuItem searchSimilarItem;
    private final JMenuItem clearIndexItem;
    private final JMenu segModeMenu;
    private final JRadioButtonMenuItem segAuto;
    private final JRadioButtonMenuItem segBorder;
    private final JRadioButtonMenuItem segOtsu;
    private final JRadioButtonMenuItem segLegacy;

    private final GUI g;

    public SimpleMenuBar(GUI g) {
        this.g = g;

        // --- File menu
        fileMenu = new JMenu("File");
        openMenuItem = new JMenuItem("Open Image...");
        exportContoursItem = new JMenuItem("Export Contours (CSV)...");
        add(fileMenu);
        fileMenu.add(openMenuItem);
        fileMenu.add(exportContoursItem);

        openMenuItem.addActionListener(evt -> openImageAction());
        exportContoursItem.addActionListener(evt -> exportContoursAction());

        // --- Search menu
        searchMenu = new JMenu("Search");
        indexFolderItem = new JMenuItem("Index Folder...");
        searchSimilarItem = new JMenuItem("Search Similar (Turning Angle)...");
        clearIndexItem = new JMenuItem("Clear Index");

        segModeMenu = new JMenu("Segmentation Mode");
        segAuto = new JRadioButtonMenuItem("AUTO (recommended)");
        segBorder = new JRadioButtonMenuItem("Border Color Distance");
        segOtsu = new JRadioButtonMenuItem("Otsu (Luminance)");
        segLegacy = new JRadioButtonMenuItem("Legacy White Background");
        ButtonGroup segGroup = new ButtonGroup();
        segGroup.add(segAuto);
        segGroup.add(segBorder);
        segGroup.add(segOtsu);
        segGroup.add(segLegacy);
        segAuto.setSelected(true);
        segModeMenu.add(segAuto);
        segModeMenu.add(segBorder);
        segModeMenu.add(segOtsu);
        segModeMenu.add(segLegacy);

        add(searchMenu);
        searchMenu.add(indexFolderItem);
        searchMenu.add(searchSimilarItem);
        searchMenu.add(segModeMenu);
        searchMenu.addSeparator();
        searchMenu.add(clearIndexItem);

        indexFolderItem.addActionListener(evt -> indexFolderAction());
        searchSimilarItem.addActionListener(evt -> searchSimilarAction());
        clearIndexItem.addActionListener(evt -> clearIndexAction());

        segAuto.addActionListener(evt -> setSegMode(ShapeExtractor.SegmentationMode.AUTO));
        segBorder.addActionListener(evt -> setSegMode(ShapeExtractor.SegmentationMode.BORDER_COLOR_DISTANCE));
        segOtsu.addActionListener(evt -> setSegMode(ShapeExtractor.SegmentationMode.OTSU_LUMA));
        segLegacy.addActionListener(evt -> setSegMode(ShapeExtractor.SegmentationMode.LEGACY_WHITE_BG));
    }


    private void setSegMode(ShapeExtractor.SegmentationMode mode) {
        g.img_pro.setSegmentationMode(mode);
        JOptionPane.showMessageDialog(g, "Segmentation mode set to: " + mode);
    }

    private void openImageAction() {
        FileDialog fd = new FileDialog(g);
        fd.setVisible(true);

        g.setTitle("Dir: " + fd.getDirectory() + " File: " + fd.getFile());
        if (fd.getFile() == null) return;

        try {
            g.img_pro.img = ImageIO.read(new File(fd.getDirectory() + fd.getFile())); // load the image
        } catch (Exception e) {
            JOptionPane.showMessageDialog(g, "Failed to load image: " + e.getMessage());
            return;
        }

        // reset output buffers
        g.img_pro.img_out  = new BufferedImage(g.img_pro.img.getWidth(), g.img_pro.img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        g.img_pro.img_out1 = new BufferedImage(g.img_pro.img.getWidth(), g.img_pro.img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        g.img_pro.img_out2 = new BufferedImage(g.img_pro.img.getWidth(), g.img_pro.img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        g.img_pro.img_out3 = new BufferedImage(g.img_pro.img.getWidth(), g.img_pro.img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        g.img_pro.img_out4 = new BufferedImage(g.img_pro.img.getWidth(), g.img_pro.img.getHeight(), BufferedImage.TYPE_INT_ARGB);

        g.input.setIcon(new ImageIcon(g.img_pro.img));
        g.output.setIcon(new ImageIcon(g.img_pro.img_out));
        g.output1.setIcon(new ImageIcon(g.img_pro.img_out1));
        g.output2.setIcon(new ImageIcon(g.img_pro.img_out2));
        g.output3.setIcon(new ImageIcon(g.img_pro.img_out3));
        g.output4.setIcon(new ImageIcon(g.img_pro.img_out4));

        g.pack();
    }


    private void exportContoursAction() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export contour coordinates as CSV");
        chooser.setSelectedFile(new File("contours.csv"));

        int res = chooser.showSaveDialog(g);
        if (res != JFileChooser.APPROVE_OPTION) return;

        File out = chooser.getSelectedFile();
        // ensure .csv extension
        if (!out.getName().toLowerCase().endsWith(".csv")) {
            out = new File(out.getParentFile(), out.getName() + ".csv");
        }
        g.img_pro.exportContoursCsvAsync(out);
    }

    private void indexFolderAction() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select dataset folder to index");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);

        int res = chooser.showOpenDialog(g);
        if (res != JFileChooser.APPROVE_OPTION) return;

        File dir = chooser.getSelectedFile();
        g.img_pro.indexFolderAsync(dir);
    }

    private void searchSimilarAction() {
        g.img_pro.searchCurrentImageAsync();
    }

    private void clearIndexAction() {
        g.img_pro.clearIndex();
        JOptionPane.showMessageDialog(g, "Index cleared.");
    }
}

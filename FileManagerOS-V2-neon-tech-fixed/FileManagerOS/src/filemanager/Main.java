package filemanager;

import filemanager.ui.MainFrame;
import filemanager.ui.Theme;
import javax.swing.*;

/**
 * Entry point for the Operating Systems file manager project.
 *
 * The app is a Java Swing GUI that reads and modifies the real filesystem
 * of the host operating system (via java.io / java.nio.file and
 * java.awt.Desktop), demonstrating direct OS interaction: directory
 * listing, file/folder creation, deletion, rename, copy, move, and
 * launching files with the OS's default associated application.
 */
public class Main {
    public static void main(String[] args) {
        Theme.apply();
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}

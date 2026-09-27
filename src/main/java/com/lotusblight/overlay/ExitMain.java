package com.lotusblight.overlay;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/**
 * The exit's first step: an error dialog over the desktop, drawn by us in the FakeWindows look - not a
 * real OS window, and it touches nothing. Only the dialog is painted; everywhere else the overlay is
 * fully transparent, so clicks there go straight through to whatever is underneath. OK, the close box,
 * Enter or Esc close it, and the process ends.
 *
 * Usage: java -cp lotusblight.jar com.lotusblight.overlay.ExitMain [--jar lotusblight-VERSION.jar]
 */
public final class ExitMain {
    private static final String TITLE = "Ошибка";
    private static final String[] BUTTONS = {"OK"};
    /** Dialog height as a share of the screen height; fonts follow it. */
    private static final double HEIGHT_SHARE = 0.15;
    /** FakeWindows wraps the body at this share of the dialog width. */
    private static final double TEXT_SHARE = 0.72;
    /** Measured and drawn with the same face, so the width worked out is the width painted. */
    private static final Font BASE_FONT = new Font(Font.DIALOG, Font.PLAIN, 12);

    private ExitMain() {}

    public static void main(String[] args) {
        String jar = "lotusblight.jar";
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--jar")) jar = args[i + 1];
        }
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("No screen to draw on.");
            System.exit(2);
        }
        String body = "Не найден файл Glitcher_.jar\n"
                + "По пути: .minecraft\\mods\\" + jar + "\\character\\Glitcher_Architect.jar";
        SwingUtilities.invokeLater(() -> show(body));
    }

    private static void show(String body) {
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        Rectangle dialog = size(body, screen);

        JFrame frame = new JFrame(TITLE);
        frame.setUndecorated(true);
        frame.setBounds(screen);
        frame.setBackground(new Color(0, 0, 0, 0));
        frame.setAlwaysOnTop(true);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        FakeWindows.Hits[] hits = new FakeWindows.Hits[1];
        JComponent canvas = new JComponent() {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics;
                g.setComposite(AlphaComposite.Clear);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setComposite(AlphaComposite.SrcOver);
                g.setFont(BASE_FONT);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                hits[0] = FakeWindows.drawDialog(g, dialog.x, dialog.y, dialog.width, dialog.height, TITLE, body, BUTTONS);
            }
        };
        MouseAdapter mouse = new MouseAdapter() {
            private Point grab;

            @Override
            public void mousePressed(MouseEvent e) {
                FakeWindows.Hits h = hits[0];
                if (h == null) return;
                if (h.close().contains(e.getPoint()) || h.buttons()[0].contains(e.getPoint())) {
                    System.exit(0);
                }
                // The title bar drags the dialog, like a real one.
                Rectangle bar = new Rectangle(dialog.x, dialog.y, dialog.width, (int) (dialog.height * 0.22));
                grab = bar.contains(e.getPoint()) ? new Point(e.getX() - dialog.x, e.getY() - dialog.y) : null;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (grab == null) return;
                dialog.setLocation(e.getX() - grab.x, e.getY() - grab.y);
                canvas.repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                grab = null;
            }
        };
        canvas.addMouseListener(mouse);
        canvas.addMouseMotionListener(mouse);
        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE || e.getKeyCode() == KeyEvent.VK_ENTER) System.exit(0);
            }
        });
        frame.setContentPane(canvas);
        frame.setVisible(true);
        frame.toFront();
        frame.requestFocus();
        Toolkit.getDefaultToolkit().beep();
    }

    /** A dialog wide enough that the longest line never wraps, centred, shrunk to fit narrow screens. */
    private static Rectangle size(String body, Rectangle screen) {
        Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        g.setFont(BASE_FONT);
        int h = (int) (screen.height * HEIGHT_SHARE);
        int w = h;
        for (int attempt = 0; attempt < 8; attempt++) {
            g.setFont(g.getFont().deriveFont(Font.PLAIN, h * 0.11f));
            int longest = 0;
            for (String line : body.split("\n")) longest = Math.max(longest, g.getFontMetrics().stringWidth(line));
            w = Math.max(h * 3, (int) Math.ceil(longest / TEXT_SHARE) + 2);
            if (w <= screen.width * 0.9) break;
            h = (int) (h * screen.width * 0.9 / w);
        }
        g.dispose();
        // In the overlay's own coordinates - the canvas starts at 0,0 whichever monitor it is on.
        return new Rectangle((screen.width - w) / 2, (screen.height - h) / 2, w, h);
    }
}

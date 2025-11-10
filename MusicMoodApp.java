import javax.swing.*;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import javax.sound.sampled.*;

import com.formdev.flatlaf.FlatDarkLaf;
import java.awt.geom.*;
import java.awt.GradientPaint;
import java.awt.RadialGradientPaint;

public class MusicMoodApp extends JFrame {

    private CardLayout cardLayout;
    private JPanel mainPanel, moodPanel, musicPanel;
    private Clip clip;
    private FloatControl volumeControl;
    private boolean isPaused = false;
    private long pausedPosition = 0;
    private String selectedMood;
    private JList<String> songList;
    private DefaultListModel<String> listModel;
    private File[] currentFiles;
    private int currentIndex = -1;

    private ControlButton playBtn, pauseBtn, stopBtn, nextBtn, prevBtn, backBtn;
    private JLabel nowPlayingLabel;
    private JSlider volumeSlider;

    private boolean isPlaying = false;

    private Clip uiClickClip;
    private Font customFont;

    private final String MUSIC_PATH = "music/";
    private final String SOUND_PATH = "sounds/click.wav";

    public static void main(String[] args) {
        FlatDarkLaf.setup();
        SwingUtilities.invokeLater(() -> {
            MusicMoodApp app = new MusicMoodApp();
            app.startFadeIn();
        });
    }

    public MusicMoodApp() {
        setTitle("Music Mood App");
        setSize(1000, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        initMoodPanel();
        initMusicPanel();

        mainPanel.add(moodPanel, "mood");
        mainPanel.add(musicPanel, "music");
        add(mainPanel);
        setVisible(true);

        loadClickSound();
        loadCustomFontAndApply();
    }

    // ===== window fade-in =====
    private void startFadeIn() {
        try {
            setOpacity(0f);
        } catch (Exception e) {
            return;
        }

        int durationMs = 1500;
        int steps = 30;
        int delay = durationMs / steps;

        Timer timer = new Timer(delay, null);
        timer.addActionListener(e -> {
            float current = getOpacity();
            float next = current + (1.0f / steps);
            if (next >= 1f) {
                setOpacity(1f);
                timer.stop();
            } else {
                setOpacity(next);
            }
        });
        timer.start();
    }

    // ===== font load =====
    private void loadCustomFontAndApply() {
        try {
            File fontFile = new File("fonts/Headlines-BoldItalic.otf");
            if (fontFile.exists()) {
                customFont = Font.createFont(Font.TRUETYPE_FONT, fontFile).deriveFont(Font.PLAIN, 22f);
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(customFont);
                SwingUtilities.invokeLater(() -> applyFontRecursively(this.getContentPane(), customFont));
            } else {
                System.out.println("Font file not found: " + fontFile.getPath());
            }
        } catch (Exception ex) {
            System.out.println("Failed to apply font: " + ex.getMessage());
        }
    }

    private void applyFontRecursively(Component component, Font font) {
        component.setFont(font);
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                applyFontRecursively(child, font);
            }
        }
    }

    // ===== click sound =====
    private void loadClickSound() {
        try {
            File soundFile = new File(SOUND_PATH);
            if (!soundFile.exists()) {
                System.out.println("click.wav not found at " + SOUND_PATH);
                return;
            }
            AudioInputStream ais = AudioSystem.getAudioInputStream(soundFile);
            uiClickClip = AudioSystem.getClip();
            uiClickClip.open(ais);
        } catch (Exception ex) {
            System.out.println("Failed to load click sound: " + ex.getMessage());
        }
    }

    private void playClickSound() {
        if (uiClickClip == null) return;
        if (uiClickClip.isRunning()) uiClickClip.stop();
        uiClickClip.setFramePosition(0);
        uiClickClip.start();
    }

    // ===== MOOD PANEL =====
    private void initMoodPanel() {
        moodPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                GradientPaint baseGradient = new GradientPaint(
                        0, 0, new Color(80, 80, 80),
                        w, h, new Color(10, 10, 10)
                );
                g2d.setPaint(baseGradient);
                g2d.fillRect(0, 0, w, h);

                RadialGradientPaint glow = new RadialGradientPaint(
                        new Point(w / 2, h / 3),
                        w / 1.2f,
                        new float[]{0f, 1f},
                        new Color[]{new Color(255, 255, 255, 35), new Color(0, 0, 0, 0)}
                );
                g2d.setPaint(glow);
                g2d.fillRect(0, 0, w, h);

                g2d.setColor(new Color(255, 255, 255, 12));
                g2d.fillRect(0, 0, w, h);

                g2d.dispose();
            }
        };
        moodPanel.setLayout(new BorderLayout());

        JLabel title = new JLabel("Music Mood App", SwingConstants.CENTER);
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 36));

        JLabel subtitle = new JLabel("Choose your mood", SwingConstants.CENTER);
        subtitle.setForeground(new Color(220, 220, 220));
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 20));

        JPanel top = new JPanel(new GridLayout(2, 1));
        top.setOpaque(false);
        top.setBorder(BorderFactory.createEmptyBorder(25, 0, 20, 0));
        top.add(title);
        top.add(subtitle);

        JPanel grid = new JPanel(new GridLayout(2, 3, 25, 25));
        grid.setOpaque(false);
        grid.setBorder(BorderFactory.createEmptyBorder(40, 80, 40, 80));

        grid.add(createMoodButton("Happy", new Color(255, 230, 90)));
        grid.add(createMoodButton("Sad", new Color(90, 120, 255)));
        grid.add(createMoodButton("Chill", new Color(90, 240, 210)));
        grid.add(createMoodButton("Energetic", new Color(255, 120, 90)));
        grid.add(createMoodButton("Love", new Color(255, 80, 160)));

        moodPanel.add(top, BorderLayout.NORTH);
        moodPanel.add(grid, BorderLayout.CENTER);
    }

    // ===== MUSIC PANEL =====
    private void initMusicPanel() {
        musicPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                GradientPaint baseGradient = new GradientPaint(
                        0, 0, new Color(80, 80, 80),
                        w, h, new Color(10, 10, 10)
                );
                g2d.setPaint(baseGradient);
                g2d.fillRect(0, 0, w, h);

                RadialGradientPaint glow = new RadialGradientPaint(
                        new Point(w / 2, h / 3),
                        w / 1.2f,
                        new float[]{0f, 1f},
                        new Color[]{new Color(255, 255, 255, 35), new Color(0, 0, 0, 0)}
                );
                g2d.setPaint(glow);
                g2d.fillRect(0, 0, w, h);

                g2d.setColor(new Color(255, 255, 255, 12));
                g2d.fillRect(0, 0, w, h);

                g2d.dispose();
            }
        };

        JLabel label = new JLabel("Select a song", SwingConstants.CENTER);
        label.setForeground(Color.WHITE);
        label.setFont(new Font("SansSerif", Font.BOLD, 26));
        label.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

        listModel = new DefaultListModel<>();
        songList = new JList<>(listModel);
        songList.setBackground(new Color(20, 20, 25));
        songList.setForeground(Color.WHITE);
        songList.setSelectionBackground(new Color(0x1DB954));
        songList.setSelectionForeground(Color.BLACK);
        songList.setFont(new Font("SansSerif", Font.PLAIN, 18));

        JScrollPane scrollPane = new JScrollPane(songList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(10, 40, 10, 40));

        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setOpaque(false);
        bottomBar.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));

        JPanel controls = new JPanel();
        controls.setOpaque(false);

        prevBtn = createControlButton(ControlButton.Type.PREV);
        playBtn = createControlButton(ControlButton.Type.PLAY_PAUSE);
        pauseBtn = null; // playBtn will toggle its icon
        stopBtn = createControlButton(ControlButton.Type.STOP);
        nextBtn = createControlButton(ControlButton.Type.NEXT);
        backBtn = createControlButton(ControlButton.Type.BACK);

        controls.add(prevBtn);
        controls.add(playBtn);
        controls.add(stopBtn);
        controls.add(nextBtn);
        controls.add(backBtn);

        nowPlayingLabel = new JLabel("Now playing: -");
        nowPlayingLabel.setForeground(Color.WHITE);
        nowPlayingLabel.setFont(new Font("SansSerif", Font.PLAIN, 16));

        JPanel centerInfo = new JPanel(new FlowLayout(FlowLayout.LEFT));
        centerInfo.setOpaque(false);
        centerInfo.add(nowPlayingLabel);

        JLabel volLabel = new JLabel("Vol");
        volLabel.setForeground(Color.WHITE);
        volLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));

        volumeSlider = new JSlider(0, 100, 80);
        volumeSlider.setPreferredSize(new Dimension(220, 22));
        volumeSlider.setOpaque(false);
        final boolean[] hovering = {false};

        volumeSlider.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hovering[0] = true; volumeSlider.repaint(); }
            @Override public void mouseExited(MouseEvent e) { hovering[0] = false; volumeSlider.repaint(); }
        });

        volumeSlider.setUI(new javax.swing.plaf.basic.BasicSliderUI(volumeSlider) {
            @Override
            public void paintTrack(Graphics g) {
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int cy = trackRect.y + (trackRect.height / 2) - 3;
                int cw = trackRect.width;
                int ch = 8;
                g2d.setColor(new Color(40, 40, 40));
                g2d.fillRoundRect(trackRect.x, cy, cw, ch, ch, ch);
                Color fill = hovering[0] ? new Color(40, 255, 120) : new Color(30, 215, 96);
                int filled = (int) (cw * (slider.getValue() / 100.0));
                g2d.setColor(fill);
                g2d.fillRoundRect(trackRect.x, cy, filled, ch, ch, ch);
                if (hovering[0]) {
                    g2d.setColor(new Color(40, 255, 120, 80));
                    g2d.setStroke(new BasicStroke(6f));
                    g2d.drawRoundRect(trackRect.x, cy, filled, ch, ch, ch);
                }
            }

            @Override
            public void paintThumb(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int size = 16;
                int x = thumbRect.x + (thumbRect.width - size) / 2;
                int y = thumbRect.y + (thumbRect.height - size) / 2;
                Color thumbColor = hovering[0] ? new Color(40, 255, 120) : new Color(30, 215, 96);
                g2d.setColor(thumbColor);
                g2d.fillOval(x, y, size, size);
                if (hovering[0]) {
                    g2d.setColor(new Color(40, 255, 120, 80));
                    g2d.setStroke(new BasicStroke(2f));
                    g2d.drawOval(x - 2, y - 2, size + 4, size + 4);
                }
                g2d.dispose();
            }
        });

        volumeSlider.addChangeListener((ChangeEvent e) -> {
            updateVolume();
            volumeSlider.repaint();
        });

        JPanel rightPanel = new JPanel();
        rightPanel.setOpaque(false);
        rightPanel.add(volLabel);
        rightPanel.add(volumeSlider);

        bottomBar.add(controls, BorderLayout.WEST);
        bottomBar.add(centerInfo, BorderLayout.CENTER);
        bottomBar.add(rightPanel, BorderLayout.EAST);

        musicPanel.add(label, BorderLayout.NORTH);
        musicPanel.add(scrollPane, BorderLayout.CENTER);
        musicPanel.add(bottomBar, BorderLayout.SOUTH);

        prevBtn.addActionListener(e -> {
            playClickSound();
            playPrevious();
        });
        playBtn.addActionListener(e -> {
            playClickSound();
            if (!isPlaying) {
                playSelectedFromList();
            } else {
                pauseOrResume();
            }
        });
        stopBtn.addActionListener(e -> {
            playClickSound();
            stopMusic();
        });
        nextBtn.addActionListener(e -> {
            playClickSound();
            playNext();
        });
        backBtn.addActionListener(e -> {
            playClickSound();
            stopMusic();
            cardLayout.show(mainPanel, "mood");
        });
    }

    // ===== custom mood button class =====
    private MoodButton createMoodButton(String text, Color baseColor) {
        MoodButton btn = new MoodButton(text, baseColor);
        btn.addActionListener(e -> {
            playClickSound();
            openMood(text.toLowerCase());
        });
        return btn;
    }

    private class MoodButton extends JButton {
        private float scale = 1.0f;
        private float glowAlpha = 0.0f;
        private Timer glowTimer;
        private final Color baseColor;

        MoodButton(String text, Color baseColor) {
            super(text);
            this.baseColor = baseColor;
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setForeground(Color.BLACK);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    animateGlow(true);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    animateGlow(false);
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    animateClick(true);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    animateClick(false);
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            int newW = (int) (w * scale);
            int newH = (int) (h * scale);
            int x = (w - newW) / 2;
            int y = (h - newH) / 2;
            g2d.translate(x, y);
            g2d.scale(scale, scale);

            g2d.setColor(new Color(0, 0, 0, 90));
            g2d.fillRoundRect(4, 6, w - 8, h - 8, 40, 40);

            GradientPaint gradient = new GradientPaint(
                    0, 0, baseColor.brighter(),
                    0, h, baseColor.darker()
            );
            g2d.setPaint(gradient);
            g2d.fillRoundRect(0, 0, w - 6, h - 6, 40, 40);

            GradientPaint highlight = new GradientPaint(
                    0, 0, new Color(255, 255, 255, 75),
                    0, h / 2, new Color(255, 255, 255, 0)
            );
            g2d.setPaint(highlight);
            g2d.fillRoundRect(0, 0, w - 6, h / 2, 40, 40);

            if (glowAlpha > 0f) {
                g2d.setColor(new Color(
                        baseColor.getRed(),
                        baseColor.getGreen(),
                        baseColor.getBlue(),
                        (int) (120 * glowAlpha)
                ));
                g2d.setStroke(new BasicStroke(5f));
                g2d.drawRoundRect(2, 2, w - 10, h - 10, 40, 40);
            }

            g2d.setColor(new Color(255, 255, 255, 50));
            g2d.setStroke(new BasicStroke(2f));
            g2d.drawRoundRect(0, 0, w - 6, h - 6, 40, 40);

            Font f = customFont != null ? customFont.deriveFont(Font.BOLD, 22f) : getFont().deriveFont(Font.BOLD, 22f);
            g2d.setFont(f);
            FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(getText());
            int textHeight = fm.getAscent();
            g2d.setColor(Color.BLACK);
            g2d.drawString(getText(), (w - textWidth) / 2, (h + textHeight / 2) / 2);

            g2d.dispose();
        }

        void animateClick(boolean pressed) {
            float target = pressed ? 0.93f : 1.0f;
            new Thread(() -> {
                try {
                    for (int i = 0; i < 5; i++) {
                        scale += (target - scale) * 0.5f;
                        repaint();
                        Thread.sleep(15);
                    }
                    scale = target;
                    repaint();
                } catch (InterruptedException ignored) {}
            }).start();
        }

        void animateGlow(boolean hoverIn) {
            if (glowTimer != null && glowTimer.isRunning()) glowTimer.stop();
            glowTimer = new Timer(15, null);
            glowTimer.addActionListener(e -> {
                float target = hoverIn ? 1.0f : 0.0f;
                glowAlpha += (target - glowAlpha) * 0.18f;
                if (Math.abs(target - glowAlpha) < 0.02f) {
                    glowAlpha = target;
                    glowTimer.stop();
                }
                repaint();
            });
            glowTimer.start();
        }
    }

    // ===== custom control button class =====
    private ControlButton createControlButton(ControlButton.Type type) {
        ControlButton btn = new ControlButton(type);
        btn.addActionListener(e -> {
            // actual actions are set in initMusicPanel
        });
        return btn;
    }

    private class ControlButton extends JButton {
        enum Type { PREV, PLAY_PAUSE, NEXT, STOP, BACK }

        private final Type type;
        private float scale = 1.0f;
        private float glowAlpha = 0.0f;
        private Timer glowTimer;

        ControlButton(Type type) {
            this.type = type;
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    animateGlow(true);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    animateGlow(false);
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    animateClick(true);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    animateClick(false);
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            int newW = (int) (w * scale);
            int newH = (int) (h * scale);
            int x = (w - newW) / 2;
            int y = (h - newH) / 2;
            g2d.translate(x, y);
            g2d.scale(scale, scale);

            int arc = 26;

            if (glowAlpha > 0f) {
                g2d.setColor(new Color(30, 215, 96, (int) (130 * glowAlpha)));
                g2d.fillRoundRect(2, 3, w - 4, h - 4, arc + 8, arc + 8);
            }

            g2d.setColor(new Color(0, 0, 0, 120));
            g2d.fillRoundRect(3, 5, w - 6, h - 5, arc, arc);

            GradientPaint gp = new GradientPaint(
                    0, 0, new Color(40, 40, 40),
                    0, h, new Color(15, 15, 15)
            );
            g2d.setPaint(gp);
            g2d.fillRoundRect(0, 0, w - 4, h - 6, arc, arc);

            g2d.setColor(new Color(255, 255, 255, 40));
            g2d.setStroke(new BasicStroke(1.5f));
            g2d.drawRoundRect(0, 0, w - 4, h - 6, arc, arc);

            g2d.setColor(new Color(230, 230, 230));

            int cx = (w - 4) / 2;
            int cy = (h - 6) / 2;
            int size = Math.min(w, h) / 3 + 4;

            switch (type) {
                case PLAY_PAUSE:
                    if (!isPlaying) {
                        Polygon triangle = new Polygon();
                        triangle.addPoint(cx - size / 2, cy - size);
                        triangle.addPoint(cx - size / 2, cy + size);
                        triangle.addPoint(cx + size, cy);
                        g2d.fillPolygon(triangle);
                    } else {
                        int barWidth = size / 3;
                        int barHeight = size * 2;
                        int gap = barWidth / 2;
                        g2d.fillRoundRect(cx - gap - barWidth, cy - barHeight / 2, barWidth, barHeight, 6, 6);
                        g2d.fillRoundRect(cx + gap, cy - barHeight / 2, barWidth, barHeight, 6, 6);
                    }
                    break;
                case PREV:
                    int tSize = size;
                    Polygon leftTri = new Polygon();
                    leftTri.addPoint(cx + tSize / 2, cy - tSize);
                    leftTri.addPoint(cx + tSize / 2, cy + tSize);
                    leftTri.addPoint(cx - tSize, cy);
                    g2d.fillPolygon(leftTri);
                    g2d.fillRect(cx + tSize / 2 + 2, cy - tSize, 4, tSize * 2);
                    break;
                case NEXT:
                    int t2 = size;
                    Polygon rightTri = new Polygon();
                    rightTri.addPoint(cx - t2 / 2, cy - t2);
                    rightTri.addPoint(cx - t2 / 2, cy + t2);
                    rightTri.addPoint(cx + t2, cy);
                    g2d.fillPolygon(rightTri);
                    g2d.fillRect(cx - t2 / 2 - 6, cy - t2, 4, t2 * 2);
                    break;
                case STOP:
                    int sq = size;
                    g2d.fillRoundRect(cx - sq / 2, cy - sq / 2, sq, sq, 6, 6);
                    break;
                case BACK:
                    int bSize = size;
                    Polygon arrow = new Polygon();
                    arrow.addPoint(cx - bSize, cy);
                    arrow.addPoint(cx, cy - bSize);
                    arrow.addPoint(cx, cy + bSize);
                    g2d.fillPolygon(arrow);
                    break;
            }

            g2d.dispose();
        }

        void animateClick(boolean pressed) {
            float target = pressed ? 0.9f : 1.0f;
            new Thread(() -> {
                try {
                    for (int i = 0; i < 5; i++) {
                        scale += (target - scale) * 0.5f;
                        repaint();
                        Thread.sleep(15);
                    }
                    scale = target;
                    repaint();
                } catch (InterruptedException ignored) {}
            }).start();
        }

        void animateGlow(boolean hoverIn) {
            if (glowTimer != null && glowTimer.isRunning()) glowTimer.stop();
            glowTimer = new Timer(15, null);
            glowTimer.addActionListener(e -> {
                float target = hoverIn ? 1.0f : 0.0f;
                glowAlpha += (target - glowAlpha) * 0.2f;
                if (Math.abs(target - glowAlpha) < 0.02f) {
                    glowAlpha = target;
                    glowTimer.stop();
                }
                repaint();
            });
            glowTimer.start();
        }
    }

    // ===== logic =====
    private void openMood(String mood) {
        selectedMood = mood;
        listModel.clear();
        currentFiles = null;
        currentIndex = -1;
        nowPlayingLabel.setText("Now playing: -");
        isPaused = false;
        pausedPosition = 0;
        isPlaying = false;
        if (playBtn != null) playBtn.repaint();

        File folder = new File(MUSIC_PATH + mood);
        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".wav"));
            if (files != null && files.length > 0) {
                currentFiles = files;
                for (File f : files) listModel.addElement(f.getName());
            } else {
                JOptionPane.showMessageDialog(this, "No songs found for " + mood);
            }
        } else {
            JOptionPane.showMessageDialog(this, "Folder missing: " + folder.getPath());
        }

        cardLayout.show(mainPanel, "music");
    }

    private void playSelectedFromList() {
        if (currentFiles == null || currentFiles.length == 0) {
            JOptionPane.showMessageDialog(this, "No songs loaded for this mood.");
            return;
        }
        int index = songList.getSelectedIndex();
        if (index < 0) {
            JOptionPane.showMessageDialog(this, "Please select a song first.");
            return;
        }
        currentIndex = index;
        isPaused = false;
        pausedPosition = 0;
        playCurrentIndex();
    }

    private void playCurrentIndex() {
        if (currentFiles == null || currentIndex < 0 || currentIndex >= currentFiles.length) return;
        stopMusic();
        File audioFile = currentFiles[currentIndex];
        try {
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile);
            clip = AudioSystem.getClip();
            clip.open(audioStream);
            setupVolumeControl();
            clip.start();
            songList.setSelectedIndex(currentIndex);
            nowPlayingLabel.setText("Now playing: " + audioFile.getName());
            isPaused = false;
            pausedPosition = 0;
            isPlaying = true;
            if (playBtn != null) playBtn.repaint();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Cannot play: " + ex.getMessage());
        }
    }

    private void playPrevious() {
        if (currentFiles == null || currentFiles.length == 0) return;
        currentIndex = (currentIndex <= 0) ? currentFiles.length - 1 : currentIndex - 1;
        isPaused = false;
        pausedPosition = 0;
        playCurrentIndex();
    }

    private void playNext() {
        if (currentFiles == null || currentFiles.length == 0) return;
        currentIndex = (currentIndex >= currentFiles.length - 1) ? 0 : currentIndex + 1;
        isPaused = false;
        pausedPosition = 0;
        playCurrentIndex();
    }

    private void pauseOrResume() {
        if (clip == null) return;
        if (!isPaused) {
            pausedPosition = clip.getMicrosecondPosition();
            clip.stop();
            isPaused = true;
            isPlaying = false;
        } else {
            clip.setMicrosecondPosition(pausedPosition);
            clip.start();
            isPaused = false;
            isPlaying = true;
        }
        if (playBtn != null) playBtn.repaint();
    }

    private void stopMusic() {
        if (clip != null) {
            if (clip.isRunning()) clip.stop();
            clip.close();
            clip = null;
        }
        isPaused = false;
        pausedPosition = 0;
        isPlaying = false;
        nowPlayingLabel.setText("Now playing: -");
        if (playBtn != null) playBtn.repaint();
    }

    private void setupVolumeControl() {
        volumeControl = null;
        if (clip != null && clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            volumeControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            updateVolume();
        }
    }

    private void updateVolume() {
        if (volumeControl == null) return;
        int value = volumeSlider.getValue();
        float min = volumeControl.getMinimum();
        float max = volumeControl.getMaximum();
        float gain = (float) (min + (max - min) * Math.pow(value / 100.0, 1.5));
        volumeControl.setValue(gain);
    }
}

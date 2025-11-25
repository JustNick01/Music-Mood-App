import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import javax.sound.sampled.*;

import com.formdev.flatlaf.FlatDarkLaf;

// Custom JPanel with alpha transparency support for animations
class AlphaPanel extends JPanel {
    private float alpha = 1f;

    public AlphaPanel(LayoutManager layout) {
        super(layout);
        setOpaque(false);
    }

    public void setAlpha(float alpha) {
        this.alpha = Math.max(0f, Math.min(1f, alpha));
    }

    public float getAlpha() {
        return alpha;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        Composite oldComposite = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        super.paintComponent(g);
        g2.setComposite(oldComposite);
    }

    @Override
    public void paintChildren(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        Composite oldComposite = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        super.paintChildren(g);
        g2.setComposite(oldComposite);
    }
}

public class MusicMoodApp extends JFrame {

    private CardLayout cardLayout;
    private JPanel mainPanel;
    private AlphaPanel moodPanel, musicPanel;

    private Clip clip;
    private FloatControl volumeControl;
    private boolean isPaused = false;
    private long pausedPosition = 0;
    
    // MP3 playback variables
    private boolean isMp3Mode = false;
    private SourceDataLine mp3Line;
    private Thread mp3Thread;
    private volatile boolean mp3StopRequested = false;
    private volatile boolean mp3Paused = false;
    private long mp3TotalMicros = -1;
    private long mp3PositionMicros = 0;
    private long mp3TotalBytes = -1;
    private File currentMp3File;
    private AudioFormat mp3DecodeFormat;

    private String selectedMood;
    private JList<String> songList;
    private DefaultListModel<String> listModel;
    private File[] currentFiles;
    private int currentIndex = -1;

    private ControlButton playBtn, stopBtn, nextBtn, prevBtn, backBtn;
    private JLabel nowPlayingLabel;
    private JLabel volLabel;
    private JLabel titleLabel;
    private JLabel moodTitleLabel;
    private JLabel moodSubtitleLabel;
    private JSlider volumeSlider;
    private JSlider progressSlider;
    private javax.swing.Timer progressTimer;
    private boolean seekingProgress = false;
    private JLabel currentTimeLabel;
    private JLabel totalTimeLabel;
    private AlphaPanel songListPanel;

    private boolean isPlaying = false; // true = clip is playing (not paused / not stopped)

    private Clip uiClickClip;
    private Font customFont;
    private Font musicFont;
    private boolean isFullscreen = false;

    private final String MUSIC_PATH = "music/";
    private final String SOUND_PATH = "sounds/click.wav";
    private final String ICON_PATH = "assets/icons/";

    public static void main(String[] args) {
        FlatDarkLaf.setup();
        SwingUtilities.invokeLater(MusicMoodApp::new);
    }

    public MusicMoodApp() {
        setTitle("Music Mood App");
        setSize(1000, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(true);  // Enable resizing
        setExtendedState(JFrame.NORMAL);  // Allow fullscreen support

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        loadClickSound();
        loadCustomFont();

        initMoodPanel();
        initMusicPanel();

        mainPanel.add(moodPanel, "mood");
        mainPanel.add(musicPanel, "music");
        add(mainPanel);

        setVisible(true);
        
        // Fade in the mood panel on startup
        SwingUtilities.invokeLater(() -> animateMoodPanelIn());
        
        // Add keyboard shortcut for fullscreen (F11)
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_F11) {
                    toggleFullscreen();
                }
            }
        });
        setFocusable(true);
    }

    private void toggleFullscreen() {
        isFullscreen = !isFullscreen;
        
        if (isFullscreen) {
            // Enter fullscreen mode
            setExtendedState(JFrame.MAXIMIZED_BOTH);
            dispose();
            setUndecorated(true);
            setVisible(true);
            setExtendedState(JFrame.NORMAL);
        } else {
            // Exit fullscreen mode
            dispose();
            setUndecorated(false);
            setVisible(true);
            setExtendedState(JFrame.NORMAL);
            setLocationRelativeTo(null);
        }
    }

    // ================= FONT =================

    private void loadCustomFont() {
        try {
            File fontFile = new File("fonts/NewFont.ttf");
            if (fontFile.exists()) {
                customFont = Font.createFont(Font.TRUETYPE_FONT, fontFile);
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(customFont);
            } else {
                System.out.println("Font not found: " + fontFile.getPath());
                customFont = new Font("SansSerif", Font.PLAIN, 20);
            }
            // Try to load a second font specifically for the music selection page
            try {
                File mf = new File("fonts/FontV2.ttf");
                if (mf.exists()) {
                    musicFont = Font.createFont(Font.TRUETYPE_FONT, mf);
                    GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(musicFont);
                } else {
                    musicFont = customFont; // fallback
                }
            } catch (Exception ex) {
                musicFont = customFont;
            }
        } catch (Exception e) {
            System.out.println("Failed to load font: " + e.getMessage());
            customFont = new Font("SansSerif", Font.PLAIN, 20);
        }
    }

    private Font fPlain(float size) {
        return (customFont != null ? customFont.deriveFont(Font.PLAIN, size)
                                   : new Font("SansSerif", Font.PLAIN, (int) size));
    }

    private Font fBold(float size) {
        return (customFont != null ? customFont.deriveFont(Font.BOLD, size)
                                   : new Font("SansSerif", Font.BOLD, (int) size));
    }

    // ================= CLICK SOUND =================

    private void loadClickSound() {
        try {
            File soundFile = new File(SOUND_PATH);
            if (!soundFile.exists()) return;
            AudioInputStream ais = AudioSystem.getAudioInputStream(soundFile);
            uiClickClip = AudioSystem.getClip();
            uiClickClip.open(ais);
        } catch (Exception ignored) {}
    }

    private void playClickSound() {
        if (uiClickClip == null) return;
        if (uiClickClip.isRunning()) uiClickClip.stop();
        uiClickClip.setFramePosition(0);
        uiClickClip.start();
    }

    // ================= MOOD PANEL =================

    private void initMoodPanel() {
        moodPanel = new AlphaPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                GradientPaint gp = new GradientPaint(0, 0, new Color(80, 80, 80),
                                                     w, h, new Color(10, 10, 10));
                g2.setPaint(gp);
                g2.fillRect(0, 0, w, h);
                super.paintComponent(g);
            }
        };

    moodTitleLabel = new JLabel("Music Mood App", SwingConstants.CENTER);
    moodTitleLabel.setForeground(Color.WHITE);
    moodTitleLabel.setFont(fBold(48f));

    moodSubtitleLabel = new JLabel("Choose your mood", SwingConstants.CENTER);
    moodSubtitleLabel.setForeground(new Color(220, 220, 220));
    moodSubtitleLabel.setFont(fPlain(26f));

        JPanel top = new JPanel(new GridLayout(2, 1));
        top.setOpaque(false);
        top.setBorder(BorderFactory.createEmptyBorder(25, 0, 20, 0));
    top.add(moodTitleLabel);
    top.add(moodSubtitleLabel);

        JPanel grid = new JPanel(new GridLayout(2, 3, 25, 25));
        grid.setOpaque(false);
        grid.setBorder(BorderFactory.createEmptyBorder(40, 80, 40, 80));

        grid.add(createMoodButton("Happy", new Color(255, 230, 90)));
        grid.add(createMoodButton("Sad", new Color(90, 120, 255)));
        grid.add(createMoodButton("Chill", new Color(90, 240, 210)));
        grid.add(createMoodButton("Energetic", new Color(255, 120, 90)));
        grid.add(createMoodButton("Love", new Color(255, 80, 160)));
        grid.add(createMoodButton("Focus", new Color(130, 220, 150)));

        moodPanel.add(top, BorderLayout.NORTH);
        moodPanel.add(grid, BorderLayout.CENTER);
        
        // Add resize listener to update button font sizes dynamically
        moodPanel.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                updateMoodButtonFonts(grid);
            }
        });
    }

    private void updateMoodButtonFonts(JPanel grid) {
        // Calculate responsive font size based on mood panel height
        int panelHeight = moodPanel.getHeight();
        int panelWidth = moodPanel.getWidth();
        
        // Scale based on both dimensions
        float scale = Math.min(panelWidth / 1000f, panelHeight / 600f);
        
        // Update title and subtitle
        if (moodTitleLabel != null) {
            float titleSize = Math.max(24f, Math.min(48f, 48f * scale));
            moodTitleLabel.setFont(fBold(titleSize));
        }
        if (moodSubtitleLabel != null) {
            float subtitleSize = Math.max(16f, Math.min(26f, 26f * scale));
            moodSubtitleLabel.setFont(fPlain(subtitleSize));
        }
        
        // Update mood button fonts
        float baseFontSize = Math.max(16f, Math.min(40f, 40f * scale));
        
        for (Component comp : grid.getComponents()) {
            if (comp instanceof MoodButton) {
                ((MoodButton) comp).setFont(fBold(baseFontSize));
            }
        }
    }

    private MoodButton createMoodButton(String text, Color baseColor) {
        MoodButton btn = new MoodButton(text, baseColor);
        // Calculate responsive font size based on window height
        float baseFontSize = Math.max(16f, Math.min(40f, getHeight() / 12f));
        btn.setFont(fBold(baseFontSize));
        btn.addActionListener(e -> {
            playClickSound();
            openMood(text.toLowerCase()); // "happy" -> folder music/happy
        });
        return btn;
    }

    private class MoodButton extends JButton {
        private final Color baseColor;
        private float glow = 0f;
        private Timer timer;
        private float pressScale = 1f;
        private Timer pressTimer;

        MoodButton(String text, Color baseColor) {
            super(text);
            this.baseColor = baseColor;
            setFocusPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { startGlow(true); }
                @Override public void mouseExited(MouseEvent e) { startGlow(false); }
                @Override public void mousePressed(MouseEvent e) { animatePressDown(); }
                @Override public void mouseReleased(MouseEvent e) { animatePressUp(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();

            // Apply press scale
            int scaledW = (int) (w * pressScale);
            int scaledH = (int) (h * pressScale);
            int x = (w - scaledW) / 2;
            int y = (h - scaledH) / 2;

            GradientPaint gp = new GradientPaint(x, y, baseColor.brighter(), x, y + scaledH, baseColor.darker());
            g2.setPaint(gp);
            g2.fillRoundRect(x, y, scaledW, scaledH, 40, 40);

            if (glow > 0f) {
                g2.setColor(new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), (int) (120 * glow)));
                g2.setStroke(new BasicStroke(4f * glow));
                g2.drawRoundRect(x - 2, y - 2, scaledW + 4, scaledH + 4, 40, 40);
            }

            g2.setColor(Color.BLACK);
            FontMetrics fm = g2.getFontMetrics(getFont());
            int tw = fm.stringWidth(getText());
            int th = fm.getAscent();
            g2.drawString(getText(), (w - tw) / 2, (h + th / 2) / 2);
        }

        private void startGlow(boolean in) {
            if (timer != null && timer.isRunning()) timer.stop();
            timer = new Timer(15, e -> {
                glow += in ? 0.08f : -0.08f;
                if (glow < 0f) { glow = 0f; timer.stop(); }
                if (glow > 1f) { glow = 1f; timer.stop(); }
                repaint();
            });
            timer.start();
        }

        private void animatePressDown() {
            if (pressTimer != null && pressTimer.isRunning()) pressTimer.stop();
            pressTimer = new Timer(20, e -> {
                pressScale -= 0.08f;
                if (pressScale < 0.9f) {
                    pressScale = 0.9f;
                    pressTimer.stop();
                }
                repaint();
            });
            pressTimer.start();
        }

        private void animatePressUp() {
            if (pressTimer != null && pressTimer.isRunning()) pressTimer.stop();
            pressTimer = new Timer(20, e -> {
                pressScale += 0.1f;
                if (pressScale > 1f) {
                    pressScale = 1f;
                    pressTimer.stop();
                }
                repaint();
            });
            pressTimer.start();
        }
    }

    // ================= MUSIC PANEL =================

    private void initMusicPanel() {
    musicPanel = new AlphaPanel(new BorderLayout()) {
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            // Solid background for top and bottom areas
            g2.setColor(new Color(100, 100, 100));
            g2.fillRect(0, 0, w, h);
            super.paintComponent(g);
        }
    };

    titleLabel = new JLabel("Select a song", SwingConstants.CENTER);
    titleLabel.setForeground(Color.WHITE);
    // revert: use the app's configured bold font for consistency
    titleLabel.setFont(fBold(48f));

    listModel = new DefaultListModel<>();
    songList = new JList<>(listModel);
    songList.setBackground(new Color(25, 25, 30));
    songList.setForeground(Color.WHITE);
    songList.setSelectionBackground(new Color(0x1DB954));
    songList.setSelectionForeground(Color.BLACK);
    if (musicFont != null) songList.setFont(musicFont.deriveFont(Font.PLAIN, 22f));
    else songList.setFont(fPlain(22f));

    JScrollPane scrollPane = new JScrollPane(songList);
    scrollPane.setBorder(BorderFactory.createEmptyBorder(10, 40, 10, 40));
    scrollPane.setOpaque(false);
    scrollPane.getViewport().setOpaque(false);

    // Wrap scrollPane in an AlphaPanel for fade-in animation with gradient
    songListPanel = new AlphaPanel(new BorderLayout()) {
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            // Smooth gradient from light gray to dark gray
            GradientPaint gp = new GradientPaint(0, 0, new Color(180, 180, 180),
                                                 0, h, new Color(60, 60, 60));
            g2.setPaint(gp);
            g2.fillRect(0, 0, w, h);
            super.paintComponent(g);
        }
    };
    songListPanel.add(scrollPane, BorderLayout.CENTER);

    // ==== bottom bar ====
    JPanel bottom = new JPanel(new BorderLayout());
    bottom.setOpaque(false);
    bottom.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));

    // ==== control buttons ====
    // increase the vertical gap so the icons sit lower and align with the center area
    JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 30));
    controls.setOpaque(false);

    prevBtn = createControlButton(ControlButton.Type.PREV);
    playBtn = createControlButton(ControlButton.Type.PLAY_PAUSE);
    stopBtn = createControlButton(ControlButton.Type.STOP);
    nextBtn = createControlButton(ControlButton.Type.NEXT);
    backBtn = createControlButton(ControlButton.Type.BACK);

    controls.add(prevBtn);
    controls.add(playBtn);
    controls.add(stopBtn);
    controls.add(nextBtn);
    controls.add(backBtn);

    // ==== center section ====
    nowPlayingLabel = new JLabel("Now playing: -");
    nowPlayingLabel.setForeground(Color.WHITE);
    // use FontV2 if available for Now playing text
    if (musicFont != null) nowPlayingLabel.setFont(musicFont.deriveFont(Font.PLAIN, 18f));
    else nowPlayingLabel.setFont(fPlain(18f));
    nowPlayingLabel.setPreferredSize(new Dimension(500, 36));
    nowPlayingLabel.setMaximumSize(new Dimension(Short.MAX_VALUE, 36));
    nowPlayingLabel.setHorizontalAlignment(SwingConstants.LEFT);
    nowPlayingLabel.setVerticalAlignment(SwingConstants.CENTER);
    nowPlayingLabel.setToolTipText("Currently playing song");

    volLabel = new JLabel("Vol");
    volLabel.setForeground(Color.WHITE);
    // use FontV2 for the volume label if available
    if (musicFont != null) volLabel.setFont(musicFont.deriveFont(Font.PLAIN, 18f));
    else volLabel.setFont(fPlain(18f));

    volumeSlider = new JSlider(0, 100, 80);
    volumeSlider.setPreferredSize(new Dimension(150, 22));
    volumeSlider.setMaximumSize(new Dimension(150, 22));
    volumeSlider.setMinimumSize(new Dimension(150, 22));
    volumeSlider.setOpaque(false);

    // ==== glowing slider ====
    final boolean[] hovering = {false};
    volumeSlider.addMouseListener(new MouseAdapter() {
        @Override public void mouseEntered(MouseEvent e) { hovering[0] = true; volumeSlider.repaint(); }
        @Override public void mouseExited(MouseEvent e) { hovering[0] = false; volumeSlider.repaint(); }
    });

    volumeSlider.setUI(new BasicSliderUI(volumeSlider) {
        @Override
        public void paintTrack(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int cy = trackRect.y + (trackRect.height / 2) - 3;
            int cw = trackRect.width;
            int ch = 8;
            g2.setColor(new Color(40, 40, 40));
            g2.fillRoundRect(trackRect.x, cy, cw, ch, ch, ch);
            int filled = (int) (cw * (slider.getValue() / 100.0));
            Color fill = hovering[0] ? new Color(40, 255, 120) : new Color(30, 215, 96);
            g2.setColor(fill);
            g2.fillRoundRect(trackRect.x, cy, filled, ch, ch, ch);
        }

        @Override
        public void paintThumb(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int size = 16;
            int x = thumbRect.x + (thumbRect.width - size) / 2;
            int y = thumbRect.y + (thumbRect.height - size) / 2;
            Color c = hovering[0] ? new Color(40, 255, 120) : new Color(30, 215, 96);
            g2.setColor(c);
            g2.fillOval(x, y, size, size);
        }
    });

    volumeSlider.addChangeListener((ChangeEvent e) -> updateVolume());

    // ====== progress / seek slider (centered) ======
    progressSlider = new JSlider(0, 1000, 0); // use 0..1000 for smoothness
    progressSlider.setPreferredSize(new Dimension(400, 8)); // Much smaller, just the track
    progressSlider.setMaximumSize(new Dimension(400, 8));
    progressSlider.setOpaque(false);
    progressSlider.setFocusable(false);
    progressSlider.setBorder(null); // Remove any border

    // user interaction: mark seeking on press, perform seek on release
    progressSlider.addMouseListener(new MouseAdapter() {
        @Override public void mousePressed(MouseEvent e) { seekingProgress = true; }
        @Override public void mouseReleased(MouseEvent e) {
            seekingProgress = false;
            int val = progressSlider.getValue();
            if (isMp3Mode && mp3TotalMicros > 0) {
                long newPos = (long) ((val / 1000.0) * mp3TotalMicros);
                mp3SeekTo(newPos);
            } else if (clip != null && clip.isOpen() && clip.getMicrosecondLength() > 0) {
                long newPos = (long) ((val / 1000.0) * clip.getMicrosecondLength());
                try { clip.setMicrosecondPosition(newPos); } catch (Exception ignored) {}
            }
        }
    });

    progressSlider.addChangeListener((ChangeEvent e) -> {
        if (seekingProgress) {
            int v = progressSlider.getValue();
            if (isMp3Mode && mp3TotalMicros > 0) {
                long len = mp3TotalMicros;
                long pos = (long) ((v / 1000.0) * len);
                progressSlider.setToolTipText(formatTime(pos) + " / " + formatTime(len));
            } else if (clip != null && clip.isOpen() && clip.getMicrosecondLength() > 0) {
                long len = clip.getMicrosecondLength();
                long pos = (long) ((v / 1000.0) * len);
                progressSlider.setToolTipText(formatTime(pos) + " / " + formatTime(len));
            }
        }
    });

    // Custom UI for the progress slider with gradient and modern look
    progressSlider.setUI(new BasicSliderUI(progressSlider) {
        @Override
        public void paintTrack(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int cy = trackRect.y + (trackRect.height / 2) - 4;
            int cw = trackRect.width;
            int ch = 8;
            
            // Background track (dark)
            g2.setColor(new Color(40, 40, 40));
            g2.fillRoundRect(trackRect.x, cy, cw, ch, ch, ch);
            
            // Gradient fill for played portion
            int filled = (int) (cw * (slider.getValue() / 1000.0));
            GradientPaint gp = new GradientPaint(
                trackRect.x, cy, new Color(30, 215, 96),
                trackRect.x + filled, cy, new Color(0, 180, 70)
            );
            g2.setPaint(gp);
            g2.fillRoundRect(trackRect.x, cy, filled, ch, ch, ch);
        }

        @Override
        public void paintThumb(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int size = 18;
            int x = thumbRect.x + (thumbRect.width - size) / 2;
            int y = thumbRect.y + (thumbRect.height - size) / 2;
            
            // Gradient thumb
            GradientPaint thumbGradient = new GradientPaint(
                x, y, new Color(30, 215, 96),
                x + size, y + size, new Color(0, 180, 70)
            );
            g2.setPaint(thumbGradient);
            g2.fillOval(x, y, size, size);
            
            // Glow effect
            g2.setColor(new Color(30, 215, 96, 100));
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(x - 2, y - 2, size + 4, size + 4);
        }
    });

    // Timer display labels for current and total time
    currentTimeLabel = new JLabel("0:00");
    currentTimeLabel.setForeground(new Color(200, 200, 200));
    currentTimeLabel.setFont(fPlain(14f));
    currentTimeLabel.setPreferredSize(new Dimension(60, 24));
    currentTimeLabel.setHorizontalAlignment(SwingConstants.RIGHT);
    currentTimeLabel.setVerticalAlignment(SwingConstants.CENTER);
    
    totalTimeLabel = new JLabel("0:00");
    totalTimeLabel.setForeground(new Color(150, 150, 150));
    totalTimeLabel.setFont(fPlain(14f));
    totalTimeLabel.setPreferredSize(new Dimension(60, 24));
    totalTimeLabel.setHorizontalAlignment(SwingConstants.LEFT);
    totalTimeLabel.setVerticalAlignment(SwingConstants.CENTER);

    // ==== progress bar container with timer ====
    JPanel progressContainer = new JPanel();
    progressContainer.setLayout(new BorderLayout(16, 0));
    progressContainer.setOpaque(false);
    progressContainer.setPreferredSize(new Dimension(550, 40));
    progressContainer.add(currentTimeLabel, BorderLayout.WEST);
    progressContainer.add(progressSlider, BorderLayout.CENTER);
    progressContainer.add(totalTimeLabel, BorderLayout.EAST);
    progressContainer.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));

    // ==== fixed alignment ====
    JPanel centerPanel = new JPanel(new BorderLayout());
centerPanel.setOpaque(false);

// "Now playing" – lower it slightly so it centers with the controls and volume box
JPanel leftInfo = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 34)); // increased vgap to move text lower
leftInfo.setOpaque(false);
leftInfo.add(nowPlayingLabel);

// "Vol" și slider – la același nivel
// increase vgap by 6px so the volume box sits slightly lower
JPanel rightInfo = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 28)); // was 22, then 26
rightInfo.setOpaque(false);
// add a bit of right padding so the volume box sits slightly left from the edge
// nudged: reduced to 15px (3px to the right from previous 18px)
rightInfo.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 15));

// Put the Vol label and slider inside a gray box with padding and subtle border
JPanel volBox = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 6));
volBox.setOpaque(true);
volBox.setBackground(new Color(70, 70, 70)); // gray box
volBox.setBorder(BorderFactory.createCompoundBorder(
    BorderFactory.createLineBorder(new Color(90, 90, 90)),
    BorderFactory.createEmptyBorder(6, 8, 6, 8)
));
volBox.add(volLabel);
volBox.add(volumeSlider);

rightInfo.add(volBox);

centerPanel.add(leftInfo, BorderLayout.CENTER);
centerPanel.add(rightInfo, BorderLayout.EAST);

    // Add resize listener to update "Now playing" label when window size changes
    centerPanel.addComponentListener(new java.awt.event.ComponentAdapter() {
        @Override
        public void componentResized(java.awt.event.ComponentEvent e) {
            // Re-update the label when the panel is resized (e.g., fullscreen)
            if (clip != null && clip.isOpen() && currentFiles != null && currentIndex >= 0) {
                updateNowPlayingLabel(currentFiles[currentIndex].getName());
            }
        }
    });

    // add progress/seek bar as its own centered row across the bottom
    JPanel seekWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 6));
    seekWrap.setOpaque(false);
    seekWrap.add(progressContainer);

    // assemble bottom: controls left, center info in center, progress centered at bottom
    bottom.add(controls, BorderLayout.WEST);
    bottom.add(centerPanel, BorderLayout.CENTER);
    bottom.add(seekWrap, BorderLayout.SOUTH);

    // ==== assemble ====
    musicPanel.add(titleLabel, BorderLayout.NORTH);
    musicPanel.add(songListPanel, BorderLayout.CENTER);
    musicPanel.add(bottom, BorderLayout.SOUTH);

    // ==== actions ====
    prevBtn.addActionListener(e -> { playClickSound(); playPrevious(); });
    nextBtn.addActionListener(e -> { playClickSound(); playNext(); });
    stopBtn.addActionListener(e -> { playClickSound(); stopMusic(); });
    backBtn.addActionListener(e -> {
        playClickSound();
        stopMusic();
        animateBackTransition();
    });
    playBtn.addActionListener(e -> {
        playClickSound();
        if (clip == null && !isMp3Mode) playSelectedFromList();
        else pauseOrResume();
        playBtn.setIcon(loadButtonIcon(ControlButton.Type.PLAY_PAUSE));
    });
    
    // Add resize listener to make all elements responsive
    musicPanel.addComponentListener(new java.awt.event.ComponentAdapter() {
        @Override
        public void componentResized(java.awt.event.ComponentEvent e) {
            updateMusicPanelSizes();
        }
    });
}


    // ================= CONTROL BUTTONS =================

    private ControlButton createControlButton(ControlButton.Type type) {
        ControlButton btn = new ControlButton(type);
        btn.setPreferredSize(new Dimension(50, 50));
        btn.setIcon(loadButtonIcon(type));
        btn.setBorder(null);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        // Add mouse listener to trigger animation on press
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                btn.animatePress();
            }
        });
        
        return btn;
    }

    private ImageIcon loadButtonIcon(ControlButton.Type type) {
        String file;
        switch (type) {
            case PLAY_PAUSE -> file = isPlaying ? "pause.png" : "play.png";
            case STOP -> file = "stop.png";
            case NEXT -> file = "next.png";
            case PREV -> file = "prev.png";
            case BACK -> file = "back.png";
            default -> file = "play.png";
        }
        File f = new File(ICON_PATH + file);
        if (!f.exists()) {
            System.out.println("Missing icon: " + f.getPath());
            return new ImageIcon();
        }
        Image img = new ImageIcon(f.getAbsolutePath()).getImage()
                .getScaledInstance(32, 32, Image.SCALE_SMOOTH);
        return new ImageIcon(img);
    }

    private static class ControlButton extends JButton {
        enum Type { PREV, PLAY_PAUSE, NEXT, STOP, BACK }
        private float pressAnimation = 0f;
        private Timer pressTimer;

        ControlButton(Type type) {}

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Apply scale animation on press with smooth easing
            float easedPress = pressAnimation * pressAnimation * (3f - 2f * pressAnimation); // smooth easing
            float scale = 1f - (easedPress * 0.15f); // scale down 15% max
            int w = getWidth(), h = getHeight();
            int scaledW = (int) (w * scale);
            int scaledH = (int) (h * scale);
            int x = (w - scaledW) / 2;
            int y = (h - scaledH) / 2;

            // Draw semi-transparent background with color based on press
            int bgAlpha = (int) (30 + easedPress * 80);
            g2.setColor(new Color(255, 255, 255, bgAlpha));
            g2.fillRoundRect(x, y, scaledW, scaledH, 15, 15);

            // Draw border/glow with stronger effect
            int glowAlpha = (int) (80 + easedPress * 100);
            Color glowColor = new Color(30, 215, 96, glowAlpha);
            g2.setColor(glowColor);
            g2.setStroke(new BasicStroke(1.5f + easedPress * 1.5f));
            g2.drawRoundRect(x, y, scaledW, scaledH, 15, 15);

            // Outer glow ring
            g2.setColor(new Color(30, 215, 96, (int) (50 * easedPress)));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(x - 4, y - 4, scaledW + 8, scaledH + 8, 15, 15);

            // Draw the icon centered
            Icon icon = getIcon();
            if (icon != null) {
                int iconX = x + (scaledW - icon.getIconWidth()) / 2;
                int iconY = y + (scaledH - icon.getIconHeight()) / 2;
                icon.paintIcon(this, g2, iconX, iconY);
            }
        }

        void animatePress() {
            if (pressTimer != null && pressTimer.isRunning()) pressTimer.stop();
            pressTimer = new Timer(30, e -> {
                pressAnimation += 0.12f;
                if (pressAnimation > 1f) {
                    pressAnimation = 1f;
                    pressTimer.stop();
                    // Animate back out
                    animateRelease();
                    return;
                }
                repaint();
            });
            pressTimer.start();
        }

        void animateRelease() {
            if (pressTimer != null && pressTimer.isRunning()) pressTimer.stop();
            pressTimer = new Timer(30, e -> {
                pressAnimation -= 0.15f;
                if (pressAnimation < 0f) {
                    pressAnimation = 0f;
                    pressTimer.stop();
                    return;
                }
                repaint();
            });
            pressTimer.start();
        }
    }

    // ================= LOGIC PLAYBACK =================

    private void openMood(String mood) {
        selectedMood = mood;
        listModel.clear();
        currentFiles = null;
        currentIndex = -1;
        stopMusic(); // reset when changing mood

        File folder = new File(MUSIC_PATH + mood);
        if (folder.exists() && folder.isDirectory()) {
                File[] files = folder.listFiles((d, n) -> {
                    String ln = n.toLowerCase();
                    return ln.endsWith(".wav") || ln.endsWith(".mp3");
                });
            if (files != null && files.length > 0) {
                currentFiles = files;
                for (File f : files) {
                    // Remove file extension for display
                    String name = f.getName();
                    int dotIndex = name.lastIndexOf('.');
                    String displayName = (dotIndex > 0) ? name.substring(0, dotIndex) : name;
                    listModel.addElement(displayName);
                }
            } else {
                JOptionPane.showMessageDialog(this, "No audio files (.wav/.mp3) found for: " + mood);
            }
        } else {
            JOptionPane.showMessageDialog(this, "Folder not found: " + folder.getPath());
        }

        // Animate transition to music panel
        animateTransition();
    }

    private void animateTransition() {
        // Create fade animation when showing music panel
        musicPanel.setOpaque(false);
        musicPanel.setAlpha(0f);
        songListPanel.setAlpha(0f);
        cardLayout.show(mainPanel, "music");
        
        Timer transitionTimer = new Timer(25, null);
        transitionTimer.addActionListener(e -> {
            float alpha = musicPanel.getAlpha();
            alpha += 0.1f;
            if (alpha >= 1f) {
                alpha = 1f;
                ((Timer) e.getSource()).stop();
                musicPanel.setOpaque(true);
            }
            musicPanel.setAlpha(alpha);
            songListPanel.setAlpha(alpha);
            mainPanel.repaint();
        });
        transitionTimer.start();
    }

    private void animateBackTransition() {
        // Fade out before returning to mood panel
        Timer fadeOutTimer = new Timer(25, null);
        fadeOutTimer.addActionListener(e -> {
            float alpha = musicPanel.getAlpha();
            alpha -= 0.1f;
            if (alpha <= 0f) {
                alpha = 0f;
                ((Timer) e.getSource()).stop();
                cardLayout.show(mainPanel, "mood");
                musicPanel.setAlpha(1f);
                songListPanel.setAlpha(1f);
            }
            musicPanel.setAlpha(alpha);
            songListPanel.setAlpha(alpha);
            mainPanel.repaint();
        });
        fadeOutTimer.start();
    }

    private void animateMoodPanelIn() {
        // Fade in the mood panel on app startup
        moodPanel.setAlpha(0f);
        Timer fadeInTimer = new Timer(25, null);
        fadeInTimer.addActionListener(e -> {
            float alpha = moodPanel.getAlpha();
            alpha += 0.08f;
            if (alpha >= 1f) {
                alpha = 1f;
                ((Timer) e.getSource()).stop();
            }
            moodPanel.setAlpha(alpha);
            mainPanel.repaint();
        });
        fadeInTimer.start();
    }

    private void playSelectedFromList() {
        if (currentFiles == null || currentFiles.length == 0) {
            JOptionPane.showMessageDialog(this, "No songs loaded for this mood.");
            return;
        }
        int index = songList.getSelectedIndex();
        if (index < 0) {
            JOptionPane.showMessageDialog(this, "Select a song first.");
            return;
        }
        currentIndex = index;
        pausedPosition = 0; // new song -> from start
        playCurrentIndex();
    }

    private void playCurrentIndex() {
        if (currentFiles == null || currentIndex < 0 || currentIndex >= currentFiles.length) return;
        File audioFile = currentFiles[currentIndex];
        String name = audioFile.getName().toLowerCase();
        
        if (name.endsWith(".mp3")) {
            playMp3File(audioFile);
        } else {
            try {
                stopAllPlayback();
                AudioInputStream ais = AudioSystem.getAudioInputStream(audioFile);
                clip = AudioSystem.getClip();
                clip.open(ais);
                setupVolumeControl();
                clip.setMicrosecondPosition(pausedPosition);
                clip.start();
                isMp3Mode = false;
                isPlaying = true;
                isPaused = false;
                songList.setSelectedIndex(currentIndex);
                updateNowPlayingLabel(audioFile.getName());
                playBtn.setIcon(loadButtonIcon(ControlButton.Type.PLAY_PAUSE));
                startProgressTimer();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Cannot play: " + ex.getMessage());
            }
        }
    }

    private void playNext() {
        if (currentFiles == null || currentFiles.length == 0) return;
        currentIndex = (currentIndex + 1) % currentFiles.length;
        pausedPosition = 0; // next song from start
        playCurrentIndex();
    }

    private void playPrevious() {
        if (currentFiles == null || currentFiles.length == 0) return;
        currentIndex = (currentIndex - 1 + currentFiles.length) % currentFiles.length;
        pausedPosition = 0; // previous from start
        playCurrentIndex();
    }

    private void pauseOrResume() {
        if (isMp3Mode) {
            if (!mp3Paused) {
                mp3Paused = true;
                if (mp3Line != null) mp3Line.stop();
                isPaused = true;
                isPlaying = false;
                stopProgressTimer();
            } else {
                mp3Paused = false;
                if (mp3Line != null) mp3Line.start();
                isPaused = false;
                isPlaying = true;
                startProgressTimer();
            }
            return;
        }
        
        if (clip == null) return;

        if (!isPaused && clip.isRunning()) {
            // pause
            pausedPosition = clip.getMicrosecondPosition();
            clip.stop();
            isPaused = true;
            isPlaying = false;
            stopProgressTimer();
        } else if (isPaused || !clip.isRunning()) {
            // resume from pausedPosition
            clip.setMicrosecondPosition(pausedPosition);
            clip.start();
            isPaused = false;
            isPlaying = true;
            startProgressTimer();
        }
    }

    private void stopClipOnly() {
        if (clip != null) {
            if (clip.isRunning()) clip.stop();
            clip.close();
            clip = null;
        }
    }
    
    private void stopMp3Only() {
        if (mp3Thread != null) {
            mp3StopRequested = true;
            try { mp3Thread.join(500); } catch (InterruptedException ignored) {}
            mp3Thread = null;
        }
        if (mp3Line != null) {
            try { mp3Line.stop(); } catch (Exception ignored) {}
            try { mp3Line.close(); } catch (Exception ignored) {}
            mp3Line = null;
        }
        isMp3Mode = false;
    }
    
    private void stopAllPlayback() {
        stopClipOnly();
        stopMp3Only();
        resetProgress();
    }
    
    // ensure progress timer is stopped and UI reset when clip is stopped
    private void resetProgress() {
        stopProgressTimer();
        if (progressSlider != null) {
            progressSlider.setValue(0);
            progressSlider.setToolTipText(null);
            progressSlider.setEnabled(true);
        }
    }

    // ===== progress timer helpers =====
    private void startProgressTimer() {
        if (progressTimer == null) {
            progressTimer = new javax.swing.Timer(200, e -> {
                updateProgressSlider();
                updateTimeLabels();
            });
        }
        progressTimer.start();
    }

    private void stopProgressTimer() {
        if (progressTimer != null) progressTimer.stop();
    }

    private void updateTimeLabels() {
        if (currentTimeLabel == null || totalTimeLabel == null) return;
        if (isMp3Mode) {
            currentTimeLabel.setText(formatTime(mp3PositionMicros));
            totalTimeLabel.setText(formatTime(mp3TotalMicros));
            return;
        }
        if (clip != null && clip.isOpen()) {
            currentTimeLabel.setText(formatTime(clip.getMicrosecondPosition()));
            totalTimeLabel.setText(formatTime(clip.getMicrosecondLength()));
        }
    }

    private void updateProgressSlider() {
        if (isMp3Mode) {
            long len = mp3TotalMicros;
            long pos = mp3PositionMicros;
            if (len <= 0) {
                progressSlider.setEnabled(false);
                progressSlider.setValue(0);
                progressSlider.setToolTipText(formatTime(pos));
                return;
            }
            progressSlider.setEnabled(true);
            if (!seekingProgress) {
                int val = (int) ((pos * 1000) / len);
                progressSlider.setValue(Math.max(0, Math.min(1000, val)));
            }
            progressSlider.setToolTipText(formatTime(pos) + " / " + formatTime(len));
            return;
        }
        
        if (clip == null || !clip.isOpen()) {
            progressSlider.setValue(0);
            return;
        }
        long len = clip.getMicrosecondLength();
        if (len <= 0) {
            progressSlider.setEnabled(false);
            progressSlider.setValue(0);
            return;
        }
        long pos = clip.getMicrosecondPosition();
        if (!seekingProgress) {
            int val = (int) ((pos * 1000) / len);
            progressSlider.setValue(Math.max(0, Math.min(1000, val)));
        }
        progressSlider.setToolTipText(formatTime(pos) + " / " + formatTime(len));
    }

    private void stopMusic() {
        stopAllPlayback();
        pausedPosition = 0;
        isPaused = false;
        isPlaying = false;
        nowPlayingLabel.setText("Now playing: -");
        if (playBtn != null) {
            playBtn.setIcon(loadButtonIcon(ControlButton.Type.PLAY_PAUSE));
        }
    }

    // ================= MP3 PLAYBACK =================
    
    private void playMp3File(File file) {
        try {
            stopAllPlayback();

            // Get duration from file properties
            mp3TotalMicros = -1;
            mp3TotalBytes = -1;
            try {
                AudioFileFormat aff = AudioSystem.getAudioFileFormat(file);
                Object dur = aff.properties().get("duration");
                if (dur instanceof Long) mp3TotalMicros = (Long) dur;
                int byteLen = aff.getByteLength();
                if (byteLen > 0) mp3TotalBytes = byteLen;
            } catch (Exception ignored) {}

            AudioInputStream baseStream = AudioSystem.getAudioInputStream(file);
            AudioFormat baseFormat = baseStream.getFormat();
            AudioFormat decodeFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(),
                    16,
                    baseFormat.getChannels(),
                    baseFormat.getChannels() * 2,
                    baseFormat.getSampleRate(),
                    false
            );
            AudioInputStream decodedStream = AudioSystem.getAudioInputStream(decodeFormat, baseStream);

            DataLine.Info info = new DataLine.Info(SourceDataLine.class, decodeFormat);
            mp3Line = (SourceDataLine) AudioSystem.getLine(info);
            mp3Line.open(decodeFormat);

            isMp3Mode = true;
            isPaused = false;
            isPlaying = true;
            mp3StopRequested = false;
            mp3Paused = false;
            mp3PositionMicros = 0;
            currentMp3File = file;
            mp3DecodeFormat = decodeFormat;

            setupVolumeControl();

            songList.setSelectedIndex(currentIndex);
            updateNowPlayingLabel(file.getName());
            playBtn.setIcon(loadButtonIcon(ControlButton.Type.PLAY_PAUSE));

            startMp3Thread(decodedStream, baseStream);
            startProgressTimer();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Cannot play: " + ex.getMessage());
            stopMp3Only();
        }
    }

    private void startMp3Thread(AudioInputStream decodedStream, AudioInputStream baseStream) {
        mp3Thread = new Thread(() -> {
            try {
                mp3Line.start();
                byte[] buffer = new byte[4096];
                int n;
                double bytesPerSecond = mp3DecodeFormat.getFrameRate() * mp3DecodeFormat.getFrameSize();
                while (!mp3StopRequested && (n = decodedStream.read(buffer, 0, buffer.length)) != -1) {
                    while (mp3Paused && !mp3StopRequested) {
                        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                    }
                    if (mp3StopRequested) break;
                    mp3Line.write(buffer, 0, n);
                    mp3PositionMicros += (long) ((n / bytesPerSecond) * 1_000_000);
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Cannot play: " + ex.getMessage()));
            } finally {
                try { decodedStream.close(); } catch (Exception ignored) {}
                try { baseStream.close(); } catch (Exception ignored) {}
                try { if (mp3Line != null) mp3Line.drain(); } catch (Exception ignored) {}
                try { if (mp3Line != null) mp3Line.close(); } catch (Exception ignored) {}
                SwingUtilities.invokeLater(() -> {
                    isPlaying = false;
                    stopProgressTimer();
                });
            }
        }, "mp3-player-thread");
        mp3Thread.start();
    }

    private void mp3SeekTo(long micros) {
        if (!isMp3Mode || currentMp3File == null || mp3TotalMicros <= 0 || mp3TotalBytes <= 0) return;
        stopMp3Only();
        try {
            AudioInputStream baseStream = AudioSystem.getAudioInputStream(currentMp3File);
            long targetBytes = (long) ((micros / (double) mp3TotalMicros) * mp3TotalBytes);
            long remaining = Math.max(0, targetBytes);
            byte[] skipBuf = new byte[4096];
            while (remaining > 0) {
                long skipped = baseStream.skip(Math.min(remaining, skipBuf.length));
                if (skipped <= 0) break;
                remaining -= skipped;
            }

            AudioInputStream decodedStream = AudioSystem.getAudioInputStream(mp3DecodeFormat, baseStream);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, mp3DecodeFormat);
            mp3Line = (SourceDataLine) AudioSystem.getLine(info);
            mp3Line.open(mp3DecodeFormat);

            isMp3Mode = true;
            mp3Paused = false;
            mp3StopRequested = false;
            isPlaying = true;
            mp3PositionMicros = micros;

            setupVolumeControl();
            startMp3Thread(decodedStream, baseStream);
            startProgressTimer();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Cannot seek: " + ex.getMessage());
        }
    }

    // ================= VOLUME =================

    private void setupVolumeControl() {
        volumeControl = null;
        if (isMp3Mode) {
            if (mp3Line != null && mp3Line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                volumeControl = (FloatControl) mp3Line.getControl(FloatControl.Type.MASTER_GAIN);
            }
        } else if (clip != null && clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            volumeControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        }
        updateVolume();
    }

    private void updateVolume() {
        if (volumeControl == null) return;
        
        // Convert linear slider (0-100) to logarithmic gain for better perceived volume control
        float sliderVal = volumeSlider.getValue() / 100f;
        float min = volumeControl.getMinimum();
        float max = volumeControl.getMaximum();
        
        // Logarithmic scale: 0 slider = min, 1 slider = max
        // Using power function for smoother progression
        float logVal = (float) Math.pow(sliderVal, 0.7f); // 0.7 gives better low-end control
        float gain = min + (max - min) * logVal;
        volumeControl.setValue(gain);
    }

    // format microsecond time to M:SS
    private String formatTime(long micros) {
        if (micros <= 0) return "0:00";
        long totalSec = micros / 1_000_000L;
        long m = totalSec / 60;
        long s = totalSec % 60;
        return String.format("%d:%02d", m, s);
    }
    // ================= SHORTEN SONG TITLE =================
    private void updateNowPlayingLabel(String fileName) {
        // Remove file extension from display
        int dotIndex = fileName.lastIndexOf('.');
        String displayFileName = (dotIndex > 0) ? fileName.substring(0, dotIndex) : fileName;
        
        // Get the actual rendered font metrics for accurate calculation
        FontMetrics fm = nowPlayingLabel.getFontMetrics(nowPlayingLabel.getFont());
        
        // Calculate available width for the label based on its parent container.
        // Use the parent width (left info area) so the text shortens automatically
        // when the volume box / right area takes more space.
        int availableWidth = 0;
        if (nowPlayingLabel.getParent() != null) availableWidth = nowPlayingLabel.getParent().getWidth();
        if (availableWidth <= 0) {
            // Fallback to the label's own width or a sensible default
            availableWidth = nowPlayingLabel.getWidth() > 0 ? nowPlayingLabel.getWidth() : 400;
        }

        // Calculate how many characters fit in the available width
        String prefix = "Now playing: ";
        int prefixWidth = fm.stringWidth(prefix);
        int remainingWidth = availableWidth - prefixWidth - 20; // 20px margin
        
        // Find the maximum characters that fit (without extension)
        String shortened = displayFileName;
        if (fm.stringWidth(displayFileName) > remainingWidth) {
            String ellipsis = "...";
            int ellipsisWidth = fm.stringWidth(ellipsis);
            int baseWidth = remainingWidth - ellipsisWidth;
            
            // Binary search for the right length
            int maxBaseChars = displayFileName.length();
            for (int i = displayFileName.length(); i > 0; i--) {
                if (fm.stringWidth(displayFileName.substring(0, i)) <= baseWidth) {
                    maxBaseChars = i;
                    break;
                }
            }
            shortened = displayFileName.substring(0, Math.max(1, maxBaseChars)) + ellipsis;
        }
        
        String displayText = prefix + shortened;
        nowPlayingLabel.setText(displayText);
        nowPlayingLabel.setToolTipText(displayFileName); // Full name without extension in tooltip
    }

    private void updateMusicPanelSizes() {
        int panelWidth = musicPanel.getWidth();
        int panelHeight = musicPanel.getHeight();
        
        if (panelWidth <= 0 || panelHeight <= 0) return; // Not initialized yet
        
        // Calculate responsive sizes based on panel dimensions
        float widthScale = panelWidth / 1000f; // Base width 1000
        float heightScale = panelHeight / 600f; // Base height 600
        float scale = Math.min(widthScale, heightScale);
        
        // Update title font size
        if (titleLabel != null) {
            float titleSize = Math.max(24f, Math.min(48f, 48f * scale));
            titleLabel.setFont(fBold(titleSize));
        }
        
        // Update volume label font size
        if (volLabel != null) {
            float volSize = Math.max(12f, Math.min(18f, 18f * scale));
            if (musicFont != null) volLabel.setFont(musicFont.deriveFont(Font.PLAIN, volSize));
            else volLabel.setFont(fPlain(volSize));
        }
        
        // Update song list font
        if (songList != null) {
            float listFontSize = Math.max(14f, Math.min(22f, 22f * scale));
            if (musicFont != null) songList.setFont(musicFont.deriveFont(Font.PLAIN, listFontSize));
            else songList.setFont(fPlain(listFontSize));
        }
        
        // Update control button sizes
        int buttonSize = (int) Math.max(30, Math.min(50, 50 * scale));
        int iconSize = (int) Math.max(20, Math.min(32, 32 * scale));
        if (prevBtn != null) updateControlButtonSize(prevBtn, buttonSize, iconSize);
        if (playBtn != null) updateControlButtonSize(playBtn, buttonSize, iconSize);
        if (stopBtn != null) updateControlButtonSize(stopBtn, buttonSize, iconSize);
        if (nextBtn != null) updateControlButtonSize(nextBtn, buttonSize, iconSize);
        if (backBtn != null) updateControlButtonSize(backBtn, buttonSize, iconSize);
        
        // Update now playing label font
        if (nowPlayingLabel != null) {
            float labelSize = Math.max(12f, Math.min(18f, 18f * scale));
            if (musicFont != null) nowPlayingLabel.setFont(musicFont.deriveFont(Font.PLAIN, labelSize));
            else nowPlayingLabel.setFont(fPlain(labelSize));
            
            // Re-update the label text with new size
            if (clip != null && clip.isOpen() && currentFiles != null && currentIndex >= 0) {
                updateNowPlayingLabel(currentFiles[currentIndex].getName());
            }
        }
        
        // Update volume slider size
        if (volumeSlider != null) {
            int sliderWidth = (int) Math.max(100, Math.min(150, 150 * scale));
            volumeSlider.setPreferredSize(new Dimension(sliderWidth, 22));
            volumeSlider.setMaximumSize(new Dimension(sliderWidth, 22));
            volumeSlider.setMinimumSize(new Dimension(sliderWidth, 22));
        }
        
        // Update progress slider size
        if (progressSlider != null) {
            int progressWidth = (int) Math.max(250, Math.min(400, 400 * scale));
            progressSlider.setPreferredSize(new Dimension(progressWidth, 8));
            progressSlider.setMaximumSize(new Dimension(progressWidth, 8));
        }
        
        // Update time labels
        if (currentTimeLabel != null && totalTimeLabel != null) {
            float timeSize = Math.max(10f, Math.min(14f, 14f * scale));
            currentTimeLabel.setFont(fPlain(timeSize));
            totalTimeLabel.setFont(fPlain(timeSize));
        }
        
        musicPanel.revalidate();
        musicPanel.repaint();
    }
    
    private void updateControlButtonSize(ControlButton btn, int size, int iconSize) {
        btn.setPreferredSize(new Dimension(size, size));
        // Reload icon with new size
        ControlButton.Type type = null;
        if (btn == prevBtn) type = ControlButton.Type.PREV;
        else if (btn == playBtn) type = ControlButton.Type.PLAY_PAUSE;
        else if (btn == stopBtn) type = ControlButton.Type.STOP;
        else if (btn == nextBtn) type = ControlButton.Type.NEXT;
        else if (btn == backBtn) type = ControlButton.Type.BACK;
        
        if (type != null) {
            btn.setIcon(loadButtonIconWithSize(type, iconSize));
        }
    }
    
    private ImageIcon loadButtonIconWithSize(ControlButton.Type type, int size) {
        String file;
        switch (type) {
            case PLAY_PAUSE -> file = isPlaying ? "pause.png" : "play.png";
            case STOP -> file = "stop.png";
            case NEXT -> file = "next.png";
            case PREV -> file = "prev.png";
            case BACK -> file = "back.png";
            default -> file = "play.png";
        }
        File f = new File(ICON_PATH + file);
        if (!f.exists()) {
            return new ImageIcon();
        }
        Image img = new ImageIcon(f.getAbsolutePath()).getImage()
                .getScaledInstance(size, size, Image.SCALE_SMOOTH);
        return new ImageIcon(img);
    }

}

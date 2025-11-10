import javax.swing.*;
import javax.sound.sampled.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

public class MusicMoodApp extends JFrame {

    private static class MoodInfo {
        String displayName;
        String filePath;
        Color color;

        MoodInfo(String displayName, String filePath, Color color) {
            this.displayName = displayName;
            this.filePath = filePath;
            this.color = color;
        }
    }

    private final Map<String, MoodInfo> moods = new LinkedHashMap<>();
    private Clip currentClip;
    private JLabel moodLabel;
    private JLabel nowPlayingLabel;

    public MusicMoodApp() {
        setTitle("Music Mood App");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 400);
        setLocationRelativeTo(null); // center
        setLayout(new BorderLayout());
        initMoods();
        initUI();
    }

    private void initMoods() {
        // aici pui caile catre melodiile tale
        moods.put("happy", new MoodInfo(
                "Happy",
                "music" + File.separator + "happy.wav",
                new Color(0xFFE082)
        ));
        moods.put("sad", new MoodInfo(
                "Sad",
                "music" + File.separator + "sad.wav",
                new Color(0x90CAF9)
        ));
        moods.put("chill", new MoodInfo(
                "Chill",
                "music" + File.separator + "chill.wav",
                new Color(0xA5D6A7)
        ));
        moods.put("energetic", new MoodInfo(
                "Energetic",
                "music" + File.separator + "energetic.wav",
                new Color(0xFFAB91)
        ));
        moods.put("love", new MoodInfo(
            "Love",
            "music" + File.separator + "love.wav",
            new Color(0xF48FB1)
    ));
    }

    private void initUI() {
        // panel sus – titlu
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        topPanel.setBackground(new Color(0x263238));

        JLabel title = new JLabel("🎧 Music Mood App");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        topPanel.add(title, BorderLayout.WEST);

        add(topPanel, BorderLayout.NORTH);

        // panel centru – mood curent + now playing
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(30, 20, 20, 20));
        centerPanel.setBackground(new Color(0x37474F));

        moodLabel = new JLabel("Select a mood to start 🎵", SwingConstants.CENTER);
        moodLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        moodLabel.setForeground(Color.WHITE);
        moodLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));

        nowPlayingLabel = new JLabel("Now playing: -", SwingConstants.CENTER);
        nowPlayingLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        nowPlayingLabel.setForeground(new Color(0xCFD8DC));
        nowPlayingLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        nowPlayingLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        centerPanel.add(moodLabel);
        centerPanel.add(Box.createVerticalStrut(10));
        centerPanel.add(nowPlayingLabel);

        add(centerPanel, BorderLayout.CENTER);

        // panel jos – butoane
        JPanel bottomPanel = new JPanel();
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 20, 20));
        bottomPanel.setLayout(new GridLayout(2, 3, 10, 10));
        bottomPanel.setBackground(new Color(0x263238));

        for (String key : moods.keySet()) {
            MoodInfo info = moods.get(key);
            JButton btn = createMoodButton(info.displayName, key, info.color);
            bottomPanel.add(btn);
        }

        // buton STOP
        JButton stopButton = new JButton("⏹ Stop");
        stopButton.setFocusPainted(false);
        stopButton.setFont(new Font("Segoe UI", Font.BOLD, 16));
        stopButton.setBackground(new Color(0xEF5350));
        stopButton.setForeground(Color.WHITE);
        stopButton.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        stopButton.addActionListener(this::stopButtonClicked);
        bottomPanel.add(stopButton);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    private JButton createMoodButton(String text, String key, Color color) {
        JButton btn = new JButton(text);
        btn.setFocusPainted(false);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 16));
        btn.setBackground(color);
        btn.setForeground(Color.DARK_GRAY);
        btn.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        btn.addActionListener(e -> playMood(key));
        return btn;
    }

    private void playMood(String key) {
        MoodInfo info = moods.get(key);
        if (info == null) return;

        stopCurrentClip();

        try {
            File audioFile = new File(info.filePath);
            if (!audioFile.exists()) {
                JOptionPane.showMessageDialog(
                        this,
                        "Could not find file:\n" + audioFile.getAbsolutePath(),
                        "File not found",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }

            AudioInputStream audioIn = AudioSystem.getAudioInputStream(audioFile);
            Clip clip = AudioSystem.getClip();
            clip.open(audioIn);
            clip.start();
            currentClip = clip;

            // update UI
            getContentPane().setBackground(info.color);
            moodLabel.setText("Mood: " + info.displayName);
            nowPlayingLabel.setText("Now playing: " + audioFile.getName());

        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(
                    this,
                    "Error playing audio:\n" + ex.getMessage(),
                    "Audio error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void stopCurrentClip() {
        if (currentClip != null) {
            if (currentClip.isRunning()) {
                currentClip.stop();
            }
            currentClip.close();
            currentClip = null;
        }
    }

    private void stopButtonClicked(ActionEvent e) {
        stopCurrentClip();
        moodLabel.setText("Music stopped ⏹");
        nowPlayingLabel.setText("Now playing: -");
        getContentPane().setBackground(new Color(0x37474F));
    }

    public static void main(String[] args) {
        // stil mai ok pe Windows
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            MusicMoodApp app = new MusicMoodApp();
            app.setVisible(true);
        });
    }
}

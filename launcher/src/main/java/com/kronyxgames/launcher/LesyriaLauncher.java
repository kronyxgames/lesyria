package com.kronyxgames.launcher;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

public class LesyriaLauncher extends JFrame {

    private JTextField usernameField;
    private JButton launchButton;
    private JCheckBox bedrockCheckBox;

    private static final String SERVER_IP = "play.lesyria.com";
    private static final int JAVA_PORT = 25565;
    private static final int BEDROCK_PORT = 19132;

    public LesyriaLauncher() {
        setTitle("Lesyria Launcher");
        setSize(400, 200);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initComponents();
        setVisible(true);
    }

    private void initComponents() {
        setLayout(new GridLayout(4, 2));

        add(new JLabel("Username:"));
        usernameField = new JTextField();
        add(usernameField);

        add(new JLabel("Edition:"));
        bedrockCheckBox = new JCheckBox("Bedrock Edition");
        add(bedrockCheckBox);

        add(new JLabel()); // Empty
        launchButton = new JButton("Launch Game");
        launchButton.addActionListener(new LaunchAction());
        add(launchButton);
    }

    private class LaunchAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            String username = usernameField.getText().trim();
            if (username.isEmpty()) {
                JOptionPane.showMessageDialog(LesyriaLauncher.this, "Please enter a username.");
                return;
            }

            boolean isBedrock = bedrockCheckBox.isSelected();
            int port = isBedrock ? BEDROCK_PORT : JAVA_PORT;

            try {
                launchMinecraft(username, SERVER_IP, port, isBedrock);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(LesyriaLauncher.this, "Failed to launch Minecraft: " + ex.getMessage());
            }
        }
    }

    private void launchMinecraft(String username, String serverIp, int port, boolean isBedrock) throws IOException {
        // This is a simplified launcher. In reality, you'd need to handle Minecraft installation,
        // authentication, and proper launching. This assumes Minecraft is installed.

        String minecraftDir = getMinecraftDirectory();
        String javaPath = System.getProperty("java.home") + "/bin/java";

        ProcessBuilder pb;
        if (isBedrock) {
            // For Bedrock, you'd need a different approach, perhaps launching the official launcher
            // with server connection. This is placeholder.
            JOptionPane.showMessageDialog(this, "Bedrock launching not fully implemented. Connect manually to " + serverIp + ":" + port);
            return;
        } else {
            // Java Edition launch (simplified)
            pb = new ProcessBuilder(
                javaPath,
                "-Xmx2G",
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+UseG1GC",
                "-XX:G1NewSizePercent=20",
                "-XX:G1ReservePercent=20",
                "-XX:MaxGCPauseMillis=50",
                "-XX:G1HeapRegionSize=32M",
                "-Djava.library.path=" + minecraftDir + "/bin/natives",
                "-cp", getClasspath(minecraftDir),
                "net.minecraft.client.main.Main",
                "--username", username,
                "--version", "1.20.4",
                "--gameDir", minecraftDir,
                "--assetsDir", minecraftDir + "/assets",
                "--assetIndex", "1.20",
                "--uuid", "00000000-0000-0000-0000-000000000000", // Fake UUID
                "--accessToken", "00000000000000000000000000000000", // Fake token
                "--userType", "legacy",
                "--server", serverIp,
                "--port", String.valueOf(port)
            );
        }

        pb.directory(new java.io.File(minecraftDir));
        pb.start();

        System.exit(0); // Close launcher after launching
    }

    private String getMinecraftDirectory() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return System.getenv("APPDATA") + "/.minecraft";
        } else if (os.contains("mac")) {
            return System.getProperty("user.home") + "/Library/Application Support/minecraft";
        } else {
            return System.getProperty("user.home") + "/.minecraft";
        }
    }

    private String getClasspath(String minecraftDir) throws IOException {
        // Simplified classpath - in reality, you'd need all JARs
        return minecraftDir + "/libraries/*:" + minecraftDir + "/versions/1.20.4/1.20.4.jar";
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(LesyriaLauncher::new);
    }
}
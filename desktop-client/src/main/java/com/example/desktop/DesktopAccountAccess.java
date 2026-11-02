package com.example.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pre-login native account center for the packaged Windows client.
 * Uses the portal's public registration/recovery APIs, so passwords and
 * verification codes are handled by the same backend as web/Android.
 */
public final class DesktopAccountAccess {
    private static final String BASE = "https://remote-javafx-portal.onrender.com";
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    private static final ObjectMapper JSON = new ObjectMapper();

    private DesktopAccountAccess() {}

    /** Returns false only when the user explicitly exits instead of continuing to sign in. */
    public static boolean showBeforeSignIn() {
        if (GraphicsEnvironment.isHeadless()) return true;
        AtomicBoolean proceed = new AtomicBoolean(true);
        try {
            SwingUtilities.invokeAndWait(() -> proceed.set(showChooser()));
        } catch (Exception ex) {
            System.err.println("Account access launcher unavailable: " + ex.getMessage());
            return true;
        }
        return proceed.get();
    }

    private static boolean showChooser() {
        JDialog dialog = new JDialog((Frame) null, "Electrical Workbench · Account Access", true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setSize(520, 560);
        dialog.setLocationRelativeTo(null);

        JPanel root = new JPanel();
        root.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));

        JLabel brand = new JLabel("⚡ ELECTRICAL WORKBENCH");
        brand.setFont(brand.getFont().deriveFont(Font.BOLD, 14f));
        JLabel title = new JLabel("Account access");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 27f));
        JLabel detail = new JLabel("<html>Sign in, create an account, verify email, or recover your username/password.</html>");
        detail.setForeground(new Color(70, 85, 105));
        detail.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton signIn = button("Continue to sign in");
        JButton create = button("Create account");
        JButton verify = button("Verify email code");
        JButton reset = button("Forgot / reset password");
        JButton username = button("Forgot username");
        JButton exit = button("Exit");

        AtomicBoolean proceed = new AtomicBoolean(true);
        signIn.addActionListener(e -> { proceed.set(true); dialog.dispose(); });
        create.addActionListener(e -> showCreate(dialog));
        verify.addActionListener(e -> showVerify(dialog));
        reset.addActionListener(e -> showPasswordReset(dialog));
        username.addActionListener(e -> showForgotUsername(dialog));
        exit.addActionListener(e -> { proceed.set(false); dialog.dispose(); });

        for (Component c : new Component[]{brand, title, detail, signIn, create, verify, reset, username, exit}) {
            if (c instanceof JComponent jc) jc.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        root.add(brand); root.add(Box.createVerticalStrut(10));
        root.add(title); root.add(Box.createVerticalStrut(6));
        root.add(detail); root.add(Box.createVerticalStrut(22));
        root.add(signIn); root.add(Box.createVerticalStrut(9));
        root.add(create); root.add(Box.createVerticalStrut(9));
        root.add(verify); root.add(Box.createVerticalStrut(9));
        root.add(reset); root.add(Box.createVerticalStrut(9));
        root.add(username); root.add(Box.createVerticalStrut(18));
        root.add(exit);

        dialog.setContentPane(root);
        dialog.setVisible(true);
        return proceed.get();
    }

    private static JButton button(String text) {
        JButton b = new JButton(text);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        b.setPreferredSize(new Dimension(440, 42));
        b.setFocusPainted(false);
        return b;
    }

    private static void showCreate(Component parent) {
        JTextField user = new JTextField();
        JTextField display = new JTextField();
        JTextField email = new JTextField();
        JPasswordField password = new JPasswordField();
        JPasswordField confirm = new JPasswordField();
        JComboBox<String> tier = new JComboBox<>(new String[]{"TIER_1", "TIER_2", "TIER_3"});

        JPanel form = form(
            "Username", user,
            "Display name", display,
            "Email", email,
            "Password", password,
            "Confirm password", confirm,
            "Requested tier", tier
        );
        int answer = JOptionPane.showConfirmDialog(parent, form, "Create account", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) return;
        if (!new String(password.getPassword()).equals(new String(confirm.getPassword()))) {
            error(parent, "Passwords do not match.");
            return;
        }
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("username", user.getText().trim());
        body.put("displayName", display.getText().trim());
        body.put("email", email.getText().trim());
        body.put("password", new String(password.getPassword()));
        body.put("requestedTier", String.valueOf(tier.getSelectedItem()));
        run(parent, "Creating account…", () -> {
            JsonNode result = post("/api/public/register/start", body);
            info(parent, "Verification code sent to " + result.path("email").asText("your email") + ".\nUse Verify email code to activate the account.");
        });
    }

    private static void showVerify(Component parent) {
        JTextField user = new JTextField();
        JTextField code = new JTextField();
        JPanel form = form("Username", user, "6-digit verification code", code);
        int answer = JOptionPane.showConfirmDialog(parent, form, "Verify email", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) return;
        run(parent, "Verifying…", () -> {
            JsonNode result = post("/api/public/register/verify", Map.of("username", user.getText().trim(), "code", code.getText().trim()));
            String checkout = result.path("checkoutUrl").asText("");
            if (!checkout.isBlank()) {
                int open = JOptionPane.showConfirmDialog(parent,
                    "Account verified. Continue to the selected tier checkout now?",
                    "Account verified", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
                if (open == JOptionPane.YES_OPTION && Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(checkout));
            } else {
                info(parent, "Account verified. You can sign in now.");
            }
        });
    }

    private static void showForgotUsername(Component parent) {
        JTextField email = new JTextField();
        JPanel form = form("Account email", email);
        int answer = JOptionPane.showConfirmDialog(parent, form, "Forgot username", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) return;
        run(parent, "Sending reminder…", () -> {
            JsonNode result = post("/api/public/recovery/username", Map.of("email", email.getText().trim()));
            info(parent, result.path("message").asText("If the email matches an account, a username reminder has been sent."));
        });
    }

    private static void showPasswordReset(Component parent) {
        JTextField account = new JTextField();
        JPanel request = form("Username or email", account);
        int first = JOptionPane.showConfirmDialog(parent, request, "Request password reset code", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (first != JOptionPane.OK_OPTION) return;
        run(parent, "Sending reset code…", () -> post("/api/public/recovery/password/start", Map.of("account", account.getText().trim())));

        JTextField user = new JTextField(account.getText().contains("@") ? "" : account.getText().trim());
        JTextField code = new JTextField();
        JPasswordField next = new JPasswordField();
        JPanel complete = form("Username", user, "6-digit reset code", code, "New password", next);
        int second = JOptionPane.showConfirmDialog(parent, complete, "Complete password reset", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (second != JOptionPane.OK_OPTION) return;
        run(parent, "Resetting password…", () -> {
            JsonNode result = post("/api/public/recovery/password/complete", Map.of(
                "username", user.getText().trim(),
                "code", code.getText().trim(),
                "newPassword", new String(next.getPassword())
            ));
            info(parent, result.path("message").asText("Password reset. You can sign in now."));
        });
    }

    private static JPanel form(Object... fields) {
        JPanel panel = new JPanel(new GridLayout(fields.length / 2, 2, 10, 9));
        for (int i = 0; i < fields.length; i += 2) {
            panel.add(new JLabel(String.valueOf(fields[i])));
            panel.add((Component) fields[i + 1]);
        }
        panel.setPreferredSize(new Dimension(480, Math.max(90, (fields.length / 2) * 42)));
        return panel;
    }

    private static void run(Component parent, String status, ThrowingAction action) {
        JDialog progress = new JDialog(SwingUtilities.getWindowAncestor(parent), "Electrical Workbench", Dialog.ModalityType.APPLICATION_MODAL);
        progress.add(new JLabel(status, SwingConstants.CENTER));
        progress.setSize(300, 100);
        progress.setLocationRelativeTo(parent);
        Thread worker = new Thread(() -> {
            try {
                action.run();
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> error(parent, ex.getMessage() == null ? "Request failed." : ex.getMessage()));
            } finally {
                SwingUtilities.invokeLater(progress::dispose);
            }
        }, "account-access-request");
        worker.setDaemon(true);
        worker.start();
        progress.setVisible(true);
    }

    private static JsonNode post(String path, Map<String,?> body) throws Exception {
        String payload = JSON.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + path))
            .timeout(Duration.ofSeconds(90))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode json = response.body() == null || response.body().isBlank() ? JSON.createObjectNode() : JSON.readTree(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String message = json.path("detail").asText(json.path("message").asText("Request returned HTTP " + response.statusCode()));
            throw new IllegalStateException(message);
        }
        return json;
    }

    private static void info(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Electrical Workbench", JOptionPane.INFORMATION_MESSAGE);
    }

    private static void error(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Electrical Workbench", JOptionPane.ERROR_MESSAGE);
    }

    @FunctionalInterface
    private interface ThrowingAction { void run() throws Exception; }
}

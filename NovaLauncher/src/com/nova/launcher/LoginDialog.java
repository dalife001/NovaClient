package com.nova.launcher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.function.Consumer;

/**
 * The sign-in window. One click opens Microsoft's page in the browser and the launcher picks up the answer by itself;
 * the second way is a short code for phones, other computers or browsers that will not come back.
 */
final class LoginDialog extends JDialog {
    private static final String CHOOSE = "choose", BROWSER = "browser", CODE = "code", ERROR = "error";

    private final String clientId;
    private final Consumer<Auth.Tokens> onDone;
    private final Consumer<String> log;
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final JLabel codeLabel = LauncherApp.label("", 38, true, LauncherApp.TEXT);
    private final JLabel codeHint = LauncherApp.label("", 13, false, LauncherApp.MUTED);
    private final JLabel errorLabel = LauncherApp.label("", 14, false, LauncherApp.RED);
    private volatile Auth.BrowserLogin browser;
    private volatile boolean closed;
    private volatile String pageToOpen;

    LoginDialog(JFrame owner, String clientId, Consumer<Auth.Tokens> onDone, Consumer<String> log) {
        super(owner, "Sign in", false);
        this.clientId = clientId;
        this.onDone = onDone;
        this.log = log;
        root.setBackground(LauncherApp.BG);
        root.add(choose(), CHOOSE);
        root.add(browserCard(), BROWSER);
        root.add(codeCard(), CODE);
        root.add(errorCard(), ERROR);
        setContentPane(root);
        setResizable(false);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { cancel(); }
        });
        pack();
        setSize(Math.max(getWidth(), 460), Math.max(getHeight(), 330));
        setLocationRelativeTo(owner);
    }

    // ------------------------------------------------------------------ the four cards
    private JPanel choose() {
        JPanel p = page();
        p.add(LauncherApp.label("Sign in", 26, true, LauncherApp.TEXT));
        p.add(gap(4));
        p.add(LauncherApp.label("with the Microsoft account that owns Minecraft", 13, false, LauncherApp.MUTED));
        p.add(gap(22));
        LauncherApp.NovaButton web = new LauncherApp.NovaButton("Sign in with Microsoft", true);
        web.addActionListener(e -> startBrowser());
        p.add(web);
        p.add(gap(10));
        LauncherApp.NovaButton code = new LauncherApp.NovaButton("Use a code instead", false);
        code.addActionListener(e -> startCode());
        p.add(code);
        p.add(gap(16));
        p.add(LauncherApp.label("Your password is never typed into Nova: you sign in on Microsoft's own page.", 12, false, LauncherApp.DIM));
        p.add(gap(14));
        p.add(cancelButton());
        return p;
    }

    private JPanel browserCard() {
        JPanel p = page();
        p.add(LauncherApp.label("Finish in your browser", 24, true, LauncherApp.TEXT));
        p.add(gap(6));
        p.add(LauncherApp.label("Sign in on the Microsoft page that just opened.", 13, false, LauncherApp.MUTED));
        p.add(LauncherApp.label("This window closes by itself when you are done.", 13, false, LauncherApp.MUTED));
        p.add(gap(18));
        p.add(spinner());
        p.add(gap(18));
        LauncherApp.NovaButton again = new LauncherApp.NovaButton("Open the page again", false);
        again.addActionListener(e -> { String u = pageToOpen; if (u != null) Platform.open(u); });
        p.add(again);
        p.add(gap(10));
        LauncherApp.NovaButton other = new LauncherApp.NovaButton("It does not work: use a code", false);
        other.addActionListener(e -> { stopBrowser(); startCode(); });
        p.add(other);
        p.add(gap(10));
        p.add(cancelButton());
        return p;
    }

    private JPanel codeCard() {
        JPanel p = page();
        p.add(LauncherApp.label("Sign in with a code", 24, true, LauncherApp.TEXT));
        p.add(gap(6));
        codeHint.setText("Open the page below on any device and enter this code.");
        p.add(codeHint);
        p.add(gap(14));
        codeLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 38));
        codeLabel.setForeground(LauncherApp.GREEN);
        codeLabel.setAlignmentX(LEFT_ALIGNMENT);
        p.add(codeLabel);
        p.add(gap(14));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        LauncherApp.NovaButton copy = new LauncherApp.NovaButton("Copy code", false);
        copy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(codeLabel.getText()), null);
            copy.setText("Copied");
        });
        LauncherApp.NovaButton open = new LauncherApp.NovaButton("Open the page", true);
        open.addActionListener(e -> { String u = pageToOpen; if (u != null) Platform.open(u); });
        row.add(open);
        row.add(Box.createHorizontalStrut(10));
        row.add(copy);
        p.add(row);
        p.add(gap(16));
        p.add(spinner());
        p.add(gap(14));
        p.add(cancelButton());
        return p;
    }

    private JPanel errorCard() {
        JPanel p = page();
        p.add(LauncherApp.label("That did not work", 24, true, LauncherApp.TEXT));
        p.add(gap(10));
        errorLabel.setAlignmentX(LEFT_ALIGNMENT);
        p.add(errorLabel);
        p.add(gap(22));
        LauncherApp.NovaButton retry = new LauncherApp.NovaButton("Try again", true);
        retry.addActionListener(e -> cards.show(root, CHOOSE));
        p.add(retry);
        p.add(gap(10));
        p.add(cancelButton());
        return p;
    }

    // ------------------------------------------------------------------ the two ways to sign in
    private void startBrowser() {
        cards.show(root, BROWSER);
        Thread t = new Thread(() -> {
            try {
                Auth.BrowserLogin b = new Auth.BrowserLogin(clientId);
                browser = b;
                pageToOpen = b.url();
                Platform.open(pageToOpen);
                log.accept("Waiting for the browser sign-in...");
                Auth.Tokens tokens = b.await(600);
                succeed(tokens);
            } catch (Exception e) {
                fail(e);
            } finally {
                stopBrowser();
            }
        }, "nova-login-browser");
        t.setDaemon(true);
        t.start();
    }

    private void startCode() {
        cards.show(root, CODE);
        codeLabel.setText("...");
        Thread t = new Thread(() -> {
            try {
                Auth.DeviceCode dc = Auth.start(clientId);
                pageToOpen = Auth.codeLink(dc);
                SwingUtilities.invokeLater(() -> {
                    codeLabel.setText(dc.userCode());
                    codeHint.setText("Open " + dc.verificationUri() + " on any device and enter this code.");
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(dc.userCode()), null);
                });
                Platform.open(pageToOpen);
                log.accept("Waiting for the code sign-in...");
                succeed(Auth.poll(clientId, dc));
            } catch (Exception e) {
                fail(e);
            }
        }, "nova-login-code");
        t.setDaemon(true);
        t.start();
    }

    private void succeed(Auth.Tokens tokens) {
        if (closed) return;
        closed = true;
        SwingUtilities.invokeLater(this::dispose);
        onDone.accept(tokens);
    }

    private void fail(Exception e) {
        if (closed) return;
        String msg = Auth.friendly(e.getMessage());
        log.accept("Login failed: " + e.getMessage());
        SwingUtilities.invokeLater(() -> {
            errorLabel.setText("<html><body style='width:340px'>" + msg.replace("<", "&lt;") + "</body></html>");
            cards.show(root, ERROR);
        });
    }

    private void stopBrowser() {
        Auth.BrowserLogin b = browser;
        if (b != null) { browser = null; b.cancel(); }
    }

    private void cancel() {
        closed = true;
        stopBrowser();
        dispose();
    }

    // ------------------------------------------------------------------ small layout helpers
    private JPanel page() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(LauncherApp.BG);
        p.setBorder(new EmptyBorder(26, 30, 22, 30));
        return p;
    }

    private Component gap(int h) { return Box.createVerticalStrut(h); }

    private JProgressBar spinner() {
        JProgressBar b = new JProgressBar();
        b.setIndeterminate(true);
        b.setBorderPainted(false);
        b.setForeground(LauncherApp.GREEN);
        b.setBackground(LauncherApp.SURFACE);
        b.setPreferredSize(new Dimension(380, 6));
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
        b.setAlignmentX(LEFT_ALIGNMENT);
        return b;
    }

    private LauncherApp.NovaButton cancelButton() {
        LauncherApp.NovaButton c = new LauncherApp.NovaButton("Cancel", false);
        c.addActionListener(e -> cancel());
        return c;
    }
}

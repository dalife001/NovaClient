package com.nova.launcher;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/** Launcher skin library: look up public Java skins, save any number, preview, and optionally upload to your account. */
final class SkinManagerDialog extends JDialog {
    private final LauncherConfig cfg;
    private final Account account;
    private final Path directory;
    private final BiConsumer<BufferedImage, Boolean> previewSink;
    private final Runnable accountSkinAction;
    private final DefaultListModel<String> model = new DefaultListModel<>();
    private final JList<String> list = new JList<>(model);
    private final JTextField username = new JTextField();
    private final JLabel status = new JLabel("Find a username or import a skin PNG. Saved skins stay on this computer.");
    private final JLabel preview = new JLabel("", SwingConstants.CENTER);
    private final JButton applyButton = new JButton("Use in launcher");
    private final JButton uploadButton = new JButton("Apply to Minecraft account");
    private boolean busy;

    SkinManagerDialog(Window owner, LauncherConfig cfg, Account account, Path directory,
                      BiConsumer<BufferedImage, Boolean> previewSink, Runnable accountSkinAction) {
        super(owner, "Nova Skin Studio", ModalityType.APPLICATION_MODAL);
        this.cfg = cfg;
        this.account = account;
        this.directory = directory;
        this.previewSink = previewSink;
        this.accountSkinAction = accountSkinAction;
        build();
        reload();
        setMinimumSize(new Dimension(700, 470));
        setSize(760, 500);
        setLocationRelativeTo(owner);
    }

    private void build() {
        JPanel root = new JPanel(new BorderLayout(16, 16));
        root.setBorder(new EmptyBorder(18, 20, 16, 20));
        root.setBackground(LauncherApp.BG);
        setContentPane(root);

        JPanel header = new JPanel(new BorderLayout(4, 4));
        header.setOpaque(false);
        JLabel title = LauncherApp.label("Skin Studio", 23, true, LauncherApp.TEXT);
        JLabel subtitle = LauncherApp.label("Look up, collect, preview, and apply Minecraft skins", 13, false, LauncherApp.MUTED);
        header.add(title, BorderLayout.NORTH);
        header.add(subtitle, BorderLayout.SOUTH);
        root.add(header, BorderLayout.NORTH);

        JPanel library = new JPanel(new BorderLayout(8, 10));
        library.setOpaque(false);
        JPanel search = new JPanel(new BorderLayout(8, 0));
        search.setOpaque(false);
        username.setFont(LauncherApp.font(Font.PLAIN, 14));
        username.setToolTipText("Minecraft Java username");
        search.add(username, BorderLayout.CENTER);
        JButton find = button("Find username");
        find.addActionListener(e -> lookup());
        search.add(find, BorderLayout.EAST);
        library.add(search, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBackground(LauncherApp.SURFACE);
        list.setForeground(LauncherApp.TEXT);
        list.setSelectionBackground(LauncherApp.ACCENT_DARK);
        list.setFont(LauncherApp.font(Font.PLAIN, 14));
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected, boolean focus) {
                JLabel c = (JLabel) super.getListCellRendererComponent(l, value, index, selected, focus);
                c.setBorder(new EmptyBorder(9, 12, 9, 12));
                if (cfg.selectedSkin.equals(value)) c.setText("★   " + value + "   · selected");
                return c;
            }
        });
        list.addListSelectionListener(e -> showSelected());
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createLineBorder(LauncherApp.BORDER));
        library.add(scroll, BorderLayout.CENTER);

        JPanel leftButtons = new JPanel(new GridLayout(1, 2, 8, 0));
        leftButtons.setOpaque(false);
        JButton importButton = button("Import PNG");
        importButton.addActionListener(e -> importPng());
        JButton removeButton = button("Remove saved skin");
        removeButton.addActionListener(e -> removeSelected());
        leftButtons.add(importButton);
        leftButtons.add(removeButton);
        library.add(leftButtons, BorderLayout.SOUTH);
        root.add(library, BorderLayout.CENTER);

        JPanel right = new JPanel(new BorderLayout(8, 12));
        right.setOpaque(false);
        right.setPreferredSize(new Dimension(235, 0));
        preview.setPreferredSize(new Dimension(220, 285));
        preview.setOpaque(true);
        preview.setBackground(LauncherApp.SURFACE);
        preview.setBorder(BorderFactory.createLineBorder(LauncherApp.BORDER));
        preview.setText("Choose a skin to preview");
        preview.setForeground(LauncherApp.MUTED);
        right.add(preview, BorderLayout.CENTER);

        JPanel actions = new JPanel(new GridLayout(0, 1, 0, 7));
        actions.setOpaque(false);
        applyButton.addActionListener(e -> useSelected());
        actions.add(applyButton);
        JButton accountDefault = button("Use signed-in skin");
        accountDefault.addActionListener(e -> useAccountSkin());
        actions.add(accountDefault);
        uploadButton.addActionListener(e -> uploadSelected());
        actions.add(uploadButton);
        status.setForeground(LauncherApp.MUTED);
        status.setFont(LauncherApp.font(Font.PLAIN, 12));
        status.setVerticalAlignment(SwingConstants.TOP);
        actions.add(status);
        right.add(actions, BorderLayout.SOUTH);
        root.add(right, BorderLayout.EAST);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    private JButton button(String text) {
        JButton b = new LauncherApp.NovaButton(text, false);
        b.setPreferredSize(new Dimension(140, 38));
        return b;
    }

    private void reload() {
        model.clear();
        try {
            Files.createDirectories(directory);
            List<String> existing = new ArrayList<>();
            for (String name : cfg.savedSkins) {
                Path file = skinPath(name);
                if (file != null && Files.isRegularFile(file)) existing.add(name);
            }
            cfg.savedSkins.clear();
            cfg.savedSkins.addAll(existing);
            for (String name : existing) model.addElement(name);
            cfg.save();
        } catch (IOException e) { setStatus("Could not open skin folder: " + e.getMessage(), LauncherApp.RED); }
        if (!model.isEmpty()) {
            int selected = cfg.selectedSkin.isBlank() ? 0 : Math.max(0, model.indexOf(cfg.selectedSkin));
            list.setSelectedIndex(selected);
        }
    }

    private Path skinPath(String name) {
        if (name == null || name.isBlank() || !Path.of(name).getFileName().toString().equals(name)) return null;
        Path base = directory.toAbsolutePath().normalize();
        Path file = base.resolve(name).normalize();
        return file.getParent() != null && file.getParent().equals(base) ? file : null;
    }

    private void lookup() {
        String name = username.getText().trim();
        if (!name.matches("[A-Za-z0-9_]{1,16}")) { setStatus("Enter a valid Java username (1–16 letters, digits, underscores).", LauncherApp.RED); return; }
        runBusy("Looking up " + name + "...", () -> {
            String escaped = URLEncoder.encode(name, StandardCharsets.UTF_8);
            Map<String, Object> profile = Http.getJson("https://api.mojang.com/users/profiles/minecraft/" + escaped);
            String uuid = Json.str(profile, "id");
            if (uuid == null || uuid.isBlank()) throw new IOException("No Minecraft Java profile found for " + name + ".");
            Map<String, Object> session = Http.getJson("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
            List<Object> properties = Json.a(session.get("properties"));
            if (properties == null || properties.isEmpty()) throw new IOException("That profile has no public skin.");
            Map<String, Object> property = Json.o(properties.get(0));
            String encoded = Json.str(property, "value");
            Map<String, Object> textures = Json.o(Json.parse(new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8)));
            Map<String, Object> skin = Json.o(Json.o(textures.get("textures")).get("SKIN"));
            String url = Json.str(skin, "url");
            if (url == null) throw new IOException("That profile has no public skin.");
            byte[] skinBytes = Http.getBytes(url);
            BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(skinBytes));
            if (!validSkin(image)) throw new IOException("The profile returned an invalid skin image.");
            boolean slim = "slim".equalsIgnoreCase(Json.str(Json.o(skin.get("metadata")), "model"));
            String safe = name + "-" + uuid.substring(0, Math.min(8, uuid.length())) + ".png";
            saveSkin(image, safe, slim);
            SwingUtilities.invokeLater(() -> {
                if (slim) setStatus("Found " + name + "’s slim skin; saved to your library.", LauncherApp.GREEN);
                else setStatus("Found " + name + "’s skin; saved to your library.", LauncherApp.GREEN);
            });
        });
    }

    private static boolean validSkin(BufferedImage image) {
        return image != null && image.getWidth() == 64 && (image.getHeight() == 64 || image.getHeight() == 32);
    }

    private void importPng() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import Minecraft skin PNG");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            BufferedImage image = ImageIO.read(chooser.getSelectedFile());
            if (!validSkin(image)) throw new IOException("Skin must be a 64x64 or 64x32 PNG.");
            String base = chooser.getSelectedFile().getName().replaceFirst("(?i)\\.png$", "").replaceAll("[^A-Za-z0-9_-]", "_");
            saveSkin(image, base + ".png", false);
            setStatus("Skin imported.", LauncherApp.GREEN);
        } catch (Exception e) { setStatus(e.getMessage(), LauncherApp.RED); }
    }

    private void saveSkin(BufferedImage image, String requested, boolean slim) throws IOException {
        Files.createDirectories(directory);
        String name = requested;
        if (skinPath(name) == null) throw new IOException("Invalid skin filename.");
        Path target = skinPath(name);
        if (target == null) throw new IOException("Invalid skin filename.");
        ImageIO.write(image, "png", target.toFile());
        if (!cfg.savedSkins.contains(name)) cfg.savedSkins.add(name);
        cfg.skinSlim.put(name, slim);
        SwingUtilities.invokeLater(() -> {
            cfg.save();
            reload();
            list.setSelectedValue(name, true);
            showSelected();
        });
    }

    private void showSelected() {
        String name = list.getSelectedValue();
        if (name == null) { preview.setIcon(null); preview.setText("Choose a skin to preview"); return; }
        Path file = skinPath(name);
        try {
            BufferedImage image = file == null ? null : ImageIO.read(file.toFile());
            if (!validSkin(image)) throw new IOException("Invalid skin image.");
            int[] pixels = SkinRenderer.front(image, cfg.skinSlim.getOrDefault(name, false));
            BufferedImage rendered = SkinRenderer.toImage(pixels, 6);
            Image scaled = rendered.getScaledInstance(180, 300, Image.SCALE_DEFAULT);
            preview.setText("");
            preview.setIcon(new ImageIcon(scaled));
        } catch (Exception e) {
            preview.setIcon(null);
            preview.setText("Preview unavailable");
        }
    }

    private void useSelected() {
        String name = list.getSelectedValue();
        Path file = skinPath(name);
        if (file == null) { setStatus("Choose a saved skin first.", LauncherApp.RED); return; }
        try {
            BufferedImage image = ImageIO.read(file.toFile());
            if (!validSkin(image)) throw new IOException("Invalid skin image.");
            cfg.selectedSkin = name;
            cfg.save();
            previewSink.accept(image, cfg.skinSlim.getOrDefault(name, false));
            setStatus("Selected skin for the launcher preview. Use Apply to Minecraft account to change your account skin.", LauncherApp.GREEN);
        } catch (Exception e) { setStatus(e.getMessage(), LauncherApp.RED); }
    }

    private void useAccountSkin() {
        cfg.selectedSkin = "";
        cfg.save();
        accountSkinAction.run();
        setStatus("Using the signed-in account skin; sign in to load its default skin.", LauncherApp.GREEN);
    }

    private void uploadSelected() {
        String name = list.getSelectedValue();
        Path file = skinPath(name);
        if (account == null || !account.online || account.token == null || account.token.isBlank()) {
            setStatus("Sign in with a Minecraft account before applying a skin.", LauncherApp.RED);
            return;
        }
        if (file == null) { setStatus("Choose a saved skin first.", LauncherApp.RED); return; }
        runBusy("Applying skin to your Minecraft profile...", () -> {
            BufferedImage image = ImageIO.read(file.toFile());
            if (!validSkin(image)) throw new IOException("Invalid skin image.");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(image, "png", bytes);
            String boundary = "----NovaSkin" + java.util.UUID.randomUUID();
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            String model = cfg.skinSlim.getOrDefault(name, false) ? "slim" : "classic";
            write(body, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"variant\"\r\n\r\n" + model + "\r\n");
            write(body, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\nContent-Type: image/png\r\n\r\n");
            body.write(bytes.toByteArray());
            write(body, "\r\n--" + boundary + "--\r\n");
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("https://api.minecraftservices.com/minecraft/profile/skins"))
                    .timeout(java.time.Duration.ofSeconds(45))
                    .header("User-Agent", "NovaLauncher/" + GameLauncher.VERSION)
                    .header("Authorization", "Bearer " + account.token)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
            java.net.http.HttpResponse<String> response = Http.CLIENT.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) throw new IOException("Minecraft skin upload failed (HTTP " + response.statusCode() + "): " + response.body());
            SwingUtilities.invokeLater(() -> {
                cfg.selectedSkin = name;
                cfg.save();
                setStatus("Skin applied to your Minecraft account.", LauncherApp.GREEN);
                    previewSink.accept(image, cfg.skinSlim.getOrDefault(name, false));
            });
        });
    }

    private static void write(ByteArrayOutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.UTF_8));
    }

    private void removeSelected() {
        String name = list.getSelectedValue();
        Path file = skinPath(name);
        if (file == null) { setStatus("Choose a saved skin first.", LauncherApp.RED); return; }
        try {
            Files.deleteIfExists(file);
            cfg.savedSkins.remove(name);
            cfg.skinSlim.remove(name);
            if (name.equals(cfg.selectedSkin)) cfg.selectedSkin = "";
            cfg.save();
            reload();
            preview.setIcon(null);
            preview.setText("Choose a skin to preview");
            setStatus("Removed saved skin.", LauncherApp.MUTED);
        } catch (IOException e) { setStatus("Could not remove skin: " + e.getMessage(), LauncherApp.RED); }
    }

    private void runBusy(String message, ThrowingRunnable action) {
        if (busy) return;
        busy = true;
        setEnabledActions(false);
        setStatus(message, LauncherApp.MUTED);
        Thread worker = new Thread(() -> {
            try {
                action.run();
                SwingUtilities.invokeLater(this::reload);
            } catch (Exception e) {
                String error = e.getMessage() == null ? "Skin request failed." : e.getMessage();
                SwingUtilities.invokeLater(() -> setStatus(error, LauncherApp.RED));
            } finally {
                SwingUtilities.invokeLater(() -> { busy = false; setEnabledActions(true); });
            }
        }, "nova-skin-manager");
        worker.setDaemon(true);
        worker.start();
    }

    private void setEnabledActions(boolean enabled) {
        for (Component c : getContentPane().getComponents()) c.setEnabled(enabled);
        applyButton.setEnabled(enabled);
        uploadButton.setEnabled(enabled && account != null && account.online);
    }

    private void setStatus(String text, Color color) {
        status.setText("<html>" + escape(text) + "</html>");
        status.setForeground(color);
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @FunctionalInterface private interface ThrowingRunnable { void run() throws Exception; }
}

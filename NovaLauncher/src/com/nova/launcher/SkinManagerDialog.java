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
import java.util.function.Supplier;

/** Launcher skin library: look up public Java skins, save any number, preview, and optionally upload to your account. */
final class SkinManagerDialog extends JPanel {
    private final LauncherConfig cfg;
    private final Account account;
    private final Path directory;
    private final Path modsDirectory;
    private final Supplier<String> minecraftVersion;
    private final BiConsumer<BufferedImage, Boolean> previewSink;
    private final Runnable accountSkinAction;
    private Runnable closeAction;
    private final DefaultListModel<String> model = new DefaultListModel<>();
    private final JList<String> list = new JList<>(model);
    private final DefaultListModel<String> modModel = new DefaultListModel<>();
    private final JList<String> modList = new JList<>(modModel);
    private final CardLayout workspaceLayout = new CardLayout();
    private final JPanel workspace = new JPanel(workspaceLayout);
    private final JTextField username = new JTextField();
    private final JLabel status = new JLabel("Find a username or import a skin PNG. Saved skins stay on this computer.");
    private final SkinPreview preview = new SkinPreview();
    private final JButton applyButton = new JButton("Use in launcher");
    private final JButton uploadButton = new JButton("Apply to Minecraft account");
    private final List<JButton> actionButtons = new ArrayList<>();
    private boolean busy;

    SkinManagerDialog(LauncherConfig cfg, Account account, Path directory, Path modsDirectory, Supplier<String> minecraftVersion,
                      BiConsumer<BufferedImage, Boolean> previewSink, Runnable accountSkinAction, Runnable closeAction) {
        this.cfg = cfg;
        this.account = account;
        this.directory = directory;
        this.modsDirectory = modsDirectory;
        this.minecraftVersion = minecraftVersion;
        this.previewSink = previewSink;
        this.accountSkinAction = accountSkinAction;
        this.closeAction = closeAction;
        build();
        reload();
    }

    void setCloseAction(Runnable action) { this.closeAction = action; }

    void lookupOnOpen(String name) {
        username.setText(name);
        SwingUtilities.invokeLater(this::lookup);
    }

    void showModsOnOpen() {
        SwingUtilities.invokeLater(() -> workspaceLayout.show(workspace, "mods"));
    }

    private void build() {
        JPanel root = new JPanel(new BorderLayout(16, 16));
        root.setBorder(new EmptyBorder(18, 20, 16, 20));
        root.setBackground(LauncherApp.BG);
        root.setOpaque(true);
        setOpaque(false);
        setLayout(new BorderLayout());
        add(root, BorderLayout.CENTER);

        JPanel header = new JPanel(new BorderLayout(4, 4));
        header.setOpaque(false);
        JLabel title = LauncherApp.label("SKIN LIBRARY", 19, true, LauncherApp.TEXT);
        JLabel subtitle = LauncherApp.label("Find a look that feels like yours", 13, false, LauncherApp.MUTED);
        header.add(title, BorderLayout.NORTH);
        header.add(subtitle, BorderLayout.SOUTH);
        JPanel tabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tabs.setOpaque(false);
        JButton skinsTab = button("Skins");
        JButton modsTab = button("Mods");
        skinsTab.addActionListener(e -> workspaceLayout.show(workspace, "skins"));
        modsTab.addActionListener(e -> { reloadMods(); workspaceLayout.show(workspace, "mods"); });
        tabs.add(skinsTab);
        tabs.add(modsTab);
        JPanel top = new JPanel(new BorderLayout(0, 12));
        top.setOpaque(false);
        top.add(header, BorderLayout.NORTH);
        top.add(tabs, BorderLayout.SOUTH);
        root.add(top, BorderLayout.NORTH);

        JPanel library = new JPanel(new BorderLayout(8, 10));
        library.setOpaque(false);
        JPanel search = new JPanel(new BorderLayout(8, 0));
        search.setOpaque(false);
        username.setFont(LauncherApp.font(Font.PLAIN, 14));
        username.setForeground(LauncherApp.TEXT);
        username.setCaretColor(LauncherApp.ACCENT);
        username.setBackground(LauncherApp.SURFACE);
        username.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LauncherApp.BORDER), new EmptyBorder(8, 11, 8, 11)));
        username.setToolTipText("Minecraft Java username");
        search.add(username, BorderLayout.CENTER);
        JButton find = button("Find username");
        find.addActionListener(e -> lookup());
        search.add(find, BorderLayout.EAST);
        library.add(search, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        list.setVisibleRowCount(1);
        list.setFixedCellWidth(148);
        list.setFixedCellHeight(190);
        list.setBackground(LauncherApp.SURFACE);
        list.setForeground(LauncherApp.TEXT);
        list.setSelectionBackground(LauncherApp.ACCENT_DARK);
        list.setFont(LauncherApp.font(Font.PLAIN, 14));
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected, boolean focus) {
                JLabel c = (JLabel) super.getListCellRendererComponent(l, value, index, selected, focus);
                c.setBorder(new EmptyBorder(8, 8, 8, 8));
                c.setOpaque(true);
                c.setBackground(selected ? LauncherApp.SURFACE2 : LauncherApp.SURFACE);
                c.setForeground(selected ? LauncherApp.TEXT : LauncherApp.MUTED);
                if (value != null) {
                    String name = value.toString();
                    Path skinFile = skinPath(name);
                    try {
                        BufferedImage skinImage = skinFile == null ? null : ImageIO.read(skinFile.toFile());
                        if (validSkin(skinImage)) c.setIcon(new ImageIcon(carouselSprite(skinImage, cfg.skinSlim.getOrDefault(name, false))));
                    } catch (Exception ignored) {}
                    c.setHorizontalTextPosition(SwingConstants.CENTER);
                    c.setVerticalTextPosition(SwingConstants.BOTTOM);
                    c.setText(cfg.selectedSkin.equals(name) ? "✦  " + name : name);
                }
                return c;
            }
        });
        list.addListSelectionListener(e -> showSelected());
        JScrollPane scroll = new JScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(BorderFactory.createLineBorder(LauncherApp.BORDER));
        scroll.getViewport().setBackground(LauncherApp.SURFACE);
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
        workspace.setOpaque(false);
        workspace.add(library, "skins");
        workspace.add(buildModsPanel(), "mods");
        root.add(workspace, BorderLayout.CENTER);

        JPanel right = new JPanel(new BorderLayout(8, 12));
        right.setOpaque(false);
        right.setPreferredSize(new Dimension(235, 0));
        preview.setPreferredSize(new Dimension(220, 285));
        preview.setOpaque(true);
        preview.setBackground(LauncherApp.SURFACE);
        preview.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LauncherApp.BORDER), new EmptyBorder(12, 12, 12, 12)));
        preview.setText("Choose a skin to preview");
        preview.setForeground(LauncherApp.MUTED);
        right.add(preview, BorderLayout.CENTER);

        JPanel actions = new JPanel(new GridLayout(0, 1, 0, 7));
        actions.setOpaque(false);
        applyButton.addActionListener(e -> useSelected());
        actions.add(applyButton);
        uploadButton.addActionListener(e -> uploadSelected());
        actions.add(uploadButton);
        status.setForeground(LauncherApp.MUTED);
        status.setFont(LauncherApp.font(Font.PLAIN, 12));
        status.setVerticalAlignment(SwingConstants.TOP);
        actions.add(status);
        right.add(actions, BorderLayout.SOUTH);
        root.add(right, BorderLayout.EAST);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        JButton close = button("Back to launcher");
        close.addActionListener(e -> { if (closeAction != null) closeAction.run(); });
        footer.add(close, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LauncherApp.alpha(LauncherApp.ACCENT, 150)), new EmptyBorder(1, 1, 1, 1)));
        registerKeyboardAction(e -> { if (closeAction != null) closeAction.run(); }, KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private JButton button(String text) {
        JButton b = new LauncherApp.NovaButton(text, false);
        b.setPreferredSize(new Dimension(140, 38));
        actionButtons.add(b);
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

    private JPanel buildModsPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setOpaque(false);
        modList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        modList.setBackground(LauncherApp.SURFACE);
        modList.setForeground(LauncherApp.TEXT);
        modList.setSelectionBackground(LauncherApp.ACCENT_DARK);
        modList.setFont(LauncherApp.font(Font.PLAIN, 14));
        modList.setFixedCellHeight(42);
        JScrollPane scroll = new JScrollPane(modList);
        scroll.setBorder(BorderFactory.createLineBorder(LauncherApp.BORDER));
        scroll.getViewport().setBackground(LauncherApp.SURFACE);
        panel.add(scroll, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        JButton toggle = button("Toggle selected");
        toggle.addActionListener(e -> toggleSelectedMod());
        JButton importMod = button("Import .jar");
        importMod.addActionListener(e -> importMod());
        JButton removeMod = button("Remove selected");
        removeMod.addActionListener(e -> removeSelectedMod());
        JButton optional = button("Sodium / Iris / Freecam");
        optional.addActionListener(e -> setStatus("Optional mods can be managed from the launcher’s Mods controls.", LauncherApp.MUTED));
        buttons.add(toggle); buttons.add(importMod); buttons.add(removeMod); buttons.add(optional);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private void reloadMods() {
        modModel.clear();
        try {
            Files.createDirectories(modsDirectory);
            try (java.util.stream.Stream<Path> files = Files.list(modsDirectory)) {
                files.filter(p -> p.getFileName().toString().endsWith(".jar") || p.getFileName().toString().endsWith(".jar.disabled"))
                        .sorted().forEach(p -> {
                            String name = p.getFileName().toString();
                            modModel.addElement((name.endsWith(".disabled") ? "○  " : "●  ") + name);
                        });
            }
        } catch (IOException e) { setStatus("Could not read mods folder: " + e.getMessage(), LauncherApp.RED); }
    }

    private Path selectedMod() {
        int index = modList.getSelectedIndex();
        if (index < 0) return null;
        String text = modModel.get(index).substring(3);
        return modsDirectory.resolve(text);
    }

    private void toggleSelectedMod() {
        Path selected = selectedMod();
        if (selected == null) { setStatus("Choose a mod first.", LauncherApp.RED); return; }
        String name = selected.getFileName().toString();
        boolean enabled = name.endsWith(".disabled");
        String base = enabled ? name.substring(0, name.length() - ".disabled".length()) : name;
        try {
            Files.move(selected, modsDirectory.resolve(enabled ? base : base + ".disabled"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            reloadMods();
        } catch (IOException e) { setStatus("Could not toggle mod: " + e.getMessage(), LauncherApp.RED); }
    }

    private void importMod() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Fabric mods (*.jar)", "jar"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            Files.createDirectories(modsDirectory);
            for (java.io.File file : chooser.getSelectedFiles()) if (file.getName().toLowerCase().endsWith(".jar"))
                Files.copy(file.toPath(), modsDirectory.resolve(file.getName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            reloadMods();
        } catch (IOException e) { setStatus("Could not import mod: " + e.getMessage(), LauncherApp.RED); }
    }

    private void removeSelectedMod() {
        Path selected = selectedMod();
        if (selected == null) { setStatus("Choose a mod first.", LauncherApp.RED); return; }
        try { Files.deleteIfExists(selected); reloadMods(); }
        catch (IOException e) { setStatus("Could not remove mod: " + e.getMessage(), LauncherApp.RED); }
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

    private static BufferedImage carouselSprite(BufferedImage skin, boolean slim) {
        BufferedImage body = SkinRenderer.toImage(SkinRenderer.front(skin, slim), 8);
        BufferedImage sprite = new BufferedImage(144, 196, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sprite.createGraphics();
        LauncherApp.aa(g);
        g.setPaint(new RadialGradientPaint(72, 158, 66, new float[]{0f, 1f}, new Color[]{new Color(LauncherApp.ACCENT.getRed(), LauncherApp.ACCENT.getGreen(), LauncherApp.ACCENT.getBlue(), 90), new Color(0, 0, 0, 0)}));
        g.fillOval(4, 85, 136, 110);
        g.setColor(new Color(255, 255, 255, 32));
        g.fillOval(43, 164, 58, 9);
        g.setColor(LauncherApp.alpha(LauncherApp.ACCENT, 170));
        g.setStroke(new BasicStroke(1.2f));
        g.drawOval(28, 8, 88, 172);
        g.drawImage(body, 8, 24, 128, 152, null);
        g.dispose();
        return sprite;
    }

    private void showSelected() {
        String name = list.getSelectedValue();
        if (name == null) { preview.setIcon(null); preview.setText("Choose a skin to preview"); return; }
        Path file = skinPath(name);
        try {
            BufferedImage image = file == null ? null : ImageIO.read(file.toFile());
            if (!validSkin(image)) throw new IOException("Invalid skin image.");
            preview.setText("");
            preview.setIcon(new ImageIcon(carouselSprite(image, cfg.skinSlim.getOrDefault(name, false))));
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
            if (name.equals(cfg.selectedSkin)) {
                cfg.selectedSkin = "";
                cfg.save();
                accountSkinAction.run();
                setStatus("Back to your signed-in Minecraft skin.", LauncherApp.GREEN);
            } else {
                cfg.selectedSkin = name;
                cfg.save();
                previewSink.accept(image, cfg.skinSlim.getOrDefault(name, false));
                setStatus("Selected for the launcher preview. Apply it to your Minecraft account when ready.", LauncherApp.GREEN);
            }
            list.repaint();
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

    /** Interactive front-facing skin preview: left drag rotates, right/middle drag pans, wheel zooms. */
    private static final class SkinPreview extends JLabel {
        private double rotation, zoom = 1.0, panX, panY;
        private int lastX, lastY, dragButton;

        SkinPreview() {
            super("Choose a skin to preview", SwingConstants.CENTER);
            setOpaque(true);
            setBackground(LauncherApp.SURFACE);
            setForeground(LauncherApp.MUTED);
            setToolTipText("Drag to rotate or pan; use the mouse wheel to zoom");
            java.awt.event.MouseAdapter controls = new java.awt.event.MouseAdapter() {
                @Override public void mousePressed(java.awt.event.MouseEvent e) {
                    lastX = e.getX(); lastY = e.getY(); dragButton = e.getButton();
                }
                @Override public void mouseReleased(java.awt.event.MouseEvent e) { dragButton = 0; }
                @Override public void mouseDragged(java.awt.event.MouseEvent e) {
                    int dx = e.getX() - lastX, dy = e.getY() - lastY;
                    if (dragButton == java.awt.event.MouseEvent.BUTTON1) rotation += dx * 0.012;
                    else if (dragButton == java.awt.event.MouseEvent.BUTTON2 || dragButton == java.awt.event.MouseEvent.BUTTON3) {
                        panX += dx; panY += dy;
                    }
                    lastX = e.getX(); lastY = e.getY(); repaint();
                }
                @Override public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                    zoom = Math.max(0.55, Math.min(2.5, zoom * Math.pow(1.12, -e.getPreciseWheelRotation())));
                    repaint();
                }
            };
            addMouseListener(controls);
            addMouseMotionListener(controls);
            addMouseWheelListener(controls);
        }

        @Override protected void paintComponent(Graphics graphics) {
            Icon icon = getIcon();
            if (!(icon instanceof ImageIcon imageIcon)) { super.paintComponent(graphics); return; }
            Graphics2D g = (Graphics2D) graphics.create();
            LauncherApp.aa(g);
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            g.translate(getWidth() / 2.0 + panX, getHeight() / 2.0 + panY);
            g.rotate(rotation);
            g.scale(zoom, zoom);
            Image image = imageIcon.getImage();
            g.drawImage(image, -image.getWidth(null) / 2, -image.getHeight(null) / 2, null);
            g.dispose();
        }
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
        for (JButton button : actionButtons) button.setEnabled(enabled);
        applyButton.setEnabled(enabled);
        uploadButton.setEnabled(enabled && account != null && account.online);
        username.setEnabled(enabled);
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

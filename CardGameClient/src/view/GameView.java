package view;

import controller.SocketHandler;
import model.Trash;
import run.ClientRun;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.HashMap;

/**
 * GameView extends JFrame with drag-drop mechanics and 4 bins.
 * Scoring: +3 correct bin, -1 wrong bin. No penalty if item falls out.
 * Uses bin images from fixed paths in package `assets` (same level as `view`).
 * NOTE: Structure preserved (static inner GameCanvas).
 */
public class GameView extends JFrame {

    private final GameCanvas canvas;
    private SocketHandler net; // giữ theo cấu trúc cũ (không dùng trong static canvas)
    private boolean forfeitSent = false;

    // [ADD] Catalog rác nhận từ server (nếu có)
    private List<Trash> serverCatalog = new ArrayList<>();

    // Dùng socket mặc định từ ClientRun
    public GameView() { this(ClientRun.socketHandler); }

    public GameView(SocketHandler net) {
        this.net = net;
        setTitle("Game - Phân loại rác");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // [ADD] Lấy danh mục rác từ server (nếu có kết nối)
        try {
            if (this.net != null) {
                java.util.List<Trash> lst = this.net.fetchTrashCatalogSync(3000); // 3s timeout
                if (lst != null && !lst.isEmpty()) {
                    this.serverCatalog = lst;
                    System.out.println("Loaded trash catalog from server: " + lst.size() + " items");
                } else {
                    System.out.println("Server catalog EMPTY or null");
                }
            } else {
                System.out.println("SocketHandler is null -> skip fetch catalog");
            }
        } catch (Exception ex) {
            System.err.println("Fetch trash catalog failed: " + ex.getMessage());
        }

        canvas = new GameCanvas();
        canvas.setServerCatalog(serverCatalog);
        setContentPane(canvas);
        
        JButton btnLeaveGame = new JButton("Thoát trận");
        btnLeaveGame.setBackground(new Color(220, 53, 69)); // đỏ nhẹ
        btnLeaveGame.setForeground(Color.WHITE);
        btnLeaveGame.setFocusPainted(false);
        btnLeaveGame.setFont(btnLeaveGame.getFont().deriveFont(Font.BOLD, 13f));
        btnLeaveGame.setBounds(20, 80, 110, 30); // vị trí góc trái

        // xử lý khi nhấn
        btnLeaveGame.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "Thoát giữa trận sẽ tính bạn THUA. Xác nhận?",
                    "Xác nhận thoát",
                    JOptionPane.YES_NO_OPTION
            );
            if (confirm == JOptionPane.YES_OPTION) {
                try {
                    if (!forfeitSent &&
                        ClientRun.socketHandler != null &&
                        ClientRun.socketHandler.getRoomIdPresent() != null &&
                        ClientRun.socketHandler.getCurrentOpponent() != null) {

                        forfeitSent = true;
                        ClientRun.socketHandler.leaveGame(ClientRun.socketHandler.getCurrentOpponent());
                    }
                } catch (Exception ignored) {}
                ClientRun.closeScene(ClientRun.SceneName.GAMEVIEW);
                if (ClientRun.homeView != null) {
                    ClientRun.homeView.setVisible(true);
                    ClientRun.homeView.toFront();
                }
            }
        });

        // thêm nút vào canvas
        canvas.setLayout(null);
        canvas.add(btnLeaveGame);

        pack();
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(1024, 680));

        // Auto start/stop
        addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent e) { start(); }

            @Override public void windowClosing(WindowEvent e) {
                stop();

                // 🔥 Gửi xử thua nếu đang trong trận
                try {
                    if (!forfeitSent &&
                        ClientRun.socketHandler != null &&
                        ClientRun.socketHandler.getRoomIdPresent() != null &&
                        ClientRun.socketHandler.getCurrentOpponent() != null) {

                        forfeitSent = true; // chỉ gửi 1 lần
                        ClientRun.socketHandler.leaveGame(ClientRun.socketHandler.getCurrentOpponent());
                    }
                } catch (Exception ignored) {}
            }
        });
    }

    public void start() { canvas.start(); forfeitSent = false; }
    public void stop()  { canvas.stop();  }

    // ===== Public API để SocketHandler cập nhật UI =====
    public void setMyName(String name) { canvas.setMyName(name); }
    public void setOpponentName(String name) { canvas.setOpponentName(name); }
    /** Server gửi RESULT_GAME -> overlay hiển thị kết quả */
    public void showServerResult(String result, String myName) { canvas.showServerResult(result, myName); }
    public void updateOpponentScore(int newScore) {
        if (canvas != null) {
            canvas.updateOpponentScore(newScore);
        }
    }
    @Override
    public void dispose() {
        stop();
        super.dispose();
    }

    // =================== Inner Canvas (actual game area) ===================
    private static class GameCanvas extends JPanel {
        // [ADD] Catalog rác từ server (sao chép vào canvas để tránh dùng biến ngoài)
        private List<Trash> serverCatalogRef = new ArrayList<>();
        public void setServerCatalog(List<Trash> list) {
            this.serverCatalogRef = (list == null) ? new ArrayList<>() : list;
        }

        private static final int CANVAS_W = 960;
        private static final int CANVAS_H = 600;
        private static final int ITEM_SIZE = 56;
        private static final int ITEM_VY = 2;
        private static final int MARGIN = 16;
        private static final int BIN_H = 160;

        // ==== ĐƯỜNG DẪN CỐ ĐỊNH CHO 4 THÙNG (đặt ảnh vào package assets) ====
        private static final String PATH_BIN_ORGANIC    = "/assets/HuuCo.png";
        private static final String PATH_BIN_RECYCLABLE = "/assets/TaiChe.png";
        private static final String PATH_BIN_HAZARDOUS  = "/assets/NguyHai.png";
        private static final String PATH_BIN_GENERAL    = "/assets/Khac.png";
        private static final String PATH_BIN_DEFAULT    = "/assets/bin_default.png"; // fallback (nếu có)

        // ==== ẢNH NỀN ====
        private static final String PATH_BG = "/assets/BG1.png";
        private Image bgImage;

        private final Random rand = new Random();
        private final List<Drop> items = new ArrayList<>();
        private final List<Bin> bins = new ArrayList<>();

        private Timer animTimer;
        private Timer spawnTimer;
        private Timer matchTimer;
        private int timeLeft = 60;
        private int nextId = 1;
        private int score = 0;
        private int opponentScoreLive = 0;

        // dragging state
        private Drop dragging = null;
        private int grabDx = 0, grabDy = 0;

        // ===== Bin images =====
        private final Map<Trash.Type, Image> binImages = new EnumMap<>(Trash.Type.class);
        private Image defaultBinImg;

        // ===== Item image cache (để vẽ ảnh rác) =====
        private final Map<String, Image> itemImageCache = new HashMap<>();

        private Image getItemImage(String path) {
            if (path == null || path.isBlank()) return null;
            Image img = itemImageCache.get(path);
            if (img != null) return img;
            img = loadImage(path);
            if (img != null) itemImageCache.put(path, img);
            return img;
        }

        // multiplayer UI state (tên hiển thị)
        private String myName = "Bạn";
        private String opponentName = "Đối thủ";

        // overlay
        private boolean showOverlay = false;
        private String overlayTitle = "";
        private String overlaySubtitle = "";
        private JButton btnConfirm;
        
        public void updateOpponentScore(int newScore) {
            opponentScoreLive = newScore; // opponentScoreLive bạn đã khai báo rồi
            repaint();                    // vẽ lại giao diện
        }


        GameCanvas() {
            setPreferredSize(new Dimension(CANVAS_W, CANVAS_H));
            setBackground(new Color(240, 245, 250));
            setLayout(null);

            buildBins();
            loadBinImages();       // nạp ảnh thùng từ /assets
            loadBackgroundImage(); // nạp ảnh nền
            buildConfirmButton();  // nút xác nhận trên overlay

            // Mouse interactions
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    // pick top-most item under cursor
                    Point p = e.getPoint();
                    for (int i = items.size() - 1; i >= 0; i--) {
                        Drop d = items.get(i);
                        if (d.getBounds().contains(p)) {
                            dragging = d;
                            grabDx = p.x - d.x;
                            grabDy = p.y - d.y;
                            d.dragging = true;
                            break;
                        }
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (dragging != null) {
                        dragging.x = e.getX() - grabDx;
                        dragging.y = e.getY() - grabDy;
                        // clamp
                        if (dragging.x < 0) dragging.x = 0;
                        if (dragging.y < -ITEM_SIZE) dragging.y = -ITEM_SIZE;
                        if (dragging.x + dragging.w > CANVAS_W) dragging.x = CANVAS_W - dragging.w;
                        if (dragging.y + dragging.h > CANVAS_H) dragging.y = CANVAS_H - dragging.h;
                        repaint();
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (dragging != null) {
                        // check drop into a bin
                        Bin target = hitBin(dragging.getBounds());
                        if (target != null) {
                            if (target.type == dragging.trash.getType()) {
                                score += 3;
                            } else {
                                score -= 1;
                            }
                            items.remove(dragging);
                            dragging = null;

                            onLocalScoreChanged(); // (protocol hiện có không dùng live-score)
                            repaint();
                            return;
                        }
                        // not dropped in any bin => resume falling
                        dragging.dragging = false;
                        dragging = null;
                    }
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        // ====== Image Loading ======
        private void loadBinImages() {
            Image imgOrganic    = loadImage(PATH_BIN_ORGANIC);
            Image imgRecyclable = loadImage(PATH_BIN_RECYCLABLE);
            Image imgHazardous  = loadImage(PATH_BIN_HAZARDOUS);
            Image imgGeneral    = loadImage(PATH_BIN_GENERAL);

            if (imgOrganic != null)    binImages.put(Trash.Type.ORGANIC, imgOrganic);
            if (imgRecyclable != null) binImages.put(Trash.Type.RECYCLABLE, imgRecyclable);
            if (imgHazardous != null)  binImages.put(Trash.Type.HAZARDOUS, imgHazardous);
            if (imgGeneral != null)    binImages.put(Trash.Type.GENERAL, imgGeneral);

            defaultBinImg = loadImage(PATH_BIN_DEFAULT); // có thể null
        }

        private void loadBackgroundImage() {
            bgImage = loadImage(PATH_BG);
        }

        private Image loadImage(String path) {
            try {
                URL url = GameView.class.getResource(path);
                if (url == null) {
                    String p = path.startsWith("/") ? path.substring(1) : path;
                    ClassLoader cl = Thread.currentThread().getContextClassLoader();
                    url = (cl != null) ? cl.getResource(p) : ClassLoader.getSystemResource(p);
                }
                if (url != null) return new ImageIcon(url).getImage();
            } catch (Exception ignored) {}
            return null;
        }

        private void buildConfirmButton() {
            btnConfirm = new JButton("Xác nhận");
            btnConfirm.setFocusPainted(false);
            btnConfirm.setFont(btnConfirm.getFont().deriveFont(Font.BOLD, 16f));
            btnConfirm.addActionListener(e -> {
                // chỉ đóng GameView; KHÔNG tạo HomeView mới
                ClientRun.closeScene(ClientRun.SceneName.GAMEVIEW);
                if (ClientRun.homeView != null) {
                    ClientRun.homeView.setVisible(true);
                    ClientRun.homeView.toFront();
                    ClientRun.homeView.requestFocus();
                }
            });
            btnConfirm.setVisible(false);
            add(btnConfirm);
        }

        private void layoutConfirmButton() {
            int panelW = 360;
            int panelH = 180;
            int cx = (getWidth() - panelW) / 2;
            int cy = (getHeight() - panelH) / 2;
            int bw = 140, bh = 40;
            btnConfirm.setBounds(cx + (panelW - bw) / 2, cy + panelH - bh - 20, bw, bh);
        }

        private void buildBins() {
            bins.clear();
            Trash.Type[] types = ensureTypes();
            int binCount = 4;
            int usableW = CANVAS_W - (binCount + 1) * MARGIN;
            int binW = Math.max(120, usableW / binCount);
            int y = CANVAS_H - BIN_H - MARGIN;

            int x = MARGIN;
            for (int i = 0; i < binCount; i++) {
                Trash.Type t = (i < types.length) ? types[i] : Trash.Type.GENERAL;
                bins.add(new Bin(t, new Rectangle(x, y, binW, BIN_H)));
                x += binW + MARGIN;
            }
        }

        private Trash.Type[] ensureTypes() {
            // Provide a stable order of 4 types. If enum has fewer, fill with GENERAL.
            List<Trash.Type> list = new ArrayList<>();
            for (Trash.Type t : Trash.Type.values()) list.add(t);
            // Desired canonical order
            Trash.Type organic = findType(list, "ORGANIC");
            Trash.Type recyclable = findType(list, "RECYCLABLE");
            Trash.Type hazardous = findType(list, "HAZARDOUS");
            Trash.Type general = findType(list, "GENERAL");

            List<Trash.Type> out = new ArrayList<>();
            if (organic != null) out.add(organic);
            if (recyclable != null) out.add(recyclable);
            if (hazardous != null) out.add(hazardous);
            if (general != null) out.add(general);
            while (out.size() < 4) out.add(Trash.Type.GENERAL);
            return out.toArray(new Trash.Type[0]);
        }

        private Trash.Type findType(List<Trash.Type> list, String name) {
            for (Trash.Type t : list) if (t.name().equalsIgnoreCase(name)) return t;
            return null;
        }

        void start() {
            stop();
            timeLeft = 60;
            score = 0;
            items.clear();

            showOverlay = false;
            overlayTitle = "";
            overlaySubtitle = "";
            btnConfirm.setVisible(false);

            animTimer = new Timer(16, e -> {
                tick();
                repaint();
            });

            spawnTimer = new Timer(900, e -> spawnOne());

            matchTimer = new Timer(1000, e -> {
                timeLeft--;
                if (timeLeft <= 0) {
                    timeLeft = 0;
                    onLocalFinished();  // submit kết thúc
                    stopTimersOnly();
                }
                repaint();
            });

            animTimer.start();
            spawnTimer.start();
            matchTimer.start();
        }

        void stop() {
            stopTimersOnly();
        }

        private void stopTimersOnly() {
            if (animTimer != null) animTimer.stop();
            if (spawnTimer != null) spawnTimer.stop(); // FIX: dừng đúng spawnTimer
            if (matchTimer != null) matchTimer.stop();
        }

        private void tick() {
            // update falling items
            for (int i = items.size() - 1; i >= 0; i--) {
                Drop d = items.get(i);
                if (!d.dragging) d.y += d.vy;
                if (d.y > CANVAS_H) {
                    // fell off screen, remove without score change
                    items.remove(i);
                }
            }
        }

        private void spawnOne() {
            // Chỉ spawn khi có danh sách từ server
            if (serverCatalogRef == null || serverCatalogRef.isEmpty()) {
                return; // only spawn when catalog from server is available
            }

            Trash base = serverCatalogRef.get(rand.nextInt(serverCatalogRef.size()));
            int x = 8 + rand.nextInt(Math.max(1, CANVAS_W - ITEM_SIZE - 16));
            Trash t = new Trash(nextId, base.getName(), base.getType(), base.getSpritePath(), x, -ITEM_SIZE);

            // [ADD] Load ảnh cho item dựa trên spritePath và cache lại
            Image img = getItemImage(t.getSpritePath());
            if (img != null) t.setImage(img);

            nextId++;
            items.add(new Drop(t, x, -ITEM_SIZE, ITEM_SIZE, ITEM_SIZE, ITEM_VY + rand.nextInt(2)));
        }

        private Bin hitBin(Rectangle r) {
            for (Bin b : bins) {
                if (b.rect.intersects(r)) return b;
            }
            return null;
        }

        // ====== Hooks theo protocol hiện có ======
        private void onLocalScoreChanged() {
            // gửi điểm hiện tại cho server (đồng bộ cho đối thủ)
            try {
                if (ClientRun.socketHandler != null &&
                    ClientRun.socketHandler.getRoomIdPresent() != null) {

                    ClientRun.socketHandler.sendData(
                        "LIVE_SCORE;" + ClientRun.socketHandler.getLoginUser() + ";" +
                        ClientRun.socketHandler.getRoomIdPresent() + ";" + score
                    );
                }
            } catch (Exception ignored) {}
        }

        private void onLocalFinished() {
            int myFinalScore = score;
            // Gửi điểm cuối lên server (dùng ClientRun.socketHandler vì GameCanvas là static)
            try {
                if (ClientRun.socketHandler != null) {
                    ClientRun.socketHandler.submitScore(myFinalScore);
                }
            } catch (Exception ignored) {}

            // Chờ RESULT_GAME từ server để biết Thắng/Thua/Hòa
            showOverlay = true;
            overlayTitle = "Đang chờ đối thủ...";
            overlaySubtitle = "Điểm của bạn: " + myFinalScore + ". Hệ thống sẽ hiển thị kết quả khi có điểm đối thủ.";
            btnConfirm.setVisible(false);
            layoutConfirmButton();
            repaint();
        }

        // Server báo kết quả cuối cùng (winner / DRAW)
        void showServerResult(String result, String myName) {
            stop(); // dừng anim/spawn/match timer
            showOverlay = true;
            btnConfirm.setVisible(true);

            if ("DRAW".equalsIgnoreCase(result)) {
                overlayTitle = "⚖️ Hòa!";
                overlaySubtitle = "Hai bên hòa điểm.";
            } else if (result != null && result.equalsIgnoreCase(myName)) {
                overlayTitle = "🎉 Bạn Thắng!";
                overlaySubtitle = "Người thắng: " + myName;
            } else {
                overlayTitle = "😢 Bạn Thua!";
                overlaySubtitle = "Người thắng: " + result;
            }

            layoutConfirmButton();
            repaint();
        }

        // ===== Painting =====
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // NỀN: vẽ trước mọi thứ khác
            if (bgImage != null) {
                // kéo giãn vừa khung (đơn giản, nhanh)
                g2.drawImage(bgImage, 0, 0, getWidth(), getHeight(), null);
            }

            // Bins: chỉ vẽ ảnh (KHÔNG vẽ chữ, KHÔNG vẽ viền)
            for (Bin b : bins) {
                Image img = binImages.get(b.type);
                if (img == null) img = defaultBinImg;

                if (img != null) {
                    g2.drawImage(img, b.rect.x, b.rect.y, b.rect.width, b.rect.height, null);
                } else {
                    // fallback nếu thiếu ảnh -> khối màu (cũng không viền/không chữ)
                    g2.setColor(colorForType(b.type).darker());
                    g2.fillRoundRect(b.rect.x, b.rect.y, b.rect.width, b.rect.height, 16, 16);
                }
            }

            // Items (ưu tiên vẽ ảnh; nếu không có ảnh thì fallback khối màu)
            for (Drop d : items) {
    Image img = d.trash.getImage();
    if (img != null) {
        g2.drawImage(img, d.x, d.y, d.w, d.h, null);
    } else {
        g2.setColor(colorForType(d.trash.getType()));
        g2.fillRoundRect(d.x, d.y, d.w, d.h, 10, 10);
    }
}


            // HUD Left (my info)
            g2.setColor(Color.DARK_GRAY);
            g2.setFont(getFont().deriveFont(Font.BOLD, 14f));
            g2.drawString("Time: " + timeLeft + "s", 12, 20);
            g2.drawString(myName + " (Bạn): " + score + " điểm", 12, 40);
            g2.setFont(getFont().deriveFont(Font.PLAIN, 12f));
            g2.drawString("Kéo rác vào đúng thùng (+3). Sai (-1).", 12, 60);

            // Opponent panel (top-right)
            drawOpponentPanel(g2);

            // Overlay at end
            if (showOverlay) {
                drawOverlay(g2);
            }
        }

        private void drawOpponentPanel(Graphics2D g2) {
            int panelW = 240;
            int panelH = 70;
            int x = getWidth() - panelW - 12;
            int y = 12;

            g2.setColor(new Color(255, 255, 255, 220));
            g2.fillRoundRect(x, y, panelW, panelH, 16, 16);
            g2.setColor(new Color(0, 0, 0, 40));
            g2.drawRoundRect(x, y, panelW, panelH, 16, 16);

            g2.setColor(new Color(40,40,40));
            g2.setFont(getFont().deriveFont(Font.BOLD, 14f));
            g2.drawString("Đối thủ: " + opponentName, x + 12, y + 24);

            g2.setFont(getFont().deriveFont(Font.PLAIN, 13f));
            g2.drawString("Điểm hiện tại: " + opponentScoreLive, x + 12, y + 44);
        }

        private void drawOverlay(Graphics2D g2) {
            g2.setColor(new Color(0, 0, 0, 120));
            g2.fillRect(0, 0, getWidth(), getHeight());

            int panelW = 360;
            int panelH = 180;
            int x = (getWidth() - panelW) / 2;
            int y = (getHeight() - panelH) / 2;

            g2.setColor(new Color(255, 255, 255));
            g2.fillRoundRect(x, y, panelW, panelH, 20, 20);
            g2.setColor(new Color(0, 0, 0, 40));
            g2.drawRoundRect(x, y, panelW, panelH, 20, 20);

            g2.setColor(new Color(30,30,30));
            g2.setFont(getFont().deriveFont(Font.BOLD, 22f));
            FontMetrics fm1 = g2.getFontMetrics();
            int t1x = x + (panelW - fm1.stringWidth(overlayTitle)) / 2;
            g2.drawString(overlayTitle, t1x, y + 54);

            g2.setFont(getFont().deriveFont(Font.PLAIN, 14f));
            FontMetrics fm2 = g2.getFontMetrics();
            int t2x = x + (panelW - fm2.stringWidth(overlaySubtitle)) / 2;
            g2.drawString(overlaySubtitle, t2x, y + 84);

            layoutConfirmButton();
        }

        private Color colorForType(Trash.Type t) {
            if (t == null) return new Color(120, 120, 120);
            return switch (t) {
                case ORGANIC    -> new Color(88, 172, 116);  // green
                case RECYCLABLE -> new Color(76, 141, 199);  // blue
                case HAZARDOUS  -> new Color(176, 82, 170);  // purple
                default         -> new Color(130, 130, 130); // gray
            };
        }

        // === inner records ===
        private static class Drop {
            final Trash trash;
            int x, y, w, h, vy;
            boolean dragging = false;
            Drop(Trash t, int x, int y, int w, int h, int vy) {
                this.trash = t;
                this.x = x;
                this.y = y;
                this.w = w;
                this.h = h;
                this.vy = vy;
            }
            Rectangle getBounds() { return new Rectangle(x, y, w, h); }
        }

        private static class Bin {
            final Trash.Type type;
            final Rectangle rect;
            Bin(Trash.Type type, Rectangle rect) {
                this.type = type;
                this.rect = rect;
            }
        }

        // ===== Setters tên để SocketHandler đẩy vào =====
        public void setOpponentName(String name) {
            this.opponentName = (name == null || name.isBlank()) ? "Đối thủ" : name;
            repaint();
        }

        public void setMyName(String name) {
            this.myName = (name == null || name.isBlank()) ? "Bạn" : name;
            repaint();
        }
    }
}










package controller;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import run.ClientRun;
import view.RankView;
import view.RankWinView;
import view.HistoryView;
import model.Trash;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public class SocketHandler {

    Socket s;
    DataInputStream dis;
    DataOutputStream dos;

    String loginUser = null; // lưu tài khoản đăng nhập hiện tại
    String roomIdPresent = null; // lưu room hiện tại
    String currentOpponent = null; // đối thủ hiện tại cho trận đấu phân loại rác
    float score = 0;

    Thread listener = null;

    // ======== [ADD] Trash catalog sync support ========
    private final Object trashLock = new Object();
    private List<Trash> trashCatalogCached = Collections.emptyList();
    // ======== [END ADD] ===============================


    // =============== CONNECT/DISCONNECT ==================
    public String connect(String addr, int port) {
        try {
            InetAddress ip = InetAddress.getByName(addr);
            s = new Socket();
            s.connect(new InetSocketAddress(ip, port), 4000);
            System.out.println("Connected to " + ip + ":" + port + ", localport:" + s.getLocalPort());

            dis = new DataInputStream(s.getInputStream());
            dos = new DataOutputStream(s.getOutputStream());

            if (listener != null && listener.isAlive()) listener.interrupt();
            listener = new Thread(this::listen, "server-listener");
            listener.start();
            return "success";
        } catch (IOException e) {
            return "failed;" + e.getMessage();
        }
    }

    private void listen() {
        boolean running = true;
        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                String received = dis.readUTF();
                if (received == null) break;
                System.out.println("RECEIVED: " + received);
                String type = received.split(";", 2)[0];
                switch (type) {
                    case "LOGIN" -> onReceiveLogin(received);
                    case "REGISTER" -> onReceiveRegister(received);
                    case "GET_LIST_ONLINE" -> onReceiveGetListOnline(received);
                    case "LOGOUT" -> onReceiveLogout(received);
                    case "INVITE_TO_CHAT" -> onReceiveInviteToChat(received);
                    case "GET_INFO_USER" -> onReceiveGetInfoUser(received);
                    case "ACCEPT_MESSAGE" -> onReceiveAcceptMessage(received);
                    case "NOT_ACCEPT_MESSAGE" -> onReceiveNotAcceptMessage(received);
                    case "LEAVE_TO_CHAT" -> onReceiveLeaveChat(received);
                    case "CHAT_MESSAGE" -> onReceiveChatMessage(received);
                    case "INVITE_TO_PLAY" -> onReceiveInviteToPlay(received);
                    case "ACCEPT_PLAY" -> onReceiveAcceptPlay(received);
                    case "NOT_ACCEPT_PLAY" -> onReceiveNotAcceptPlay(received);
                    case "LEAVE_TO_GAME" -> onReceiveLeaveGame(received);
                    case "CHECK_STATUS_USER" -> onReceiveCheckStatusUser(received);
                    case "START_GAME" -> onReceiveStartGame(received);
                    case "RESULT_GAME" -> onReceiveResultGame(received);
                    case "RANK" -> onReceiveRank(received);
                    case "RANKWIN" -> onReceiveRankWin(received);
                    case "HISTORY" -> onReceiveHistory(received);
                    case "TRASH_LIST" -> onReceiveTrashList(received);
                    case "SCORE_UPDATE" -> onReceiveScoreUpdate(received);
                    case "LIVE_SCORE" -> onReceiveLiveScore(received);
                    case "EXIT" -> running = false;
                    default -> System.out.println("Unknown type: " + type);
                }
            }
        } catch (EOFException eof) {
            // server closed
        } catch (IOException ex) {
            Logger.getLogger(SocketHandler.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try { if (s != null) s.close(); } catch (IOException ignored) {}
            try { if (dis != null) dis.close(); } catch (IOException ignored) {}
            try { if (dos != null) dos.close(); } catch (IOException ignored) {}

            // alert if connect interup (on EDT)
            runEDT(() -> {
                JOptionPane.showMessageDialog(null, "Mất kết nối tới server", "Lỗi", JOptionPane.ERROR_MESSAGE);
                ClientRun.closeAllScene();
                ClientRun.openScene(ClientRun.SceneName.CONNECTSERVER);
            });
        }
    }

    
    // ===================== API: fetch trash catalog =====================
    /**
     * Send TRASH_LIST_REQUEST and wait for catalog response up to timeoutMs.
     */
    public java.util.List<Trash> fetchTrashCatalogSync(int timeoutMs) throws java.io.IOException {
        synchronized (trashLock) {
            trashCatalogCached = null; // reset
            sendData("TRASH_LIST_REQUEST");
            long end = System.currentTimeMillis() + Math.max(1, timeoutMs);
            while (trashCatalogCached == null && System.currentTimeMillis() < end) {
                try {
                    trashLock.wait(end - System.currentTimeMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            return (trashCatalogCached != null)
                    ? new java.util.ArrayList<>(trashCatalogCached)
                    : java.util.Collections.emptyList();
        }
    }


    // =============== SEND HELPERS ========================
    public void sendData(String data) {
        try {
            dos.writeUTF(data);
            dos.flush(); // đảm bảo đẩy ngay
        } catch (IOException ex) {
            Logger.getLogger(SocketHandler.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private static void runEDT(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) r.run();
        else SwingUtilities.invokeLater(r);
    }

    // =============== CLIENT → SERVER APIs =================
    public void login(String email, String password) { sendData("LOGIN;" + email + ";" + password); }
    public void register(String email, String password) { sendData("REGISTER;" + email + ";" + password); }
    public void getRank() { sendData("RANK"); }
    public void getRankWin() { sendData("RANKWIN"); }
    public void getHistory(String username) { sendData("HISTORY;" + username); }
    public void logout() { this.loginUser = null; sendData("LOGOUT"); }
    public void close() { sendData("CLOSE"); }
    public void getListOnline() { sendData("GET_LIST_ONLINE"); }
    public void getInfoUser(String username) { sendData("GET_INFO_USER;" + username); }
    public void checkStatusUser(String username) { sendData("CHECK_STATUS_USER;" + username); }
    public void inviteToChat(String userInvited) { sendData("INVITE_TO_CHAT;" + loginUser + ";" + userInvited); }
    public void leaveChat(String userInvited) { sendData("LEAVE_TO_CHAT;" + loginUser + ";" + userInvited); }
    public void sendMessage(String userInvited, String message) {
        String chat = "[" + loginUser + "] : " + message + "\n";
        runEDT(() -> ClientRun.messageView.setContentChat(chat));
        sendData("CHAT_MESSAGE;" + loginUser + ";" + userInvited + ";" + message);
    }
    public void inviteToPlay(String userInvited) { sendData("INVITE_TO_PLAY;" + loginUser + ";" + userInvited); }
    public void leaveGame(String userInvited) { sendData("LEAVE_TO_GAME;" + loginUser + ";" + userInvited + ";" + roomIdPresent); }
    public void startGame(String userInvited) { sendData("START_GAME;" + loginUser + ";" + userInvited + ";" + roomIdPresent); }

    public void submitResult(String competitor, int score) {
        sendData("SUBMIT_RESULT;" + loginUser + ";" + competitor + ";" + roomIdPresent + ";" + score);
    }
    public void submitScore(int finalScore) {
        if (currentOpponent == null || roomIdPresent == null) {
            System.out.println("WARN: missing opponent or room; cannot submit score");
            return;
        }
        sendData("SUBMIT_RESULT;" + loginUser + ";" + currentOpponent + ";" + roomIdPresent + ";" + finalScore);
    }
    public void acceptPlayAgain() { sendData("ASK_PLAY_AGAIN;YES;" + loginUser); }
    public void notAcceptPlayAgain() { sendData("ASK_PLAY_AGAIN;NO;" + loginUser); }

    // =============== SERVER → CLIENT HANDLERS =============
    private void onReceiveLogin(String received) {
        String[] sp = received.split(";");
        String status = sp[1];
        if ("failed".equals(status)) {
            String failedMsg = sp[2];
            runEDT(() -> JOptionPane.showMessageDialog(ClientRun.loginView, failedMsg, "Lỗi", JOptionPane.ERROR_MESSAGE));
        } else if ("success".equals(status)) {
            this.loginUser = sp[2];
            this.score = Float.parseFloat(sp[3]);
            System.out.println(loginUser + " " + score);
            runEDT(() -> {
                ClientRun.closeScene(ClientRun.SceneName.LOGIN);
                ClientRun.openScene(ClientRun.SceneName.HOMEVIEW);
                ClientRun.homeView.setUsername(loginUser);
                ClientRun.homeView.setUserScore(score);
            });
        }
    }

    private void onReceiveRegister(String received) {
        String[] sp = received.split(";");
        String status = sp[1];
        if ("failed".equals(status)) {
            String failedMsg = sp[2];
            runEDT(() -> JOptionPane.showMessageDialog(ClientRun.registerView, failedMsg, "Lỗi", JOptionPane.ERROR_MESSAGE));
        } else if ("success".equals(status)) {
            runEDT(() -> {
                JOptionPane.showMessageDialog(ClientRun.registerView, "Đăng ký thành công! Mời đăng nhập!");
                ClientRun.closeScene(ClientRun.SceneName.REGISTER);
                ClientRun.openScene(ClientRun.SceneName.LOGIN);
            });
        }
    }

    private void onReceiveInviteToChat(String received) {
        String[] sp = received.split(";");
        String status = sp[1];
        if ("success".equals(status)) {
            String userHost = sp[2];
            String userInvited = sp[3];
            runEDT(() -> {
                int ok = JOptionPane.showConfirmDialog(ClientRun.homeView, userHost + " muốn chat với bạn", "Chat?", JOptionPane.YES_NO_OPTION);
                if (ok == JOptionPane.YES_OPTION) {
                    ClientRun.openScene(ClientRun.SceneName.MESSAGEVIEW);
                    ClientRun.messageView.setInfoUserChat(userHost);
                    sendData("ACCEPT_MESSAGE;" + userHost + ";" + userInvited);
                } else {
                    sendData("NOT_ACCEPT_MESSAGE;" + userHost + ";" + userInvited);
                }
            });
        }
    }

    private void onReceiveAcceptMessage(String received) {
        String[] sp = received.split(";");
        String status = sp[1];
        if ("success".equals(status)) {
            String userInvited = sp[3];
            runEDT(() -> {
                ClientRun.openScene(ClientRun.SceneName.MESSAGEVIEW);
                ClientRun.messageView.setInfoUserChat(userInvited);
            });
        }
    }

    private void onReceiveNotAcceptMessage(String received) {
        String[] sp = received.split(";");
        String status = sp[1];
        if ("success".equals(status)) {
            String userInvited = sp[3];
            runEDT(() -> JOptionPane.showMessageDialog(ClientRun.homeView, userInvited + " không muốn chat với bạn!"));
        }
    }

    private void onReceiveLeaveChat(String received) {
        String[] sp = received.split(";");
        String status = sp[1];
        if ("success".equals(status)) {
            String userHost = sp[2];
            runEDT(() -> {
                ClientRun.closeScene(ClientRun.SceneName.MESSAGEVIEW);
                JOptionPane.showMessageDialog(ClientRun.homeView, userHost + " đã rời khỏi mục chat!");
            });
        }
    }

    private void onReceiveChatMessage(String received) {
        String[] sp = received.split(";");
        String status = sp[1];
        if ("success".equals(status)) {
            String userHost = sp[2];
            String message = sp[4];
            String chat = "[" + userHost + "] : " + message + "\n";
            runEDT(() -> ClientRun.messageView.setContentChat(chat));
        }
    }

    private void onReceiveGetListOnline(String received) {
        String[] sp = received.split(";");
        String status = sp[1];
        if ("success".equals(status)) {
            int userCount = Integer.parseInt(sp[2]);
            Vector vheader = new Vector();
            vheader.add("Người chơi");
            vheader.add("Điểm");
            Vector vdata = new Vector();
            if (userCount > 1) {
                for (int i = 3; i < userCount + 4; i += 2) {
                    String user = sp[i];
                    float s = Float.parseFloat(sp[i + 1]);
                    if (!user.equals(loginUser) && !user.equals("null")) {
                        Vector row = new Vector();
                        row.add(user);
                        row.add(s);
                        vdata.add(row);
                    }
                }
                runEDT(() -> ClientRun.homeView.setListUser(vdata, vheader));
            } else {
                runEDT(() -> ClientRun.homeView.resetTblUser());
            }
        } else {
            runEDT(() -> JOptionPane.showMessageDialog(ClientRun.loginView, "Đã có lỗi!", "Lỗi", JOptionPane.ERROR_MESSAGE));
        }
    }

    private void onReceiveHistory(String received) {
        StringBuilder historyDisplay = new StringBuilder();
        String[] data = received.split(";");
        if (data.length > 0 && "success".equals(data[1])) {
            for (int i = 2; i < data.length; i += 3) {
                if (i + 2 < data.length) {
                    String opponent = data[i];
                    String date = data[i + 1];
                    String result = data[i + 2];
                    historyDisplay.append("Match with ").append(opponent)
                            .append(" on ").append(date).append(": ")
                            .append("thang".equals(result) ? "Thắng" : "Thua")
                            .append("\n");
                }
            }
        } else {
            historyDisplay.append("No game history found for this user.");
        }
        runEDT(() -> {
            ClientRun.historyView = new HistoryView();
            ClientRun.historyView.updateHistoryDisplay(historyDisplay.toString());
            ClientRun.historyView.setVisible(true);
        });
    }

    private void onReceiveRank(String received) {
        String[] data = received.split(";");
        StringBuilder sb = new StringBuilder();
        for (String d : data) sb.append(d).append(";");
        String payload = sb.toString();
        runEDT(() -> {
            if (ClientRun.rankView != null) ClientRun.rankView.updateRankDisplay(payload);
            ClientRun.rankView = new RankView();
            ClientRun.rankView.updateRankDisplay(payload);
            ClientRun.rankView.setVisible(true);
        });
    }

    private void onReceiveRankWin(String received) {
        String[] data = received.split(";");
        StringBuilder sb = new StringBuilder();
        for (String d : data) sb.append(d).append(";");
        String payload = sb.toString();
        runEDT(() -> {
            if (ClientRun.rankWinView != null) ClientRun.rankWinView.updateRankDisplay(payload);
            ClientRun.rankWinView = new RankWinView();
            ClientRun.rankWinView.updateRankDisplay(payload);
            ClientRun.rankWinView.setVisible(true);
        });
    }

    private void onReceiveLogout(String received) {
        String[] sp = received.split(";");
        String status = sp[1];
        if ("success".equals(status)) {
            runEDT(() -> {
                ClientRun.closeScene(ClientRun.SceneName.HOMEVIEW);
                ClientRun.openScene(ClientRun.SceneName.LOGIN);
            });
        }
    }

    private void onReceiveInviteToPlay(String received) {
        String[] sp = received.split(";");
        String status = sp[1];
        if ("success".equals(status)) {
            String userHost = sp[2];
            String userInvited = sp[3];
            String roomId = sp[4];
            currentOpponent = userHost;
            roomIdPresent = roomId;
            runEDT(() -> {
                int ok = JOptionPane.showConfirmDialog(
                        ClientRun.homeView,
                        "Người chơi " + userHost + " mời bạn vào trận. Chơi ngay?",
                        "Mời chơi",
                        JOptionPane.YES_NO_OPTION
                );
                if (ok == JOptionPane.YES_OPTION) {
                    ClientRun.openScene(ClientRun.SceneName.GAMEVIEW);
                    // đẩy tên vào HUD GameView
                    pushNamesToGameView();
                    sendData("ACCEPT_PLAY;" + userHost + ";" + userInvited + ";" + roomId);
                } else {
                    sendData("NOT_ACCEPT_PLAY;" + userHost + ";" + userInvited + ";" + roomId);
                }
            });
        }
    }

    private void onReceiveGetInfoUser(String received) {
        String[] sp = received.split(";");
        String status = sp[1];
        if ("success".equals(status)) {
            String userName = sp[2];
            String userScore = sp[3];
            String userWin = sp[4];
            String userDraw = sp[5];
            String userLose = sp[6];
            String userStatus = sp[7];
            runEDT(() -> {
                ClientRun.openScene(ClientRun.SceneName.INFOPLAYER);
                ClientRun.infoPlayerView.setInfoUser(userName, userScore, userWin, userDraw, userLose, userStatus);
            });
        }
    }

    private void onReceiveCheckStatusUser(String received) {
        String[] sp = received.split(";");
        String status = sp[2];
        runEDT(() -> ClientRun.homeView.setStatusCompetitor(status));
    }

    private void onReceiveAcceptPlay(String received) {
        String[] sp = received.split(";");
        String status = sp[1];
        if ("success".equals(status)) {
            String userHost = sp[2];
            String userInvited = sp[3];
            String roomId = sp[4];

            this.roomIdPresent = roomId;
            this.currentOpponent = userInvited;

            runEDT(() -> {
                ClientRun.openScene(ClientRun.SceneName.GAMEVIEW);
                pushNamesToGameView();

                // ✅ Nếu tôi là người mời (host), tự động gửi START_GAME
                if (loginUser != null && loginUser.equals(userHost)) {
                    System.out.println("🚀 Auto send START_GAME from host " + loginUser);
                    sendData("START_GAME;" + userHost + ";" + userInvited + ";" + roomId);
                }
            });
        }
}

    private void onReceiveNotAcceptPlay(String received) {
        String[] sp = received.split(";");
        String status = sp[1];
        if ("success".equals(status)) {
            String userInvited = sp[3];
            runEDT(() -> JOptionPane.showMessageDialog(ClientRun.homeView, userInvited + " không muốn chơi với bạn!"));
        }
    }

    private void onReceiveLeaveGame(String received) {
        String[] sp = received.split(";");
        String status = sp[1];
        if ("success".equals(status)) {
            String user1 = sp[2];
            roomIdPresent = null;
            runEDT(() -> {
                ClientRun.closeScene(ClientRun.SceneName.GAMEVIEW);
                JOptionPane.showMessageDialog(ClientRun.homeView, user1 + " đã thoát game!\n Bạn thắng! 🎉");
            });
        }
    }

    private void onReceiveStartGame(String received) {
        String[] sp = received.split(";");
        String status = sp[1];
        if ("success".equals(status)) {
            String host = sp.length > 2 ? sp[2] : null;
            String guest = sp.length > 3 ? sp[3] : null;

            // đồng bộ đối thủ nếu thiếu
            if (loginUser != null) {
                if (loginUser.equals(host)) currentOpponent = guest;
                else if (loginUser.equals(guest)) currentOpponent = host;
            }

            runEDT(() -> {
                pushNamesToGameView();
                if (ClientRun.gameView != null) ClientRun.gameView.start();
            });
        }
    }

    private void onReceiveResultGame(String received) {
        String[] sp = received.split(";");
        String status = sp[1];
        String result = sp[2]; // DRAW hoặc username người thắng
        if ("success".equals(status)) {
            runEDT(() -> {
                if (ClientRun.gameView != null) {
                    // Hiển thị overlay trong GameView
                    ClientRun.gameView.showServerResult(result, loginUser);
                }
                // Giữ lại thông báo cũ để KHÔNG mất chức năng
                String msg;
                if ("DRAW".equals(result)) msg = "Hòa điểm.";
                else if (result.equals(loginUser)) msg = "Bạn THẮNG!";
                else msg = "Bạn THUA.";
                JOptionPane.showMessageDialog(ClientRun.homeView, msg, "Kết quả", JOptionPane.INFORMATION_MESSAGE);
            });
        }
    }

    private void onReceiveAskPlayAgain(String received) {
        // TODO: implement if server sends ASK_PLAY_AGAIN
    }
    
    private void onReceiveScoreUpdate(String received) {
        String[] sp = received.split(";");
        // format: SCORE_UPDATE;success;username;score
        if (sp.length >= 4 && "success".equals(sp[1])) {
            String user = sp[2];
            float newScore = Float.parseFloat(sp[3]);

            // nếu là mình -> cập nhật label
            if (loginUser != null && loginUser.equals(user)) {
                this.score = newScore;
                runEDT(() -> ClientRun.homeView.setUserScore(newScore));
            }

            // gọi refresh bảng người chơi cho tiện
            getListOnline();
        }
    }
    
    private void onReceiveLiveScore(String received) {
        String[] sp = received.split(";");
        if (sp.length < 4) return;
        String status = sp[1];
        if (!"success".equals(status)) return;

        String player = sp[2];
        int newScore = Integer.parseInt(sp[3]);

        // Nếu đối thủ cập nhật điểm thì hiển thị
        if (ClientRun.gameView != null &&
            !player.equals(loginUser)) {
            ClientRun.gameView.updateOpponentScore(newScore);
        }
    }

    // ====== Helper: đẩy tên người chơi vào HUD GameView ======
    private void pushNamesToGameView() {
        if (ClientRun.gameView != null) {
            ClientRun.gameView.setMyName(loginUser != null ? loginUser : "Bạn");
            ClientRun.gameView.setOpponentName(currentOpponent != null ? currentOpponent : "Đối thủ");
        }
    }

    // =============== GET/SET ==============================
    public String getLoginUser() { return loginUser; }
    public void setLoginUser(String loginUser) { this.loginUser = loginUser; }
    public Socket getS() { return s; }
    public void setS(Socket s) { this.s = s; }
    public String getRoomIdPresent() { return roomIdPresent; }
    public void setRoomIdPresent(String roomIdPresent) { this.roomIdPresent = roomIdPresent; }
    public float getScore() { return score; }
    public void setScore(float score) { this.score = score; }

    // ===================== Handler: TRASH_LIST response =====================
    // Format: "TRASH_LIST;OK;id,name,category,image|..."
    private void onReceiveTrashList(String received) {
        String[] sp = received.split(";", 3);
        if (sp.length < 2) return;

        if (!"OK".equals(sp[1])) {
            synchronized (trashLock) {
                trashCatalogCached = java.util.Collections.emptyList();
                trashLock.notifyAll();
            }
            return;
        }

        String payload = (sp.length >= 3) ? sp[2] : "";
        java.util.List<Trash> out = new java.util.ArrayList<>();
        if (!payload.isEmpty()) {
            String[] rows = payload.split("\\|");
            for (String row : rows) {
                String[] f = row.split(",", -1); // id, name, category, image
                if (f.length < 4) continue;
                try {
                    int id = Integer.parseInt(f[0]);
                    String name = f[1];
                    Trash.Type type = Trash.parseType(f[2]);
                    String spritePath = resolveSpritePath(f[3]);
                    out.add(new Trash(id, name, type, spritePath, 0, 0));
                } catch (Exception ignored) {}
            }
        }
        synchronized (trashLock) {
            trashCatalogCached = out;
            trashLock.notifyAll();
        }
    }

    private static String resolveSpritePath(String imageOrKey) {
        if (imageOrKey == null || imageOrKey.isBlank()) return "/assets/trash/default.png";
        if (imageOrKey.startsWith("/")) return imageOrKey;
        return "/assets/trash/" + imageOrKey + ".png";
    }
    
    public String getCurrentOpponent() {
        return currentOpponent;
    }

    public void setCurrentOpponent(String opponent) {
        this.currentOpponent = opponent;
    }

}



/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package service;

import controller.UserController;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import static java.lang.Math.random;
import java.net.Socket;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Random;
import run.ServerRun;

/**
 *
 * @author admin
 */
public class Client implements Runnable {

    Socket s;
    DataInputStream dis;
    DataOutputStream dos;
    Random random = new Random();
    String loginUser;
    Client cCompetitor;
    float score = 0;
    int player_finished = 0;

    Room joinedRoom;

    public Client(Socket s) throws IOException {
        this.s = s;

        // obtaining input and output streams 
        this.dis = new DataInputStream(s.getInputStream());
        this.dos = new DataOutputStream(s.getOutputStream());
    }

    @Override
    public void run() {

        String received;
        boolean running = true;
        while (!ServerRun.isShutDown) {
            try {
                // receive the request from client
                received = dis.readUTF();

                System.out.println(received);
                String type = received.split(";")[0];

                switch (type) {
                    case "LOGIN":
                        onReceiveLogin(received);
                        break;
                    case "REGISTER":
                        onReceiveRegister(received);
                        break;
                    case "GET_LIST_ONLINE":
                        onReceiveGetListOnline();
                        break;
                    case "GET_INFO_USER":
                        onReceiveGetInfoUser(received);
                        break;
                    case "INVITE_TO_CHAT":
                        onReceiveInviteToChat(received);
                        break;
                    case "ACCEPT_MESSAGE":
                        onReceiveAcceptMessage(received);
                        break;
                    case "NOT_ACCEPT_MESSAGE":
                        onReceiveNotAcceptMessage(received);
                        break;
                    case "LEAVE_TO_CHAT":
                        onReceiveLeaveChat(received);
                        break;
                    case "CHAT_MESSAGE":
                        onReceiveChatMessage(received);
                        break;
                    case "RANK":
                        onReceiveRank();
                        break;
                    case "RANKWIN":
                        onReceiveRankWin();
                        break;
                    case "HISTORY":
                        onReceiveHistory(received);
                        break;
                    case "LOGOUT":
                        onReceiveLogout();
                        break;
                    case "CHECK_STATUS_USER":
                        onReceiveCheckStatusUser(received);
                        break;
                    case "INVITE_TO_PLAY":
                        onReceiveInviteToPlay(received);
                        break;
                    case "ACCEPT_PLAY":
                        onReceiveAcceptPlay(received);
                        break;
                    case "NOT_ACCEPT_PLAY":
                        onReceiveNotAcceptPlay(received);
                        break;
                    case "LEAVE_TO_GAME":
                        onReceiveLeaveGame(received);
                        break;
                    case "START_GAME":
                        onReceiveStartGame(received);
                        break;
                    case "SUBMIT_RESULT":
                        onReceiveSubmitResult(received);
                        break;
                    case "ASK_PLAY_AGAIN":
                        onReceiveAskPlayAgain(received);
                        break;
                    case "PLAYER_FINISHED":
                        onReceivePlayerFinished(received);
                        break;
                    case "TRASH_LIST_REQUEST":
                        onReceiveTrashListRequest();
                        break;

                    case "EXIT":
                        running = false;
                }
            } catch (IOException ex) {
                System.out.println(ex);
                break;
            } catch (SQLException ex) {
                Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
            }

        }

        try {
            // closing resources 
            this.s.close();
            this.dis.close();
            this.dos.close();
            System.out.println("- Client disconnected: " + s);

            // remove from clientManager
            ServerRun.clientManager.remove(this);

        } catch (IOException ex) {
            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    // send data fucntions
    public String sendData(String data) {
        try {
            this.dos.writeUTF(data);
            return "success";
        } catch (IOException e) {
            System.err.println("Send data failed!");
            return "failed;" + e.getMessage();
        }
    }

    private void onReceiveLogin(String received) {
        // get email / password from data
        String[] splitted = received.split(";");
        String username = splitted[1];
        String password = splitted[2];
        // check login
        String result = new UserController().login(username, password);

        if (result.split(";")[0].equals("success")) {
            // set login user
            this.loginUser = username;
            this.score = Float.parseFloat(result.split(";")[2]);
        }

        // send result
        sendData("LOGIN" + ";" + result);
    }

    private void onReceiveRegister(String received) {
        // get email / password from data
        String[] splitted = received.split(";");
        String username = splitted[1];
        String password = splitted[2];

        // reigster
        String result = new UserController().register(username, password);

        // send result
        sendData("REGISTER" + ";" + result);
    }

    private void onReceiveGetListOnline() {
        String result = ServerRun.clientManager.getListUseOnline();

        // send result
        String msg = "GET_LIST_ONLINE" + ";" + result;
        ServerRun.clientManager.broadcast(msg);
    }

    private void onReceiveHistory(String received) {
        String[] splitted = received.split(";");
        String username = splitted[1];

        String result = new UserController().getHistory(username);

        sendData("HISTORY;" + result);
    }

    private void onReceiveGetInfoUser(String received) {
        String[] splitted = received.split(";");
        String username = splitted[1];

        String result = new UserController().getInfoUser(username);

        String status = "";
        Client c = ServerRun.clientManager.find(username);
        if (c == null) {
            status = "Offline";
        } else {
            if (c.getJoinedRoom() == null) {
                status = "Online";
            } else {
                status = "In Game";
            }
        }
        sendData("GET_INFO_USER" + ";" + result + ";" + status);
    }

    private void onReceiveInviteToChat(String received) {
        String[] splitted = received.split(";");
        String userHost = splitted[1];
        String userInvited = splitted[2];

        // send result
        String msg = "INVITE_TO_CHAT;" + "success;" + userHost + ";" + userInvited;
        ServerRun.clientManager.sendToAClient(userInvited, msg);
    }

    private void onReceiveAcceptMessage(String received) {
        String[] splitted = received.split(";");
        String userHost = splitted[1];
        String userInvited = splitted[2];

        // send result
        String msg = "ACCEPT_MESSAGE;" + "success;" + userHost + ";" + userInvited;
        ServerRun.clientManager.sendToAClient(userHost, msg);
    }

    private void onReceiveNotAcceptMessage(String received) {
        String[] splitted = received.split(";");
        String userHost = splitted[1];
        String userInvited = splitted[2];

        // send result
        String msg = "NOT_ACCEPT_MESSAGE;" + "success;" + userHost + ";" + userInvited;
        ServerRun.clientManager.sendToAClient(userHost, msg);
    }

    private void onReceiveLeaveChat(String received) {
        String[] splitted = received.split(";");
        String userHost = splitted[1];
        String userInvited = splitted[2];

        // send result
        String msg = "LEAVE_TO_CHAT;" + "success;" + userHost + ";" + userInvited;
        ServerRun.clientManager.sendToAClient(userInvited, msg);
    }

    private void onReceiveChatMessage(String received) {
        String[] splitted = received.split(";");
        String userHost = splitted[1];
        String userInvited = splitted[2];
        String message = splitted[3];

        // send result
        String msg = "CHAT_MESSAGE;" + "success;" + userHost + ";" + userInvited + ";" + message;
        ServerRun.clientManager.sendToAClient(userInvited, msg);
    }

    private void onReceiveLogout() {
        this.loginUser = null;
        // send result
        sendData("LOGOUT" + ";" + "success");
        onReceiveGetListOnline();
    }

    private void onReceiveCheckStatusUser(String received) {
        String[] splitted = received.split(";");
        String username = splitted[1];

        String status = "";
        Client c = ServerRun.clientManager.find(username);
        if (c == null) {
            status = "OFFLINE";
        } else {
            if (c.getJoinedRoom() == null) {
                status = "ONLINE";
            } else {
                status = "INGAME";
            }
        }
        // send result
        sendData("CHECK_STATUS_USER" + ";" + username + ";" + status);
    }

    private void onReceiveInviteToPlay(String received) {
    String[] sp = received.split(";");
    String userHost = sp[1];
    String userInvited = sp[2];

    // 1) Tạo room nếu host chưa có
    if (this.joinedRoom == null) {
        // Tạo id ngẫu nhiên
        String roomId = java.util.UUID.randomUUID().toString();
        Room room = new Room(roomId);             // <- tùy constructor Room của bạn
        ServerRun.roomManager.add(room);          // <- hoặc put(room), register(room) tùy API bạn
        this.joinedRoom = room;
    }

    // 2) Đảm bảo host đã ở trong phòng
    try {
        this.joinedRoom.addClient(this);
    } catch (Exception ignore) {
        // nếu room đã có host thì bỏ qua
    }

    // 3) Ghi nhận đối thủ (để dùng sau)
    this.cCompetitor = ServerRun.clientManager.find(userInvited);

    // 4) Gửi invite + roomId cho người được mời
    String msg = "INVITE_TO_PLAY;" + "success;" + userHost + ";" + userInvited + ";" + this.joinedRoom.getId();
    ServerRun.clientManager.sendToAClient(userInvited, msg);
}

    private void onReceiveAcceptPlay(String received) {
        String[] sp = received.split(";");
        String userHost = sp[1];
        String userInvited = sp[2];
        String roomId = sp[3];

        Room room = ServerRun.roomManager.find(roomId);
        if (room == null) {
            System.out.println("[ERROR] Room not found: " + roomId);
            return;
        }

        // Thêm client2 (người được mời)
        this.joinedRoom = room;
        room.addClient(this);

        // Ghi lại competitor
        this.cCompetitor = ServerRun.clientManager.find(userHost);

        // Ghi log debug
        System.out.println("[ROOM] " + userHost + " (host) vs " + userInvited + " (guest) joined room " + roomId);
        System.out.println("[DEBUG] client1=" + (room.getClient1() != null ? room.getClient1().getLoginUser() : "null")
                         + ", client2=" + (room.getClient2() != null ? room.getClient2().getLoginUser() : "null"));

        // Gửi lại cho host
        String msg = "ACCEPT_PLAY;success;" + userHost + ";" + userInvited + ";" + room.getId();
        ServerRun.clientManager.sendToAClient(userHost, msg);
    }

    private void onReceiveNotAcceptPlay(String received) {
        String[] splitted = received.split(";");
        String userHost = splitted[1];
        String userInvited = splitted[2];
        String roomId = splitted.length > 3 ? splitted[3] : null;

        // userHost out room
        Client hostClient = ServerRun.clientManager.find(userHost);
        if (hostClient != null) {
            hostClient.setJoinedRoom(null);
            hostClient.setcCompetitor(null);
        }

        // tìm room an toàn
        Room room = (roomId != null) ? ServerRun.roomManager.find(roomId) : null;
        if (room != null) {
            ServerRun.roomManager.remove(room);
        }

        // send result
        String msg = "NOT_ACCEPT_PLAY;" + "success;" + userHost + ";" + userInvited + ";" 
                     + (room != null ? room.getId() : "no-room");
        ServerRun.clientManager.sendToAClient(userHost, msg);
    }


    private void onReceiveLeaveGame(String received) throws SQLException {
        String[] splitted = received.split(";");
        String user1 = splitted[1];
        String user2 = splitted[2];
        String roomId = splitted[3];

        joinedRoom.userLeaveGame(user1);

        this.cCompetitor = null;
        this.joinedRoom = null;

        // delete room
        Room room = ServerRun.roomManager.find(roomId);
        ServerRun.roomManager.remove(room);

        // userHost out room
        Client c = ServerRun.clientManager.find(user2);
        c.setJoinedRoom(null);
        // Delete competitor of userhost
        c.setcCompetitor(null);

        // send result
        String msg = "LEAVE_TO_GAME;" + "success;" + user1 + ";" + user2;
        ServerRun.clientManager.sendToAClient(user2, msg);
    }

    private void onReceiveStartGame(String received) {
        String[] splitted = received.split(";");
        String user1 = splitted[1];
        String user2 = splitted[2];
        String roomId = splitted[3];

        // Build START_GAME message — không cần substring
        String data = "START_GAME;success;" + user1 + ";" + user2 + ";" + roomId;

        // Reset room state và gửi cho cả 2
        if (joinedRoom != null) {
            joinedRoom.resetRoom();
            joinedRoom.broadcast(data);
            joinedRoom.startGame();
            System.out.println("[START_GAME] room=" + roomId + " players=" 
                + joinedRoom.getClient1().getLoginUser() + ", " + joinedRoom.getClient2().getLoginUser());
        } else {
            System.out.println("[ERROR] onReceiveStartGame: joinedRoom is null for " + loginUser);
        }
    }

    private void onReceiveSubmitResult(String received) throws SQLException {
        String[] sp = received.split(";");
        String user1 = sp[1];
        String user2 = sp[2];
        String roomId = sp[3];
        String playerScore = sp[4];

        Room r = ServerRun.roomManager.find(roomId);
        if (r == null) {
            System.out.println("[ERROR] Room not found: " + roomId);
            return;
        }

        // Lưu điểm cho người gửi
        if (user1.equals(r.getClient1().getLoginUser())) {
            r.setResultClient1(playerScore);
        } else if (user1.equals(r.getClient2().getLoginUser())) {
            r.setResultClient2(playerScore);
        }

        System.out.println("[DEBUG] SubmitResult from " + user1 +
            " -> result1=" + r.getResultClient1() +
            ", result2=" + r.getResultClient2());

        // ✅ CHỈ gửi RESULT_GAME khi cả 2 đều có điểm
        if (r.getResultClient1() != null && r.getResultClient2() != null) {
            String winner = r.handleResultClient();
            String data = "RESULT_GAME;success;" + winner + ";"
                    + r.getClient1().getLoginUser() + ";" + r.getClient2().getLoginUser() + ";" + r.getId();
            System.out.println(data);
            r.broadcast(data);
            r.waitingClientTimer();
        } else {
            System.out.println("[WAIT] Waiting for the other player’s result...");
        }
    }

    private void onReceiveAskPlayAgain(String received) throws SQLException {
        String[] splitted = received.split(";");
        String reply = splitted[1];
        String user1 = splitted[2];

        System.out.println("client1: " + joinedRoom.getClient1().getLoginUser());
        System.out.println("client2: " + joinedRoom.getClient2().getLoginUser());

        if (user1.equals(joinedRoom.getClient1().getLoginUser())) {
            joinedRoom.setPlayAgainC1(reply);
        } else if (user1.equals(joinedRoom.getClient2().getLoginUser())) {
            joinedRoom.setPlayAgainC2(reply);
        }

        while (!joinedRoom.getWaitingTime().equals("00:00")) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        String result = this.joinedRoom.handlePlayAgain();
        if (result.equals("YES")) {
            joinedRoom.broadcast("ASK_PLAY_AGAIN;YES;" + joinedRoom.getClient1().loginUser + ";" + joinedRoom.getClient2().loginUser);
        } else if (result.equals("NO")) {
            joinedRoom.broadcast("ASK_PLAY_AGAIN;NO;");

            Room room = ServerRun.roomManager.find(joinedRoom.getId());
            // delete room            
            ServerRun.roomManager.remove(room);
            this.joinedRoom = null;
            this.cCompetitor = null;
        } else if (result == null) {
            System.out.println("da co loi xay ra huhu");
        }
    }
    private void onReceiveTrashListRequest() {
    try {
        java.sql.Connection conn = connection.DatabaseConnection.getConnection();
        dao.TrashDAO dao = new dao.TrashDAO(conn);
        java.util.List<model.TrashModel> items = dao.listAll();

        StringBuilder sb = new StringBuilder("TRASH_LIST;OK;");
        for (int i = 0; i < items.size(); i++) {
            model.TrashModel t = items.get(i);
            if (i > 0) sb.append('|');
            sb.append(t.getTrashId()).append(',')
              .append(t.getTrashName()).append(',')
              .append(t.getCategory()).append(',')
              .append(t.getImage() == null ? "" : t.getImage());
        }
        sendData(sb.toString());
    } catch (Exception ex) {
        sendData("TRASH_LIST;FAIL;" + ex.getMessage());
    }
}



    // Get set
    public String getLoginUser() {
        return loginUser;
    }

    public void setLoginUser(String loginUser) {
        this.loginUser = loginUser;
    }

    public float getScore() {
        return score;
    }

    public void setScore(float score) {
        this.score = score;
    }

    private void onReceiveRank() {
        String result = new UserController().getRank();
        sendData(result);
    }

    private void onReceiveRankWin() {
        String result = new UserController().getRankWin();
        sendData(result);
    }

    public Client getcCompetitor() {
        return cCompetitor;
    }

    public void setcCompetitor(Client cCompetitor) {
        this.cCompetitor = cCompetitor;
    }

    public Room getJoinedRoom() {
        return joinedRoom;
    }

    public void setJoinedRoom(Room joinedRoom) {
        this.joinedRoom = joinedRoom;
    }

    private void onReceivePlayerFinished(String received) {
        player_finished += 1;

    }
}

package service;

import java.util.ArrayList;

/**
 *
 * @author admin
 */
public class RoomManager {
    ArrayList<Room> rooms;


    public RoomManager() {
        rooms = new ArrayList<>();
    }



    public boolean add(Room r) {
        if (!rooms.contains(r)) {
            rooms.add(r);
            return true;
        }
        return true;
    }

    public boolean remove(Room r) {
        if (rooms.contains(r)) {
            rooms.remove(r);
            return true;
        }
        return false;
    }

    public Room find(String id) {
        for (Room r : rooms) {
            if (r.getId().equals(id)) {
                return r;
            }
        }
        return null;
    }

    public int getSize() {
        return rooms.size();
    }

    public ArrayList<Room> getRooms() {
        return rooms;
    }
}
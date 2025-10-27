// server/src/dao/TrashDAO.java
package dao;

import model.TrashModel;
import java.sql.*;
import java.util.*;

public class TrashDAO {
    private final Connection conn;
    public TrashDAO(Connection conn) { this.conn = conn; }

    public List<TrashModel> listAll() throws SQLException {
        String sql = "SELECT id, trash_name, category, image FROM trash";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<TrashModel> list = new ArrayList<>();
            while (rs.next()) {
                TrashModel t = new TrashModel(
                    rs.getInt("id"),
                    rs.getString("trash_name"),
                    rs.getString("category"),   // là String, bạn đảm bảo đúng 1 trong 4
                    rs.getString("image")       // spriteKey hoặc path
                );
                list.add(t);
            }
            return list;
        }
    }
}

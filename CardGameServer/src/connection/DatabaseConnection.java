package connection;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * DatabaseConnection
 * - Hỗ trợ cả:
 *     1) DatabaseConnection.getConnection()          // static (tiện cho chỗ bạn đang gọi)
 *     2) DatabaseConnection.getInstance().getConnection() // instance (tương thích ngược)
 */
public class DatabaseConnection {

    // Khuyến nghị cấu hình JDBC URL thêm timezone + unicode
    private static final String JDBC_URL =
            "jdbc:mysql://localhost:3306/game_db"
            + "?useSSL=false&allowPublicKeyRetrieval=true"
            + "&serverTimezone=Asia/Bangkok"
            + "&useUnicode=true&characterEncoding=utf8";
    private static final String JDBC_USERNAME = "root";
    private static final String JDBC_PASSWORD = "123456";
    private static final String DRIVER = "com.mysql.cj.jdbc.Driver";

    private static DatabaseConnection instance;
    private Connection connection;

    public static synchronized DatabaseConnection getInstance() {
        if (instance == null) instance = new DatabaseConnection();
        return instance;
    }

    private DatabaseConnection() {
        // load driver 1 lần
        try {
            Class.forName(DRIVER);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("MySQL Driver not found: " + DRIVER, e);
        }
    }

    /**
     * Static helper để gọi nhanh: DatabaseConnection.getConnection()
     */
    public static Connection getConnection() {
        return getInstance().getConnectionInternal();
    }

    /**
     * Instance getter cũ: DatabaseConnection.getInstance().getConnection()
     */
 

    private synchronized Connection getConnectionInternal() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(JDBC_URL, JDBC_USERNAME, JDBC_PASSWORD);
                System.out.println("Connected to Database.");
            }
            return connection;
        } catch (SQLException e) {
            throw new RuntimeException("Cannot obtain MySQL connection", e);
        }
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }
}


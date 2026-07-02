package Bot;

import java.sql.*;
public class DBConnection {
	public static Connection connect() {
	String jdbcURL = "jdbc:postgresql://localhost:5432/TGBot?currentSchema=public";
    String username = "bot";
    String password = "12345";
    try {
        Class.forName("org.postgresql.Driver");
    } catch (ClassNotFoundException e) {
        System.err.println("Драйвер PostgreSQL не найден! Проверьте зависимости.");
        e.printStackTrace();
    }
    try {
        Connection connection = null;		
        try{
        	connection=DriverManager.getConnection(jdbcURL, username, password);
        }catch (SQLException e) {
        	System.err.println(e.getMessage());
        }
        return connection;
        /*System.out.println(
            "Connected to PostgreSQL database!");
        connection.close();
        System.out.println("Connection closed.");*/
    }
    catch (Exception e) {
        e.printStackTrace();
    }
    return null;
	};
	
	public void getUserStats() {};
	public void getMyPolls(int userID) {};
	public void closeConnection() {};
	public void sendResults() {};
}

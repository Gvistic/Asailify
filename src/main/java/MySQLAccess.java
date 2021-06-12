import java.io.IOException;
import java.sql.*;


public class MySQLAccess {
    private Connection connection;
    PropertyValues propertyValues = new PropertyValues();

    public Connection connect(){
        try {
            propertyValues.initPropertyValues();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }

        if (connection == null){
            try {
                String jdbcURL = propertyValues.getJdbcURL();
                String username = propertyValues.getUser();
                String password = propertyValues.getPassword();

                connection = DriverManager.getConnection(jdbcURL, username, password);
            } catch (SQLException e){
                e.printStackTrace();
            }
        }
        return  connection;
    }

    public void disconnect(){
        if (connection != null){
            try{
                connection.close();
                connection = null;
            } catch (SQLException e){
                e.printStackTrace();
            }
        }
    }
}

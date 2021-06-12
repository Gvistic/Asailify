import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class PropertyValues {
    private String token = "";
    private String pasteAPI = "";
    private String jdbcURL = "";
    private String user = "";
    private String password = "";

    InputStream inputStream;

    public void initPropertyValues() throws IOException {
        try{
            Properties properties = new Properties();
            String configFileName = "config.properties";

            inputStream = getClass().getClassLoader().getResourceAsStream(configFileName);

            if (inputStream != null){
                properties.load(inputStream);
            }else{
                throw new FileNotFoundException("Property not found: " + configFileName);
            }

            token = properties.getProperty("DISCORD_BOT_TOKEN");
            pasteAPI = properties.getProperty("PASTEE_API_KEY");
            jdbcURL = properties.getProperty("SQL_JDBC_URL");
            user = properties.getProperty("SQL_USERNAME");
            password = properties.getProperty("SQL_PASSWORD");

        }catch (Exception e){
            System.out.println("Config file Exception: " + e);
        }finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getPasteAPI() {
        return pasteAPI;
    }

    public void setPasteAPI(String pasteAPI) {
        this.pasteAPI = pasteAPI;
    }

    public String getJdbcURL() {
        return jdbcURL;
    }

    public void setJdbcURL(String jdbcURL) {
        this.jdbcURL = jdbcURL;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}

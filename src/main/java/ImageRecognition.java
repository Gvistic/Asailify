import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Image detection and recognition class.
 */

public class ImageRecognition {
    public OkHttpClient httpClient = new OkHttpClient();
    List<AvatarObject> avatarBlackList;
    InputStream urlStream;

    /**
     * Populates the blacklist in order to do comparisons.
     */
    public void populateBlacklist(){
        avatarBlackList = new ArrayList<>();

        MySQLAccess database = new MySQLAccess();
        String query = "SELECT * FROM avatar_blacklist";
        ResultSet resultSet = null;
        try{
            Statement statement = database.connect().createStatement();
            resultSet = statement.executeQuery(query);

            while(resultSet.next()){
                String avatarURL = resultSet.getString("avatar_url");
                String avatarType = resultSet.getString("type");

                AvatarObject avatarObj = new AvatarObject(avatarURL,avatarType, createBufferedImage(avatarURL));
                avatarBlackList.add(avatarObj);
            }
        } catch (SQLException throwable) {
            throwable.printStackTrace();
        } finally {
            database.disconnect();
            try {
                if (resultSet != null) {
                    resultSet.close();
                }
            }catch (SQLException e){
                e.printStackTrace();
            }
        }
    }

    /**
     * This method iterates through each pixel and compares and contrasts.
     * Since discord limits its profile pictures to a max of 128px by 128px, iterating through each pixel is not taxing.
     *
     * I plan to diversify the image comparison methods in the future w/ neural networks/machine learning etc..
     *
     * @param avatar avatar URL.
     * @return List of doubles containing numerical differences against each image provided in the blacklist.
     * < 10 is most likely a match.
     * @throws IOException  File reading.
     */
    public List<AvatarDiffReceipt> pixelSimilarity(String avatar) throws IOException {
        List<AvatarDiffReceipt> avatarDiffReceipt = new ArrayList<>();

        //Creates a buffered image of the target avatar.
        URL avatarURL = new URL(avatar);
        BufferedImage avatarImage = ImageIO.read(avatarURL);

        double diff = 0;
        int w1;
        int h1;

        for (AvatarObject s : avatarBlackList) {
            BufferedImage baseImage = s.getBufferedImage();

            if (baseImage == null){
                System.out.println("Base image is null for: " + s.getAvatar());
            }

            w1 = avatarImage.getWidth();
            h1 = avatarImage.getHeight();
            if (baseImage != null){
                if (w1 != 128 || h1 != 128){
                    BufferedImage newBaseImage = new BufferedImage(w1, h1, avatarImage.getType());

                    Graphics2D g2d = newBaseImage.createGraphics();
                    g2d.drawImage(baseImage, 0, 0,null);
                    g2d.dispose();

                    baseImage = newBaseImage;
                }
                if (w1 == baseImage.getWidth() || h1 == baseImage.getHeight()){
                    for (int j = 0; j < h1; j++) {
                        for (int i = 0; i < w1; i++) {
                            int pixel1 = baseImage.getRGB(i, j);
                            Color color1 = new Color(pixel1, true);
                            int r1 = color1.getRed();
                            int g1 = color1.getGreen();
                            int b1 = color1.getBlue();

                            int pixel2 = avatarImage.getRGB(i, j);

                            Color color2 = new Color(pixel2, true);
                            int r2 = color2.getRed();
                            int g2 = color2.getGreen();
                            int b2 = color2.getBlue();

                            long data = Math.abs(r1 - r2) + Math.abs(g1 - g2) + Math.abs(b1 - b2);
                            diff = diff + data;
                        }
                    }
                    double avg = (diff/((double) w1 * h1 * 3));

                    AvatarDiffReceipt avatarDiff = new AvatarDiffReceipt((avg/255)*100,s.getAvatarType());
                    avatarDiffReceipt.add(avatarDiff);
                    diff = 0;
                }
            }
        }
        return avatarDiffReceipt;
    }

    /**
     * This method checks similarity against a custom given image instead of the blacklist.
     *
     * @param avatar avatar URL.
     * @param image image to check similarity.
     * @return List of doubles containing numerical differences against the image provided.
     * < 10 is most likely a match.
     * @throws IOException  File reading.
     */
    public List<AvatarDiffReceipt> pixelSimilarityCustom(String avatar, String image) throws IOException {
        List<AvatarDiffReceipt> avatarDiffReceipt = new ArrayList<>();

        AvatarObject avatarObj = new AvatarObject(avatar,"Custom Search", createBufferedImage(image));

        URL avatarURL = new URL(avatar);
        BufferedImage avatarImage = ImageIO.read(avatarURL);

        double diff = 0;
        int w1;
        int h1;

        BufferedImage baseImage = avatarObj.getBufferedImage();

        if (baseImage == null){
            System.out.println("Base image is null for: " + avatarObj.getAvatar());
        }

        w1 = avatarImage.getWidth();
        h1 = avatarImage.getHeight();
        if (baseImage != null){
            if (w1 != 128 || h1 != 128){
                BufferedImage newBaseImage = new BufferedImage(w1, h1, avatarImage.getType());

                Graphics2D g2d = newBaseImage.createGraphics();
                g2d.drawImage(baseImage, 0, 0,null);
                g2d.dispose();

                baseImage = newBaseImage;
            }
            if (w1 == baseImage.getWidth() || h1 == baseImage.getHeight()){
                for (int j = 0; j < h1; j++) {
                    for (int i = 0; i < w1; i++) {
                        int pixel1 = baseImage.getRGB(i, j);
                        Color color1 = new Color(pixel1, true);

                        int r1 = color1.getRed();
                        int g1 = color1.getGreen();
                        int b1 = color1.getBlue();

                        int pixel2 = avatarImage.getRGB(i, j);

                        Color color2 = new Color(pixel2, true);
                        int r2 = color2.getRed();
                        int g2 = color2.getGreen();
                        int b2 = color2.getBlue();

                        long data = Math.abs(r1 - r2) + Math.abs(g1 - g2) + Math.abs(b1 - b2);
                        diff = diff + data;
                    }
                }
                double avg = (diff/((double) w1 * h1 * 3));

                AvatarDiffReceipt avatarDiff = new AvatarDiffReceipt((avg/255)*100,avatarObj.getAvatarType());
                avatarDiffReceipt.add(avatarDiff);
            }
        }

        return avatarDiffReceipt;
    }


    /**
     * Requests bytestream for ImageIO and sets the baseImage.
     * @param s supplied URL string.
     */
    private BufferedImage createBufferedImage(String s) {
        if (s.contains(".webp?size=")){
            s = s.substring(0,s.indexOf(".webp?size="));
            s = s + ".png";
        }

        Request request = new Request.Builder()
                .url(s)
                .build();

        try{
            Response response = httpClient.newCall(request).execute();
            urlStream = response.body().byteStream();
            BufferedImage bufferedImage = ImageIO.read(urlStream);
            response.body().close();
            return bufferedImage;
        } catch (IOException ioException){
            System.out.println("Get Request Error: " + s);
            return null;
        }
    }
}

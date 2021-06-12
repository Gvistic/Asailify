import java.awt.image.BufferedImage;

/**
 * Custom object for individual avatars containing their avatar type and similarity index.
 */

public class AvatarObject {
    private String avatar;
    private String avatarType;
    private BufferedImage bufferedImage;

    public AvatarObject(String URL, String avatarType, BufferedImage bufferedImage){
        this.avatar = URL;
        this.avatarType = avatarType;
        this.bufferedImage = bufferedImage;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public void setAvatarType(String avatarType) {
        this.avatarType = avatarType;
    }

    public String getAvatar() {
        return avatar;
    }

    public String getAvatarType() {
        return avatarType;
    }

    public BufferedImage getBufferedImage() {
        return bufferedImage;
    }

    public void setBufferedImage(BufferedImage bufferedImage) {
        this.bufferedImage = bufferedImage;
    }
}

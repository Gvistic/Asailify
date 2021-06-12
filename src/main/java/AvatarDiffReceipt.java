public class AvatarDiffReceipt {
    double differenceIndex;
    String type;

    public AvatarDiffReceipt(double differenceIndex, String type){
        this.differenceIndex = differenceIndex;
        this.type = type;
    }

    public double getDifferenceIndex() {
        return differenceIndex;
    }

    public void setDifferenceIndex(double differenceIndex) {
        this.differenceIndex = differenceIndex;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}

package model;

import java.io.Serializable;

public class TrashModel implements Serializable {
    private static final long serialVersionUID = 1L;

    // Enum để dùng trong logic server khi cần
    public enum TrashCategory { ORGANIC, RECYCLABLE, HAZARDOUS, GENERAL }

    private int trashId;
    private String trashName;
    private String category; // VARCHAR trong MySQL (1 trong 4 giá trị hợp lệ)
    private String image;    // hoặc spriteKey tùy bạn

    public TrashModel() {}

    public TrashModel(int trashId, String trashName, String category, String image) {
        this.trashId = trashId;
        this.trashName = trashName;
        this.category = category; // bạn đảm bảo đã đúng 1 trong 4 giá trị
        this.image = image;
    }

    // ===== converters ngắn gọn =====
    public TrashCategory getCategoryEnum() {
        // Nếu DB luôn đúng 1 trong 4 giá trị thì dòng dưới là đủ
        try { return TrashCategory.valueOf(category.toUpperCase()); }
        catch (Exception e) { return TrashCategory.GENERAL; }
    }

    public void setCategoryEnum(TrashCategory cat) {
        this.category = (cat == null) ? "GENERAL" : cat.name();
    }

    // ===== getters/setters =====
    public int getTrashId() { return trashId; }
    public void setTrashId(int trashId) { this.trashId = trashId; }

    public String getTrashName() { return trashName; }
    public void setTrashName(String trashName) { this.trashName = trashName; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
}

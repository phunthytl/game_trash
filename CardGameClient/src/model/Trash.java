package model;

import java.awt.Image;
import java.awt.Rectangle;

public class Trash {
    public enum Type { ORGANIC, RECYCLABLE, HAZARDOUS, GENERAL }

    private final int id;
    private final String name;
    private final Type type;

    // sprite path để GameView tự load Image nếu muốn
    private final String spritePath;
    private Image image; // optional

    private int x, y;           // vị trí top-left
    private int w = 48, h = 48; // kích thước mặc định
    private int vy = 3;         // vận tốc rơi mặc định (px/tick)

    public Trash(int id, String name, Type type, String spritePath, int x, int y) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.spritePath = spritePath;
        this.x = x;
        this.y = y;
    }

    // Nếu nhận chuỗi category từ server → enum
    public static Type parseType(String category) {
        if (category == null) return Type.GENERAL;
        try { return Type.valueOf(category.trim().toUpperCase()); }
        catch (IllegalArgumentException ex) { return Type.GENERAL; }
    }

    // ===== getters =====
    public int getId() { return id; }
    public String getName() { return name; }
    public Type getType() { return type; }
    public String getSpritePath() { return spritePath; }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getW() { return w; }
    public int getH() { return h; }
    public int getVy() { return vy; }

    // ===== setters dùng trong gameplay =====
    public void setX(int x) { this.x = x; }
    public void setY(int y) { this.y = y; }
    public void setSize(int w, int h) { this.w = w; this.h = h; }
    public void setVy(int vy) { this.vy = vy; }

    public void setImage(Image image) { this.image = image; }
    public Image getImage() { return image; }

    public Rectangle getBounds() { return new Rectangle(x, y, w, h); }

    public void tick() { y += vy; }
}

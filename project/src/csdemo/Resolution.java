package csdemo;

/**
 *
 * @author Adam Jurcik <xjurc@fi.muni.cz>
 */
public enum Resolution {
    
    RECT_800_600(800, 600),
    RECT_1024_768(1024, 768),
    SQUARE_800(800, 800),
    SQUARE_1024(1024, 1024);
    
    private final int width;
    private final int height;
    
    private Resolution(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
    
}

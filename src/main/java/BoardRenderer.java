import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

public class BoardRenderer {
    private static final int SQUARE_SIZE = 80; // 80x80 Pixel pro Feld
    private static final int BOARD_SIZE = SQUARE_SIZE * 8;
    private Map<Character, BufferedImage> pieceImages = new HashMap<>();

    public BoardRenderer(String assetsFolderPath) throws Exception {
        char[] pieces = {'P', 'N', 'B', 'R', 'Q', 'K', 'p', 'n', 'b', 'r', 'q', 'k'};


        for (char p : pieces) {
            String prefix = Character.isUpperCase(p) ? "w" : "b";
            String fileName = prefix + p + ".png";

            File imgFile = new File(assetsFolderPath + "/" + fileName);
            if (imgFile.exists()) {
                pieceImages.put(p, ImageIO.read(imgFile));
            } else {
                System.out.println("Fehlt: " + fileName);
            }
        }
    }

    public void generateImage(String fen, String outputFilePath) throws Exception {
        BufferedImage boardImage = new BufferedImage(BOARD_SIZE, BOARD_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = boardImage.createGraphics();

        Color lightSquare = new Color(240, 217, 181);
        Color darkSquare = new Color(181, 136, 99);

        // Brett malen
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                boolean isLight = (row + col) % 2 == 0;
                g2d.setColor(isLight ? lightSquare : darkSquare);
                g2d.fillRect(col * SQUARE_SIZE, row * SQUARE_SIZE, SQUARE_SIZE, SQUARE_SIZE);
            }
        }

        String boardLayout = fen.split(" ")[0];
        int row = 0;
        int col = 0;

        for (char c : boardLayout.toCharArray()) {
            if (c == '/') {
                row++;
                col = 0;
            } else if (Character.isDigit(c)) {
                // Leere Felder überspringen
                col += Character.getNumericValue(c);
            } else {
                // Figur zeichnen
                BufferedImage pieceImg = pieceImages.get(c);
                if (pieceImg != null) {
                    g2d.drawImage(pieceImg, col * SQUARE_SIZE, row * SQUARE_SIZE, SQUARE_SIZE, SQUARE_SIZE, null);
                }
                col++;
            }
        }

        g2d.dispose();
        ImageIO.write(boardImage, "png", new File(outputFilePath));
    }
}
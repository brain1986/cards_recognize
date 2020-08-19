import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map.Entry;

public class Cards {
    private static Path modelsPath;

    public static void main(String[] args) {
        try {
            modelsPath = Paths.get("./models");
            Files.walk(Paths.get(args[0]))
                    .filter(p -> p.toString().endsWith(".png"))
                    .forEach(p -> {
                        try {
                            List<String> cards = recognizeCards(p.toUri());
                            System.out.println(String.format("%s - %s", p.getFileName(), String.join("", cards)));
                        } catch (Exception e) {
                            System.out.println(String.format("Exception thrown with %s: %s", p, e));
                        }
                    });
            System.out.println("Finished. Press any key...");
            System.in.read();
        } catch (Exception e) {
            System.out.println(String.format("Exception thrown: %s", e));
        }
    }

    private static List<String> recognizeCards(URI currentImgPath) throws Exception {
        List<String> cards = new ArrayList<>();
        BufferedImage screenImg = ImageIO.read(new File(currentImgPath));
        screenImg = imgToBlackAndWhite(screenImg);

        for (int cardNumber = 0; cardNumber < 5; cardNumber++) {
            BufferedImage cardImg = screenImg.getSubimage(147 + (cardNumber * 72), 591, 54, 77);
            BufferedImage cardWeight = cardImg.getSubimage(0, 0, 33, 26);
            BufferedImage cardSuit = cardImg.getSubimage(20, 42, 32, 34);

            cardWeight = trimImage(cardWeight);
            cardSuit = trimImage(cardSuit);

            Optional<String> weightOptional = recognizeSymbol(cardWeight);
            Optional<String> suitOptional = recognizeSymbol(cardSuit);
            if (weightOptional.isPresent() && suitOptional.isPresent())
                cards.add(weightOptional.get().concat(suitOptional.get()));
        }
        return cards;
    }

    private static BufferedImage imgToBlackAndWhite(BufferedImage srcImg) {
        for (int x = 0; x < srcImg.getWidth(); x++) {
            for (int y = 0; y < srcImg.getHeight(); y++) {
                int pixelColor = srcImg.getRGB(x, y);
                if (pixelColor == -1 || pixelColor == -8882056)
                    srcImg.setRGB(x, y, -1);
                else
                    srcImg.setRGB(x, y, 0);
            }
        }
        return srcImg;
    }

    private static BufferedImage trimImage(BufferedImage srcImage) {
        int minX = 100000;
        int minY = 100000;
        int maxX = 0;
        int maxY = 0;
        for (int x = 0; x < srcImage.getWidth(); x++) {
            for (int y = 0; y < srcImage.getHeight(); y++) {
                int mycol = srcImage.getRGB(x, y);
                if (mycol != -1) {
                    if (x <= minX)
                        minX = x;
                    if (y <= minY)
                        minY = y;
                    if (x >= maxX)
                        maxX = x;
                    if (y >= maxY)
                        maxY = y;
                }
            }
        }
        return srcImage.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private static Optional<String> recognizeSymbol(BufferedImage symbolImg) throws Exception {
        var mostMatchingEntry = Files.walk(modelsPath)
                .filter(p -> p.toString().endsWith(".png"))
                .map(p -> getMostMatchedSymbol(symbolImg, p).get())
                .sorted(Comparator.comparingDouble(v -> v.getKey()))
                .filter(v -> v.getKey() < 0.3)
                .findFirst().orElseGet(() -> new SimpleEntry<Double, String>(0d, "|unrecognized"));

        return (mostMatchingEntry.getKey() == -1d)
                ? Optional.empty() : Optional.of(mostMatchingEntry.getValue());
    }

    private static Optional<Entry<Double, String>> getMostMatchedSymbol(BufferedImage symbolImg, Path modelPath) {
        try {
            int matched = 1;
            int unmatched = 1;
            boolean cardPresence = false;
            BufferedImage modelImg = ImageIO.read(new File(modelPath.toUri()));
            for (int x = modelImg.getWidth() - 1; x >= 0; x--) {
                for (int y = modelImg.getHeight() - 1; y >= 0; y--) {
                    if (x < symbolImg.getWidth() && y < symbolImg.getHeight()) {
                        if (symbolImg.getRGB(x, y) == -1)
                            cardPresence = true;

                        if (modelImg.getRGB(x, y) == symbolImg.getRGB(x, y))
                            matched++;
                        else
                            unmatched++;
                    }
                }
            }
            if (cardPresence)
                return Optional.of(new SimpleEntry<Double, String>((double) unmatched / matched,
                        modelPath.getFileName().toString().replace(".png", "")));
            else
                return Optional.of(new SimpleEntry<Double, String>(-1d, ""));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
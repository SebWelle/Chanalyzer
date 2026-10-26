import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.game.Game;
import com.github.bhlangonijr.chesslib.move.Move;
import com.github.bhlangonijr.chesslib.move.MoveList;
import com.github.bhlangonijr.chesslib.pgn.PgnHolder;
import java.io.File;
import java.io.FileWriter;
import java.time.LocalDate;

public class Main {
    static final String ANKI_DECK = "Schach-Training";
    static final String ANKI_MODEL = "Einfach";
    static final String ANKI_FRONT = "Vorderseite";
    static final String ANKI_BACK = "Rückseite";


    public static void main(String[] args) {
        int currentMonth = LocalDate.now().getMonthValue();
        int currentYear = LocalDate.now().getYear();

        String targetDeck = ANKI_DECK + "::games_" + currentYear + "_" + currentMonth;

        HttpClient client = HttpClient.newHttpClient();

        createAnkiDeck(client, targetDeck);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.chess.com/pub/player/sebcarlsen187/games/2026/" + currentMonth))
                .header("User-Agent", "SebsChessApp - Java HttpClient")
                .build();

        String result = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .join();

        String regex = "\"pgn\":\"(.*?)(?<!\\\\)\"";

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(result);

        List<String> matchList = new ArrayList<>();

        while (matcher.find()) {
            matchList.add(matcher.group(1));
        }

        StockfishClient stockfish = new StockfishClient();

        // Anki Media Pfad bekommen
        String os = System.getProperty("os.name").toLowerCase();
        String ankiMediaDir = "";
        String ankiProfile = "Benutzer 1";

        if (os.contains("win")) {
            ankiMediaDir = System.getenv("APPDATA") + "\\Anki2\\" + ankiProfile + "\\collection.media\\";
        } else if (os.contains("mac")) {
            ankiMediaDir = System.getProperty("user.home") + "/Library/Application Support/Anki2/" + ankiProfile + "/collection.media/";
        }

        File mediaFolder = new File(ankiMediaDir);

        if (!mediaFolder.exists()) {
            System.out.println("WARNUNG: Anki-Ordner nicht gefunden! Bilder landen im Projektordner.");
            ankiMediaDir = "";
        }

        try {
            stockfish.startEngine();
            BoardRenderer renderer = new BoardRenderer("assets");

            for (String treffer : matchList) {

                String cleanPgn = treffer.replace("\\n", "\n")
                        .replace("\\r", "")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\");

                try {
                    File tempFile = File.createTempFile("temp_game", ".pgn");
                    try (FileWriter writer = new FileWriter(tempFile)) {
                        writer.write(cleanPgn);
                    }

                    PgnHolder pgn = new PgnHolder(tempFile.getAbsolutePath());
                    pgn.loadPgn();

                    if (pgn.getGames().isEmpty()) continue;

                    Game game = pgn.getGames().get(0);
                    game.loadMoveText();

                    Board board = new Board();
                    MoveList moves = game.getHalfMoves();

                    System.out.println("\nAnalysiere Partie: " + game.getWhitePlayer().getName() + " vs " + game.getBlackPlayer().getName());

                    for (Move move : moves) {
                        String fenVorher = board.getFen();
                        Side currentTurn = board.getSideToMove();

                        board.doMove(move);

                        String whitePlayer = game.getWhitePlayer().getName();
                        Side userSide = whitePlayer.equalsIgnoreCase("sebcarlsen187") ? Side.WHITE : Side.BLACK;
                        String colorName = (userSide == Side.WHITE) ? "Weiß" : "Schwarz";

                        if (currentTurn == userSide) {
                            StockfishClient.AnalysisResult evalVorher = stockfish.getEval(fenVorher, 12);

                            // filtern ob ich den besten zug gemacht hab
                            if (move.toString().equals(evalVorher.bestMove)) {
                                continue;
                            }

                            StockfishClient.AnalysisResult evalDanach = stockfish.getEval(board.getFen(), 12);

                            int verlust = evalVorher.score + evalDanach.score;

                            // patzer check
                            if (verlust >= 300) {
                                System.out.println("PATZER GEFUNDEN!");
                                System.out.println("Dein Zug: " + move.toString() + " | Bester Zug: " + evalVorher.bestMove);

                                String imageName = "blunder_" + System.currentTimeMillis() + ".png";
                                String fullPath = ankiMediaDir + imageName;
                                renderer.generateImage(fenVorher, fullPath);

                                double bauernVerlust = Math.round((verlust / 100.0) * 100.0) / 100.0;

                                String front = "<img src='" + imageName + "'><br><br><b>Finde den besten Zug für " + colorName + "!</b>";

                                String back = "Du hast gespielt: <b>" + move.toString() + "</b><br>"
                                        + "Bester Zug: <b style='color:green;'>" + evalVorher.bestMove + "</b><br>"
                                        + "Bewertungsverlust: <b>" + bauernVerlust + " Bauern</b>";

                                sendToAnki(client,targetDeck, front, back);
                                Thread.sleep(10);
                            }
                        }
                    }

                    tempFile.delete();

                } catch (Exception e) {
                    System.out.println("Fehler beim Parsen einer Partie: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.out.println("Kritischer Fehler: " + e.getMessage());
        } finally {
            stockfish.stopEngine();
        }

        System.out.println("\nFertig! Alle Patzer wurden direkt in dein Anki-Deck übertragen.");
    }

    // AnkiConnect
    private static void sendToAnki(HttpClient client,String targetDeck, String front, String back) {
        String safeFront = front.replace("\"", "\\\"");
        String safeBack = back.replace("\"", "\\\"");

        String jsonPayload = "{"
                + "\"action\": \"addNote\","
                + "\"version\": 6,"
                + "\"params\": {"
                + "  \"note\": {"
                + "    \"deckName\": \"" + targetDeck + "\","
                + "    \"modelName\": \"" + ANKI_MODEL + "\","
                + "    \"fields\": {"
                + "      \"" + ANKI_FRONT + "\": \"" + safeFront + "\","
                + "      \"" + ANKI_BACK + "\": \"" + safeBack + "\""
                + "    },"
                + "    \"tags\": [\"schach_patzer\"]"
                + "  }"
                + "}"
                + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:8765"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        try {
            client.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            System.out.println("Konnte Karte nicht an Anki senden. Ist Anki geöffnet? Fehler: " + e.getMessage());
        }
    }


    private static void createAnkiDeck(HttpClient client, String deckName) {
        String jsonPayload = "{"
                + "\"action\": \"createDeck\","
                + "\"version\": 6,"
                + "\"params\": {"
                + "  \"deck\": \"" + deckName + "\""
                + "}"
                + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:8765"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        try {
            client.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            System.out.println("Fehler beim Erstellen des Decks: " + e.getMessage());
        }
    }
}
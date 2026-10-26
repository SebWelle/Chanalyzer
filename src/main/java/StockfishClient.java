import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class StockfishClient {
    private Process engineProcess;
    private BufferedReader processReader;
    private BufferedWriter processWriter;

    private final String STOCKFISH_PATH = "stockfish-windows-x86-64-universal.exe";

    public static class AnalysisResult {
        public int score;
        public String bestMove;

        public AnalysisResult(int score, String bestMove) {
            this.score = score;
            this.bestMove = bestMove;
        }
    }

    public void startEngine() throws Exception {
        engineProcess = new ProcessBuilder(STOCKFISH_PATH).start();
        processReader = new BufferedReader(new InputStreamReader(engineProcess.getInputStream()));
        processWriter = new BufferedWriter(new OutputStreamWriter(engineProcess.getOutputStream()));
    }

    public void sendCommand(String command) throws Exception {
        processWriter.write(command + "\n");
        processWriter.flush();
    }

    public AnalysisResult getEval(String fen, int depth) throws Exception {
        sendCommand("position fen " + fen);
        sendCommand("go depth " + depth);

        String line;
        int score = 0;
        String bestMove = "";

        while ((line = processReader.readLine()) != null) {
            if (line.contains("score cp")) {
                score = Integer.parseInt(line.split("score cp ")[1].split(" ")[0]);
            } else if (line.contains("score mate")) {
                int mateIn = Integer.parseInt(line.split("score mate ")[1].split(" ")[0]);
                score = mateIn > 0 ? 10000 : -10000;
            }

            if (line.startsWith("bestmove")) {
                bestMove = line.split(" ")[1];
                break;
            }
        }
        return new AnalysisResult(score, bestMove);
    }

    public void stopEngine() {
        try {
            sendCommand("quit");
            processReader.close();
            processWriter.close();
        } catch (Exception ignored) {}
    }
}
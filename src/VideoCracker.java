import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

public class VideoCracker {

    // On augmente un peu la plage de recherche pour être sûr
    private static final int MAX_SEARCH_S = 127;
    private static final int MAX_SEARCH_R = 255;

    public static class CrackingResult {
        public int bestR;
        public int bestS;
        public double score;
        public boolean success;

        public CrackingResult(int r, int s, double score, boolean success) {
            this.bestR = r;
            this.bestS = s;
            this.score = score;
            this.success = success;
        }
    }

    public static CrackingResult crackVideo(String videoPath) {
        VideoCapture cap = new VideoCapture(videoPath);
        if (!cap.isOpened()) {
            return new CrackingResult(0, 0, Double.MAX_VALUE, false);
        }

        // On prend une frame au milieu pour éviter les fondus au noir du début/fin
        int totalFrames = (int) cap.get(Videoio.CAP_PROP_FRAME_COUNT);
        Mat workFrame = getFrameAt(cap, totalFrames / 2);

        // Validation frame (à 80% de la vidéo)
        Mat controlFrame = getFrameAt(cap, (int)(totalFrames * 0.8));

        cap.release();

        if (workFrame.empty()) return new CrackingResult(0, 0, Double.MAX_VALUE, false);

        // Prétraitement : Gris + Petite largeur pour accélérer (ex: 100px de large)
        Mat processed = preprocessFrame(workFrame);
        Mat tempFrame = new Mat();

        // Etape 1 : Trouver S
        // On ne calcule le score que sur le premier bloc (HighestOneBit) pour 'isoler' le S et ne pas être perturbé par le R.
        int height = processed.rows();
        int mainBlockHeight = Integer.highestOneBit(height); // Ex: 512 pour 1000

        int bestS = 0;
        double bestScoreS = Double.MAX_VALUE;
        for (int s = 0; s <= MAX_SEARCH_S; s++) {
            processed.copyTo(tempFrame);
            // On teste avec R=0
            LineLogic.decrypt(tempFrame, 0, s);

            // On mesure le bruit uniquement sur le bloc principal (lignes 0 à mainBlockHeight) pour isoler S
            double score = calculateVerticalNoise(tempFrame, 0, mainBlockHeight);

            if (score < bestScoreS) {
                bestScoreS = score;
                bestS = s;
            }
        }
        System.out.println("Candidat S trouvé : " + bestS);

        // Etape 2 : Trouver R
        // On cherche le R qui va aligner les blocs entre eux
        // On calcule donc le score sur toute l'image.

        int bestR = 0;
        double bestScoreR = Double.MAX_VALUE;
        for (int r = 0; r <= MAX_SEARCH_R; r++) {
            processed.copyTo(tempFrame);
            LineLogic.decrypt(tempFrame, r, bestS);
            // Score sur toute la hauteur cette fois car on cherche à aligner les blocs
            double score = calculateVerticalNoise(tempFrame, 0, height);

            if (score < bestScoreR) {
                bestScoreR = score;
                bestR = r;
            }
        }
        System.out.println("Candidat R trouvé : " + bestR);

        // Nettoyage
        workFrame.release();
        controlFrame.release();
        processed.release();
        tempFrame.release();

        return new CrackingResult(bestR, bestS, bestScoreR, true);
    }

    private static Mat getFrameAt(VideoCapture cap, int index) {
        cap.set(Videoio.CAP_PROP_POS_FRAMES, index);
        Mat frame = new Mat();
        cap.read(frame);
        return frame;
    }

    private static Mat preprocessFrame(Mat input) {
        Mat resized = new Mat();
        Mat gray = new Mat();
        Imgproc.cvtColor(input, gray, Imgproc.COLOR_BGR2GRAY);
        // On réduit la largeur à 64px pour aller très vite, la hauteur reste la même
        Imgproc.resize(gray, resized, new Size(64, input.rows()));
        gray.release();
        return resized;
    }

    /**
     * Calcule le bruit vertical (différence entre ligne Y et Y+1).
     */
    private static double calculateVerticalNoise(Mat frame, int startY, int endY) {
        int width = frame.cols();
        int totalRows = frame.rows();

        // Sécurité bornes
        if (endY > totalRows) endY = totalRows;
        if (startY < 0) startY = 0;

        // On recupère les données
        byte[] data = new byte[width * totalRows];
        frame.get(0, 0, data);

        long totalDiffSq = 0;
        int count = 0;

        // On s'arrête à endY - 1 car on compare Y avec Y+1
        for (int y = startY; y < endY - 1; y++) {
            int rowOffsetCurrent = y * width;
            int rowOffsetNext = (y + 1) * width;

            for (int x = 0; x < width; x++) {
                int val1 = data[rowOffsetCurrent + x] & 0xFF;
                int val2 = data[rowOffsetNext + x] & 0xFF;

                int diff = val1 - val2;
                totalDiffSq += (diff * diff);
                count++;
            }
        }

        if (count == 0) return Double.MAX_VALUE;
        return (double) totalDiffSq / count;
    }
}
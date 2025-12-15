/**
 Nom :  Belhadj, Bernard
 Prénom : Quentin, Elena
 Groupe : S5-A
 Projet : VideoScramble

 Description : Cette classe implémente la logique d'analyse cryptanalytique (Brute-force). Elle tente de retrouver les clés R et S en analysant la cohérence statistique des pixels et le bruit de l'image.
 */

import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

public class VideoCracker {

    // Plages de recherche
    private static final int MAX_SEARCH_S = 127;
    private static final int MAX_SEARCH_R = 255;

    // Nombre de colonnes à analyser pour éviter le piège des zones unies
    private static final int PROBE_COUNT = 3;

    // Classe pour stocker le résultat
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

    /**
     * Analyse la vidéo cible pour retrouver les paramètres de chiffrement (R et S).
     * Procède par analyse de colonnes sondes et minimisation du score de bruit.
     *
     * @param videoPath Chemin de la vidéo à analyser.
     * @return Un objet CrackingResult contenant les meilleures clés trouvées.
     */
    public static CrackingResult crackVideo(String videoPath) {
        VideoCapture cap = new VideoCapture(videoPath);
        if (!cap.isOpened()) {
            return new CrackingResult(0, 0, Double.MAX_VALUE, false);
        }

        // On récupère une frame au milieu de la vidéo (plus sûr que le début car fade-in)
        int totalFrames = (int) cap.get(Videoio.CAP_PROP_FRAME_COUNT);
        Mat workFrame = new Mat();
        cap.set(Videoio.CAP_PROP_POS_FRAMES, totalFrames / 2);
        cap.read(workFrame);
        cap.release(); // On libère le lien avec le fichier vidéo

        if (workFrame.empty()) return new CrackingResult(0, 0, Double.MAX_VALUE, false);

        // Extraction multi-sondes
        // On extrait 3 colonnes (byte[]) reparties sur l'image (25%, 50%, 75%)
        // Apres on n'a plus besoin d'OpenCV pour le calcul (parceque c'est super lourd openCV)
        byte[][] probes = extractProbeColumns(workFrame, PROBE_COUNT);

        // On libère immediatement la memoire de l'image complete
        workFrame.release();

        // Recherche de S (diviser pour regner)
        // On fixe R=0 pour trouver la coherence d'ecartement (S)
        int bestS = 0;
        double bestScoreS = Double.MAX_VALUE;

        for (int s = 0; s <= MAX_SEARCH_S; s++) {
            double totalScoreCurrentS = 0;

            // On cumule le score de bruit sur nos 3 colonnes
            for (byte[] columnProbe : probes) {
                // Appel a la methode optimisee de LineLogic
                totalScoreCurrentS += LineLogic.getScoreEuclideanFast(columnProbe, 0, s);
            }

            if (totalScoreCurrentS < bestScoreS) {
                bestScoreS = totalScoreCurrentS;
                bestS = s;
            }
        }

        System.out.println("Candidat S trouve : " + bestS + " (Score cumule : " + bestScoreS + ")");

        int bestR = 0;
        double bestScoreR = Double.MAX_VALUE;

        // Est-ce qu'on a plusieurs blocs ?
        // On regarde la taille de la première sonde
        boolean canUseBoundary = Integer.highestOneBit(probes[0].length) < probes[0].length;

        for (int r = 0; r <= MAX_SEARCH_R; r++) {
            double currentScore = 0;

            for (byte[] columnProbe : probes) {
                // On calcule la cohérence interne
                double internalNoise = LineLogic.getScoreEuclideanFast(columnProbe, r, bestS);

                // On calcule la cohérence de frontière si possible
                // Pas de boucle donc linéraire
                double boundaryNoise = 0;
                if (canUseBoundary) {
                    // On multiplie par un gros poids pour eviter les valeurs nulles
                    boundaryNoise = LineLogic.getBoundaryScore(columnProbe, r, bestS) * 100.0;
                }

                currentScore += internalNoise + boundaryNoise;
            }

            if (currentScore < bestScoreR) {
                bestScoreR = currentScore;
                bestR = r;
            }
        }

        System.out.println("Candidat R trouve : " + bestR);

        return new CrackingResult(bestR, bestS, bestScoreR, true);
    }

    /**
     * Extrait plusieurs colonnes de l'image à des positions X fixes pour l'analyse.
     * Optimise les performances en convertissant en gris et en extrayant uniquement les octets nécessaires.
     *
     * @param input          L'image source OpenCV.
     * @param numberOfProbes Nombre de colonnes à extraire.
     * @return Un tableau 2D d'octets représentant les colonnes extraites.
     */
    private static byte[][] extractProbeColumns(Mat input, int numberOfProbes) {
        Mat gray = new Mat();
        // Conversion en niveaux de gris (1 canal)
        Imgproc.cvtColor(input, gray, Imgproc.COLOR_BGR2GRAY);

        int width = gray.cols();
        int height = gray.rows();

        // Lecture de TOUS les pixels dans un tampon
        byte[] allPixels = new byte[width * height];
        gray.get(0, 0, allPixels);

        // Preparation des colonnes de sortie
        byte[][] extractedProbes = new byte[numberOfProbes][height];

        // Calcul des positions X (ex: 25%, 50%, 75% pour 3 sondes)
        int[] xPositions = new int[numberOfProbes];
        for (int i = 0; i < numberOfProbes; i++) {
            xPositions[i] = (width * (i + 1)) / (numberOfProbes + 1);
        }

        // Remplissage des colonnes en parcourant le tampon unique
        for (int y = 0; y < height; y++) {
            int rowOffset = y * width;

            for (int i = 0; i < numberOfProbes; i++) {
                int pixelIndex = rowOffset + xPositions[i];
                extractedProbes[i][y] = allPixels[pixelIndex];
            }
        }

        // On rend la memoire
        gray.release();
        return extractedProbes;
    }
}
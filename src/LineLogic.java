import org.opencv.core.Mat;
import java.math.BigInteger;
import java.util.Arrays;

public class LineLogic {
    public static void encrypt(Mat frame, int r, int s) {
        processImageBase(frame, r, s, true);
    }

    public static void decrypt(Mat frame, int r, int s) {
        processImageBase(frame, r, s, false);
    }

    /**
     * Méthode commune qui gère la découpe en blocs de puissance de 2
     */
    private static void processImageBase(Mat frame, int r, int s, boolean isEncrypt) {
        int height = frame.rows();      // hauteur de l'image
        int width = frame.cols();       // largeur de l'image
        int channels = frame.channels();// nombre de canaux (souvent 3 car RGB)
        int rowWidth = width * channels; // largeur d'une ligne en bytes

        // Extraction des données de l'image dans un tableau de bytes
        int bufferSize = (int) (frame.total() * channels);  // taille totale en bytes (total pixels * channels) -> ex: 3 bytes par pixel pour RGB
        byte[] sourceData = new byte[bufferSize];
        frame.get(0, 0, sourceData); // Copie des données de l'image dans sourceData

        // Tableau pour stocker les données modifiées
        byte[] resultData = Arrays.copyOf(sourceData, sourceData.length);

        int currentY = 0; // Position verticale actuelle (on encode de haut en bas)
        int remainingHeight = height; // Hauteur restante à traiter

        // Découpage en puissances de 2 (pour gérer n'importe quelle hauteur d'image)
        while (remainingHeight > 0) {
            int blockSize = Integer.highestOneBit(remainingHeight); // Plus grande puissance de 2 <= remainingHeight

            if (blockSize < 2) break; // On arrête si on a strictement moins de 2 lignes

            if (isEncrypt) {
                // Appel de la logique d'encryption
                // Source et destination, largeur, position Y de départ, taille du bloc, r et s
                processBlockEncrypt(sourceData, resultData, rowWidth, currentY, blockSize, r, s);
            } else {
                processBlockDecrypt(sourceData, resultData, rowWidth, currentY, blockSize, r, s);
            }

            currentY += blockSize;
            remainingHeight -= blockSize;
        }

        // Réinsertion des données modifiées dans l'image
        frame.put(0, 0, resultData);
    }

    /**
     * Logique d'encryption : y' = (r + a * y) % size
     */
    private static void processBlockEncrypt(byte[] source, byte[] destination, int rowWidth,
                                            int startY, int size, int r, int s) {
        long a = 2L * s + 1; // Le multiplicateur (toujours impair)

        for (int i = 0; i < size; i++) {
            // Formule affine : (r + a * i) % N     -> calcul de la distance jusqu'a la nouvelle ligne y'
            int targetRowRelative = (int) ((r + a * i) % size);

            int srcIndex = (startY + i) * rowWidth;
            int dstIndex = (startY + targetRowRelative) * rowWidth;

            // Copie des octets de la ligne entière (width * channels)
            // On copie depuis la source a srcIndex vers la destination a dstIndex une ligne de taille rowWidth
            System.arraycopy(source, srcIndex, destination, dstIndex, rowWidth);
        }
    }

    /**
     * Logique de décryption : y = a^-1 * (y' - r) % size (encryption inverse)
     */
    private static void processBlockDecrypt(byte[] source, byte[] destination, int rowWidth,
                                            int startY, int size, int r, int s) {
        long a = 2L * s + 1;
        long a_inv = modInverse(a, size); // Calcul de l'inverse modulaire de a modulo size

        for (int i = 0; i < size; i++) {
            // Formule inverse : a^-1 * (i - r) % N
            long val = (i - r);
            // Calcul de la nouvelle position de la ligne
            int targetRowRelative = (int) ((a_inv * val) % size);

            // Correction modulo négatif en Java
            if (targetRowRelative < 0) targetRowRelative += size;


            int srcIndex = (startY + i) * rowWidth;
            int dstIndex = (startY + targetRowRelative) * rowWidth;

            // Copie des octets de la ligne entière (width * channels)
            // On copie depuis la source a srcIndex vers la destination a dstIndex une ligne de taille rowWidth
            System.arraycopy(source, srcIndex, destination, dstIndex, rowWidth);
        }
    }

    /**
     * Calcule l'inverse modulaire de a modulo m en utilisant l'algorithme d'Euclide étendu
     * Complexité : O(log m)
     */
    private static long modInverse(long a, long m) {
        long m0 = m;
        long y = 0, x = 1;
        long q, t;

        if (m == 1) return 0;

        while (a > 1) {
            // q est le quotient
            q = a / m;
            // t sert de tampon
            t = m;

            // m est le reste maintenant, on échange comme dans l'algorithme d'Euclide
            m = a % m;
            a = t;
            t = y;

            // Mise à jour de x et y
            y = x - q * y;
            x = t;
        }

        // Si x est négatif, on le rend positif
        if (x < 0) x += m0;

        return x;
    }
}
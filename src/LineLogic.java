/**
 Nom :  Belhadj, Bernard
 Prénom : Quentin, Elena
 Groupe : S5-A
 Projet : VideoScramble

 Description : Cette classe contient les algorithmes mathématiques de bas niveau. Elle gère la permutation des lignes de l'image (chiffrement/déchiffrement) et les calculs de distance euclidienne pour le cracking.
 */

import org.opencv.core.Mat;
import java.math.BigInteger;
import java.util.Arrays;

public class LineLogic {

    /**
     * Chiffre une image entière en modifiant l'ordre de ses lignes (permutation affine).
     *
     * @param frame L'image OpenCV à traiter.
     * @param r     Décalage R.
     * @param s     Multiplicateur S.
     */
    public static void encrypt(Mat frame, int r, int s) {
        processImageBase(frame, r, s, true);
    }

    /**
     * Déchiffre une image entière (permutation inverse).
     *
     * @param frame L'image OpenCV à traiter.
     * @param r     Décalage R.
     * @param s     Multiplicateur S.
     */
    public static void decrypt(Mat frame, int r, int s) {
        processImageBase(frame, r, s, false);
    }

    /**
     * Méthode commune qui gère la découpe de l'image en blocs de hauteur puissance de 2.
     * Applique ensuite le traitement ligne par ligne sur chaque bloc.
     *
     * @param frame     L'image source.
     * @param r         La clé R.
     * @param s         La clé S.
     * @param isEncrypt Indique le sens de l'opération (chiffrement vs déchiffrement).
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
     * Applique la logique d'encryption sur un bloc de lignes : y' = (r + a * y) % size.
     *
     * @param source      Buffer complet source.
     * @param destination Buffer complet destination.
     * @param rowWidth    Largeur d'une ligne en octets.
     * @param startY      Index de la première ligne du bloc courant.
     * @param size        Nombre de lignes dans le bloc courant.
     * @param r           Clé R.
     * @param s           Clé S.
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
     * Applique la logique de décryption sur un bloc de lignes : y = a^-1 * (y' - r) % size.
     *
     * @param source      Buffer complet source.
     * @param destination Buffer complet destination.
     * @param rowWidth    Largeur d'une ligne en octets.
     * @param startY      Index de la première ligne du bloc courant.
     * @param size        Nombre de lignes dans le bloc courant.
     * @param r           Clé R.
     * @param s           Clé S.
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
     * Calcule l'inverse modulaire de a modulo m en utilisant l'algorithme d'Euclide étendu.
     * Complexité : O(log m).
     *
     * @param a Le nombre dont on cherche l'inverse.
     * @param m Le module.
     * @return L'inverse x tel que (a * x) % m == 1.
     */
    public static long modInverse(long a, long m) {
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

    // ------------------------------------------------------ //
    //         FONCTION UTILISEES POUR LE CRACKING ICI        //
    // ------------------------------------------------------ //

    /**
     * Calcule le score de continuité (bruit) sans modifier l'image ("Zero-copy").
     * Simule la reconstruction de la colonne pour mesurer la différence moyenne entre pixels voisins.
     *
     * @param encryptedColumn La colonne de pixels chiffrée.
     * @param r               Hypothèse de clé R.
     * @param s               Hypothèse de clé S.
     * @return Le score de bruit (plus c'est bas, mieux c'est).
     */
    public static double getScoreEuclideanFast(byte[] encryptedColumn, int r, int s) {
        int totalHeight = encryptedColumn.length;

        // On se concentre uniquement sur le bloc principal (ex: 1024 lignes)
        int blockSize = Integer.highestOneBit(totalHeight);

        // Si l'image est trop petite
        if (blockSize < 2) return Double.MAX_VALUE;

        long totalDiffSq = 0;
        int count = 0;

        // Le multiplicateur de l'encryption (toujours impair grâce au 2L + 1)
        long a = 2L * s + 1;

        // On parcourt les lignes Y de 0 à blockSize-1
        // On veut comparer la ligne reconstituée Y avec la ligne Y+1
        for (int y = 0; y < blockSize - 1; y++) {

            // On calcule ou se trouvent les pixels Y et Y+1
            // dans l'image melangee en utilisant la formule d'encryption.
            // pos = (r + a * y) % size
            int indexCurrent = (int) ((r + a * y) % blockSize);
            int indexNext    = (int) ((r + a * (y + 1)) % blockSize);

            // Lecture des valeurs avec le masque 0xFF pour gérer le byte signe
            int val1 = encryptedColumn[indexCurrent] & 0xFF;
            int val2 = encryptedColumn[indexNext] & 0xFF;

            // Calcul de la distance d'Euclide (Carre de la différence)
            int diff = val1 - val2;
            totalDiffSq += (diff * diff);
            count++;
        }

        return (count == 0) ? Double.MAX_VALUE : (double) totalDiffSq / count;
    }

    /**
     * Calcule le "choc" visuel (discontinuité) à la frontière entre le premier et le deuxième bloc de puissance de 2.
     * Permet d'affiner la recherche de R.
     *
     * @param encryptedColumn La colonne de pixels chiffrée.
     * @param r               Hypothèse de clé R.
     * @param s               Hypothèse de clé S.
     * @return La différence de valeur pixel à la frontière des blocs.
     */
    public static double getBoundaryScore(byte[] encryptedColumn, int r, int s) {
        int totalHeight = encryptedColumn.length;

        // Taille du premier bloc
        int block1Size = Integer.highestOneBit(totalHeight);

        // S'il n'y a qu'un seul bloc (ex: image 1024x1024), cette méthode ne peut pas marcher
        // On retourne 0 au cas ou (normalement gere dans le bloc parent)
        if (block1Size == totalHeight) return 0;

        // Taille du deuxième bloc
        int remaining = totalHeight - block1Size;
        int block2Size = Integer.highestOneBit(remaining);

        // Paramètre a
        long a = 2L * s + 1;

        // Pixel A le DERNIER pixel du premier bloc
        // Sa position reelle depend de R dans le bloc 1
        int lastLineOfBlock1 = block1Size - 1;
        int indexA = (int) ((r + a * lastLineOfBlock1) % block1Size);

        // Pixel B le PREMIER pixel du deuxième bloc
        // Sa position réelle dépend de R dans le bloc 2
        int indexB = block1Size + (int) ((r + a * 0) % block2Size);

        // On compare :
        int valA = encryptedColumn[indexA] & 0xFF;
        int valB = encryptedColumn[indexB] & 0xFF;

        // On veut que la différence soit minimale pour la meilleur continuite
        return Math.abs(valA - valB);
    }
}
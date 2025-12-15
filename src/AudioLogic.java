/**
 Nom :  Belhadj, Bernard
 Prénom : Quentin, Elena
 Groupe : S5-A
 Projet : VideoScramble

 Description : Cette classe implémente la logique de permutation des données audio. Elle découpe les buffers en sous-blocs (puissance de 2) pour appliquer les transformations affines.
 */

public class AudioLogic {

    /**
     * Chiffre un buffer audio complet en utilisant une permutation affine.
     *
     * @param buffer Le tableau de bytes audio à modifier.
     * @param r      Paramètre de décalage.
     * @param s      Paramètre multiplicatif.
     */
    public static void encrypt(byte[] buffer, int r, int s) {
        processAudioBuffer(buffer, r, s, true);
    }

    /**
     * Déchiffre un buffer audio complet (opération inverse).
     *
     * @param buffer Le tableau de bytes audio à modifier.
     * @param r      Paramètre de décalage.
     * @param s      Paramètre multiplicatif.
     */
    public static void decrypt(byte[] buffer, int r, int s) {
        processAudioBuffer(buffer, r, s, false);
    }

    /**
     * Découpe le buffer en sous-blocs de taille puissance de 2 maximale pour traiter l'intégralité des données.
     * Applique la permutation ou l'inverse permutation sur chaque sous-bloc.
     *
     * @param data      Les données brutes.
     * @param r         Clé R.
     * @param s         Clé S.
     * @param isEncrypt Vrai pour chiffrer, Faux pour déchiffrer.
     */
    private static void processAudioBuffer(byte[] data, int r, int s, boolean isEncrypt) {
        int totalLength = data.length;
        int currentOffset = 0;
        int remaining = totalLength;

        while (remaining >= 2) {
            // Plus grande puissance de 2 possible dans ce qui reste
            int blockSize = Integer.highestOneBit(remaining);

            // Extraire sous-bloc
            byte[] subBlockSource = new byte[blockSize];
            System.arraycopy(data, currentOffset, subBlockSource, 0, blockSize);

            byte[] subBlockDest = new byte[blockSize];

            // Traitement
            if (isEncrypt) {
                permute(subBlockSource, subBlockDest, blockSize, r, s);
            } else {
                inversePermute(subBlockSource, subBlockDest, blockSize, r, s);
            }

            // Réinjection
            System.arraycopy(subBlockDest, 0, data, currentOffset, blockSize);

            // Avance
            currentOffset += blockSize;
            remaining -= blockSize;
        }
    }

    /**
     * Applique la permutation affine : y' = (r + a * y) % size.
     *
     * @param src  Bloc source.
     * @param dst  Bloc destination.
     * @param size Taille du bloc (doit être une puissance de 2).
     * @param r    Décalage.
     * @param s    Générateur pour 'a' (a = 2s + 1).
     */
    private static void permute(byte[] src, byte[] dst, int size, int r, int s) {
        long a = 2L * s + 1;
        for (int i = 0; i < size; i++) {
            int targetIndex = (int) ((r + a * i) % size);
            dst[targetIndex] = src[i];
        }
    }

    /**
     * Applique la permutation inverse : y = a^-1 * (y' - r) % size.
     *
     * @param src  Bloc source.
     * @param dst  Bloc destination.
     * @param size Taille du bloc.
     * @param r    Décalage.
     * @param s    Générateur pour 'a'.
     */
    private static void inversePermute(byte[] src, byte[] dst, int size, int r, int s) {
        long a = 2L * s + 1;
        long a_inv = LineLogic.modInverse(a, size);

        for (int i = 0; i < size; i++) {
            long val = (i - r);
            int targetIndex = (int) ((a_inv * val) % size);

            if (targetIndex < 0) targetIndex += size;

            dst[targetIndex] = src[i];
        }
    }
}
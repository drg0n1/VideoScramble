import java.util.Arrays;

public class AudioLogic {

    /**
     * Chiffre un buffer audio (algo itératif pour couvrir tout le buffer).
     */
    public static void encrypt(byte[] buffer, int r, int s) {
        processAudioBuffer(buffer, r, s, true);
    }

    /**
     * Déchiffre un buffer audio.
     */
    public static void decrypt(byte[] buffer, int r, int s) {
        processAudioBuffer(buffer, r, s, false);
    }

    /**
     * Découpe le buffer en sous-blocs de puissance de 2 pour traiter 100% des données.
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

    // y' = (r + a * y) % size
    private static void permute(byte[] src, byte[] dst, int size, int r, int s) {
        long a = 2L * s + 1;
        for (int i = 0; i < size; i++) {
            int targetIndex = (int) ((r + a * i) % size);
            dst[targetIndex] = src[i];
        }
    }

    // y = a^-1 * (y' - r) % size
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
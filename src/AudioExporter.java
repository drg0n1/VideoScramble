/**
 Nom :  Belhadj, Bernard
 Prénom : Quentin, Elena
 Groupe : S5-A
 Projet : VideoScramble

 Description : Cette classe gère l'exportation et la synchronisation de l'audio traité (chiffré ou déchiffré) vers un fichier WAV, en alignant les blocs audio sur les frames vidéo.
 */

import javax.sound.sampled.*;
import java.io.*;
import java.util.Arrays;

public class AudioExporter {

    /**
     * Lit un fichier audio source, applique l'effet de chiffrement ou de déchiffrement (clés r, s)
     * en synchronisation avec le framerate (FPS) de la vidéo, et sauvegarde le résultat.
     *
     * @param inputPath  Chemin du fichier vidéo ou audio source.
     * @param outputPath Chemin de destination du fichier WAV généré.
     * @param r          La clé R (décalage).
     * @param s          La clé S (multiplicateur).
     * @param encrypt    Vrai pour chiffrer, Faux pour déchiffrer.
     * @param fps        Le nombre d'images par seconde de la vidéo pour calculer la taille des chunks.
     */
    public static void export(String inputPath, String outputPath, int r, int s, boolean encrypt, double fps) {
        try {
            // 1. Trouver le fichier source (WAV)
            File inputFile = new File(inputPath);
            if (!inputPath.toLowerCase().endsWith(".wav")) {
                String wavPath = inputPath.substring(0, inputPath.lastIndexOf('.')) + ".wav";
                inputFile = new File(wavPath);
            }
            // Vérification de l'existence du fichier
            if (!inputFile.exists()) {
                System.out.println("Export Audio annulé : Fichier WAV source introuvable.");
                return;
            }

            // 2. Prépare les flux
            AudioInputStream ais = AudioSystem.getAudioInputStream(inputFile);
            AudioFormat format = ais.getFormat();

            // Calcul de la taille de chunk pour la synchro
            int frameSize = format.getFrameSize();
            int bytesPerVideoFrame = (int) ((format.getSampleRate() * frameSize) / fps);

            // Alignement sur la taille de frame
            if (bytesPerVideoFrame % frameSize != 0) {
                bytesPerVideoFrame -= (bytesPerVideoFrame % frameSize);
            }

            // 3. Lecture et Traitement
            ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
            byte[] buffer = new byte[bytesPerVideoFrame];
            int bytesRead;

            while ((bytesRead = ais.read(buffer)) != -1) {
                // Gestion du dernier bloc partiel
                byte[] chunkToProcess;
                if (bytesRead < buffer.length) {
                    chunkToProcess = Arrays.copyOf(buffer, bytesRead);
                } else {
                    chunkToProcess = buffer;
                }

                // Application de l'effet
                if (encrypt) {
                    AudioLogic.encrypt(chunkToProcess, r, s);
                } else {
                    AudioLogic.decrypt(chunkToProcess, r, s);
                }

                outBuffer.write(chunkToProcess);
            }

            // 4. Sauvegarde
            byte[] processedData = outBuffer.toByteArray();
            ByteArrayInputStream bais = new ByteArrayInputStream(processedData);
            AudioInputStream processedStream = new AudioInputStream(bais, format, processedData.length / frameSize);

            File outFile = new File(outputPath);
            AudioSystem.write(processedStream, AudioFileFormat.Type.WAVE, outFile);

            System.out.println("Export Audio terminé : " + outputPath);

            ais.close();
            processedStream.close();

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Erreur lors de l'export audio : " + e.getMessage());
        }
    }
}
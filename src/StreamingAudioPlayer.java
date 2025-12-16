/**
 Nom :  Belhadj, Bernard
 Prénom : Quentin, Elena
 Groupe : S5-A
 Projet : VideoScramble

 Description : Cette classe gère la lecture audio en temps réel via un thread dédié. Elle permet d'appliquer les effets de transformation audio à la volée et gère désormais le bouclage (loop) automatique du son.
 */
import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class StreamingAudioPlayer {

    private SourceDataLine line;
    private AudioInputStream audioInputStream;
    private Thread playbackThread;

    // On garde une référence au fichier pour pouvoir le recharger en boucle
    private File currentAudioFile;

    private volatile boolean isRunning = false;
    private volatile boolean isMuted = false;

    // Paramètres d'effet
    private volatile boolean enableEncryption = false;
    private volatile boolean enableDecryption = false;
    private volatile int keyR = 0;
    private volatile int keyS = 0;

    // Synchro Vidéo (par défaut 30 fps)
    private double videoFps = 30.0;

    /**
     * Définit le nombre d'images par seconde pour synchroniser les buffers audio.
     *
     * @param fps Le framerate de la vidéo associée.
     */
    public void setFps(double fps) {
        if (fps > 0) this.videoFps = fps;
    }

    /**
     * Charge le fichier audio correspondant au chemin donné.
     * Tente de trouver un fichier .wav si le chemin pointe vers une vidéo.
     *
     * @param filePath Chemin vers le fichier média.
     */
    public void load(String filePath) {
        stop(); // Arrête tout avant de charger
        try {
            File audioFile = new File(filePath);

            // Si le fichier n'existe pas ou n'est pas wav, on tente de trouver le .wav correspondant
            // Ex: "video.avi" -> cherche "video.wav"
            if (!filePath.toLowerCase().endsWith(".wav")) {
                String wavPath = filePath.substring(0, filePath.lastIndexOf('.')) + ".wav";
                audioFile = new File(wavPath);
            }

            if (!audioFile.exists()) {
                System.out.println("Audio : Fichier WAV introuvable pour " + filePath);
                return;
            }

            // Sauvegarde du fichier pour le bouclage
            this.currentAudioFile = audioFile;

            audioInputStream = AudioSystem.getAudioInputStream(audioFile);
            AudioFormat format = audioInputStream.getFormat();
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format);

        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            System.err.println("Erreur Audio Player: " + e.getMessage());
            audioInputStream = null;
            currentAudioFile = null;
        }
    }

    /**
     * Lance le thread de lecture audio.
     * Lit les données par blocs et boucle indéfiniment (replay) tant que isRunning est vrai.
     */
    public void play() {
        if (audioInputStream == null || line == null) return;

        isRunning = true;
        line.start();

        playbackThread = new Thread(() -> {
            try {
                AudioFormat fmt = audioInputStream.getFormat();

                // On veut que 1 bloc audio = temps d'1 frame vidéo
                int frameSize = fmt.getFrameSize();
                float sampleRate = fmt.getSampleRate();

                // Taille buffer = (BytesPerSec / FPS)
                int bufferSize = (int) ((sampleRate * frameSize) / videoFps);

                // Alignement sur la taille d'un sample complet
                if (bufferSize % frameSize != 0) {
                    bufferSize -= (bufferSize % frameSize);
                }
                if (bufferSize < frameSize) bufferSize = frameSize;

                byte[] buffer = new byte[bufferSize];
                int bytesRead;

                while (isRunning) {
                    // Lecture du flux
                    bytesRead = audioInputStream.read(buffer, 0, buffer.length);

                    // --- GESTION DU BOUCLAGE (LOOP) ---
                    if (bytesRead == -1) {
                        // Fin du fichier atteinte : on recharge le flux depuis le début
                        audioInputStream.close();
                        if (currentAudioFile != null && currentAudioFile.exists()) {
                            audioInputStream = AudioSystem.getAudioInputStream(currentAudioFile);
                            continue; // On repart au début de la boucle while immédiatement
                        } else {
                            break; // Fichier introuvable, on arrête
                        }
                    }

                    // Gestion fin de fichier (dernier bloc incomplet avant le loop)
                    byte[] chunkToProcess;
                    if (bytesRead < bufferSize) {
                        chunkToProcess = Arrays.copyOf(buffer, bytesRead);
                    } else {
                        chunkToProcess = buffer;
                    }

                    // Copie de travail pour ne pas abîmer le buffer de lecture
                    byte[] audioBlock = Arrays.copyOf(chunkToProcess, chunkToProcess.length);

                    if (!isMuted) {
                        if (enableEncryption) {
                            AudioLogic.encrypt(audioBlock, keyR, keyS);
                        } else if (enableDecryption) {
                            AudioLogic.decrypt(audioBlock, keyR, keyS);
                        }

                        // Envoi à la carte son
                        if (line.isOpen()) {
                            line.write(audioBlock, 0, audioBlock.length);
                        }
                    }
                }
            } catch (IOException | UnsupportedAudioFileException e) {
                e.printStackTrace();
            }
        });
        playbackThread.setDaemon(true);
        playbackThread.start();
    }

    /**
     * Arrête la lecture audio, ferme la ligne de données et termine le thread.
     */
    public void stop() {
        isRunning = false;
        // On ferme proprement la ligne
        if (line != null) {
            line.stop();
            line.close();
        }
        // On attend la fin du thread
        if (playbackThread != null && playbackThread.isAlive()) {
            try { playbackThread.join(100); } catch (InterruptedException e) {}
        }
        // Fermeture du flux fichier
        if (audioInputStream != null) {
            try { audioInputStream.close(); } catch (IOException e) {}
        }
    }

    /**
     * Active ou désactive la sortie sonore (Mute).
     *
     * @param mute Vrai pour couper le son, Faux pour l'activer.
     */
    public void setMute(boolean mute) {
        this.isMuted = mute;
    }

    /**
     * Configure les paramètres de l'effet audio en temps réel.
     *
     * @param encrypt Activer le mode chiffrement.
     * @param decrypt Activer le mode déchiffrement.
     * @param r       La clé R.
     * @param s       La clé S.
     */
    public void setEffect(boolean encrypt, boolean decrypt, int r, int s) {
        this.enableEncryption = encrypt;
        this.enableDecryption = decrypt;
        this.keyR = r;
        this.keyS = s;
    }
}
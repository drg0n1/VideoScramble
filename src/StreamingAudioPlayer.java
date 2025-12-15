import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class StreamingAudioPlayer {

    private SourceDataLine line;
    private AudioInputStream audioInputStream;
    private Thread playbackThread;

    private volatile boolean isRunning = false;
    private volatile boolean isMuted = false;

    // Paramètres d'effet
    private volatile boolean enableEncryption = false;
    private volatile boolean enableDecryption = false;
    private volatile int keyR = 0;
    private volatile int keyS = 0;

    // Synchro Vidéo (par défaut 30 fps)
    private double videoFps = 30.0;

    public void setFps(double fps) {
        if (fps > 0) this.videoFps = fps;
    }

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

            audioInputStream = AudioSystem.getAudioInputStream(audioFile);
            AudioFormat format = audioInputStream.getFormat();
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format);

        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            System.err.println("Erreur Audio Player: " + e.getMessage());
            audioInputStream = null;
        }
    }

    public void play() {
        if (audioInputStream == null || line == null) return;

        isRunning = true;
        line.start();

        playbackThread = new Thread(() -> {
            AudioFormat fmt = audioInputStream.getFormat();

            // CALCUL CRITIQUE DE LA TAILLE DE FRAME AUDIO
            // On veut que 1 bloc audio = temps d'1 frame vidéo
            int frameSize = fmt.getFrameSize(); // Octets par sample (ex: 4 pour stéréo 16bit)
            float sampleRate = fmt.getSampleRate();

            // Taille buffer = (BytesPerSec / FPS)
            int bufferSize = (int) ((sampleRate * frameSize) / videoFps);

            // Alignement sur la taille d'un sample complet (pour ne pas couper un sample en deux)
            if (bufferSize % frameSize != 0) {
                bufferSize -= (bufferSize % frameSize);
            }
            if (bufferSize < frameSize) bufferSize = frameSize; // Sécurité minimum

            byte[] buffer = new byte[bufferSize];
            int bytesRead;

            try {
                while (isRunning && (bytesRead = audioInputStream.read(buffer, 0, buffer.length)) != -1) {

                    // Gestion fin de fichier (dernier bloc incomplet)
                    byte[] chunkToProcess;
                    if (bytesRead < bufferSize) {
                        chunkToProcess = Arrays.copyOf(buffer, bytesRead);
                    } else {
                        chunkToProcess = buffer; // Pas de copie, on travaille direct
                    }

                    // Copie de travail pour ne pas abîmer le buffer de lecture si besoin
                    byte[] audioBlock = Arrays.copyOf(chunkToProcess, chunkToProcess.length);

                    if (!isMuted) {
                        if (enableEncryption) {
                            AudioLogic.encrypt(audioBlock, keyR, keyS);
                        } else if (enableDecryption) {
                            AudioLogic.decrypt(audioBlock, keyR, keyS);
                        }

                        // Envoi à la carte son
                        line.write(audioBlock, 0, audioBlock.length);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        playbackThread.setDaemon(true);
        playbackThread.start();
    }

    public void stop() {
        isRunning = false;
        if (line != null) {
            line.stop();
            line.close();
        }
        if (playbackThread != null) {
            try { playbackThread.join(100); } catch (InterruptedException e) {}
        }
    }

    public void setMute(boolean mute) {
        this.isMuted = mute;
    }

    public void setEffect(boolean encrypt, boolean decrypt, int r, int s) {
        this.enableEncryption = encrypt;
        this.enableDecryption = decrypt;
        this.keyR = r;
        this.keyS = s;
    }
}
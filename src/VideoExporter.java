import javafx.concurrent.Task;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;
import org.opencv.videoio.Videoio;

public class VideoExporter {

    /**
     * Crée une tâche JavaFX pour l'exportation vidéo et audio chiffrée.
     * Gère l'ouverture de la vidéo source, le traitement frame par frame, et l'export audio.
     */
    public static Task<Void> createExportTask(String inputPath, int rKey, int sKey) {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                VideoCapture capGen = new VideoCapture(inputPath);
                if (!capGen.isOpened()) {
                    updateMessage("Erreur : Impossible d'ouvrir le fichier source.");
                    return null;
                }

                // Récupération des propriétés vidéo
                double fps = capGen.get(Videoio.CAP_PROP_FPS);
                if (fps <= 0) fps = 30.0;
                int width = (int) capGen.get(Videoio.CAP_PROP_FRAME_WIDTH);
                int height = (int) capGen.get(Videoio.CAP_PROP_FRAME_HEIGHT);

                // Correction dimensions pour s'assurer qu'elles sont paires (requis par certains codecs)
                if (width % 2 != 0) width--;
                if (height % 2 != 0) height--;
                Size size = new Size(width, height);

                // Configuration de la sortie vidéo
                String outputVideoPath = "videos/output_encrypted.avi";
                int fourcc = VideoWriter.fourcc('M', 'J', 'P', 'G');
                VideoWriter writerGen = new VideoWriter(outputVideoPath, fourcc, fps, size, true);

                if (!writerGen.isOpened()) {
                    capGen.release();
                    updateMessage("Erreur Writer Vidéo.");
                    return null;
                }

                // Audio exporter
                updateMessage("Export de l'audio crypté...");
                String outputAudioPath = "videos/output_encrypted.wav";
                // true = encrypt mode
                AudioExporter.export(inputPath, outputAudioPath, rKey, sKey, true, fps);

                // Traitement video
                Mat frame = new Mat();
                Mat resized = new Mat();
                int framesProcessed = 0;

                while (capGen.read(frame)) {
                    if (isCancelled()) break;

                    // Redimensionnement si nécessaire (pour les dimensions impaires)
                    if (frame.width() != width || frame.height() != height) {
                        Imgproc.resize(frame, resized, size);
                        LineLogic.encrypt(resized, rKey, sKey);
                        writerGen.write(resized);
                    } else {
                        LineLogic.encrypt(frame, rKey, sKey);
                        writerGen.write(frame);
                    }

                    framesProcessed++;
                    updateMessage("Génération : Frame " + framesProcessed);
                }

                // Nettoyage des ressources
                frame.release();
                resized.release();
                capGen.release();
                writerGen.release();

                updateMessage("Succès ! Vidéo: output_encrypted.avi + Audio: .wav");
                return null;
            }
        };
    }
}
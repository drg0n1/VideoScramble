import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;
import org.opencv.videoio.Videoio;

import java.io.ByteArrayInputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class VideoApp extends Application {

    // UI Elements Globaux
    private RadioButton webcamRadio;
    private RadioButton videoRadio;
    private ComboBox<String> videoSelector;
    private Button startButton;
    private Label statusLabel;

    // UI Onglets
    private TabPane tabPane;
    private Tab encryptTab;
    private Tab decryptTab;

    // UI Encryption
    private ImageView encSourceView;
    private ImageView encResultView;
    private TextField rEncField = new TextField("50");
    private TextField sEncField = new TextField("10");
    private ToggleButton muteEncButton; // Bouton Mute Encrypt
    private Button generateButton;
    private ProgressBar progressBar;

    // UI Décryption
    private ImageView decSourceView;
    private ImageView decResultView;
    private TextField rDecField = new TextField("50");
    private TextField sDecField = new TextField("10");
    private ToggleButton muteDecButton; // Bouton Mute Decrypt
    private Button crackButton;

    // Moteurs
    private VideoCapture capture;
    private ScheduledExecutorService timer;
    private boolean cameraActive = false;

    // Audio Player
    private StreamingAudioPlayer audioPlayer = new StreamingAudioPlayer();

    public static void main(String[] args) {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Video Scramble & Cracker Pro (+Audio)");

        // Barre de sélection
        HBox sourceBox = createSourceSelection();

        // Onglets
        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        createEncryptTab();
        createDecryptTab();

        tabPane.getTabs().addAll(encryptTab, decryptTab);

        // Listener Changement d'onglet : Mettre à jour l'audio
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            updateAudioSettings();
            // Synchro des boutons mute visuels si besoin (optionnel)
        });

        // Barre de statut
        statusLabel = new Label("Prêt. Sélectionnez une source.");
        statusLabel.setPadding(new Insets(5));
        statusLabel.setTextFill(Color.DARKBLUE);

        // Layout
        BorderPane root = new BorderPane();
        root.setTop(sourceBox);
        root.setCenter(tabPane);
        root.setBottom(statusLabel);

        Scene scene = new Scene(root, 1150, 800);
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> stopAcquisition());
        primaryStage.show();
    }

    private void createEncryptTab() {
        encryptTab = new Tab("🔒 CHIFFREMENT (Encoder)");

        encSourceView = createImageView();
        encResultView = createImageView();

        HBox videoBox = new HBox(20,
                new VBox(5, new Label("Source (Originale)"), encSourceView),
                new VBox(5, new Label("Résultat (Chiffré)"), encResultView)
        );
        videoBox.setAlignment(Pos.CENTER);
        videoBox.setPadding(new Insets(15));

        // Contrôles
        generateButton = new Button("Générer Vidéo (.avi)");
        generateButton.setStyle("-fx-base: #ffcccc; -fx-font-weight: bold;");
        generateButton.setOnAction(e -> generateEncryptedVideo());

        progressBar = new ProgressBar(0);
        progressBar.setVisible(false);

        // Audio Control
        muteEncButton = new ToggleButton("Mute Audio");
        muteEncButton.setOnAction(e -> {
            audioPlayer.setMute(muteEncButton.isSelected());
            muteDecButton.setSelected(muteEncButton.isSelected()); // Synchro visuelle
        });

        // Listeners pour mise à jour temps réel
        rEncField.textProperty().addListener((o, old, nev) -> updateAudioSettings());
        sEncField.textProperty().addListener((o, old, nev) -> updateAudioSettings());

        HBox controls = new HBox(15);
        controls.setAlignment(Pos.CENTER);
        controls.setPadding(new Insets(15));
        controls.getChildren().addAll(
                new Label("Clé R:"), rEncField,
                new Label("Clé S:"), sEncField,
                muteEncButton,
                new Separator(Orientation.VERTICAL),
                generateButton,
                progressBar
        );

        BorderPane content = new BorderPane();
        content.setCenter(videoBox);
        content.setBottom(controls);
        encryptTab.setContent(content);
    }

    private void createDecryptTab() {
        decryptTab = new Tab("🔓 DÉCHIFFREMENT (Décoder)");

        decSourceView = createImageView();
        decResultView = createImageView();

        HBox videoBox = new HBox(20,
                new VBox(5, new Label("Entrée (Chiffrée)"), decSourceView),
                new VBox(5, new Label("Sortie (Déchiffrée)"), decResultView)
        );
        videoBox.setAlignment(Pos.CENTER);
        videoBox.setPadding(new Insets(15));

        crackButton = new Button("Auto-Crack");
        crackButton.setStyle("-fx-base: #ccffcc; -fx-font-weight: bold;");
        crackButton.setOnAction(e -> startCracking());

        // Audio Control
        muteDecButton = new ToggleButton("Mute Audio");
        muteDecButton.setOnAction(e -> {
            audioPlayer.setMute(muteDecButton.isSelected());
            muteEncButton.setSelected(muteDecButton.isSelected()); // Synchro visuelle
        });

        // Listeners
        rDecField.textProperty().addListener((o, old, nev) -> updateAudioSettings());
        sDecField.textProperty().addListener((o, old, nev) -> updateAudioSettings());

        HBox controls = new HBox(15);
        controls.setAlignment(Pos.CENTER);
        controls.setPadding(new Insets(15));
        controls.getChildren().addAll(
                new Label("Clé R:"), rDecField,
                new Label("Clé S:"), sDecField,
                muteDecButton,
                new Separator(Orientation.VERTICAL),
                crackButton
        );

        BorderPane content = new BorderPane();
        content.setCenter(videoBox);
        content.setBottom(controls);
        decryptTab.setContent(content);
    }

    private HBox createSourceSelection() {
        ToggleGroup group = new ToggleGroup();
        webcamRadio = new RadioButton("Webcam");
        webcamRadio.setToggleGroup(group);
        videoRadio = new RadioButton("Fichier Vidéo");
        videoRadio.setToggleGroup(group);
        videoRadio.setSelected(true);

        videoSelector = new ComboBox<>();
        videoSelector.getItems().addAll(
                "videos/cat.avi",
                "videos/video.mp4",
                "videos/output_encrypted.avi"
        );
        videoSelector.setEditable(true);
        videoSelector.setValue("videos/cat.avi");
        videoSelector.setPrefWidth(200);

        // Logic UI : Webcam vs Fichier
        webcamRadio.selectedProperty().addListener((obs, oldVal, newVal) -> {
            boolean isWebcam = newVal;
            videoSelector.setDisable(isWebcam);
            if (generateButton != null) generateButton.setDisable(isWebcam);

            // Si on passe en webcam, on coupe l'audio immédiatement
            if (isWebcam) {
                audioPlayer.stop();
                muteEncButton.setDisable(true);
                muteDecButton.setDisable(true);
            } else {
                muteEncButton.setDisable(false);
                muteDecButton.setDisable(false);
            }
        });

        startButton = new Button("Charger / Démarrer");
        startButton.setStyle("-fx-font-weight: bold;");
        startButton.setOnAction(e -> startVideo());

        HBox box = new HBox(15, new Label("Source :"), webcamRadio, videoRadio, videoSelector, startButton);
        box.setPadding(new Insets(15));
        box.setAlignment(Pos.CENTER);
        box.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #ccc; -fx-border-width: 0 0 1 0;");
        return box;
    }

    private void startVideo() {
        stopAcquisition(); // Reset complet (Audio + Video)

        if (webcamRadio.isSelected()) {
            // MODE WEBCAM
            capture = new VideoCapture(0);
            statusLabel.setText("Source : Webcam (Audio désactivé)");
            // On s'assure que l'audio est OFF
            audioPlayer.stop();

        } else {
            // MODE FICHIER
            String path = videoSelector.getValue();
            capture = new VideoCapture(path);

            // Récupération FPS pour synchro audio
            double fps = capture.get(Videoio.CAP_PROP_FPS);
            if (fps <= 0) fps = 30.0;

            statusLabel.setText("Source : " + path + " (" + (int)fps + " FPS)");

            // Chargement Audio
            audioPlayer.setFps(fps);
            audioPlayer.load(path); // Cherchera le .wav automatiquement
            audioPlayer.play();
            updateAudioSettings(); // Applique les clés R/S actuelles
        }

        if (capture.isOpened()) {
            cameraActive = true;
            Runnable frameGrabber = this::grabFrame;
            timer = Executors.newSingleThreadScheduledExecutor();
            timer.scheduleAtFixedRate(frameGrabber, 0, 33, TimeUnit.MILLISECONDS);
        } else {
            statusLabel.setText("Erreur : Impossible d'ouvrir la vidéo.");
        }
    }

    private void stopAcquisition() {
        // Arrêt Vidéo
        if (timer != null && !timer.isShutdown()) {
            timer.shutdown();
            try { timer.awaitTermination(33, TimeUnit.MILLISECONDS); }
            catch (InterruptedException e) { e.printStackTrace(); }
        }
        if (capture != null && capture.isOpened()) capture.release();

        // Arrêt Audio
        audioPlayer.stop();

        cameraActive = false;
    }

    // Envoie les clés et le mode au player audio
    private void updateAudioSettings() {
        boolean isEncryptMode = encryptTab.isSelected();
        int r, s;

        if (isEncryptMode) {
            r = parseSafe(rEncField.getText());
            s = parseSafe(sEncField.getText());
            // Mode Encrypt : On demande au player d'encrypter
            audioPlayer.setEffect(true, false, r, s);
        } else {
            r = parseSafe(rDecField.getText());
            s = parseSafe(sDecField.getText());
            // Mode Decrypt : On demande au player de décrypter
            audioPlayer.setEffect(false, true, r, s);
        }
    }

    // --- LOGIQUE OPENCV ---

    private void grabFrame() {
        if (capture != null && capture.isOpened()) {
            Mat frame = new Mat();
            if (capture.read(frame)) {

                boolean isEncryptMode = encryptTab.isSelected();

                if (isEncryptMode) {
                    processEncryptionPipeline(frame);
                } else {
                    processDecryptionPipeline(frame);
                }

                frame.release();
            } else {
                // Boucle automatique si fichier
                if (!webcamRadio.isSelected()) {
                    capture.set(Videoio.CAP_PROP_POS_FRAMES, 0);
                    // Pour l'audio, la boucle est gérée plus difficilement en simple stream,
                    // ici on recharge simplement pour faire simple ou on laisse l'audio finir.
                    // (Dans une V2 on gérerait la boucle audio synchronisée)
                }
            }
        }
    }

    private void processEncryptionPipeline(Mat cleanFrame) {
        Image imgSource = mat2Image(cleanFrame);
        Mat processedFrame = cleanFrame.clone();

        int r = parseSafe(rEncField.getText());
        int s = parseSafe(sEncField.getText());

        LineLogic.encrypt(processedFrame, r, s);

        Image imgResult = mat2Image(processedFrame);

        Platform.runLater(() -> {
            encSourceView.setImage(imgSource);
            encResultView.setImage(imgResult);
        });
        processedFrame.release();
    }

    private void processDecryptionPipeline(Mat inputFrame) {
        Image imgInput = mat2Image(inputFrame);
        Mat processedFrame = inputFrame.clone();

        int r = parseSafe(rDecField.getText());
        int s = parseSafe(sDecField.getText());

        LineLogic.decrypt(processedFrame, r, s);

        Image imgResult = mat2Image(processedFrame);

        Platform.runLater(() -> {
            decSourceView.setImage(imgInput);
            decResultView.setImage(imgResult);
        });
        processedFrame.release();
    }

    // --- Methodes de Génération et Crack (Simplifiées ici mais présentes) ---

    private void generateEncryptedVideo() {
        if (webcamRadio.isSelected()) {
            statusLabel.setText("Impossible de générer un fichier depuis la webcam.");
            return;
        }

        String inputPath = videoSelector.getValue();
        if (inputPath == null || inputPath.isEmpty()) return;

        generateButton.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(0);
        statusLabel.setText("Génération Vidéo + Audio en cours...");

        final int rKey = parseSafe(rEncField.getText());
        final int sKey = parseSafe(sEncField.getText());

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                VideoCapture capGen = new VideoCapture(inputPath);
                if (!capGen.isOpened()) {
                    updateMessage("Erreur : Impossible d'ouvrir le fichier source.");
                    return null;
                }

                // Propriétés vidéo
                double fps = capGen.get(Videoio.CAP_PROP_FPS);
                if (fps <= 0) fps = 30.0;
                int totalFrames = (int) capGen.get(Videoio.CAP_PROP_FRAME_COUNT);
                int width = (int) capGen.get(Videoio.CAP_PROP_FRAME_WIDTH);
                int height = (int) capGen.get(Videoio.CAP_PROP_FRAME_HEIGHT);

                // Correction dimensions paires
                if (width % 2 != 0) width--;
                if (height % 2 != 0) height--;
                Size size = new Size(width, height);

                // Initialisation writer Vidéo
                String outputVideoPath = "videos/output_encrypted.avi";
                int fourcc = VideoWriter.fourcc('M', 'J', 'P', 'G');
                VideoWriter writerGen = new VideoWriter(outputVideoPath, fourcc, fps, size, true);

                // --- EXPORT AUDIO ---
                // On lance l'export audio en parallèle ou juste avant la boucle vidéo
                updateMessage("Export de l'audio crypté...");
                String outputAudioPath = "videos/output_encrypted.wav";
                // true = encrypt
                AudioExporter.export(inputPath, outputAudioPath, rKey, sKey, true, fps);

                if (!writerGen.isOpened()) {
                    capGen.release();
                    updateMessage("Erreur Writer Vidéo.");
                    return null;
                }

                // Boucle de traitement Vidéo
                Mat frame = new Mat();
                Mat resized = new Mat();
                int framesProcessed = 0;

                while (capGen.read(frame)) {
                    if (isCancelled()) break;

                    if (frame.width() != width || frame.height() != height) {
                        Imgproc.resize(frame, resized, size);
                        LineLogic.encrypt(resized, rKey, sKey);
                        writerGen.write(resized);
                    } else {
                        LineLogic.encrypt(frame, rKey, sKey);
                        writerGen.write(frame);
                    }

                    framesProcessed++;
                    updateProgress(framesProcessed, totalFrames);
                    updateMessage("Vidéo : Frame " + framesProcessed + " / " + totalFrames);
                }

                frame.release();
                resized.release();
                capGen.release();
                writerGen.release();

                updateMessage("Succès ! Vidéo: output_encrypted.avi + Audio: .wav");
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            statusLabel.setText(task.getMessage());
            progressBar.setVisible(false);
            generateButton.setDisable(false);

            // Ajouter à la liste pour lecture immédiate
            if (!videoSelector.getItems().contains("videos/output_encrypted.avi")) {
                videoSelector.getItems().add("videos/output_encrypted.avi");
            }
        });

        task.setOnFailed(e -> {
            statusLabel.setText("Erreur lors de la génération.");
            progressBar.setVisible(false);
            generateButton.setDisable(false);
            e.getSource().getException().printStackTrace();
        });

        new Thread(task).start();
    }

    private void startCracking() {
        String path = videoSelector.getValue();
        if (webcamRadio.isSelected()) {
            statusLabel.setText("Sélectionnez un fichier vidéo pour le crack.");
            return;
        }

        statusLabel.setText("Analyse en cours... (Cela peut prendre quelques secondes)");
        crackButton.setDisable(true);

        new Thread(() -> {
            // Lancement du crack
            VideoCracker.CrackingResult result = VideoCracker.crackVideo(path);

            Platform.runLater(() -> {
                crackButton.setDisable(false);
                if (result.success) {
                    statusLabel.setText("🔓 CLÉ TROUVÉE ! R=" + result.bestR + ", S=" + result.bestS);
                    rDecField.setText(String.valueOf(result.bestR));
                    sDecField.setText(String.valueOf(result.bestS));

                    // IMPORTANT : Appliquer le résultat à l'audio immédiatement
                    updateAudioSettings();
                } else {
                    statusLabel.setText("Échec : Vidéo trop sombre ou algorithme inefficace.");
                }
            });
        }).start();
    }

    // --- UTILS ---
    private ImageView createImageView() {
        ImageView iv = new ImageView();
        iv.setFitWidth(450); // Ajusté un peu
        iv.setPreserveRatio(true);
        iv.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 10, 0, 0, 0);");
        return iv;
    }

    private int parseSafe(String txt) {
        try { return Integer.parseInt(txt); } catch (Exception e) { return 0; }
    }

    private Image mat2Image(Mat frame) {
        MatOfByte buffer = new MatOfByte();
        Imgcodecs.imencode(".png", frame, buffer);
        return new Image(new ByteArrayInputStream(buffer.toArray()));
    }
}
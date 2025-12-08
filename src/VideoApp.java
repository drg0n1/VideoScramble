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
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;
import org.opencv.videoio.Videoio;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayInputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;

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
    private ImageView encSourceView; // Vue originale
    private ImageView encResultView; // Vue cryptée
    private TextField rEncField = new TextField("50");
    private TextField sEncField = new TextField("10");
    private Button generateButton; // Remplacé le recordButton
    private ProgressBar progressBar;

    // UI Décryption
    private ImageView decSourceView; // Vue cryptée (input)
    private ImageView decResultView; // Vue décryptée (output)
    private TextField rDecField = new TextField("50");
    private TextField sDecField = new TextField("10");
    private Button crackButton;

    // OpenCV
    private VideoCapture capture; // Pour la preview uniquement
    private ScheduledExecutorService timer;
    private boolean cameraActive = false;
    private String currentVideoPath = "";

    public static void main(String[] args) {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Video Scramble & Cracker Pro");

        // Barre de sélection de source (en haut)
        HBox sourceBox = createSourceSelection();

        // Création des onglets
        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        createEncryptTab();
        createDecryptTab();

        tabPane.getTabs().addAll(encryptTab, decryptTab);

        // Barre de statut (en bas)
        statusLabel = new Label("Prêt. Sélectionnez une source.");
        statusLabel.setPadding(new Insets(5));
        statusLabel.setTextFill(Color.DARKBLUE);

        // Layout principal
        BorderPane root = new BorderPane();
        root.setTop(sourceBox);
        root.setCenter(tabPane);
        root.setBottom(statusLabel);

        Scene scene = new Scene(root, 1100, 750);
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> stopAcquisition());
        primaryStage.show();
    }

    private void createEncryptTab() {
        encryptTab = new Tab("🔒 CHIFFREMENT (Encoder)");

        // Vues
        encSourceView = createImageView();
        encResultView = createImageView();

        HBox videoBox = new HBox(20,
                new VBox(5, new Label("Source (Originale)"), encSourceView),
                new VBox(5, new Label("Résultat (Chiffré)"), encResultView)
        );
        videoBox.setAlignment(Pos.CENTER);
        videoBox.setPadding(new Insets(15));

        // Contrôles
        HBox controls = new HBox(15);
        controls.setAlignment(Pos.CENTER);
        controls.setPadding(new Insets(15));

        generateButton = new Button("Générer Vidéo Complète (.avi)");
        generateButton.setStyle("-fx-base: #ffcccc; -fx-font-weight: bold;");
        generateButton.setOnAction(e -> generateEncryptedVideo());

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(200);
        progressBar.setVisible(false);

        controls.getChildren().addAll(
                new Label("Clé R:"), rEncField,
                new Label("Clé S:"), sEncField,
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

        // Vues
        decSourceView = createImageView();
        decResultView = createImageView();

        HBox videoBox = new HBox(20,
                new VBox(5, new Label("Entrée (Chiffrée)"), decSourceView),
                new VBox(5, new Label("Sortie (Déchiffrée)"), decResultView)
        );
        videoBox.setAlignment(Pos.CENTER);
        videoBox.setPadding(new Insets(15));

        // Contrôles
        HBox controls = new HBox(15);
        controls.setAlignment(Pos.CENTER);
        controls.setPadding(new Insets(15));

        crackButton = new Button("Auto-Crack (Trouver la clé)");
        crackButton.setStyle("-fx-base: #ccffcc; -fx-font-weight: bold;");
        crackButton.setOnAction(e -> startCracking());

        controls.getChildren().addAll(
                new Label("Clé R:"), rDecField,
                new Label("Clé S:"), sDecField,
                new Separator(Orientation.VERTICAL),
                crackButton
        );

        BorderPane content = new BorderPane();
        content.setCenter(videoBox);
        content.setBottom(controls);
        decryptTab.setContent(content);
    }

    private ImageView createImageView() {
        ImageView iv = new ImageView();
        iv.setFitWidth(480);
        iv.setPreserveRatio(true);
        iv.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 10, 0, 0, 0);");
        return iv;
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
                "videos/output_encrypted.avi",
                "videos/videoTest.m4v"
        );
        videoSelector.setEditable(true);
        videoSelector.setValue("videos/cat.avi");
        videoSelector.setPrefWidth(200);

        // On désactive le bouton de génération si c'est une webcam (car on ne peut pas "pré-générer" le futur)
        webcamRadio.selectedProperty().addListener((obs, oldVal, newVal) -> {
            videoSelector.setDisable(newVal);
            if (generateButton != null) generateButton.setDisable(newVal);
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

    // --- LOGIQUE OPENCV ---

    private void grabFrame() {
        if (capture != null && capture.isOpened()) {
            Mat frame = new Mat();
            if (capture.read(frame)) {

                // On vérifie quel onglet est actif pour savoir quoi faire
                boolean isEncryptMode = encryptTab.isSelected();

                if (isEncryptMode) {
                    processEncryptionPipeline(frame);
                } else {
                    processDecryptionPipeline(frame);
                }

                frame.release();
            } else {
                if (!webcamRadio.isSelected()) capture.set(Videoio.CAP_PROP_POS_FRAMES, 0);
            }
        }
    }

    private void processEncryptionPipeline(Mat cleanFrame) {
        // Afficher la source
        Image imgSource = mat2Image(cleanFrame);

        // Cloner et Encrypter (pour la preview)
        Mat processedFrame = cleanFrame.clone();
        int r = parseSafe(rEncField.getText());
        int s = parseSafe(sEncField.getText());
        LineLogic.encrypt(processedFrame, r, s);

        // Afficher le résultat
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

    private void generateEncryptedVideo() {
        if (webcamRadio.isSelected()) {
            statusLabel.setText("Impossible de générer un fichier depuis la webcam (flux infini).");
            return;
        }

        String inputPath = videoSelector.getValue();
        if (inputPath == null || inputPath.isEmpty()) return;

        // Configuration UI
        generateButton.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(0);
        statusLabel.setText("Génération du fichier crypté en cours...");

        // Clés
        final int rKey = parseSafe(rEncField.getText());
        final int sKey = parseSafe(sEncField.getText());

        // Tâche de fond
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

                // Initialisation writer
                String outputPath = "videos/output_encrypted.avi";
                int fourcc = VideoWriter.fourcc('M', 'J', 'P', 'G');
                VideoWriter writerGen = new VideoWriter(outputPath, fourcc, fps, size, true);

                if (!writerGen.isOpened()) {
                    capGen.release();
                    updateMessage("Erreur : Impossible de créer le fichier de sortie.");
                    return null;
                }

                // Boucle de traitement
                Mat frame = new Mat();
                Mat resized = new Mat(); // Pour la correction de taille si besoin
                int framesProcessed = 0;

                while (capGen.read(frame)) {
                    // Vérification annulation
                    if (isCancelled()) break;

                    // Redimensionnement si nécessaire (pairs)
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
                }

                // Nettoyage
                frame.release();
                resized.release();
                capGen.release();
                writerGen.release();
                updateMessage("Génération terminée : " + outputPath);
                return null;
            }
        };

        // Callbacks UI
        task.setOnSucceeded(e -> {
            statusLabel.setText(task.getMessage());
            progressBar.setVisible(false);
            generateButton.setDisable(false);

            // Ajouter le nouveau fichier à la liste si nécessaire
            if (!videoSelector.getItems().contains("videos/output_encrypted.avi")) {
                videoSelector.getItems().add("videos/output_encrypted.avi");
            }
            // Sélectionner le fichier généré pour pouvoir le tester immédiatement
        });

        task.setOnFailed(e -> {
            statusLabel.setText("Erreur lors de la génération.");
            progressBar.setVisible(false);
            generateButton.setDisable(false);
            e.getSource().getException().printStackTrace();
        });

        // Lancer le thread
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
            VideoCracker.CrackingResult result = VideoCracker.crackVideo(path);

            Platform.runLater(() -> {
                crackButton.setDisable(false);
                if (result.success) {
                    statusLabel.setText("🔓 CLÉ TROUVÉE ! R=" + result.bestR + ", S=" + result.bestS);
                    rDecField.setText(String.valueOf(result.bestR));
                    sDecField.setText(String.valueOf(result.bestS));
                } else {
                    statusLabel.setText("Échec : Vidéo trop sombre ou algorithme inefficace.");
                }
            });
        }).start();
    }

    // --- UTILITAIRES ---
    private void startVideo() {
        stopAcquisition(); // Reset complet

        if (webcamRadio.isSelected()) {
            capture = new VideoCapture(0);
            statusLabel.setText("Source : Webcam");
        } else {
            String path = videoSelector.getValue();
            capture = new VideoCapture(path);
            statusLabel.setText("Source : " + path);
        }

        if (capture.isOpened()) {
            cameraActive = true;
            Runnable frameGrabber = this::grabFrame;
            timer = Executors.newSingleThreadScheduledExecutor();
            timer.scheduleAtFixedRate(frameGrabber, 0, 33, TimeUnit.MILLISECONDS);
        } else {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Impossible d'ouvrir la vidéo.");
            alert.showAndWait();
        }
    }

    private int parseSafe(String txt) {
        try { return Integer.parseInt(txt); } catch (Exception e) { return 0; }
    }

    private Image mat2Image(Mat frame) {
        MatOfByte buffer = new MatOfByte();
        Imgcodecs.imencode(".png", frame, buffer);
        return new Image(new ByteArrayInputStream(buffer.toArray()));
    }

    private void stopAcquisition() {
        if (timer != null && !timer.isShutdown()) {
            timer.shutdown();
            try { timer.awaitTermination(33, TimeUnit.MILLISECONDS); }
            catch (InterruptedException e) { e.printStackTrace(); }
        }
        if (capture != null && capture.isOpened()) capture.release();
        cameraActive = false;
    }
}
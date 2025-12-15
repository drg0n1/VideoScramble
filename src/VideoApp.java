/**
 Nom :  Belhadj, Bernard
 Prénom : Quentin, Elena
 Groupe : S5-A
 Projet : VideoScramble

 Description : Cette classe est le point d'entrée de l'interface graphique (JavaFX). Elle orchestre la capture vidéo, l'affichage UI, les pipelines de traitement (chiffrement/déchiffrement) et les interactions utilisateur.
 */

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
    private Label fpsLabel;

    // UI Onglets
    private TabPane tabPane;
    private Tab encryptTab;
    private Tab decryptTab;

    // UI Encryption
    private ImageView encSourceView;
    private ImageView encResultView;
    private TextField rEncField = new TextField("50");
    private TextField sEncField = new TextField("10");
    private ToggleButton muteEncButton;
    private Button generateButton;

    // UI Décryption
    private ImageView decSourceView;
    private ImageView decResultView;
    private TextField rDecField = new TextField("50");
    private TextField sDecField = new TextField("10");
    private ToggleButton muteDecButton;
    private Button crackButton;

    // Moteurs
    private VideoCapture capture;
    private ScheduledExecutorService timer;
    private boolean cameraActive = false;

    // Audio Player
    private StreamingAudioPlayer audioPlayer = new StreamingAudioPlayer();


    /**
     * Point d'entrée principal de l'application. Charge la librairie native OpenCV.
     * @param args Arguments de la ligne de commande.
     */
    public static void main(String[] args) {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        launch(args);
    }

    /**
     * Initialisation de l'interface graphique JavaFX (Stage, Scene, Layouts).
     * @param primaryStage Le stage principal de l'application.
     */
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

    /**
     * Crée et configure l'onglet dédié au chiffrement (UI et contrôles).
     */
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

        // Audio Control
        muteEncButton = new ToggleButton("Mute Audio");
        muteEncButton.setOnAction(e -> {
            audioPlayer.setMute(muteEncButton.isSelected());
            muteDecButton.setSelected(muteEncButton.isSelected());
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
                generateButton
        );

        BorderPane content = new BorderPane();
        content.setCenter(videoBox);
        content.setBottom(controls);
        encryptTab.setContent(content);
    }

    /**
     * Crée et configure l'onglet dédié au déchiffrement (UI, contrôles et bouton Crack).
     */
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
            muteEncButton.setSelected(muteDecButton.isSelected());
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

    /**
     * Crée la barre supérieure permettant de choisir entre Webcam et Fichier Vidéo.
     * @return HBox contenant les contrôles de sélection.
     */
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

        fpsLabel = new Label("FPS: -");
        fpsLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #555;");
        fpsLabel.setPadding(new Insets(0, 10, 0, 10));

        // Logic UI : Webcam vs Fichier
        webcamRadio.selectedProperty().addListener((obs, oldVal, newVal) -> {
            boolean isWebcam = newVal;
            videoSelector.setDisable(isWebcam);
            if (generateButton != null) generateButton.setDisable(isWebcam);

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

        // Ajout du fpsLabel dans la HBox
        HBox box = new HBox(15, new Label("Source :"), webcamRadio, videoRadio, videoSelector, startButton, fpsLabel);
        box.setPadding(new Insets(15));
        box.setAlignment(Pos.CENTER);
        box.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #ccc; -fx-border-width: 0 0 1 0;");
        return box;
    }

    /**
     * Initialise la capture vidéo (Webcam ou Fichier) et lance le thread de récupération des frames.
     * Initialise également le lecteur audio si la source est un fichier.
     */
    private void startVideo() {
        stopAcquisition();

        if (webcamRadio.isSelected()) {
            // MODE WEBCAM
            capture = new VideoCapture(0);
            statusLabel.setText("Source : Webcam (Audio désactivé)");
            fpsLabel.setText("FPS: Web"); // Pas de FPS fixe pour webcam brute ici
            audioPlayer.stop();

        } else {
            // MODE FICHIER
            String path = videoSelector.getValue();
            capture = new VideoCapture(path);

            // Récupération FPS
            double fps = capture.get(Videoio.CAP_PROP_FPS);
            if (fps <= 0) fps = 30.0;

            statusLabel.setText("Source : " + path);

            fpsLabel.setText(String.format("FPS: %.2f", fps));

            // Chargement Audio
            audioPlayer.setFps(fps);
            audioPlayer.load(path);
            audioPlayer.play();
            updateAudioSettings();
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

    /**
     * Arrête l'acquisition vidéo, ferme les threads, le timer et libère les ressources audio et vidéo.
     */
    private void stopAcquisition() {
        if (timer != null && !timer.isShutdown()) {
            timer.shutdown();
            try { timer.awaitTermination(33, TimeUnit.MILLISECONDS); }
            catch (InterruptedException e) { e.printStackTrace(); }
        }
        if (capture != null && capture.isOpened()) capture.release();

        audioPlayer.stop();
        cameraActive = false;
    }

    /**
     * Met à jour les paramètres du lecteur audio (clés R/S et mode) en fonction de l'onglet actif et des champs de texte.
     */

    private void updateAudioSettings() {
        boolean isEncryptMode = encryptTab.isSelected();
        int r, s;

        if (isEncryptMode) {
            r = parseSafe(rEncField.getText());
            s = parseSafe(sEncField.getText());
            audioPlayer.setEffect(true, false, r, s);
        } else {
            r = parseSafe(rDecField.getText());
            s = parseSafe(sDecField.getText());
            audioPlayer.setEffect(false, true, r, s);
        }
    }

    /**
     * Méthode appelée périodiquement pour récupérer une frame, la traiter et mettre à jour l'UI.
     */
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
                if (!webcamRadio.isSelected()) {
                    capture.set(Videoio.CAP_PROP_POS_FRAMES, 0);
                }
            }
        }
    }

    /**
     * Traite une frame pour l'affichage dans l'onglet Chiffrement (Original -> Chiffré).
     * @param cleanFrame La frame brute lue depuis la source.
     */
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

    /**
     * Traite une frame pour l'affichage dans l'onglet Déchiffrement (Chiffré -> Déchiffré).
     * @param inputFrame La frame source (supposée être la vidéo chiffrée ou à déchiffrer).
     */
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


    /**
     * Lance une tâche de fond pour générer un fichier vidéo (.avi) chiffré ainsi que son fichier audio associé (.wav).
     */
    private void generateEncryptedVideo() {
        if (webcamRadio.isSelected()) {
            statusLabel.setText("Impossible de générer un fichier depuis la webcam.");
            return;
        }

        String inputPath = videoSelector.getValue();
        if (inputPath == null || inputPath.isEmpty()) return;

        generateButton.setDisable(true);
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

                double fps = capGen.get(Videoio.CAP_PROP_FPS);
                if (fps <= 0) fps = 30.0;
                int width = (int) capGen.get(Videoio.CAP_PROP_FRAME_WIDTH);
                int height = (int) capGen.get(Videoio.CAP_PROP_FRAME_HEIGHT);

                if (width % 2 != 0) width--;
                if (height % 2 != 0) height--;
                Size size = new Size(width, height);

                String outputVideoPath = "videos/output_encrypted.avi";
                int fourcc = VideoWriter.fourcc('M', 'J', 'P', 'G');
                VideoWriter writerGen = new VideoWriter(outputVideoPath, fourcc, fps, size, true);

                updateMessage("Export de l'audio crypté...");
                String outputAudioPath = "videos/output_encrypted.wav";
                AudioExporter.export(inputPath, outputAudioPath, rKey, sKey, true, fps);

                if (!writerGen.isOpened()) {
                    capGen.release();
                    updateMessage("Erreur Writer Vidéo.");
                    return null;
                }

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
                    updateMessage("Génération : Frame " + framesProcessed);
                }

                frame.release();
                resized.release();
                capGen.release();
                writerGen.release();

                updateMessage("Succès ! Vidéo: output_encrypted.avi + Audio: output_encrypted.wav");
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            statusLabel.setText(task.getMessage());
            generateButton.setDisable(false);

            if (!videoSelector.getItems().contains("videos/output_encrypted.avi")) {
                videoSelector.getItems().add("videos/output_encrypted.avi");
            }
        });

        task.setOnFailed(e -> {
            statusLabel.setText("Erreur lors de la génération.");
            generateButton.setDisable(false);
            e.getSource().getException().printStackTrace();
        });

        new Thread(task).start();
    }

    /**
     * Lance l'algorithme de cassage (Brute-force intelligent) dans un thread séparé et met à jour l'UI avec les clés trouvées.
     */
    private void startCracking() {
        String path = videoSelector.getValue();
        if (webcamRadio.isSelected()) {
            statusLabel.setText("Sélectionnez un fichier vidéo pour le crack.");
            return;
        }

        statusLabel.setText("Analyse en cours... (Cela peut prendre quelques secondes)");
        crackButton.setDisable(true);

        new Thread(() -> {
            long startTime = System.currentTimeMillis();

            // Lancement du crack
            VideoCracker.CrackingResult result = VideoCracker.crackVideo(path);

            long endTime = System.currentTimeMillis();
            double durationSeconds = (endTime - startTime) / 1000.0;

            Platform.runLater(() -> {
                crackButton.setDisable(false);
                if (result.success) {
                    statusLabel.setText(String.format("🔓 CLÉ TROUVÉE ! R=%d, S=%d (Temps: %.3fs)", result.bestR, result.bestS, durationSeconds));
                    rDecField.setText(String.valueOf(result.bestR));
                    sDecField.setText(String.valueOf(result.bestS));

                    updateAudioSettings();
                } else {
                    statusLabel.setText("Échec : Vidéo trop sombre ou algorithme inefficace. (Temps: " + durationSeconds + "s)");
                }
            });
        }).start();
    }

    /**
     * Helper pour créer une ImageView stylisée.
     * @return Une instance configurée de ImageView.
     */
    private ImageView createImageView() {
        ImageView iv = new ImageView();
        iv.setFitWidth(450);
        iv.setPreserveRatio(true);
        iv.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 10, 0, 0, 0);");
        return iv;
    }

    /**
     * Helper pour parser un entier depuis une String sans lever d'exception (retourne 0 en cas d'erreur).
     * @param txt La chaîne à convertir.
     * @return L'entier converti ou 0.
     */
    private int parseSafe(String txt) {
        try { return Integer.parseInt(txt); } catch (Exception e) { return 0; }
    }

    /**
     * Convertit une matrice OpenCV (Mat) en Image JavaFX.
     * @param frame La frame OpenCV BGR.
     * @return L'objet Image pour affichage JavaFX.
     */
    private Image mat2Image(Mat frame) {
        MatOfByte buffer = new MatOfByte();
        Imgcodecs.imencode(".png", frame, buffer);
        return new Image(new ByteArrayInputStream(buffer.toArray()));
    }
}
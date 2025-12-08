import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;

import java.io.ByteArrayInputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;

public class VideoApp extends Application {

    // UI Elements
    private ImageView encryptedView;
    private ImageView decryptedView;

    // UI Source Selection
    private RadioButton webcamRadio;
    private RadioButton videoRadio;
    private ComboBox<String> videoSelector;
    private Button startButton;

    // Inputs Encrypt
    private TextField rEncField = new TextField("50");
    private TextField sEncField = new TextField("10");

    // Inputs Decrypt
    private TextField rDecField = new TextField("50");
    private TextField sDecField = new TextField("10");

    private CheckBox lockCheckbox = new CheckBox("Forcer même clés");

    // OpenCV
    private VideoCapture capture;
    private ScheduledExecutorService timer;
    private boolean cameraActive = false;

    public static void main(String[] args) {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Video Scramble");

        // Configuration de la source vidéo (en haut)
        HBox sourceBox = createSourceSelection();

        // Contrôles des Clés (en bas)
        GridPane controls = new GridPane();
        controls.setPadding(new Insets(10));
        controls.setHgap(10);
        controls.setVgap(10);
        controls.setAlignment(Pos.CENTER);

        controls.add(new Label("Clé Encryption (R, S):"), 0, 0);
        controls.add(new Label("R:"), 1, 0);
        controls.add(rEncField, 2, 0);
        controls.add(new Label("S:"), 3, 0);
        controls.add(sEncField, 4, 0);

        controls.add(new Label("Clé Décryption (R, S):"), 0, 1);
        controls.add(new Label("R:"), 1, 1);
        controls.add(rDecField, 2, 1);
        controls.add(new Label("S:"), 3, 1);
        controls.add(sDecField, 4, 1);

        controls.add(lockCheckbox, 5, 1);

        configureInputs();

        // Affichage des bidéo (au centre)
        encryptedView = new ImageView();
        encryptedView.setFitWidth(480);
        encryptedView.setPreserveRatio(true);

        decryptedView = new ImageView();
        decryptedView.setFitWidth(480);
        decryptedView.setPreserveRatio(true);

        HBox videoBox = new HBox(20,
                new VBox(5, new Label("Vidéo Chiffrée"), encryptedView),
                new VBox(5, new Label("Vidéo Déchiffrée"), decryptedView)
        );
        videoBox.setAlignment(Pos.CENTER);
        videoBox.setPadding(new Insets(15));

        // --- Layout Principal ---
        BorderPane root = new BorderPane();
        root.setTop(sourceBox);    // Menu de sélection en haut
        root.setCenter(videoBox);
        root.setBottom(controls);

        Scene scene = new Scene(root, 1000, 650);
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> stopAcquisition());
        primaryStage.show();
    }

    private HBox createSourceSelection() {
        ToggleGroup group = new ToggleGroup();

        webcamRadio = new RadioButton("Webcam");
        webcamRadio.setToggleGroup(group);

        videoRadio = new RadioButton("Fichier Vidéo");
        videoRadio.setToggleGroup(group);
        videoRadio.setSelected(true); // Par défaut sur vidéo

        // Liste déroulante
        videoSelector = new ComboBox<>();
        videoSelector.getItems().addAll("videos/cat.avi", "videos/video.mp4");
        videoSelector.setEditable(true); // Permet d'écrire un chemin manuel
        videoSelector.setValue("videos/cat.avi"); // Valeur par défaut
        videoSelector.setPrefWidth(200);

        // Désactiver le sélecteur si on choisit la webcam
        videoSelector.disableProperty().bind(webcamRadio.selectedProperty());

        startButton = new Button("Démarrer");
        startButton.setStyle("-fx-font-weight: bold;");
        startButton.setOnAction(e -> startVideo());

        HBox box = new HBox(15,
                new Label("Source :"),
                webcamRadio,
                videoRadio,
                videoSelector,
                startButton
        );
        box.setPadding(new Insets(15));
        box.setAlignment(Pos.CENTER);
        box.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #ccc; -fx-border-width: 0 0 1 0;");

        return box;
    }

    private void configureInputs() {
        lockCheckbox.setSelected(true);
        rDecField.setDisable(true);
        sDecField.setDisable(true);

        lockCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            rDecField.setDisable(newVal);
            sDecField.setDisable(newVal);
            if (newVal) {
                rDecField.setText(rEncField.getText());
                sDecField.setText(sEncField.getText());
            }
        });

        // Synchronisation si lock est activé
        rEncField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (lockCheckbox.isSelected()) rDecField.setText(newVal);
        });
        sEncField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (lockCheckbox.isSelected()) sDecField.setText(newVal);
        });
    }

    private void startVideo() {
        // Arrêter l'ancienne acquisition
        stopAcquisition();

        // Si la webcam est sélectionnée
        boolean useWebcam = webcamRadio.isSelected();

        // Selection de la source
        if (useWebcam) {
            capture = new VideoCapture(0);
            System.out.println("Démarrage de la webcam...");
        } else {
            String filename = videoSelector.getValue();
            capture = new VideoCapture(filename);
            System.out.println("Chargement du fichier vidéo : " + filename);
        }

        if (capture.isOpened()) {
            cameraActive = true;
            Runnable frameGrabber = this::grabFrame;
            timer = Executors.newSingleThreadScheduledExecutor();
            timer.scheduleAtFixedRate(frameGrabber, 0, 33, TimeUnit.MILLISECONDS);
        } else {
            System.err.println("ERREUR : Impossible d'ouvrir le flux vidéo (fichier ou webcam).");
            Alert alert = new Alert(Alert.AlertType.ERROR, "Impossible d'ouvrir la source vidéo.\nVérifiez le chemin du fichier ou la connexion webcam.");
            alert.showAndWait();
        }
    }

    private void grabFrame() {
        if (capture.isOpened()) {
            Mat frame = new Mat();
            if (capture.read(frame)) {
                Mat encryptedFrame = frame.clone();

                int rEnc = parseSafe(rEncField.getText());
                int sEnc = parseSafe(sEncField.getText());

                LineLogic.encrypt(encryptedFrame, rEnc, sEnc);

                Mat decryptedFrame = encryptedFrame.clone();

                int rDec = parseSafe(rDecField.getText());
                int sDec = parseSafe(sDecField.getText());

                LineLogic.decrypt(decryptedFrame, rDec, sDec);

                Image imageEnc = mat2Image(encryptedFrame);
                Image imageDec = mat2Image(decryptedFrame);

                Platform.runLater(() -> {
                    encryptedView.setImage(imageEnc);
                    decryptedView.setImage(imageDec);
                });

                frame.release();
                encryptedFrame.release();
                decryptedFrame.release();
            } else {
                capture.set(org.opencv.videoio.Videoio.CAP_PROP_POS_FRAMES, 0);
            }
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
        if (capture != null && capture.isOpened()) {
            capture.release();
        }
        cameraActive = false;
    }
}
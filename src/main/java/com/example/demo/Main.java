package com.example.demo;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.CssMetaData;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Border;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.prefs.Preferences;

public class Main extends Application implements Initializable {

    @FXML
    private ListView<String> filesListView;

    @FXML
    private TextField numberTextField;

    @FXML
    private Label lblSource;

    @FXML
    private Label lblDestination;

    @FXML
    private ImageView imagePreview;

    @FXML
    private CheckBox checkOnlyJpg;
    @FXML
    private CheckBox checkExist;

    @FXML
    private Pane dragdrop;

    @FXML
    private Label lblZoom;

    @FXML
    private Slider sliderZoom;
    @FXML
    private Label numFoto;

    private Stage stage;

    ExecutorService executor = Executors.newSingleThreadExecutor();
    CompletableFuture<Boolean> future;

    private DirectoryChooser directoryChooser = new DirectoryChooser();

    private Optional<String> sourceAddress = Optional.empty();

    private Optional<String> destinationAddress = Optional.empty();

    private Optional<List<String>> listFiles = Optional.empty();

    private Optional<ObservableList<String>> items = Optional.empty();

    private ObservableList<String> obsList = FXCollections.observableArrayList(new ArrayList<>());

    private int index = 0;

    private int scale = 4;

    private boolean zoomed = false;

    private Preferences pref;

    public static void main (String[] args) {
        Application.launch(args);
        //launch();
    }

    @Override
    public void start(Stage stage) throws Exception {

        //check program validity
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://raw.githubusercontent.com/DiLilloDaniele/SmistaFoto/master/settings.json"))
                .build();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(i -> {
                    if(i.contains("true")) {
                        System.out.println("PROGRAMMA ATTIVO");
                    } else {
                        System.exit(1);
                    }
                })
                .join();

        /*Thread.setDefaultUncaughtExceptionHandler( new Thread.UncaughtExceptionHandler(){
            public void uncaughtException(Thread t, Throwable e) {
                System.out.println("*****Yeah, Caught the Exception*****");
                //e.printStackTrace(); // you can use e.printStackTrace ( printstream ps )
                System.out.println(e.getCause().getCause().toString());
                try {
                    ExceptionManager.getInstance().writeToFile(e.getCause().getCause().toString());
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });*/

        this.stage = stage;
        //FXMLLoader.load(ClassLoader.getSystemResource("layouts/main.fxml"));
        Parent root = FXMLLoader.load(Main.class.getResource("main.fxml"));

        Scene scene = new Scene(root, 1080, 720);
        stage.setTitle("Smista Foto");
        stage.setScene(scene);
        stage.show();
    }

    @FXML
    private void order() {
        if(sourceAddress.isPresent()) {
            List<String> newList = obsList.stream().sorted(String::compareTo).collect(Collectors.toList());
            resetList(newList);
        }
    }

    @FXML
    private void checkExistent() {
        if(sourceAddress.isPresent() && destinationAddress.isPresent()) {
            setPhotoList();
        }
    }

    @FXML
    private void Undo() {
        FileManager.lastPhotoMoved.ifPresent(path -> {
            obsList.add(0, path.getValue());
            FileManager.deleteFile();
        });
    }

    @FXML
    private void zoomIn() {
        imagePreview.setScaleX(scale);
        imagePreview.setScaleY(scale);
        zoomed = true;
    }

    @FXML
    private void zoomOut() {
        imagePreview.setScaleX(1);
        imagePreview.setScaleY(1);
        imagePreview.setTranslateX(0);
        imagePreview.setTranslateY(0);
        zoomed = false;
    }

    @FXML
    private void zoomBtnIn() {
        imagePreview.setFitWidth(imagePreview.getFitWidth() + 200);
        numberTextField.requestFocus();
    }

    @FXML
    private void zoomBtnOut() {
        imagePreview.setFitWidth(imagePreview.getFitWidth() - 200);
        numberTextField.requestFocus();
    }

    @FXML
    private void handleKeyPressed(KeyEvent ke){
        if(ke.getCode() == KeyCode.ENTER) {
            moveImage();
            numberTextField.setText("");
        }
    }

    @FXML
    private void saveHandle(ActionEvent event) {
        moveImage();
    }

    private void createAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information Dialog");
        //alert.setHeaderText("Look, an Information Dialog");
        alert.setContentText(msg);

        alert.showAndWait();
    }
    private void moveImage() {
        if(sourceAddress.isPresent() && destinationAddress.isPresent()) {
            String num = numberTextField.getText();
            if(num.equals("") || num.equals(" ")) {
                createAlert("Inserire un nome/numero valido");
                return;
            }
            if(!obsList.isEmpty()) {
                String nameFile = obsList.get(index);
                String fileToCopy = sourceAddress.get();
                String dest = destinationAddress.get();
                FileManager.moveImage(fileToCopy, dest, num, nameFile, checkOnlyJpg.isSelected());

                //imposto immagine successiva selezionata
                obsList.remove(index);
                numFoto.setText(obsList.size() + " Foto");

                if(index == obsList.size()) {
                    index = index - 1;
                }
                if(!obsList.isEmpty()) {
                    filesListView.getSelectionModel().select(index);
                    setImage(filesListView.getItems().get(index));
                } else {
                    resetImage();
                }
            } else {
                resetImage();
            }
        } else {
            createAlert("Selezionare sorgente/destinazione");
        }
    }

    private void resetImage() {
        numFoto.setText("0 Foto");
        setImage("");
        dragdrop.setVisible(true);
        imagePreview.setVisible(false);
    }

    private void setPhotoList() {
        if(sourceAddress.isPresent()) {
            resetList();
            List<String> list = FileManager.getListOfImages(sourceAddress.get(), destinationAddress, checkOnlyJpg.isSelected(), checkExist.isSelected(), obsList);
            int count = list.size();
            numFoto.setText(count + " Foto");
            setListOfFiles();
            /*
            future = new CompletableFuture();
            //fintanto che non finisce di importare non si può scegliere la destinazione
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    System.out.println(" ZIOBOOOO");
                    List<String> list = FileManager.getListOfImages(sourceAddress.get(), destinationAddress, checkOnlyJpg.isSelected(), checkExist.isSelected(), obsList);
                    //obsList = FXCollections.observableArrayList(list);
                    int count = list.size();
                    System.out.println(" FATTO");
                    future.complete(true);
                }
            });

            future.thenRunAsync(() -> {
                System.out.println("END IMPORT");
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        System.out.println("AGGIORNO LISTA");
                        numFoto.setText(obsList.size() + " Foto");

                        if(!obsList.isEmpty())
                            setImage(obsList.get(0));
                    }
                });
            });
            */
            dragdrop.setVisible(false);
            imagePreview.setVisible(true);
        }
    }

    private void resetList() {
        obsList = FXCollections.observableArrayList(new ArrayList<>());
        filesListView.setItems(obsList);
    }

    private void resetList(List<String> list) {
        obsList = FXCollections.observableArrayList(list);
        filesListView.setItems(obsList);
    }

    @FXML
    private void onSourceClick(ActionEvent event) {
        //TODO try catch su NullPointerException???
        File selectedDirectory = directoryChooser.showDialog(this.stage);
        this.lblSource.setText(selectedDirectory.getAbsolutePath());
        sourceAddress = Optional.of(selectedDirectory.getAbsolutePath());
        checkOnlyJpg.setSelected(!FileManager.isThereAtLeastARaw(sourceAddress.get()));
        setPhotoList();
    }

    private void setListOfFiles() {
        if(!obsList.isEmpty())
            setImage(obsList.get(0));
    }

    @FXML
    private void infoProgram() {
        createAlert("Selezionare prima di tutto la cartella sorgente (con le foto da smistare) e la cartella destinazione.\n" +
                "Le foto JPEG presenti nella cartella sorgente verranno mostrate nella lista a lato. Selezionandole sarà" +
                "possibile smistarle scrivendo il nome della cartella che si vuole creare, nell'apposita casella di testo.\n Version: 3.04.23");
    }

    @FXML
    private void onDestinationClick(ActionEvent event) {
        sourceAddress.ifPresent(s -> directoryChooser.setInitialDirectory(new File(s)));
        File selectedDirectory = directoryChooser.showDialog(this.stage);
        this.lblDestination.setText(selectedDirectory.getAbsolutePath());
        destinationAddress = Optional.of(selectedDirectory.getAbsolutePath());
        pref = Preferences.userRoot().node(this.getClass().getName());
        pref.put("lastPath", selectedDirectory.getAbsolutePath());
        setPhotoList();
    }

    private void setImage(String name) {
        try {
            imagePreview.setRotate(0);
            AnchorPane.setTopAnchor(imagePreview, 0.0);
            Image image = new Image("file:///" + sourceAddress.get() + "/" + name);
            File file = new File(sourceAddress.get() + "/" + name);
            Metadata metadata = ImageMetadataReader.readMetadata(file);
            Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            try {
                int orientation = directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
                if(orientation == 8) {
                    AnchorPane.setTopAnchor(imagePreview, 150.0);
                    imagePreview.setRotate(-90);
                }
            } catch (Exception me) {
                me.printStackTrace();
                System.out.println("ERROR");
            }
            imagePreview.setImage(image);
        } catch (Exception e) {

            e.printStackTrace();
        }

    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        filesListView.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
                String name = filesListView.getSelectionModel().getSelectedItem();
                if(sourceAddress.isPresent()) {
                    try {
                        setImage(name);
                        index = filesListView.getSelectionModel().getSelectedIndex();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        checkExist.selectedProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                numberTextField.requestFocus();
            }
        });

        checkOnlyJpg.selectedProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                numberTextField.requestFocus();
            }
        });

        dragdrop.setOnDragOver(new EventHandler<DragEvent>() {
            @Override
            public void handle(DragEvent event) {
                if(event.getGestureSource() != dragdrop && event.getDragboard().hasFiles()) {
                    dragdrop.setStyle("-fx-border-color: black; -fx-background-color: lightgrey;");
                    event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                }

                event.consume();
            }
        });

        dragdrop.setOnDragExited(new EventHandler<DragEvent>() {
            @Override
            public void handle(DragEvent event) {
                dragdrop.setStyle("-fx-border-color: white; -fx-background-color: lightgrey;");
            }
        });

        dragdrop.setOnDragDropped(new EventHandler<DragEvent>() {
            @Override
            public void handle(DragEvent event) {
                Dragboard db = event.getDragboard();
                boolean success = false;
                if(db.hasFiles()) {
                    String source;
                    boolean isFolder = false;
                    List<String> files;
                    Path path = Paths.get(db.getFiles().get(0).getAbsolutePath());

                    if(db.getFiles().stream().filter(i -> i.isDirectory()).collect(Collectors.toList()).size() > 0 && db.getFiles().size() == 1) {
                        source = path.toString();
                        isFolder = true;
                    } else {
                        source = path.getParent().toString();
                    }
                    //check if there is at least a raw
                    checkOnlyJpg.setSelected(!FileManager.isThereAtLeastARaw(source));
                    lblSource.setText(source);
                    sourceAddress = Optional.of(source);
                    if(isFolder) {
                        resetList();
                        files = FileManager.getListOfImages(source, destinationAddress, checkOnlyJpg.isSelected(), checkExist.isSelected(), obsList);
                    } else {
                        files = db.getFiles().stream().map(i -> i.getName())
                                .filter(i -> i.toLowerCase().endsWith(".jpg")
                                        || i.toLowerCase().endsWith(".jpeg"))
                                .collect(Collectors.toList());
                        obsList = FXCollections.observableArrayList(files);
                        filesListView.setItems(obsList);
                    }

                    numFoto.setText(files.size() + " Foto");
                    setListOfFiles();
                    success = true;
                    dragdrop.setVisible(false);
                    imagePreview.setVisible(true);
                }
                dragdrop.setStyle("-fx-border-color: white; -fx-background-color: lightgrey;");
                event.setDropCompleted(success);
                event.consume();
            }
        });

        imagePreview.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {

                if(event.getButton() == MouseButton.SECONDARY) {
                    zoomOut();
                } else {
                    if(!zoomed) {
                        double rotation = imagePreview.getRotate();

                        double mouseX = event.getX();
                        double mouseY = event.getY();
                        int x = 1;
                        int y = 1;

                        if(rotation == -90) {
                            double aspectRatio = imagePreview.getFitWidth() / imagePreview.getImage().getWidth();
                            double realWidth = imagePreview.getImage().getWidth() * aspectRatio;
                            double realHeight = imagePreview.getImage().getHeight() * aspectRatio;
                            double centerX = realHeight / 2;
                            double centerY = realWidth / 2;
                            System.out.println("( " + centerX + ", " + centerY + " )");

                            if(mouseX < centerY)
                                x = x * (-1);
                            if(mouseY > centerX)
                                y = y * (-1);

                            imagePreview.setTranslateX((y * Math.abs(mouseY - centerX)) * scale);
                            imagePreview.setTranslateY((x * Math.abs(mouseX - centerY)) * scale);
                            zoomIn();
                        } else {
                            double aspectRatio = imagePreview.getFitWidth() / imagePreview.getImage().getWidth();
                            double realWidth = imagePreview.getImage().getWidth() * aspectRatio;
                            double realHeight = imagePreview.getImage().getHeight() * aspectRatio;
                            double centerX = realWidth / 2;
                            double centerY = realHeight / 2;

                            if(mouseX > centerX)
                                x = x * (-1);
                            if(mouseY > centerY)
                                y = y * (-1);

                            zoomIn();
                            imagePreview.setTranslateX((x * Math.abs(mouseX - centerX)) * scale);
                            imagePreview.setTranslateY((y * Math.abs(mouseY - centerY)) * scale);
                        }
                    } else {
                        zoomOut();
                    }
                }
                numberTextField.requestFocus();
            }
        });

        sliderZoom.setValue(6);

        sliderZoom.valueProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                lblZoom.setText("Zoom: " + newValue.intValue() + "x");
                scale = newValue.intValue();
            }
        });

        pref = Preferences.userRoot().node(this.getClass().getName());
        String lastPath = pref.get("lastPath", "null");
        if(!lastPath.equals("null")) {
            destinationAddress = Optional.of(lastPath);
        }
        destinationAddress.ifPresent(s -> lblDestination.setText(s));

    }
}

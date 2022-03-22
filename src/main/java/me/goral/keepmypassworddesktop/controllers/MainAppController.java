package me.goral.keepmypassworddesktop.controllers;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Pair;
import me.goral.keepmypassworddesktop.MainApp;
import me.goral.keepmypassworddesktop.database.DatabaseHandler;
import me.goral.keepmypassworddesktop.util.*;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.io.File;
import java.time.LocalDate;
import java.util.*;

import static me.goral.keepmypassworddesktop.util.AlertsUtil.showErrorDialog;
import static me.goral.keepmypassworddesktop.util.ConfUtil.createConfFiles;

public class MainAppController {

    private Boolean login = false;

    @FXML
    Button btnLogin;
    @FXML
    Label dateLabel;

    /**
     * The function sets the text of the dateLabel to the current year
     */
    @FXML
    private void initialize(){
        LocalDate l = LocalDate.now();
        dateLabel.setText(String.valueOf(l.getYear()));
    }

    /**
     * If the user is logging in, we check if the username and password are correct. If they are, we show the logged in
     * screen. If they are not, we show an error dialog
     */
    @FXML
    protected void onLoginButtonClick() {
        Dialog<Pair<String, String>> dialog = new Dialog<>();
        String dialogType = login ? "Login" :  "Register";
        dialog.setTitle(dialogType + " Dialog");
        dialog.setHeaderText(login ? "Log in to your account" : "Set up your account");
        dialog.setGraphic(new ImageView(MainApp.class.getResource("/me/goral/keepmypassworddesktop/images/login-64.png").toString()));
        dialog.getDialogPane().getStylesheets().add(MainApp.class.getResource("styles/dialog.css").toExternalForm());

        ButtonType registerButtonType = new ButtonType(dialogType, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().setAll(registerButtonType, cancelButtonType);
        Node regBtn = dialog.getDialogPane().lookupButton(registerButtonType);
        Node canBtn = dialog.getDialogPane().lookupButton(cancelButtonType);
        regBtn.getStyleClass().add("btn");
        canBtn.getStyleClass().add("btn");
        regBtn.setDisable(true);

        Stage stage = (Stage) dialog.getDialogPane().getScene().getWindow();
        stage.getIcons().add(new Image(MainApp.class.getResourceAsStream("/me/goral/keepmypassworddesktop/images/access-32.png")));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20,150,10,10));

        TextField username = new TextField();
        username.setPromptText("Username");
        PasswordField password = new PasswordField();
        password.setPromptText("Password");

        grid.add(new Label("Username"), 0, 0);
        grid.add(username, 1, 0);
        grid.add(new Label("Password"), 0, 1);
        grid.add(password, 1, 1);

        username.textProperty().addListener(((observableValue, oldV, newV) -> regBtn.setDisable(newV.trim().isEmpty())));

        dialog.getDialogPane().setContent(grid);
        Platform.runLater(username::requestFocus);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == registerButtonType){

                Pair<String, Color> checker = PasswordGeneratorUtil.checkPasswordComplexity(password.getText());

                if (password.getText().isEmpty() && !login){
                    AlertsUtil.showErrorDialog("Error", "There is a problem.", "You can't register with empty password.");
                    return null;
                } else if ((!checker.getKey().equals("Strong password") && !checker.getKey().equals("Medium password")) && !login){
                    AlertsUtil.showErrorDialog("Error", "There is a problem.", "Password is not strong enough.");
                    return null;
                }
                return new Pair<>(username.getText(), password.getText());
            }
            return null;
        });

        Optional<Pair<String, String>> res = dialog.showAndWait();
        res.ifPresent(result -> {

            String uname = result.getKey();
            String plain = result.getValue();

            if (login){
                //login
                try {
                    String config = ConfUtil.readConfigFile();
                    String[] configArr = config.split(":");
                    String unameFromString = configArr[0];
                    String encryptedInitial = configArr[1];
                    String ivString = configArr[2];
                    String salt = configArr[3];
                    String argon = ArgonUtil.encrypt(plain, salt);
                    SecretKey key = AESUtil.generateKey(argon);

                    if (SHAUtil.hashSHA(uname).equals(unameFromString)){
                        boolean authorized = AuthUtil.authorize(encryptedInitial, ivString, key);
                        if (!authorized){
                            showErrorDialog("Error", "Invalid username or password",
                                    "Please provide correct credentials.");
                            onLoginButtonClick();
                        } else {
                            FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("layouts/logged.fxml"));
                            Parent root = loader.load();

                            LoggedController loggedController = loader.getController();
                            loggedController.setSecretKey(key);
                            loggedController.setUnameLabel(uname);

                            Scene sc = new Scene(root);
                            String css = MainApp.class.getResource("styles/main.css").toExternalForm();
                            sc.getStylesheets().add(css);
                            MainApp.getStage().setScene(sc);
                        }
                    } else {
                        showErrorDialog("Error", "Invalid username or password",
                                "Please provide correct credentials.");
                        onLoginButtonClick();
                    }
                } catch (Exception e) {
                    AlertsUtil.showExceptionStackTraceDialog(e);
                }
            } else {

                //register
                try {
                    IvParameterSpec iv = AESUtil.generateIv();
                    String salt = Base64.getEncoder().encodeToString(ArgonUtil.generateSalt());
                    String argon = ArgonUtil.encrypt(plain, salt);
                    SecretKey key = AESUtil.generateKey(argon);
                    String init = AuthUtil.encryptInitial(key, iv);

                    String output = SHAUtil.hashSHA(uname) + ":" + init + ":" + salt;
                    createConfFiles(output);
                    login = true;
                    handleAppRun();
                    onLoginButtonClick();

                } catch (Exception e){
                    AlertsUtil.showExceptionStackTraceDialog(e);
                }
            }
        });
    }

    public void setIsLogged() {
        login = true;
    }

    /**
     * If the database and config files don't exist, then the user is
     * registering. If the database exists but the config file doesn't, then the user is registering. If the config file
     * exists but the database doesn't, then the user is registering. If the config file and database exist, then the user is
     * logging in
     */
    public void handleAppRun() {
        if (!ConfUtil.checkIfConfigExists() && !ConfUtil.checkIfDatabaseExists()) {
            btnLogin.setText("Register");
        } else if (ConfUtil.checkIfDatabaseExists() && !ConfUtil.checkIfConfigExists()) {
            File db = new File("database.db");
            db.delete();
            btnLogin.setText("Register");
        } else if (ConfUtil.checkIfConfigExists() && !ConfUtil.checkIfDatabaseExists()){
            DatabaseHandler.createDatabase();
            btnLogin.setText("Log in");
            login = true;
        } else {
            btnLogin.setText("Log in");
            login = true;
        }
    }
}
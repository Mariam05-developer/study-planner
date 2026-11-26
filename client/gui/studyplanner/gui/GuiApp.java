package studyplanner.gui;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import javafx.stage.Window;

import studyplanner.ApiClient;
import studyplanner.Config;

// Main class. Switches between the login screen and the main window
public class GuiApp extends Application {
    final Tasks tasks = new Tasks();
    PlannerService account;
    LocalPlanner guest;
    Planner service;
    private Scene scene;
    private Stage stage;
    private MainView main;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        stage = primaryStage;
        Config config;
        try {
            Path directory = Config.defaultDirectory();
            config = new Config(directory);
            account = new PlannerService(config);
            guest = new LocalPlanner(config, directory.resolve(LocalPlanner.FILE_NAME),
                    Clock.systemDefaultZone());
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR,
                    "Cannot read the saved login: " + e.getMessage()).showAndWait();
            return;
        }
        scene = new Scene(new javafx.scene.layout.Pane(), 1120, 720);
        scene.getStylesheets().add(stylesheet());
        stage.setTitle("Study Planner");
        stage.setMinWidth(820);
        stage.setMinHeight(560);
        stage.setScene(scene);
        if (config.isGuest()) {
            showMain(guest);
        } else if (account.hasSavedLogin()) {
            showMain(account);
        } else {
            showLogin(null);
        }
        stage.show();
    }

    static String stylesheet() {
        return GuiApp.class.getResource("app.css").toExternalForm();
    }

    Stage stage() {
        return stage;
    }

    private void show(Parent root) {
        scene.setRoot(root);
    }

    void showLogin(String message) {
        if (main != null) {
            main.close();
            main = null;
        }
        tasks.discardPending();
        // close any open dialogs
        for (Window window : List.copyOf(Window.getWindows())) {
            if (window instanceof Stage other && other != stage) {
                other.close();
            }
        }
        show(new LoginView(this, message));
    }

    void showMain(Planner planner) {
        service = planner;
        main = new MainView(this);
        show(main);
        main.refresh();
    }

    boolean handleExpiredSession(Throwable error) {
        if (error instanceof ApiClient.ApiException api && api.status == 401) {
            showLogin("Your session has ended. Please log in again.");
            return true;
        }
        return false;
    }

    static String describe(Throwable error) {
        if (error instanceof ApiClient.ApiException
                || error instanceof IllegalArgumentException) {
            return error.getMessage();
        }
        if (error instanceof IOException || error instanceof UncheckedIOException) {
            return "File error: " + error.getMessage();
        }
        error.printStackTrace();
        return "Unexpected error: " + error;
    }
}

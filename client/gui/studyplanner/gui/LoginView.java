package studyplanner.gui;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import studyplanner.ApiClient;

// Login screen: log in, create an account or continue as guest
class LoginView extends StackPane {
    private final TextField server = new TextField();
    private final TextField username = new TextField();
    private final PasswordField password = new PasswordField();
    private final PasswordField confirm = new PasswordField();
    private final Label confirmLabel = new Label("Repeat the password");
    private final Label subtitle = new Label();
    private final Label message = Widgets.wrapped("", "error");
    private final Label warning = Widgets.wrapped(
            "This address is not encrypted: your password could be read on the network. "
                    + "Use https:// for a remote server.", "warning");
    private final Button submit = new Button();
    private final Button toggle = new Button();
    private final Button guest = new Button("Continue as guest");
    private boolean registering;

    LoginView(GuiApp app, String notice) {
        getStyleClass().add("login");
        server.setId("server");
        username.setId("username");
        password.setId("password");
        confirm.setId("confirm");
        submit.setId("submit");
        toggle.setId("toggle-mode");
        message.setId("login-error");
        guest.setId("guest");

        server.setText(app.account.server());
        submit.setDefaultButton(true);
        submit.setMaxWidth(Double.MAX_VALUE);
        submit.getStyleClass().add("primary");
        toggle.setMaxWidth(Double.MAX_VALUE);
        guest.setMaxWidth(Double.MAX_VALUE);

        Label title = new Label("Study Planner");
        title.getStyleClass().add("heading");
        VBox card = new VBox(6, title, subtitle,
                new Label("Username"), username,
                new Label("Password"), password,
                confirmLabel, confirm,
                message, submit, toggle,
                new Label("Server"), server, warning,
                new Separator(), guest,
                Widgets.wrapped("No account or server needed. Your planner is saved "
                        + "on this computer.", "muted"));
        card.getStyleClass().add("login-card");
        card.setMaxSize(360, USE_PREF_SIZE);
        setAlignment(Pos.CENTER);
        getChildren().add(card);

        server.textProperty().addListener((observed, old, text) -> checkAddress(text));
        checkAddress(server.getText());
        toggle.setOnAction(event -> {
            setRegistering(!registering);
            showMessage(null);
        });
        submit.setOnAction(event -> submit(app));
        guest.setOnAction(event -> continueAsGuest(app));
        setRegistering(false);
        showMessage(notice);
    }

    private void checkAddress(String text) {
        Widgets.setShown(warning,
                ApiClient.isUnencryptedRemote(PlannerService.normalizeServer(text)));
    }

    private void setRegistering(boolean value) {
        registering = value;
        subtitle.setText(value ? "Create an account (password: 8 characters or more)"
                : "Log in to your account");
        submit.setText(value ? "Create account" : "Log in");
        toggle.setText(value ? "Back to log in" : "Create account");
        Widgets.setShown(confirm, value);
        Widgets.setShown(confirmLabel, value);
        confirm.clear();
    }

    private void showMessage(String text) {
        message.setText(text == null ? "" : text);
        Widgets.setShown(message, text != null);
    }

    private void submit(GuiApp app) {
        String name = username.getText().strip();
        String secret = password.getText();
        if (name.isEmpty() || secret.isEmpty()) {
            showMessage("Enter a username and a password");
            return;
        }
        if (registering && !secret.equals(confirm.getText())) {
            showMessage("The passwords do not match");
            return;
        }
        String address = server.getText();
        boolean register = registering;
        showMessage(null);
        setBusy(true);
        app.tasks.run(() -> {
            app.account.signIn(address, name, secret, register);
            return null;
        }, done -> app.showMain(app.account), error -> {
            setBusy(false);
            showMessage(GuiApp.describe(error));
        });
    }

    private void continueAsGuest(GuiApp app) {
        showMessage(null);
        setBusy(true);
        app.tasks.run(() -> {
            app.guest.start();
            return null;
        }, done -> app.showMain(app.guest), error -> {
            setBusy(false);
            showMessage(GuiApp.describe(error));
        });
    }

    private void setBusy(boolean busy) {
        submit.setDisable(busy);
        toggle.setDisable(busy);
        guest.setDisable(busy);
    }
}

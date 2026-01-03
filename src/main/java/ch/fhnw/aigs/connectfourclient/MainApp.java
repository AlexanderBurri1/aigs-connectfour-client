package ch.fhnw.aigs.connectfourclient;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

public class MainApp extends Application {


    private static final String BASE_URL = "http://127.0.0.1:50005";

    private final ApiClient api = new ApiClient(BASE_URL);

    private Stage stage;


    private String token;
    private String userName;


    private Game currentGame;


    private Label statusLabel;
    private GridPane boardGrid;
    private ComboBox<Integer> difficultyBox;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        primaryStage.setTitle("Connect Four (AIGS)");
        showLoginScene();
        primaryStage.show();
    }



    private void showLoginScene() {
        var root = new VBox(10);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);

        var title = new Label("Connect Four");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        var userField = new TextField();
        userField.setPromptText("Username");

        var passField = new PasswordField();
        passField.setPromptText("Password");

        var info = new Label("");
        info.setTextFill(Color.DARKRED);

        var registerBtn = new Button("Register");
        var loginBtn = new Button("Login");

        var btnRow = new HBox(10, registerBtn, loginBtn);
        btnRow.setAlignment(Pos.CENTER);

        registerBtn.setOnAction(e -> runAsync(
                () -> api.register(userField.getText().trim(), passField.getText()),
                ok -> {
                    info.setTextFill(Color.DARKGREEN);
                    info.setText("Registered! Now login.");
                },
                ex -> info.setText("Register failed: " + ex.getMessage())
        ));

        loginBtn.setOnAction(e -> runAsync(
                () -> api.login(userField.getText().trim(), passField.getText()),
                user -> {
                    this.token = user.token;
                    this.userName = user.userName;
                    showGameScene();
                },
                ex -> info.setText("Login failed: " + ex.getMessage())
        ));

        root.getChildren().addAll(title, userField, passField, btnRow, info);
        stage.setScene(new Scene(root, 420, 320));
    }



    private void showGameScene() {
        var root = new BorderPane();
        root.setPadding(new Insets(12));

        // Top bar
        var top = new HBox(10);
        top.setAlignment(Pos.CENTER_LEFT);

        var userLbl = new Label("Logged in as: " + userName);
        userLbl.setStyle("-fx-font-weight: bold;");

        difficultyBox = new ComboBox<>();
        difficultyBox.getItems().addAll(1, 2);
        difficultyBox.setValue(1);

        var newBtn = new Button("New Game");
        var logoutBtn = new Button("Logout");

        top.getChildren().addAll(userLbl, new Label("Difficulty:"), difficultyBox, newBtn, logoutBtn);
        root.setTop(top);


        var center = new VBox(8);
        center.setAlignment(Pos.CENTER);

        var columnButtons = new HBox(6);
        columnButtons.setAlignment(Pos.CENTER);

        for (int c = 0; c < 7; c++) {
            int col = c;
            var b = new Button("▼");
            b.setPrefWidth(45);
            b.setOnAction(e -> makeMove(col));
            columnButtons.getChildren().add(b);
        }

        boardGrid = new GridPane();
        boardGrid.setHgap(6);
        boardGrid.setVgap(6);
        boardGrid.setAlignment(Pos.CENTER);

        center.getChildren().addAll(columnButtons, boardGrid);
        root.setCenter(center);


        statusLabel = new Label("Click “New Game” to start.");
        root.setBottom(statusLabel);
        BorderPane.setMargin(statusLabel, new Insets(10, 0, 0, 0));


        newBtn.setOnAction(e -> newGame());
        logoutBtn.setOnAction(e -> {
            token = null;
            userName = null;
            currentGame = null;
            showLoginScene();
        });

        stage.setScene(new Scene(root, 520, 520));
    }

    private void newGame() {
        int diff = difficultyBox.getValue();
        statusLabel.setText("Creating game...");
        runAsync(
                () -> api.newGame(token, "ConnectFour", diff),
                game -> {
                    this.currentGame = game;
                    statusLabel.setText("Your turn. Click a column.");
                    renderBoard(game);
                },
                ex -> statusLabel.setText("New game failed: " + ex.getMessage())
        );
    }

    private void makeMove(int col) {
        if (currentGame == null) {
            statusLabel.setText("Start a new game first.");
            return;
        }
        if (currentGame.result) {
            statusLabel.setText("Game ended. Click New Game to restart.");
            return;
        }

        statusLabel.setText("Playing column " + col + "...");
        runAsync(
                () -> api.move(token, col),
                game -> {
                    this.currentGame = game;
                    renderBoard(game);

                    if (game.result) {
                        statusLabel.setText("Game ended! Click New Game to play again.");
                    } else {
                        statusLabel.setText("Your turn. Click a column.");
                    }
                },
                ex -> statusLabel.setText("Move failed: " + ex.getMessage())
        );
    }

    private void renderBoard(Game game) {
        boardGrid.getChildren().clear();

        // Expect 6x7
        for (int r = 0; r < game.board.length; r++) {
            for (int c = 0; c < game.board[r].length; c++) {
                long v = game.board[r][c];

                Circle circle = new Circle(18);
                circle.setStroke(Color.GRAY);

                if (v == 1) circle.setFill(Color.GOLD);
                else if (v == -1) circle.setFill(Color.DODGERBLUE);
                else circle.setFill(Color.WHITESMOKE);

                boardGrid.add(circle, c, r);
            }
        }
    }



    private <T> void runAsync(ThrowingSupplier<T> work, java.util.function.Consumer<T> onOk, java.util.function.Consumer<Exception> onErr) {
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception { return work.get(); }
        };
        task.setOnSucceeded(e -> onOk.accept(task.getValue()));
        task.setOnFailed(e -> onErr.accept(new Exception(task.getException())));
        new Thread(task, "api-call").start();
    }

    @FunctionalInterface
    interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    public static void main(String[] args) {
        launch(args);
    }
}

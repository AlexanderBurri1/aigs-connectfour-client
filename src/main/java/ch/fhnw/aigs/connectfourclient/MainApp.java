package ch.fhnw.aigs.connectfourclient;

import javafx.animation.Interpolator;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

public class MainApp extends Application {

    private static final int ROWS = 6;
    private static final int COLS = 7;


    private static final Color HUMAN_COLOR = Color.web("#EF4444");
    private static final Color AI_COLOR    = Color.web("#3B82F6");
    private static final Color EMPTY_HOLE  = Color.web("#E8EEF6");
    private static final Color BOARD_BLUE  = Color.web("#1D4ED8");

    private static final double CELL_SIZE = 62;
    private static final double PIECE_RADIUS = 20;


    private static final int AI_DROP_DELAY_MS = 260;


    private static final String PREF_SERVER_URL = "connectfour_server_url";
    private static final String DEFAULT_SERVER_URL = "http://127.0.0.1:50005";

    private final Preferences prefs = Preferences.userNodeForPackage(MainApp.class);

    private Stage stage;


    private String serverUrl;
    private ApiClient api;


    private String token;
    private String userName;


    private Game currentGame;
    private long[][] lastBoard;


    private Label statusLabel;
    private ComboBox<Integer> difficultyBox;
    private ProgressIndicator busyIndicator;

    private Button newBtn;
    private Button logoutBtn;
    private Button settingsBtn;


    private Circle[][] pieceCircles;
    private StackPane[][] cellPanes;
    private StackPane winnerBanner;
    private Label winnerBannerText;


    private ScrollPane boardScroll;
    private StackPane boardStack;


    private final boolean[] fullCols = new boolean[COLS];
    private boolean boardEnabled = false;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        stage.setTitle("Connect Four (AIGS)");
        stage.setMinWidth(680);
        stage.setMinHeight(640);

        serverUrl = prefs.get(PREF_SERVER_URL, DEFAULT_SERVER_URL);
        api = new ApiClient(serverUrl);

        showLoginScene();
        stage.show();
    }


    private void showLoginScene() {
        VBox root = new VBox(12);
        root.setPadding(new Insets(26));
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: #F8FAFC;");

        Label title = new Label("Connect Four");
        title.setStyle("-fx-font-size: 26px; -fx-font-weight: 800;");

        Label subtitle = new Label("AIGS Game Service Client");
        subtitle.setStyle("-fx-text-fill: #64748B; -fx-font-size: 12px;");

        TextField userField = new TextField();
        userField.setPromptText("Username");
        userField.setMaxWidth(320);

        PasswordField passField = new PasswordField();
        passField.setPromptText("Password");
        passField.setMaxWidth(320);

        Label info = new Label();
        info.setWrapText(true);
        info.setMaxWidth(420);
        info.setTextFill(Color.web("#B91C1C"));

        Button registerBtn = new Button("Register");
        Button loginBtn = new Button("Login");
        styleSecondaryButton(registerBtn);
        stylePrimaryButton(loginBtn);

        Button settings = new Button("⚙");
        settings.setTooltip(new Tooltip("Settings (Server URL)"));
        styleIconButton(settings);
        settings.setOnAction(e -> openSettingsDialog(true));

        HBox btnRow = new HBox(10, registerBtn, loginBtn, settings);
        btnRow.setAlignment(Pos.CENTER);

        registerBtn.setOnAction(e -> runAsync(
                () -> api.register(userField.getText().trim(), passField.getText()),
                ok -> {
                    info.setTextFill(Color.web("#15803D"));
                    info.setText("Registered successfully. Now login.");
                },
                ex -> {
                    info.setTextFill(Color.web("#B91C1C"));
                    info.setText("Register failed: " + ex.getMessage());
                }
        ));

        loginBtn.setOnAction(e -> runAsync(
                () -> api.login(userField.getText().trim(), passField.getText()),
                user -> {
                    this.token = user.token;
                    this.userName = user.userName;
                    showGameScene();
                },
                ex -> {
                    info.setTextFill(Color.web("#B91C1C"));
                    info.setText("Login failed: " + ex.getMessage());
                }
        ));

        Label serverInfo = new Label("Server: " + serverUrl);
        serverInfo.setStyle("-fx-text-fill: #64748B; -fx-font-size: 11px;");

        root.getChildren().addAll(title, subtitle, userField, passField, btnRow, info, serverInfo);

        Scene scene = new Scene(root, 720, 460);
        stage.setScene(scene);
    }


    private void showGameScene() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(14));
        root.setStyle("-fx-background-color: #F8FAFC;");


        FlowPane top = new FlowPane(12, 10);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(12));
        top.setStyle(cardStyle());

        Label userLbl = new Label("Logged in as: " + userName);
        userLbl.setStyle("-fx-font-weight: 800; -fx-text-fill: #0F172A;");

        difficultyBox = new ComboBox<>();
        difficultyBox.getItems().addAll(1, 2);
        difficultyBox.setValue(1);
        difficultyBox.setPrefWidth(110);

        newBtn = new Button("New Game");
        logoutBtn = new Button("Logout");
        settingsBtn = new Button("⚙");
        settingsBtn.setTooltip(new Tooltip("Settings (Server URL)"));

        stylePrimaryButton(newBtn);
        styleSecondaryButton(logoutBtn);
        styleIconButton(settingsBtn);

        busyIndicator = new ProgressIndicator();
        busyIndicator.setPrefSize(18, 18);
        busyIndicator.setVisible(false);

        Label serverLabel = new Label("Server:");
        Label serverPill = smallPill(serverUrl);

        serverPill.setMaxWidth(360);
        serverPill.setTooltip(new Tooltip(serverUrl));

        top.getChildren().addAll(
                userLbl,
                new Label("Difficulty:"), difficultyBox,
                new Separator(),
                serverLabel, serverPill,
                busyIndicator,
                settingsBtn, newBtn, logoutBtn
        );

        root.setTop(top);


        VBox center = new VBox(10);
        center.setAlignment(Pos.CENTER);
        center.setFillWidth(true);


        StackPane boardCard = new StackPane();
        boardCard.setPadding(new Insets(14));
        boardCard.setStyle(cardStyle());
        boardCard.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);


        Rectangle plate = new Rectangle(COLS * CELL_SIZE + 44, ROWS * CELL_SIZE + 44);
        plate.setArcWidth(22);
        plate.setArcHeight(22);
        plate.setFill(BOARD_BLUE);
        plate.setEffect(new DropShadow(16, Color.color(0, 0, 0, 0.25)));

        GridPane grid = buildBoardGrid();


        winnerBannerText = new Label("");
        winnerBannerText.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: 800;");

        winnerBanner = new StackPane(winnerBannerText);
        winnerBanner.setVisible(false);
        winnerBanner.setMaxWidth(520);
        winnerBanner.setPadding(new Insets(12));
        winnerBanner.setStyle("-fx-background-radius: 16; -fx-background-color: rgba(15,23,42,0.92);");
        winnerBanner.setEffect(new DropShadow(14, Color.color(0, 0, 0, 0.35)));

        boardStack = new StackPane(plate, grid, winnerBanner);
        boardStack.setAlignment(Pos.CENTER);


        boardScroll = new ScrollPane(boardStack);
        boardScroll.setFitToWidth(true);
        boardScroll.setFitToHeight(true);
        boardScroll.setPannable(true);
        boardScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        boardScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        boardScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        boardScroll.setPadding(new Insets(0));

        boardCard.getChildren().add(boardScroll);


        HBox statusBar = new HBox();
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(10, 12, 10, 12));
        statusBar.setStyle(cardStyle());

        statusLabel = new Label("Click “New Game” to start.");
        statusLabel.setWrapText(true);
        statusLabel.setStyle("-fx-text-fill: #0F172A; -fx-font-weight: 700;");
        statusLabel.maxWidthProperty().bind(statusBar.widthProperty().subtract(24));
        statusBar.getChildren().add(statusLabel);

        center.getChildren().addAll(boardCard, statusBar);


        VBox.setVgrow(boardCard, Priority.ALWAYS);

        root.setCenter(center);


        newBtn.setOnAction(e -> newGame());
        logoutBtn.setOnAction(e -> {
            token = null;
            userName = null;
            currentGame = null;
            lastBoard = null;
            showLoginScene();
        });
        settingsBtn.setOnAction(e -> openSettingsDialog(false));

        Scene scene = new Scene(root, 920, 820);
        stage.setScene(scene);


        boardScroll.viewportBoundsProperty().addListener((obs, oldV, newV) -> updateBoardScale());
        boardStack.layoutBoundsProperty().addListener((obs, oldB, newB) -> updateBoardScale());
        updateBoardScale();

        setBoardInteractive(false);
    }


    private void updateBoardScale() {
        if (boardScroll == null || boardStack == null) return;

        Bounds viewport = boardScroll.getViewportBounds();
        Bounds content = boardStack.getLayoutBounds();
        if (viewport.getWidth() <= 0 || viewport.getHeight() <= 0) return;
        if (content.getWidth() <= 0 || content.getHeight() <= 0) return;

        double scale = Math.min(viewport.getWidth() / content.getWidth(),
                viewport.getHeight() / content.getHeight());


        scale = Math.min(scale, 1.0);
        scale = Math.max(scale, 0.85);

        boardStack.setScaleX(scale);
        boardStack.setScaleY(scale);
    }

    private GridPane buildBoardGrid() {
        pieceCircles = new Circle[ROWS][COLS];
        cellPanes = new StackPane[ROWS][COLS];

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setAlignment(Pos.CENTER);
        grid.setPadding(new Insets(20));

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {

                StackPane cell = new StackPane();
                cell.setPrefSize(CELL_SIZE, CELL_SIZE);

                Circle hole = new Circle(PIECE_RADIUS + 7);
                hole.setFill(EMPTY_HOLE);
                hole.setStroke(Color.web("#CBD5E1"));

                Circle piece = new Circle(PIECE_RADIUS);
                piece.setFill(Color.TRANSPARENT);
                piece.setStroke(Color.color(0, 0, 0, 0.18));
                piece.setEffect(new DropShadow(7, Color.color(0, 0, 0, 0.20)));

                pieceCircles[r][c] = piece;
                cellPanes[r][c] = cell;

                int col = c;

                cell.setOnMouseClicked(e -> makeMove(col));
                cell.setOnMouseEntered(e -> highlightColumn(col, true));
                cell.setOnMouseExited(e -> highlightColumn(col, false));

                cell.getChildren().addAll(hole, piece);
                grid.add(cell, c, r);
            }
        }

        return grid;
    }

    private void highlightColumn(int col, boolean on) {
        if (currentGame == null || currentGame.result) return;
        if (currentGame.board != null && currentGame.board[0][col] != 0) return;

        for (int r = 0; r < ROWS; r++) {
            StackPane cell = cellPanes[r][col];
            cell.setScaleX(on ? 1.03 : 1.0);
            cell.setScaleY(on ? 1.03 : 1.0);
        }
    }

    private void newGame() {
        int diff = difficultyBox.getValue();
        statusLabel.setText("Creating game...");
        setBusy(true);
        hideWinnerBanner();

        runAsync(
                () -> api.newGame(token, "ConnectFour", diff),
                game -> {
                    currentGame = game;
                    lastBoard = deepCopy(game.board);
                    for (int c = 0; c < COLS; c++) fullCols[c] = false;

                    renderBoard(game);
                    statusLabel.setText("Your turn. Click a column on the board.");

                    setBoardInteractive(true);
                    updateFullColumns(game.board);
                    setBusy(false);
                },
                ex -> {
                    statusLabel.setText("New game failed: " + ex.getMessage());
                    setBusy(false);
                }
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
        if (currentGame.board != null && currentGame.board[0][col] != 0) {
            statusLabel.setText("That column is full.");
            return;
        }

        statusLabel.setText("Your move…");
        setBusy(true);
        setBoardInteractive(false);

        runAsync(
                () -> api.move(token, col),
                game -> {
                    currentGame = game;

                    animateMoveUpdate(lastBoard, game.board);

                    Winner w = determineWinner(game.board);
                    if (game.result) {
                        showWinnerBanner(w);
                        statusLabel.setText("Game ended. Click New Game to play again.");
                        setBoardInteractive(false);
                    } else {
                        statusLabel.setText("AI moved. Your turn.");
                    }

                    lastBoard = deepCopy(game.board);
                    updateFullColumns(game.board);
                    setBusy(false);

                    if (!game.result) {
                        PauseTransition pt = new PauseTransition(Duration.millis(AI_DROP_DELAY_MS + 450));
                        pt.setOnFinished(ev -> setBoardInteractive(true));
                        pt.play();
                    }
                },
                ex -> {
                    statusLabel.setText("Move failed: " + ex.getMessage());
                    setBoardInteractive(true);
                    if (currentGame != null) updateFullColumns(currentGame.board);
                    setBusy(false);
                }
        );
    }

    private void renderBoard(Game game) {
        if (game.board == null) return;

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                long v = game.board[r][c];
                Circle piece = pieceCircles[r][c];
                piece.setTranslateY(0);

                if (v == 1) piece.setFill(HUMAN_COLOR);
                else if (v == -1) piece.setFill(AI_COLOR);
                else piece.setFill(Color.TRANSPARENT);
            }
        }
    }

    private void animateMoveUpdate(long[][] before, long[][] after) {
        List<CellChange> humanNew = new ArrayList<>();
        List<CellChange> aiNew = new ArrayList<>();

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                long b = before == null ? 0 : before[r][c];
                long a = after[r][c];
                Circle piece = pieceCircles[r][c];
                piece.setTranslateY(0);

                if (b == 0 && a != 0) {
                    piece.setFill(Color.TRANSPARENT);
                    if (a == 1) humanNew.add(new CellChange(r, c, a));
                    else aiNew.add(new CellChange(r, c, a));
                } else {
                    if (a == 1) piece.setFill(HUMAN_COLOR);
                    else if (a == -1) piece.setFill(AI_COLOR);
                    else piece.setFill(Color.TRANSPARENT);
                }
            }
        }

        for (CellChange cc : humanNew) {
            animateDrop(cc.row, cc.col, cc.value, Duration.millis(0));
        }
        for (CellChange cc : aiNew) {
            animateDrop(cc.row, cc.col, cc.value, Duration.millis(AI_DROP_DELAY_MS));
        }
    }

    private void animateDrop(int row, int col, long value, Duration delay) {
        Circle piece = pieceCircles[row][col];

        if (value == 1) piece.setFill(HUMAN_COLOR);
        else if (value == -1) piece.setFill(AI_COLOR);

        double startY = - (ROWS * CELL_SIZE * 0.75);
        piece.setTranslateY(startY);

        TranslateTransition tt = new TranslateTransition(Duration.millis(360 + row * 28), piece);
        tt.setDelay(delay);
        tt.setFromY(startY);
        tt.setToY(0);
        tt.setInterpolator(Interpolator.EASE_IN);
        tt.play();
    }


    private enum Winner { HUMAN, AI, DRAW, NONE }

    private Winner determineWinner(long[][] board) {
        if (board == null) return Winner.NONE;
        if (hasFour(board, 1)) return Winner.HUMAN;
        if (hasFour(board, -1)) return Winner.AI;
        if (isDraw(board)) return Winner.DRAW;
        return Winner.NONE;
    }

    private void showWinnerBanner(Winner w) {
        String text;
        switch (w) {
            case HUMAN -> text = "🎉 You win!";
            case AI -> text = "🤖 AI wins!";
            case DRAW -> text = "🤝 Draw!";
            default -> text = "Game ended!";
        }
        winnerBannerText.setText(text);
        winnerBanner.setVisible(true);
    }

    private void hideWinnerBanner() {
        winnerBanner.setVisible(false);
        winnerBannerText.setText("");
    }

    private boolean isDraw(long[][] board) {
        for (int c = 0; c < COLS; c++) {
            if (board[0][c] == 0) return false;
        }
        return true;
    }

    private boolean hasFour(long[][] b, long p) {
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c <= COLS - 4; c++)
                if (b[r][c] == p && b[r][c+1] == p && b[r][c+2] == p && b[r][c+3] == p) return true;

        for (int c = 0; c < COLS; c++)
            for (int r = 0; r <= ROWS - 4; r++)
                if (b[r][c] == p && b[r+1][c] == p && b[r+2][c] == p && b[r+3][c] == p) return true;

        for (int r = 0; r <= ROWS - 4; r++)
            for (int c = 0; c <= COLS - 4; c++)
                if (b[r][c] == p && b[r+1][c+1] == p && b[r+2][c+2] == p && b[r+3][c+3] == p) return true;

        for (int r = 3; r < ROWS; r++)
            for (int c = 0; c <= COLS - 4; c++)
                if (b[r][c] == p && b[r-1][c+1] == p && b[r-2][c+2] == p && b[r-3][c+3] == p) return true;

        return false;
    }


    private void updateFullColumns(long[][] board) {
        if (board == null) return;
        for (int c = 0; c < COLS; c++) {
            fullCols[c] = board[0][c] != 0;
        }
        applyInteractivity();
    }

    private void setBoardInteractive(boolean enabled) {
        boardEnabled = enabled;
        applyInteractivity();
    }

    private void applyInteractivity() {
        if (cellPanes == null) return;

        boolean gameOver = (currentGame != null && currentGame.result);

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                boolean disabled = !boardEnabled || gameOver || fullCols[c];
                cellPanes[r][c].setDisable(disabled);
                cellPanes[r][c].setOpacity(fullCols[c] ? 0.75 : 1.0);
                cellPanes[r][c].setCursor(disabled ? Cursor.DEFAULT : Cursor.HAND);
            }
        }
    }

    private void setBusy(boolean busy) {
        busyIndicator.setVisible(busy);
        newBtn.setDisable(busy);
        logoutBtn.setDisable(busy);
        settingsBtn.setDisable(busy);
        difficultyBox.setDisable(busy);
    }


    private void openSettingsDialog(boolean fromLogin) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Settings");
        dialog.setHeaderText("Server configuration");

        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        TextField urlField = new TextField(serverUrl);
        urlField.setPromptText("http://127.0.0.1:50005");

        Label hint = new Label("Example: http://127.0.0.1:50005");
        hint.setStyle("-fx-text-fill: #64748B;");

        VBox content = new VBox(8, new Label("Server URL:"), urlField, hint);
        content.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(content);

        dialog.showAndWait().ifPresent(result -> {
            if (result == save) {
                String newUrl = urlField.getText().trim();
                if (!newUrl.startsWith("http://") && !newUrl.startsWith("https://")) {
                    alert("Invalid URL", "Please include http:// or https://");
                    return;
                }

                serverUrl = newUrl;
                prefs.put(PREF_SERVER_URL, serverUrl);
                api = new ApiClient(serverUrl);

                token = null;
                userName = null;
                currentGame = null;
                lastBoard = null;

                showLoginScene();
            }
        });
    }

    private void alert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    private static Label smallPill(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-background-color: #F1F5F9; -fx-text-fill: #0F172A; -fx-padding: 4 8 4 8; -fx-background-radius: 999;");
        return l;
    }


    private static String cardStyle() {
        return "-fx-background-color: white;" +
                "-fx-background-radius: 16;" +
                "-fx-border-radius: 16;" +
                "-fx-border-color: #E2E8F0;";
    }

    private static void stylePrimaryButton(Button b) {
        b.setStyle("-fx-background-color: #0F172A;" +
                "-fx-text-fill: white;" +
                "-fx-font-weight: 800;" +
                "-fx-background-radius: 12;" +
                "-fx-padding: 8 14 8 14;" +
                "-fx-cursor: hand;");
    }

    private static void styleSecondaryButton(Button b) {
        b.setStyle("-fx-background-color: #F1F5F9;" +
                "-fx-text-fill: #0F172A;" +
                "-fx-font-weight: 800;" +
                "-fx-background-radius: 12;" +
                "-fx-padding: 8 14 8 14;" +
                "-fx-cursor: hand;");
    }

    private static void styleIconButton(Button b) {
        b.setStyle("-fx-background-color: white;" +
                "-fx-border-color: #E2E8F0;" +
                "-fx-border-radius: 12;" +
                "-fx-background-radius: 12;" +
                "-fx-font-weight: 900;" +
                "-fx-padding: 6 10 6 10;" +
                "-fx-cursor: hand;");
        b.setMinWidth(38);
    }


    private static long[][] deepCopy(long[][] board) {
        if (board == null) return null;
        long[][] copy = new long[board.length][];
        for (int i = 0; i < board.length; i++) {
            copy[i] = new long[board[i].length];
            System.arraycopy(board[i], 0, copy[i], 0, board[i].length);
        }
        return copy;
    }

    private record CellChange(int row, int col, long value) {}


    private <T> void runAsync(ThrowingSupplier<T> work,
                              java.util.function.Consumer<T> onOk,
                              java.util.function.Consumer<Exception> onErr) {
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

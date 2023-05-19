import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.application.Platform;


//クイズクライアントのメインクラスであり、UIの表示、サーバーとの通信、回答の送信などを管理します
public class QuizClient_fix extends Application {

  //接続するサーバーのIPアドレスとポート番号を指定
  private static final String SERVER_IP = "127.0.0.1";
  private static final int SERVER_PORT = 8080;

  //UI要素

  //問題表示用
  private TextArea questionArea;
  //回答入力用
  private TextField answerField;
  //スコア表示用
  private Label scoreLabel;
  //スタートボタン
  private Button startButton;
  private int score = 0;
  private int questionCount = 0;

  private BufferedReader in;
  private PrintWriter out;

  public static void main(String[] args) {
    launch(args);
  }

  //JavaFXアプリケーションの起動時に呼び出され、UIの構築、サーバーへの接続、メッセージの受信などを行います
  //JavaFXアプリケーションのUIの構築とイベントハンドリングを行うメソッド
  @Override
  public void start(Stage primaryStage) throws Exception {
    // Set up UI elements
    questionArea = new TextArea();
    questionArea.setEditable(false);
    questionArea.setPrefHeight(150);

    //説明文を表示
    questionArea.setText("早押しクイズを始めます。\n\n一番早く正しい回答を送った参加者に点数を与えます。\nクイズは全5問です。\n\n\nStartを押して開始してください。");

    answerField = new TextField();
    answerField.setPromptText("Press the start button");
    //answerField.setPromptText("Enter your answer...");

    scoreLabel = new Label("Score: 0");

    startButton = new Button("Start");
    startButton.setOnAction(e -> sendStartSignal());

    VBox root = new VBox(
      10,
      questionArea,
      answerField,
      scoreLabel,
      startButton
    );
    root.setPadding(new Insets(10));
    root.setAlignment(Pos.CENTER);

    // Set up the scene and stage
    Scene scene = new Scene(root, 400, 300);
    // CSSファイルを読み込む
    scene.getStylesheets().add(getClass().getResource("color.css").toExternalForm());
    primaryStage.setScene(scene);
    primaryStage.setTitle("Quiz Client");
    primaryStage.show();

    //サーバーへの接続を確立
    connectToServer();

    //回答をサーバーに送信し、回答エリアをリセット
    answerField.setOnAction(
      e -> {
        String answer = answerField.getText().trim();
        out.println("ANSWER_c " + answer);
        answerField.clear();
      }
    );

    // Listen for messages from the server
    //新しいスレッドを開始し、サーバーからのメッセージを非同期で受信
    new Thread(
      () -> {
        try {
          String message;
          //受信したメッセージを解析し、問題とスコアの表示を更新
          while ((message = in.readLine()) != null) {

            //受信したメッセージが"QUESTION "で始まる場合
            if (message.startsWith("QUESTION_s ")) {
              questionCount++;

              //クイズが5問終了した場合、最終スコアが表示
              if (questionCount > 5) {
                questionArea.setText("全てのクイズが終了しました！あなたの最終得点は" + score +"点です。");

                //回答入力フィールドとスタートボタンが無効化
                answerField.setDisable(true);
                startButton.setDisable(true);

              //5問未満の時
              } else {
                //"QUESTION_s"以降11文字目から)の問題のテキストが表示
                questionArea.setText(questionCount+"問目\n"+message.substring(11));
                answerField.setPromptText("Enter your answer....");
                
                //回答入力フィールドとスタートボタンを有効化
                answerField.setDisable(false);
                startButton.setDisable(false);

              }

              //受信したメッセージが"CORRECT_s  "で始まる場合
            } else if (message.startsWith("CORRECT_s ")) {
              
              //回答入力フィールドとスタートボタンが無効化
                answerField.setDisable(true);
                startButton.setDisable(true);

              //スコアが更新
              //"CORRECT_s"以降(10文字目から)の得点をscoreに加える
              score += Integer.parseInt(message.substring(10));

              //スコア表示の更新をJavaFXのUIスレッドで行う
              Platform.runLater(() -> {
                // スコア表示の更新
                scoreLabel.setText("Score: " + score);
              });

              //受信したメッセージが"ANSWER_s "で始まる場合
            }else if(message.startsWith("ANSWER_s ")){
              //正答を表示
              //"ANSWER_s "以降(9文字目から)の正答
              questionArea.setText("正解者が出ました\n正解は\n\n" + message.substring(9)+"\n\nです");
              
              //回答入力フィールドとスタートボタンが無効化
              answerField.setDisable(true);
              startButton.setDisable(true);

              //受信したメッセージが"WRONG_s "で始まる場合
            }else if(message.startsWith("WRONG_s ")){

              //今の問題を補完して
              String currentText = questionArea.getText();
              //間違いのメッセージを追加して表示
              String appendedText = currentText + "\n\nあなたの回答は間違いです！\n正しい回答を入力してください。";
              questionArea.setText(appendedText);            }
  

          }
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    )
    .start();
  }

  //サーバーへの接続を確立するメソッド
  private void connectToServer() {
    try {
      //サーバーのIPアドレスとポート番号を使用して、ソケット接続を作成s
      Socket socket = new Socket(SERVER_IP, SERVER_PORT);

      //入出力ストリームを初期化し、サーバーとの通信に使用
      in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      out = new PrintWriter(socket.getOutputStream(), true);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  //ゲームの開始シグナルをサーバーに送信するメソッド
  private void sendStartSignal() {
    //サーバーに"START"メッセージを送信
    out.println("START_c");
    
  }
}

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public class QuizServer_fix {

  //ポート番号変更
  private static final int port = 8080;

  //問題と答えが格納されたテキストファイルのパス
  private static final String questionsFile = "questions.txt";
  private static final String answersFile = "answers.txt";

  //問題と答えのリスト
  private List<String> questions;
  private List<String> answers;

  //クライアントハンドラのリスト
  private List<ClientHandler> clients = new CopyOnWriteArrayList<>();

  //出題済み問題のインデックスリスト
  private List<Integer> usedQuestionIndices = new ArrayList<>();

  //現在の問題が進行中かを示すフラグ
  private AtomicBoolean questionInProgress = new AtomicBoolean(false);



  public QuizServer_fix() {
    try {
      questions = Files.readAllLines(Paths.get(questionsFile));
      answers = Files.readAllLines(Paths.get(answersFile));
    } catch (IOException e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  public void start() {
    try (ServerSocket serverSocket = new ServerSocket(port)) {
      System.out.println("Quiz server started on port " + port);

      while (true) {
        Socket socket = serverSocket.accept();
        ClientHandler client = new ClientHandler(socket, this);
        clients.add(client);
        new Thread(client).start();
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }


  //ランダムな問題をクライアントに送信するメソッド
  public void sendRandomQuestion() {
    //全ての問題を使ってしまったら。使用履歴をクリア
    if (usedQuestionIndices.size() == questions.size()) {
      usedQuestionIndices.clear();
    }

    //出題される問題を事前に指定された問題リストからランダムに選択
    int questionIndex;
    do {
      questionIndex = ThreadLocalRandom.current().nextInt(questions.size());
    } while (usedQuestionIndices.contains(questionIndex));
    usedQuestionIndices.add(questionIndex);
    String question = questions.get(questionIndex);
    String answer = answers.get(questionIndex);

    //問題と答えは、ClientHandlerインスタンスを保持する各クライアントに送信
    for (ClientHandler client : clients) {
      client.setAnswer(answer);
      client.sendMessage("QUESTION_s " + question);
    }

    //問題を進行中に変更
    questionInProgress.set(true);
  }

    //正答受信後、全クライアントに処理を行うメソッド
    public void handleAnswerReceived() {
      //質問が進行中の時、停止して回答をnullにする
      if (questionInProgress.compareAndSet(true, false)) {
        for (ClientHandler client : clients) {
          client.setAnswer(null);
        }
  
        //3秒間のスリープをしクライアントに回答を送信する前に一定の待機時間を確保
        try {
          Thread.sleep(3000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
  
        //次のランダムな質問を生成し、全てのクライアントに送信
        sendRandomQuestion();
      }
    }

  //クライアントを削除
  public void removeClient(ClientHandler client) {
    clients.remove(client);
  }

  //クライアントとの通信を担当するクラス
  private class ClientHandler implements Runnable {
    private Socket socket;
    private QuizServer_fix server;
    private BufferedReader in;
    private PrintWriter out;
    private String answer;

    public ClientHandler(Socket socket, QuizServer_fix server) throws IOException {
      this.socket = socket;
      this.server = server;
      in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      out = new PrintWriter(socket.getOutputStream(), true);
    }

    @Override
    public void run() {
      try {
        String message;
        while ((message = in.readLine()) != null) {
          handleMessage(message);
        }
      } catch (IOException e) {
        e.printStackTrace();
      } finally {
        server.removeClient(this);
        try {
          socket.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }

    //受信したメッセージの処理を行うメソッド
    private void handleMessage(String message) {

      //メッセージが"ANSWER_c"で始まる場合、回答として扱われます
      if (message.startsWith("ANSWER_c ")) {
        handleAnswer(message.substring(9));

        //メッセージが"START_c"で始まる場合、クライアントがゲームを開始する意図を示しており、サーバーが問題を送信します。
      } else if (message.startsWith("START_c")) {
        
        //質問が進行中でない場合、問題を送信する
        if (!questionInProgress.get()) {
          server.sendRandomQuestion();
        }
      }
    }

    //クライアントからの回答の正誤判定をする
    private void handleAnswer(String clientAnswer) {
      //正解の場合
      if (answer != null && clientAnswer.equalsIgnoreCase(answer)) {
        //out.println("RESULT correct");

        //正解したクライアントのみに得点を与える
        out.println("CORRECT_s " + "1");

        //回答を送信
        for (ClientHandler client : clients) {
                  client.out.println("ANSWER_s "+client.answer);
        }
 
        //次の問題に進むための処理を全クライアントに行う
        server.handleAnswerReceived();
      } else {
        //out.println("RESULT incorrect");

        //間違いのメッセージを表示する
        out.println("WRONG_s ");


      }
    }

     
    public void sendMessage(String message) {
      out.println(message);
    }

    public void setAnswer(String answer) {
      this.answer = answer;
    }
  }

  public static void main(String[] args) throws IOException {
    QuizServer_fix server = new QuizServer_fix();
    server.start();
  }
}

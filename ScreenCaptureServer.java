package screencaptureserver;

import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

public class ScreenCaptureServer implements KeyEventDispatcher {

    enum HttpStatus {
        OK(200, "OK"),
        BAD_REQUEST(400, "Bad request"),
        NOT_FOUND(404, "Not found");

        final int code;
        final String message;

        HttpStatus(int code, String message) {
            this.code = code;
            this.message = message;
        }
    }

    private static final int SERVER_PORT = 8080;
    private static final String FORMAT = "jpeg";
    private static String HOST_NAME;
    private static String HOST_ADDR;
    private ServerSocket serverSocket;
    private Robot robot;
    private Map<String, Integer> keyCombos;

    public static void main(String[] args) throws Exception {
        HOST_NAME = InetAddress.getLocalHost().getHostName();
        HOST_ADDR = InetAddress.getLocalHost().getHostAddress();
        System.out.println("Starting ScreenCaptureServer on: http://" + HOST_ADDR + ":8080");
        new ScreenCaptureServer().run();
    }

    public ScreenCaptureServer() throws Exception {
        serverSocket = new ServerSocket(SERVER_PORT);
        serverSocket.setReuseAddress(true);
        robot = new Robot();
        keyCombos = getKeyCombos();
    }

    public void run() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);

        while (true) {
            try {
                Socket socket = serverSocket.accept();
                socket.setSoTimeout(10000);
                serve(socket);
            } catch (IOException e) {
                System.out.println("IOException: " + e.getMessage());
                return;
            }
        }
    }

    private void serve(Socket socket) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());

        String request = in.readLine();
        writeLog("REQ: " + request);

        String path = parseRequestLocation(request);

        if (path == null || path.length() < 1) {
            sendText(out, HttpStatus.BAD_REQUEST, "Bad Request: " + request);
        } else {
            if (path.startsWith("/screen.map?")) {
                int q = path.indexOf('?');
                int comma = path.indexOf(',', q);
                int x = Integer.parseInt(path.substring(q + 1, comma));
                int y = Integer.parseInt(path.substring(comma + 1));
                clickMouse(x, y);
                sleep(1000);
                sendMainPage(out);
            } else if (path.equals("/screen")) {
                sendScreenshot(out);
            } else if (path.equals("/")) {
                sendMainPage(out);
            } else if (path.contains("/submit")) {
                if (request.startsWith("POST")) {

                    // Read the submitted data
                    StringBuilder requestData = new StringBuilder();
                    while (in.ready()) {
                        requestData.append((char) in.read());
                    }
                    // Parse the submitted text
                    String submittedText = "";
                    String[] requestDataTokens = requestData.toString().split("\\r?\\n");
                    for (String token : requestDataTokens) {
                        if (token.startsWith("text=")) {
                            submittedText = URLDecoder.decode(token.substring(5), "UTF-8");
                            break;
                        }
                    }

                    // Simulate keystrokes using the submitted text
                    simulateKeystrokes(submittedText);
                }
                sendMainPage(out);
            } else {
                sendText(out, HttpStatus.NOT_FOUND, "Page not found");
            }
        }

        out.flush();
        out.close();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            System.out.println("InterruptedException: " + e.getMessage());
        }
    }

    private String parseRequestLocation(String request) throws UnsupportedEncodingException {
        if (request != null) {
            String[] tokens = request.split("\\s+");

            if (tokens.length == 3 && tokens[0].equals("GET") && (tokens[2].equals("HTTP/1.0") || tokens[2].equals("HTTP/1.1"))) {
                return URLDecoder.decode(tokens[1], "UTF-8");
            } else if (tokens.length == 3 && tokens[0].equals("POST") && (tokens[2].equals("HTTP/1.0") || tokens[2].equals("HTTP/1.1"))) {
                return URLDecoder.decode(tokens[1], "UTF-8");
            }
        }
        return null;
    }

    private void clickMouse(int x, int y) {
        writeLog("Click on screen position: [" + x + "," + y + "]");
        robot.mouseMove(x, y);
        //robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    private void sendScreenshot(BufferedOutputStream out) throws IOException {
        BufferedImage image = robot.createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));
        ByteArrayOutputStream baos = new ByteArrayOutputStream(500000);
        ImageIO.write(image, FORMAT, baos);
        sendDataContent(out, HttpStatus.OK, "image/" + FORMAT, baos.toByteArray());
    }

    private void sendMainPage(BufferedOutputStream out) throws IOException {
        String output = "<html><head><title>" + HOST_NAME + "</title></head><body>"
                + "<form action=\"/submit\" method=\"POST\">"
                + "<input type=\"text\" name=\"text\" placeholder=\"Enter text\">"
                + "<input type=\"submit\" value=\"Submit\">"
                + "</form>"
                + "<a href=\"screen.map\"><img border=\"0\" ismap=\"ismap\" src=\"/screen\"></a>"
                + "</body></html>";
        sendText(out, HttpStatus.OK, output);
    }

    private static void sendDataContent(BufferedOutputStream out, HttpStatus status, String contentType, byte[] content) throws IOException {
        Date date = new Date();
        int contentLength = content.length;
        String headers = "HTTP/1.0 " + status.code + " " + status.message + "\r\n"
                + "Date: " + date + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Pragma: no-cache\r\n"
                + (contentLength > 0 ? "Content-Length: " + contentLength + "\r\n" : "")
                + "Last-Modified: " + date + "\r\n"
                + "\r\n";
        out.write(headers.getBytes());
        out.write(content);
    }

    private static void sendText(BufferedOutputStream out, HttpStatus status, String message) throws IOException {
        sendDataContent(out, status, "text/html", message.getBytes());
    }

    private Map<String, Integer> getKeyCombos() {
        Map<String, Integer> keyCombos = new HashMap<>();
        keyCombos.put("enter", KeyEvent.VK_ENTER);
        keyCombos.put("Enter", KeyEvent.VK_ENTER);
        keyCombos.put("sup", KeyEvent.VK_BACK_SPACE);
        keyCombos.put("Sup", KeyEvent.VK_BACK_SPACE);
        keyCombos.put("ctrl+c", KeyEvent.VK_C);
        keyCombos.put("ctrl+v", KeyEvent.VK_V);
        keyCombos.put("ctrl+x", KeyEvent.VK_X);
        //keyCombos.put("cmd+space", TODO);
        return keyCombos;
    }

    private void simulateKeystrokes(String text) {
        String[] textSplit = text.split(" ");
        for (String word : textSplit) {
            if (keyCombos.containsKey(word.toLowerCase()) || keyCombos.containsKey(word.toUpperCase())) {
                int keyCode = keyCombos.get(word.toUpperCase());
                robot.keyPress(keyCode);
                robot.keyRelease(keyCode);
            } else {
                for (int i = 0; i < text.length(); i++) {
                    int keyCode = KeyEvent.getExtendedKeyCodeForChar(text.charAt(i));
                    if (keyCode != KeyEvent.VK_UNDEFINED) {
                        sendKeyPress(keyCode);
                        sleep(100);
                    }
                }
            }
        }
    }
    
    private void sendKeyPress(int keyCode) {
        robot.keyPress(keyCode);
        robot.keyRelease(keyCode);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getID() == KeyEvent.KEY_PRESSED) {
            String letter = String.valueOf(e.getKeyChar());
            if (keyCombos.containsKey(letter.toUpperCase())) {
                int keyCode = keyCombos.get(letter.toUpperCase());
                robot.keyPress(keyCode);
                robot.keyRelease(keyCode);
                System.out.println("Key combination pressed for letter: " + letter);
            } else {
                System.out.println("No key combination found for letter: " + letter);
            }
        }
        return false;
    }

    private static void writeLog(String message) {
        System.out.println(message);
    }
}

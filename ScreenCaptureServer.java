package remotedesktop;

/**
 *
 * @author Slam
 */
import java.awt.GraphicsEnvironment;
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
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import javax.imageio.ImageIO;

public class RemoteDesktop implements KeyEventDispatcher {

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

    private static final int SERVER_PORT = 3000;
    private static final String FORMAT = "jpeg";
    private static String HOST_NAME;
    private static String HOST_ADDR;
    private ServerSocket serverSocket;
    private Robot robot;
    private Map<String, Integer> keyCombos;
    private String currentDirectory;
    private File dir;

    public static void main(String[] args) throws Exception {
        HOST_NAME = InetAddress.getLocalHost().getHostName();
        HOST_ADDR = InetAddress.getLocalHost().getHostAddress();
        System.out.println("Starting ScreenCaptureServer on: http://" + HOST_ADDR + ":" + SERVER_PORT);
        String msg = "Este es un mensaje informativo, si puedes verlo significa que está funcionando correctamente el servicio remoto\n"
                + "Envía al chat la siguiente información o captura la pantalla:\n"
                + HOST_NAME + "-> " + getMyPublicIP();
        System.out.println(msg);
        //JOptionPane.showMessageDialog(null, msg);
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("Running in headless mode");
            // Evitar usar clases como Robot aquí 
            // modo solo consola o terminal
            System.out.println("Activando modo consola");

            new RemoteDesktop(true).runTerminalMode();
        } else {
            // Modo UI 
            System.out.println("Modo gráfico activo");
            new RemoteDesktop(false).run();
        }

    }

    public void runTerminalMode() {
        String startDirectory = System.getProperty("user.dir");
        JFileExplorer explorer = new JFileExplorer(startDirectory);
        explorer.start();
    }

    public static String getMyPublicIP() {
        String res = "";
        try {
            Enumeration<NetworkInterface> eths = NetworkInterface.getNetworkInterfaces();
            while (eths.hasMoreElements()) {
                NetworkInterface ni = eths.nextElement();
                if (ni.isUp() && ni.supportsMulticast()) {
                    Enumeration<InetAddress> inets = ni.getInetAddresses();
                    while (inets.hasMoreElements()) {
                        InetAddress ia = inets.nextElement();
                        if (ia.isSiteLocalAddress()) {
                            res += ia.toString();
                        }
                    }
                }
            }
        } catch (SocketException ex) {
            System.err.println("SocketEx: " + ex.getMessage());
        }
        return res;
    }

    public RemoteDesktop(boolean isTerminal) throws Exception {
        serverSocket = new ServerSocket(SERVER_PORT);
        serverSocket.setReuseAddress(true);
        String startDirectory = System.getProperty("user.dir");
        this.currentDirectory = new File(startDirectory).getAbsolutePath();
        this.dir = new File(currentDirectory);
        if (!isTerminal) {
            robot = new Robot();
            keyCombos = getKeyCombos();
        }
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

        for (char ch : text.toCharArray()) {
            int keyCode = KeyEvent.getExtendedKeyCodeForChar(ch);
            if (keyCode != KeyEvent.VK_UNDEFINED) {
                sendKeyPress(keyCode);
            }
        }
        System.out.println("Typed: " + text);

        /*
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
        }*/
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

class JFileExplorer {

    private String currentDirectory;
    private File dir;

    public JFileExplorer(String startDirectory) {
        this.currentDirectory = new File(startDirectory).getAbsolutePath();
        this.dir = new File(currentDirectory);
    }

    public void start() {
        Scanner scanner = new Scanner(System.in);
        String command;

        while (true) {
            System.out.print(currentDirectory + "> ");
            command = scanner.nextLine().trim();

            if (command.equals("exit")) {
                break;
            }

            if (command.startsWith("cd")) {
                changeDirectory(command);
            } else if (command.startsWith("ls")) {
                listFiles();
            } else if (command.startsWith("cat")) {
                concatenate(command); //cat command for display file content
            } /*else if (command.startsWith("cls") || command.startsWith("clear")) {
                clear(); //clear screen
            }*/ else if (command.startsWith("help")) {
                System.out.println("Commands:");
                System.out.println("help - Show this texts");
                System.out.println("exit - Exits the program");
            } else {
                executeCommand(command);
            }
        }
    }

    private void executeCommand(String command) {
        try {
            ProcessBuilder builder = new ProcessBuilder();
            // Determina si usar shell/bash o cmd
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                builder.command("cmd.exe", "/c", command);
            } else {
                builder.command("sh", "-c", command);
            }

            builder.redirectErrorStream(true);
            Process process = builder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                //currentDirectory = line;
                System.out.println(line);
            }

            int exitCode = process.waitFor();
            System.out.println("\nExited with code: " + exitCode);
        } catch (Exception e) {
            System.err.println("E: " + e.getMessage());
        }
    }

    private void changeDirectory(String cmdWithPath) {
        String[] input = cmdWithPath.split(" ", 2);

        if (input.length > 1) {
            String path = input[1].trim();

            if (path.equals("..")) {
                dir = new File(currentDirectory).getParentFile();
            } else if (path.equals(".")) {
                dir = new File(currentDirectory);
            } else {
                dir = new File(path);
                if (!dir.isAbsolute()) {
                    dir = new File(currentDirectory, path);
                }
            }

            if (dir != null && dir.exists() && dir.isDirectory()) {
                try {
                    currentDirectory = dir.getCanonicalPath();
                } catch (IOException e) {
                    System.out.println("Error obtaining canonical path: " + e.getMessage());
                }
            } else {
                System.out.println("Directory does not exist: " + path);
            }
        } else {
            System.out.println("No directory specified.");
        }
    }

    private void listFiles() {
        if (dir != null) {
            for (File files : dir.listFiles()) {
                if (files.isFile()) {
                    System.out.println(
                            (files.canRead() ? "r" : "-")
                            + (files.canWrite() ? "w" : "-")
                            + (files.canExecute() ? "x" : "-")
                            + " " + new Date(files.lastModified()).toString()
                            + " " + files.length()
                            + " --> " + files.getName() + " (FILE)");
                } else if (files.isDirectory()) {
                    System.out.println(
                            (files.canRead() ? "r" : "-")
                            + (files.canWrite() ? "w" : "-")
                            + (files.canExecute() ? "x" : "-")
                            + " " + new Date(files.lastModified()).toString()
                            + " " + files.length()
                            + " --> " + files.getName() + " (DIR)");
                }
            }
        }
    }

    private void concatenate(String command) {
        String[] splCMD = command.split(" ", 2);
        if (splCMD.length > 1) {
            try {
                File cfile = new File(dir, splCMD[1]);
                BufferedReader br = new BufferedReader(new FileReader(cfile));
                String line;
                while ((line = br.readLine()) != null) {
                    System.out.println(line);
                }
            } catch (IOException ex) {
                System.out.println("IOE: " + ex.getMessage());
            }
        }
    }

    /*private void clear() { // cls or clear screen
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }*/
}

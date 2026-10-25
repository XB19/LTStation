package com.ltstation.center.sw.hw;

import com.ltstation.center.legacy.LegacyLog;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Serveur FTP des caméras de lecture de plaques (Station.ListenClientConnect et commandes FTP) :
 * les caméras déposent leurs photos dans « Fiddler » ; une commande SIZE sur un fichier .jpg
 * transmet le nom (IP_..._plaque_...) au traitement d'autorisation du véhicule.
 */
public class CameraFtpServer {

    private static final Charset ANSI = Charset.forName("GBK");

    private final String ip;
    private final Map<String, String> users;
    private final LegacyLog log;
    private final Consumer<String> dealCarNo;
    private final Path workDir;
    private volatile ServerSocket listener;

    /** Session FTP (FTPUserSeesion) : lecture / écriture de lignes en page de code du poste, octets bruts. */
    static final class Session {
        final Socket socket;
        final BufferedReader reader;
        final PrintWriter writer;
        final InputStream in;
        final OutputStream out;

        Session(Socket s) throws IOException {
            socket = s;
            in = s.getInputStream();
            out = s.getOutputStream();
            reader = new BufferedReader(new InputStreamReader(in, ANSI));
            writer = new PrintWriter(new OutputStreamWriter(out, ANSI), true);
        }

        void writeLine(String line) {
            writer.print(line + "\r\n");
            writer.flush();
        }

        void close() {
            try {
                socket.shutdownInput();
                socket.shutdownOutput();
            } catch (IOException ignored) {
                // déjà fermée
            }
            try {
                socket.close();
            } catch (IOException ignored) {
                // déjà fermée
            }
        }
    }

    /** FTPUser */
    static final class User {
        Session commandSession;
        Session dataSession;
        ServerSocket dataListener;
        InetSocketAddress remoteEndPoint;
        String userName = "";
        String workDir;
        String currentDir;
        int loginOK;
        boolean isBinary;
        boolean isPassive;
    }

    public CameraFtpServer(String ip, Map<String, String> users, LegacyLog log, Path baseDir, Consumer<String> dealCarNo) {
        this.ip = ip;
        this.users = users;
        this.log = log;
        this.dealCarNo = dealCarNo;
        this.workDir = baseDir.resolve("Fiddler");
    }

    public boolean running() {
        return listener != null && !listener.isClosed();
    }

    /** ListenClientConnect : écoute sur le port 21 de l'adresse configurée (urlIP). */
    public void ListenClientConnect() {
        try {
            listener = new ServerSocket();
            listener.bind(new InetSocketAddress(InetAddress.getByName(ip), 21));
            log.writeLog("Start");
            Files.createDirectories(workDir);
            String text = workDir.toAbsolutePath() + java.io.File.separator;
            while (true) {
                try {
                    Socket client = listener.accept();
                    log.writeLog("客户端（" + client.getRemoteSocketAddress() + "）与本机（" + listener.getLocalSocketAddress() + "）建立Ftp连接");
                    User u = new User();
                    u.commandSession = new Session(client);
                    u.workDir = text;
                    Thread t = new Thread(() -> UserProcessing(u), "ftp-user");
                    t.setDaemon(true);
                    t.start();
                } catch (IOException e) {
                    break;
                }
            }
        } catch (IOException | RuntimeException ex) {
            log.writeLog("FTP Error:" + ex.getMessage());
        }
    }

    public void stop() {
        try {
            if (listener != null) {
                listener.close();
            }
        } catch (IOException ignored) {
            // déjà fermé
        }
    }

    private void UserProcessing(User user) {
        try {
            String str = "220";
            RepleyCommandToUser(user, str);
            while (true) {
                String text;
                try {
                    text = user.commandSession.reader.readLine();
                } catch (IOException ex) {
                    if (!user.commandSession.socket.isConnected()) {
                        log.writeLog("客户端(" + user.commandSession.socket.getRemoteSocketAddress() + "断开连接！)");
                    } else {
                        log.writeLog("接收命令失败！" + ex.getMessage());
                    }
                    return;
                }
                if (text == null) {
                    break;
                }
                log.writeLog("来自" + user.commandSession.socket.getRemoteSocketAddress() + "：[" + text + "]");
                if (text.contains("SIZE") && text.contains(".jpg")) {
                    dealCarNo.accept(text);
                }
                String cmd = text;
                String param = "";
                int num = text.indexOf(' ');
                if (num != -1) {
                    cmd = text.substring(0, num).toUpperCase();
                    param = text.substring(cmd.length()).trim();
                }
                try {
                    if (cmd.equals("QUIT")) {
                        RepleyCommandToUser(user, "220 Bye");
                        user.commandSession.close();
                        return;
                    }
                    switch (user.loginOK) {
                        case 0 -> CommandUser(user, cmd, param);
                        case 1 -> CommandPassword(user, cmd, param);
                        case 2 -> {
                            switch (cmd) {
                                case "CWD" -> CommandCWD(user, param);
                                case "PWD" -> RepleyCommandToUser(user, "257 '" + user.currentDir + "' is the current directory");
                                case "PASV" -> CommandPASV(user);
                                case "PORT" -> CommandPORT(user, param);
                                case "LIST", "NLIST" -> CommandLIST(user, param);
                                case "RETR" -> CommandRETR(user, param);
                                case "STOR" -> CommandSTOR(user, param);
                                case "DELE" -> CommandDELE(user, param);
                                case "TYPE" -> CommandTYPE(user, param);
                                default -> RepleyCommandToUser(user, "502 command is not implemented.");
                            }
                        }
                        default -> {
                        }
                    }
                } catch (IOException | RuntimeException ex2) {
                    log.writeLog("eexx:" + ex2.getMessage());
                }
            }
            log.writeLog("接收字符串为null,结束线程！");
        } catch (RuntimeException ex3) {
            log.writeLog("UserProcessing Error:" + ex3.getMessage());
        }
    }

    private void RepleyCommandToUser(User user, String str) {
        try {
            user.commandSession.writeLine(str);
            log.writeLog("向客户端（" + user.commandSession.socket.getRemoteSocketAddress() + "）发送[" + str + "]");
        } catch (RuntimeException e) {
            log.writeLog("向客户端（" + user.commandSession.socket.getRemoteSocketAddress() + "）发送信息失败");
        }
    }

    private void CommandUser(User user, String command, String param) {
        String reply;
        if (command.equals("USER")) {
            reply = "331 USER command OK, password required.";
            user.userName = param;
            user.loginOK = 1;
        } else {
            reply = "501 USER command syntax error.";
        }
        RepleyCommandToUser(user, reply);
    }

    private void CommandPassword(User user, String command, String param) {
        String reply;
        if (command.equals("PASS")) {
            String value = users.get(user.userName);
            if (value != null) {
                if (value.equals(param)) {
                    reply = "230 User logged in success";
                    user.loginOK = 2;
                } else {
                    reply = "530 Password incorrect.";
                }
            } else {
                reply = "530 User name or password incorrect.";
            }
        } else {
            reply = "501 PASS command Syntax error.";
        }
        RepleyCommandToUser(user, reply);
        user.currentDir = user.workDir;
    }

    private void CommandCWD(User user, String temp) {
        String reply;
        try {
            String text = trimEnd(user.workDir, '/') + "/" + temp;
            if (Files.isDirectory(Path.of(text))) {
                user.currentDir = text;
                reply = "250 Directory changed to '" + text + "' successfully";
            } else {
                reply = "550 Directory '" + text + "' does not exist";
            }
        } catch (RuntimeException e) {
            reply = "502 Directory changed unsuccessfully";
        }
        RepleyCommandToUser(user, reply);
    }

    private static String trimEnd(String s, char c) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == c) {
            end--;
        }
        return s.substring(0, end);
    }

    private static String stamp(Path p) throws IOException {
        LocalDateTime t = LocalDateTime.ofInstant(Files.readAttributes(p, BasicFileAttributes.class).creationTime().toInstant(), ZoneId.systemDefault());
        return t.getMonth().getDisplayName(TextStyle.SHORT, Locale.US) + t.format(DateTimeFormatter.ofPattern(" dd yyyy"));
    }

    private void CommandLIST(User user, String parameter) throws IOException {
        StringBuilder text = new StringBuilder();
        String host = InetAddress.getLocalHost().getHostName();
        Path dir = Path.of(user.currentDir);
        if (!parameter.isEmpty()) {
            if (Files.isDirectory(Path.of(user.currentDir + parameter))) {
                dir = Path.of(user.currentDir + parameter);
            } else {
                String t2 = trimEnd(user.currentDir, '/');
                user.currentDir = t2.substring(0, t2.lastIndexOf('/') + 1);
            }
        }
        try (Stream<Path> s = Files.list(dir)) {
            for (Path d : s.filter(Files::isDirectory).toList()) {
                text.append("dwr-\\t").append(host).append('\t').append(stamp(d)).append('\t').append(d.getFileName()).append(System.lineSeparator());
            }
        }
        Path fdir = Path.of(user.currentDir);
        if (!parameter.isEmpty() && Files.isDirectory(Path.of(user.currentDir + parameter + "/"))) {
            fdir = Path.of(user.currentDir + parameter + "/");
        }
        try (Stream<Path> s = Files.list(fdir)) {
            for (Path f : s.filter(Files::isRegularFile).toList()) {
                text.append("-wr-\t").append(host).append('\t').append(Files.size(f)).append(' ').append(stamp(f)).append('\t').append(f.getFileName())
                    .append(System.lineSeparator());
            }
        }
        boolean isBinary = user.isBinary;
        user.isBinary = false;
        RepleyCommandToUser(user, "150 Opening ASCII data connection");
        InitDataSession(user);
        log.writeLog("向用户发送(字符串信息)：[" + text + "]");
        try {
            user.dataSession.writeLine(text.toString());
            log.writeLog("发送完毕");
        } finally {
            user.dataSession.close();
        }
        RepleyCommandToUser(user, "226 Transfer complete");
        user.isBinary = isBinary;
    }

    private void CommandRETR(User user, String filename) throws IOException {
        Path file = Path.of(user.currentDir + filename);
        RepleyCommandToUser(user, user.isBinary ? "150 Opening BINARY mode data connection for download" : "150 Opening ASCII mode data connection for download");
        InitDataSession(user);
        log.writeLog("向用户发送(文件流)：[...");
        try (InputStream fs = Files.newInputStream(file)) {
            if (user.isBinary) {
                fs.transferTo(user.dataSession.out);
                user.dataSession.out.flush();
            } else {
                for (String line : Files.readAllLines(file, ANSI)) {
                    user.dataSession.writeLine(line);
                }
            }
            log.writeLog("...]发送完毕！");
        } finally {
            user.dataSession.close();
        }
        RepleyCommandToUser(user, "226 Transfer complete");
    }

    private void CommandSTOR(User user, String filename) {
        try {
            Path file = Path.of(user.currentDir + filename);
            RepleyCommandToUser(user, user.isBinary ? "150 Opening BINARY mode data connection for upload" : "150 Opeing ASCII mode data connection for upload");
            InitDataSession(user);
            log.writeLog("接收用户上传数据（文件流）：[...");
            try (OutputStream fs = Files.newOutputStream(file)) {
                if (user.isBinary) {
                    user.dataSession.in.transferTo(fs);
                } else {
                    String line;
                    while ((line = user.dataSession.reader.readLine()) != null) {
                        fs.write(line.getBytes(ANSI));
                    }
                }
                log.writeLog("...]接收完毕");
            } finally {
                user.dataSession.close();
            }
            RepleyCommandToUser(user, "226 Transfer complete");
        } catch (IOException | RuntimeException ex) {
            log.writeLog("Cmd Insert Error:" + ex.getMessage());
        }
    }

    private void CommandDELE(User user, String filename) throws IOException {
        log.writeLog("正在删除文件" + filename + "...");
        Files.deleteIfExists(Path.of(user.currentDir + filename));
        log.writeLog("删除成功");
        RepleyCommandToUser(user, "250 File " + filename + " has been deleted.");
    }

    private void CommandPASV(User user) throws IOException {
        InetAddress address = InetAddress.getByName(ip);
        Random random = new Random();
        int num;
        int num2;
        while (true) {
            num = 5 + random.nextInt(195);
            num2 = random.nextInt(200);
            int port = (num << 8) | num2;
            try {
                user.dataListener = new ServerSocket();
                user.dataListener.bind(new InetSocketAddress(address, port));
                log.writeLog("TCP 数据连接已打开（被动模式）--" + address.getHostAddress() + "：" + port);
            } catch (IOException e) {
                continue;
            }
            break;
        }
        user.isPassive = true;
        RepleyCommandToUser(user, "227 Entering Passive Mode(" + address.getHostAddress().replace('.', ',') + "," + num + "," + num2 + ")");
    }

    private void CommandPORT(User user, String portString) {
        String[] a = portString.split(",");
        String host = a[0] + "." + a[1] + "." + a[2] + "." + a[3];
        int port = (Integer.parseInt(a[4]) << 8) | Integer.parseInt(a[5]);
        user.remoteEndPoint = new InetSocketAddress(host, port);
        RepleyCommandToUser(user, "200 PORT command successful.");
    }

    private void CommandTYPE(User user, String param) {
        String reply;
        if (param.equals("I")) {
            user.isBinary = true;
            reply = "220 Type set to I(Binary)";
        } else {
            user.isBinary = false;
            reply = "330 Type set to A(ASCII)";
        }
        RepleyCommandToUser(user, reply);
    }

    private void InitDataSession(User user) throws IOException {
        Socket client;
        if (user.isPassive) {
            log.writeLog("采用被动模式返回LIST目录和文件列表");
            client = user.dataListener.accept();
        } else {
            log.writeLog("采用主动模式向用户发送LIST目录和文件列表");
            client = new Socket();
            client.connect(user.remoteEndPoint);
        }
        user.dataSession = new Session(client);
    }
}

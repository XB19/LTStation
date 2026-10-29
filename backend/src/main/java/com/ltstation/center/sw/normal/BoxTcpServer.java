package com.ltstation.center.sw.normal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Serveur TCP des boîtiers sans fil (TouchSocket TcpService de NormalMainForm) : port 7011, une session par boîtier,
 * renommée avec le numéro du boîtier (ResetIdAsync), fermée après 7 s sans données (CheckClear « 超时无数据 »).
 */
public final class BoxTcpServer {

    /** TcpSessionClient : connexion d'un boîtier. */
    public static final class Session {
        volatile String id;
        public final String IP;
        public final int Port;
        private final Socket socket;
        private volatile long lastData = System.currentTimeMillis();

        Session(String id, Socket socket) {
            this.id = id;
            this.socket = socket;
            this.IP = socket.getInetAddress().getHostAddress();
            this.Port = socket.getPort();
        }

        public String Id() {
            return id;
        }

        /** ResetIdAsync : l'identifiant devient le numéro du boîtier. */
        public void ResetId(String newId) {
            clients.remove(id, this);
            id = newId;
            clients.put(newId, this);
        }

        public synchronized void Send(byte[] data) {
            try {
                OutputStream out = socket.getOutputStream();
                out.write(data);
                out.flush();
            } catch (IOException e) {
                Close(e.getMessage());
            }
        }

        public void Close(String reason) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // déjà fermée
            }
        }
    }

    private static final Map<String, Session> clients = new ConcurrentHashMap<>();
    private static final AtomicLong counter = new AtomicLong();
    private static volatile ServerSocket server;

    /** Plugin de réception (BoxReceiveAsyncPlugin) et événement Closed de NormalMainForm. */
    public static volatile BiConsumer<Session, byte[]> OnReceived = (s, b) -> { };
    public static volatile Consumer<Session> OnClosed = s -> { };

    private BoxTcpServer() {
    }

    public static boolean IsRunning() {
        return server != null && !server.isClosed();
    }

    public static synchronized void Start(int port) throws IOException {
        if (IsRunning()) {
            return;
        }
        ServerSocket ss = new ServerSocket();
        ss.setReuseAddress(true);
        ss.bind(new InetSocketAddress(port));
        server = ss;
        thread("box-tcp-accept", () -> {
            while (!ss.isClosed()) {
                try {
                    Socket s = ss.accept();
                    Session session = new Session(String.valueOf(counter.incrementAndGet()), s);
                    clients.put(session.id, session);
                    thread("box-tcp-" + session.id, () -> read(session));
                } catch (IOException e) {
                    if (ss.isClosed()) {
                        return;
                    }
                }
            }
        });
        thread("box-tcp-checkclear", () -> {
            while (!ss.isClosed()) {
                long now = System.currentTimeMillis();
                for (Session s : clients.values()) {
                    if (now - s.lastData > 7000) {
                        s.Close("Aucune donnée (délai dépassé)");
                    }
                }
                sleep(1000);
            }
        });
    }

    public static synchronized void Stop() {
        if (server != null) {
            try {
                server.close();
            } catch (IOException ignored) {
                // déjà fermé
            }
            server = null;
        }
        for (Session s : clients.values()) {
            s.Close("Stop");
        }
    }

    /** TcpService.TryGetClient */
    public static Session TryGetClient(String id) {
        return clients.get(id);
    }

    private static void read(Session session) {
        byte[] buf = new byte[8192];
        try (InputStream in = session.socket.getInputStream()) {
            int n;
            while ((n = in.read(buf)) > 0) {
                session.lastData = System.currentTimeMillis();
                try {
                    OnReceived.accept(session, Arrays.copyOf(buf, n));
                } catch (RuntimeException e) {
                    // ClosePlugin : une erreur de traitement ferme la connexion
                    session.Close(e.getMessage());
                }
            }
        } catch (IOException ignored) {
            // connexion coupée
        } finally {
            clients.remove(session.id, session);
            session.Close("Closed");
            OnClosed.accept(session);
        }
    }

    private static void thread(String name, Runnable r) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        t.start();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

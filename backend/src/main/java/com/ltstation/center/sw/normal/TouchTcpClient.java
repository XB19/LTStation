package com.ltstation.center.sw.normal;

import static com.ltstation.center.legacy.Sugar.p;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.SW;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Wireless.TouchTcpClient : connexion TCP à un appareil de la station (StationDevice, adresse BindUrl « hôte:port »).
 * Comme l'original, NormalMainForm ne fait que s'y connecter ; aucun abonné ne traite les données reçues.
 */
public class TouchTcpClient {

    public int ID;
    public String MahcineType;
    private String url;
    private volatile Socket client;
    private volatile Thread receiveTask;
    public volatile BiConsumer<String, byte[]> OnDataReceived = (a, b) -> { };
    public volatile Consumer<String> OnConnected = a -> { };
    public volatile BiConsumer<String, String> OnDisconnected = (a, b) -> { };

    public TouchTcpClient(int id) {
        try {
            ID = id;
            Row device = SW.db().first("StationDevice", "{ID} = :i", p("i", id));
            if (device != null) {
                url = device.str("BindUrl");
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public boolean isConnected() {
        Socket s = client;
        return s != null && s.isConnected() && !s.isClosed();
    }

    public boolean Connect() {
        if (isConnected()) {
            return true;
        }
        try {
            if (url == null || url.isEmpty()) {
                throw new IllegalStateException("Connected error");
            }
            String host = url.replaceFirst("^tcp://", "");
            int colon = host.lastIndexOf(':');
            Socket s = new Socket();
            s.connect(new InetSocketAddress(host.substring(0, colon), Integer.parseInt(host.substring(colon + 1))), 3000);
            client = s;
            StartReceiveLoop();
            OnConnected.accept(String.valueOf(ID));
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private void StartReceiveLoop() {
        if (receiveTask != null && receiveTask.isAlive()) {
            return;
        }
        receiveTask = new Thread(() -> {
            byte[] buf = new byte[4096];
            try (InputStream in = client.getInputStream()) {
                int n;
                while (isConnected() && (n = in.read(buf)) > 0) {
                    OnDataReceived.accept(String.valueOf(ID), Arrays.copyOf(buf, n));
                }
                OnDisconnected.accept(String.valueOf(ID), "Closed");
            } catch (IOException | RuntimeException ignored) {
                // connexion coupée
            }
        }, "touch-tcp-" + ID);
        receiveTask.setDaemon(true);
        receiveTask.start();
    }

    public boolean Send(byte[] data) {
        if (!isConnected()) {
            return false;
        }
        try {
            client.getOutputStream().write(data);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public boolean InitAndLogin() {
        return true;
    }
}

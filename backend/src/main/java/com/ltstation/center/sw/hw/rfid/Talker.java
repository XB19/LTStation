package com.ltstation.center.sw.hw.rfid;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.function.Consumer;

/** RFIDReader.Talker : lecteur RFID relié en TCP (réception sur un fil dédié). */
public class Talker {

    private Socket client;
    private InputStream in;
    private OutputStream out;
    private Thread waitThread;
    private volatile boolean bIsConnect;
    public Consumer<byte[]> MessageReceived = b -> { };

    public boolean Connect(String ip, int port, String[] strException) {
        strException[0] = "";
        try {
            client = new Socket();
            client.connect(new InetSocketAddress(ip, port), 5000);
            in = client.getInputStream();
            out = client.getOutputStream();
            waitThread = new Thread(this::ReceivedData, "rfid-tcp");
            waitThread.setDaemon(true);
            waitThread.start();
            bIsConnect = true;
            return true;
        } catch (IOException | RuntimeException ex) {
            strException[0] = ex.getMessage();
            return false;
        }
    }

    private void ReceivedData() {
        byte[] buf = new byte[4096];
        while (bIsConnect) {
            try {
                int num = in.read(buf);
                if (num < 0) {
                    break;
                }
                if (num != 0) {
                    MessageReceived.accept(Arrays.copyOf(buf, num));
                }
            } catch (IOException | RuntimeException e) {
                if (!bIsConnect) {
                    break;
                }
            }
        }
    }

    public synchronized boolean SendMessage(byte[] buffer) {
        try {
            out.write(buffer);
            out.flush();
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    public void SignOut() {
        bIsConnect = false;
        try {
            if (client != null) {
                client.close();
            }
        } catch (IOException ignored) {
            // déjà fermé
        }
    }

    public boolean IsConnect() {
        return bIsConnect;
    }
}

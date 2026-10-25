package com.ltstation.center.sw.hw;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * DataBaseDeal.TCPClientQY : client TCP de la plateforme de supervision QY (JSON en UTF-8,
 * délai de réception de 100 ms). Les erreurs sont rendues comme les codes Winsock d'origine.
 */
public class TCPClientQY {

    public Socket tcpClient;
    public String IP;
    public int Port;

    public TCPClientQY(String ip, int port) {
        this.IP = ip;
        this.Port = port;
    }

    public synchronized boolean TcpConnect() {
        try {
            if (tcpClient != null) {
                tcpClient.close();
            }
            tcpClient = new Socket();
            tcpClient.connect(new InetSocketAddress(IP, Port), 5000);
            tcpClient.setSoTimeout(100);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    public boolean TcpSend(String mess) {
        try {
            tcpClient.getOutputStream().write(mess.getBytes(StandardCharsets.UTF_8));
            tcpClient.getOutputStream().flush();
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    public String TcpReceive() {
        try {
            if (tcpClient == null || !tcpClient.isConnected() || tcpClient.isClosed()) {
                TcpConnect();
                return "ScoketBroken:10057";
            }
            byte[] buf = new byte[1024];
            InputStream in = tcpClient.getInputStream();
            int count = in.read(buf);
            if (count < 0) {
                // connexion fermée par le serveur : Receive renvoie 0 octet
                return "";
            }
            return new String(buf, 0, count, StandardCharsets.UTF_8);
        } catch (SocketTimeoutException e) {
            return "ErrorCode:10060";
        } catch (SocketException e) {
            String m = String.valueOf(e.getMessage()).toLowerCase();
            int code = m.contains("reset") ? 10054 : m.contains("abort") ? 10053 : 10057;
            TcpConnect();
            return "ScoketBroken:" + code;
        } catch (IOException | RuntimeException e) {
            return "Exceptin:" + e.getMessage();
        }
    }
}

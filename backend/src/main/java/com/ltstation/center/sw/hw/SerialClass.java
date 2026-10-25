package com.ltstation.center.sw.hw;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import com.ltstation.center.legacy.LegacyLog;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * SerialClass (Tool) du logiciel Station : accès à un port série (pompes, boîtiers, jauges,
 * afficheurs de prix) avec jSerialComm. Sans appareil branché, l'ouverture échoue proprement
 * (openPort renvoie false avec le message d'erreur), comme l'original.
 */
public class SerialClass {

    /** Parité (System.IO.Ports.Parity). */
    public enum Parity { None, Odd, Even, Mark, Space }

    /** Bits d'arrêt (System.IO.Ports.StopBits). */
    public enum StopBits { None, One, Two, OnePointFive }

    private final LegacyLog portLog = new LegacyLog("PortLog");
    private SerialPort port;
    private String portName;
    private int baudRate = 9600;
    private Parity parity = Parity.None;
    private int dataBits = 8;
    private StopBits stopBits = StopBits.One;
    private int readTimeout = 2000;
    private boolean rts;
    private boolean dtr;

    public volatile boolean ReceiveEventFlag;
    public int SleeepTime = 200;
    private Consumer<byte[]> dataReceived;

    public SerialClass() {
    }

    /** SerialClass(comPortName) : 9600 bauds, parité paire, 8 bits, 1 bit d'arrêt. */
    public SerialClass(String comPortName) {
        this.portName = comPortName;
        this.baudRate = 9600;
        this.parity = Parity.Even;
        this.dataBits = 8;
        this.stopBits = StopBits.One;
        this.rts = true;
        this.readTimeout = 2000;
    }

    public SerialClass(String comPortName, int baudRate, Parity parity, int dataBits, StopBits stopBits) {
        this.portName = comPortName;
        this.baudRate = baudRate;
        this.parity = parity;
        this.dataBits = dataBits;
        this.stopBits = stopBits;
        this.rts = true;
        this.dtr = true;
        this.readTimeout = 10000;
    }

    public String getPortName() {
        return portName;
    }

    public void setPortName(String name) {
        this.portName = name;
    }

    public int getBaudRate() {
        return baudRate;
    }

    public void setBaudRate(int baud) {
        this.baudRate = baud;
        if (port != null) {
            port.setBaudRate(baud);
        }
    }

    /** Événement DataReceived : appelé avec les octets reçus. */
    public void onDataReceived(Consumer<byte[]> handler) {
        this.dataReceived = handler;
    }

    public void setSerialPort(String comPortName, int baud, int bits, int stop) {
        closePort();
        this.portName = comPortName;
        this.baudRate = baud;
        this.parity = Parity.None;
        this.dataBits = bits;
        this.stopBits = StopBits.values()[Math.max(0, Math.min(stop, 3))];
        this.rts = false;
        this.readTimeout = 3000;
    }

    private static int jParity(Parity p) {
        return switch (p) {
            case Odd -> SerialPort.ODD_PARITY;
            case Even -> SerialPort.EVEN_PARITY;
            case Mark -> SerialPort.MARK_PARITY;
            case Space -> SerialPort.SPACE_PARITY;
            default -> SerialPort.NO_PARITY;
        };
    }

    private static int jStop(StopBits s) {
        return switch (s) {
            case Two -> SerialPort.TWO_STOP_BITS;
            case OnePointFive -> SerialPort.ONE_POINT_FIVE_STOP_BITS;
            default -> SerialPort.ONE_STOP_BIT;
        };
    }

    /** openPort : ouvre le port ; renvoie false et le message d'erreur si le port est absent. */
    public synchronized boolean openPort(String[] error) {
        if (error != null && error.length > 0) {
            error[0] = "";
        }
        closePort();
        try {
            if (portName == null || portName.isBlank()) {
                throw new IllegalStateException("Nom de port vide");
            }
            port = SerialPort.getCommPort(portName);
            port.setComPortParameters(baudRate, dataBits, jStop(stopBits), jParity(parity));
            port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, readTimeout, 0);
            if (!port.openPort()) {
                String msg = "Impossible d'ouvrir le port " + portName + " (appareil non branché ?)";
                port = null;
                throw new IllegalStateException(msg);
            }
            if (rts) {
                port.setRTS();
            } else {
                port.clearRTS();
            }
            if (dtr) {
                port.setDTR();
            }
            port.addDataListener(new SerialPortDataListener() {
                @Override
                public int getListeningEvents() {
                    return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
                }

                @Override
                public void serialEvent(SerialPortEvent event) {
                    received();
                }
            });
            return true;
        } catch (Exception ex) {
            if (error != null && error.length > 0) {
                error[0] = ex.getMessage();
            }
            portLog.writeLog("Open " + portName + " : " + ex.getMessage());
            return false;
        }
    }

    private void received() {
        if (ReceiveEventFlag || port == null) {
            return;
        }
        try {
            Thread.sleep(SleeepTime);
            int n = port.bytesAvailable();
            if (n <= 0) {
                return;
            }
            byte[] buf = new byte[n];
            int r = port.readBytes(buf, n);
            if (r > 0 && dataReceived != null) {
                dataReceived.accept(r == n ? buf : Arrays.copyOf(buf, r));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
            // comme l'original : les erreurs de réception sont ignorées
        }
    }

    public boolean PortIsOpen() {
        return port != null && port.isOpen();
    }

    public synchronized void closePort() {
        if (port != null) {
            try {
                port.removeDataListener();
                port.closePort();
            } catch (Exception ignored) {
                // port déjà fermé
            }
            port = null;
        }
    }

    public void SendData(String data) {
        if (!ReceiveEventFlag && PortIsOpen()) {
            byte[] b = data.getBytes(StandardCharsets.US_ASCII);
            port.writeBytes(b, b.length);
        }
    }

    public void SendData(byte[] data, int offset, int count) {
        if (!ReceiveEventFlag && PortIsOpen()) {
            port.writeBytes(data, count, offset);
        }
    }

    public void sendbyte(byte[] buff, int offset, int count) {
        if (PortIsOpen()) {
            port.writeBytes(buff, count, offset);
        }
    }

    public void RestBaud() {
        setBaudRate(115200);
    }

    public void setEmpty() {
        if (PortIsOpen()) {
            port.flushIOBuffers();
        }
    }

    /** SendCommand : envoie une trame et attend la réponse (Overtime × 10 ms). Renvoie -1 si le port est fermé. */
    public synchronized int SendCommand(byte[] sendData, byte[] receiveData, int overtime) {
        if (!PortIsOpen()) {
            return -1;
        }
        try {
            ReceiveEventFlag = true;
            port.flushIOBuffers();
            port.writeBytes(sendData, sendData.length);
            Thread.sleep(10);
            int n = 0;
            while (n++ < overtime && port.bytesAvailable() < receiveData.length) {
                Thread.sleep(10);
            }
            int avail = port.bytesAvailable();
            int toRead = Math.min(Math.max(avail, 0), receiveData.length);
            return toRead <= 0 ? 0 : port.readBytes(receiveData, toRead);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        } finally {
            ReceiveEventFlag = false;
        }
    }

    /** Ports série présents sur le poste (SerialPort.GetPortNames). */
    public static String[] serialsIsConnected() {
        return Arrays.stream(SerialPort.getCommPorts()).map(SerialPort::getSystemPortName).toArray(String[]::new);
    }

    public String[] getSerials() {
        return serialsIsConnected();
    }

    // ------------------------------------------------------------ conversions hexadécimales

    public static String ByteToString(byte[] in) {
        StringBuilder sb = new StringBuilder();
        for (byte b : in) {
            sb.append(String.format("%02X ", b & 0xFF));
        }
        return sb.toString();
    }

    public static byte[] StringToByte(String in) {
        String[] parts = in.split(" ");
        byte[] out = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = (byte) Integer.parseInt(parts[i], 16);
        }
        return out;
    }

    /** strToToHexByte : "0A1B" -> {0x0A, 0x1B} ; s'arrête silencieusement sur un caractère invalide. */
    public static byte[] strToToHexByte(String hex) {
        byte[] out = new byte[hex.length() / 2];
        try {
            String h = hex.replace(" ", "");
            if (h.length() % 2 != 0) {
                h += " ";
            }
            for (int i = 0; i < out.length; i++) {
                out[i] = (byte) Integer.parseInt(h.substring(i * 2, i * 2 + 2).trim(), 16);
            }
        } catch (RuntimeException ignored) {
            // comme l'original : renvoie ce qui a été converti
        }
        return out;
    }

    public static String byteToHexStr(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        if (bytes != null) {
            for (byte b : bytes) {
                sb.append(String.format("%02X", b & 0xFF));
            }
        }
        return sb.toString();
    }

    public byte[] str2Bcd(String asc) {
        if (asc.length() % 2 != 0) {
            asc = "0" + asc;
        }
        byte[] a = asc.getBytes(StandardCharsets.US_ASCII);
        byte[] out = new byte[asc.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) ((nib(a[2 * i]) << 4) + nib(a[2 * i + 1]));
        }
        return out;
    }

    private static int nib(byte c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'z') {
            return c - 'a' + 10;
        }
        return c - 'A' + 10;
    }

    public String STOHEX(int s, int len) {
        int n = len >> 1;
        byte[] b = {(byte) s, (byte) (s >> 8), (byte) (s >> 16), (byte) (s >> 24)};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(Integer.toHexString(b[n - i - 1] & 0xFF));
        }
        while (sb.length() < len) {
            sb.insert(0, '0');
        }
        return sb.toString();
    }

    public static byte ConvertBCD(byte b) {
        int v = b & 0xFF;
        return (byte) (((v / 10) << 4) | (v % 10));
    }

    public static byte ConvertBCDToInt(byte b) {
        return (byte) (((b >> 4) & 0xF) * 10 + (b & 0xF));
    }
}

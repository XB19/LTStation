package com.ltstation.center.sw.hw.rfid;

import com.ltstation.center.sw.hw.SerialClass;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * RFIDReader.ReaderMethod : commandes du lecteur RFID UHF (port série ou TCP) et découpage des trames
 * reçues (A0 + longueur) ; chaque trame complète est passée à AnalyCallback.
 */
public class ReaderMethod {

    private final Talker italker = new Talker();
    private SerialClass iSerialPort;
    private int m_nType = -1;
    public Consumer<byte[]> ReceiveCallback;
    public Consumer<byte[]> SendCallback;
    public Consumer<MessageTran> AnalyCallback;
    private final byte[] m_btAryBuffer = new byte[4096];
    private int m_nLenth;

    public ReaderMethod() {
        italker.MessageReceived = this::RunReceiveDataCallback;
    }

    public int OpenCom(String strPort, int nBaudrate, String[] strException) {
        strException[0] = "";
        CloseCom();
        try {
            iSerialPort = new SerialClass(strPort, nBaudrate, SerialClass.Parity.None, 8, SerialClass.StopBits.One);
            iSerialPort.onDataReceived(this::RunReceiveDataCallback);
            String[] err = {""};
            if (!iSerialPort.openPort(err)) {
                strException[0] = err[0];
                return -1;
            }
        } catch (RuntimeException ex) {
            strException[0] = ex.getMessage();
            return -1;
        }
        m_nType = 0;
        return 0;
    }

    public void CloseCom() {
        if (iSerialPort != null && iSerialPort.PortIsOpen()) {
            iSerialPort.closePort();
        }
        m_nType = -1;
    }

    public int ConnectServer(String ip, int port, String[] strException) {
        if (!italker.Connect(ip, port, strException)) {
            return -1;
        }
        m_nType = 1;
        return 0;
    }

    public void SignOut() {
        italker.SignOut();
        m_nType = -1;
    }

    private synchronized void RunReceiveDataCallback(byte[] received) {
        try {
            if (ReceiveCallback != null) {
                ReceiveCallback.accept(received);
            }
            byte[] array = new byte[received.length + m_nLenth];
            System.arraycopy(m_btAryBuffer, 0, array, 0, m_nLenth);
            System.arraycopy(received, 0, array, m_nLenth, received.length);
            int num = 0;
            int num2 = 0;
            for (int i = 0; i < array.length; i++) {
                if (array.length <= i + 1) {
                    continue;
                }
                if ((array[i] & 0xFF) == 160) {
                    int num3 = array[i + 1] & 0xFF;
                    if (i + 1 + num3 < array.length) {
                        byte[] frame = Arrays.copyOfRange(array, i, i + num3 + 2);
                        MessageTran msgTran = new MessageTran(frame);
                        if (AnalyCallback != null) {
                            AnalyCallback.accept(msgTran);
                        }
                        i += 1 + num3;
                        num = i + 1;
                    } else {
                        i += 1 + num3;
                    }
                } else {
                    num2 = i;
                }
            }
            if (num < num2) {
                num = num2 + 1;
            }
            if (num < array.length) {
                m_nLenth = array.length - num;
                Arrays.fill(m_btAryBuffer, (byte) 0);
                System.arraycopy(array, num, m_btAryBuffer, 0, array.length - num);
            } else {
                m_nLenth = 0;
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public int SendMessage(byte[] data) {
        if (m_nType == 0) {
            if (iSerialPort == null || !iSerialPort.PortIsOpen()) {
                return -1;
            }
            iSerialPort.sendbyte(data, 0, data.length);
            if (SendCallback != null) {
                SendCallback.accept(data);
            }
            return 0;
        }
        if (m_nType == 1) {
            if (!italker.IsConnect()) {
                return -1;
            }
            if (italker.SendMessage(data)) {
                if (SendCallback != null) {
                    SendCallback.accept(data);
                }
                return 0;
            }
        }
        return -1;
    }

    private int send(int readId, int cmd) {
        return SendMessage(new MessageTran((byte) readId, (byte) cmd).AryTranData());
    }

    private int send(int readId, int cmd, byte... data) {
        return SendMessage(new MessageTran((byte) readId, (byte) cmd, data).AryTranData());
    }

    public byte CheckValue(byte[] data) {
        return new MessageTran().CheckSum(data, 0, data.length);
    }

    public int Reset(int readId) {
        return send(readId, 112);
    }

    public int SetUartBaudrate(int readId, int indexBaudrate) {
        return send(readId, 113, (byte) indexBaudrate);
    }

    public int GetFirmwareVersion(int readId) {
        return send(readId, 114);
    }

    public int SetReaderAddress(int readId, int newReadId) {
        return send(readId, 115, (byte) newReadId);
    }

    public int SetWorkAntenna(int readId, int antenna) {
        return send(readId, 116, (byte) antenna);
    }

    public int GetWorkAntenna(int readId) {
        return send(readId, 117);
    }

    public int SetOutputPower(int readId, byte[] power) {
        return send(readId, 118, power);
    }

    public int GetOutputPower(int readId) {
        return send(readId, 151);
    }

    public int GetOutputPowerFour(int readId) {
        return send(readId, 119);
    }

    public int MeasureReturnLoss(int readId, int frequency) {
        return send(readId, 126, (byte) frequency);
    }

    public int SetFrequencyRegion(int readId, int region, int start, int end) {
        return send(readId, 120, (byte) region, (byte) start, (byte) end);
    }

    public int SetUserDefineFrequency(int readId, int startFreq, int interval, int quantity) {
        return send(readId, 120, (byte) 4, (byte) interval, (byte) quantity, (byte) (startFreq >> 16), (byte) (startFreq >> 8), (byte) startFreq);
    }

    public int GetFrequencyRegion(int readId) {
        return send(readId, 121);
    }

    public int SetBeeperMode(int readId, int mode) {
        return send(readId, 122, (byte) mode);
    }

    public int GetReaderTemperature(int readId) {
        return send(readId, 123);
    }

    public int GetAntImpedanceMatch(int readId, int frequency) {
        return send(readId, 126, (byte) frequency);
    }

    public int SetDrmMode(int readId, int mode) {
        return send(readId, 124, (byte) mode);
    }

    public int GetDrmMode(int readId) {
        return send(readId, 125);
    }

    public int ReadGpioValue(int readId) {
        return send(readId, 96);
    }

    public int WriteGpioValue(int readId, int gpio, int value) {
        return send(readId, 97, (byte) gpio, (byte) value);
    }

    public int SetAntDetector(int readId, int status) {
        return send(readId, 98, (byte) status);
    }

    public int GetAntDetector(int readId) {
        return send(readId, 99);
    }

    public int GetMonzaStatus(int readId) {
        return send(readId, 142);
    }

    public int SetMonzaStatus(int readId, int status) {
        return send(readId, 141, (byte) status);
    }

    public int SetRadioProfile(int readId, int profile) {
        return send(readId, 105, (byte) profile);
    }

    public int GetRadioProfile(int readId) {
        return send(readId, 106);
    }

    public int GetReaderIdentifier(int readId) {
        return send(readId, 104);
    }

    public int SetReaderIdentifier(int readId, byte[] identifier) {
        return send(readId, 103, identifier);
    }

    public int Inventory(int readId, int round) {
        return send(readId, 128, (byte) round);
    }

    public int CustomizedInventory(int readId, int session, int target, int round) {
        return send(readId, 139, (byte) session, (byte) target, (byte) round);
    }

    /** Lecture d'une zone mémoire du badge (banque, adresse et nombre de mots, mot de passe facultatif). */
    public int ReadTag(int readId, int memBank, int wordAdd, int wordCnt, byte[] password) {
        byte[] a = new byte[3 + (password == null ? 0 : password.length)];
        a[0] = (byte) memBank;
        a[1] = (byte) wordAdd;
        a[2] = (byte) wordCnt;
        if (password != null) {
            System.arraycopy(password, 0, a, 3, password.length);
        }
        return send(readId, 129, a);
    }

    public int WriteTag(int readId, byte[] password, int memBank, int wordAdd, int wordCnt, byte[] data) {
        byte[] a = new byte[data.length + 7];
        System.arraycopy(password, 0, a, 0, 4);
        a[4] = (byte) memBank;
        a[5] = (byte) wordAdd;
        a[6] = (byte) wordCnt;
        System.arraycopy(data, 0, a, 7, data.length);
        return send(readId, 130, a);
    }

    public int LockTag(int readId, byte[] password, int memBank, int lockType) {
        byte[] a = new byte[6];
        System.arraycopy(password, 0, a, 0, 4);
        a[4] = (byte) memBank;
        a[5] = (byte) lockType;
        return send(readId, 131, a);
    }

    public int KillTag(int readId, byte[] password) {
        return send(readId, 132, Arrays.copyOf(password, 4));
    }

    public int SetAccessEpcMatch(int readId, int mode, int epcLen, byte[] epc) {
        byte[] a = new byte[epcLen + 2];
        a[0] = (byte) mode;
        a[1] = (byte) epcLen;
        System.arraycopy(epc, 0, a, 2, epc.length);
        return send(readId, 133, a);
    }

    public int CancelAccessEpcMatch(int readId, int mode) {
        return send(readId, 133, (byte) mode);
    }

    public int GetAccessEpcMatch(int readId) {
        return send(readId, 134);
    }

    public int InventoryReal(int readId, int round) {
        return send(readId, 137, (byte) round);
    }

    public int FastSwitchInventory(int readId, byte[] data) {
        return send(readId, 138, data);
    }

    public int GetInventoryBuffer(int readId) {
        return send(readId, 144);
    }

    public int GetAndResetInventoryBuffer(int readId) {
        return send(readId, 145);
    }

    public int GetInventoryBufferTagCount(int readId) {
        return send(readId, 146);
    }

    public int ResetInventoryBuffer(int readId) {
        return send(readId, 147);
    }

    public int SetBufferDataFrameInterval(int readId, int interval) {
        return send(readId, 148, (byte) interval);
    }

    public int GetBufferDataFrameInterval(int readId) {
        return send(readId, 149);
    }

    public int InventoryISO18000(int readId) {
        return send(readId, 176);
    }

    public int ReadTagISO18000(int readId, byte[] uid, int wordAdd, int wordCnt) {
        byte[] a = Arrays.copyOf(uid, uid.length + 2);
        a[uid.length] = (byte) wordAdd;
        a[uid.length + 1] = (byte) wordCnt;
        return send(readId, 177, a);
    }

    public int WriteTagISO18000(int readId, byte[] uid, int wordAdd, int wordCnt, byte[] buffer) {
        byte[] a = new byte[uid.length + 2 + buffer.length];
        System.arraycopy(uid, 0, a, 0, uid.length);
        a[uid.length] = (byte) wordAdd;
        a[uid.length + 1] = (byte) wordCnt;
        System.arraycopy(buffer, 0, a, uid.length + 2, buffer.length);
        return send(readId, 178, a);
    }

    public int LockTagISO18000(int readId, byte[] uid, int wordAdd) {
        byte[] a = Arrays.copyOf(uid, uid.length + 1);
        a[uid.length] = (byte) wordAdd;
        return send(readId, 179, a);
    }

    public int QueryTagISO18000(int readId, byte[] uid, int wordAdd) {
        byte[] a = Arrays.copyOf(uid, uid.length + 1);
        a[uid.length] = (byte) wordAdd;
        return send(readId, 180, a);
    }

    public int setTagMask(int readId, int maskNo, int target, int action, int memBank, int startAdd, int maskLen, byte[] maskValue) {
        byte[] a = new byte[7 + maskValue.length];
        a[0] = (byte) maskNo;
        a[1] = (byte) target;
        a[2] = (byte) action;
        a[3] = (byte) memBank;
        a[4] = (byte) startAdd;
        a[5] = (byte) maskLen;
        System.arraycopy(maskValue, 0, a, 6, maskValue.length);
        a[a.length - 1] = 0;
        return send(readId, 152, a);
    }

    public int getTagMask(int readId) {
        return send(readId, 152, (byte) 32);
    }

    public int clearTagMask(int readId, int maskNo) {
        return send(readId, 152, (byte) maskNo);
    }
}

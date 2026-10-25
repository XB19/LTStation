package com.ltstation.center.sw.hw.rfid;

/** RFIDReader.MessageTran : trame du lecteur RFID (A0, longueur, adresse, commande, données, somme de contrôle). */
public class MessageTran {

    private byte btPacketType;
    private byte btDataLen;
    private byte btReadId;
    private byte btCmd;
    private byte[] btAryData;
    private byte btCheck;
    private byte[] btAryTranData;

    public byte[] AryTranData() {
        return btAryTranData;
    }

    public byte[] AryData() {
        return btAryData;
    }

    public int ReadId() {
        return btReadId & 0xFF;
    }

    public int Cmd() {
        return btCmd & 0xFF;
    }

    public int PacketType() {
        return btPacketType & 0xFF;
    }

    public MessageTran() {
    }

    public MessageTran(byte readId, byte cmd, byte[] data) {
        int num = data.length;
        btPacketType = (byte) 160;
        btDataLen = (byte) (num + 3);
        btReadId = readId;
        btCmd = cmd;
        btAryData = data.clone();
        btAryTranData = new byte[num + 5];
        btAryTranData[0] = btPacketType;
        btAryTranData[1] = btDataLen;
        btAryTranData[2] = btReadId;
        btAryTranData[3] = btCmd;
        System.arraycopy(btAryData, 0, btAryTranData, 4, num);
        btCheck = CheckSum(btAryTranData, 0, num + 4);
        btAryTranData[num + 4] = btCheck;
    }

    public MessageTran(byte readId, byte cmd) {
        btPacketType = (byte) 160;
        btDataLen = 3;
        btReadId = readId;
        btCmd = cmd;
        btAryTranData = new byte[] {btPacketType, btDataLen, btReadId, btCmd, 0};
        btCheck = CheckSum(btAryTranData, 0, 4);
        btAryTranData[4] = btCheck;
    }

    /** Trame reçue : décodée si la somme de contrôle est juste. */
    public MessageTran(byte[] tran) {
        int num = tran.length;
        btAryTranData = tran.clone();
        if (CheckSum(btAryTranData, 0, num - 1) != tran[num - 1]) {
            return;
        }
        btPacketType = tran[0];
        btDataLen = tran[1];
        btReadId = tran[2];
        btCmd = tran[3];
        btCheck = tran[num - 1];
        if (num > 5) {
            btAryData = new byte[num - 5];
            System.arraycopy(tran, 4, btAryData, 0, num - 5);
        }
    }

    /** Complément à deux de la somme des octets. */
    public byte CheckSum(byte[] buffer, int start, int len) {
        int b = 0;
        for (int i = start; i < start + len; i++) {
            b = (b + buffer[i]) & 0xFF;
        }
        return (byte) ((~b + 1) & 0xFF);
    }
}

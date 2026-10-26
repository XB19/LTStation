package com.ltstation.center.sw.normal;

import com.ltstation.center.sw.normal.WirelessCmd.CmdList;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;

/** Wireless.IBoxClientParam : boîtier sans fil connu (pistolets, cuves, lecteurs RFID, files de commandes). */
public class IBoxClientParam {

    public volatile BoxTcpServer.Session socketClient;
    public volatile Thread CommadnThread;
    public int ID;
    public int StationID;
    public int CompanyID;
    public String NoHead;
    public String IP;
    public int port;
    public volatile List<AllNozStatue> NozList = new CopyOnWriteArrayList<>();
    public volatile List<TankStatue> TankList = new CopyOnWriteArrayList<>();
    public volatile List<RFIDRunData> RFIDList = new CopyOnWriteArrayList<>();
    public float Longitude;
    public float Latitude;
    public int TimeZone;
    public volatile int CurrentProgram;
    public volatile long ProgramDateTime;
    public final List<CmdList> cmdRFID = new CopyOnWriteArrayList<>();
    public volatile List<CmdList> cmdAuth = new CopyOnWriteArrayList<>();
    public volatile Deque<CmdList> cmdNormal = new ConcurrentLinkedDeque<>();
    public volatile List<String> TankMac = new ArrayList<>();
    public volatile boolean Online;
    public volatile String cmdHead;
    public volatile boolean IsConnect;
    public String BoxVersion;
    public int VersionIsCheck;
    public int TankRun;
    public PLCParam pLCParam;
    public PriceBorad priceBorad;

    public void StartThread() {
        CommadnThread = new Thread(this::SendNormalCommand, "BoxClientSend");
        CommadnThread.setDaemon(true);
        CommadnThread.start();
    }

    /** Envoi des commandes en attente (une toutes les 500 ms). */
    private void SendNormalCommand() {
        while (IsConnect) {
            try {
                if (NoHead != null) {
                    BaseInfo.log("SendNormalCommand:" + cmdNormal.size(), NoHead.toUpperCase());
                    if (NoHead.length() > 6 && !cmdNormal.isEmpty()) {
                        CmdList cmd = cmdNormal.poll();
                        if (cmd != null) {
                            byte[] outData = BaseInfo.CreateCmd(cmdHead, TimeZone, cmd, this);
                            BaseInfo.log("cmdGet:" + (outData != null ? "True" : "False"), NoHead.toUpperCase());
                            if (outData == null) {
                                throw new IllegalStateException("Create Command Error");
                            }
                            if (socketClient == null) {
                                BoxTcpServer.Session client = BoxTcpServer.TryGetClient(NoHead.toUpperCase());
                                BaseInfo.log("sdeal:" + (client != null ? "True" : "False"), NoHead.toUpperCase());
                                if (client != null) {
                                    socketClient = client;
                                }
                            }
                            if (socketClient != null) {
                                socketClient.Send(outData);
                            }
                        }
                    }
                }
            } catch (RuntimeException ex) {
                BaseInfo.log("Command Create Error" + ex.getMessage(), NoHead == null ? "Main" : NoHead.toUpperCase());
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}

package com.ltstation.center.sw.hw;

import java.util.ArrayList;
import java.util.List;
import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import javax.smartcardio.TerminalFactory;

/**
 * SmartCard.Operate : lecteur de cartes à puce PC/SC (winscard), par javax.smartcardio. Sans
 * lecteur branché, toutes les opérations échouent proprement (false / statut d'erreur), comme
 * l'original dont les appels winscard renvoient un code d'erreur.
 */
public class Operate {

    /** Statut renvoyé par ScardCommand quand aucune carte n'est connectée (SCARD_E_INVALID_HANDLE). */
    public static final String NOT_CONNECTED = "6";

    private Card card;
    private CardChannel channel;

    static {
        // Réponses 61xx : GET RESPONSE automatique (comme Operate.ScardCommand)
        System.setProperty("sun.security.smartcardio.t0GetResponse", "true");
        System.setProperty("sun.security.smartcardio.t1GetResponse", "true");
    }

    private static List<CardTerminal> terminals() {
        try {
            return TerminalFactory.getDefault().terminals().list();
        } catch (CardException | RuntimeException e) {
            return List.of();
        }
    }

    public boolean EstablishContext() {
        try {
            TerminalFactory.getDefault().terminals().list();
            return true;
        } catch (CardException | RuntimeException e) {
            return false;
        }
    }

    /** Noms des lecteurs PC/SC branchés ; null si le service de cartes à puce est indisponible. */
    public String[] GetReaders() {
        if (!EstablishContext()) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (CardTerminal t : terminals()) {
            names.add(t.getName());
        }
        return names.toArray(new String[0]);
    }

    /** Connexion à la carte posée sur le lecteur nommé (protocole T=0 ou T=1). */
    public boolean SCardConnect(String readerName) {
        SCardDisconnect();
        for (CardTerminal t : terminals()) {
            if (t.getName().equals(readerName)) {
                try {
                    card = t.connect("*");
                    channel = card.getBasicChannel();
                    return true;
                } catch (CardException | RuntimeException e) {
                    card = null;
                    channel = null;
                    return false;
                }
            }
        }
        return false;
    }

    public boolean SCardReconnect() {
        if (card == null) {
            return false;
        }
        try {
            String name = null;
            for (CardTerminal t : terminals()) {
                if (t.isCardPresent()) {
                    name = t.getName();
                    break;
                }
            }
            return name != null && SCardConnect(name);
        } catch (CardException e) {
            return false;
        }
    }

    public boolean SCardDisconnect() {
        if (card == null) {
            return false;
        }
        try {
            card.disconnect(true);
            return true;
        } catch (CardException | RuntimeException e) {
            return false;
        } finally {
            card = null;
            channel = null;
        }
    }

    public boolean connected() {
        return channel != null;
    }

    /** ATR de la carte. */
    public boolean SCardStatus(String reader, String[] recvData) {
        recvData[0] = "";
        if (card == null) {
            return false;
        }
        recvData[0] = SerialClass.byteToHexStr(card.getATR().getBytes());
        return true;
    }

    /**
     * Envoie une commande APDU (hexadécimal) ; renvoie le mot d'état SW1SW2 (ex. "9000") et place
     * les données de la réponse dans recvData[0].
     */
    public String ScardCommand(String apdu, String[] recvData) {
        recvData[0] = "";
        if (channel == null) {
            return NOT_CONNECTED;
        }
        apdu = apdu.replace(" ", "").replace("　", "");
        try {
            ResponseAPDU r = channel.transmit(new CommandAPDU(SerialClass.strToToHexByte(apdu)));
            recvData[0] = SerialClass.byteToHexStr(r.getData());
            return String.format("%02X%02X", r.getSW1(), r.getSW2());
        } catch (CardException | RuntimeException e) {
            return NOT_CONNECTED;
        }
    }
}

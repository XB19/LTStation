package com.ltstation.center.sw.core;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** MultiLanguage du logiciel Station : textes lus dans station/Language/&lt;langue&gt;.xml (fr-FR par défaut). */
public final class MultiLanguage {

    private static final Map<String, String> DIC = new ConcurrentHashMap<>();
    private static volatile String current = "";

    private MultiLanguage() {
    }

    public static synchronized void languageChange(String lang) {
        if (lang.equals(current) && !DIC.isEmpty()) {
            return;
        }
        DIC.clear();
        try (InputStream in = MultiLanguage.class.getClassLoader().getResourceAsStream("station/Language/" + lang + ".xml")) {
            if (in == null) {
                return;
            }
            Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in).getDocumentElement();
            NodeList nodes = root.getChildNodes();
            for (int i = 0; i < nodes.getLength(); i++) {
                Node n = nodes.item(i);
                if (n.getNodeType() == Node.ELEMENT_NODE) {
                    DIC.put(n.getNodeName(), n.getTextContent());
                }
            }
            current = lang;
        } catch (Exception ignored) {
            // comme l'original : langue absente = textes vides
        }
    }

    /** GetText : texte de la langue courante, "" s'il est absent. */
    public static String getText(String name) {
        if (DIC.isEmpty()) {
            languageChange("fr-FR");
        }
        return DIC.getOrDefault(name, "");
    }

    public static Map<String, String> all() {
        if (DIC.isEmpty()) {
            languageChange("fr-FR");
        }
        return Map.copyOf(DIC);
    }
}

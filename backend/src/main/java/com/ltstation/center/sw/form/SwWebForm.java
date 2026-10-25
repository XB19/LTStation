package com.ltstation.center.sw.form;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fenêtre Formium d'origine (navigateur intégré) : affiche une page web du logiciel
 * (web/login/login.html, web/tank/tank.html…), lui envoie des appels JavaScript
 * (ExecuteJavascript) et reçoit ses appels à l'objet « my » (GlobalObject.AddObject("my")).
 */
public abstract class SwWebForm extends SwForm {

    /** Fonctions de l'objet « my » : nom JavaScript → gestionnaire (argument dans e["arg"]). */
    final Map<String, String> jsFunctions = new LinkedHashMap<>();

    /** Page d'origine, relative à station.buesky.local (ex. "web/login/login.html"). */
    protected abstract String Url();

    /** ExecuteJavascript(script) : exécuté dans la page une fois chargée. */
    protected void ExecuteJavascript(String script) {
        messages.add(Map.of("js", script));
    }

    /** jSObject.AddFunction(nom).Execute += gestionnaire */
    protected void JsFunction(String name, String handler) {
        jsFunctions.put(name, handler);
    }

    /** Premier argument chaîne de l'appel JavaScript (args.Arguments.FirstOrDefault(p => p.IsString)). */
    protected String JsArg() {
        return e.s("arg");
    }

    /** LoadHandler.OnLoadEnd : la page est chargée. */
    public void LoadHandler_OnLoadEnd() {
    }
}

package com.ltstation.center.sw.form;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.security.PublicApi;
import com.ltstation.center.sw.web.SwBootController;
import com.ltstation.center.sw.web.SwException;
import jakarta.servlet.http.HttpSession;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fenêtres pilotées par le serveur : ouverture (constructeur + Load), événements (Click,
 * SelectedIndexChanged, CellClick, Tick…), fermeture. Chaque réponse contient uniquement les
 * contrôles et tableaux modifiés, les messages à afficher et l'éventuelle question en attente.
 */
@RestController
@RequestMapping("/api/sw/form")
@PublicApi
public class SwFormController {

    private static final Logger LOG = LoggerFactory.getLogger(SwFormController.class);
    private static final String SESSION_FORMS = "SW_FORMS";
    private final FormRegistry registry;

    public SwFormController(FormRegistry registry) {
        this.registry = registry;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, SwForm> forms(HttpSession session) {
        Object o = session.getAttribute(SESSION_FORMS);
        if (o == null) {
            o = new ConcurrentHashMap<String, SwForm>();
            session.setAttribute(SESSION_FORMS, o);
        }
        return (Map<String, SwForm>) o;
    }

    private static SwForm form(HttpSession session, String id) {
        SwForm f = forms(session).get(id);
        if (f == null) {
            throw new SwException("Fenêtre fermée", 410);
        }
        return f;
    }

    /** Fenêtres portées (les autres s'ouvrent en affichage seul). */
    @GetMapping("/list")
    public Map<String, Object> list() {
        return Map.of("forms", registry.names());
    }

    /** new Form(args) puis Form_Load. */
    @PostMapping("/open")
    @SuppressWarnings("unchecked")
    public Map<String, Object> open(@RequestBody Map<String, Object> body, HttpSession session) {
        String name = String.valueOf(body.getOrDefault("form", ""));
        // Fenêtres de l'assistant d'installation : ouvertes sans connexion tant que la station n'est pas enregistrée
        if (!registry.anonymous(name) || SwBootController.registered()) {
            SwBootController.requireLogin(session);
        }
        SwForm f = registry.create(name);
        f.id = UUID.randomUUID().toString();
        f.formName = name;
        f.layout = Layouts.get(name);
        if (body.get("args") instanceof Map<?, ?> a) {
            f.args.putAll((Map<String, Object>) a);
        }
        forms(session).put(f.id, f);
        synchronized (f) {
            Map<String, Object> out = run(f, "init", body, true);
            if (out.get("suspend") == null && f.closeResult == null) {
                String load = loadHandler(f);
                if (load != null) {
                    out = run(f, load, body, true);
                }
            }
            out.put("id", f.id);
            // Question pendant l'ouverture : le client rouvre la fenêtre avec la réponse
            if (f.closeResult != null || out.get("suspend") != null) {
                forms(session).remove(f.id);
            }
            return out;
        }
    }

    /** Gestionnaire du Load : déclaré dans la mise en page ou nommé <Form>_Load. */
    private static String loadHandler(SwForm f) {
        Map<String, String> ev = f.layout.events().get("$this");
        if (ev != null && ev.get("Load") != null) {
            return ev.get("Load");
        }
        String shortName = f.formName.contains(".") ? f.formName.substring(f.formName.lastIndexOf('.') + 1) : f.formName;
        String def = shortName + "_Load";
        return method(f, def) != null ? def : null;
    }

    /** Événement : {handler, sender, values, grids, e, answers}. */
    @PostMapping("/{id}/event")
    public Map<String, Object> event(@PathVariable String id, @RequestBody Map<String, Object> body, HttpSession session) {
        SwForm f = form(session, id);
        synchronized (f) {
            Map<String, Object> out = run(f, String.valueOf(body.getOrDefault("handler", "")), body, false);
            if (f.closeResult != null) {
                forms(session).remove(id);
            }
            return out;
        }
    }

    /** Fermeture par la croix : FormClosing (e.Cancel possible) puis FormClosed. */
    @PostMapping("/{id}/close")
    public Map<String, Object> close(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body,
                                     HttpSession session) {
        SwForm f = forms(session).get(id);
        if (f == null) {
            return Map.of("close", "Cancel");
        }
        synchronized (f) {
            Map<String, String> ev = f.layout.events().getOrDefault("$this", Map.of());
            Map<String, Object> b = body == null ? Map.of() : body;
            Map<String, Object> out = null;
            for (String k : List.of("FormClosing", "FormClosed")) {
                if (ev.get(k) != null) {
                    out = run(f, ev.get(k), b, false);
                    if (out.get("suspend") != null || f.e.b("Cancel")) {
                        out.remove("close");
                        return out;
                    }
                }
            }
            forms(session).remove(id);
            if (out == null) {
                out = new LinkedHashMap<>();
            }
            out.put("close", f.closeResult == null ? "Cancel" : f.closeResult);
            out.put("result", f.result);
            return out;
        }
    }

    private static Method method(SwForm f, String name) {
        for (Class<?> c = f.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static void applyClient(SwForm f, Map<String, Object> body) {
        if (body.get("values") instanceof Map<?, ?> vals) {
            vals.forEach((k, v) -> {
                if (v instanceof Map<?, ?> props) {
                    Ctl c = f.c(String.valueOf(k));
                    props.forEach((pk, pv) -> c.fromClient(String.valueOf(pk), pv));
                }
            });
        }
        if (body.get("grids") instanceof Map<?, ?> gs) {
            gs.forEach((k, v) -> f.g(String.valueOf(k)).setCurrent(v instanceof Number n ? n.intValue() : -1));
        }
        f.answers = body.get("answers") instanceof Map<?, ?> a ? new LinkedHashMap<>((Map<String, Object>) a) : new LinkedHashMap<>();
        f.e = body.get("e") instanceof Map<?, ?> e ? new Row((Map<String, ?>) e) : new Row();
        f.sender = String.valueOf(body.getOrDefault("sender", ""));
    }

    /**
     * Exécute un gestionnaire. En cas de question (Confirm / ShowDialog), les messages déjà
     * produits sont abandonnés : le client rejoue l'événement complet avec la réponse.
     */
    private static Map<String, Object> run(SwForm f, String handler, Map<String, Object> body, boolean full) {
        applyClient(f, body);
        f.handler = handler;
        f.suspendCounter = 0;
        f.messages.clear();
        Map<String, Object> suspend = null;
        String error = null;
        try {
            if ("init".equals(handler)) {
                f.init();
            } else if ("$continue".equals(handler)) {
                f.resume();
            } else {
                Method m = method(f, handler);
                if (m != null) {
                    m.invoke(f);
                } else if (!(f instanceof FormRegistry.LayoutOnlyForm)) {
                    // Gestionnaire vide dans l'original (ex. Form_Load sans contenu) : rien à faire
                    LOG.debug("{}.{} : gestionnaire sans contenu", f.formName, handler);
                }
            }
        } catch (InvocationTargetException ite) {
            Throwable t = ite.getCause();
            if (t instanceof SwForm.Suspend s) {
                suspend = s.request;
                f.messages.clear();
            } else if (t instanceof SwException se) {
                error = se.getMessage();
            } else {
                error = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                LOG.error("{}.{}", f.formName, handler, t);
            }
        } catch (SwForm.Suspend s) {
            suspend = s.request;
            f.messages.clear();
        } catch (SwException se) {
            error = se.getMessage();
        } catch (ReflectiveOperationException ex) {
            error = ex.getMessage();
        }
        return state(f, full, suspend, error);
    }

    private static Map<String, Object> state(SwForm f, boolean full, Map<String, Object> suspend, String error) {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> ctls = new LinkedHashMap<>();
        f.ctls.forEach((k, c) -> {
            if (full || c.dirty()) {
                ctls.put(k, c.props());
                c.clean();
            }
        });
        Map<String, Object> grids = new LinkedHashMap<>();
        f.grids.forEach((k, g) -> {
            if (full || g.dirty()) {
                grids.put(k, g.toClient());
                g.clean();
            }
        });
        out.put("controls", ctls);
        out.put("grids", grids);
        List<Map<String, Object>> msgs = new ArrayList<>(f.messages);
        if (error != null) {
            msgs.add(Map.of("text", error, "error", true));
        }
        out.put("messages", msgs);
        f.messages.clear();
        if (!f.timers.isEmpty()) {
            out.put("timers", new LinkedHashMap<>(f.timers));
            f.timers.clear();
        }
        if (f.title != null) {
            out.put("title", f.title);
        }
        if (suspend != null) {
            out.put("suspend", suspend);
        }
        if (f.closeResult != null) {
            out.put("close", f.closeResult);
            out.put("result", f.result);
        }
        return out;
    }
}

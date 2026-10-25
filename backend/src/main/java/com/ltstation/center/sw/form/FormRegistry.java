package com.ltstation.center.sw.form;

import java.util.Map;
import java.util.TreeMap;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

/** Fenêtres portées : classes {@link SwForm} annotées {@link SwWindow}, trouvées dans le package sw. */
@Component
public class FormRegistry {

    private final Map<String, Class<? extends SwForm>> forms = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    @SuppressWarnings("unchecked")
    public FormRegistry() {
        var scan = new ClassPathScanningCandidateComponentProvider(false);
        scan.addIncludeFilter(new AnnotationTypeFilter(SwWindow.class));
        for (BeanDefinition d : scan.findCandidateComponents("com.ltstation.center.sw")) {
            try {
                Class<?> c = Class.forName(d.getBeanClassName());
                if (SwForm.class.isAssignableFrom(c)) {
                    forms.put(c.getAnnotation(SwWindow.class).value(), (Class<? extends SwForm>) c);
                }
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    public boolean has(String name) {
        return forms.containsKey(name);
    }

    public boolean anonymous(String name) {
        Class<? extends SwForm> c = forms.get(name);
        return c != null && c.getAnnotation(SwWindow.class).anonymous();
    }

    public java.util.Set<String> names() {
        return forms.keySet();
    }

    /** Nouvelle instance ; une fenêtre pas encore portée s'affiche avec sa mise en page seule. */
    public SwForm create(String name) {
        Class<? extends SwForm> c = forms.get(name);
        if (c == null) {
            return new LayoutOnlyForm();
        }
        try {
            return c.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Fenêtre " + name + " : " + e.getMessage(), e);
        }
    }

    /** Fenêtre sans logique côté serveur (affichage de la mise en page d'origine). */
    static final class LayoutOnlyForm extends SwForm {
    }
}

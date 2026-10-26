package com.ltstation.center.sw.forms;

import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/**
 * ProBar : fenêtre d'attente affichée pendant un traitement long. Les fenêtres portées exécutent ce
 * traitement dans la requête même (l'interface affiche alors son indicateur d'occupation).
 */
@SwWindow("ProBar")
public class ProBar extends SwForm {

    public void ProClose() {
        try {
            Close();
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}

package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** StationMessage : informations de la station (NormalStation) ; l'enregistrement est vide dans l'original. */
@SwWindow("NormalForm.StationMessage")
public class StationMessage extends SwForm {

    private int ID = -1;

    public void SaveButton_Click() {
        // comme l'original : les valeurs sont lues sans être enregistrées
    }

    public void StationMessage_Load() {
        Row s = db().first("NormalStation");
        if (s != null) {
            ID = s.i("ID");
            c("StName").Text(s.str("StationName"));
            c("BackUpAddress").Text(s.str("BackUpAddress"));
            c("BackUpTime").Value(Conv.toDateTime(s.get("BackUpTime")));
            c("VoiceAddress").Text(s.str("VoiceAddress"));
            c("IsVoice").Checked(s.b("IsVoice"));
            c("ReportAddress").Text(s.str("ReportAddress"));
            c("BackLen").SelectedIndex(s.i("BackUpLength"));
        }
    }
}

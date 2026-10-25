package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.DataTable;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.ExportFile;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.util.ArrayList;
import java.util.List;

/** TankVolume : barème hauteur / volume d'une cuve (import Excel, enregistrement, envoi à la sonde). */
@SwWindow("TankVolume")
public class TankVolume extends SwForm {

    @Override
    protected void init() {
        if (Owner instanceof Station s) {
            s.TVolume = this;
        }
        Poll(1000);
    }

    public void ImportButton_Click() {
        OpenFileDialog("Excel(*.xls;*.xlsx;*.csv)|*.xls;*.xlsx;*.csv", path -> {
            try {
                DataTable dt = new ExportFile().ReadVolumeByExcel(path);
                List<Row> list = new ArrayList<>();
                for (int i = 0; i < dt.Rows.size(); i++) {
                    list.add(Row.of("Height", Conv.toDouble(dt.get(i, 0)), "Vol", Conv.toDouble(dt.get(i, 1))));
                }
                ShowData(list);
            } catch (RuntimeException ex) {
                MessageBox(ex.getMessage());
            }
        });
    }

    public void SaveBtn_Click() {
        int id = c("TankSelect").SelectedRow().i("ID");
        List<Row> list = new ArrayList<>();
        for (int i = 0; i < g("DisPlayView").RowCount(); i++) {
            list.add(Row.of("TankID", id, "Height", Conv.toDouble(g("DisPlayView").Cell(i, 0)), "Vol", Conv.toDouble(g("DisPlayView").Cell(i, 1))));
        }
        db().delete("TankVolConfig", "{TankID} = :t", p("t", id));
        db().insertAll("TankVolConfig", list);
    }

    public void DowloadBtn_Click() {
        try {
            c("ProgressMess").Text(c("ProgressMess").Text() + L("DownLoad") + "\n");
            int id = c("TankSelect").SelectedRow().i("ID");
            ((Station) Owner).DoloadVolumeTable(id);
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public void SetText(String str) {
        synchronized (this) {
            c("ProgressMess").Text(c("ProgressMess").Text() + str);
        }
    }

    /** Progression du téléchargement du barème (appelée par la sonde). */
    public void DowloadMessage(int i, int total) {
        SetText(i + "/" + total + "\n");
        if (i == total) {
            SetText(L("DownLoadComplete") + "\n");
        }
    }

    public void TankVolume_Load() {
        c("TankSelect").DataSource(db().list("tankConfig"), "ID", "ID");
    }

    public void TankSelect_SelectedIndexChanged() {
        Row tank = c("TankSelect").SelectedRow();
        if (tank == null) {
            return;
        }
        ShowData(db().list("TankVolConfig", "{TankID} = :t ORDER BY {Height}", p("t", tank.get("ID"))));
    }

    private void ShowData(List<Row> list) {
        try {
            g("DisPlayView").RowsClear();
            for (Row item : list) {
                g("DisPlayView").RowsAdd(item.get("Height"), item.get("Vol"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}

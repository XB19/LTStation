package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.normal.NormalFunction;
import java.util.List;

/** NozDelopy : ports d'un boîtier série (arguments CID, CIndex) ; ajout / modification / retrait de pistolets. */
@SwWindow("NormalForm.NozDelopy")
public class NozDelopy extends SwForm {

    public void GetNozData() {
        try {
            ShowData(db().list("Box_Set", "{Port_ID} = :p", p("p", argInt("CID"))));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void ShowData(List<Row> list) {
        try {
            g("NozView").RowsClear();
            for (Row item : list) {
                String parity = switch (item.i("Box_Parity")) {
                    case 0 -> "无校验";
                    case 1 -> "奇校验";
                    case 2 -> "偶校验";
                    default -> "";
                };
                String type = switch (item.i("Box_CommunicationTypeID")) {
                    case 0 -> "无";
                    case 1 -> "蓝天协议";
                    case 2 -> "蓝天通讯盒协议";
                    default -> "";
                };
                g("NozView").RowsAdd(item.get("ID"), item.get("Box_PortID"), item.get("Box_Baud"), item.get("Box_Stop"), parity, type,
                    item.get("Box_IntervalTime"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void Add_Click() {
        ShowDialog("NormalForm.NozAdd", r -> {
            if ("OK".equals(r.result())) {
                GetNozData();
            }
        }, "Index", argInt("CID"));
    }

    private int currentId() {
        return Conv.toInt(g("NozView").Cell(g("NozView").CurrentRowIndex(), 0));
    }

    public void Delete_Click() {
        try {
            int id = currentId();
            if (Confirm("是否删除")) {
                // comme l'original : l'identifiant du port (Box_Set) sert d'identifiant de pistolet
                Row noz = db().first("NormalNoz", "{ID} = :i", p("i", id));
                if (noz == null) {
                    throw new IllegalStateException("枪信息不存在");
                }
                noz.put("IsUsing", false);
                db().update("NormalNoz", noz);
                NormalFunction.MainFormMessage.accept(1029, noz.i("NozID"), 0);
                GetNozData();
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void NozDelopy_Load() {
        GetNozData();
    }

    public void NozView_CellDoubleClick() {
        try {
            int id = currentId();
            ShowDialog("NormalForm.NozUpdate", r -> {
                if ("OK".equals(r.result())) {
                    GetNozData();
                }
            }, "ID", id, "Index", argInt("CID"));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}

package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.math.BigDecimal;

/** NormalStationCreate : création de la station sur Center (code suivant, clé de la société) ou affichage de la station existante. */
@SwWindow("NormalForm.NormalStationCreate")
public class NormalStationCreate extends SwForm {

    public void NormalStationCreate_Load() {
        Row s = db().first("Stations");
        if (s == null) {
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetStationMax", "");
            Row max = Wire.parseRow(BaseFunction.DecryptByAES(r.Mess()));
            String code = max == null ? null : max.str("Code");
            int num = code != null && !code.isEmpty() && !code.equals("0") ? Integer.parseInt(code) + 1 : 1000001;
            c("StationCode").Text(String.valueOf(num));
        } else {
            c("StationCode").Text(s.str("Code"));
            c("StationName").Text(s.str("Name"));
            c("StationAddress").Text(s.str("Address"));
            c("StationPhone").Text(s.str("Tel"));
            c("Longitude").Text(s.str("Longitude"));
            c("Latitude").Text(s.str("Latitude"));
            c("EmailBox").Text(s.str("email"));
        }
    }

    private static BigDecimal dec(String text, String error) {
        if (text.isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(error);
        }
    }

    public void SaveButton_Click() {
        try {
            if (c("StationName").Text().isEmpty()) {
                throw new IllegalStateException(L("StationNameEmpty"));
            }
            BigDecimal lon = dec(c("Longitude").Text(), L("LongitudeError"));
            BigDecimal lat = dec(c("Latitude").Text(), L("LatitudeError"));
            if (c("Secretkey").Text().isEmpty()) {
                throw new IllegalStateException(L("ApplicationNumberEmpty"));
            }
            Row stations = SW.dto().make("Stations", "Code", c("StationCode").Text(), "Name", c("StationName").Text(), "Address", c("StationAddress").Text(),
                "Tel", c("StationPhone").Text(), "Longitude", lon, "Latitude", lat, "StationID", -1, "RegionCode", "00", "CityCode", "0000",
                "SuperCode", "00000000", "MeasureTime", "00", "SendMailServer", c("Secretkey").Text(), "email", c("EmailBox").Text());
            CustomerRespone r = BaseFunction.post("ServerDeal/CreateNormalStation", stations);
            if (!r.Result()) {
                throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
            }
            String[] array = BaseFunction.DecryptByAES(r.Mess()).split("\\|");
            stations.put("StationID", Integer.parseInt(array[1]));
            stations.put("SendMailServer", array[0]);
            try (var tx = db().begin()) {
                db().delete("Stations", null, null);
                db().insert("Stations", stations);
                tx.commit();
            }
            IniInfo.Code = stations.str("Code");
            IniInfo.StationID = stations.i("StationID");
            IniInfo.StationName = stations.str("Name");
            IniInfo.CompanyID = Conv.toInt(array[0]);
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void skinButton1_Click() {
        Close("Cancel");
    }

    public void Secretkey_MouseMove() {
        // toolTip1.SetToolTip(Secretkey, GetApplicationNumber) : l'aide est donnée par l'info-bulle du champ
    }
}

package com.ltstation.center.sw.cardmake;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.security.PublicApi;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Démarrage de CardMake (Program.cs) : poste reconnu par son adresse MAC, sinon fenêtre Active. */
@RestController
@RequestMapping("/api/sw/cardmake")
@PublicApi
public class SwCardMakeController {

    @GetMapping("/boot")
    public Map<String, Object> boot() {
        List<String> mac = BaseFunction.GetMacAddress();
        if (mac.isEmpty()) {
            return Map.of("error", "Station Get Error");
        }
        String text = BaseFunction.EncryptByAes(mac.get(0));
        CustomerRespone r = BaseFunction.post("ExtendUpdate/GetMacStationValid", Wire.json(BaseFunction.EncryptByAes(text)));
        if (r.Mess() == null) {
            return Map.of("error", "Center ne répond pas");
        }
        if (!r.Result()) {
            return Map.of("valid", false, "mac", text);
        }
        Row st = Wire.parseRow(BaseFunction.DecryptByAES(r.Mess()));
        CM.StationID = st.i("StationID");
        CM.StationCode = st.str("Code");
        CM.StationName = st.str("Name");
        CM.StationERPCode = st.str("Email2");
        // Clés de cartes : CardWrite les demande avec le code de la station
        if (IniInfo.Code == null || IniInfo.Code.isEmpty()) {
            IniInfo.Code = CM.StationCode;
        }
        return Map.of("valid", true);
    }
}

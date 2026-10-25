package com.ltstation.center.sw.web;

import com.ltstation.center.security.PublicApi;
import com.ltstation.center.sw.hw.CardWrite;
import com.ltstation.center.sw.hw.SerialClass;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** État du matériel du poste : lecteurs de cartes PC/SC et ports série détectés. */
@RestController
@RequestMapping("/api/sw/hw")
@PublicApi
public class SwHardwareController {

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        List<String> readers = CardWrite.readers();
        m.put("cardReaders", readers);
        m.put("cardReaderConnected", readers.contains(CardWrite.READER));
        String[] ports = SerialClass.serialsIsConnected();
        m.put("serialPorts", ports == null ? List.of() : List.of(ports));
        return m;
    }
}

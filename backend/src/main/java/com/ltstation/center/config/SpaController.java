package com.ltstation.center.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Renvoie index.html pour les routes du frontend React (build servi par Spring Boot). */
@Controller
public class SpaController {

    @GetMapping({"/", "/login", "/activation", "/main/**", "/gprs/**"})
    public String index() {
        return "forward:/index.html";
    }

    /** Logiciel Station (station.html). */
    @GetMapping({"/station", "/station/", "/station/**"})
    public String station() {
        return "forward:/station.html";
    }
}

package com.du.gis_project.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MapController {

    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }
}

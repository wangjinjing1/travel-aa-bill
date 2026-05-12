package com.travelbill.api.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebAppController {
    @GetMapping({"/", "/plans", "/register"})
    public String app() {
        return "forward:/index.html";
    }
}

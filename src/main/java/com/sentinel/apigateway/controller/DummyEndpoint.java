package com.sentinel.apigateway.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DummyEndpoint {

    @RequestMapping("/dummy")
    public ResponseEntity<Boolean> dummy() {
        return ResponseEntity.ok(true);
    }
}

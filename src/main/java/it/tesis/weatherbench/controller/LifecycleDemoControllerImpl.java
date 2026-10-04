package it.tesis.weatherbench.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.tesis.weatherbench.service.LifecycleDemoService;

@RestController
public class LifecycleDemoControllerImpl implements LifecycleDemoController {

    private final LifecycleDemoService lifecycleDemoService;

    public LifecycleDemoControllerImpl(LifecycleDemoService lifecycleDemoService) {
        this.lifecycleDemoService = lifecycleDemoService;
    }

    @Override
    public ResponseEntity<Map<String, Object>> jpaPersistenceContext() {
        return ResponseEntity.ok(lifecycleDemoService.jpaPersistenceContextDemo());
    }

    @Override
    public ResponseEntity<Map<String, Object>> mongoStateless() {
        return ResponseEntity.ok(lifecycleDemoService.mongoStatelessDemo());
    }
}

package com.eai.trigger.manual;

import com.eai.domain.TriggerType;
import com.eai.execution.ExecutionEngine;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/interfaces")
public class ManualRunController {

    private final ExecutionEngine engine;

    public ManualRunController(ExecutionEngine engine) {
        this.engine = engine;
    }

    @PostMapping("/{id}/run")
    public ResponseEntity<Map<String, String>> run(@PathVariable String id,
                                                    @RequestBody(required = false) Map<String, Object> meta) {
        String runId = engine.start(id, TriggerType.MANUAL, meta == null ? Map.of() : meta);
        return ResponseEntity.accepted().body(Map.of("runId", runId));
    }
}

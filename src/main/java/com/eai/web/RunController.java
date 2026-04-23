package com.eai.web;

import com.eai.domain.ExecutionLogRepository;
import com.eai.domain.ExecutionRunRepository;
import com.eai.domain.InterfaceDefRepository;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/runs")
public class RunController {

    private final ExecutionRunRepository runs;
    private final ExecutionLogRepository logs;
    private final InterfaceDefRepository interfaces;

    public RunController(ExecutionRunRepository runs,
                         ExecutionLogRepository logs,
                         InterfaceDefRepository interfaces) {
        this.runs = runs;
        this.logs = logs;
        this.interfaces = interfaces;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("runs", runs.findTop100ByOrderByStartedAtDesc());
        model.addAttribute("interfaces", interfaces.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(
                        d -> d.getId(), d -> d.getName(), (a, b) -> a)));
        return "runs/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable String id, Model model) {
        var run = runs.findById(id).orElseThrow();
        model.addAttribute("run", run);
        model.addAttribute("logs", logs.findByRunIdOrderBySeqAsc(id));
        model.addAttribute("interfaceName", interfaces.findById(run.getInterfaceId())
                .map(d -> d.getName()).orElse(run.getInterfaceId()));
        return "runs/detail";
    }
}

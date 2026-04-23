package com.eai.web;

import com.eai.catalog.CatalogService;
import com.eai.domain.*;
import com.eai.execution.ExecutionEngine;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/interfaces")
public class InterfaceController {

    private final CatalogService catalog;
    private final ExecutionEngine engine;
    private final ObjectMapper mapper;

    public InterfaceController(CatalogService catalog, ExecutionEngine engine, ObjectMapper mapper) {
        this.catalog = catalog;
        this.engine = engine;
        this.mapper = mapper;
    }

    @GetMapping
    public String list(Model model) {
        var ifaces = catalog.listInterfaces();
        var adapters = catalog.listAdapters();
        var adaptersById = adapters.stream()
                .collect(java.util.stream.Collectors.toMap(AdapterConfigEntity::getId, a -> a, (a, b) -> a));
        // Build per-interface source/target system info for the list page
        java.util.Map<String, String[]> endpointSummary = new java.util.HashMap<>();
        for (InterfaceDef d : ifaces) {
            var vOpt = catalog.latestVersion(d.getId());
            if (vOpt.isEmpty()) continue;
            var ver = vOpt.get();
            endpointSummary.put(d.getId(), new String[]{
                    describeEndpoint(adaptersById.get(ver.getSourceAdapterId())),
                    describeEndpoint(adaptersById.get(ver.getTargetAdapterId()))
            });
        }
        model.addAttribute("interfaces", ifaces);
        model.addAttribute("adapters", adapters);
        model.addAttribute("endpointSummary", endpointSummary);
        return "interfaces/list";
    }

    private String describeEndpoint(AdapterConfigEntity a) {
        if (a == null) return "—";
        String sys = a.getSystemCode() == null ? "(unlinked)" : a.getSystemCode();
        return sys + " · " + a.getName();
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        InterfaceVersion v = new InterfaceVersion();
        populateForm(model, new InterfaceDef(), v);
        return "interfaces/edit";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable String id, Model model) {
        InterfaceDef def = catalog.findInterface(id).orElseThrow();
        InterfaceVersion v = catalog.latestVersion(id).orElse(new InterfaceVersion());
        populateForm(model, def, v);
        return "interfaces/edit";
    }

    private void populateForm(Model model, InterfaceDef def, InterfaceVersion v) {
        var adapters = catalog.listAdapters();
        model.addAttribute("iface", def);
        model.addAttribute("version", v);
        model.addAttribute("adapters", adapters);
        model.addAttribute("systems", catalog.listSystems());
        model.addAttribute("triggerTypes", TriggerType.values());
        // pre-selected system for the source/target adapter (if any)
        model.addAttribute("sourceSystemCode", systemCodeOfAdapter(adapters, v.getSourceAdapterId()));
        model.addAttribute("targetSystemCode", systemCodeOfAdapter(adapters, v.getTargetAdapterId()));
    }

    private String systemCodeOfAdapter(java.util.List<AdapterConfigEntity> adapters, String adapterId) {
        if (adapterId == null) return null;
        return adapters.stream()
                .filter(a -> adapterId.equals(a.getId()))
                .map(AdapterConfigEntity::getSystemCode)
                .findFirst().orElse(null);
    }

    @PostMapping("/save")
    public String save(@RequestParam(required = false) String id,
                       @RequestParam String name,
                       @RequestParam(required = false) String description,
                       @RequestParam(defaultValue = "true") boolean active,
                       @RequestParam String sourceAdapterId,
                       @RequestParam String targetAdapterId,
                       @RequestParam(required = false) String mappingRulesJson,
                       @RequestParam(required = false) String triggerConfigJson,
                       RedirectAttributes ra) {
        InterfaceDef def = (id == null || id.isBlank())
                ? new InterfaceDef()
                : catalog.findInterface(id).orElse(new InterfaceDef());
        def.setName(name);
        def.setDescription(description);
        def.setActive(active);

        InterfaceVersion v = new InterfaceVersion();
        v.setSourceAdapterId(sourceAdapterId);
        v.setTargetAdapterId(targetAdapterId);
        v.setMappingRulesJson(mappingRulesJson == null || mappingRulesJson.isBlank() ? "[]" : mappingRulesJson);
        v.setTriggerConfigJson(triggerConfigJson);
        InterfaceDef saved = catalog.saveInterface(def, v);
        ra.addFlashAttribute("msg", "Saved interface " + saved.getName());
        return "redirect:/interfaces/" + saved.getId() + "/edit";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable String id, RedirectAttributes ra) {
        catalog.deleteInterface(id);
        ra.addFlashAttribute("msg", "Deleted interface");
        return "redirect:/interfaces";
    }

    @PostMapping("/{id}/run")
    public String runNow(@PathVariable String id, RedirectAttributes ra) {
        String runId = engine.start(id, TriggerType.MANUAL, Map.of("ui", true));
        ra.addFlashAttribute("msg", "Run queued: " + runId);
        return "redirect:/runs/" + runId;
    }
}

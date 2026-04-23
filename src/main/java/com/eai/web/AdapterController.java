package com.eai.web;

import com.eai.catalog.CatalogService;
import com.eai.domain.AdapterConfigEntity;
import com.eai.domain.AdapterDirection;
import com.eai.domain.AdapterType;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/adapters")
public class AdapterController {

    private final CatalogService catalog;

    public AdapterController(CatalogService catalog) {
        this.catalog = catalog;
    }

    private void addFormModel(Model model) {
        model.addAttribute("types", AdapterType.values());
        model.addAttribute("directions", AdapterDirection.values());
        model.addAttribute("systems", catalog.listSystems());
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("adapters", catalog.listAdapters());
        model.addAttribute("systems", catalog.listSystems());
        return "adapters/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("adapter", new AdapterConfigEntity());
        addFormModel(model);
        return "adapters/edit";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable String id, Model model) {
        AdapterConfigEntity a = catalog.findAdapter(id).orElseThrow();
        model.addAttribute("adapter", a);
        addFormModel(model);
        return "adapters/edit";
    }

    @PostMapping("/save")
    public String save(@RequestParam(required = false) String id,
                       @RequestParam String name,
                       @RequestParam AdapterType type,
                       @RequestParam AdapterDirection direction,
                       @RequestParam(required = false) String systemCode,
                       @RequestParam String configJson,
                       RedirectAttributes ra) {
        AdapterConfigEntity a = (id == null || id.isBlank())
                ? new AdapterConfigEntity()
                : catalog.findAdapter(id).orElse(new AdapterConfigEntity());
        a.setName(name);
        a.setType(type);
        a.setDirection(direction);
        a.setSystemCode((systemCode == null || systemCode.isBlank()) ? null : systemCode);
        a.setConfigJson(configJson == null ? "{}" : configJson);
        AdapterConfigEntity saved = catalog.saveAdapter(a);
        ra.addFlashAttribute("msg", "Saved adapter " + saved.getName());
        return "redirect:/adapters";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable String id, RedirectAttributes ra) {
        catalog.deleteAdapter(id);
        ra.addFlashAttribute("msg", "Deleted adapter");
        return "redirect:/adapters";
    }
}

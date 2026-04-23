package com.eai.web;

import com.eai.catalog.CatalogService;
import com.eai.domain.AdapterConfigRepository;
import com.eai.domain.RegisteredSystem;
import com.eai.domain.SystemType;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/systems")
public class SystemController {

    private final CatalogService catalog;
    private final AdapterConfigRepository adapters;

    public SystemController(CatalogService catalog, AdapterConfigRepository adapters) {
        this.catalog = catalog;
        this.adapters = adapters;
    }

    @GetMapping
    public String list(Model model) {
        var systems = catalog.listSystems();
        Map<String, Long> adapterCountByCode = adapters.findAll().stream()
                .filter(a -> a.getSystemCode() != null && !a.getSystemCode().isBlank())
                .collect(Collectors.groupingBy(a -> a.getSystemCode(), Collectors.counting()));
        model.addAttribute("systems", systems);
        model.addAttribute("adapterCounts", adapterCountByCode);
        return "systems/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("system", new RegisteredSystem());
        model.addAttribute("types", SystemType.values());
        return "systems/edit";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable String id, Model model) {
        RegisteredSystem s = catalog.findSystem(id).orElseThrow();
        model.addAttribute("system", s);
        model.addAttribute("types", SystemType.values());
        model.addAttribute("linkedAdapters", adapters.findAll().stream()
                .filter(a -> s.getCode().equals(a.getSystemCode()))
                .toList());
        return "systems/edit";
    }

    @PostMapping("/save")
    public String save(@RequestParam(required = false) String id,
                       @RequestParam String code,
                       @RequestParam String name,
                       @RequestParam SystemType type,
                       @RequestParam(required = false) String category,
                       @RequestParam(required = false) String owner,
                       @RequestParam(required = false) String contact,
                       @RequestParam(required = false) String baseUrl,
                       @RequestParam(required = false) String description,
                       @RequestParam(defaultValue = "true") boolean active,
                       RedirectAttributes ra) {
        RegisteredSystem s = (id == null || id.isBlank())
                ? new RegisteredSystem()
                : catalog.findSystem(id).orElse(new RegisteredSystem());
        s.setCode(code.trim());
        s.setName(name.trim());
        s.setType(type);
        s.setCategory(nullIfBlank(category));
        s.setOwner(nullIfBlank(owner));
        s.setContact(nullIfBlank(contact));
        s.setBaseUrl(nullIfBlank(baseUrl));
        s.setDescription(nullIfBlank(description));
        s.setActive(active);
        try {
            RegisteredSystem saved = catalog.saveSystem(s);
            ra.addFlashAttribute("msg", "Saved system " + saved.getCode());
        } catch (Exception e) {
            ra.addFlashAttribute("msg", "Save failed: " + rootCause(e));
            return "redirect:/systems/new";
        }
        return "redirect:/systems";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable String id, RedirectAttributes ra) {
        catalog.deleteSystem(id);
        ra.addFlashAttribute("msg", "Deleted system");
        return "redirect:/systems";
    }

    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static String rootCause(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) c = c.getCause();
        return c.getMessage() == null ? c.getClass().getSimpleName() : c.getMessage();
    }
}

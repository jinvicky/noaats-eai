package com.eai.web;

import com.eai.domain.PlatformUser;
import com.eai.domain.PlatformUserRepository;
import com.eai.domain.UserRole;

import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/users")
public class UserController {

    private final PlatformUserRepository users;

    public UserController(PlatformUserRepository users) {
        this.users = users;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", users.findAllByOrderByUsernameAsc());
        model.addAttribute("activeCount", users.countByActiveTrue());
        return "users/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("user", new PlatformUser());
        model.addAttribute("roles", UserRole.values());
        return "users/edit";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable String id, Model model) {
        PlatformUser u = users.findById(id).orElseThrow();
        model.addAttribute("user", u);
        model.addAttribute("roles", UserRole.values());
        return "users/edit";
    }

    @PostMapping("/save")
    @Transactional
    public String save(@RequestParam(required = false) String id,
                       @RequestParam String username,
                       @RequestParam(required = false) String fullName,
                       @RequestParam(required = false) String email,
                       @RequestParam UserRole role,
                       @RequestParam(required = false) String team,
                       @RequestParam(required = false) String description,
                       @RequestParam(defaultValue = "true") boolean active,
                       RedirectAttributes ra) {
        PlatformUser u = (id == null || id.isBlank())
                ? new PlatformUser()
                : users.findById(id).orElse(new PlatformUser());
        u.setUsername(username.trim());
        u.setFullName(nullIfBlank(fullName));
        u.setEmail(nullIfBlank(email));
        u.setRole(role);
        u.setTeam(nullIfBlank(team));
        u.setDescription(nullIfBlank(description));
        u.setActive(active);
        try {
            PlatformUser saved = users.save(u);
            ra.addFlashAttribute("msg", "Saved user " + saved.getUsername());
        } catch (Exception e) {
            ra.addFlashAttribute("msg", "Save failed: " + rootCause(e));
            return "redirect:/users/new";
        }
        return "redirect:/users";
    }

    @PostMapping("/{id}/delete")
    @Transactional
    public String delete(@PathVariable String id, RedirectAttributes ra) {
        users.deleteById(id);
        ra.addFlashAttribute("msg", "Deleted user");
        return "redirect:/users";
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

package ua.kpi.datingapp.user;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Сторінки входу та реєстрації (US-01). Сам вхід обробляє Spring Security. */
@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registrationPage(Model model) {
        model.addAttribute("form", new RegistrationForm());
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegistrationForm form,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes) {
        if (!form.passwordsMatch()) {
            bindingResult.rejectValue("confirmPassword", "mismatch", "Паролі не збігаються");
        }
        if (form.getBirthDate() != null && !form.isAdult()) {
            bindingResult.rejectValue("birthDate", "underage", "Реєстрація доступна з 18 років");
        }
        if (bindingResult.hasErrors()) {
            return "register";
        }

        try {
            userService.register(form.getEmail(), form.getPassword(), form.getFirstName(), form.getBirthDate());
        } catch (EmailAlreadyUsedException e) {
            bindingResult.rejectValue("email", "duplicate", "Користувач з таким email уже існує");
            return "register";
        }

        redirectAttributes.addFlashAttribute("message", "Реєстрація успішна. Увійдіть у систему.");
        return "redirect:/login";
    }
}

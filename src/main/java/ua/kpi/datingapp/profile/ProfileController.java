package ua.kpi.datingapp.profile;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ua.kpi.datingapp.user.UserService;

import java.security.Principal;

/** Створення, редагування та перегляд анкет. */
@Controller
public class ProfileController {

    private final ProfileService profileService;
    private final UserService userService;

    public ProfileController(ProfileService profileService, UserService userService) {
        this.profileService = profileService;
        this.userService = userService;
    }

    /** Власна анкета (створюється під час реєстрації). */
    @GetMapping("/profile")
    public String ownProfile(Principal principal) {
        Long userId = userService.getIdByEmail(principal.getName());
        return "redirect:/users/" + userId + "/profile";
    }

    @GetMapping("/profile/edit")
    public String editForm(Principal principal, Model model) {
        Long userId = userService.getIdByEmail(principal.getName());
        ProfileForm form = profileService.findOwnProfileData(userId)
                .map(ProfileForm::from)
                .orElseGet(ProfileForm::new);
        model.addAttribute("form", form);
        return "profile-form";
    }

    @PostMapping("/profile/edit")
    public String saveProfile(@Valid @ModelAttribute("form") ProfileForm form,
                              BindingResult bindingResult,
                              Principal principal,
                              RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "profile-form";
        }
        Long userId = userService.getIdByEmail(principal.getName());
        profileService.saveOwnProfile(userId, form.toData());
        redirectAttributes.addFlashAttribute("message", "Анкету збережено");
        return "redirect:/profile";
    }

    /** Чужа анкета: закрита частина передається в шаблон лише за наявності доступу. */
    @GetMapping("/users/{userId}/profile")
    public String viewProfile(@PathVariable("userId") Long userId, Principal principal, Model model) {
        Long viewerId = userService.getIdByEmail(principal.getName());
        model.addAttribute("view", profileService.viewProfile(viewerId, userId));
        return "profile-view";
    }
}

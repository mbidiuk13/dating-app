package ua.kpi.datingapp.invitation;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ua.kpi.datingapp.profile.ProfileService;
import ua.kpi.datingapp.user.UserService;

import java.security.Principal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Обмін запрошеннями. Адреси відповідають діаграмі послідовностей ЛР2:
 * POST /api/invitations/send (крок 2) і POST /api/invitations/{id}/accept (крок 13).
 */
@Controller
public class InvitationController {

    private final InvitationService invitationService;
    private final UserService userService;
    private final ProfileService profileService;

    public InvitationController(InvitationService invitationService,
                                UserService userService,
                                ProfileService profileService) {
        this.invitationService = invitationService;
        this.userService = userService;
        this.profileService = profileService;
    }

    @GetMapping("/invitations")
    public String invitations(Principal principal, Model model) {
        Long userId = currentUserId(principal);
        List<InvitationDTO> received = invitationService.getReceived(userId);
        List<InvitationDTO> sent = invitationService.getSent(userId);

        Set<Long> counterparts = new HashSet<>();
        received.forEach(invitation -> counterparts.add(invitation.counterpartUserId()));
        sent.forEach(invitation -> counterparts.add(invitation.counterpartUserId()));

        model.addAttribute("received", received);
        model.addAttribute("sent", sent);
        model.addAttribute("names", profileService.getFirstNames(counterparts));
        return "invitations";
    }

    @PostMapping("/api/invitations/send")
    public String send(@RequestParam("receiverId") Long receiverId, Principal principal, RedirectAttributes redirect) {
        try {
            invitationService.sendInvitation(currentUserId(principal), receiverId);
            redirect.addFlashAttribute("message", "Запрошення надіслано");
        } catch (InvitationException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/users/" + receiverId + "/profile";
    }

    /** Крок 25: після підтвердження показуємо анкету з відкритою закритою інформацією та сумісністю. */
    @PostMapping("/api/invitations/{id}/accept")
    public String accept(@PathVariable("id") Long id, Principal principal, RedirectAttributes redirect) {
        try {
            InvitationDTO accepted = invitationService.acceptInvitation(id, currentUserId(principal));
            redirect.addFlashAttribute("message", "Запрошення підтверджено. Закрита інформація тепер доступна вам обом.");
            return "redirect:/users/" + accepted.counterpartUserId() + "/profile";
        } catch (InvitationException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/invitations";
        }
    }

    @PostMapping("/api/invitations/{id}/reject")
    public String reject(@PathVariable("id") Long id, Principal principal, RedirectAttributes redirect) {
        try {
            invitationService.rejectInvitation(id, currentUserId(principal));
            redirect.addFlashAttribute("message", "Запрошення відхилено");
        } catch (InvitationException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/invitations";
    }

    @PostMapping("/api/invitations/{id}/cancel")
    public String cancel(@PathVariable("id") Long id, Principal principal, RedirectAttributes redirect) {
        try {
            invitationService.cancelInvitation(id, currentUserId(principal));
            redirect.addFlashAttribute("message", "Запрошення скасовано");
        } catch (InvitationException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/invitations";
    }

    private Long currentUserId(Principal principal) {
        return userService.getIdByEmail(principal.getName());
    }
}

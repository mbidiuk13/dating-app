package ua.kpi.datingapp.profile;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ua.kpi.datingapp.user.UserService;

import java.security.Principal;
import java.util.List;
import java.util.Set;

/** Пошук анкет за ключовими словами (US-03). */
@Controller
public class SearchController {

    private final ProfileService profileService;
    private final UserService userService;

    public SearchController(ProfileService profileService, UserService userService) {
        this.profileService = profileService;
        this.userService = userService;
    }

    @GetMapping("/search")
    public String search(@RequestParam(name = "q", required = false) String query,
                         Principal principal,
                         Model model) {
        Set<String> tags = Keyword.parseTags(query);
        List<ProfilePublicDTO> results = tags.isEmpty()
                ? List.of()
                : profileService.search(userService.getIdByEmail(principal.getName()), tags);

        model.addAttribute("query", query);
        model.addAttribute("searched", !tags.isEmpty());
        model.addAttribute("results", results);
        return "search";
    }
}

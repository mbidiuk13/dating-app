package ua.kpi.datingapp.profile;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ua.kpi.datingapp.user.UserService;

import java.security.Principal;
import java.util.Set;

/** Пошук анкет за ключовими словами (US-03) із сортуванням і фільтром за віком. */
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
                         @RequestParam(name = "sort", required = false) String sort,
                         @RequestParam(name = "minAge", required = false) Integer minAge,
                         @RequestParam(name = "maxAge", required = false) Integer maxAge,
                         Principal principal,
                         Model model) {
        Set<String> tags = Keyword.parseTags(query);
        SearchCriteria criteria = new SearchCriteria(tags, SearchSort.fromParam(sort), minAge, maxAge);
        SearchResults results = tags.isEmpty()
                ? SearchResults.empty()
                : profileService.search(userService.getIdByEmail(principal.getName()), criteria);

        model.addAttribute("query", query);
        model.addAttribute("criteria", criteria);
        model.addAttribute("sorts", SearchSort.values());
        model.addAttribute("searched", !tags.isEmpty());
        model.addAttribute("results", results);
        return "search";
    }
}

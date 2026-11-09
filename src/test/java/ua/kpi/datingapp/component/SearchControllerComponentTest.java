package ua.kpi.datingapp.component;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ua.kpi.datingapp.config.SecurityConfig;
import ua.kpi.datingapp.profile.ProfilePublicDTO;
import ua.kpi.datingapp.profile.ProfileService;
import ua.kpi.datingapp.profile.SearchController;
import ua.kpi.datingapp.profile.SearchCriteria;
import ua.kpi.datingapp.profile.SearchHit;
import ua.kpi.datingapp.profile.SearchResults;
import ua.kpi.datingapp.profile.SearchSort;
import ua.kpi.datingapp.user.UserService;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Компонентні тести веб-шару: SearchController + Spring Security + шаблон search.html.
 * Сервіси замінені моками, тож перевіряється лише взаємодія контролера з ними:
 * які параметри запиту доходять до сервісу і що потрапляє на сторінку.
 */
@WebMvcTest(SearchController.class)
@Import(SecurityConfig.class)
@DisplayName("SearchController + Security + шаблон: сторінка пошуку")
class SearchControllerComponentTest {

    private static final String EMAIL = "bohdan@test.ua";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ProfileService profileService;
    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("Ключові слова, сортування й межі віку з адреси доходять до сервісу в нормалізованому вигляді")
    void queryParametersArePassedToService() throws Exception {
        when(userService.getIdByEmail(EMAIL)).thenReturn(2L);
        when(profileService.search(eq(2L), any(SearchCriteria.class))).thenReturn(SearchResults.empty());

        mockMvc.perform(get("/search")
                        .param("q", "Кава, джаз")
                        .param("sort", "youngest")
                        .param("minAge", "40")
                        .param("maxAge", "25")
                        .with(user(EMAIL)))
                .andExpect(status().isOk())
                .andExpect(view().name("search"));

        ArgumentCaptor<SearchCriteria> criteria = ArgumentCaptor.forClass(SearchCriteria.class);
        verify(profileService).search(eq(2L), criteria.capture());
        assertThat(criteria.getValue().tags()).containsExactlyInAnyOrder("кава", "джаз");
        assertThat(criteria.getValue().sort()).isEqualTo(SearchSort.YOUNGEST);
        assertThat(criteria.getValue().minAge()).isEqualTo(25);
        assertThat(criteria.getValue().maxAge()).isEqualTo(40);
    }

    @Test
    @DisplayName("Порожній запит не звертається до сервісу пошуку")
    void emptyQueryDoesNotCallService() throws Exception {
        mockMvc.perform(get("/search").with(user(EMAIL)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("searched", false));

        verifyNoInteractions(profileService);
    }

    @Test
    @DisplayName("Знайдена анкета показується з кількістю спільних інтересів і кнопкою запрошення")
    void resultsShowSharedInterestsAndInviteButton() throws Exception {
        when(userService.getIdByEmail(EMAIL)).thenReturn(2L);
        SearchHit hit = new SearchHit(new ProfilePublicDTO(5L, "Олег", 30, "Люблю каву", List.of(), Set.of("кава")), 1);
        when(profileService.search(eq(2L), any(SearchCriteria.class))).thenReturn(new SearchResults(List.of(hit), 1));

        mockMvc.perform(get("/search").param("q", "кава").with(user(EMAIL)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Олег")))
                .andExpect(content().string(containsString("Спільних інтересів: 1")))
                .andExpect(content().string(containsString("Надіслати запрошення")))
                .andExpect(content().string(containsString("/users/5/profile")));
    }

    @Test
    @DisplayName("Вік, що не є числом, дає сторінку «Некоректний запит» (400)")
    void notANumberAgeGivesBadRequestPage() throws Exception {
        mockMvc.perform(get("/search").param("q", "кава").param("minAge", "abc").with(user(EMAIL)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Некоректний запит")));

        verifyNoInteractions(profileService);
    }

    @Test
    @DisplayName("Гостя без входу перенаправлено на сторінку входу")
    void guestIsRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/search").param("q", "кава"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        verifyNoInteractions(profileService, userService);
    }
}

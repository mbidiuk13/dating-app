package ua.kpi.datingapp.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ua.kpi.datingapp.invitation.InvitationDTO;
import ua.kpi.datingapp.invitation.InvitationService;
import ua.kpi.datingapp.invitation.InvitationStatus;
import ua.kpi.datingapp.profile.KeywordRepository;
import ua.kpi.datingapp.user.UserRepository;
import ua.kpi.datingapp.user.UserService;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Інтеграційні тести: усі шари разом (Security → контролери → сервіси → JPA → БД H2)
 * через справжні HTTP-запити MockMvc. Перевіряють функціональні вимоги ЛР1 (US-01…US-05, UC-1)
 * і ті нефункціональні вимоги, що стосуються взаємодії модулів (приватність, безпечні помилки).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UseCaseIntegrationTest {

    private static final String PASSWORD = "password123";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    /** email → ім'я, з яким користувача зареєстровано. */
    private final Map<String, String> names = new ConcurrentHashMap<>();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private KeywordRepository keywordRepository;
    @Autowired
    private InvitationService invitationService;

    // ---------- US-01: реєстрація, вхід, BCrypt ----------

    @Test
    void guestIsRedirectedToLoginPage() throws Exception {
        mockMvc.perform(get("/search"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void registeredUserLogsInAndPasswordIsStoredAsBCryptHash() throws Exception {
        String email = register("Anna");

        mockMvc.perform(formLogin("/login").userParameter("email").user(email).password(PASSWORD))
                .andExpect(authenticated().withUsername(email));

        String hash = userRepository.findByEmail(email).orElseThrow().getPasswordHash();
        assertThat(hash).startsWith("$2a$").doesNotContain(PASSWORD);
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        String email = register("Anna");

        mockMvc.perform(formLogin("/login").userParameter("email").user(email).password("wrong-password"))
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void logoutEndsSession() throws Exception {
        String email = register("Anna");

        mockMvc.perform(post("/logout").with(user(email)).with(csrf()))
                .andExpect(redirectedUrl("/login?logout"));
    }

    // ---------- UC-1: основний сценарій ----------

    @Test
    void mainScenarioOpensPrivateDataForBothAfterAcceptance() throws Exception {
        String tag = uniqueTag();
        String anna = register("Anna");
        String bohdan = register("Bohdan");
        fillProfile(anna, tag, "anna-secret-contact", false, false, null);
        fillProfile(bohdan, tag, "bohdan-secret-contact", false, false, null);
        Long annaId = userService.getIdByEmail(anna);
        Long bohdanId = userService.getIdByEmail(bohdan);

        // US-03: Богдан знаходить Анну; у результатах немає закритих даних
        mockMvc.perform(get("/search").param("q", tag).with(user(bohdan)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/users/" + annaId + "/profile")))
                .andExpect(content().string(not(containsString("anna-secret-contact"))));

        // US-02: до підтвердження закрита інформація навіть не передається в HTML
        mockMvc.perform(get("/users/{id}/profile", annaId).with(user(bohdan)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("anna-secret-contact"))));

        // UC-1, кроки 1–2: запрошення зі статусом PENDING
        mockMvc.perform(post("/api/invitations/send").param("receiverId", annaId.toString())
                        .with(user(bohdan)).with(csrf()))
                .andExpect(redirectedUrl("/users/" + annaId + "/profile"))
                .andExpect(flash().attribute("message", "Запрошення надіслано"));
        InvitationDTO received = invitationService.getReceived(annaId).get(0);
        assertThat(received.status()).isEqualTo(InvitationStatus.PENDING);

        // UC-1, кроки 3–4: Анна бачить запрошення у списку і підтверджує
        mockMvc.perform(get("/invitations").with(user(anna)))
                .andExpect(content().string(containsString("PENDING")));
        mockMvc.perform(post("/api/invitations/{id}/accept", received.id()).with(user(anna)).with(csrf()))
                .andExpect(redirectedUrl("/users/" + bohdanId + "/profile"));

        // Взаємний доступ і US-05: сумісність показується відсотком і коротким текстом
        mockMvc.perform(get("/users/{id}/profile", annaId).with(user(bohdan)))
                .andExpect(content().string(containsString("anna-secret-contact")))
                .andExpect(content().string(containsString(" %")))
                .andExpect(content().string(containsString("Сумісність за матрицею долі")));
        mockMvc.perform(get("/users/{id}/profile", bohdanId).with(user(anna)))
                .andExpect(content().string(containsString("bohdan-secret-contact")));
    }

    @Test
    void rejectedInvitationKeepsPrivateDataHiddenAndHasNoCompatibility() throws Exception {
        String anna = register("Anna");
        String bohdan = register("Bohdan");
        fillProfile(anna, uniqueTag(), "anna-secret-contact", false, false, null);
        Long annaId = userService.getIdByEmail(anna);

        sendInvitation(bohdan, annaId);
        Long invitationId = invitationService.getReceived(annaId).get(0).id();
        mockMvc.perform(post("/api/invitations/{id}/reject", invitationId).with(user(anna)).with(csrf()))
                .andExpect(redirectedUrl("/invitations"));

        InvitationDTO rejected = invitationService.getReceived(annaId).get(0);
        assertThat(rejected.status()).isEqualTo(InvitationStatus.REJECTED);
        assertThat(rejected.compatibilityPercentage()).isNull();
        mockMvc.perform(get("/users/{id}/profile", annaId).with(user(bohdan)))
                .andExpect(content().string(not(containsString("anna-secret-contact"))));
    }

    @Test
    void secondInvitationIsRefusedWhileFirstIsActive() throws Exception {
        String anna = register("Anna");
        String bohdan = register("Bohdan");
        Long annaId = userService.getIdByEmail(anna);

        sendInvitation(bohdan, annaId);
        mockMvc.perform(post("/api/invitations/send").param("receiverId", annaId.toString())
                        .with(user(bohdan)).with(csrf()))
                .andExpect(flash().attribute("error", "Між вами вже є активне запрошення"));

        assertThat(invitationService.getReceived(annaId)).hasSize(1);
    }

    @Test
    void senderCanCancelPendingInvitation() throws Exception {
        String anna = register("Anna");
        String bohdan = register("Bohdan");
        Long annaId = userService.getIdByEmail(anna);

        sendInvitation(bohdan, annaId);
        Long invitationId = invitationService.getReceived(annaId).get(0).id();
        mockMvc.perform(post("/api/invitations/{id}/cancel", invitationId).with(user(bohdan)).with(csrf()))
                .andExpect(flash().attribute("message", "Запрошення скасовано"));

        assertThat(invitationService.getReceived(annaId).get(0).status()).isEqualTo(InvitationStatus.CANCELLED);
    }

    // ---------- Самостійне налаштування відкритих/закритих полів ----------

    @Test
    void hiddenPhotoAndAgeAreSentOnlyAfterAcceptance() throws Exception {
        String photo = "https://example.com/hidden-photo-" + SEQUENCE.incrementAndGet() + ".jpg";
        String anna = register("Anna", LocalDate.now().minusYears(27).minusDays(3));
        String bohdan = register("Bohdan");
        fillProfile(anna, uniqueTag(), "anna-secret-contact", true, true, photo);
        Long annaId = userService.getIdByEmail(anna);

        mockMvc.perform(get("/users/{id}/profile", annaId).with(user(bohdan)))
                .andExpect(content().string(not(containsString(photo))))
                .andExpect(content().string(not(containsString("27 р."))));

        sendInvitation(bohdan, annaId);
        Long invitationId = invitationService.getReceived(annaId).get(0).id();
        mockMvc.perform(post("/api/invitations/{id}/accept", invitationId).with(user(anna)).with(csrf()));

        mockMvc.perform(get("/users/{id}/profile", annaId).with(user(bohdan)))
                .andExpect(content().string(containsString(photo)))
                .andExpect(content().string(containsString("27 р.")));
    }

    // ---------- Пошук: сортування та фільтр за віком ----------

    @Test
    void searchSortsByCommonInterestsAndFiltersByAge() throws Exception {
        String tagA = uniqueTag();
        String tagB = uniqueTag();
        String viewer = register("Viewer");
        String twoMatches = register("Twomatch", LocalDate.now().minusYears(25));
        String oneMatch = register("Onematch", LocalDate.now().minusYears(22));
        String older = register("Oldermatch", LocalDate.now().minusYears(45));
        fillProfile(twoMatches, tagA + ", " + tagB, null, false, false, null);
        fillProfile(oneMatch, tagA, null, false, false, null);
        fillProfile(older, tagA, null, false, false, null);

        String byRelevance = mockMvc.perform(get("/search").param("q", tagA + " " + tagB).with(user(viewer)))
                .andReturn().getResponse().getContentAsString();
        assertThat(byRelevance.indexOf("Twomatch")).isLessThan(byRelevance.indexOf("Onematch"));
        assertThat(byRelevance).contains("Спільних інтересів: 2");

        String youngestFirst = mockMvc.perform(get("/search").param("q", tagA).param("sort", "youngest")
                        .with(user(viewer)))
                .andReturn().getResponse().getContentAsString();
        assertThat(youngestFirst.indexOf("Onematch")).isLessThan(youngestFirst.indexOf("Twomatch"));
        assertThat(youngestFirst.indexOf("Twomatch")).isLessThan(youngestFirst.indexOf("Oldermatch"));

        mockMvc.perform(get("/search").param("q", tagA).param("minAge", "18").param("maxAge", "30")
                        .with(user(viewer)))
                .andExpect(content().string(containsString("Twomatch")))
                .andExpect(content().string(containsString("Onematch")))
                .andExpect(content().string(not(containsString("Oldermatch"))));
    }

    // ---------- Видалення анкети ----------

    @Test
    void deletedProfileDisappearsFromSearchButKeywordsStay() throws Exception {
        String tag = uniqueTag();
        String anna = register("Deletedanna");
        String bohdan = register("Bohdan");
        fillProfile(anna, tag, null, false, false, null);
        Long annaId = userService.getIdByEmail(anna);
        sendInvitation(bohdan, annaId);

        mockMvc.perform(post("/profile/delete").with(user(anna)).with(csrf()))
                .andExpect(redirectedUrl("/profile/edit"));
        mockMvc.perform(post("/profile/delete").param("confirm", "true").with(user(anna)).with(csrf()))
                .andExpect(redirectedUrl("/login?deleted"));

        assertThat(userRepository.findByEmail(anna)).isEmpty();
        assertThat(keywordRepository.findByTag(tag)).isPresent();
        mockMvc.perform(get("/search").param("q", tag).with(user(bohdan)))
                .andExpect(content().string(not(containsString("Deletedanna"))));
        mockMvc.perform(get("/invitations").with(user(bohdan)))
                .andExpect(status().isOk());
    }

    // ---------- НФВ надійності: безпечні повідомлення про помилки ----------

    @Test
    void missingProfileShowsUkrainianErrorWithoutStackTrace() throws Exception {
        String anna = register("Anna");

        mockMvc.perform(get("/users/{id}/profile", 999_999).with(user(anna)))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Сторінку не знайдено")))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("at ua.kpi"))));
    }

    @Test
    void malformedAddressShowsBadRequestPage() throws Exception {
        String anna = register("Anna");

        mockMvc.perform(get("/users/not-a-number/profile").with(user(anna)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Некоректний запит")))
                .andExpect(content().string(not(containsString("Exception"))));
    }

    @Test
    void healthEndpointReportsSystemIsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("UP")));
    }

    // ---------- допоміжні методи ----------

    private String register(String firstName) throws Exception {
        return register(firstName, LocalDate.now().minusYears(20));
    }

    /** Реєстрація через справжню форму; повертає email нового користувача. */
    private String register(String firstName, LocalDate birthDate) throws Exception {
        String email = firstName.toLowerCase() + SEQUENCE.incrementAndGet() + "@test.ua";
        mockMvc.perform(post("/register")
                        .param("email", email)
                        .param("password", PASSWORD)
                        .param("confirmPassword", PASSWORD)
                        .param("firstName", firstName)
                        .param("birthDate", birthDate.toString())
                        .with(csrf()))
                .andExpect(redirectedUrl("/login"));
        names.put(email, firstName);
        return email;
    }

    private void fillProfile(String email, String keywords, String contact,
                             boolean photosPrivate, boolean agePrivate, String photo) throws Exception {
        MockHttpServletRequestBuilder request = post("/profile/edit")
                .param("firstName", names.get(email))
                .param("openDescription", "Опис для тесту")
                .param("keywords", keywords)
                .param("photosPrivate", String.valueOf(photosPrivate))
                .param("agePrivate", String.valueOf(agePrivate))
                .with(user(email))
                .with(csrf());
        if (contact != null) {
            request.param("closedContactInfo", contact);
        }
        if (photo != null) {
            request.param("openPhotos", photo);
        }
        mockMvc.perform(request).andExpect(redirectedUrl("/profile"));
    }

    private void sendInvitation(String senderEmail, Long receiverId) throws Exception {
        mockMvc.perform(post("/api/invitations/send").param("receiverId", receiverId.toString())
                        .with(user(senderEmail)).with(csrf()))
                .andExpect(flash().attribute("message", "Запрошення надіслано"));
    }

    private static String uniqueTag() {
        return "tag" + SEQUENCE.incrementAndGet();
    }
}

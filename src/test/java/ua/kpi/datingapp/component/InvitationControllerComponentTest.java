package ua.kpi.datingapp.component;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ua.kpi.datingapp.config.SecurityConfig;
import ua.kpi.datingapp.invitation.InvitationController;
import ua.kpi.datingapp.invitation.InvitationDTO;
import ua.kpi.datingapp.invitation.InvitationException;
import ua.kpi.datingapp.invitation.InvitationService;
import ua.kpi.datingapp.invitation.InvitationStatus;
import ua.kpi.datingapp.profile.ProfileService;
import ua.kpi.datingapp.user.UserService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Компонентні тести веб-шару запрошень (UC-1): InvitationController + Spring Security + шаблон.
 * Адреси POST /api/invitations/... відповідають діаграмі послідовностей ЛР2.
 */
@WebMvcTest(InvitationController.class)
@Import(SecurityConfig.class)
@DisplayName("InvitationController + Security + шаблон: обмін запрошеннями")
class InvitationControllerComponentTest {

    private static final String ANNA = "anna@test.ua";
    private static final Long ANNA_ID = 1L;
    private static final Long BOHDAN_ID = 2L;

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private InvitationService invitationService;
    @MockitoBean
    private UserService userService;
    @MockitoBean
    private ProfileService profileService;

    @Test
    @DisplayName("Надсилання запрошення повертає на анкету отримувача з повідомленням")
    void sendRedirectsToReceiverProfileWithMessage() throws Exception {
        when(userService.getIdByEmail(ANNA)).thenReturn(ANNA_ID);

        mockMvc.perform(post("/api/invitations/send").param("receiverId", "2").with(user(ANNA)).with(csrf()))
                .andExpect(redirectedUrl("/users/2/profile"))
                .andExpect(flash().attribute("message", "Запрошення надіслано"));

        verify(invitationService).sendInvitation(ANNA_ID, BOHDAN_ID);
    }

    @Test
    @DisplayName("Повторне запрошення показує зрозуміле повідомлення замість помилки")
    void duplicateInvitationShowsMessage() throws Exception {
        when(userService.getIdByEmail(ANNA)).thenReturn(ANNA_ID);
        when(invitationService.sendInvitation(ANNA_ID, BOHDAN_ID))
                .thenThrow(new InvitationException("Між вами вже є активне запрошення"));

        mockMvc.perform(post("/api/invitations/send").param("receiverId", "2").with(user(ANNA)).with(csrf()))
                .andExpect(redirectedUrl("/users/2/profile"))
                .andExpect(flash().attribute("error", "Між вами вже є активне запрошення"));
    }

    @Test
    @DisplayName("POST-запит без CSRF-токена відхиляється (403), сервіс не викликається")
    void postWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(post("/api/invitations/send").param("receiverId", "2").with(user(ANNA)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(invitationService);
    }

    @Test
    @DisplayName("Після підтвердження відкривається анкета відправника")
    void acceptOpensSenderProfile() throws Exception {
        when(userService.getIdByEmail(ANNA)).thenReturn(ANNA_ID);
        when(invitationService.acceptInvitation(10L, ANNA_ID))
                .thenReturn(invitation(10L, BOHDAN_ID, true, InvitationStatus.ACCEPTED));

        mockMvc.perform(post("/api/invitations/10/accept").with(user(ANNA)).with(csrf()))
                .andExpect(redirectedUrl("/users/2/profile"))
                .andExpect(flash().attribute("message",
                        "Запрошення підтверджено. Закрита інформація тепер доступна вам обом."));
    }

    @Test
    @DisplayName("Сторінка запрошень показує ім'я відправника, стан і кнопку підтвердження")
    void invitationsPageListsReceivedInvitation() throws Exception {
        when(userService.getIdByEmail(ANNA)).thenReturn(ANNA_ID);
        when(invitationService.getReceived(ANNA_ID))
                .thenReturn(List.of(invitation(10L, BOHDAN_ID, true, InvitationStatus.PENDING)));
        when(invitationService.getSent(ANNA_ID)).thenReturn(List.of());
        when(profileService.getFirstNames(anyCollection())).thenReturn(Map.of(BOHDAN_ID, "Богдан"));

        mockMvc.perform(get("/invitations").with(user(ANNA)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Богдан")))
                .andExpect(content().string(containsString("PENDING")))
                .andExpect(content().string(containsString("Підтвердити")))
                .andExpect(content().string(containsString("Ви ще не надсилали запрошень")));
    }

    private static InvitationDTO invitation(Long id, Long counterpartId, boolean incoming, InvitationStatus status) {
        return new InvitationDTO(id, counterpartId, incoming, status, LocalDateTime.now(), null, null, null);
    }
}

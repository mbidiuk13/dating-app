package ua.kpi.datingapp.profile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ua.kpi.datingapp.user.User;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Юніт-тести сутності Profile: розподіл на відкриту й закриту інформацію (US-02). */
@DisplayName("Profile: відкрита та закрита частини анкети")
class ProfileTest {

    private static final String PHOTO_1 = "https://photos.test/anna-1.jpg";
    private static final String PHOTO_2 = "https://photos.test/anna-2.jpg";

    private Profile profile;

    @BeforeEach
    void setUp() {
        User user = new User("anna@test.ua", "hash");
        ReflectionTestUtils.setField(user, "id", 1L);

        profile = new Profile(user, "Анна", LocalDate.now().minusYears(25));
        profile.updateOpenData("Анна", "Люблю каву та джаз", PHOTO_1 + "\n\n   " + PHOTO_2 + "  ");
        profile.updateClosedData("@anna_tg", "Живу на Подолі");
        profile.replaceKeywords(Set.of(new Keyword("кава"), new Keyword("джаз")));
    }

    @Test
    @DisplayName("Відкрита частина не містить жодного закритого поля")
    void publicInfoHasNoClosedData() {
        ProfilePublicDTO publicInfo = profile.getPublicInfo();

        assertThat(publicInfo.ownerId()).isEqualTo(1L);
        assertThat(publicInfo.age()).isEqualTo(25);
        assertThat(publicInfo.photoUrls()).containsExactly(PHOTO_1, PHOTO_2);
        assertThat(publicInfo.keywords()).containsExactly("джаз", "кава");
        assertThat(publicInfo.toString()).doesNotContain("@anna_tg", "Подолі");
    }

    @Test
    @DisplayName("Приховані власником фото й вік переходять у закриту частину")
    void hiddenPhotosAndAgeMoveToPrivatePart() {
        profile.updateVisibility(true, true);

        ProfilePublicDTO publicInfo = profile.getPublicInfo();
        assertThat(publicInfo.hasAge()).isFalse();
        assertThat(publicInfo.photoUrls()).isEmpty();

        ProfilePrivateDTO privateInfo = profile.getPrivateInfo();
        assertThat(privateInfo.hiddenAge()).isEqualTo(25);
        assertThat(privateInfo.hiddenPhotoUrls()).containsExactly(PHOTO_1, PHOTO_2);
        assertThat(privateInfo.closedContactInfo()).isEqualTo("@anna_tg");
    }

    @Test
    @DisplayName("Відкриті фото й вік не дублюються в закритій частині")
    void visibleFieldsAreNotDuplicatedInPrivatePart() {
        ProfilePrivateDTO privateInfo = profile.getPrivateInfo();

        assertThat(privateInfo.hiddenAge()).isNull();
        assertThat(privateInfo.hiddenPhotoUrls()).isEmpty();
    }

    @Test
    @DisplayName("Вік рахується повними роками: до дня народження ще на рік менше")
    void ageCountsFullYearsOnly() {
        Profile birthdayTomorrow = new Profile(new User("b@test.ua", "hash"), "Богдан",
                LocalDate.now().minusYears(30).plusDays(1));

        assertThat(birthdayTomorrow.getAge()).isEqualTo(29);
    }

    @Test
    @DisplayName("Дані для форми редагування збігаються з тим, що збережено")
    void profileDataContainsAllEditableFields() {
        profile.updateVisibility(true, false);

        ProfileData data = profile.toProfileData();

        assertThat(data.firstName()).isEqualTo("Анна");
        assertThat(data.openDescription()).isEqualTo("Люблю каву та джаз");
        assertThat(data.closedContactInfo()).isEqualTo("@anna_tg");
        assertThat(data.closedPrivateDetails()).isEqualTo("Живу на Подолі");
        assertThat(data.keywords()).containsExactly("джаз", "кава");
        assertThat(data.photosPrivate()).isTrue();
        assertThat(data.agePrivate()).isFalse();
    }

    @Test
    @DisplayName("Порожнє поле фото дає порожній список, а не список з порожнім рядком")
    void blankPhotosGiveEmptyList() {
        profile.updateOpenData("Анна", "Опис", "   ");

        assertThat(profile.getPublicInfo().photoUrls()).isEmpty();
    }
}

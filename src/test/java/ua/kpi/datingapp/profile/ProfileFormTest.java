package ua.kpi.datingapp.profile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/** Юніт-тести перетворення веб-форми анкети на дані для сервісу й навпаки. */
@DisplayName("ProfileForm: обробка даних форми анкети")
class ProfileFormTest {

    @Test
    @DisplayName("Пробіли по краях обрізаються, порожні поля стають null")
    void toDataTrimsValuesAndTurnsBlankIntoNull() {
        ProfileForm form = new ProfileForm();
        form.setFirstName("  Анна ");
        form.setOpenDescription("   ");
        form.setClosedContactInfo(null);
        form.setClosedPrivateDetails(" Деталі ");

        ProfileData data = form.toData();

        assertThat(data.firstName()).isEqualTo("Анна");
        assertThat(data.openDescription()).isNull();
        assertThat(data.closedContactInfo()).isNull();
        assertThat(data.closedPrivateDetails()).isEqualTo("Деталі");
    }

    @Test
    @DisplayName("Рядок ключових слів розбирається на нормалізовані теги")
    void toDataParsesKeywords() {
        ProfileForm form = new ProfileForm();
        form.setFirstName("Анна");
        form.setKeywords("Кава, джаз; КАВА");

        assertThat(form.toData().keywords()).containsExactlyInAnyOrder("кава", "джаз");
    }

    @Test
    @DisplayName("Перемикачі видимості передаються в сервіс без змін")
    void visibilitySwitchesArePassedToService() {
        ProfileForm form = new ProfileForm();
        form.setFirstName("Анна");
        form.setPhotosPrivate(true);

        ProfileData data = form.toData();

        assertThat(data.photosPrivate()).isTrue();
        assertThat(data.agePrivate()).isFalse();
    }

    @Test
    @DisplayName("Форма редагування заповнюється збереженими даними")
    void fromDataFillsFormForEditing() {
        ProfileData data = new ProfileData("Анна", "Опис", null, "@anna", null,
                new TreeSet<>(Set.of("кава", "джаз")), false, true);

        ProfileForm form = ProfileForm.from(data);

        assertThat(form.getFirstName()).isEqualTo("Анна");
        assertThat(form.getKeywords()).isEqualTo("джаз, кава");
        assertThat(form.getClosedContactInfo()).isEqualTo("@anna");
        assertThat(form.isPhotosPrivate()).isFalse();
        assertThat(form.isAgePrivate()).isTrue();
    }
}

package ua.kpi.datingapp.compatibility;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArcanaDestinyMatrixServiceTest {

    private final ArcanaDestinyMatrixService service = new ArcanaDestinyMatrixService();

    @Test
    void sameBirthDatesGiveMaximumCompatibility() {
        LocalDate date = LocalDate.of(2004, 5, 17);

        DestinyMatrixResult result = service.calculateCompatibility(date, date);

        assertThat(result.getCompatibilityPercentage()).isEqualTo(100);
        assertThat(result.getCompatibilityDescription()).isNotBlank();
    }

    @Test
    void resultDoesNotDependOnOrderOfDates() {
        LocalDate first = LocalDate.of(2003, 1, 29);
        LocalDate second = LocalDate.of(2005, 11, 3);

        int forward = service.calculateCompatibility(first, second).getCompatibilityPercentage();
        int backward = service.calculateCompatibility(second, first).getCompatibilityPercentage();

        assertThat(forward).isEqualTo(backward);
    }

    @Test
    void percentageStaysWithinAllowedRange() {
        LocalDate start = LocalDate.of(1990, 1, 1);
        for (int i = 0; i < 400; i++) {
            LocalDate a = start.plusDays(i * 37L);
            LocalDate b = start.plusDays(i * 53L + 11);

            int percentage = service.calculateCompatibility(a, b).getCompatibilityPercentage();

            assertThat(percentage).isBetween(ArcanaDestinyMatrixService.MIN_PERCENTAGE, 100);
        }
    }

    @Test
    void personalArcanaIsAlwaysBetween1And22() {
        LocalDate date = LocalDate.of(1970, 1, 1);
        for (int i = 0; i < 20_000; i++) {
            assertThat(service.personalArcana(date.plusDays(i))).isBetween(1, 22);
        }
    }

    @Test
    void reduceSumsDigitsUntilNumberFitsArcanaRange() {
        assertThat(ArcanaDestinyMatrixService.reduce(22)).isEqualTo(22);
        assertThat(ArcanaDestinyMatrixService.reduce(29)).isEqualTo(11);
        assertThat(ArcanaDestinyMatrixService.reduce(2004)).isEqualTo(6);
    }

    @Test
    void rejectsMissingDate() {
        assertThatThrownBy(() -> service.calculateCompatibility(null, LocalDate.now()))
                .isInstanceOf(NullPointerException.class);
    }
}

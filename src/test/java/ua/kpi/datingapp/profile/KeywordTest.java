package ua.kpi.datingapp.profile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Юніт-тести: нормалізація та розбір ключових слів (US-03, пошук за ключовими словами). */
@DisplayName("Keyword: нормалізація та розбір ключових слів")
class KeywordTest {

    @Test
    @DisplayName("Тег приводиться до нижнього регістру без пробілів по краях")
    void normalizeTrimsAndLowercases() {
        assertThat(Keyword.normalize("  МуЗиКа ")).isEqualTo("музика");
        assertThat(Keyword.normalize(null)).isEmpty();
    }

    @Test
    @DisplayName("Теги розділяються комою, крапкою з комою та пробілами, повтори прибираються")
    void parseTagsSplitsBySeparatorsAndRemovesDuplicates() {
        assertThat(Keyword.parseTags("кава, Музика;подорожі   кава"))
                .containsExactly("кава", "музика", "подорожі");
    }

    @Test
    @DisplayName("Межа довжини: тег із 50 символів приймається, із 51 — відкидається")
    void parseTagsKeepsBoundaryLengthAndDropsLonger() {
        String fifty = "a".repeat(Keyword.MAX_TAG_LENGTH);
        String fiftyOne = "b".repeat(Keyword.MAX_TAG_LENGTH + 1);

        assertThat(Keyword.parseTags(fifty + ", " + fiftyOne + ", кава"))
                .containsExactly(fifty, "кава");
    }

    @Test
    @DisplayName("Порожній рядок або null дає порожній набір тегів")
    void parseTagsOfBlankIsEmpty() {
        assertThat(Keyword.parseTags(null)).isEmpty();
        assertThat(Keyword.parseTags("  ,; ")).isEmpty();
    }

    @Test
    @DisplayName("Не можна створити порожній або задовгий тег")
    void constructorRejectsEmptyOrTooLongTag() {
        assertThatThrownBy(() -> new Keyword("   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Keyword("x".repeat(Keyword.MAX_TAG_LENGTH + 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new Keyword("x".repeat(Keyword.MAX_TAG_LENGTH)).getTag()).hasSize(Keyword.MAX_TAG_LENGTH);
    }

    @Test
    @DisplayName("Теги, що відрізняються лише регістром, вважаються однаковими")
    void keywordsWithSameNormalizedTagAreEqual() {
        Keyword upper = new Keyword("Кава");
        Keyword lower = new Keyword("кава");

        assertThat(upper).isEqualTo(lower);
        assertThat(upper.hashCode()).isEqualTo(lower.hashCode());
    }
}

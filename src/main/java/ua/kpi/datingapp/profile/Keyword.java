package ua.kpi.datingapp.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "keywords")
public class Keyword {

    public static final int MAX_TAG_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = MAX_TAG_LENGTH)
    private String tag;

    protected Keyword() {
        // для JPA
    }

    public Keyword(String tag) {
        String normalized = normalize(tag);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Ключове слово не може бути порожнім");
        }
        if (normalized.length() > MAX_TAG_LENGTH) {
            throw new IllegalArgumentException("Ключове слово довше за " + MAX_TAG_LENGTH + " символів");
        }
        this.tag = normalized;
    }

    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    public static Set<String> parseTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split("[,;\\s]+"))
                .map(Keyword::normalize)
                .filter(tag -> !tag.isEmpty() && tag.length() <= MAX_TAG_LENGTH)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Long getId() {
        return id;
    }

    public String getTag() {
        return tag;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Keyword other)) return false;
        return Objects.equals(tag, other.tag);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(tag);
    }
}

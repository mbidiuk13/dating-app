package ua.kpi.datingapp.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import ua.kpi.datingapp.user.User;

import java.time.LocalDate;
import java.time.Period;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Анкета користувача (ЛР2, діаграма класів).
 * Створюється разом з користувачем під час реєстрації (композиція User–Profile).
 * Дані фізично розділені на відкриті (open*) та закриті (closed*) поля (US-02).
 * Назовні анкета віддається лише через DTO: getPublicInfo() не містить закритих полів.
 * Власник може додатково перенести фото та вік у закриту частину (photosPrivate, agePrivate).
 * Індекс за датою народження пришвидшує фільтр пошуку за віком (НФВ продуктивності).
 */
@Entity
@Table(name = "profiles", indexes = @Index(name = "idx_profiles_birth_date", columnList = "birth_date"))
public class Profile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Композиція: анкета не існує без користувача і видаляється разом з ним. */
    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    /** Використовується для розрахунку матриці долі; іншим користувачам показується лише вік. */
    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    // ---- Відкрита інформація: бачать усі авторизовані користувачі ----

    @Column(name = "open_description", length = 2000)
    private String openDescription;

    /** Посилання на фото, по одному в рядку. */
    @Column(name = "open_photos", length = 2000)
    private String openPhotos;

    // ---- Закрита інформація: лише після взаємного ACCEPTED ----

    @Column(name = "closed_contact_info", length = 1000)
    private String closedContactInfo;

    @Column(name = "closed_private_details", length = 2000)
    private String closedPrivateDetails;

    // ---- Налаштування видимості: власник сам вирішує, чи показувати фото й вік усім ----

    /** true — фото бачать лише після взаємного ACCEPTED. */
    @Column(name = "photos_private", columnDefinition = "boolean default false")
    private boolean photosPrivate;

    /** true — вік бачать лише після взаємного ACCEPTED; анкета не бере участі у фільтрі за віком. */
    @Column(name = "age_private", columnDefinition = "boolean default false")
    private boolean agePrivate;

    /** Агрегація: теги — окремий довідник, спільний для багатьох анкет. */
    @ManyToMany
    @JoinTable(name = "profile_keywords",
            joinColumns = @JoinColumn(name = "profile_id"),
            inverseJoinColumns = @JoinColumn(name = "keyword_id"),
            indexes = @Index(name = "idx_profile_keywords_keyword", columnList = "keyword_id"))
    private Set<Keyword> keywords = new HashSet<>();

    protected Profile() {
        // для JPA
    }

    public Profile(User user, String firstName, LocalDate birthDate) {
        this.user = user;
        this.firstName = firstName;
        this.birthDate = birthDate;
    }

    public void updateOpenData(String firstName, String description, String photos) {
        this.firstName = firstName;
        this.openDescription = description;
        this.openPhotos = photos;
    }

    public void updateClosedData(String contactInfo, String privateDetails) {
        this.closedContactInfo = contactInfo;
        this.closedPrivateDetails = privateDetails;
    }

    /** Самостійне налаштування відкритих/закритих полів (ЛР1, середній пріоритет). */
    public void updateVisibility(boolean photosPrivate, boolean agePrivate) {
        this.photosPrivate = photosPrivate;
        this.agePrivate = agePrivate;
    }

    public void replaceKeywords(Set<Keyword> newKeywords) {
        this.keywords.clear();
        this.keywords.addAll(newKeywords);
    }

    public int getAge() {
        return Period.between(birthDate, LocalDate.now()).getYears();
    }

    /**
     * Відкрита частина анкети — без жодного закритого поля.
     * Якщо власник приховав фото або вік, вони сюди не потрапляють взагалі.
     */
    public ProfilePublicDTO getPublicInfo() {
        return new ProfilePublicDTO(
                user.getId(),
                firstName,
                agePrivate ? null : getAge(),
                openDescription,
                photosPrivate ? List.of() : photoUrls(),
                keywordTags());
    }

    /**
     * Закрита частина анкети. Право доступу (власник або взаємне ACCEPTED)
     * перевіряє ProfileService до виклику цього методу.
     * Приховані власником фото та вік передаються лише тут.
     */
    public ProfilePrivateDTO getPrivateInfo() {
        return new ProfilePrivateDTO(
                closedContactInfo,
                closedPrivateDetails,
                photosPrivate ? photoUrls() : List.of(),
                agePrivate ? getAge() : null);
    }

    /** Поля для форми редагування власної анкети. */
    public ProfileData toProfileData() {
        return new ProfileData(firstName, openDescription, openPhotos,
                closedContactInfo, closedPrivateDetails, keywordTags(),
                photosPrivate, agePrivate);
    }

    private List<String> photoUrls() {
        if (openPhotos == null || openPhotos.isBlank()) {
            return List.of();
        }
        return Arrays.stream(openPhotos.split("\\R"))
                .map(String::trim)
                .filter(url -> !url.isEmpty())
                .toList();
    }

    private Set<String> keywordTags() {
        return keywords.stream()
                .map(Keyword::getTag)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getFirstName() {
        return firstName;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public boolean isPhotosPrivate() {
        return photosPrivate;
    }

    public boolean isAgePrivate() {
        return agePrivate;
    }
}

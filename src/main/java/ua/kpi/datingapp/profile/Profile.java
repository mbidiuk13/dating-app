package ua.kpi.datingapp.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
 */
@Entity
@Table(name = "profiles")
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

    /** Агрегація: теги — окремий довідник, спільний для багатьох анкет. */
    @ManyToMany
    @JoinTable(name = "profile_keywords",
            joinColumns = @JoinColumn(name = "profile_id"),
            inverseJoinColumns = @JoinColumn(name = "keyword_id"))
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

    public void replaceKeywords(Set<Keyword> newKeywords) {
        this.keywords.clear();
        this.keywords.addAll(newKeywords);
    }

    public int getAge() {
        return Period.between(birthDate, LocalDate.now()).getYears();
    }

    /** Відкрита частина анкети — без жодного закритого поля. */
    public ProfilePublicDTO getPublicInfo() {
        return new ProfilePublicDTO(
                user.getId(),
                firstName,
                getAge(),
                openDescription,
                photoUrls(),
                keywordTags());
    }

    /**
     * Закрита частина анкети. Право доступу (власник або взаємне ACCEPTED)
     * перевіряє ProfileService до виклику цього методу.
     */
    public ProfilePrivateDTO getPrivateInfo() {
        return new ProfilePrivateDTO(closedContactInfo, closedPrivateDetails);
    }

    /** Поля для форми редагування власної анкети. */
    public ProfileData toProfileData() {
        return new ProfileData(firstName, openDescription, openPhotos,
                closedContactInfo, closedPrivateDetails, keywordTags());
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
}

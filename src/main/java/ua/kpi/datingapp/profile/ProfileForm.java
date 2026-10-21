package ua.kpi.datingapp.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Веб-форма анкети з розділами «Відкрита» та «Закрита» інформація. */
public class ProfileForm {

    @NotBlank(message = "Вкажіть ім'я")
    @Size(max = 50, message = "Не більше 50 символів")
    private String firstName;

    @NotBlank(message = "Розкажіть трохи про себе")
    @Size(max = 2000, message = "Не більше 2000 символів")
    private String openDescription;

    @Size(max = 2000, message = "Не більше 2000 символів")
    private String openPhotos;

    @Size(max = 500, message = "Не більше 500 символів")
    private String keywords;

    @Size(max = 1000, message = "Не більше 1000 символів")
    private String closedContactInfo;

    @Size(max = 2000, message = "Не більше 2000 символів")
    private String closedPrivateDetails;

    public static ProfileForm from(ProfileData data) {
        ProfileForm form = new ProfileForm();
        form.firstName = data.firstName();
        form.openDescription = data.openDescription();
        form.openPhotos = data.openPhotos();
        form.keywords = String.join(", ", data.keywords());
        form.closedContactInfo = data.closedContactInfo();
        form.closedPrivateDetails = data.closedPrivateDetails();
        return form;
    }

    public ProfileData toData() {
        return new ProfileData(
                trimToNull(firstName),
                trimToNull(openDescription),
                trimToNull(openPhotos),
                trimToNull(closedContactInfo),
                trimToNull(closedPrivateDetails),
                Keyword.parseTags(keywords));
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getOpenDescription() {
        return openDescription;
    }

    public void setOpenDescription(String openDescription) {
        this.openDescription = openDescription;
    }

    public String getOpenPhotos() {
        return openPhotos;
    }

    public void setOpenPhotos(String openPhotos) {
        this.openPhotos = openPhotos;
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public String getClosedContactInfo() {
        return closedContactInfo;
    }

    public void setClosedContactInfo(String closedContactInfo) {
        this.closedContactInfo = closedContactInfo;
    }

    public String getClosedPrivateDetails() {
        return closedPrivateDetails;
    }

    public void setClosedPrivateDetails(String closedPrivateDetails) {
        this.closedPrivateDetails = closedPrivateDetails;
    }
}

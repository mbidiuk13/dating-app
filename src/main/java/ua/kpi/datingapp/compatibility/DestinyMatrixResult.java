package ua.kpi.datingapp.compatibility;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Результат розрахунку сумісності за матрицею долі (ЛР2, діаграма класів). */
@Entity
@Table(name = "destiny_matrix_results")
public class DestinyMatrixResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "compatibility_percentage", nullable = false)
    private int compatibilityPercentage;

    @Column(name = "compatibility_description", nullable = false, length = 500)
    private String compatibilityDescription;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    protected DestinyMatrixResult() {
        // для JPA
    }

    public DestinyMatrixResult(int compatibilityPercentage, String compatibilityDescription) {
        if (compatibilityPercentage < 0 || compatibilityPercentage > 100) {
            throw new IllegalArgumentException("Відсоток сумісності має бути від 0 до 100");
        }
        this.compatibilityPercentage = compatibilityPercentage;
        this.compatibilityDescription = compatibilityDescription;
        this.calculatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public int getCompatibilityPercentage() {
        return compatibilityPercentage;
    }

    public String getCompatibilityDescription() {
        return compatibilityDescription;
    }

    public LocalDateTime getCalculatedAt() {
        return calculatedAt;
    }
}

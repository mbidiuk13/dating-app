package ua.kpi.datingapp.compatibility;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Спрощений нумерологічний розрахунок для MVP.
 * Кожна дата зводиться до особистого аркана (1–22): сума аркан дня, місяця і року,
 * зведена до діапазону 1–22. Що ближчі аркани партнерів, то вища сумісність (40–100 %).
 * Розрахунок симетричний і детермінований: однакові дати завжди дають однаковий результат.
 */
@Service
public class ArcanaDestinyMatrixService implements DestinyMatrixService {

    static final int MAX_ARCANA = 22;
    static final int MIN_PERCENTAGE = 40;

    @Override
    public DestinyMatrixResult calculateCompatibility(LocalDate date1, LocalDate date2) {
        Objects.requireNonNull(date1, "date1");
        Objects.requireNonNull(date2, "date2");

        int arcana1 = personalArcana(date1);
        int arcana2 = personalArcana(date2);
        int difference = Math.abs(arcana1 - arcana2);

        int percentage = 100 - Math.round(difference * (100f - MIN_PERCENTAGE) / (MAX_ARCANA - 1));
        return new DestinyMatrixResult(percentage, describe(percentage));
    }

    /** Особистий аркан дати народження: 1–22. */
    int personalArcana(LocalDate date) {
        int day = reduce(date.getDayOfMonth());
        int month = reduce(date.getMonthValue());
        int year = reduce(digitSum(date.getYear()));
        return reduce(day + month + year);
    }

    /** Зводить число до діапазону 1–22 через суму цифр. */
    static int reduce(int number) {
        int result = number;
        while (result > MAX_ARCANA) {
            result = digitSum(result);
        }
        return result;
    }

    static int digitSum(int number) {
        int sum = 0;
        int rest = Math.abs(number);
        while (rest > 0) {
            sum += rest % 10;
            rest /= 10;
        }
        return sum;
    }

    /** Короткий опис простими словами, без нумерологічних термінів (критерій US-05). */
    static String describe(int percentage) {
        if (percentage >= 85) {
            return "Дуже висока сумісність: у вас схожі погляди та ритм життя.";
        }
        if (percentage >= 70) {
            return "Висока сумісність: вам буде легко знайти спільну мову.";
        }
        if (percentage >= 55) {
            return "Середня сумісність: відмінності можуть доповнювати одне одного.";
        }
        return "Помірна сумісність: вам знадобиться більше часу, щоб краще зрозуміти одне одного.";
    }
}

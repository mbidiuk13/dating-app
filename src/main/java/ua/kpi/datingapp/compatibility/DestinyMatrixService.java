package ua.kpi.datingapp.compatibility;

import java.time.LocalDate;

/**
 * Сервіс обчислення сумісності (ЛР2, діаграма класів).
 * Інтерфейс дозволяє замінити алгоритм, не змінюючи InvitationService
 * (принципи Open/Closed та Dependency Inversion).
 */
public interface DestinyMatrixService {

    DestinyMatrixResult calculateCompatibility(LocalDate date1, LocalDate date2);
}

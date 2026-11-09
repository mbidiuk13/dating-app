package ua.kpi.datingapp.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProfileRepository extends JpaRepository<Profile, Long> {

    Optional<Profile> findByUserId(Long userId);

    List<Profile> findByUserIdIn(Collection<Long> userIds);

    /**
     * Пошук анкет активних користувачів, що мають хоча б один із тегів (US-03).
     * Повертає лише короткі рядки й кількість спільних тегів; повні анкети
     * завантажуються окремо лише для тих, що будуть показані.
     *
     * @param ageFilter true — застосовано фільтр за віком: анкети з прихованим віком не беруть участі
     */
    @Query("""
            select new ua.kpi.datingapp.profile.SearchMatch(
                p.id, p.firstName, p.birthDate, p.agePrivate, count(k.id))
            from Profile p
            join p.keywords k
            where k.tag in :tags
              and p.user.id <> :excludeUserId
              and p.user.active = true
              and p.birthDate between :bornFrom and :bornTo
              and (:ageFilter = false or p.agePrivate = false)
            group by p.id, p.firstName, p.birthDate, p.agePrivate
            """)
    List<SearchMatch> findMatches(@Param("tags") Collection<String> tags,
                                  @Param("excludeUserId") Long excludeUserId,
                                  @Param("bornFrom") LocalDate bornFrom,
                                  @Param("bornTo") LocalDate bornTo,
                                  @Param("ageFilter") boolean ageFilter);

    /** Анкети для показу разом із власниками одним запитом (без N+1). */
    @Query("select p from Profile p join fetch p.user where p.id in :ids")
    List<Profile> findAllWithUserByIdIn(@Param("ids") Collection<Long> ids);
}

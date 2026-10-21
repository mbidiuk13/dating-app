package ua.kpi.datingapp.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProfileRepository extends JpaRepository<Profile, Long> {

    Optional<Profile> findByUserId(Long userId);

    List<Profile> findByUserIdIn(Collection<Long> userIds);

    /** Пошук анкет активних користувачів, що мають хоча б один із тегів (US-03). */
    @Query("""
            select distinct p from Profile p
            join p.keywords k
            where k.tag in :tags
              and p.user.id <> :excludeUserId
              and p.user.active = true
            """)
    List<Profile> searchByKeywords(@Param("tags") Collection<String> tags,
                                   @Param("excludeUserId") Long excludeUserId);
}

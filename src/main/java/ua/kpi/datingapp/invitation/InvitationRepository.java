package ua.kpi.datingapp.invitation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    /** Усі запрошення між двома користувачами в обох напрямках, найновіші першими. */
    @Query("""
            select i from Invitation i
            where (i.sender.id = :userA and i.receiver.id = :userB)
               or (i.sender.id = :userB and i.receiver.id = :userA)
            order by i.sentAt desc
            """)
    List<Invitation> findAllBetween(@Param("userA") Long userA, @Param("userB") Long userB);

    @Query("""
            select i from Invitation i
            where i.status in :statuses
              and ((i.sender.id = :userA and i.receiver.id = :userB)
                or (i.sender.id = :userB and i.receiver.id = :userA))
            """)
    List<Invitation> findBetweenWithStatuses(@Param("userA") Long userA,
                                             @Param("userB") Long userB,
                                             @Param("statuses") Collection<InvitationStatus> statuses);

    /**
     * Активне запрошення: очікує відповіді або вже прийняте (ЛР2, діаграма послідовностей, крок 4).
     * Поки воно існує, нове запрошення тій самій особі надіслати не можна.
     */
    default List<Invitation> findActiveBetween(Long userA, Long userB) {
        return findBetweenWithStatuses(userA, userB,
                List.of(InvitationStatus.PENDING, InvitationStatus.ACCEPTED));
    }

    default Optional<Invitation> findLatestBetween(Long userA, Long userB) {
        return findAllBetween(userA, userB).stream().findFirst();
    }

    List<Invitation> findBySenderIdOrderBySentAtDesc(Long senderId);

    List<Invitation> findByReceiverIdOrderBySentAtDesc(Long receiverId);

    /** Усі запрошення, де користувач — відправник або отримувач (для видалення облікового запису). */
    @Query("select i from Invitation i where i.sender.id = :userId or i.receiver.id = :userId")
    List<Invitation> findAllInvolving(@Param("userId") Long userId);
}

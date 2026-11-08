package ua.kpi.datingapp.invitation;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import ua.kpi.datingapp.compatibility.DestinyMatrixResult;
import ua.kpi.datingapp.user.User;

import java.time.LocalDateTime;

/**
 * Запрошення (ЛР2, діаграма класів і діаграма станів).
 * Зміна стану можлива лише через методи accept/reject/cancel,
 * які перевіряють дозволені переходи.
 */
@Entity
@Table(name = "invitations", indexes = {
        // НФВ продуктивності: пошук запрошення між двома користувачами та списки «отримані»
        @Index(name = "idx_invitations_sender_receiver", columnList = "sender_id, receiver_id"),
        @Index(name = "idx_invitations_receiver", columnList = "receiver_id")
})
public class Invitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvitationStatus status;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @Column(name = "status_changed_at")
    private LocalDateTime statusChangedAt;

    /** 1 до 0..1: результат з'являється лише при переході в ACCEPTED. */
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "destiny_matrix_result_id", unique = true)
    private DestinyMatrixResult destinyMatrixResult;

    protected Invitation() {
        // для JPA
    }

    public Invitation(User sender, User receiver) {
        this.sender = sender;
        this.receiver = receiver;
        this.status = InvitationStatus.PENDING;
        this.sentAt = LocalDateTime.now();
    }

    /** PENDING → ACCEPTED. Результат сумісності зберігається в тій самій транзакції. */
    public void accept(DestinyMatrixResult result) {
        transitionTo(InvitationStatus.ACCEPTED);
        this.destinyMatrixResult = result;
    }

    /** PENDING → REJECTED. Закриті дані залишаються прихованими. */
    public void reject() {
        transitionTo(InvitationStatus.REJECTED);
    }

    /** PENDING → CANCELLED. Відправник відкликає запрошення до відповіді. */
    public void cancel() {
        transitionTo(InvitationStatus.CANCELLED);
    }

    public boolean isPending() {
        return status == InvitationStatus.PENDING;
    }

    private void transitionTo(InvitationStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvitationException("Запрошення вже оброблено (поточний стан: " + status + ")");
        }
        this.status = target;
        this.statusChangedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public User getSender() {
        return sender;
    }

    public User getReceiver() {
        return receiver;
    }

    public InvitationStatus getStatus() {
        return status;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public LocalDateTime getStatusChangedAt() {
        return statusChangedAt;
    }

    public DestinyMatrixResult getDestinyMatrixResult() {
        return destinyMatrixResult;
    }
}

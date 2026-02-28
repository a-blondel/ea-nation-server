package eu.eahub.repositories;

import eu.eahub.entities.ChannelSubscriptionEntity;
import eu.eahub.enums.GameGenre;
import eu.eahub.enums.SubscriptionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChannelSubscriptionRepository extends JpaRepository<ChannelSubscriptionEntity, Long> {
    Optional<ChannelSubscriptionEntity> findByGuildIdAndSubscriptionTypeAndGameGenre(String guildId, SubscriptionType subscriptionType, GameGenre gameGenre);

    void deleteByGuildIdAndSubscriptionTypeAndGameGenre(String guildId, SubscriptionType subscriptionType, GameGenre gameGenre);

    List<ChannelSubscriptionEntity> findAllBySubscriptionTypeAndGameGenre(SubscriptionType subscriptionType, GameGenre gameGenre);
}

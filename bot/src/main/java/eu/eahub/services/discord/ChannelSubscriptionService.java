package eu.eahub.services.discord;

import eu.eahub.entities.ChannelSubscriptionEntity;
import eu.eahub.enums.GameGenre;
import eu.eahub.enums.SubscriptionType;
import eu.eahub.repositories.ChannelSubscriptionRepository;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class ChannelSubscriptionService {
    private final ChannelSubscriptionRepository repository;
    private final Map<String, List<ChannelSubscriptionEntity>> subscriptionCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void loadCache() {
        // Cache subscriptions by combination of type and genre
        for (SubscriptionType type : SubscriptionType.values()) {
            for (GameGenre genre : GameGenre.values()) {
                String key = getCacheKey(type, genre);
                subscriptionCache.put(key, repository.findAllBySubscriptionTypeAndGameGenre(type, genre));
            }
        }
    }

    @Transactional
    public ChannelSubscriptionEntity subscribe(String guildId, String channelId, SubscriptionType subscriptionType, GameGenre gameGenre) {
        Optional<ChannelSubscriptionEntity> existing = repository.findByGuildIdAndSubscriptionTypeAndGameGenre(guildId, subscriptionType, gameGenre);
        ChannelSubscriptionEntity entity = existing.orElseGet(ChannelSubscriptionEntity::new);
        entity.setGuildId(guildId);
        entity.setChannelId(channelId);
        entity.setSubscriptionType(subscriptionType);
        entity.setGameGenre(gameGenre);
        entity.setUpdatedAt(LocalDateTime.now());
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(LocalDateTime.now());
        }
        ChannelSubscriptionEntity saved = repository.save(entity);

        // Update cache
        String cacheKey = getCacheKey(subscriptionType, gameGenre);
        subscriptionCache.compute(cacheKey, (key, list) -> {
            List<ChannelSubscriptionEntity> filtered = list == null ? List.of() : list.stream()
                    .filter(sub -> !(sub.getGuildId().equals(guildId)))
                    .toList();
            return new java.util.ArrayList<>() {{
                addAll(filtered);
                add(saved);
            }};
        });
        return saved;
    }

    @Transactional
    public void unsubscribe(String guildId, SubscriptionType subscriptionType, GameGenre gameGenre) {
        repository.deleteByGuildIdAndSubscriptionTypeAndGameGenre(guildId, subscriptionType, gameGenre);

        // Update cache
        String cacheKey = getCacheKey(subscriptionType, gameGenre);
        subscriptionCache.computeIfPresent(cacheKey, (key, list) ->
                list.stream().filter(sub -> !sub.getGuildId().equals(guildId)).toList()
        );
    }

    public List<ChannelSubscriptionEntity> getAllByTypeAndGenre(SubscriptionType subscriptionType, GameGenre gameGenre) {
        String cacheKey = getCacheKey(subscriptionType, gameGenre);
        return subscriptionCache.getOrDefault(cacheKey, List.of());
    }

    private String getCacheKey(SubscriptionType type, GameGenre genre) {
        return type.name() + "_" + genre.name();
    }
}

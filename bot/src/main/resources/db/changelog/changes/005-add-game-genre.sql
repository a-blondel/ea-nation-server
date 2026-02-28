-- Add game_genre column to channel_subscription table
ALTER TABLE discord.CHANNEL_SUBSCRIPTION ADD COLUMN game_genre VARCHAR(32);

-- Update existing records to have 'FPS' as game_genre
UPDATE discord.CHANNEL_SUBSCRIPTION SET game_genre = 'FPS';

-- Make game_genre NOT NULL after updating existing records
ALTER TABLE discord.CHANNEL_SUBSCRIPTION ALTER COLUMN game_genre SET NOT NULL;

-- Drop old unique constraint and create new one including game_genre
ALTER TABLE discord.CHANNEL_SUBSCRIPTION DROP CONSTRAINT channel_subscription_guild_id_subscription_type_key;
ALTER TABLE discord.CHANNEL_SUBSCRIPTION ADD CONSTRAINT channel_subscription_guild_id_subscription_type_game_genre_key
    UNIQUE (guild_id, subscription_type, game_genre);

-- Add game_genre column to status_message table
ALTER TABLE discord.STATUS_MESSAGE ADD COLUMN game_genre VARCHAR(32);

-- Update existing records to have 'FPS' as game_genre
UPDATE discord.STATUS_MESSAGE SET game_genre = 'FPS';

-- Make game_genre NOT NULL after updating existing records
ALTER TABLE discord.STATUS_MESSAGE ALTER COLUMN game_genre SET NOT NULL;

-- ============================================================
-- Migration: Refactor game identification
-- Replace raw VERS values with arbitrary PLATFORM_GAMENAME
-- Drop SLUS columns from all tables
-- ============================================================

-- ==================== UPDATE VERS VALUES ====================

-- MOH (MoHH dedicated server VERS merged into parent game)
UPDATE core.game SET vers = 'PSP_MOH07' WHERE vers IN ('PSP/MOH07', 'PSP/MOHGPS071');
UPDATE core.persona_connection SET vers = 'PSP_MOH07' WHERE vers IN ('PSP/MOH07', 'PSP/MOHGPS071');
UPDATE core.user_set SET vers = 'PSP_MOH07' WHERE vers IN ('PSP/MOH07', 'PSP/MOHGPS071');
UPDATE stats.mohh_persona_stats SET vers = 'PSP_MOH07' WHERE vers IN ('PSP/MOH07', 'PSP/MOHGPS071');

UPDATE core.game SET vers = 'PSP_MOH08' WHERE vers = 'PSP/MOH08';
UPDATE core.persona_connection SET vers = 'PSP_MOH08' WHERE vers = 'PSP/MOH08';
UPDATE core.user_set SET vers = 'PSP_MOH08' WHERE vers = 'PSP/MOH08';
UPDATE stats.mohh_persona_stats SET vers = 'PSP_MOH08' WHERE vers = 'PSP/MOH08';

UPDATE core.game SET vers = 'WII_MOH08' WHERE vers = 'WII/MOH08';
UPDATE core.persona_connection SET vers = 'WII_MOH08' WHERE vers = 'WII/MOH08';
UPDATE core.user_set SET vers = 'WII_MOH08' WHERE vers = 'WII/MOH08';
UPDATE stats.mohh_persona_stats SET vers = 'WII_MOH08' WHERE vers = 'WII/MOH08';

-- NFS
UPDATE core.game SET vers = 'PC_NFS06' WHERE vers = '"pc/1.3-Nov 21 2005"';
UPDATE core.persona_connection SET vers = 'PC_NFS06' WHERE vers = '"pc/1.3-Nov 21 2005"';
UPDATE core.user_set SET vers = 'PC_NFS06' WHERE vers = '"pc/1.3-Nov 21 2005"';
UPDATE stats.nfs_persona_stats SET vers = 'PC_NFS06' WHERE vers = '"pc/1.3-Nov 21 2005"';

UPDATE core.game SET vers = 'PS2_NFS06' WHERE vers = '"ps2/1.2-Sep 20 2005"';
UPDATE core.persona_connection SET vers = 'PS2_NFS06' WHERE vers = '"ps2/1.2-Sep 20 2005"';
UPDATE core.user_set SET vers = 'PS2_NFS06' WHERE vers = '"ps2/1.2-Sep 20 2005"';
UPDATE stats.nfs_persona_stats SET vers = 'PS2_NFS06' WHERE vers = '"ps2/1.2-Sep 20 2005"';

UPDATE core.game SET vers = 'PSP_NFS06' WHERE vers = 'PSP/NFS06';
UPDATE core.persona_connection SET vers = 'PSP_NFS06' WHERE vers = 'PSP/NFS06';
UPDATE core.user_set SET vers = 'PSP_NFS06' WHERE vers = 'PSP/NFS06';
UPDATE stats.nfs_persona_stats SET vers = 'PSP_NFS06' WHERE vers = 'PSP/NFS06';

UPDATE core.game SET vers = 'PSP_NFS07' WHERE vers = 'PSP/NFS07';
UPDATE core.persona_connection SET vers = 'PSP_NFS07' WHERE vers = 'PSP/NFS07';
UPDATE core.user_set SET vers = 'PSP_NFS07' WHERE vers = 'PSP/NFS07';
UPDATE stats.nfs_persona_stats SET vers = 'PSP_NFS07' WHERE vers = 'PSP/NFS07';

UPDATE core.game SET vers = 'PSP_NFS08' WHERE vers = 'PSP/NFS08';
UPDATE core.persona_connection SET vers = 'PSP_NFS08' WHERE vers = 'PSP/NFS08';
UPDATE core.user_set SET vers = 'PSP_NFS08' WHERE vers = 'PSP/NFS08';
UPDATE stats.nfs_persona_stats SET vers = 'PSP_NFS08' WHERE vers = 'PSP/NFS08';

UPDATE core.game SET vers = 'PSP_NFS09' WHERE vers = 'PSP/NFS09';
UPDATE core.persona_connection SET vers = 'PSP_NFS09' WHERE vers = 'PSP/NFS09';
UPDATE core.user_set SET vers = 'PSP_NFS09' WHERE vers = 'PSP/NFS09';
UPDATE stats.nfs_persona_stats SET vers = 'PSP_NFS09' WHERE vers = 'PSP/NFS09';

UPDATE core.game SET vers = 'PS2_NFS05' WHERE vers = 'ps2/1.0-Oct 15 2004';
UPDATE core.persona_connection SET vers = 'PS2_NFS05' WHERE vers = 'ps2/1.0-Oct 15 2004';
UPDATE core.user_set SET vers = 'PS2_NFS05' WHERE vers = 'ps2/1.0-Oct 15 2004';
UPDATE stats.nfs_persona_stats SET vers = 'PS2_NFS05' WHERE vers = 'ps2/1.0-Oct 15 2004';

-- NHL
UPDATE core.game SET vers = 'PSP_NHL07' WHERE vers = 'PSP/NHL07';
UPDATE core.persona_connection SET vers = 'PSP_NHL07' WHERE vers = 'PSP/NHL07';
UPDATE core.user_set SET vers = 'PSP_NHL07' WHERE vers = 'PSP/NHL07';
UPDATE stats.nhl_persona_stats SET vers = 'PSP_NHL07' WHERE vers = 'PSP/NHL07';

UPDATE core.game SET vers = 'PS2_NHL08' WHERE vers = 'PS2/NHL08';
UPDATE core.persona_connection SET vers = 'PS2_NHL08' WHERE vers = 'PS2/NHL08';
UPDATE core.user_set SET vers = 'PS2_NHL08' WHERE vers = 'PS2/NHL08';
UPDATE stats.nhl_persona_stats SET vers = 'PS2_NHL08' WHERE vers = 'PS2/NHL08';

UPDATE core.game SET vers = 'PS2_NHL06' WHERE vers = 'PS2/NHL06';
UPDATE core.persona_connection SET vers = 'PS2_NHL06' WHERE vers = 'PS2/NHL06';
UPDATE core.user_set SET vers = 'PS2_NHL06' WHERE vers = 'PS2/NHL06';
UPDATE stats.nhl_persona_stats SET vers = 'PS2_NHL06' WHERE vers = 'PS2/NHL06';

UPDATE core.game SET vers = 'PS2_NHL07' WHERE vers = 'PS2/NHL07';
UPDATE core.persona_connection SET vers = 'PS2_NHL07' WHERE vers = 'PS2/NHL07';
UPDATE core.user_set SET vers = 'PS2_NHL07' WHERE vers = 'PS2/NHL07';
UPDATE stats.nhl_persona_stats SET vers = 'PS2_NHL07' WHERE vers = 'PS2/NHL07';

-- FIFA
UPDATE core.game SET vers = 'PS2_FIFA06' WHERE vers = 'PS2/FIFA06';
UPDATE core.persona_connection SET vers = 'PS2_FIFA06' WHERE vers = 'PS2/FIFA06';
UPDATE core.user_set SET vers = 'PS2_FIFA06' WHERE vers = 'PS2/FIFA06';
UPDATE stats.fifa_persona_stats SET vers = 'PS2_FIFA06' WHERE vers = 'PS2/FIFA06';

UPDATE core.game SET vers = 'PS2_FIFA07' WHERE vers = 'PS2/FIFA07';
UPDATE core.persona_connection SET vers = 'PS2_FIFA07' WHERE vers = 'PS2/FIFA07';
UPDATE core.user_set SET vers = 'PS2_FIFA07' WHERE vers = 'PS2/FIFA07';
UPDATE stats.fifa_persona_stats SET vers = 'PS2_FIFA07' WHERE vers = 'PS2/FIFA07';

UPDATE core.game SET vers = 'PSP_FIFA07' WHERE vers = 'PSP/FIFA07';
UPDATE core.persona_connection SET vers = 'PSP_FIFA07' WHERE vers = 'PSP/FIFA07';
UPDATE core.user_set SET vers = 'PSP_FIFA07' WHERE vers = 'PSP/FIFA07';
UPDATE stats.fifa_persona_stats SET vers = 'PSP_FIFA07' WHERE vers = 'PSP/FIFA07';

UPDATE core.game SET vers = 'PS2_FIFA08' WHERE vers = 'PS2/FIFA08';
UPDATE core.persona_connection SET vers = 'PS2_FIFA08' WHERE vers = 'PS2/FIFA08';
UPDATE core.user_set SET vers = 'PS2_FIFA08' WHERE vers = 'PS2/FIFA08';
UPDATE stats.fifa_persona_stats SET vers = 'PS2_FIFA08' WHERE vers = 'PS2/FIFA08';

UPDATE core.game SET vers = 'PSP_FIFA08' WHERE vers = 'PSP/FIFA08';
UPDATE core.persona_connection SET vers = 'PSP_FIFA08' WHERE vers = 'PSP/FIFA08';
UPDATE core.user_set SET vers = 'PSP_FIFA08' WHERE vers = 'PSP/FIFA08';
UPDATE stats.fifa_persona_stats SET vers = 'PSP_FIFA08' WHERE vers = 'PSP/FIFA08';

UPDATE core.game SET vers = 'PSP_FIFA09' WHERE vers = 'PSP/FIFA09';
UPDATE core.persona_connection SET vers = 'PSP_FIFA09' WHERE vers = 'PSP/FIFA09';
UPDATE core.user_set SET vers = 'PSP_FIFA09' WHERE vers = 'PSP/FIFA09';
UPDATE stats.fifa_persona_stats SET vers = 'PSP_FIFA09' WHERE vers = 'PSP/FIFA09';

UPDATE core.game SET vers = 'PSP_FIFA10' WHERE vers = 'PSP/FIFA10';
UPDATE core.persona_connection SET vers = 'PSP_FIFA10' WHERE vers = 'PSP/FIFA10';
UPDATE core.user_set SET vers = 'PSP_FIFA10' WHERE vers = 'PSP/FIFA10';
UPDATE stats.fifa_persona_stats SET vers = 'PSP_FIFA10' WHERE vers = 'PSP/FIFA10';

UPDATE core.game SET vers = 'PSP_UEFA07' WHERE vers = 'PSP/UEFA07';
UPDATE core.persona_connection SET vers = 'PSP_UEFA07' WHERE vers = 'PSP/UEFA07';
UPDATE core.user_set SET vers = 'PSP_UEFA07' WHERE vers = 'PSP/UEFA07';
UPDATE stats.fifa_persona_stats SET vers = 'PSP_UEFA07' WHERE vers = 'PSP/UEFA07';

UPDATE core.game SET vers = 'PS2_UEFA07' WHERE vers = 'PS2/UEFA07';
UPDATE core.persona_connection SET vers = 'PS2_UEFA07' WHERE vers = 'PS2/UEFA07';
UPDATE core.user_set SET vers = 'PS2_UEFA07' WHERE vers = 'PS2/UEFA07';
UPDATE stats.fifa_persona_stats SET vers = 'PS2_UEFA07' WHERE vers = 'PS2/UEFA07';

UPDATE core.game SET vers = 'PSP_WORLDCUP06' WHERE vers = 'FLM';
UPDATE core.persona_connection SET vers = 'PSP_WORLDCUP06' WHERE vers = 'FLM';
UPDATE core.user_set SET vers = 'PSP_WORLDCUP06' WHERE vers = 'FLM';
UPDATE stats.fifa_persona_stats SET vers = 'PSP_WORLDCUP06' WHERE vers = 'FLM';

UPDATE core.game SET vers = 'PS2_WORLDCUP06' WHERE vers = 'PS2/UEFA06';
UPDATE core.persona_connection SET vers = 'PS2_WORLDCUP06' WHERE vers = 'PS2/UEFA06';
UPDATE core.user_set SET vers = 'PS2_WORLDCUP06' WHERE vers = 'PS2/UEFA06';
UPDATE stats.fifa_persona_stats SET vers = 'PS2_WORLDCUP06' WHERE vers = 'PS2/UEFA06';

UPDATE core.game SET vers = 'PSP_WORLDCUP10' WHERE vers = 'PSP/WORLDCUP10';
UPDATE core.persona_connection SET vers = 'PSP_WORLDCUP10' WHERE vers = 'PSP/WORLDCUP10';
UPDATE core.user_set SET vers = 'PSP_WORLDCUP10' WHERE vers = 'PSP/WORLDCUP10';
UPDATE stats.fifa_persona_stats SET vers = 'PSP_WORLDCUP10' WHERE vers = 'PSP/WORLDCUP10';

-- Other games (no stats tables — only core.game, core.persona_connection, core.user_set)
UPDATE core.game SET vers = 'PSP_KOK06' WHERE vers = 'PSP/KOK06';
UPDATE core.persona_connection SET vers = 'PSP_KOK06' WHERE vers = 'PSP/KOK06';
UPDATE core.user_set SET vers = 'PSP_KOK06' WHERE vers = 'PSP/KOK06';

UPDATE core.game SET vers = 'PS2_MARVEL06' WHERE vers = 'PS2/MARVEL06';
UPDATE core.persona_connection SET vers = 'PS2_MARVEL06' WHERE vers = 'PS2/MARVEL06';
UPDATE core.user_set SET vers = 'PS2_MARVEL06' WHERE vers = 'PS2/MARVEL06';

UPDATE core.game SET vers = 'PSP_MADDEN07' WHERE vers = 'PSP/MADDEN07';
UPDATE core.persona_connection SET vers = 'PSP_MADDEN07' WHERE vers = 'PSP/MADDEN07';
UPDATE core.user_set SET vers = 'PSP_MADDEN07' WHERE vers = 'PSP/MADDEN07';

UPDATE core.game SET vers = 'PSP_MADDEN08' WHERE vers = 'PSP/MADDEN-2008';
UPDATE core.persona_connection SET vers = 'PSP_MADDEN08' WHERE vers = 'PSP/MADDEN-2008';
UPDATE core.user_set SET vers = 'PSP_MADDEN08' WHERE vers = 'PSP/MADDEN-2008';

UPDATE core.game SET vers = 'PSP_MADDEN09' WHERE vers = 'PSP/MADDEN-2009';
UPDATE core.persona_connection SET vers = 'PSP_MADDEN09' WHERE vers = 'PSP/MADDEN-2009';
UPDATE core.user_set SET vers = 'PSP_MADDEN09' WHERE vers = 'PSP/MADDEN-2009';

UPDATE core.game SET vers = 'PSP_MADDEN10' WHERE vers = 'PSP/MADDEN-2010';
UPDATE core.persona_connection SET vers = 'PSP_MADDEN10' WHERE vers = 'PSP/MADDEN-2010';
UPDATE core.user_set SET vers = 'PSP_MADDEN10' WHERE vers = 'PSP/MADDEN-2010';

UPDATE core.game SET vers = 'PSP_NBA06' WHERE vers = 'PSP/NBA06';
UPDATE core.persona_connection SET vers = 'PSP_NBA06' WHERE vers = 'PSP/NBA06';
UPDATE core.user_set SET vers = 'PSP_NBA06' WHERE vers = 'PSP/NBA06';

UPDATE core.game SET vers = 'PSP_NBA07' WHERE vers = 'PSP/NBA07';
UPDATE core.persona_connection SET vers = 'PSP_NBA07' WHERE vers = 'PSP/NBA07';
UPDATE core.user_set SET vers = 'PSP_NBA07' WHERE vers = 'PSP/NBA07';

UPDATE core.game SET vers = 'PSP_NBA08' WHERE vers = 'PSP/NBA08';
UPDATE core.persona_connection SET vers = 'PSP_NBA08' WHERE vers = 'PSP/NBA08';
UPDATE core.user_set SET vers = 'PSP_NBA08' WHERE vers = 'PSP/NBA08';

UPDATE core.game SET vers = 'PS2_NBA08' WHERE vers = 'PS2/NBA08';
UPDATE core.persona_connection SET vers = 'PS2_NBA08' WHERE vers = 'PS2/NBA08';
UPDATE core.user_set SET vers = 'PS2_NBA08' WHERE vers = 'PS2/NBA08';

UPDATE core.game SET vers = 'PSP_NCAA07' WHERE vers = 'PSP/NCAA07';
UPDATE core.persona_connection SET vers = 'PSP_NCAA07' WHERE vers = 'PSP/NCAA07';
UPDATE core.user_set SET vers = 'PSP_NCAA07' WHERE vers = 'PSP/NCAA07';

UPDATE core.game SET vers = 'PSP_TW07' WHERE vers = 'PSP/TW07';
UPDATE core.persona_connection SET vers = 'PSP_TW07' WHERE vers = 'PSP/TW07';
UPDATE core.user_set SET vers = 'PSP_TW07' WHERE vers = 'PSP/TW07';

UPDATE core.game SET vers = 'PSP_TW08' WHERE vers = 'PSP/TW08';
UPDATE core.persona_connection SET vers = 'PSP_TW08' WHERE vers = 'PSP/TW08';
UPDATE core.user_set SET vers = 'PSP_TW08' WHERE vers = 'PSP/TW08';

UPDATE core.game SET vers = 'PSP_TW10' WHERE vers = 'PSP/TEST10';
UPDATE core.persona_connection SET vers = 'PSP_TW10' WHERE vers = 'PSP/TEST10';
UPDATE core.user_set SET vers = 'PSP_TW10' WHERE vers = 'PSP/TEST10';

UPDATE core.game SET vers = 'PS2_NASCAR09' WHERE vers = 'XBOX/ALPHA';
UPDATE core.persona_connection SET vers = 'PS2_NASCAR09' WHERE vers = 'XBOX/ALPHA';
UPDATE core.user_set SET vers = 'PS2_NASCAR09' WHERE vers = 'XBOX/ALPHA';

UPDATE core.game SET vers = 'PS2_MM06' WHERE vers = 'PS2/MM06';
UPDATE core.persona_connection SET vers = 'PS2_MM06' WHERE vers = 'PS2/MM06';
UPDATE core.user_set SET vers = 'PS2_MM06' WHERE vers = 'PS2/MM06';

UPDATE core.game SET vers = 'PS2_MM07' WHERE vers = 'PS2/MM07';
UPDATE core.persona_connection SET vers = 'PS2_MM07' WHERE vers = 'PS2/MM07';
UPDATE core.user_set SET vers = 'PS2_MM07' WHERE vers = 'PS2/MM07';

-- ==================== DROP SLUS COLUMNS ====================
-- Indexes on SLUS columns are auto-dropped with the columns

ALTER TABLE core.game DROP COLUMN slus;
ALTER TABLE core.persona_connection DROP COLUMN slus;
ALTER TABLE stats.mohh_persona_stats DROP COLUMN slus;
ALTER TABLE stats.nfs_persona_stats DROP COLUMN slus;
ALTER TABLE stats.fifa_persona_stats DROP COLUMN slus;
ALTER TABLE stats.nhl_persona_stats DROP COLUMN slus;

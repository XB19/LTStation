-- Vue convertie depuis la base Station d'origine (NozView).
-- SQL Server compare PriceInfos.Code (nvarchar) à tankConfig.OilCode (int) en convertissant
-- le texte en entier : on fait la même conversion explicitement.

CREATE OR REPLACE VIEW noz_view AS
SELECT g.guncode, p.current_ppu, p.new_ppu, p.validate_time,
       n.gun_id, n.statue, n.amount, n.volume, p.code, g.vol_total, n.station_id
FROM gun_config g
JOIN tank_config t ON g.tankcode = t.id
JOIN price_infos p ON CASE WHEN p.code ~ '^\s*-?\d+\s*$' THEN trim(p.code)::integer END = t.oil_code
LEFT JOIN noz_statue n ON g.guncode = n.gun_id;

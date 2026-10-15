-- Vues converties depuis SQL Server (NozRealDate_View, TankView).

CREATE OR REPLACE VIEW noz_real_date_view AS
SELECT e.is_online, e.station_id, e.station_name, e.oil_id, e.noz_id, e.device_no, e.amount, e.volume,
       e.update_time, COALESCE(e.statue, -1) AS statue, e.company_id, e.grade_name, e.tank_no,
       p.new_ppu, p.current_ppu, p.validate_time, e.amount_total, e.volume_total, e.price
FROM (SELECT d.is_online, d.station_id, d.station_name, d.oil_id, d.noz_id, d.device_no, d.amount, d.volume,
             d.update_time, d.statue, d.company_id, g.name AS grade_name, d.amount_total,
             d.volume_total, d.price, d.tank_no
      FROM (SELECT c.is_online, c.station_id, c.station_name, c.oil_id, c.noz_id, c.device_no, c.amount, c.volume,
                   c.update_time, c.statue, s.company_id, c.amount_total, c.volume_total, c.price, c.tank_no
            FROM (SELECT a.is_online, a.station_id, a.station_name, a.oil_id, a.noz_id, a.device_no, a.tank_no,
                         b.amount, b.volume, b.update_time, b.statue, b.amount_total, b.volume_total, b.price
                  FROM (SELECT * FROM air_device WHERE is_run = true) a
                  LEFT JOIN (SELECT station_id, noz_id, device_no, amount, volume, update_time, statue,
                                    amount_total, volume_total, price
                             FROM noz_real_data WHERE device_no <> '') b
                         ON a.station_id = b.station_id AND a.noz_id = b.noz_id AND a.device_no = b.device_no) c
            LEFT JOIN stations s ON c.station_id = s.station_id) d
      LEFT JOIN grade_specifics g ON d.oil_id = g.grade_specific_id) e
LEFT JOIN price_infos p ON e.station_id = p.station_id AND e.oil_id = p.grade_specific_id
UNION
SELECT false AS is_online, a.station_id, ''::varchar AS station_name, b.grade_id AS oil_id, a.noz_id,
       ''::varchar AS device_no, b.current_amount AS amount, b.current_volume AS volume, b.update_time,
       b.statue, a.company_id, b.grade_name, ''::varchar AS tank_no, b.price AS new_ppu, b.price AS current_ppu,
       NULL::timestamp AS validate_time, b.amount_total, b.volume_total, b.price
FROM station_noz a
LEFT JOIN station_noz_statue b ON a.station_id = b.station_id AND a.noz_id = b.noz_id;

CREATE OR REPLACE VIEW tank_view AS
SELECT c.*, d.new_ppu, d.validate_time, d.current_ppu
FROM (SELECT a.id, a.station_id, a.grade_id, a.tank_name, a.total_height, a.total_volume, a.device_no,
             a.company_id, b.current_height, b.current_volume, b.current_water_height, b.current_temp,
             b.update_time, 1 AS air_tank_type
      FROM (SELECT * FROM air_tank WHERE is_run = true) a
      LEFT JOIN tank_real_data b ON a.id = b.tank_id AND a.station_id = b.station_id) c
LEFT JOIN price_infos d ON c.station_id = d.station_id AND c.grade_id = d.grade_specific_id
UNION
SELECT e.*, f.new_ppu, f.validate_time, f.current_ppu
FROM (SELECT a.tank_no AS id, a.station_id, b.grade_id, b.tank_name, b.tank_height AS total_height,
             b.tank_volume AS total_volume, ''::varchar AS device_no, b.company_id, b.current_height,
             b.current_volume, b.current_water_height, b.current_temp, b.update_time, 2 AS air_tank_type
      FROM station_tank a
      LEFT JOIN station_tank_statue b ON a.station_id = b.station_id AND a.tank_no = b.tank_id) e
LEFT JOIN price_infos f ON e.station_id = f.station_id AND e.grade_id = f.grade_specific_id;

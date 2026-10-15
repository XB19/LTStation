"""Données de démonstration (facultatives) pour essayer la plateforme.

Usage : python seed_demo.py          -> crée les données « DÉMO »
        python seed_demo.py --remove -> les supprime

Tout est rattaché à la station « Station Démo Lomé » (code DEMO001) ; rien d'autre n'est modifié.
"""
import random
import sys
from datetime import datetime, timedelta

from migrate_data import load_env, pg_conn

STATION_CODE = "DEMO001"
CUSTOMER_ID = 900000001
EMP_NO = "DEMO0001"


def remove(c):
    row = c.execute("SELECT station_id FROM stations WHERE code = %s", (STATION_CODE,)).fetchone()
    if not row:
        print("Aucune donnée de démonstration")
        return
    sid = row[0]
    for sql in (
        "DELETE FROM oil_transactions WHERE station_id = %s",
        "DELETE FROM account_service_logs WHERE station_id = %s",
        "DELETE FROM online_deposit WHERE station_id = %s",
        "DELETE FROM tank_history WHERE station_id = %s",
        "DELETE FROM air_device WHERE station_id = %s",
        "DELETE FROM air_tank WHERE station_id = %s",
        "DELETE FROM price_infos WHERE station_id = %s",
        "DELETE FROM employee_card_infos WHERE station_id = %s",
        "DELETE FROM customer_card_infos WHERE station_id = %s",
    ):
        c.execute(sql, (sid,))
    c.execute("DELETE FROM customer_accounts WHERE customer_account_id IN (%s, %s)", (CUSTOMER_ID, CUSTOMER_ID + 1))
    c.execute("DELETE FROM customers WHERE customer_id = %s", (CUSTOMER_ID,))
    c.execute("DELETE FROM gprs_account WHERE user_name = 'demo'")
    c.execute("DELETE FROM employees WHERE employee_no = %s", (EMP_NO,))
    c.execute("DELETE FROM stations WHERE station_id = %s", (sid,))
    print("Données de démonstration supprimées")


def seed(c):
    if c.execute("SELECT 1 FROM stations WHERE code = %s", (STATION_CODE,)).fetchone():
        print("Les données de démonstration existent déjà (python seed_demo.py --remove pour les supprimer)")
        return
    city = c.execute("SELECT city_id, company_id FROM cities ORDER BY city_id DESC LIMIT 1").fetchone()
    grade = c.execute("SELECT grade_specific_id, code FROM grade_specifics ORDER BY grade_specific_id LIMIT 1").fetchone()
    sid = c.execute("""INSERT INTO stations (name, code, station_type_id, city_id, address, phone, longitude, latitude,
                       is_active, company_id, station_price, two_year, three_year)
                       VALUES ('Station Démo Lomé', %s, 3, %s, 'Boulevard du 13 Janvier', '22890000000', 1.222, 6.132,
                       0, %s, 0, 0, 0) RETURNING station_id""", (STATION_CODE, city[0], city[1])).fetchone()[0]
    c.execute("""INSERT INTO price_infos (station_id, grade_specific_id, current_ppu, new_ppu, current_density, newdensity,
                 validate_time, company_id) VALUES (%s, %s, 650, 680, 0, 0, now(), %s)""", (sid, grade[0], city[1]))
    tank = c.execute("""INSERT INTO air_tank (station_id, grade_id, is_run, total_height, total_volume, current_height,
                        current_oil, current_temp, tank_name, update_time, electrostatic_grounding, tankleakage,
                        combustible_gas, tankhightemperature, tank_level, company_id)
                        VALUES (%s, %s, true, 2500, 30000, 1650, 19800, 28, 'Cuve 1', now(), 0, 0, 0, 0, 0, %s)
                        RETURNING id""", (sid, grade[0], city[1])).fetchone()[0]
    for noz in (1, 2):
        c.execute("""INSERT INTO air_device (station_id, station_name, oil_id, noz_id, device_no, tank_no, create_time,
                     amount_digits, volume_digits, price_digits, is_run, is_online, connec_time, lng, lat, valid_time,
                     digits_type, company_id) VALUES (%s, 'Station Démo Lomé', %s, %s, %s, %s, now(), 2, 2, 2, true, %s, now(),
                     %s, %s, %s, '664', %s)""",
                  (sid, grade[0], noz, f"DEMO{noz:016d}", str(tank), noz == 1, f"{1.2228 + noz * 0.0004:.6f}", "6.1319",
                   (datetime.now() + timedelta(days=20 if noz == 1 else 400)).strftime("%Y-%m-%d %H:%M:%S"), city[1]))
    emp = c.execute("""INSERT INTO employees (employee_no, name, code, station_id, certificate_type_id, gender_id, address,
                       telephone, memo, code_inner, is_using, score, pc_menu_auth, gzh_auth)
                       VALUES (%s, 'Kossi Démo', 'Kossi Démo', %s, 1, 1, 'demo', '22890000001', 'demo1234', 1, true, 120,
                       '1,2,5,9,10,11,12,13,14,15,16,26', '5,6') RETURNING employee_id""", (EMP_NO, sid)).fetchone()[0]
    c.execute("INSERT INTO gprs_account (user_name, password, role_id, type_id, create_time) VALUES ('demo', 'demo1234', 0, 1, now())")
    c.execute("""INSERT INTO customers (customer_id, name, code, certificate_type_id, certificate_no, telephone, vehicle_no,
                 group_id, issue_status, company_id) VALUES (%s, 'Ama Démo', %s, 1, 'TG-123456', '22890000002', 'TG-4521-AB',
                 100000001, true, %s)""", (CUSTOMER_ID, str(CUSTOMER_ID), city[1]))
    for acc in (CUSTOMER_ID, CUSTOMER_ID + 1):
        c.execute("""INSERT INTO customer_accounts (customer_account_id, account_no, balance, deposit_datetime, book_balance,
                     consume_datetime, load_up_limit, service_fee, update_datetime, status, total_times_daily, total_amount_daily,
                     total_amount_monthly, total_volume_monthly, mileage_lastest, volume_lastest, trans_time,
                     total_times_daily_store, total_amount_daily_store, total_amount_monthly_store)
                     VALUES (%s, %s, 0, now(), 0, now(), 0, 0, now(), '0', 0, 0, 0, 0, 0, 0, now(), 0, 0, 0)""", (acc, str(acc)))
    asn = "01000100009000000001"
    c.execute("""INSERT INTO customer_card_infos (asn, customer_id, customer_account_id, group_id, is_credit_card, credit_lines,
                 account_no, discount, station_id, issue_datetime, valid_datetime, card_status_type_id, limit_type_id,
                 is_limit_vehicle_no, media_type_id, company_id, score, open_accoun_emp_name)
                 VALUES (%s, %s, %s, 100000001, false, 0, %s, 100, %s, now() - interval '90 days', now() + interval '3 years',
                 0, 0, false, 1, %s, 250, 'Kossi Démo')""", (asn, CUSTOMER_ID, CUSTOMER_ID, str(CUSTOMER_ID), sid, city[1]))
    emp_asn = "01000400009000000001"
    c.execute("""INSERT INTO employee_card_infos (asn, employee_id, card_type_id, issue_datetime, valid_datetime,
                 customer_account_id, card_status_type_id, station_id, open_station_id, company_id, open_accoun_emp_name)
                 VALUES (%s, %s, 4, now() - interval '90 days', now() + interval '3 years', %s, 0, %s, %s, %s, 'Kossi Démo')""",
              (emp_asn, emp, CUSTOMER_ID + 1, sid, sid, city[1]))

    random.seed(42)
    balance = 0.0
    now = datetime.now()
    log = """INSERT INTO account_service_logs (transaction_type_id, asn, card_type_id, account_no, process_code,
             card_service_log_id, amount, fee_amount, discount_amount, account_datetime, payment_id, cheque_no, station_id,
             employee_id, terminal_no, trans_datetime, balance, credit_count_id, operator_name)
             VALUES (%s, %s, 1, %s, '', %s, %s, %s, %s, %s, 1, '', %s, %s, '', %s, %s, '', 'Kossi Démo')"""
    for day in range(60, -1, -1):
        base = (now - timedelta(days=day)).replace(hour=7, minute=0, second=0, microsecond=0)
        if day % 10 == 0:
            amt = 50000.0
            balance += amt + 1000
            t = base + timedelta(minutes=5)
            c.execute(log, (2, asn, str(CUSTOMER_ID), 2, amt, 0, 1000, t, sid, emp, t, balance))
            c.execute("""INSERT INTO online_deposit (asn, order_no, amount, is_using, create_time, dis_count_amount, charge_type,
                         operate_name, station_id, company_id) VALUES (%s, %s, %s, 3, %s, 1000, 2, 'Kossi Démo', %s, %s)""",
                      (asn, f"DEMO{t:%Y%m%d%H%M%S}", amt, t, sid, city[1]))
        for k in range(random.randint(4, 9)):
            t = base + timedelta(hours=random.randint(0, 14), minutes=random.randint(0, 59))
            vol = round(random.uniform(5, 45), 2)
            amn = round(vol * 680, 2)
            card = k == 0 and balance > amn
            if card:
                balance -= amn
                c.execute(log, (11, asn, str(CUSTOMER_ID), 11, amn, vol, 0, t, sid, emp, t, balance))
            c.execute("""INSERT INTO oil_transactions (station_id, gun_id, t_type, true_time, asn, grade_specific_id, volume, bal,
                         amn, ppu, employee_id, volume_total, car_number, posttc, ctc, tac, gmac, gtac, psamtid, psamttc,
                         upload, flg, truely_pay, payment_id, device_no, error_flg_id, company_id)
                         VALUES (%s, %s, 0, %s, %s, %s, %s, %s, %s, 680, %s, 0, %s, '00000001', '', '', '', '', '', '', '1', '0',
                         %s, 1, %s, 0, %s)""",
                      (sid, 1 + k % 2, t, asn if card else "00000000000000000000", grade[0], vol, balance if card else 0,
                       amn, emp, "TG-4521-AB" if card else "", amn, f"DEMO{1 + k % 2:016d}", city[1]))
        c.execute("""INSERT INTO tank_history (tank_id, create_time, height, volume, temp, station_id)
                     VALUES (%s, %s, %s, %s, 28, %s)""",
                  (tank, base + timedelta(hours=12), 1500 + random.randint(0, 600), 18000 + random.randint(0, 7000), sid))
    c.execute("UPDATE customer_accounts SET balance = %s WHERE customer_account_id = %s", (round(balance, 3), CUSTOMER_ID))
    print(f"Données de démonstration créées (station {sid}, carte {asn}, employé « demo » / mot de passe « demo1234 »)")


def main():
    with pg_conn(load_env()) as c:
        if "--remove" in sys.argv:
            remove(c)
        else:
            seed(c)


if __name__ == "__main__":
    main()

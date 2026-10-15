package com.ltstation.center.service;

import java.util.Map;

/** Libellés métier traduits (ressources Language.resx de l'original). */
public final class Labels {

    private Labels() {
    }

    /** Type de carte déduit du préfixe de l'ASN (GetCardType). */
    public static String cardType(String asn, Integer tType) {
        if (tType != null && tType == 10) {
            return "Paiement par QR code";
        }
        if (asn == null || asn.length() < 6) {
            return "Sans carte";
        }
        return switch (asn.substring(0, 6)) {
            case "010004" -> "Carte employé";
            case "010001" -> "Carte utilisateur";
            case "010011" -> "Carte de crédit";
            case "010013" -> "Carte secondaire";
            case "010014" -> "Carte principale";
            case "FFFFFF" -> "M1";
            default -> "Sans carte";
        };
    }

    private static final Map<Integer, String> ERRORS = Map.ofEntries(
            Map.entry(-11, "Tout mis à jour"),
            Map.entry(-10, "Mise à jour sans paramètre"),
            Map.entry(-1, "Toutes erreurs"),
            Map.entry(0, "Normal"),
            Map.entry(1, "Carte en liste noire"),
            Map.entry(2, "Carte expirée"),
            Map.entry(4097, "Carte limitée au produit / tentative sur un autre produit"),
            Map.entry(4098, "Carte régionale / tentative hors région"),
            Map.entry(4099, "Carte limitée en nombre de pleins par jour"),
            Map.entry(4100, "Carte limitée en montant par jour"),
            Map.entry(4101, "Carte limitée à une immatriculation"),
            Map.entry(4102, "Mot de passe employé de la carte véhicule incorrect"),
            Map.entry(4103, "Kilométrage limité"),
            Map.entry(4104, "Erreur de capteur"),
            Map.entry(4105, "Erreur de compteur"),
            Map.entry(4114, "Carte limitée en litres par mois"),
            Map.entry(4115, "Autre erreur"),
            Map.entry(8192, "Remplacement de la carte mère ou du clavier"),
            Map.entry(8193, "Modification du prix unitaire (mode normal)"),
            Map.entry(8194, "Modification de la quantité de fermeture de vanne"),
            Map.entry(8195, "Modification de la densité"),
            Map.entry(8196, "Modification de l'heure"),
            Map.entry(8197, "Modification des impulsions"),
            Map.entry(8198, "Modification de la pompe"),
            Map.entry(8199, "Modification du mot de passe (menu mot de passe)"),
            Map.entry(8200, "Modification du mot de passe (paramètres)"),
            Map.entry(8201, "Modification du mot de passe (mode)"),
            Map.entry(8202, "Changement de mode"));

    /** Code d'erreur de transaction (GetErrorType). */
    public static String transactionError(Integer code) {
        return ERRORS.getOrDefault(code == null ? 0 : code, "Erreur non reconnue");
    }

    /** Types d'opération de compte (TransactionTypes / GetTransactionType). */
    public static final String[] TRANSACTION_TYPES = {"", "Ouverture de compte", "Recharge", "Déclaration de perte",
            "Perte du mot de passe", "Déblocage en ligne", "Modification des informations", "Ouverture (remplacement de carte)",
            "Recharge (remplacement de carte)", "Clôture du compte", "Transfert de solde", "Consommation", "Annulation (contre-passation)",
            "Vente hors carburant", "Remboursement", "Transfert après clôture", "Virement carte de gestion",
            "Annulation de transfert", "Recharge inverse carte chauffeur", "Retour carte de gestion", "Échec - retour"};

    public static String transactionType(Object id) {
        int i = id instanceof Number n ? n.intValue() : -1;
        return i > 0 && i < TRANSACTION_TYPES.length ? TRANSACTION_TYPES[i] : "";
    }

    /** États de carte (CardStatusTypes). */
    public static final String[] CARD_STATUS = {"Normale (y compris verrouillée)", "Déclarée perdue", "Perte vérifiée",
            "Carte remplacée", "Pré-clôture", "Clôturée", "Pré-ouverture", "Pré-remplacement", "Suspendue",
            "Échéance de remboursement dépassée", "Plafond de crédit dépassé"};

    public static String cardStatus(Object id) {
        int i = id instanceof Number n ? n.intValue() : -1;
        return i >= 0 && i < CARD_STATUS.length ? CARD_STATUS[i] : "";
    }
}

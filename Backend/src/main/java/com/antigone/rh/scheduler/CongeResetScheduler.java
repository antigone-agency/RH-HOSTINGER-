package com.antigone.rh.scheduler;

import com.antigone.rh.entity.Conge;
import com.antigone.rh.entity.Employe;
import com.antigone.rh.enums.StatutDemande;
import com.antigone.rh.enums.TypeConge;
import com.antigone.rh.enums.TypeReferentiel;
import com.antigone.rh.repository.CongeRepository;
import com.antigone.rh.repository.EmployeRepository;
import com.antigone.rh.repository.ReferentielRepository;
import com.antigone.rh.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;

/**
 * Réinitialisation annuelle du solde de congé au 1er janvier (année civile).
 *
 * Architecture du soldeConge :
 *   - soldeConge = BASE de l'année civile courante (pas le solde courant diminué)
 *   - À la création d'un employé : initialisé aux jours acquis proratisés (sans report)
 *   - Chaque 1er janv. par ce scheduler : report (max MAX_REPORT_CONGE) + droit annuel
 *
 * Règles appliquées le 1er janvier :
 *   1. Calculer les jours consommés l'an passé depuis la base de données
 *   2. reliquat = max(0, soldeConge_N-1 - joursConsommes_N-1)
 *   3. report = min(reliquat, MAX_REPORT_CONGE)  — max 5 jours par défaut
 *   4. Nouveau solde = report + droit annuel de l'année N
 *   5. Notifier chaque employé
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CongeResetScheduler {

    private final EmployeRepository employeRepository;
    private final CongeRepository congeRepository;
    private final ReferentielRepository referentielRepository;
    private final NotificationService notificationService;

    /**
     * Tâche planifiée : chaque 1er janvier à 00h01.
     * Cron : seconde minute heure jour mois jour-semaine
     */
    @Scheduled(cron = "0 1 0 1 1 *")
    @Transactional
    public void reinitialiserSoldesConge() {
        log.info("=== Démarrage réinitialisation annuelle des soldes de congé ({})", LocalDate.now());

        double maxReport  = getRefValue("MAX_REPORT_CONGE",       5.0);
        double droitAn1   = getRefValue("SOLDE_CONGE_AN1",        18.0);
        double droitAn2   = getRefValue("SOLDE_CONGE_AN2_PLUS",   24.0);
        double tauxAn1    = getRefValue("TAUX_MENSUEL_AN1",        1.5);
        double tauxAn2    = getRefValue("TAUX_MENSUEL_AN2_PLUS",   2.0);

        List<Employe> employes = employeRepository.findAll();
        int treated = 0;

        for (Employe employe : employes) {
            try {
                resetSoldeForEmploye(employe, maxReport, droitAn1, droitAn2, tauxAn1, tauxAn2);
                treated++;
            } catch (Exception e) {
                log.error("Erreur reset solde {} {} (id={}) : {}",
                        employe.getNom(), employe.getPrenom(), employe.getId(), e.getMessage(), e);
            }
        }

        log.info("=== Réinitialisation terminée : {}/{} employés traités", treated, employes.size());
    }

    /**
     * Réinitialise le solde d'un employé pour la nouvelle année civile.
     * Peut être appelé manuellement depuis un endpoint admin si nécessaire.
     */
    @Transactional
    public void resetSoldeForEmploye(Employe employe,
                                     double maxReport,
                                     double droitAn1, double droitAn2,
                                     double tauxAn1,  double tauxAn2) {

        LocalDate today = LocalDate.now();
        int annee = today.getYear();

        // ── 1. Droit annuel pour la nouvelle année civile ─────────────────────────────
        double droitAnnuel;
        if (employe.getDateEmbauche() != null) {
            LocalDate jan1 = LocalDate.of(annee, 1, 1);
            int anciennete = Period.between(employe.getDateEmbauche(), jan1).getYears();
            droitAnnuel = anciennete < 1 ? droitAn1 : droitAn2;
        } else {
            droitAnnuel = droitAn2; // valeur par défaut
        }

        // ── 2. Report depuis l'année précédente ───────────────────────────────────────
        // Reliquat = base de l'an passé (soldeConge stocké) - congés consommés l'an passé
        // La base de l'an passé était soit :
        //   · le solde initialisé à la création (jours proratisés)
        //   · le solde mis à jour par ce même scheduler le 1er jan. N-1
        double soldeBaseAnneePrecedente = employe.getSoldeConge() != null ? employe.getSoldeConge() : 0.0;
        double joursConsommesAnneePrecedente = 0;

        if (employe.getDateEmbauche() != null) {
            LocalDate debutPrev = LocalDate.of(annee - 1, 1, 1);
            LocalDate finPrev   = LocalDate.of(annee - 1, 12, 31);
            // Si l'employé a été embauché en cours d'année précédente, ne compter que depuis sa date d'embauche
            if (employe.getDateEmbauche().isAfter(debutPrev)) {
                debutPrev = employe.getDateEmbauche();
            }

            List<Conge> prevApprouves = congeRepository.findByEmployeIdAndTypeCongeAndStatutAndDateDebutBetween(
                    employe.getId(), TypeConge.CONGE_PAYE, StatutDemande.APPROUVEE,
                    debutPrev, finPrev);
            joursConsommesAnneePrecedente = prevApprouves.stream()
                    .mapToDouble(c -> c.getNombreJours() != null ? c.getNombreJours() : 0)
                    .sum();
        }

        double reliquat = Math.max(0, soldeBaseAnneePrecedente - joursConsommesAnneePrecedente);
        double joursReportes = Math.min(reliquat, maxReport);

        // ── 3. Nouveau solde = report + droit annuel ──────────────────────────────────
        double nouveauSolde = joursReportes + droitAnnuel;
        double ancienSoldeBase = employe.getSoldeConge() != null ? employe.getSoldeConge() : 0.0;

        employe.setSoldeConge(nouveauSolde);
        employeRepository.save(employe);

        log.info("Reset {} {} : base_N-1={:.1f}, consommés_N-1={:.1f}, reliquat={:.1f}, report={:.1f}, droitAnnuel={:.0f} → nouveau={:.1f}",
                employe.getNom(), employe.getPrenom(),
                ancienSoldeBase, joursConsommesAnneePrecedente,
                reliquat, joursReportes, droitAnnuel, nouveauSolde);

        // ── 4. Notification à l'employé ───────────────────────────────────────────────
        String message = String.format(
                "Bonne année ! 🎉 Votre solde de congé a été renouvelé au 1er janvier %d.\n" +
                "• Jours reportés de l'année précédente : %.1f jour(s) (max %.0f)\n" +
                "• Droit annuel %d : %.0f jour(s)\n" +
                "• Nouveau solde de base : %.1f jour(s)",
                annee, joursReportes, maxReport, annee, droitAnnuel, nouveauSolde
        );
        notificationService.create(employe,
                "🗓️ Renouvellement du solde de congé " + annee,
                message,
                null);
    }

    private double getRefValue(String libelle, double defaultValue) {
        return referentielRepository
                .findByLibelleAndTypeReferentiel(libelle, TypeReferentiel.PARAMETRE_SYSTEME)
                .map(ref -> {
                    try {
                        return Double.parseDouble(ref.getValeur());
                    } catch (NumberFormatException e) {
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }
}

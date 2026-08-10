package com.ypyit.neoelima.domain.user;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.StudentParentUserEntity;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import com.ypyit.neoelima.domain.user.service.UserService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Se connecter ne touche que sa propre date de connexion.
 *
 * <p>L'ordre de mise à jour portait {@code where (select exists (…)) = true} : une sous-requête
 * <strong>non corrélée</strong>, qui ne référençait aucune colonne de la ligne mise à jour. Elle
 * valait un seul booléen pour tout l'ordre, et dès que le compte existait — donc à chaque connexion
 * réussie — le {@code where} était vrai pour <strong>toutes les lignes</strong> de {@code users}.
 *
 * <p>Ce n'était pas une inélégance. Relevé en production avant correctif : 10 comptes,
 * <strong>une seule valeur distincte</strong> de {@code last_login}. Et cela se voyait à l'écran :
 * « Sécurité » affiche la connexion précédente, lue du claim {@code LAST_LOGIN} que
 * {@code AuthService.generateToken} prend sur l'entité — chaque parent lisait donc la dernière
 * connexion de n'importe qui sur la plateforme. C'était le seul des deux signaux de cet écran censé
 * apprendre quelque chose à qui le lit.
 *
 * <p>Le test s'écrit sur deux comptes parce que le défaut est <em>invisible</em> sur un seul : avec
 * un unique utilisateur en base, l'ancienne requête donnait exactement le bon résultat.
 */
@Transactional
class LastLoginIsPersonalTest extends AbstractIntegrationTest {

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("se connecter ne touche pas la date de connexion des autres comptes")
    void loggingInLeavesEveryoneElseAlone() {
        Instant longAgo = Instant.parse("2020-01-01T08:00:00Z");
        String minePhone = this.aPhone("07");
        String othersPhone = this.aPhone("05");
        UserEntity mine = this.aParentReachableAt(minePhone, longAgo);
        UserEntity theirs = this.aParentReachableAt(othersPhone, longAgo);

        Instant beforeCall = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        this.userService.updateLastLogin(minePhone);
        // L'ordre est natif et modifiant : le contexte de persistance ignore ce qu'il vient de faire.
        this.entityManager.clear();

        assertThat(this.userRepository.findById(mine.getId()).orElseThrow().getLastLogin())
                .as("le compte qui vient de se connecter porte une date fraîche")
                .isAfterOrEqualTo(beforeCall);

        // La vérification qui compte, et la seule que l'ancienne requête échouait.
        assertThat(this.userRepository.findById(theirs.getId()).orElseThrow().getLastLogin())
                .as("un compte tiers ne bouge pas quand quelqu'un d'autre se connecte")
                .isEqualTo(longAgo);
    }

    @Test
    @DisplayName("un identifiant inconnu ne réécrit la date de personne")
    void anUnknownUsernameWritesNothing() {
        Instant longAgo = Instant.parse("2020-01-01T08:00:00Z");
        UserEntity existing = this.aParentReachableAt(this.aPhone("01"), longAgo);

        // Le pire cas de l'ancienne forme : `exists` rendait `false`, donc rien ne bougeait. Mais
        // rien ne garantissait que la nouvelle forme se comporte pareil — un `in` sur un ensemble
        // vide ne met à jour aucune ligne, et c'est ce qu'on fige ici.
        this.userService.updateLastLogin("+2250700000000");
        this.entityManager.clear();

        assertThat(this.userRepository.findById(existing.getId()).orElseThrow().getLastLogin())
                .isEqualTo(longAgo);
    }

    /// Un numéro distinct par test : la base du conteneur est partagée entre les classes.
    private String aPhone(String prefix) {
        return "+225" + prefix + (10_000_000 + Math.abs(UUID.randomUUID().hashCode() % 89_999_999));
    }

    private UserEntity aParentReachableAt(String phone, Instant lastLogin) {
        return this.userRepository.saveAndFlush(StudentParentUserEntity.builder()
                .firstName("Aya").lastName("Traoré")
                .lastLogin(lastLogin)
                .contacts(new HashSet<>(Set.of(ContactEntity.builder()
                        .type(ContactType.PHONE_NUMBER).value(phone)
                        .isPrimary(true).build())))
                .build());
    }
}

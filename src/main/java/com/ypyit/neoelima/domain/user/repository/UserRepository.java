package com.ypyit.neoelima.domain.user.repository;

import com.ypyit.neoelima.domain.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    @Query(value = "select u.* from user_contacts uc " +
            "left join contact c on c.id =  uc.contact_id " +
            "left join users u on u.id =  uc.user_id " +
            "where c.is_primary = true and c.value = :username",
            nativeQuery = true)
    Optional<UserEntity> findByPrimaryContact(@Param("username") String username);

    @Query(value = "select exists (select 1 from user_contacts uc " +
            "left join contact c on c.id =  uc.contact_id " +
            "left join users u on u.id =  uc.user_id " +
            "where c.is_primary = true and c.value = :username)",
            nativeQuery = true)
    boolean existsByPrimaryContact(@Param("username") String username);

    /**
     * Date de connexion de <strong>l'utilisateur nommé</strong>, et de lui seul.
     *
     * <p>La forme précédente demandait {@code where (select exists (…)) = true} : une sous-requête
     * <strong>non corrélée</strong>, qui ne référence aucune colonne de la ligne mise à jour. Elle
     * valait donc un seul booléen pour tout l'ordre — et dès que le compte existait, c'est-à-dire à
     * chaque connexion réussie, le {@code where} était vrai pour <strong>toutes les lignes</strong>.
     *
     * <p>Chaque connexion réécrivait ainsi le {@code last_login} de tout le monde. Relevé en
     * production avant correctif : 10 comptes, <strong>une seule valeur distincte</strong>. La
     * conséquence se voyait à l'écran — « Sécurité » montre la connexion précédente, lue du claim
     * {@code LAST_LOGIN} du jeton, donc chaque parent lisait la dernière connexion de n'importe qui.
     * Accessoirement, l'ordre prenait un verrou par ligne : deux connexions simultanées se
     * sérialisaient sur toute la table {@code users}.
     */
    @Modifying
    @Query(value = "update users set last_login = :lastLogin " +
            "where id in (select uc.user_id from user_contacts uc " +
            "join contact c on c.id = uc.contact_id " +
            "where c.is_primary = true and c.value = :username)", nativeQuery = true)
    void setLastLogin(@Param("lastLogin") Instant lastLogin, @Param("username") String username);
}

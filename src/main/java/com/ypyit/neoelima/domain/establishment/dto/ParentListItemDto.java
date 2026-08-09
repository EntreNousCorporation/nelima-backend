package com.ypyit.neoelima.domain.establishment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ypyit.neoelima.domain.user.dto.ContactDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * Un parent tel qu'il apparaît dans une liste — au portail de son école ou au back-office YPYit.
 *
 * <p>Identité et enfants rattachés, rien de plus pour l'instant : de quoi reconnaître la famille et
 * la joindre. Aucun agrégat financier n'y figure — le reste dû et les échéances se lisent sur la
 * fiche de chaque enfant, et les additionner ici sur une page tronquée donnerait des montants faux.
 *
 * <p>La portée dépend de l'appelant, jamais d'un paramètre : au portail école, seuls les parents
 * ayant un enfant dans l'établissement de l'utilisateur connecté, et seuls ces enfants-là. Au
 * back-office, tous les parents du parc, chaque enfant portant le nom de son établissement.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ParentListItemDto {

    private UUID id;
    private String firstName;
    private String lastName;

    /**
     * Identifiant de connexion du parent : la valeur de son contact principal.
     *
     * <p>Ce n'est pas une colonne — il se dérive du contact marqué principal, comme
     * {@code UserEntity.getUsername()}. Nul quand ce contact manque, ce qui ne concerne que des
     * données héritées abîmées.
     */
    private String username;

    private List<ContactDto> contacts;

    /**
     * Nombre d'enfants rattachés visibles dans la portée courante.
     *
     * <p>Au portail école, il compte les seuls enfants inscrits dans l'établissement de l'appelant ;
     * au back-office, tous les enfants de la famille.
     */
    private long childrenCount;

    @Schema(description = "Les enfants rattachés visibles dans la portée de l'appelant")
    private List<ParentListChildDto> children;

    /**
     * Une ligne enfant dans la liste des parents.
     *
     * <p>Toutes les informations de repli sont facultatives : un élève sans classe, sans niveau ou
     * dont l'établissement a été détaché doit rester visible dans la famille. Le faire disparaître
     * serait la pire réponse à un défaut de saisie de l'école.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParentListChildDto {
        private UUID id;
        private String firstName;
        private String lastName;
        private String registrationNumber;

        /** Classe d'affectation — « CM1 A ». Nulle tant que l'élève n'est pas réparti. */
        private String className;

        /** Niveau, en français — « CM1 ». Nul si le niveau n'a pas été saisi. */
        private String levelLabel;

        /**
         * Établissement de l'enfant. Renseigné pour le back-office, où une famille peut avoir des
         * enfants dans plusieurs écoles ; au portail école, tous les enfants visibles y sont déjà.
         */
        private String establishmentName;
    }
}

package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.domain.establishment.dto.StudentDto;
import com.ypyit.neoelima.domain.user.dto.UserDto;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Efface les coordonnées des tuteurs d'un élève rendu à un parent.
 *
 * <p>Un couple séparé partage un enfant, pas ses coordonnées. Chaque route parent qui sérialise un
 * {@link StudentDto} — {@code /students/mine}, {@code /students/claim}, et la recherche de
 * rattachement {@code GET /students} — expose sinon l'intégralité des {@code ContactDto} des
 * tuteurs, téléphone et courriel compris, et leur contact principal se relit dans {@code username}.
 * L'application n'a besoin que de l'identifiant du tuteur pour savoir si c'est le compte courant ;
 * le reste est tu.
 *
 * <p>La minimisation ne vaut que pour les routes parent : le personnel de l'école a besoin de
 * joindre les familles, et ses routes servent le {@link StudentDto} complet. C'est donc un geste
 * explicite à l'émission, jamais une correspondance globale du mapper.
 */
public final class TutorContactPrivacy {

    private TutorContactPrivacy() {
    }

    public static StudentDto hide(StudentDto student) {
        if (student == null) {
            return null;
        }
        hideAll(List.of(student));
        return student;
    }

    public static List<StudentDto> hideAll(List<StudentDto> students) {
        students.stream()
                .filter(Objects::nonNull)
                .map(StudentDto::getParentUsers)
                .filter(Objects::nonNull)
                .flatMap(Set::stream)
                .forEach(TutorContactPrivacy::stripTutor);
        return students;
    }

    private static void stripTutor(UserDto tutor) {
        tutor.setContacts(null);
        // Le courriel et le téléphone se lisaient aussi dans `username`, qui porte le contact
        // principal : le taire aussi, sinon la minimisation fuit par là.
        tutor.setUsername(null);
    }
}

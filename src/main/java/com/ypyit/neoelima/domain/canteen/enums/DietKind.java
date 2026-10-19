package com.ypyit.neoelima.domain.canteen.enums;

/**
 * Régime ou allergie déclaré au dossier de l'élève.
 *
 * <p>Les allergies sont distinguées des régimes : une allergie se signale à la cuisine comme un
 * danger, un régime comme une préférence. La fiche cuisine les présente séparément.
 */
public enum DietKind {
    NO_PORK(false),
    VEGETARIAN(false),
    NO_GLUTEN(false),
    ALLERGY_PEANUT(true),
    ALLERGY_LACTOSE(true),
    ALLERGY_EGG(true),
    ALLERGY_FISH(true),
    ALLERGY_OTHER(true);

    private final boolean allergy;

    DietKind(boolean allergy) {
        this.allergy = allergy;
    }

    public boolean isAllergy() {
        return this.allergy;
    }
}

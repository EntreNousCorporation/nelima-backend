package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.domain.establishment.dto.CityDto;
import com.ypyit.neoelima.domain.establishment.entity.CityEntity;
import com.ypyit.neoelima.domain.establishment.repository.CityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Référentiel des villes où se situent les écoles.
 *
 * <p>La console ne laisse plus taper une ville, elle la fait choisir ici. Le contrôle est refait au
 * serveur : un choix imposé à l'écran n'engage que l'écran, et la route reste appelable sans lui.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CityService {

    private final CityRepository cityRepository;

    /** Les villes proposées au choix, par ordre alphabétique — le préfixe groupe déjà Abidjan. */
    public List<CityDto> selectable() {
        return this.cityRepository.findByActiveTrueOrderByLabelAsc().stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Refuse une ville qui n'est pas du référentiel.
     *
     * <p>Une ville absente est rejetée, jamais créée en silence : le référentiel n'aurait plus rien
     * d'un référentiel si le premier formulaire venu pouvait l'étendre.
     *
     * <p>Les villes désactivées sont acceptées à l'écriture. Ce sont les saisies libres reprises à
     * la mise en place : une école qui en porte une doit pouvoir corriger son nom ou son téléphone
     * sans être forcée de déménager au passage.
     */
    public void assertKnown(String label) {
        if (Objects.isNull(label) || label.isBlank()) {
            return;
        }
        if (!this.cityRepository.existsByLabel(label)) {
            throw new BadRequestException(String.format("La ville « %s » n'est pas au référentiel.", label));
        }
    }

    private CityDto toDto(CityEntity entity) {
        return CityDto.builder()
                .label(entity.getLabel())
                .region(entity.getRegion())
                .build();
    }
}

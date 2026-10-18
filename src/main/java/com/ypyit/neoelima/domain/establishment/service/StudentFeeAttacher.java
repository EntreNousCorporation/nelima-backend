package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.domain.establishment.entity.FeeEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentFeeEntity;
import com.ypyit.neoelima.domain.establishment.repository.FeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentFeeRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Ce qu'un élève nouvellement inscrit doit : les frais de son niveau, avec leurs tranches.
 *
 * <p>Un seul endroit pour l'inscription unitaire et l'import. L'import en avait sa propre version,
 * qui ne rattachait aucun frais : un élève importé ne devait rien tant qu'un frais n'était pas
 * créé ou redéfini après lui.
 */
@Component
@RequiredArgsConstructor
public class StudentFeeAttacher {

    private final FeeRepository feeRepository;
    private final StudentFeeRepository studentFeeRepository;
    private final InstallmentGenerator installmentGenerator;

    @Transactional
    public void attachFees(StudentEntity student, UUID establishmentId, String levelOfStudyCode) {
        if (StringUtils.isBlank(levelOfStudyCode)) {
            return;
        }
        for (FeeEntity fee : this.feeRepository.findAllByEstablishment_Id(establishmentId)) {
            boolean targetsLevel = fee.getLevelOfStudies().stream()
                    .anyMatch(level -> levelOfStudyCode.equals(level.getCode()));
            if (targetsLevel) {
                // La dette sans ses tranches ne se voit nulle part : le solde, l'échéancier et les
                // relances se lisent tous dans les tranches.
                StudentFeeEntity studentFee = this.studentFeeRepository.saveAndFlush(
                        StudentFeeEntity.builder().student(student).fee(fee).build());
                this.installmentGenerator.generateFor(studentFee);
            }
        }
    }
}

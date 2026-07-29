package com.ypyit.neoelima.domain.payment;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.payment.service.ReceiptNumberAllocator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La numérotation des reçus doit être continue et sans doublon par établissement : c'est une
 * exigence comptable, l'école doit pouvoir justifier la continuité de sa suite.
 *
 * <p>Le cas qui casse une implémentation naïve à base de {@code max(sequence_number) + 1} est
 * deux encaissements simultanés dans la même école — banal en début d'année scolaire. Ces tests
 * exercent donc de vraies transactions concurrentes, pas des appels séquentiels.
 */
class ReceiptNumberAllocatorTest extends AbstractIntegrationTest {

    private static final int CONCURRENT_ALLOCATIONS = 8;

    @Autowired
    private ReceiptNumberAllocator allocator;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("des allocations concurrentes dans la même école ne produisent aucun doublon")
    void concurrentAllocationsAreUnique() throws Exception {
        EstablishmentEntity establishment = newEstablishment("École concurrente");

        List<Long> sequences = allocateConcurrently(establishment, CONCURRENT_ALLOCATIONS);

        assertThat(sequences)
                .as("chaque encaissement doit obtenir son propre numéro")
                .doesNotHaveDuplicates()
                .hasSize(CONCURRENT_ALLOCATIONS);
        assertThat(sequences)
                .as("la suite doit être continue, sans trou")
                .containsExactlyInAnyOrderElementsOf(
                        IntStream.rangeClosed(1, CONCURRENT_ALLOCATIONS)
                                .mapToObj(Long::valueOf).toList());
    }

    @Test
    @DisplayName("chaque établissement a sa propre suite, indépendante des autres")
    void sequencesArePerEstablishment() {
        EstablishmentEntity victorLoba = newEstablishment("Victor Loba " + UUID.randomUUID());
        EstablishmentEntity sainteMarie = newEstablishment("Sainte Marie " + UUID.randomUUID());
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        long firstOfVictorLoba = tx.execute(status -> allocator.allocate(victorLoba));
        long secondOfVictorLoba = tx.execute(status -> allocator.allocate(victorLoba));
        long firstOfSainteMarie = tx.execute(status -> allocator.allocate(sainteMarie));

        assertThat(firstOfVictorLoba).isEqualTo(1L);
        assertThat(secondOfVictorLoba).isEqualTo(2L);
        assertThat(firstOfSainteMarie)
                .as("une nouvelle école repart de 1 quoi qu'il arrive chez les autres")
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("le numéro affiché est daté et cadré sur six chiffres")
    void formatsHumanReadableNumber() {
        assertThat(allocator.format(2026, 42L)).isEqualTo("2026-000042");
    }

    /** Lance n allocations en parallèle, chacune dans sa propre transaction. */
    private List<Long> allocateConcurrently(EstablishmentEntity establishment, int count)
            throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        // Toutes les tâches attendent le même signal, pour qu'elles se disputent réellement le
        // verrou plutôt que de s'exécuter les unes après les autres.
        CountDownLatch startSignal = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(count);
        try {
            List<Callable<Long>> tasks = IntStream.range(0, count)
                    .<Callable<Long>>mapToObj(i -> () -> {
                        startSignal.await();
                        return tx.execute(status -> allocator.allocate(establishment));
                    })
                    .toList();

            List<Future<Long>> futures = tasks.stream().map(pool::submit).toList();
            startSignal.countDown();

            List<Long> results = new java.util.ArrayList<>();
            for (Future<Long> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private EstablishmentEntity newEstablishment(String name) {
        return establishmentRepository.saveAndFlush(
                EstablishmentEntity.builder().name(name).active(true).build());
    }
}

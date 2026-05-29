package se.meditrack.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import se.meditrack.dto.AdjustStockRequest;
import se.meditrack.entity.CareUnit;
import se.meditrack.entity.Medication;
import se.meditrack.entity.StockItem;
import se.meditrack.enums.MedicationForm;
import se.meditrack.enums.MovementReason;
import se.meditrack.repository.CareUnitRepository;
import se.meditrack.repository.MedicationRepository;
import se.meditrack.repository.StockItemRepository;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency-test som bevisar att pessimistic locking pa StockItem
 * faktiskt serialiserar samtidiga adjustStock-anrop. Utan lasning skulle
 * vi fa lost updates - fel saldo. Med PESSIMISTIC_WRITE utfardar MySQL
 * SELECT FOR UPDATE, transaktionerna serialiseras pa rad-niva, och
 * slutsaldot blir korrekt.
 *
 * Monstret: starta StockItem pa 100, kor N parallella tradar som var
 * och en gor adjustStock(-1). Forvanta saldot = 100 - N.
 *
 * Detta ar ett integrationstest - full Spring-kontext, riktig MySQL via
 * Testcontainers. Concurrency-buggar ar inte synliga i enhetstester med
 * mockade repos, eftersom mocken inte simulerar SQL-lasning.
 */
class StockServiceConcurrencyTest extends AbstractIntegrationTest {

    private static final int THREAD_COUNT = 20;
    private static final int STARTING_QUANTITY = 100;

    @Autowired
    private StockService stockService;

    @Autowired
    private StockItemRepository stockItemRepository;

    @Autowired
    private MedicationRepository medicationRepository;

    @Autowired
    private CareUnitRepository careUnitRepository;

    @Test
    void pessimisticLockSerialiserarSamtidigaJusteringar() throws Exception {
        // Setup: skapa egen testdata sa vi inte paverkar seed
        Long stockItemId = createTestStockItem();

        // Concurrency: N tradar gor adjustStock(-1) samtidigt
        ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < THREAD_COUNT; i++) {
            pool.submit(() -> {
                try {
                    startLatch.await();
                    stockService.adjustStock(stockItemId,
                            new AdjustStockRequest(-1, MovementReason.CORRECTION, "concurrency-test"));
                    successes.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean allFinished = doneLatch.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        // Verifiera: saldot ska vara EXAKT 100 - N
        assertThat(allFinished)
                .as("Alla %d tradar skulle vara klara inom 30s", THREAD_COUNT)
                .isTrue();
        assertThat(successes.get())
                .as("Alla %d justeringar skulle lyckas", THREAD_COUNT)
                .isEqualTo(THREAD_COUNT);
        assertThat(failures.get())
                .as("Inga justeringar skulle misslyckas")
                .isZero();

        StockItem finalState = stockItemRepository.findById(stockItemId).orElseThrow();
        assertThat(finalState.getQuantity())
                .as("Saldot ska vara exakt %d (start %d - %d tradar)",
                        STARTING_QUANTITY - THREAD_COUNT, STARTING_QUANTITY, THREAD_COUNT)
                .isEqualTo(STARTING_QUANTITY - THREAD_COUNT);
    }

    /**
     * Skapar testdata under den seedade care-unit 1 (Vardcentralen Norr).
     * Vi anvander seed-care-unit eftersom CurrentUserProvider just nu ar
     * en platshallare som returnerar careUnitId=1. Att skapa egen care-unit
     * skulle gora att tenant-filtrering blockerar testet - service hittar
     * inte stock_itemet under "fel" care-unit.
     *
     * Lakemedel och stock_item ar nyskapade per testkorning - vi paverkar
     * inte seedat lakemedel-data.
     */
    private Long createTestStockItem() {
        CareUnit unit = careUnitRepository.findById(1L).orElseThrow(
                () -> new IllegalStateException(
                        "Seed-data saknas - care_unit id=1 forvantas finnas. " +
                                "Kontrollera att Flyway R__seed.sql kordes."));

        Medication med = new Medication();
        med.setCareUnit(unit);
        med.setName("Concurrency-test-lakemedel");
        med.setAtcCode("TEST00001");
        med.setForm(MedicationForm.TABLET);
        med.setStrength("100 mg");
        med.setUnit("tablett");
        med.setControlledSubstance(false);
        med.setActive(true);
        med = medicationRepository.save(med);

        StockItem stock = new StockItem();
        stock.setCareUnit(unit);
        stock.setMedication(med);
        stock.setQuantity(STARTING_QUANTITY);
        stock.setThreshold(10);
        stock = stockItemRepository.save(stock);

        return stock.getId();
    }
}
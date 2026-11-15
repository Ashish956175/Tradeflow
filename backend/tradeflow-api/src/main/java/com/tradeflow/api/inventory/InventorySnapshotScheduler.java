package com.tradeflow.api.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class InventorySnapshotScheduler {

    private static final Logger logger =
            LoggerFactory.getLogger(InventorySnapshotScheduler.class);

    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of("Asia/Kolkata");

    private final InventorySnapshotService snapshotService;
    private final InventorySnapshotExecutionAuditService auditService;

    public InventorySnapshotScheduler(
            InventorySnapshotService snapshotService,
            InventorySnapshotExecutionAuditService auditService
    ) {
        this.snapshotService = snapshotService;
        this.auditService = auditService;
    }

    @Scheduled(
            cron = "0 59 23 * * *",
            zone = "Asia/Kolkata"
    )
    public void createDailyInventorySnapshots() {

        LocalDate snapshotDate = LocalDate.now(BUSINESS_ZONE);

        Long executionId;

        try {
            executionId = auditService.startExecution(snapshotDate);

        } catch (Exception exception) {
            logger.error(
                    "Could not create inventory snapshot execution audit. Date: {}",
                    snapshotDate,
                    exception
            );
            return;
        }

        int snapshotsCreated;

        try {
            snapshotsCreated =
                    snapshotService.createDailySnapshots(snapshotDate);

        } catch (Exception exception) {

            logger.error(
                    "Daily inventory snapshot failed. Date: {}, execution ID: {}",
                    snapshotDate,
                    executionId,
                    exception
            );

            try {
                auditService.markFailed(
                        executionId,
                        exception.toString()
                );

            } catch (Exception auditException) {
                logger.error(
                        "Could not update failed inventory snapshot execution audit. Execution ID: {}",
                        executionId,
                        auditException
                );
            }

            return;
        }

        try {
            auditService.markSuccessful(
                    executionId,
                    snapshotsCreated
            );

            logger.info(
                    "Daily inventory snapshot completed. Date: {}, execution ID: {}, snapshots created: {}",
                    snapshotDate,
                    executionId,
                    snapshotsCreated
            );

        } catch (Exception auditException) {
            logger.error(
                    "Snapshots were created, but the SUCCESS audit update failed. Date: {}, execution ID: {}. Execution remains RUNNING.",
                    snapshotDate,
                    executionId,
                    auditException
            );
        }
    }
}

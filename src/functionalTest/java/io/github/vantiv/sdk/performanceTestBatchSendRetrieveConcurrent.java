package io.github.vantiv.sdk;

import io.github.vantiv.sdk.generate.CardType;
import io.github.vantiv.sdk.generate.MethodOfPaymentTypeEnum;
import io.github.vantiv.sdk.generate.OrderSourceType;
import io.github.vantiv.sdk.generate.Sale;
import io.github.vantiv.sdk.generate.SaleResponse;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;

/**
 * Performance test for the split send/retrieve concurrent batch workflow that
 * mirrors the structure of {@code performanceTestSDKMultiThreaded}: each thread
 * independently sends and retrieves its own sequence of batch files using the
 * two-step API:
 * <ol>
 *   <li>{@link CnpBatchFileRequest#sendOnlyToCnpSFTPConcurrent()} — send only</li>
 *   <li>{@link CnpBatchFileRequest#retrieveOnlyFromCnpSFTPConcurrent()} — retrieve only</li>
 * </ol>
 *
 * <p>Defaults: 50 threads, 10 batch files per thread, 50 transactions per file.
 *
 * <p>Run from the project root:
 * <pre>
 *   JAVA_HOME=/usr/local/litle-build/jdk1.8-LATEST \
 *   ./gradlew testFunctional \
 *     --tests "io.github.vantiv.sdk.performanceTestBatchSendRetrieveConcurrent"
 * </pre>
 * Or as a standalone main class:
 * <pre>
 *   java -cp &lt;classpath&gt; io.github.vantiv.sdk.performanceTestBatchSendRetrieveConcurrent
 * </pre>
 */
public class performanceTestBatchSendRetrieveConcurrent {

    private List<BatchSendRetrieveThread> testPool = new ArrayList<BatchSendRetrieveThread>();

    static String merchantId = "1288791";

    public static void main(String[] args) throws Exception {
        performanceTestBatchSendRetrieveConcurrent test =
                new performanceTestBatchSendRetrieveConcurrent();
        test.performTest();
    }

    public performanceTestBatchSendRetrieveConcurrent() {
        for (int x = 0; x < 3; x++) {
            BatchSendRetrieveThread t = new BatchSendRetrieveThread(1000 + x);
            testPool.add(t);
        }
    }

    public void performTest() {
        for (BatchSendRetrieveThread t : testPool) {
            t.start();
        }

        // wait for all threads to finish
        boolean allDone = false;
        while (!allDone) {
            int doneCount = 0;
            for (BatchSendRetrieveThread t : testPool) {
                if (!t.isAlive()) {
                    doneCount++;
                }
            }
            if (doneCount == testPool.size()) {
                allDone = true;
            } else {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        System.out.println("All test threads have completed");
    }

    class BatchSendRetrieveThread extends Thread {

        long threadId;
        long requestCount  = 0;
        long successCount  = 0;
        long failedCount   = 0;
        long sendFailCount = 0;
        long retrFailCount = 0;

        /** Number of batch files this thread sends (one per cycle). */
        private static final int FILES_PER_THREAD = 1;

        /** Transactions added to each batch file. */
        private static final int TRANSACTIONS_PER_FILE = 1;

        private Properties config;

        public BatchSendRetrieveThread(long idNumber) {
            threadId = idNumber;
            try {
                FileInputStream fileInputStream = new FileInputStream(
                        (new Configuration()).location());
                config = new Properties();
                config.load(fileInputStream);
                fileInputStream.close();

                String tmpBase = System.getProperty("java.io.tmpdir");
                config.setProperty("batchRequestFolder",
                        tmpBase + File.separator + "cnpPerfSplitConcurrentRequests");
                config.setProperty("batchResponseFolder",
                        tmpBase + File.separator + "cnpPerfSplitConcurrentResponses");
                config.setProperty("printxml", "false");

                new File(config.getProperty("batchRequestFolder")).mkdirs();
                new File(config.getProperty("batchResponseFolder")).mkdirs();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            Random rand = new Random();
            long startTime = System.currentTimeMillis();
            long totalBatchTime = 0;
            for (int n = 0; n < FILES_PER_THREAD; n++) {
                totalBatchTime += doCycle(n);
                try {
                    long sleepTime = rand.nextInt(50);
                    sleep(sleepTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            long duration = System.currentTimeMillis() - startTime;
            System.out.println("Thread " + threadId + " completed."
                    + "  Total Files:" + requestCount
                    + "  Success:" + successCount
                    + "  Failed:" + failedCount
                    + "  SendFailed:" + sendFailCount
                    + "  RetrieveFailed:" + retrFailCount
                    + "  Elapsed Time:" + (duration / 1000) + " secs"
                    + "  Average File Time:" + (requestCount > 0 ? totalBatchTime / requestCount : 0) + " ms");
        }

        /**
         * Sends one batch file via the split send/retrieve API and processes the response.
         *
         * <p>Step 1: {@link CnpBatchFileRequest#sendOnlyToCnpSFTPConcurrent()} — prepares
         * and uploads the file (uses {@link CnpBatchFileRequest#prepareForDeliveryConcurrent()}
         * internally to avoid temp-file collisions).
         * <br>
         * Step 2: {@link CnpBatchFileRequest#retrieveOnlyFromCnpSFTPConcurrent()} — downloads
         * and parses the response file.
         *
         * @param cycleIndex index within this thread's run (used for unique file naming)
         * @return elapsed wall-clock time in milliseconds for this cycle
         */
        private long doCycle(int cycleIndex) {
            requestCount++;
            String fileName = "cnpSdk-perfSplitConcurrent-t" + threadId
                    + "-c" + cycleIndex
                    + "-" + System.currentTimeMillis() + ".xml";

            long startTime = System.currentTimeMillis();
            try {
                Properties props = copyConfig();
                CnpBatchFileRequest batchFile = new CnpBatchFileRequest(fileName, props);
                CnpBatchRequest batch = batchFile.createBatchConcurrent(merchantId);

                for (int i = 0; i < TRANSACTIONS_PER_FILE; i++) {
                    Sale sale = new Sale();
                    sale.setReportGroup("perfSplitGroup");
                    sale.setOrderId("t" + threadId + "-c" + cycleIndex + "-n" + i
                            + "-" + System.currentTimeMillis());
                    sale.setAmount(106L);
                    sale.setOrderSource(OrderSourceType.ECOMMERCE);
                    CardType card = new CardType();
                    card.setType(MethodOfPaymentTypeEnum.VI);
                    card.setNumber("4100000000000002");
                    card.setExpDate("1210");
                    sale.setCard(card);
                    sale.setId("t" + threadId + "c" + cycleIndex + "i" + i);
                    batch.addTransaction(sale);
                }

                // Step 1 – send only
                try {
                    batchFile.sendOnlyToCnpSFTPConcurrent();
                } catch (CnpBatchException e) {
                    sendFailCount++;
                    System.err.println("Thread " + threadId + " cycle " + cycleIndex
                            + " SEND error: " + e.getMessage());
                    failedCount++;
                    return System.currentTimeMillis() - startTime;
                }

                // Step 2 – retrieve only
                try {
                    CnpBatchFileResponse fileResponse = batchFile.retrieveOnlyFromCnpSFTPConcurrent();
                    boolean cycleSuccess = drainResponse(fileResponse);
                    if (cycleSuccess) {
                        successCount++;
                    } else {
                        failedCount++;
                    }
                } catch (CnpBatchException e) {
                    retrFailCount++;
                    failedCount++;
                    System.err.println("Thread " + threadId + " cycle " + cycleIndex
                            + " RETRIEVE error: " + e.getMessage());
                }

            } catch (Exception e) {
                failedCount++;
                System.err.println("Thread " + threadId + " cycle " + cycleIndex
                        + " error: " + e.getMessage());
            }
            return System.currentTimeMillis() - startTime;
        }

        /**
         * Drains the response and returns {@code true} if at least one transaction
         * responded with code {@code "000"} (approved).
         */
        private boolean drainResponse(CnpBatchFileResponse fileResponse) {
            final boolean[] approved = {false};
            CnpResponseProcessor processor = new CnpResponseProcessorAdapter() {
                @Override
                public void processSaleResponse(SaleResponse saleResponse) {
                    if ("000".equals(saleResponse.getResponse())) {
                        approved[0] = true;
                    }
                }
            };
            CnpBatchResponse batchResponse;
            while ((batchResponse = fileResponse.getNextCnpBatchResponse()) != null) {
                while (batchResponse.processNextTransaction(processor)) {
                    // drain
                }
            }
            return approved[0];
        }

        /** Returns a shallow copy of the thread's config so each batch file gets its own instance. */
        private Properties copyConfig() {
            Properties copy = new Properties();
            copy.putAll(config);
            return copy;
        }
    }

    /**
     * No-op adapter for {@link CnpResponseProcessor}.
     * Subclasses override only the callbacks they care about.
     */
    static abstract class CnpResponseProcessorAdapter implements CnpResponseProcessor {

        public void processAuthorizationResponse(io.github.vantiv.sdk.generate.AuthorizationResponse r) {}
        public void processAuthReversalResponse(io.github.vantiv.sdk.generate.AuthReversalResponse r) {}
        public void processCaptureResponse(io.github.vantiv.sdk.generate.CaptureResponse r) {}
        public void processForceCaptureResponse(io.github.vantiv.sdk.generate.ForceCaptureResponse r) {}
        public void processCaptureGivenAuthResponse(io.github.vantiv.sdk.generate.CaptureGivenAuthResponse r) {}
        public void processCreditResponse(io.github.vantiv.sdk.generate.CreditResponse r) {}
        public void processEcheckCreditResponse(io.github.vantiv.sdk.generate.EcheckCreditResponse r) {}
        public void processEcheckPreNoteCreditResponse(io.github.vantiv.sdk.generate.EcheckPreNoteCreditResponse r) {}
        public void processEcheckPreNoteSaleResponse(io.github.vantiv.sdk.generate.EcheckPreNoteSaleResponse r) {}
        public void processEcheckSalesResponse(io.github.vantiv.sdk.generate.EcheckSalesResponse r) {}
        public void processEcheckVerificationResponse(io.github.vantiv.sdk.generate.EcheckVerificationResponse r) {}
        public void processEcheckRedepositResponse(io.github.vantiv.sdk.generate.EcheckRedepositResponse r) {}
        public void processRegisterTokenResponse(io.github.vantiv.sdk.generate.RegisterTokenResponse r) {}
        public void processSaleResponse(SaleResponse r) {}
        public void processUpdateCardValidationNumOnTokenResponse(io.github.vantiv.sdk.generate.UpdateCardValidationNumOnTokenResponse r) {}
        public void processUpdateSubscriptionResponse(io.github.vantiv.sdk.generate.UpdateSubscriptionResponse r) {}
        public void processCancelSubscriptionResponse(io.github.vantiv.sdk.generate.CancelSubscriptionResponse r) {}
        public void processCreatePlanResponse(io.github.vantiv.sdk.generate.CreatePlanResponse r) {}
        public void processUpdatePlanResponse(io.github.vantiv.sdk.generate.UpdatePlanResponse r) {}
        public void processActivateResponse(io.github.vantiv.sdk.generate.ActivateResponse r) {}
        public void processDeactivateResponse(io.github.vantiv.sdk.generate.DeactivateResponse r) {}
        public void processLoadResponse(io.github.vantiv.sdk.generate.LoadResponse r) {}
        public void processUnloadResponse(io.github.vantiv.sdk.generate.UnloadResponse r) {}
        public void processBalanceInquiryResponse(io.github.vantiv.sdk.generate.BalanceInquiryResponse r) {}
        public void processAccountUpdateResponse(io.github.vantiv.sdk.generate.AccountUpdateResponse r) {}
        public void processSubmerchantCreditResponse(io.github.vantiv.sdk.generate.SubmerchantCreditResponse r) {}
        public void processSubmerchantDebitResponse(io.github.vantiv.sdk.generate.SubmerchantDebitResponse r) {}
        public void processPayFacCreditResponse(io.github.vantiv.sdk.generate.PayFacCreditResponse r) {}
        public void processPayFacDebitResponse(io.github.vantiv.sdk.generate.PayFacDebitResponse r) {}
        public void processReserveCreditResponse(io.github.vantiv.sdk.generate.ReserveCreditResponse r) {}
        public void processReserveDebitResponse(io.github.vantiv.sdk.generate.ReserveDebitResponse r) {}
        public void processVendorCreditResponse(io.github.vantiv.sdk.generate.VendorCreditResponse r) {}
        public void processVendorDebitResponse(io.github.vantiv.sdk.generate.VendorDebitResponse r) {}
        public void processPhysicalCheckCreditResponse(io.github.vantiv.sdk.generate.PhysicalCheckCreditResponse r) {}
        public void processPhysicalCheckDebitResponse(io.github.vantiv.sdk.generate.PhysicalCheckDebitResponse r) {}
        public void processFundingInstructionVoidResponse(io.github.vantiv.sdk.generate.FundingInstructionVoidResponse r) {}
        public void processGiftCardAuthReversalResponse(io.github.vantiv.sdk.generate.GiftCardAuthReversalResponse r) {}
        public void processGiftCardCaptureResponse(io.github.vantiv.sdk.generate.GiftCardCaptureResponse r) {}
        public void processGiftCardCreditResponse(io.github.vantiv.sdk.generate.GiftCardCreditResponse r) {}
        public void processFastAccessFundingResponse(io.github.vantiv.sdk.generate.FastAccessFundingResponse r) {}
        public void processTranslateToLowValueTokenResponse(io.github.vantiv.sdk.generate.TranslateToLowValueTokenResponse r) {}
        public void processCustomerCreditResponse(io.github.vantiv.sdk.generate.CustomerCreditResponse r) {}
        public void processCustomerDebitResponse(io.github.vantiv.sdk.generate.CustomerDebitResponse r) {}
        public void processPayoutOrgCreditResponse(io.github.vantiv.sdk.generate.PayoutOrgCreditResponse r) {}
        public void processPayoutOrgDebitResponse(io.github.vantiv.sdk.generate.PayoutOrgDebitResponse r) {}
        public void processDepositTransactionReversalResponse(io.github.vantiv.sdk.generate.DepositTransactionReversalResponse r) {}
        public void processRefundTransactionReversalResponse(io.github.vantiv.sdk.generate.RefundTransactionReversalResponse r) {}
    }
}

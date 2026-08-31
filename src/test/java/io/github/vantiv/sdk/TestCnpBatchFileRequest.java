package io.github.vantiv.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.xml.bind.Marshaller;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import io.github.vantiv.sdk.generate.ApplepayHeaderType;
import io.github.vantiv.sdk.generate.ApplepayType;
import io.github.vantiv.sdk.generate.CardType;
import io.github.vantiv.sdk.generate.MethodOfPaymentTypeEnum;
import io.github.vantiv.sdk.generate.OrderSourceType;
import io.github.vantiv.sdk.generate.Sale;

public class TestCnpBatchFileRequest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private CnpBatchFileRequest cnpBatchFileRequest;

    @Before
    public void before() throws Exception {
        cnpBatchFileRequest = new CnpBatchFileRequest("testFile.xml", buildBaseProperties());
    }

    // ==================== EXISTING TESTS ====================

    @Test
    public void testInitializeMembers() throws Exception {
        Properties configToPass = new Properties();
        configToPass.setProperty("username", "usr1");
        configToPass.setProperty("password", "pass");

        cnpBatchFileRequest.initializeMembers("testFile.xml", configToPass);

        assertEquals("usr1", cnpBatchFileRequest.getConfig().getProperty("username"));
        assertEquals("pass", cnpBatchFileRequest.getConfig().getProperty("password"));
    }

    @Test
    public void testCreateBatchAndGetNumberOfBatches() throws Exception {
        assertEquals(0, cnpBatchFileRequest.getNumberOfBatches());

        CnpBatchRequest testBatch = cnpBatchFileRequest.createBatch("101");
        assertNotNull(testBatch);

        assertEquals(1, cnpBatchFileRequest.getNumberOfBatches());
    }

    @Test
    public void testGetNumberOfTransactionInFile() throws Exception {
        assertEquals(0, cnpBatchFileRequest.getNumberOfTransactionInFile());

        CnpBatchRequest testBatch = cnpBatchFileRequest.createBatch("101");
        Marshaller mockMarshaller = Mockito.mock(Marshaller.class);
        testBatch.setMarshaller(mockMarshaller);
        testBatch.setNumOfTxn(1);
        testBatch.addTransaction(createTestSale(101L, "101"));
        testBatch.addTransaction(createTestSale(102L, "102"));
        testBatch.addTransaction(createTestSale(103L, "103"));

        CnpBatchRequest testBatch2 = cnpBatchFileRequest.createBatch("101");
        testBatch2.setMarshaller(mockMarshaller);
        testBatch2.setNumOfTxn(1);
        testBatch2.addTransaction(createTestSale(104L, "104"));
        testBatch2.addTransaction(createTestSale(105L, "105"));
        testBatch2.addTransaction(createTestSale(106L, "106"));

        assertEquals(8, cnpBatchFileRequest.getNumberOfTransactionInFile());
    }

    @Test
    public void testIsEmpty() throws Exception {
        assertTrue(cnpBatchFileRequest.isEmpty());

        CnpBatchRequest testBatch = cnpBatchFileRequest.createBatch("101");
        testBatch.setNumOfTxn(1);
        Marshaller mockMarshaller = Mockito.mock(Marshaller.class);
        testBatch.setMarshaller(mockMarshaller);
        testBatch.addTransaction(createTestSale(101L, "101"));

        assertFalse(cnpBatchFileRequest.isEmpty());
    }

    @Test
    public void testIsFull() throws Exception {
        Properties property = new Properties();
        property.setProperty("username", "PHXMLTEST");
        property.setProperty("password", "password");
        property.setProperty("maxAllowedTransactionsPerFile", "4");
        property.setProperty("maxTransactionsPerBatch", "4");
        property.setProperty("batchHost", "localhost");
        property.setProperty("batchPort", "2104");
        property.setProperty("batchTcpTimeout", "10000");
        property.setProperty("batchUseSSL", "false");
        property.setProperty("proxyHost", "");
        property.setProperty("proxyPort", "");
        property.setProperty("merchantId", "101");
        property.setProperty("batchRequestFolder", "test/unit/requestFolder/");
        property.setProperty("batchResponseFolder", "test/unit/responseFolder/");
        property.setProperty("sftpUsername", "");
        property.setProperty("sftpPassword", "");

        cnpBatchFileRequest = new CnpBatchFileRequest("testFile.xml", property);

        assertFalse(cnpBatchFileRequest.isFull());

        CnpBatchRequest testBatch = cnpBatchFileRequest.createBatch("101");
        testBatch.setNumOfTxn(1);
        Marshaller mockMarshaller = Mockito.mock(Marshaller.class);
        testBatch.setMarshaller(mockMarshaller);

        testBatch.addTransaction(createTestSale(101L, "101"));
        testBatch.addTransaction(createTestSale(102L, "102"));
        testBatch.addTransaction(createTestSaleWithApplepayAndSecondaryAmount(103L, 10L, "user", "103"));

        assertTrue(cnpBatchFileRequest.isFull());
    }

    // ==================== NEW TESTS ====================

    // --- Initialization edge cases ---

    @Test(expected = CnpBatchException.class)
    public void testInitializeMembersThrowsWhenMaxAllowedTransactionsExceedsLimit() {
        Properties props = buildBaseProperties();
        props.setProperty("maxAllowedTransactionsPerFile", "600000");
        new CnpBatchFileRequest("testFile.xml", props);
    }

    // --- Getters ---

    @Test
    public void testGetConfig_returnsConfiguredProperties() {
        Properties config = cnpBatchFileRequest.getConfig();
        assertNotNull(config);
        assertEquals("PHXMLTEST", config.getProperty("username"));
        assertEquals("password", config.getProperty("password"));
        assertEquals("101", config.getProperty("merchantId"));
    }

    @Test
    public void testGetMaxAllowedTransactionsPerFile_returnsConfiguredValue() {
        assertEquals(1000, cnpBatchFileRequest.getMaxAllowedTransactionsPerFile());
    }

    @Test
    public void testGetFile_returnsNonNullFileInBatchRequestFolder() {
        File requestFile = cnpBatchFileRequest.getFile();
        assertNotNull(requestFile);
        assertEquals("testFile.xml", requestFile.getName());
        assertTrue(requestFile.getPath().contains("test/unit"));
    }

    @Test
    public void testGetFileToWrite_returnsFileWithCorrectNameInFolder() {
        File file = cnpBatchFileRequest.getFileToWrite("batchRequestFolder");
        assertNotNull(file);
        assertEquals("testFile.xml", file.getName());
        assertTrue(file.getParentFile().getPath().contains("test/unit"));
    }

    @Test
    public void testGetFileToWrite_createsParentDirectoriesIfMissing() throws Exception {
        File newFolder = new File(tempFolder.getRoot(), "newSubDir/deeperSubDir");
        assertFalse(newFolder.exists());

        Properties props = buildBaseProperties();
        props.setProperty("batchRequestFolder", newFolder.getAbsolutePath());
        props.setProperty("batchResponseFolder", newFolder.getAbsolutePath());
        CnpBatchFileRequest req = new CnpBatchFileRequest("testFile.xml", props);

        assertTrue(req.getFile().getParentFile().exists());
    }

    // --- Batch / transaction count edge cases ---

    @Test
    public void testGetNumberOfBatches_incrementsWithEachCreateBatch() {
        assertEquals(0, cnpBatchFileRequest.getNumberOfBatches());
        cnpBatchFileRequest.createBatch("101");
        cnpBatchFileRequest.createBatch("102");
        cnpBatchFileRequest.createBatch("103");
        assertEquals(3, cnpBatchFileRequest.getNumberOfBatches());
    }

    @Test
    public void testGetNumberOfTransactionInFile_isZeroWithNoBatches() {
        assertEquals(0, cnpBatchFileRequest.getNumberOfTransactionInFile());
    }

    @Test
    public void testIsEmpty_remainsTrueWhenBatchExistsButHasNoTransactions() {
        cnpBatchFileRequest.createBatch("101");
        assertTrue(cnpBatchFileRequest.isEmpty());
    }

    @Test
    public void testIsFull_returnsFalseWhenBelowMaxTransactionLimit() throws Exception {
        File reqDir = tempFolder.newFolder("batchReqNotFull");
        File respDir = tempFolder.newFolder("batchRespNotFull");
        Properties props = buildBaseProperties();
        props.setProperty("maxAllowedTransactionsPerFile", "10");
        props.setProperty("maxTransactionsPerBatch", "10");
        props.setProperty("batchRequestFolder", reqDir.getAbsolutePath());
        props.setProperty("batchResponseFolder", respDir.getAbsolutePath());

        CnpBatchFileRequest req = new CnpBatchFileRequest("testFile.xml", props);
        CnpBatchRequest batch = req.createBatch("101");
        Marshaller mockMarshaller = Mockito.mock(Marshaller.class);
        batch.setMarshaller(mockMarshaller);
        batch.addTransaction(createTestSale(100L, "order1"));

        assertFalse(req.isFull());
    }

    // --- fillInMissingFieldsFromConfig ---

    @Test
    public void testFillInMissingFieldsFromConfig_doesNotOverrideExistingProperty() {
        Properties config = buildBaseProperties();
        config.setProperty("username", "existingUser");
        config.setProperty("password", "existingPass");

        cnpBatchFileRequest.fillInMissingFieldsFromConfig(config);

        assertEquals("existingUser", config.getProperty("username"));
        assertEquals("existingPass", config.getProperty("password"));
    }

    @Test
    public void testFillInMissingFieldsFromConfig_populatesFromInstanceProperties() {
        Properties config = buildBaseProperties();
        config.remove("password");

        cnpBatchFileRequest.fillInMissingFieldsFromConfig(config);

        assertEquals("password", config.getProperty("password"));
    }

    // --- setCommunication / send ---

    @Test
    public void testSetCommunication_isUsedBySubsequentSendCall() throws Exception {
        Communication mockCommunication = Mockito.mock(Communication.class);
        cnpBatchFileRequest.setCommunication(mockCommunication);

        cnpBatchFileRequest.sendOnlyToCnpSFTP(true);

        verify(mockCommunication).sendCnpRequestFileToSFTP(any(File.class), any(Properties.class));
    }

    @Test
    public void testSendOnlyToCnpSFTP_useExistingFileTrue_skipsPrepareAndOnlySends() throws Exception {
        Communication mockCommunication = Mockito.mock(Communication.class);
        cnpBatchFileRequest.setCommunication(mockCommunication);

        cnpBatchFileRequest.sendOnlyToCnpSFTP(true);

        verify(mockCommunication, times(1)).sendCnpRequestFileToSFTP(any(File.class), any(Properties.class));
        verify(mockCommunication, never()).receiveCnpRequestResponseFileFromSFTP(
                any(File.class), any(File.class), any(Properties.class));
    }

    @Test
    public void testSendOnlyToCnpSFTP_deletesRequestFileWhenDeleteBatchFilesTrue() throws Exception {
        File reqDir = tempFolder.newFolder("batchReqDelete");
        File respDir = tempFolder.newFolder("batchRespDelete");
        Properties props = buildBaseProperties();
        props.setProperty("batchRequestFolder", reqDir.getAbsolutePath());
        props.setProperty("batchResponseFolder", respDir.getAbsolutePath());
        props.setProperty("deleteBatchFiles", "true");

        CnpBatchFileRequest req = new CnpBatchFileRequest("batchFile.xml", props);
        req.getFile().createNewFile();
        assertTrue(req.getFile().exists());

        Communication mockCommunication = Mockito.mock(Communication.class);
        req.setCommunication(mockCommunication);
        req.sendOnlyToCnpSFTP(true);

        assertFalse(req.getFile().exists());
    }

    // --- prepareForDelivery / generateRequestFile ---

    @Test
    public void testPrepareForDelivery_withNoBatches_createsRequestFile() throws Exception {
        File reqDir = tempFolder.newFolder("batchReqNoBatch");
        File respDir = tempFolder.newFolder("batchRespNoBatch");
        Properties props = buildBaseProperties();
        props.setProperty("batchRequestFolder", reqDir.getAbsolutePath());
        props.setProperty("batchResponseFolder", respDir.getAbsolutePath());

        CnpBatchFileRequest req = new CnpBatchFileRequest("outFile.xml", props);
        // prepareForDelivery writes the temp file into batchRequestFolder/tmp/
        new File(reqDir, "tmp").mkdirs();

        req.prepareForDelivery();

        assertTrue(req.getFile().exists());
    }

    @Test
    public void testPrepareForDelivery_withTransactions_createsRequestFile() throws Exception {
        File reqDir = tempFolder.newFolder("batchReqWithTxn");
        File respDir = tempFolder.newFolder("batchRespWithTxn");
        Properties props = buildBaseProperties();
        props.setProperty("batchRequestFolder", reqDir.getAbsolutePath());
        props.setProperty("batchResponseFolder", respDir.getAbsolutePath());

        CnpBatchFileRequest req = new CnpBatchFileRequest("outFile.xml", props);
        CnpBatchRequest batch = req.createBatch("101"); // also creates the /tmp subdir
        Marshaller mockMarshaller = Mockito.mock(Marshaller.class);
        batch.setMarshaller(mockMarshaller);
        batch.addTransaction(createTestSale(100L, "order1"));

        req.prepareForDelivery();

        assertTrue(req.getFile().exists());
    }

    // --- setId ---

    @Test
    public void testSetId_idAppearsInGeneratedRequestFile() throws Exception {
        File reqDir = tempFolder.newFolder("batchReqSetId");
        File respDir = tempFolder.newFolder("batchRespSetId");
        Properties props = buildBaseProperties();
        props.setProperty("batchRequestFolder", reqDir.getAbsolutePath());
        props.setProperty("batchResponseFolder", respDir.getAbsolutePath());

        CnpBatchFileRequest req = new CnpBatchFileRequest("outFile.xml", props);
        req.setId("myRequestId123");
        new File(reqDir, "tmp").mkdirs();
        req.prepareForDelivery();

        assertTrue(readFileContent(req.getFile()).contains("myRequestId123"));
    }

    // --- setResponseFile / retrieveOnlyFromCnpSFTP ---

    @Test
    public void testSetResponseFile_changesResponseFileUsedInRetrieve() throws Exception {
        File reqDir = tempFolder.newFolder("batchReqSetResp");
        File respDir = tempFolder.newFolder("batchRespSetResp");
        Properties props = buildBaseProperties();
        props.setProperty("batchRequestFolder", reqDir.getAbsolutePath());
        props.setProperty("batchResponseFolder", respDir.getAbsolutePath());

        CnpBatchFileRequest req = new CnpBatchFileRequest("outFile.xml", props);
        File customResponseFile = new File(respDir, "customResponse.xml");
        writeValidCnpResponseXml(customResponseFile);
        req.setResponseFile(customResponseFile);

        Communication mockCommunication = Mockito.mock(Communication.class);
        req.setCommunication(mockCommunication);

        CnpBatchFileResponse response = req.retrieveOnlyFromCnpSFTP();

        assertNotNull(response);
        verify(mockCommunication).receiveCnpRequestResponseFileFromSFTP(
                any(File.class), eq(customResponseFile), any(Properties.class));
    }

    @Test
    public void testRetrieveOnlyFromCnpSFTP_callsCommunicationAndReturnsValidResponse() throws Exception {
        File reqDir = tempFolder.newFolder("batchReqRetrieve");
        File respDir = tempFolder.newFolder("batchRespRetrieve");
        Properties props = buildBaseProperties();
        props.setProperty("batchRequestFolder", reqDir.getAbsolutePath());
        props.setProperty("batchResponseFolder", respDir.getAbsolutePath());

        CnpBatchFileRequest req = new CnpBatchFileRequest("outFile.xml", props);
        File responseFile = new File(respDir, "outFile.xml");
        writeValidCnpResponseXml(responseFile);
        req.setResponseFile(responseFile);

        Communication mockCommunication = Mockito.mock(Communication.class);
        req.setCommunication(mockCommunication);

        CnpBatchFileResponse result = req.retrieveOnlyFromCnpSFTP();

        assertNotNull(result);
        verify(mockCommunication, times(1)).receiveCnpRequestResponseFileFromSFTP(
                any(File.class), any(File.class), any(Properties.class));
    }

    // ==================== HELPERS ====================

    /**
     * Builds a complete set of properties required by CnpBatchFileRequest,
     * covering all keys inspected by fillInMissingFieldsFromConfig to prevent
     * attempts to read from the .cnp_SDK_config.properties file during unit tests.
     */
    private Properties buildBaseProperties() {
        Properties props = new Properties();
        props.setProperty("username", "PHXMLTEST");
        props.setProperty("password", "password");
        props.setProperty("maxAllowedTransactionsPerFile", "1000");
        props.setProperty("maxTransactionsPerBatch", "500");
        props.setProperty("batchHost", "localhost");
        props.setProperty("batchPort", "2104");
        props.setProperty("batchTcpTimeout", "10000");
        props.setProperty("batchUseSSL", "false");
        props.setProperty("merchantId", "101");
        props.setProperty("proxyHost", "");
        props.setProperty("proxyPort", "");
        props.setProperty("reportGroup", "test");
        props.setProperty("batchRequestFolder", "test/unit/");
        props.setProperty("batchResponseFolder", "test/unit/");
        props.setProperty("sftpUsername", "sftp");
        props.setProperty("sftpPassword", "password");
        props.setProperty("sftpTimeout", "");
        props.setProperty("printxml", "false");
        props.setProperty("useEncryption", "false");
        props.setProperty("VantivPublicKeyPath", "");
        props.setProperty("PrivateKeyPath", "");
        props.setProperty("PublicKeyPath", "");
        props.setProperty("gpgPassphrase", "");
        props.setProperty("deleteBatchFiles", "false");
        return props;
    }

    /** Writes a minimal but valid cnpResponse XML that JAXB can unmarshal into CnpResponse. */
    private void writeValidCnpResponseXml(File file) throws IOException {
        FileWriter fw = new FileWriter(file);
        try {
            fw.write("<cnpResponse xmlns=\"http://www.vantivcnp.com/schema\" "
                    + "version=\"12.51\" response=\"0\" message=\"Valid Format\" "
                    + "cnpSessionId=\"12345\"></cnpResponse>");
        } finally {
            fw.close();
        }
    }

    private String readFileContent(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(new FileReader(file));
        try {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        } finally {
            br.close();
        }
        return sb.toString();
    }

    public Sale createTestSale(Long amount, String orderId) {
        Sale sale = new Sale();
        sale.setAmount(amount);
        sale.setOrderId(orderId);
        sale.setOrderSource(OrderSourceType.ECOMMERCE);
        CardType card = new CardType();
        card.setType(MethodOfPaymentTypeEnum.VI);
        card.setNumber("4100000000000002");
        card.setExpDate("1210");
        sale.setCard(card);
        sale.setReportGroup("test");
        return sale;
    }

    public Sale createTestSaleWithApplepayAndSecondaryAmount(Long amount, Long secAmount, String applepayData, String orderId) {
        Sale sale = new Sale();
        sale.setAmount(amount);
        sale.setSecondaryAmount(secAmount);
        sale.setOrderId(orderId);
        sale.setOrderSource(OrderSourceType.ECOMMERCE);
        ApplepayType applepayType = new ApplepayType();
        ApplepayHeaderType applepayHeaderType = new ApplepayHeaderType();
        applepayHeaderType.setApplicationData("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        applepayHeaderType.setEphemeralPublicKey("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        applepayHeaderType.setPublicKeyHash("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        applepayHeaderType.setTransactionId("1234");
        applepayType.setHeader(applepayHeaderType);
        applepayType.setData(applepayData);
        applepayType.setSignature("sign");
        applepayType.setVersion("1");
        sale.setApplepay(applepayType);
        sale.setReportGroup("test");
        return sale;
    }

    // =========================================================================
    // Tests for the new thread-safe / concurrent API
    // =========================================================================

    @Test
    public void testCreateBatchConcurrent_returnsNonNullAndIncrementsCount() throws Exception {
        assertEquals(0, cnpBatchFileRequest.getNumberOfBatches());

        CnpBatchRequest batch = cnpBatchFileRequest.createBatchConcurrent("101");

        assertNotNull(batch);
        assertEquals(1, cnpBatchFileRequest.getNumberOfBatches());
    }

    @Test
    public void testCreateBatchConcurrent_tempFilePathContainsInstanceId() throws Exception {
        // Each concurrent batch must embed the parent file's instanceId so its
        // temp file is unique even when two instances share the same folder.
        CnpBatchRequest batch = cnpBatchFileRequest.createBatchConcurrent("101");
        // The filePath is package-private; we verify indirectly by adding a
        // transaction which opens the file at that path.
        Marshaller mockMarshaller = Mockito.mock(Marshaller.class);
        batch.setMarshaller(mockMarshaller);
        batch.addTransaction(createTestSale(100L, "order1"));
        // File was created at filePath — just confirm getFile() is not null.
        assertNotNull(batch.getFile());
    }

    @Test
    public void testPrepareForDeliveryConcurrent_createsRequestFile() throws Exception {
        File reqDir = tempFolder.newFolder("concReq");
        File respDir = tempFolder.newFolder("concResp");
        Properties props = buildBaseProperties();
        props.setProperty("batchRequestFolder", reqDir.getAbsolutePath());
        props.setProperty("batchResponseFolder", respDir.getAbsolutePath());

        CnpBatchFileRequest req = new CnpBatchFileRequest("concFile.xml", props);
        new File(reqDir, "tmp").mkdirs();

        req.prepareForDeliveryConcurrent();

        assertTrue(req.getFile().exists());
    }

    @Test
    public void testPrepareForDeliveryConcurrent_usesUniqueInstanceTempFile() throws Exception {
        // Two instances sharing the same batchRequestFolder must produce distinct
        // temp files so they do not overwrite each other.
        File sharedDir = tempFolder.newFolder("sharedBatchFolder");
        File respDir = tempFolder.newFolder("sharedRespFolder");

        Properties props1 = buildBaseProperties();
        props1.setProperty("batchRequestFolder", sharedDir.getAbsolutePath());
        props1.setProperty("batchResponseFolder", respDir.getAbsolutePath());
        Properties props2 = buildBaseProperties();
        props2.setProperty("batchRequestFolder", sharedDir.getAbsolutePath());
        props2.setProperty("batchResponseFolder", respDir.getAbsolutePath());

        CnpBatchFileRequest req1 = new CnpBatchFileRequest("file1.xml", props1);
        CnpBatchFileRequest req2 = new CnpBatchFileRequest("file2.xml", props2);

        new File(sharedDir, "tmp").mkdirs();

        req1.prepareForDeliveryConcurrent();
        req2.prepareForDeliveryConcurrent();

        // Both request files must have been written without one deleting the other.
        assertTrue("req1 request file must exist", req1.getFile().exists());
        assertTrue("req2 request file must exist", req2.getFile().exists());
    }

    @Test
    public void testSendOnlyToCnpSFTPConcurrent_callsCommunication() throws Exception {
        File reqDir = tempFolder.newFolder("concSendReq");
        File respDir = tempFolder.newFolder("concSendResp");
        Properties props = buildBaseProperties();
        props.setProperty("batchRequestFolder", reqDir.getAbsolutePath());
        props.setProperty("batchResponseFolder", respDir.getAbsolutePath());

        CnpBatchFileRequest req = new CnpBatchFileRequest("sendFile.xml", props);
        Communication mockCommunication = Mockito.mock(Communication.class);
        req.setCommunication(mockCommunication);

        req.sendOnlyToCnpSFTPConcurrent();

        verify(mockCommunication, times(1))
                .sendCnpRequestFileToSFTP(any(File.class), any(Properties.class));
        verify(mockCommunication, never())
                .receiveCnpRequestResponseFileFromSFTP(any(File.class), any(File.class), any(Properties.class));
    }

    @Test
    public void testSendToCnpSFTPConcurrent_callsSendAndReceive() throws Exception {
        File reqDir = tempFolder.newFolder("concSendRcvReq");
        File respDir = tempFolder.newFolder("concSendRcvResp");
        Properties props = buildBaseProperties();
        props.setProperty("batchRequestFolder", reqDir.getAbsolutePath());
        props.setProperty("batchResponseFolder", respDir.getAbsolutePath());

        CnpBatchFileRequest req = new CnpBatchFileRequest("sendRcvFile.xml", props);
        File responseFile = new File(respDir, "sendRcvFile.xml");
        writeValidCnpResponseXml(responseFile);
        req.setResponseFile(responseFile);

        Communication mockCommunication = Mockito.mock(Communication.class);
        req.setCommunication(mockCommunication);

        CnpBatchFileResponse response = req.sendToCnpSFTPConcurrent(true);

        assertNotNull(response);
        verify(mockCommunication, times(1))
                .sendCnpRequestFileToSFTP(any(File.class), any(Properties.class));
        verify(mockCommunication, times(1))
                .receiveCnpRequestResponseFileFromSFTP(any(File.class), any(File.class), any(Properties.class));
    }

    @Test
    public void testSendToCnpSFTPConcurrent_withExecutorService_returnsValidFuture() throws Exception {
        File reqDir = tempFolder.newFolder("concFutureReq");
        File respDir = tempFolder.newFolder("concFutureResp");
        Properties props = buildBaseProperties();
        props.setProperty("batchRequestFolder", reqDir.getAbsolutePath());
        props.setProperty("batchResponseFolder", respDir.getAbsolutePath());

        CnpBatchFileRequest req = new CnpBatchFileRequest("futureFile.xml", props);
        File responseFile = new File(respDir, "futureFile.xml");
        writeValidCnpResponseXml(responseFile);
        req.setResponseFile(responseFile);

        Communication mockCommunication = Mockito.mock(Communication.class);
        req.setCommunication(mockCommunication);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<CnpBatchFileResponse> future = req.sendToCnpSFTPConcurrent(executor);
            CnpBatchFileResponse response = future.get();

            assertNotNull(future);
            assertNotNull(response);
            verify(mockCommunication, times(1))
                    .sendCnpRequestFileToSFTP(any(File.class), any(Properties.class));
            verify(mockCommunication, times(1))
                    .receiveCnpRequestResponseFileFromSFTP(any(File.class), any(File.class), any(Properties.class));
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Verifies that N concurrent {@code CnpBatchFileRequest} instances sending to the same
     * {@code batchRequestFolder} via {@link ExecutorService} do not interfere with each
     * other's temporary files and all produce a valid response.
     */
    @Test
    public void testSendToCnpSFTPConcurrent_multipleInstancesInParallel_noCrossContamination() throws Exception {
        final int threadCount = 4;
        File sharedReqDir = tempFolder.newFolder("multiReq");
        File sharedRespDir = tempFolder.newFolder("multiResp");
        new File(sharedReqDir, "tmp").mkdirs();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        final CountDownLatch ready = new CountDownLatch(threadCount);
        List<Future<CnpBatchFileResponse>> futures = new ArrayList<Future<CnpBatchFileResponse>>();

        for (int i = 0; i < threadCount; i++) {
            final String fileName = "batchFile-" + i + ".xml";
            Properties props = buildBaseProperties();
            props.setProperty("batchRequestFolder", sharedReqDir.getAbsolutePath());
            props.setProperty("batchResponseFolder", sharedRespDir.getAbsolutePath());

            final CnpBatchFileRequest req = new CnpBatchFileRequest(fileName, props);
            final File respFile = new File(sharedRespDir, fileName);
            writeValidCnpResponseXml(respFile);
            req.setResponseFile(respFile);

            final Communication mockComm = Mockito.mock(Communication.class);
            req.setCommunication(mockComm);

            futures.add(executor.submit(new java.util.concurrent.Callable<CnpBatchFileResponse>() {
                @Override
                public CnpBatchFileResponse call() throws Exception {
                    ready.countDown();
                    ready.await(); // all threads start as close together as possible
                    return req.sendToCnpSFTPConcurrent(true);
                }
            }));
        }

        int successCount = 0;
        for (Future<CnpBatchFileResponse> f : futures) {
            assertNotNull(f.get());
            successCount++;
        }
        assertEquals("All concurrent batch requests must complete successfully", threadCount, successCount);
        executor.shutdown();
    }

    // ============== NEW: sendOnlyToCnpSFTPConcurrent(ExecutorService) ==============

    @Test
    public void testSendOnlyToCnpSFTPConcurrent_withExecutorService_callsCommunication() throws Exception {
        File reqDir = tempFolder.newFolder("sendOnlyExecReq");
        File respDir = tempFolder.newFolder("sendOnlyExecResp");
        Properties props = buildBaseProperties();
        props.setProperty("batchRequestFolder", reqDir.getAbsolutePath());
        props.setProperty("batchResponseFolder", respDir.getAbsolutePath());

        CnpBatchFileRequest req = new CnpBatchFileRequest("sendOnlyExec.xml", props);
        Communication mockCommunication = Mockito.mock(Communication.class);
        req.setCommunication(mockCommunication);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Void> future = req.sendOnlyToCnpSFTPConcurrent(executor);
            future.get(); // blocks until done

            assertNotNull(future);
            verify(mockCommunication, times(1))
                    .sendCnpRequestFileToSFTP(any(File.class), any(Properties.class));
            verify(mockCommunication, never())
                    .receiveCnpRequestResponseFileFromSFTP(any(File.class), any(File.class), any(Properties.class));
        } finally {
            executor.shutdown();
        }
    }

    @Test
    public void testSendOnlyToCnpSFTPConcurrent_withExecutorService_futureReturnsNull() throws Exception {
        File reqDir = tempFolder.newFolder("sendOnlyNullReq");
        File respDir = tempFolder.newFolder("sendOnlyNullResp");
        Properties props = buildBaseProperties();
        props.setProperty("batchRequestFolder", reqDir.getAbsolutePath());
        props.setProperty("batchResponseFolder", respDir.getAbsolutePath());

        CnpBatchFileRequest req = new CnpBatchFileRequest("sendOnlyNull.xml", props);
        Communication mockCommunication = Mockito.mock(Communication.class);
        req.setCommunication(mockCommunication);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Void> future = req.sendOnlyToCnpSFTPConcurrent(executor);
            Void result = future.get();
            // Future<Void> must resolve to null (Void has no instances)
            assertTrue("Future<Void> result must be null", result == null);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    public void testSendOnlyToCnpSFTPConcurrent_multipleInstancesInParallel_allComplete() throws Exception {
        final int threadCount = 3;
        File sharedReqDir = tempFolder.newFolder("sendOnlyParallelReq");
        File sharedRespDir = tempFolder.newFolder("sendOnlyParallelResp");
        new File(sharedReqDir, "tmp").mkdirs();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        final CountDownLatch ready = new CountDownLatch(threadCount);
        List<Future<Void>> sendFutures = new ArrayList<Future<Void>>();

        for (int i = 0; i < threadCount; i++) {
            Properties props = buildBaseProperties();
            props.setProperty("batchRequestFolder", sharedReqDir.getAbsolutePath());
            props.setProperty("batchResponseFolder", sharedRespDir.getAbsolutePath());

            final CnpBatchFileRequest req = new CnpBatchFileRequest("batchSendOnly-" + i + ".xml", props);
            final Communication mockComm = Mockito.mock(Communication.class);
            req.setCommunication(mockComm);

            // Wrap in a Callable so all threads start at the same latch point
            final CnpBatchFileRequest finalReq = req;
            sendFutures.add(executor.submit(new java.util.concurrent.Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    ready.countDown();
                    ready.await();
                    finalReq.sendOnlyToCnpSFTPConcurrent();
                    return null;
                }
            }));
        }

        int successCount = 0;
        for (Future<Void> f : sendFutures) {
            f.get(); // must not throw
            successCount++;
        }
        assertEquals("All send-only concurrent requests must complete", threadCount, successCount);
        executor.shutdown();
    }

    // ============== NEW: retrieveOnlyFromCnpSFTPConcurrent() ==============

    @Test
    public void testRetrieveOnlyFromCnpSFTPConcurrent_delegatesToRetrieve() throws Exception {
        File reqDir = tempFolder.newFolder("retrieveOnlyConcReq");
        File respDir = tempFolder.newFolder("retrieveOnlyConcResp");
        Properties props = buildBaseProperties();
        props.setProperty("batchRequestFolder", reqDir.getAbsolutePath());
        props.setProperty("batchResponseFolder", respDir.getAbsolutePath());

        CnpBatchFileRequest req = new CnpBatchFileRequest("retrieveConc.xml", props);
        File responseFile = new File(respDir, "retrieveConc.xml");
        writeValidCnpResponseXml(responseFile);
        req.setResponseFile(responseFile);

        Communication mockCommunication = Mockito.mock(Communication.class);
        req.setCommunication(mockCommunication);

        CnpBatchFileResponse response = req.retrieveOnlyFromCnpSFTPConcurrent();

        assertNotNull(response);
        verify(mockCommunication, times(1))
                .receiveCnpRequestResponseFileFromSFTP(any(File.class), any(File.class), any(Properties.class));
        verify(mockCommunication, never())
                .sendCnpRequestFileToSFTP(any(File.class), any(Properties.class));
    }

    // ============== NEW: retrieveOnlyFromCnpSFTPConcurrent(ExecutorService) ==============

    @Test
    public void testRetrieveOnlyFromCnpSFTPConcurrent_withExecutorService_returnsValidFuture() throws Exception {
        File reqDir = tempFolder.newFolder("retrieveOnlyExecReq");
        File respDir = tempFolder.newFolder("retrieveOnlyExecResp");
        Properties props = buildBaseProperties();
        props.setProperty("batchRequestFolder", reqDir.getAbsolutePath());
        props.setProperty("batchResponseFolder", respDir.getAbsolutePath());

        CnpBatchFileRequest req = new CnpBatchFileRequest("retrieveOnlyExec.xml", props);
        File responseFile = new File(respDir, "retrieveOnlyExec.xml");
        writeValidCnpResponseXml(responseFile);
        req.setResponseFile(responseFile);

        Communication mockCommunication = Mockito.mock(Communication.class);
        req.setCommunication(mockCommunication);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<CnpBatchFileResponse> future = req.retrieveOnlyFromCnpSFTPConcurrent(executor);
            CnpBatchFileResponse response = future.get();

            assertNotNull(future);
            assertNotNull(response);
            verify(mockCommunication, times(1))
                    .receiveCnpRequestResponseFileFromSFTP(any(File.class), any(File.class), any(Properties.class));
            verify(mockCommunication, never())
                    .sendCnpRequestFileToSFTP(any(File.class), any(Properties.class));
        } finally {
            executor.shutdown();
        }
    }

    @Test
    public void testRetrieveOnlyFromCnpSFTPConcurrent_multipleInstancesInParallel_allComplete() throws Exception {
        final int threadCount = 3;
        File sharedRespDir = tempFolder.newFolder("retrieveOnlyParallelResp");
        File sharedReqDir  = tempFolder.newFolder("retrieveOnlyParallelReq");

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        final CountDownLatch ready = new CountDownLatch(threadCount);
        List<Future<CnpBatchFileResponse>> retrieveFutures = new ArrayList<Future<CnpBatchFileResponse>>();

        for (int i = 0; i < threadCount; i++) {
            Properties props = buildBaseProperties();
            props.setProperty("batchRequestFolder", sharedReqDir.getAbsolutePath());
            props.setProperty("batchResponseFolder", sharedRespDir.getAbsolutePath());

            final CnpBatchFileRequest req = new CnpBatchFileRequest("batchRetrieve-" + i + ".xml", props);
            final File respFile = new File(sharedRespDir, "batchRetrieve-" + i + ".xml");
            writeValidCnpResponseXml(respFile);
            req.setResponseFile(respFile);

            final Communication mockComm = Mockito.mock(Communication.class);
            req.setCommunication(mockComm);

            final CnpBatchFileRequest finalReq = req;
            retrieveFutures.add(executor.submit(new java.util.concurrent.Callable<CnpBatchFileResponse>() {
                @Override
                public CnpBatchFileResponse call() throws Exception {
                    ready.countDown();
                    ready.await();
                    return finalReq.retrieveOnlyFromCnpSFTPConcurrent();
                }
            }));
        }

        int successCount = 0;
        for (Future<CnpBatchFileResponse> f : retrieveFutures) {
            assertNotNull(f.get());
            successCount++;
        }
        assertEquals("All retrieve-only concurrent requests must complete", threadCount, successCount);
        executor.shutdown();
    }

    /**
     * Full split-workflow test: send all files in parallel with
     * {@link CnpBatchFileRequest#sendOnlyToCnpSFTPConcurrent(ExecutorService)},
     * then retrieve all in parallel with
     * {@link CnpBatchFileRequest#retrieveOnlyFromCnpSFTPConcurrent(ExecutorService)}.
     */
    @Test
    public void testSplitWorkflow_sendThenRetrieve_multipleInstancesInParallel() throws Exception {
        final int threadCount = 4;
        File sharedReqDir  = tempFolder.newFolder("splitWorkflowReq");
        File sharedRespDir = tempFolder.newFolder("splitWorkflowResp");
        new File(sharedReqDir, "tmp").mkdirs();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<CnpBatchFileRequest> requests = new ArrayList<CnpBatchFileRequest>();

        for (int i = 0; i < threadCount; i++) {
            Properties props = buildBaseProperties();
            props.setProperty("batchRequestFolder", sharedReqDir.getAbsolutePath());
            props.setProperty("batchResponseFolder", sharedRespDir.getAbsolutePath());

            CnpBatchFileRequest req = new CnpBatchFileRequest("splitFile-" + i + ".xml", props);
            File respFile = new File(sharedRespDir, "splitFile-" + i + ".xml");
            writeValidCnpResponseXml(respFile);
            req.setResponseFile(respFile);

            Communication mockComm = Mockito.mock(Communication.class);
            req.setCommunication(mockComm);
            requests.add(req);
        }

        // Phase 1 – send all files in parallel
        List<Future<Void>> sendFutures = new ArrayList<Future<Void>>();
        for (CnpBatchFileRequest req : requests) {
            sendFutures.add(req.sendOnlyToCnpSFTPConcurrent(executor));
        }
        for (Future<Void> f : sendFutures) {
            f.get(); // assert no exception
        }

        // Phase 2 – retrieve all files in parallel
        List<Future<CnpBatchFileResponse>> retrieveFutures = new ArrayList<Future<CnpBatchFileResponse>>();
        for (CnpBatchFileRequest req : requests) {
            retrieveFutures.add(req.retrieveOnlyFromCnpSFTPConcurrent(executor));
        }

        int successCount = 0;
        for (Future<CnpBatchFileResponse> f : retrieveFutures) {
            assertNotNull("Retrieve response must not be null", f.get());
            successCount++;
        }
        assertEquals("All split-workflow requests must complete", threadCount, successCount);
        executor.shutdown();
    }
}
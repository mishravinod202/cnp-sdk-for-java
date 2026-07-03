package io.github.vantiv.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Properties;

import io.github.vantiv.sdk.generate.*;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

public class TestQueryDpoWalletBalance {

    private static CnpOnline cnp;

    @BeforeClass
    public static void beforeClass() throws Exception {
        cnp = new CnpOnline();
    }

    @Test
    public void simpleQueryDpoWalletBalance() throws Exception {
        QueryDpoWalletBalance queryDpoWalletBalance = new QueryDpoWalletBalance();
        queryDpoWalletBalance.setId("dpoQuery1");
        queryDpoWalletBalance.setReportGroup("Default Report Group");

        QueryDpoWalletBalanceResponse response = cnp.queryDpoWalletBalance(queryDpoWalletBalance);
        assertNotNull(response);
        assertEquals("dpoQuery1", response.getId());
        assertNotNull(response.getResponse());
        assertNotNull(response.getMessage());
    }

    @Test
    public void queryDpoWalletBalance_withMockedResponse() throws Exception {
        QueryDpoWalletBalance queryDpoWalletBalance = new QueryDpoWalletBalance();
        queryDpoWalletBalance.setId("dpoMockQuery");
        queryDpoWalletBalance.setReportGroup("Default Report Group");

        Communication mockedCommunication = Mockito.mock(Communication.class);
        Properties config = new Properties();
        config.setProperty("url", "https://www.testvantivcnp.com/sandbox/communicator/online");
        config.setProperty("username", "SDKTEAM12");
        config.setProperty("password", "V2b9F4k7");
        config.setProperty("merchantId", "1288791");
        config.setProperty("reportGroup", "Default Report Group");
        config.setProperty("version", "12.50");
        config.setProperty("printxml", "true");

        String responseXml = "<cnpOnlineResponse version='12.50' response='0' message='Valid Format' xmlns='http://www.vantivcnp.com/schema'>" +
                "<queryDpoWalletBalanceResponse id='dpoMockQuery' reportGroup='Default Report Group'>" +
                "<cnpTxnId>123456789012</cnpTxnId>" +
                "<response>000</response>" +
                "<responseTime>2026-06-01T10:00:00</responseTime>" +
                "<message>Approved</message>" +
                "<projectedAvailableBalance>10000</projectedAvailableBalance>" +
                "<reserveBalance>2000</reserveBalance>" +
                "<availableRtpBalance>8000</availableRtpBalance>" +
                "</queryDpoWalletBalanceResponse></cnpOnlineResponse>";

        Mockito.when(mockedCommunication.requestToServer(Mockito.anyString(), Mockito.any(Properties.class)))
               .thenReturn(responseXml);

        CnpOnline cnpWithMock = new CnpOnline(config);
        cnpWithMock.setCommunication(mockedCommunication);

        QueryDpoWalletBalanceResponse response = cnpWithMock.queryDpoWalletBalance(queryDpoWalletBalance);
        assertEquals("dpoMockQuery", response.getId());
        assertEquals("000", response.getResponse());
        assertEquals("Approved", response.getMessage());
        assertEquals(Long.valueOf(10000L), response.getProjectedAvailableBalance());
        assertEquals(Long.valueOf(2000L), response.getReserveBalance());
        assertEquals(Long.valueOf(8000L), response.getAvailableRtpBalance());
        assertEquals(123456789012L, response.getCnpTxnId());
    }

    @Test
    public void queryDpoWalletBalance_withIdentityBundle() throws Exception {
        QueryDpoWalletBalance queryDpoWalletBalance = new QueryDpoWalletBalance();
        queryDpoWalletBalance.setId("dpoIdentityTest");
        queryDpoWalletBalance.setReportGroup("Default Report Group");

        IdentityBundle identityBundle = new IdentityBundle();
        identityBundle.setMerchantId("merchant001");
        identityBundle.setEntityId("entity001");
        identityBundle.setCommandId("cmd001");

        Communication mockedCommunication = Mockito.mock(Communication.class);
        Properties config = new Properties();
        config.setProperty("url", "https://www.testvantivcnp.com/sandbox/communicator/online");
        config.setProperty("username", "SDKTEAM12");
        config.setProperty("password", "V2b9F4k7");
        config.setProperty("merchantId", "1288791");
        config.setProperty("reportGroup", "Default Report Group");
        config.setProperty("version", "12.50");

        String responseXml = "<cnpOnlineResponse version='12.50' response='0' message='Valid Format' xmlns='http://www.vantivcnp.com/schema'>" +
                "<queryDpoWalletBalanceResponse id='dpoIdentityTest' reportGroup='Default Report Group'>" +
                "<cnpTxnId>555444333222</cnpTxnId>" +
                "<response>000</response>" +
                "<responseTime>2026-06-01T11:00:00</responseTime>" +
                "<message>Approved</message>" +
                "</queryDpoWalletBalanceResponse></cnpOnlineResponse>";

        Mockito.when(mockedCommunication.requestToServer(Mockito.anyString(), Mockito.any(Properties.class)))
               .thenReturn(responseXml);

        CnpOnline cnpWithMock = new CnpOnline(config);
        cnpWithMock.setCommunication(mockedCommunication);

        QueryDpoWalletBalanceResponse response = cnpWithMock.queryDpoWalletBalance(queryDpoWalletBalance);
        assertEquals("dpoIdentityTest", response.getId());
        assertEquals("000", response.getResponse());
        assertEquals(555444333222L, response.getCnpTxnId());
    }
}

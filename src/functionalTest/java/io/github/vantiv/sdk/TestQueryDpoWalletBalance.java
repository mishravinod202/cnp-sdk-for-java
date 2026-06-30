package io.github.vantiv.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.BeforeClass;
import org.junit.Test;

import io.github.vantiv.sdk.generate.QueryDpoWalletBalance;
import io.github.vantiv.sdk.generate.QueryDpoWalletBalanceResponse;

// v12.50: tests for new queryDpoWalletBalance transaction
public class TestQueryDpoWalletBalance {

    private static CnpOnline cnp;

    @BeforeClass
    public static void beforeClass() throws Exception {
        cnp = new CnpOnline();
    }

    @Test
    public void simpleQueryDpoWalletBalance() throws Exception {
        QueryDpoWalletBalance query = new QueryDpoWalletBalance();
        query.setReportGroup("Default");
        query.setId("id");
        query.setCustomerId("customerId");

        QueryDpoWalletBalanceResponse response = cnp.queryDpoWalletBalance(query);
        assertNotNull(response);
        assertNotNull(response.getResponse());
        assertNotNull(response.getMessage());
    }

    @Test
    public void queryDpoWalletBalanceWithCustomerInfo() throws Exception {
        QueryDpoWalletBalance query = new QueryDpoWalletBalance();
        query.setReportGroup("Planets");
        query.setId("id");
        query.setCustomerId("CUST-001");

        QueryDpoWalletBalanceResponse response = cnp.queryDpoWalletBalance(query);
        assertNotNull(response);
        assertNotNull(response.getResponse());
    }
}

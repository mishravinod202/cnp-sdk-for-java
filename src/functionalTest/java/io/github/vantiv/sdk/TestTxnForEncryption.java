package io.github.vantiv.sdk;

import io.github.vantiv.sdk.generate.*;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class TestTxnForEncryption {
    private  CnpOnline cnp;
    @Before
    public void beforeClass() throws IOException {
        Properties config = new Properties();
        config.load(new FileInputStream(new Configuration().location()));
        config.setProperty("oltpEncryptionPayload", "true");
        config.setProperty("oltpEncryptionKeySequence", "10000");
        config.setProperty("oltpEncryptionKeyPath",System.getProperty("user.dir") + "/src/functionalTest/resources/testCnpOltpEncryptionKey.asc");
        cnp = new CnpOnline(config);
    }

    @Test
    public void simpleAuthForEncryption() throws Exception {
        Authorization authorization = new Authorization();
        authorization.setReportGroup("abc");
        authorization.setOrderId("12344");
        authorization.setAmount(106L);
        authorization.setOrderSource(OrderSourceType.ECOMMERCE);
        authorization.setId("id");
        CardType card = new CardType();
        card.setType(MethodOfPaymentTypeEnum.VI);
        card.setNumber("4100000000000001");
        card.setExpDate("1210");
        authorization.setCard(card);
        AuthorizationResponse response = cnp.authorize(authorization);
        assertEquals("abc",response.getReportGroup());
        assertEquals("sandbox", response.getLocation());
    }
}

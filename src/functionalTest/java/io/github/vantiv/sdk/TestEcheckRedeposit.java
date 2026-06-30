package io.github.vantiv.sdk;

import static org.junit.Assert.assertEquals;

import org.junit.BeforeClass;
import org.junit.Test;

import io.github.vantiv.sdk.generate.EcheckAccountTypeEnum;
import io.github.vantiv.sdk.generate.EcheckRedeposit;
import io.github.vantiv.sdk.generate.EcheckRedepositResponse;
import io.github.vantiv.sdk.generate.EcheckTokenType;
import io.github.vantiv.sdk.generate.EcheckType;
import io.github.vantiv.sdk.generate.IdentityBundle;

public class TestEcheckRedeposit {

	private static CnpOnline cnp;

	@BeforeClass
	public static void beforeClass() throws Exception {
		cnp = new CnpOnline();
	}
	
	@Test
	public void simpleEcheckRedeposit() throws Exception{
		EcheckRedeposit echeckredeposit = new EcheckRedeposit();
		echeckredeposit.setCnpTxnId(123456L);
		echeckredeposit.setId("id");
		EcheckRedepositResponse response = cnp.echeckRedeposit(echeckredeposit);
		assertEquals("Approved", response.getMessage());
		assertEquals("sandbox", response.getLocation());
	}
	
	@Test
	public void echeckRedepositWithEcheck() throws Exception{
		EcheckRedeposit echeckredeposit = new EcheckRedeposit();
		echeckredeposit.setCnpTxnId(123456L);
		EcheckType echeck = new EcheckType();
		echeck.setAccType(EcheckAccountTypeEnum.CHECKING);
		echeck.setAccNum("12345657890");
		echeck.setRoutingNum("123456789");
		echeck.setCheckNum("123455");
		echeck.setAccountId("123");
	    echeckredeposit.setId("id");
		
		echeckredeposit.setEcheck(echeck);
		EcheckRedepositResponse response = cnp.echeckRedeposit(echeckredeposit);
		assertEquals("Approved", response.getMessage());
		assertEquals("sandbox", response.getLocation());
	}
	
	@Test
	public void echeckRedepositWithEcheckToken() throws Exception{
		EcheckRedeposit echeckredeposit = new EcheckRedeposit();
		echeckredeposit.setCnpTxnId(123456L);
		EcheckTokenType echeckToken = new EcheckTokenType();
		echeckToken.setAccType(EcheckAccountTypeEnum.CHECKING.value());
		echeckToken.setCnpToken("1234565789012");
		echeckToken.setRoutingNum("123456789");
		echeckToken.setCheckNum("123455");
	    echeckredeposit.setId("id");
		
		echeckredeposit.setEcheckToken(echeckToken);
		EcheckRedepositResponse response = cnp.echeckRedeposit(echeckredeposit);
		assertEquals("Approved", response.getMessage());
		assertEquals("sandbox", response.getLocation());
	}

	// v12.50: identityBundle added to echeckRedeposit
	@Test
	public void echeckRedepositWithIdentityBundle() throws Exception {
		EcheckRedeposit echeckredeposit = new EcheckRedeposit();
		echeckredeposit.setCnpTxnId(123456L);
		EcheckType echeck = new EcheckType();
		echeck.setAccType(EcheckAccountTypeEnum.CHECKING);
		echeck.setAccNum("12345657890");
		echeck.setRoutingNum("123456789");
		echeck.setCheckNum("123455");
		echeckredeposit.setEcheck(echeck);
		IdentityBundle identityBundle = new IdentityBundle();
		identityBundle.setMerchantId("12222");
		identityBundle.setEntityId("222222");
		identityBundle.setEntityReference("32222");
		identityBundle.setResourceId("422222");
		identityBundle.setResourceReference("52222");
		identityBundle.setCommandId("6222");
		identityBundle.setCommandReference("72222");
		echeckredeposit.setIdentityBundle(identityBundle);
		echeckredeposit.setId("id");
		EcheckRedepositResponse response = cnp.echeckRedeposit(echeckredeposit);
		assertEquals("Approved", response.getMessage());
		assertEquals("sandbox", response.getLocation());
	}

}

package io.github.vantiv.sdk;

import java.io.*;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import io.github.vantiv.sdk.generate.Authentication;
import io.github.vantiv.sdk.generate.CnpRequest;
import org.bouncycastle.openpgp.PGPException;


public class CnpBatchFileRequest{

	private JAXBContext jc;
	private Properties properties;
	private Communication communication;
	private List<CnpBatchRequest> cnpBatchRequestList;
	private String requestFileName;
	private File requestFile;
	private File responseFile;
	private File tempBatchRequestFile;
	private String requestId;
	private Marshaller marshaller;
	private Configuration config = null;

	/**
	 * Unique identifier for this instance, used to guarantee that temporary files
	 * produced during {@link #prepareForDelivery()} do not collide with those of
	 * other {@code CnpBatchFileRequest} instances running concurrently in the same
	 * {@code batchRequestFolder}.
	 */
	private final String instanceId = UUID.randomUUID().toString();

    protected int maxAllowedTransactionsPerFile;

	/**
	 * Recommend NOT to change this value.
	 */
	private final int CNP_LIMIT_MAX_ALLOWED_TNXS_PER_FILE = 500000;

	/**
	 * Construct a CnpBatchFileRequest using the configuration specified in location specified by requestFileName
	 */
	public CnpBatchFileRequest(String requestFileName) {
		initializeMembers(requestFileName);
	}

	/**
	 * Construct a CnpBatchFileRequest specifying the file name for the
	 * request (ex: filename: TestFile.xml the extension should be provided if
	 * the file has to generated in certain format like xml or txt etc) and
	 * configuration in code. This should be used by integrations that have
	 * another way to specify their configuration settings (ofbiz, etc)
	 *
	 * Properties that *must* be set are:
	 *
	 * batchHost (eg https://payments.vantivcnp.com)
	 * merchantId
	 * password
	 * batchTcpTimeout (in seconds)
	 * BatchRequestPath folder - specify the absolute path
	 * BatchResponsePath folder - specify the absolute path
	 * sftpUsername
	 * sftpPassword
	 * Optional properties
	 * are:
	 * proxyHost
	 * proxyPort
	 * printxml (possible values "true" and "false" defaults to false)
	 *
	 * @param requestFileName name of request file
	 * @param properties configuration properties
	 */
	public CnpBatchFileRequest(String requestFileName, Properties properties) {
		initializeMembers(requestFileName, properties);
	}

	/**
	 * This constructor is primarily here for test purposes only.
	 * @param requestFileName name of request file
	 * @param config configuration to use for processing
	 */
	public CnpBatchFileRequest(String requestFileName, Configuration config) {
		this.config = config;
		initializeMembers(requestFileName, null);
	}

    private void initializeMembers(String requestFileName) {
        initializeMembers(requestFileName, null);
    }

	public void initializeMembers(String requestFileName, Properties in_properties) throws CnpBatchException{
		try {
			this.jc = JAXBContext.newInstance("io.github.vantiv.sdk.generate");
			if(config == null){
				config = new Configuration();
			}
			this.communication = new Communication();
			this.cnpBatchRequestList = new ArrayList<CnpBatchRequest>();
			this.requestFileName = requestFileName;
			marshaller = jc.createMarshaller();
			// JAXB_FRAGMENT property required to prevent unnecessary XML info from being printed in the file during marshal.
			marshaller.setProperty(Marshaller.JAXB_FRAGMENT, true);
			// Proper formatting of XML purely for aesthetic purposes.
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

            if (in_properties == null || in_properties.isEmpty()) {
                this.properties = new Properties();
                this.properties.load(new FileInputStream(config.location()));
            } else {
                fillInMissingFieldsFromConfig(in_properties);
                this.properties = in_properties;
            }

			this.maxAllowedTransactionsPerFile = Integer.parseInt(properties.getProperty("maxAllowedTransactionsPerFile", "1000"));
			if (maxAllowedTransactionsPerFile > CNP_LIMIT_MAX_ALLOWED_TNXS_PER_FILE) {
				throw new CnpBatchException("maxAllowedTransactionsPerFile property value cannot exceed "
						+ String.valueOf(CNP_LIMIT_MAX_ALLOWED_TNXS_PER_FILE));
			}

            requestFile = getFileToWrite("batchRequestFolder");
            responseFile = getFileToWrite("batchResponseFolder");

		} catch (FileNotFoundException e) {
			throw new CnpBatchException(
					"Configuration file not found." +
							" If you are not using the .cnp_SDK_config.properties file," +
							" please use the " + CnpBatchFileRequest.class.getSimpleName() +
							"(String, Properties) constructor." +
							" If you are using .cnp_SDK_config.properties, you can generate one using:" +
							" java -jar cnp-sdk-for-java-x.xx.jar", e);
		} catch (IOException e) {
			throw new CnpBatchException(
					"Configuration file could not be loaded.  " +
							"Check to see if the current user has permission to access the file", e);
		} catch (JAXBException e) {
			throw new CnpBatchException(
					"Unable to load jaxb dependencies.  Perhaps a classpath issue?", e);
		}
	}

    protected void setCommunication(Communication communication) {
        this.communication = communication;
    }

    public Properties getConfig() {
        return this.properties;
    }

	/**
	 * Returns a CnpBatchRequest object, the container for transactions.
	 * @param merchantId merchant ID for this batch
	 * @return CnpBatchRequest created batch request
	 * @throws CnpBatchException Vantiv batch exception
	 */
	public CnpBatchRequest createBatch(String merchantId) throws CnpBatchException {
		CnpBatchRequest cnpBatchRequest = new CnpBatchRequest(merchantId, this);
		cnpBatchRequestList.add(cnpBatchRequest);
		return cnpBatchRequest;
	}

	/**
	 * This method generates the request file alone. To generate the response
	 * object call sendToCnp method.
	 *
	 * @throws CnpBatchException Vantiv batch exception
	 */
	public void generateRequestFile() throws CnpBatchException {
		OutputStream cnpReqWriter = null;
		try {
			CnpRequest cnpRequest = buildCnpRequest();

			// Code to write to the file directly
			StringWriter sw = new StringWriter();
			Marshaller marshaller;

			try {
				marshaller = jc.createMarshaller();
				marshaller.marshal(cnpRequest, sw);
			} catch (JAXBException e) {
				throw new CnpBatchException("Unable to load jaxb dependencies.  Perhaps a classpath issue?");
			}

			String xmlRequest = sw.toString();

			xmlRequest = xmlRequest.replace("</cnpRequest>", " ");

			cnpReqWriter = new FileOutputStream(requestFile);
			FileInputStream fis = new FileInputStream(tempBatchRequestFile);
			byte[] readData = new byte[1024];
			cnpReqWriter.write(xmlRequest.getBytes());
			int i = fis.read(readData);

			while (i != -1) {
				cnpReqWriter.write(readData, 0, i);
				i = fis.read(readData);
			}

			cnpReqWriter.write(("</cnpRequest>\n").getBytes());
			//marshaller.marshal(cnpRequest, os);
			fis.close();
			tempBatchRequestFile.delete();
			cnpReqWriter.close();
		} catch (IOException e) {
			throw new CnpBatchException("Error while creating a batch request file. " +
					"Check to see if the current user has permission to read and write to " +
					this.properties.getProperty("batchRequestFolder"), e);
		}
	}

    public File getFile() {
        return requestFile;
    }


    public int getMaxAllowedTransactionsPerFile() {
        return this.maxAllowedTransactionsPerFile;
    }


    void fillInMissingFieldsFromConfig(Properties config) throws CnpBatchException {
        Properties localConfig = new Properties();
        boolean propertiesReadFromFile = false;
        try {
            String[] allProperties = {"username", "password", "proxyHost",
                    "proxyPort", "batchHost", "batchPort",
                    "batchTcpTimeout", "batchUseSSL",
                    "maxAllowedTransactionsPerFile", "maxTransactionsPerBatch",
                    "batchRequestFolder", "batchResponseFolder", "sftpUsername", "sftpPassword", "sftpTimeout",
                    "sftpPollIntervalMillis", "merchantId", "printxml", "useEncryption", "VantivPublicKeyPath", "PrivateKeyPath", "PublicKeyPath", "gpgPassphrase", "deleteBatchFiles"};

            for (String prop : allProperties) {
                // if the value of a property is not set,
                // look at the Properties member of the class first, and the .properties file next.
                if (config.getProperty(prop) == null) {
                    if (this.properties != null && this.properties.get(prop) != null) {
                        config.setProperty(prop, this.properties.getProperty(prop));
                    } else {
                        if (!propertiesReadFromFile) {
                            localConfig.load(new FileInputStream((new Configuration()).location()));
                            propertiesReadFromFile = true;
                        }
                        if (localConfig.getProperty(prop) != null) {
                            config.setProperty(prop, localConfig.getProperty(prop));
                        }
                    }
                }
            }
        } catch (FileNotFoundException e) {
            throw new CnpBatchException("File .cnp_SDK_config.properties was not found. " +
                    "Please run the Setup.java application to create the file at location " +
                    (new Configuration()).location(), e);
        } catch (IOException e) {
            throw new CnpBatchException(
                    "There was an exception while reading the .cnp_SDK_config.properties file.", e);
        }
    }

	public int getNumberOfBatches() {
		return this.cnpBatchRequestList.size();
	}

	public int getNumberOfTransactionInFile() {
		int i;
		int totalNumberOfTransactions = 0;
		for (i = 0; i < getNumberOfBatches(); i++) {
			CnpBatchRequest lbr = cnpBatchRequestList.get(i);
			totalNumberOfTransactions += lbr.getNumberOfTransactions();
		}

		return totalNumberOfTransactions;
	}

	/**
	 * Sends the file to Vantiv over sFTP, the preferred method of sending batches to Vantiv eCommerce.
	 * @return A response object for the batch file
	 * @throws CnpBatchException Vantiv batch exception
	 */
	public CnpBatchFileResponse sendToCnpSFTP() throws CnpBatchException {
		return sendToCnpSFTP(false);
	}

	/**
	 * Sends the file to Vantiv over sFTP, this is the preferred method of sending batches to Vantiv eCommerce.
	 * @param useExistingFile If the batch file was prepared in an earlier step, this method
	 * can be told to use the existing file.
	 * @return A response object for the batch file
	 * @throws CnpBatchException Vantiv batch exception
	 */
	public CnpBatchFileResponse sendToCnpSFTP(boolean useExistingFile) throws CnpBatchException{

		sendOnlyToCnpSFTP(useExistingFile);

		CnpBatchFileResponse retObj = retrieveOnlyFromCnpSFTP();

		return retObj;
	}

	/**
	 * Only sends the file to Vantiv over sFTP. This method requires separate invocation of the retrieve method.
	 * @throws CnpBatchException Vantiv batch exception
	 */
	public void sendOnlyToCnpSFTP() throws CnpBatchException {
		sendOnlyToCnpSFTP(false);
	}

	/**
	 * Only sends the file to Vantiv after sFTP. This method requires separate invocation of the retrieve method.
	 * @param useExistingFile If the batch file was prepared in an earlier step, this method
	 * can be told to use the existing file.
	 * @throws CnpBatchException Vantiv batch exception
	 */
	public void sendOnlyToCnpSFTP(boolean useExistingFile) throws CnpBatchException {
		try {
			if (!useExistingFile) {
				prepareForDelivery();
			}

            communication.sendCnpRequestFileToSFTP(requestFile, properties);

            checkDeleteBatchRequestFiles();

		} catch (IOException e) {
			throw new CnpBatchException("There was an exception while creating the Cnp Request file. " +
					"Check to see if the current user has permission to read and write to " +
					this.properties.getProperty("batchRequestFolder"), e);
		}
	}


    private void checkDeleteBatchRequestFiles() {
        boolean deleteBatchFiles = "true".equalsIgnoreCase(properties.getProperty("deleteBatchFiles"));

        if (deleteBatchFiles) {
            requestFile.delete();
        }
    }

	/**
	 * Only retrieves the file from Vantiv over sFTP. This method requires separate invocation of the send method.
	 * @return A response object for the file
	 * @throws CnpBatchException Vantiv batch exception
	 */
	public CnpBatchFileResponse retrieveOnlyFromCnpSFTP() throws CnpBatchException {
		try {

            boolean useEncryption = "true".equalsIgnoreCase(properties.getProperty("useEncryption"));

            File requestFileToFetch = requestFile;
            File responseFileToRecieve = responseFile;

            if (useEncryption) {
                requestFileToFetch = new File(requestFile.getAbsolutePath());
                responseFileToRecieve = new File(responseFile.getAbsolutePath() + ".encrypted");
            }

			communication.receiveCnpRequestResponseFileFromSFTP(requestFileToFetch, responseFileToRecieve, properties);

            if (useEncryption) {
                decryptResponseFile();
            }

			CnpBatchFileResponse retObj = new CnpBatchFileResponse(responseFile);

            checkDeleteBatchResponseFiles(responseFileToRecieve);

			return retObj;
		} catch (IOException e) {
			throw new CnpBatchException("There was an exception while creating the Cnp Request file. " +
					"Check to see if the current user has permission to read and write to " +
					this.properties.getProperty("batchRequestFolder"), e);
		}
	}

    private void decryptResponseFile() {
        String encResponseFilename = responseFile.getAbsolutePath() + ".encrypted";
        String pp = properties.getProperty("gpgPassphrase");
        String privateKeyPath = properties.getProperty("PrivateKeyPath");
        try {
            PgpHelper.decrypt(encResponseFilename, responseFile.getAbsolutePath(), privateKeyPath, pp);
        } catch (PGPException pgpe) {
            throw new CnpBatchException("Error while decrypting response file. Check if " + privateKeyPath + " contains correct private key." +
                    "and that the gpgPassphrase provided in config file is correct.", pgpe);
        } catch (IOException ioe) {
            throw new CnpBatchException("Error in decrypting response file. Check to see if the current user has permission to read and write to" +
                    this.properties.getProperty("batchRequestFolder") + "." +
                    "Also check if " + privateKeyPath + " contains the private key.");
        }
    }


    private void checkDeleteBatchResponseFiles(File fileToBeDeleted) {
        boolean deleteBatchFiles = "true".equalsIgnoreCase(properties.getProperty("deleteBatchFiles"));
        if (deleteBatchFiles) {
            responseFile.delete();
            fileToBeDeleted.delete();
        }
    }

    /**
     * Prepare final batch request file to be submitted in batch request folder.
     */
    public void prepareForDelivery() {
        if ("true".equalsIgnoreCase(properties.getProperty("useEncryption"))) {
            prepareForEncryptedDelivery();
        }
        else {
            try {
                String writeFolderPath = this.properties.getProperty("batchRequestFolder");

                tempBatchRequestFile = new File(writeFolderPath + "/tmp/tempBatchFileTesting");
                OutputStream batchReqWriter = new FileOutputStream(tempBatchRequestFile.getAbsoluteFile());
                // close the all the batch files
                byte[] readData = new byte[1024];
                for (CnpBatchRequest batchReq : cnpBatchRequestList) {
                    batchReq.closeFile();
                    String batchRequestXml = buildBatchRequestXml(batchReq);
                    batchRequestXml = batchRequestXml.replaceFirst("/>", ">");

                    FileInputStream fis = new FileInputStream(batchReq.getFile());

                    batchReqWriter.write(batchRequestXml.getBytes());
                    int i = fis.read(readData);

                    while (i != -1) {
                        batchReqWriter.write(readData, 0, i);
                        i = fis.read(readData);
                    }

                    batchReqWriter.write(("</batchRequest>\n").getBytes());
                    fis.close();
                    batchReq.getFile().delete();
                }
                // close the file
                batchReqWriter.close();
                generateRequestFile();
                File tmpFile = new File(writeFolderPath + "/tmp");
                if (tmpFile.exists()) {
                    tmpFile.delete();
                }
            }
            catch (IOException ioe) {
                throw new CnpBatchException(
                        "There was an exception while creating the Cnp Request file. " +
                                "Check to see if the current user has permission to read and write to " +
                                this.properties.getProperty("batchRequestFolder"), ioe);
            }
        }
    }


    /**
     * Prepare final encrypted batch request file to be submitted in batch request folder.
     */
    private void prepareForEncryptedDelivery() {

        String privateKeyPath = properties.getProperty("PrivateKeyPath");
        String gpgPassphrase = properties.getProperty("gpgPassphrase");
        String vantivPubKeyPath = properties.getProperty("VantivPublicKeyPath");
        String cnpRequestXml = buildCnpRequestXml();
        try {
            cnpRequestXml = cnpRequestXml.replace("</cnpRequest>", " ");
            OutputStream encryptedCnpRequestWriter = PgpHelper.encryptionStream(requestFile.getAbsolutePath(), vantivPubKeyPath);
            encryptedCnpRequestWriter.write(cnpRequestXml.getBytes());

            byte[] clearData = new byte[2097152];
            for (CnpBatchRequest batchReq : cnpBatchRequestList) {
                batchReq.closeFile();
                String batchRequestXml = buildBatchRequestXml(batchReq);
                batchRequestXml = batchRequestXml.replaceFirst("/>", ">");
                encryptedCnpRequestWriter.write(batchRequestXml.getBytes());
                InputStream decryptionStream = PgpHelper.decryptionStream(batchReq.getFile().getAbsolutePath(),
                        privateKeyPath,
                        gpgPassphrase);
                int len;
                while ((len = decryptionStream.read(clearData)) > 0) {
                    encryptedCnpRequestWriter.write(clearData, 0, len);
                }
                decryptionStream.close();
                encryptedCnpRequestWriter.write("</batchRequest>\n".getBytes());
                batchReq.getFile().delete();
            }
            encryptedCnpRequestWriter.write(("</cnpRequest>\n").getBytes());
            encryptedCnpRequestWriter.close();
        }
        catch (IOException e) {
            throw new CnpBatchException(
                    "There was an exception while creating the Cnp Request file. " +
                            "Check to see if the current user has permission to read and write to " +
                            this.properties.getProperty("batchRequestFolder"), e);
        }
        catch (PGPException pgpe) {
            throw new CnpBatchException("Error in creating encrypted request file. Check if " + privateKeyPath + " contains correct private key." +
                    "and that the gpgPassphrase provided in config file is correct." +
                    "\nAlso check if " + vantivPubKeyPath + " contains correct public key.", pgpe);
        }
    }


    /**
     *
     * @param batchRequest
     * @return BatchRequest header xml containing the summary of the transactions within the  given batch request.
     */
    private String buildBatchRequestXml(CnpBatchRequest batchRequest) {
        try {
            StringWriter sw = new StringWriter();
            marshaller.marshal(batchRequest.getBatchRequest(), sw);
            return sw.toString();
        } catch (JAXBException e) {
            throw new CnpBatchException(
                    "There was an exception while marshalling BatchRequest or CnpRequest objects.", e);
        }
    }

    void setResponseFile(File inFile) {
        this.responseFile = inFile;
    }

    void setId(String id) {
        this.requestId = id;
    }


    /**
     *
     * @return CnpRequest xml header containing authentication information for the presenter.
     */
    private String buildCnpRequestXml() {
        CnpRequest cnpRequest = buildCnpRequest();

        // Code to write to the file directly
        StringWriter sw = new StringWriter();
        Marshaller marshaller;
        try {
            marshaller = jc.createMarshaller();
            marshaller.marshal(cnpRequest, sw);
        } catch (JAXBException e) {
            throw new CnpBatchException("Unable to load jaxb dependencies.  Perhaps a classpath issue?");
        }
        return sw.toString();
    }



    /**
     * This method initializes the high level properties for the XML(ex:
     * initializes the user name and password for the presenter)
     *
     * @return
     */
    private CnpRequest buildCnpRequest() {
        Authentication authentication = new Authentication();
        authentication.setPassword(this.properties.getProperty("password"));
        authentication.setUser(this.properties.getProperty("username"));
        CnpRequest cnpRequest = new CnpRequest();

		if(requestId != null && requestId.length() != 0) {
			cnpRequest.setId(requestId);
		}
		cnpRequest.setAuthentication(authentication);
		cnpRequest.setVersion(Versions.XML_VERSION);
		BigInteger numOfBatches = BigInteger.valueOf(this.cnpBatchRequestList.size());
		cnpRequest.setNumBatchRequests(numOfBatches);

		return cnpRequest;
	}

    /**
     * This method gets the file of either the request or response. It will also
     * make sure that the folder structure where the file lives will be there.
     *
     * @param locationKey Key to use to get the path to the folder.
     * @return File ready to be written to.
     */
    File getFileToWrite(String locationKey) {
        String fileName = this.requestFileName;
        String writeFolderPath = this.properties.getProperty(locationKey);
        File fileToReturn = new File(writeFolderPath, fileName);

        if (!fileToReturn.getParentFile().exists()) {
            fileToReturn.getParentFile().mkdirs();
        }

        return fileToReturn;
    }

    public boolean isEmpty() {
        return (getNumberOfTransactionInFile() == 0) ? true : false;
    }

    public boolean isFull() {
        return (getNumberOfTransactionInFile() == this.maxAllowedTransactionsPerFile);
    }

    // =========================================================================
    // Thread-safe / concurrent API
    // =========================================================================

    /**
     * Thread-safe variant of {@link #createBatch(String)}.
     * <p>
     * Uses the per-instance {@link #instanceId} together with a random UUID to
     * build a temp file path that is guaranteed to be unique across all
     * concurrently running {@code CnpBatchFileRequest} instances, even when they
     * share the same {@code batchRequestFolder}.
     *
     * @param merchantId merchant ID for the new batch
     * @return a newly created, thread-safe {@link CnpBatchRequest}
     * @throws CnpBatchException if the batch cannot be created
     */
    public CnpBatchRequest createBatchConcurrent(String merchantId) throws CnpBatchException {
        CnpBatchRequest cnpBatchRequest = new CnpBatchRequest(merchantId, this,
                instanceId + "-" + UUID.randomUUID().toString());
        cnpBatchRequestList.add(cnpBatchRequest);
        return cnpBatchRequest;
    }

    /**
     * Thread-safe variant of {@link #prepareForDelivery()}.
     * <p>
     * Identical to {@code prepareForDelivery()} except that the intermediate
     * temporary file is named using the per-instance {@link #instanceId} so that
     * concurrent instances sharing the same {@code batchRequestFolder} do not
     * overwrite each other's in-flight work.
     *
     * @throws CnpBatchException if an I/O error occurs while assembling the request file
     */
    public void prepareForDeliveryConcurrent() {
        if ("true".equalsIgnoreCase(properties.getProperty("useEncryption"))) {
            prepareForEncryptedDelivery();
        } else {
            try {
                String writeFolderPath = this.properties.getProperty("batchRequestFolder");
                File tmpDir = new File(writeFolderPath + "/tmp");
                tmpDir.mkdirs();
                tempBatchRequestFile = new File(tmpDir, "tempBatch-" + instanceId);
                byte[] readData = new byte[1024];
                try (OutputStream batchReqWriter = new FileOutputStream(tempBatchRequestFile.getAbsoluteFile())) {
                    for (CnpBatchRequest batchReq : cnpBatchRequestList) {
                        batchReq.closeFile();
                        String batchRequestXml = buildBatchRequestXml(batchReq);
                        batchRequestXml = batchRequestXml.replaceFirst("/>", ">");
                        batchReqWriter.write(batchRequestXml.getBytes());
                        try (FileInputStream fis = new FileInputStream(batchReq.getFile())) {
                            int i = fis.read(readData);
                            while (i != -1) {
                                batchReqWriter.write(readData, 0, i);
                                i = fis.read(readData);
                            }
                        }
                        batchReqWriter.write(("</batchRequest>\n").getBytes());
                        batchReq.getFile().delete();
                    }
                }
                generateRequestFile();
                // Do NOT delete the tmp directory here — other concurrent instances
                // may still need it. The individual temp file is deleted inside
                // generateRequestFile(), so no resources are leaked.
            } catch (IOException ioe) {
                throw new CnpBatchException(
                        "There was an exception while creating the Cnp Request file. " +
                                "Check to see if the current user has permission to read and write to " +
                                this.properties.getProperty("batchRequestFolder"), ioe);
            }
        }
    }

    /**
     * Thread-safe variant of {@link #sendOnlyToCnpSFTP()}.
     * <p>
     * Uses {@link #prepareForDeliveryConcurrent()} so that the intermediate temp file is
     * named uniquely per instance and concurrent senders do not collide on the same
     * {@code batchRequestFolder}.
     *
     * @throws CnpBatchException if an I/O error occurs during sending
     */
    public void sendOnlyToCnpSFTPConcurrent() throws CnpBatchException {
        try {
            prepareForDeliveryConcurrent();
            communication.sendCnpRequestFileToSFTP(requestFile, properties);
            checkDeleteBatchRequestFiles();
        } catch (IOException e) {
            throw new CnpBatchException("There was an exception while creating the Cnp Request file. " +
                    "Check to see if the current user has permission to read and write to " +
                    this.properties.getProperty("batchRequestFolder"), e);
        }
    }

    /**
     * Thread-safe variant of {@link #sendToCnpSFTP()}.
     * Prepares, sends, and retrieves the batch file in a single blocking call.
     * Safe to invoke concurrently from multiple threads, each on its own
     * {@code CnpBatchFileRequest} instance.
     *
     * @return a {@link CnpBatchFileResponse} containing responses for all transactions
     * @throws CnpBatchException if an I/O error occurs during the operation
     */
    public CnpBatchFileResponse sendToCnpSFTPConcurrent() throws CnpBatchException {
        return sendToCnpSFTPConcurrent(false);
    }

    /**
     * Thread-safe variant of {@link #sendToCnpSFTP(boolean)}.
     *
     * @param useExistingFile when {@code true} skips file preparation
     * @return a {@link CnpBatchFileResponse} containing responses for all transactions
     * @throws CnpBatchException if an I/O error occurs during the operation
     */
    public CnpBatchFileResponse sendToCnpSFTPConcurrent(boolean useExistingFile) throws CnpBatchException {
        sendOnlyToCnpSFTPConcurrent();
        return retrieveOnlyFromCnpSFTP();
    }

    /**
     * Submits this batch file request to Vantiv over sFTP asynchronously using the
     * supplied {@link ExecutorService}. Each {@code CnpBatchFileRequest} instance is
     * fully self-contained; multiple instances can therefore be submitted to the same
     * executor simultaneously without any contention over temporary file resources.
     *
     * <p>Example — sending four batch files in parallel:
     * <pre>
     * ExecutorService executor = Executors.newFixedThreadPool(4);
     * List&lt;Future&lt;CnpBatchFileResponse&gt;&gt; futures = new ArrayList&lt;&gt;();
     *
     * for (CnpBatchFileRequest req : batchRequests) {
     *     futures.add(req.sendToCnpSFTPConcurrent(executor));
     * }
     *
     * for (Future&lt;CnpBatchFileResponse&gt; future : futures) {
     *     CnpBatchFileResponse response = future.get();   // blocks until that file is done
     *     // process response ...
     * }
     * executor.shutdown();
     * </pre>
     *
     * @param executor the {@link ExecutorService} used to execute the operation
     * @return a {@link Future} that will hold the {@link CnpBatchFileResponse} on completion
     */
    public Future<CnpBatchFileResponse> sendToCnpSFTPConcurrent(final ExecutorService executor) {
        return executor.submit(new Callable<CnpBatchFileResponse>() {
            @Override
            public CnpBatchFileResponse call() throws Exception {
                return sendToCnpSFTPConcurrent(false);
            }
        });
    }

    /**
     * Async variant of {@link #sendOnlyToCnpSFTPConcurrent()} that executes
     * on the supplied {@link ExecutorService}.
     *
     * <p>Delegates to {@link #sendOnlyToCnpSFTPConcurrent()}, which uses
     * {@link #prepareForDeliveryConcurrent()} to guarantee that the intermediate
     * temp file is named uniquely per instance.
     * Multiple instances can therefore call this method concurrently on the same executor
     * and the same {@code batchRequestFolder} without colliding.
     *
     * <p>Use this with {@link #retrieveOnlyFromCnpSFTPConcurrent(ExecutorService)} to
     * pipeline the two steps independently:
     * <pre>
     * ExecutorService executor = Executors.newFixedThreadPool(N);
     *
     * // Phase 1 – send all files in parallel
     * List&lt;Future&lt;Void&gt;&gt; sendFutures = new ArrayList&lt;&gt;();
     * for (CnpBatchFileRequest req : requests) {
     *     sendFutures.add(req.sendOnlyToCnpSFTPConcurrent(executor));
     * }
     * for (Future&lt;Void&gt; f : sendFutures) f.get();  // wait for all sends
     *
     * // Phase 2 – retrieve all responses in parallel
     * List&lt;Future&lt;CnpBatchFileResponse&gt;&gt; retrieveFutures = new ArrayList&lt;&gt;();
     * for (CnpBatchFileRequest req : requests) {
     *     retrieveFutures.add(req.retrieveOnlyFromCnpSFTPConcurrent(executor));
     * }
     * for (Future&lt;CnpBatchFileResponse&gt; f : retrieveFutures) {
     *     CnpBatchFileResponse response = f.get();
     *     // process response ...
     * }
     * executor.shutdown();
     * </pre>
     *
     * @param executor the {@link ExecutorService} used to execute the send operation
     * @return a {@link Future} that completes (with {@code null} value) when the
     *         file has been sent and any request-side cleanup is done
     */
    public Future<Void> sendOnlyToCnpSFTPConcurrent(final ExecutorService executor) {
        return executor.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                sendOnlyToCnpSFTPConcurrent();
                return null;
            }
        });
    }

    /**
     * Retrieves the batch response file from Vantiv over sFTP.
     *
     * <p>{@link #retrieveOnlyFromCnpSFTP()} is already safe to call concurrently
     * from different {@code CnpBatchFileRequest} instances: every instance owns its
     * own {@code requestFile}, {@code responseFile}, {@code communication}, and
     * {@code properties}, so there is no shared mutable state between instances.
     * This method exists for naming symmetry with the rest of the concurrent API and
     * to make the two-step send/retrieve workflow explicit.
     *
     * @return a {@link CnpBatchFileResponse} containing responses for all transactions
     * @throws CnpBatchException if an I/O error occurs during retrieval
     * @see #retrieveOnlyFromCnpSFTP()
     */
    public CnpBatchFileResponse retrieveOnlyFromCnpSFTPConcurrent() throws CnpBatchException {
        return retrieveOnlyFromCnpSFTP();
    }

    /**
     * Async variant of {@link #retrieveOnlyFromCnpSFTPConcurrent()} that executes
     * on the supplied {@link ExecutorService}.
     *
     * <p>Each instance operates on its own response file, so multiple instances can
     * execute this method on the same executor simultaneously without interference.
     *
     * @param executor the {@link ExecutorService} used to execute the retrieve operation
     * @return a {@link Future} that will hold the {@link CnpBatchFileResponse} on completion
     */
    public Future<CnpBatchFileResponse> retrieveOnlyFromCnpSFTPConcurrent(final ExecutorService executor) {
        return executor.submit(new Callable<CnpBatchFileResponse>() {
            @Override
            public CnpBatchFileResponse call() throws Exception {
                return retrieveOnlyFromCnpSFTPConcurrent();
            }
        });
    }

}

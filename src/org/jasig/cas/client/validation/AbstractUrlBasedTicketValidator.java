/*
 * Licensed to Jasig under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Jasig licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License.  You may obtain a
 * copy of the License at the following location:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jasig.cas.client.validation;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.jasig.cas.client.ssl.HttpURLConnectionFactory;
import org.jasig.cas.client.ssl.HttpsURLConnectionFactory;
import org.jasig.cas.client.util.CommonUtils;
import org.jasig.cas.client.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract validator implementation for tickets that must be validated against a server.
 *
 * @author Scott Battaglia
 * @since 3.1
 */
public abstract class AbstractUrlBasedTicketValidator implements TicketValidator {
	
	private static final String CONST_PARAM_TGT_TICKET = "targetTicket";

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * URLConnection factory instance to use when making validation requests to the CAS server.
     * Defaults to {@link HttpsURLConnectionFactory}
     */
    private HttpURLConnectionFactory urlConnectionFactory = new HttpsURLConnectionFactory();

    /**
     * Prefix for the CAS server.   Should be everything up to the url endpoint, including the /.
     *
     * i.e. https://cas.rutgers.edu/
     */
    private final String casServerUrlPrefix;

    /**
     * Whether the request include a renew or not.
     */
    private boolean renew;

    /**
     * A map containing custom parameters to pass to the validation url.
     */
    private Map<String, String> customParameters;

    private String encoding;

    /**
     * Constructs a new TicketValidator with the casServerUrlPrefix.
     *
     * @param casServerUrlPrefix the location of the CAS server.
     */
    protected AbstractUrlBasedTicketValidator(final String casServerUrlPrefix) {
        this.casServerUrlPrefix = casServerUrlPrefix;
        CommonUtils.assertNotNull(this.casServerUrlPrefix, "casServerUrlPrefix cannot be null.");
    }

    /**
     * Template method for ticket validators that need to provide additional parameters to the validation url.
     *
     * @param urlParameters the map containing the parameters.
     */
    protected void populateUrlAttributeMap(final Map<String, String> urlParameters) {
        // nothing to do
    }

    /**
     * The endpoint of the validation URL.  Should be relative (i.e. not start with a "/").  I.e. validate or serviceValidate.
     * @return the endpoint of the validation URL.
     */
    protected abstract String getUrlSuffix();

    /**
     * Disable XML Schema validation.  Note, setting this to true may not be reversable. Defaults to false. Setting it to false
     * after setting it to true may not have any affect.
     *
     * @param disabled whether to disable or not.
     */
    protected abstract void setDisableXmlSchemaValidation(boolean disabled);

    /**
     * Constructs the URL to send the validation request to.
     *
     * @param ticket the ticket to be validated.
     * @param serviceUrl the service identifier.
     * @return the fully constructed URL.
     */
    protected final String constructValidationUrl(final String ticket, final String serviceUrl) {
        final Map<String, String> urlParameters = new HashMap<String, String>();

        logger.debug("Placing URL parameters in map.");
        urlParameters.put("ticket", ticket);
        urlParameters.put("service", serviceUrl);

        if (this.renew) {
            urlParameters.put("renew", "true");
        }

        logger.debug("Calling template URL attribute map.");
        populateUrlAttributeMap(urlParameters);

        logger.debug("Loading custom parameters from configuration.");
        if (this.customParameters != null) {
            urlParameters.putAll(this.customParameters);
        }

        final String suffix = getUrlSuffix();
        final StringBuilder buffer = new StringBuilder(urlParameters.size() * 10 + this.casServerUrlPrefix.length()
                + suffix.length() + 1);

        int i = 0;

        buffer.append(this.casServerUrlPrefix);
        if (!this.casServerUrlPrefix.endsWith("/")) {
            buffer.append("/");
        }
        buffer.append(suffix);

        for (Map.Entry<String, String> entry : urlParameters.entrySet()) {
            final String key = entry.getKey();
            final String value = entry.getValue();

            if (value != null) {
                buffer.append(i++ == 0 ? "?" : "&");
                buffer.append(key);
                buffer.append("=");
                final String encodedValue = encodeUrl(value);
                buffer.append(encodedValue);
            }
        }

        return buffer.toString();

    }

    /**
     * Encodes a URL using the URLEncoder format.
     *
     * @param url the url to encode.
     * @return the encoded url, or the original url if "UTF-8" character encoding could not be found.                       
     */
    protected final String encodeUrl(final String url) {
        if (url == null) {
            return null;
        }

        try {
            return URLEncoder.encode(url, "UTF-8");
        } catch (final UnsupportedEncodingException e) {
            return url;
        }
    }

    /**
     * Parses the response from the server into a CAS Assertion.
     *
     * @param response the response from the server, in any format.
     * @return the CAS assertion if one could be parsed from the response.
     * @throws TicketValidationException if an Assertion could not be created.
     *
     */
    protected abstract Assertion parseResponseFromServer(final String response) throws TicketValidationException;

    /**
     * Contacts the CAS Server to retrieve the response for the ticket validation.
     *
     * @param validationUrl the url to send the validation request to.
     * @param ticket the ticket to validate.
     * @return the response from the CAS server.
     */

    protected abstract String retrieveResponseFromServer(URL validationUrl, String ticket);

    @SuppressWarnings("deprecation")
	public final Assertion validate(final String ticket, final String service) throws TicketValidationException {
        final String validationUrl = constructValidationUrl(ticket, service);
        logger.debug("Constructing validation url: {}", validationUrl);
         
        try {
            logger.debug("Retrieving response from server.");
            //final String serverResponse = retrieveResponseFromServer(new URL(validationUrl), ticket);
            final String serverResponse;
            if(validationUrl.indexOf("targetTicket")>-1){
            	String newValidationUrl = removeTargetTicket(validationUrl);
            	final String webService = PropertiesUtil.getPropertiesValue("webService");
            	if(StringUtils.isNotBlank(webService)){
            		int start = newValidationUrl.indexOf("/serviceValidate");
            		newValidationUrl = webService + newValidationUrl.substring(start);
            	}
            	serverResponse = retrieveResponseFromServer(new URL(newValidationUrl), ticket);
            }else{
            	serverResponse = retrieveResponseFromServer(new URL(validationUrl), ticket);
            }

            if (serverResponse == null) {
                throw new TicketValidationException("The CAS server returned no response.");
            }

            logger.debug("Server response: {}", serverResponse);

            return parseResponseFromServer(serverResponse);
        } catch (final MalformedURLException e) {
            throw new TicketValidationException(e);
        }
    }
    
    private final String removeTargetTicket(String validationUrl) {
    	String newValidationUrl = "";
    	int serviceIndex = validationUrl.indexOf("&service=");
    	String serviceValidate = validationUrl.substring(0,serviceIndex+9);
    	validationUrl = validationUrl.substring(serviceIndex+9);
    	try{
    		validationUrl = java.net.URLDecoder.decode(validationUrl,"utf-8");
    	}catch(UnsupportedEncodingException e){
    		logger.error(e.getMessage());
    	}catch(Exception e){
    		logger.error(e.getMessage());
    	}
    	int start = validationUrl.indexOf(CONST_PARAM_TGT_TICKET);
    	newValidationUrl = validationUrl.substring(start + CONST_PARAM_TGT_TICKET.length() + 1);
    	int end = newValidationUrl.indexOf("&");
    	// 编码票据在中间出现
    	if(end > -1){
    		newValidationUrl = validationUrl.substring(0,start-1) + validationUrl.substring(end);
    	}else{
    		newValidationUrl = validationUrl.substring(0,start-1);
    	}
    	try{
    		newValidationUrl = java.net.URLEncoder.encode(newValidationUrl,"utf-8");
    	}catch(UnsupportedEncodingException e){
    		logger.error(e.getMessage());
    	}catch(Exception e){
    		logger.error(e.getMessage());
    	}
    	newValidationUrl = serviceValidate + newValidationUrl;
    	return newValidationUrl;
    }

    public final void setRenew(final boolean renew) {
        this.renew = renew;
    }

    public final void setCustomParameters(final Map<String, String> customParameters) {
        this.customParameters = customParameters;
    }

    public final void setEncoding(final String encoding) {
        this.encoding = encoding;
    }

    protected final String getEncoding() {
        return this.encoding;
    }

    protected final boolean isRenew() {
        return this.renew;
    }

    protected final String getCasServerUrlPrefix() {
        return this.casServerUrlPrefix;
    }

    protected final Map<String, String> getCustomParameters() {
        return this.customParameters;
    }

    protected HttpURLConnectionFactory getURLConnectionFactory() {
        return this.urlConnectionFactory;
    }

    public void setURLConnectionFactory(final HttpURLConnectionFactory urlConnectionFactory) {
        this.urlConnectionFactory = urlConnectionFactory;
    }
}

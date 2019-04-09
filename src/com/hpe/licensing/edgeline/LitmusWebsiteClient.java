package com.hpe.licensing.edgeline;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.*;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.message.BasicNameValuePair;
import org.apache.log4j.*;


public class LitmusWebsiteClient {
	private static final String LITMUS_ERROR_CREATE_LICENSE = "Error creating license";
	private static final String LABEL_ERROR_CREATE_LICENSE = "Error creating license";
	private static final String LITMUS_OK_CREATE_LICENSE = "License created";
	private static final String LITMUS_KEY_WRAPPER1 = "<pre>";
	private static final String LITMUS_KEY_WRAPPER2 = "</pre>";
	
	// Proxy
	public static final String ProxyMethod = "http";
	public static final String ProxyServer = "web-proxy.houston.hpecorp.net";
	public static final int ProxyPort= 8088;

	// Transaction traces
	private static final String logFilenamePrefix="/opt/sasuapps/slm/log/litmus/litmus.";
	private static final String logFilenameWeek="yyyy.ww";
	private static final String logDateFormat="yyyyMMdd|HH:mm:ss";
	private static final SimpleDateFormat logFilenameSdf = new SimpleDateFormat(logFilenameWeek);
	private static final SimpleDateFormat logSdf = new SimpleDateFormat(logDateFormat);
	private static final String logFilenameExt = ".log";
	
	
	
	
	public static void main(String[] args) throws ClientProtocolException, IOException {
		int timeoutInSeconds = 10;
		String litmusURL = "http://activate.id/cgi-bin/litmus_mklic";
		HttpHost proxy = new HttpHost(ProxyServer,ProxyPort,ProxyMethod);
		
		Logger logger = Logger.getLogger(LitmusWebsiteClient.class);
		System.out.println("START");
		LitmusWebsiteClient client = new LitmusWebsiteClient(litmusURL,proxy,timeoutInSeconds,logger);
		debug(client,"key","123654789a");
		debug(client,"5690-1256-5876-0714","11aabb22cc33");
		debug(client,"5691-1256-8806-1402","11aabb22cc44");
		debug(client,"5692-1256-0266-6458","11aabb22cc44");
		System.out.println("END");
	}
	

	/**
	 * Creates a Litmus client
	 * @param litmusurl	Litmus URL
	 * @param proxy	proxy to be used, null if no proxy required
	 * @param timeoutinseconds	timeout in seconds
	 * @param logger	Logger
	 */
	public LitmusWebsiteClient(String litmusurl, HttpHost proxy, int timeoutinseconds, Logger logger) {
		this.logger = logger;
		this.url=litmusurl;
		this.timeout=timeoutinseconds;
		this.proxy=proxy;
	}

	/**
	 * Goes to Litmus website and gets a license key
	 * @param key	token key
	 * @param hostid	host id
	 * @return	license key
	 * @throws LicenseKeyException with error message
	 */
	public String getLicenseKey(String key, String hostid) throws LicenseKeyException  {
		logger.info("key=["+key+"] hostid=["+hostid+"]");
		
		if(key==null||key.trim().length()==0){
			traceTransaction(key, hostid, "E|key is null/empty");
			throwError("key is null/empty");
		}
		if(hostid==null||hostid.trim().length()==0){
			traceTransaction(key, hostid, "E|hostid is null/empty");
			throwError("hostid is null/empty");
		}
		
		CloseableHttpClient httpClient = createClient();
		HttpPost httpPost = createRequest(key, hostid);
		logger.debug("Executing request " + httpPost.getRequestLine());
		String body=executeRequest(httpClient, httpPost);
		closeClient(httpClient);
		try{
			String str=parseResponse(body,key,hostid);
			traceTransaction(key, hostid, "O|OK");
			return str;
		} catch(LicenseKeyException e){
			traceTransaction(key, hostid, "E|"+e.getMessage());
			throw e;
		}

	}

	
/* ********************************************************************	
 *
 *    ######
 *    #     #  #####      #    #    #    ##     #####  ######
 *    #     #  #    #     #    #    #   #  #      #    #
 *    ######   #    #     #    #    #  #    #     #    #####
 *    #        #####      #    #    #  ######     #    #
 *    #        #   #      #     #  #   #    #     #    #
 *    #        #    #     #      ##    #    #     #    ######
 *        
************************************************************************/
	private void closeClient(CloseableHttpClient httpClient) {
		try {
			httpClient.close();
		} catch (IOException e) {
			logger.error(e);
		}
	}


	private void throwError(String msg) throws LicenseKeyException{
		logger.error(msg);
		throw new LicenseKeyException(msg);
	}
	
	/**
	 * Parse response from Litmus
	 * @param body	Litmus' response body
	 * @param key	key submitted by customer
	 * @param hostid	host id submitted by customer
	 * @return	license key
	 * @throws LicenseKeyException	if no key was generated or found
	 */
	private String parseResponse(String body, String key, String hostid) throws LicenseKeyException{
		if (body.contains(LITMUS_ERROR_CREATE_LICENSE)) {
			//<html><body><h3>Error creating license. status=-1021</h3><h5>Activation key not found in database</h5>RLM Activation Pro server version 13.0BL2 (actver=13.0BL2)	<br>Activation key used: key</body></html>
			logger.error(body);
			StringTokenizer multiTokenizer = new StringTokenizer(body.replace("</h5>", "<h5>").replace("<h5>", "£"), "£");
			String msg=null;
			String item=multiTokenizer.nextToken();
			while (multiTokenizer.hasMoreTokens())
			{
				item=multiTokenizer.nextToken();
				if(isBlank(msg)) msg=item;
			    logger.error(item);
			}
			if(isBlank(msg)) msg=LABEL_ERROR_CREATE_LICENSE;
			throwError(msg);
		}
		if (body.contains(LITMUS_OK_CREATE_LICENSE)) {
		/*
			 <html><body><h3>License created:</h3><pre>LICENSE litmus otlink-std 2.0 8-apr-2019 uncounted hostid=11aabb22cc44 
			  platforms="x64_l arm9_l" issued=29-mar-2019
			  akey=5691-1256-8806-1402 options=devices=3,tags=250 _ck=d850e05601
			  sig="60P04503H8UPVN7VPCNSQCJR3J5VS2N0PWFX9H822GPD39PG3VBFB574R7P0B8
			  QTNMM2N0NPGG"
			</pre><h5>Cut and paste this license into your license file</h5></h5></body></html>
		 */
			String tmp=body;
			int i = body.indexOf(LITMUS_KEY_WRAPPER1);
			if(i>0){
				tmp=body.substring(i+LITMUS_KEY_WRAPPER1.length());
				i=tmp.indexOf(LITMUS_KEY_WRAPPER2);
				if(i>0){
					tmp=tmp.substring(0, i);
					logger.debug(body);
					logger.info("OT link key for ["+key+"]/["+hostid+"] is ["+tmp+"]");
					return tmp;
				}
			}
		}
		// if we've reached here, we couldn't find the key or an unexpected error occurred
		logger.error(body);
		throw new LicenseKeyException(LABEL_ERROR_CREATE_LICENSE);
	}
	
	private CloseableHttpClient createClient(){
		RequestConfig config = RequestConfig.custom()
				  .setConnectTimeout(timeout * 1000)
				  .setConnectionRequestTimeout(timeout * 1000)
				  .setSocketTimeout(timeout * 1000).build();
		CloseableHttpClient client = null;
		if(proxy!=null){
			DefaultProxyRoutePlanner routePlanner = new DefaultProxyRoutePlanner(proxy);
			client = HttpClientBuilder.create().setDefaultRequestConfig(config).setRoutePlanner(routePlanner).build();
		} else client = HttpClientBuilder.create().setDefaultRequestConfig(config).build();
		
		return client;
	}
	
	/**
	 * Builds the POST request as if it was coming from user submitting in LITMUS website
	 * @param key
	 * @param hostid
	 * @return
	 * @throws LicenseKeyException
	 */
	private HttpPost createRequest(String key, String hostid) throws LicenseKeyException{
	    HttpPost httpPost = new HttpPost(url);
	    List<NameValuePair> params = new ArrayList<NameValuePair>();
	    params.add(new BasicNameValuePair("akey", key));
	    params.add(new BasicNameValuePair("hostid", hostid));
	    params.add(new BasicNameValuePair("count", "1"));
	    params.add(new BasicNameValuePair("extra", ""));
	    params.add(new BasicNameValuePair("log", ""));
	    params.add(new BasicNameValuePair("hostname", ""));
		try {
			httpPost.setEntity(new UrlEncodedFormEntity(params));
		} catch (UnsupportedEncodingException e) {
			throwError("UnsupportedEncodingException: "+e.getMessage());
		}
	    httpPost.setHeader("User-Agent", "Mozilla/5.0"); // add header`
	    return httpPost;
	}
	
	private String executeRequest(CloseableHttpClient httpClient, HttpUriRequest httpPost) throws LicenseKeyException{
		try {
		    ResponseHandler<String> handler = new BasicResponseHandler();
		    HttpResponse response = httpClient.execute(httpPost);
		    String body = handler.handleResponse(response);
		    int code = response.getStatusLine().getStatusCode();
			if(code!=200) {
				throwError("invalid response code ["+code+"]");
			}
		    return body;
		} catch (ClientProtocolException e) {
			throwError("ClientProtocolException: "+e.getMessage());
		} catch (IOException e) {
			throwError("IOException: "+e.getMessage());
		}
		return null;
	}
	
	
	/**
	 * Utility method to check if a string is either null or empty
	 * @param in String to be tested
	 * @return whether string was null or empty
	 */
	private static boolean isBlank(String in){
		if(in==null) return true;
		if(in.trim().length()==0) return true;
		return false;
	}
	
	public static void debug(LitmusWebsiteClient client, String key, String hostid){
		try {
			System.out.println(client.getLicenseKey(key,hostid));
		}
		catch (Exception e){
			System.err.println(e.getMessage());
		}
	}
	/**
	 * Trace a transaction
	 * @param key
	 * @param hostid
	 * @param result
	 */
	private void traceTransaction(String key, String hostid,String result){
		String filename= logFilenamePrefix+logFilenameSdf.format(new Date())+logFilenameExt;
		String msg=logSdf.format(new Date())+"|"+key+"|"+hostid+"|"+result;
		try {
		    PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(filename, true)));
		    out.println(msg);
		    out.close();
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
	}

	// fields
	private Logger logger=null;
	private String url;
	private int timeout;
	private HttpHost proxy;

	/**
	 * Custom exception
	 */
	public class LicenseKeyException extends Exception {
		private static final long serialVersionUID = -4652924706525143974L;
		public LicenseKeyException(String message) {
	        super(message);
	    }
	}

}

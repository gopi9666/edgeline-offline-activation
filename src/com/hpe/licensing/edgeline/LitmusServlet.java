package com.hpe.licensing.edgeline;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.DatatypeConverter;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.http.HttpHost;
import org.apache.log4j.Logger;

import com.hpe.licensing.edgeline.LitmusWebsiteClient.LicenseKeyException;




public class LitmusServlet extends HttpServlet {
	private static final long serialVersionUID = 6322585781570847737L;

	// Litmus config
	private static final int timeoutInSeconds = 10;
	
	
	// Images, fields, sizes, labels...
	private static final String FORM_ID = "getLicenseKey";
	private static final String FORM_SUBMIT_ID = "submit";
	private static final String DOWNLOAD_ID = "download";
	private static final String SHORTCUT_ICON = "img/hpe-shortcut.png";
	private static final String ACTIV_KEY_REGEX = "^[0-9]{4}-[0-9]{4}-[0-9]{4}-[0-9]{4}$";


	private static final String HELP_ACTIV_KEY = "The activation key has been provided to you when registering your product.<br/>It is formatted as &lt;4 digits&gt;-&lt;4 digits&gt;-&lt;4 digits&gt;-&lt;4 digits&gt;";
	private static final String HELP_HOST_ID = "The host ID identifies your Edgeline appliance<br/>It can be obtained by running: <b>cli --print hostid</b>";

	
	private static final String HTML_JAVASCRIPT = "./ot.js.html";
	private static final String HTML_HEADER = "./ot.header.html";
	private static final String HTML_BODY_OPEN = "./ot.body.open.html";
	private static final String HTML_RESULT = "./ot.result.html";
	private static final String HTML_FORM= "./ot.form.html";
	private static final String HTML_FOOTER= "./ot.footer.html";

	private static final String PROP_REMOTE_URL = "litmusUrl";
	private static final String PROP_TITLE = "pageTitle";
	private static final String PROP_FORM_FIELD_HOST = "litmusHostId";
	private static final String PROP_FORM_FIELD_KEY = "litmusKey";
	private static final String PROP_SUPPORT_EMAIL = "supportEmail";
	private static final String PROP_SUPPORT_NAME = "supportDescription";


	
	
	private LitmusWebsiteClient client = null;
	private String title="HPE Edgeline Offline Activation";
	private String form_field_hostid="hostid";
	private String form_field_key="key";
	private String help_link = "<a href='mailto:licensing.support@hpe.com'>HPE Licensing Support teams</a>";
	private String error_help_text = "If this error persists, please contact "+help_link+".";


	@Override
	public void init() throws ServletException {
		super.init();
		HttpHost proxy = new HttpHost(LitmusWebsiteClient.ProxyServer,LitmusWebsiteClient.ProxyPort,LitmusWebsiteClient.ProxyMethod);
		Logger logger = Logger.getLogger(LitmusServlet.class);
		String url=null;
		String supportEmail=null;
		String supportName=null;
		
		ServletConfig config = getServletConfig(); 
		@SuppressWarnings("rawtypes")
		Enumeration e = config.getInitParameterNames();
	    while(e.hasMoreElements())
	    {
		      String name = (String) e.nextElement();  // returns the <param-name> 
		      String value = config.getInitParameter(name);  // returns <param-value> 
		      if(PROP_REMOTE_URL.equals(name)) url=value;
		      if(PROP_TITLE.equals(name)) title=value;
		      if(PROP_FORM_FIELD_HOST.equals(name)) form_field_hostid=value;
		      if(PROP_FORM_FIELD_KEY.equals(name)) form_field_key=value;
		      if(PROP_SUPPORT_EMAIL.equals(name)) supportEmail=value;
		      if(PROP_SUPPORT_NAME.equals(name)) supportName=value;
		      logger.info("Config|"+name+"=["+value+"]");
	    }
	    if(isBlank(url)) throw new ServletException("Could not load configuration: no value for ["+PROP_REMOTE_URL+"]");
	    if(!isBlank(supportName)&&!isBlank(supportEmail)){
	    	 help_link = "<a href='mailto:"+supportEmail+"'>"+supportName+"</a>";
	    	 error_help_text = "If this error persists, please contact "+help_link+".";
	    }
		this.client  = new LitmusWebsiteClient(url, proxy, timeoutInSeconds, logger);

	}
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String download = req.getParameter(DOWNLOAD_ID);
		String hostid = req.getParameter(form_field_hostid);
		if(!isBlank(download)){
			downloadKey(resp, hostid,download.trim());
			return;
		}
		displayPage(resp);
	}
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String key = req.getParameter(form_field_key);
		String hostid = req.getParameter(form_field_hostid);		
		String download = req.getParameter(DOWNLOAD_ID);
		if(!isBlank(download)){
			downloadKey(resp, hostid,download.trim());
			return;
		}

		if(isBlank(hostid)||isBlank(key)) {
			displayPage(resp,null,"All fields are required.",key,hostid);
			return;
		}
		if(this.client==null) {
			displayPage(resp,null,"Internal error (CLT_INIT)",key,hostid);
			return;
		}
		if(!key.matches(ACTIV_KEY_REGEX)){
			displayPage(resp,null,"The activation key is incorrect",key,hostid);
			return;
			
		}
		String licenseKey = null;
		try {
			licenseKey = client.getLicenseKey(key.trim(), hostid.trim());
			displayPage(resp,licenseKey,null,key,hostid);
			return;
		} catch (LicenseKeyException e) {
			displayPage(resp,null,e.getMessage(),key,hostid);
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
	
	/**
	 * Displays the start page with the form
	 * @param resp HttpServletResponse
	 * @throws IOException
	 */
	private void displayPage(HttpServletResponse resp) throws IOException{
		displayPage(resp,null,null,null,null);
	}
	
	/**
	 * Displays the activation page with potentially an error or the license key
	 * @param resp HttpServletResponse
	 * @param licenseKey	license key to display
	 * @param error	error text to display (will be truncated to 50 chars)
	 * @param key	the key customer has entered
	 * @param hostid the host id customer has entered
	 * @throws IOException
	 */
	private void displayPage(HttpServletResponse resp, String licenseKey, String error,String key,String hostid) throws IOException{
		resp.setContentType("text/html");
		PrintWriter out = resp.getWriter();
		
		
		Collection<String> lines = readResource(HTML_HEADER);
		for(String line:lines)
			out.println(line.replace("+TITLE+",title)
					.replace("+SHORTCUT+", SHORTCUT_ICON)
					);
		lines = readResource(HTML_BODY_OPEN);
		for(String line:lines)
			out.println(line.replace("+TITLE+",title));
		
		if(isBlank(licenseKey)){
			if(!isBlank(error)) {
				out.println("<div class='error'><h4>An error occurred, we could not create your license key.</h4>");
				out.println("<p>"+truncate(error,50)+"<br/>"+error_help_text+"</p></div>");
			}
			lines = readResource(HTML_FORM);
			for(String line:lines)
				out.println(line.replace("+FORM_FIELD_HOSTID+",form_field_hostid)
								.replace("+FORM_FIELD_KEY+",form_field_key)
								.replace("+FORM_ID+",FORM_ID)
								.replace("+HELP_ACTIV_KEY+",HELP_ACTIV_KEY)
								.replace("+HELP_HOST_ID+",HELP_HOST_ID)
								.replace("+FORM_SUBMIT_ID+",FORM_SUBMIT_ID)
								.replace("+KEY_VALUE+",(key==null?"":key.trim()))
								.replace("+HOSTID_VALUE+",(hostid==null?"":hostid.trim()))
						);
			} else {
			lines = readResource(HTML_RESULT);
			for(String line:lines)
				out.println(line.replace("+FORM_FIELD_HOSTID+",form_field_hostid)
								.replace("+DOWNLOAD_ID+",DOWNLOAD_ID)
								.replace("+licenseKey+",licenseKey)
								.replace("+hostid+",hostid)
								.replace("+encodedKey+",encodeKey(licenseKey))
						);
		}
		lines = readResource(HTML_JAVASCRIPT);
		for(String line:lines)
			out.println(line.replace("+FORM_ID+",FORM_ID)
							.replace("+FORM_FIELD_KEY+",form_field_key)
							.replace("+FORM_FIELD_HOSTID+",form_field_hostid)
							.replace("+ACTIV_KEY_REGEX+",ACTIV_KEY_REGEX)
					);

		readResource(out, HTML_FOOTER);
	}
	
	
	/**
	 * Encode key so it can be passed as a URL parameter
	 * @param key	key to be encoded
	 * @return base64 string
	 */
	protected static String encodeKey(String key){
		if(isBlank(key)) return "";
		return DatatypeConverter.printBase64Binary(key.getBytes()).replace("=","%3D").replace("/","%2F");		
	}
	
	/**
	 * Decode a key from a URL encoded base64 value
	 * @param key	key to be encoded
	 * @return base64 string, url encoded
	 */
	protected static byte[] decodeKey(String key){
		if(isBlank(key)) return new byte[0];
		return DatatypeConverter.parseBase64Binary(StringEscapeUtils.unescapeHtml4(key.replace("%3D","=").replace("%2F","/")));		
	}

	/**
	 * Pushes the key to customer as a text file
	 * @param resp HttpServletResponse
	 * @param hostid
	 * @param download
	 * @throws IOException
	 */
	private void downloadKey(HttpServletResponse resp,String hostid, String download) throws IOException{
		resp.setContentType("text/plain");
        System.out.println("["+download+"]");
        System.out.println("["+new String(DatatypeConverter.parseBase64Binary(download))+"]");
		byte[] key = decodeKey(download);
		resp.setContentLength(key.length);
		String filename="OT_link";
		if(!isBlank(hostid)) filename+="."+hostid.replace(".", "");
		filename+=".txt";
        resp.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        ServletOutputStream outStream = resp.getOutputStream();
        System.out.println("["+new String(key)+"]");
        outStream.write(key);
        outStream.close();
	}
	
	
	/** 
	 * Utility method to truncate a string
	 * @param in	String to be truncated
	 * @param len	max length
	 * @return truncated string
	 */
	private static String truncate(String in, int len){
		if(isBlank(in)) return "";
		if(in.length()<=len) return in;
		return in.substring(0,len);
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

	public static void readResource(PrintWriter out, String filename){
		Collection<String>lines = readResource(filename);
		for(String line:lines) out.println(line);

	}
	public static Collection<String> readResource(String filename){
		Collection<String> lines = new ArrayList<String>();
		BufferedReader br=null;
	      try {
	  			InputStream is=LitmusWebsiteClient.class.getResourceAsStream(filename);
	  			if(is==null) return lines;
	  			InputStreamReader ir = new InputStreamReader(is);
	  			if(is==null||ir==null) return lines;
	            br = new BufferedReader(ir);
	            String readLine = "";
	            while ((readLine = br.readLine()) != null) {
	                lines.add(readLine);
	            }
	    		return lines;
	        } catch (Exception e) {
	            e.printStackTrace();
	            return new ArrayList<String>();
	        } finally{
	            if(br!=null)
					try {
						br.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
	        }
	}
	
}

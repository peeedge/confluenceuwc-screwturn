/**
 * Created by IntelliJ IDEA.
 * User: brendan
 * Date: Apr 5, 2006
 * Time: 4:32:57 PM
 * To change this template use File | Settings | File Templates.
 */
package com.atlassian.uwc.ui.xmlrpcwrapperOld;

import com.atlassian.uwc.ui.ConfluenceSettingsForm;
import com.atlassian.uwc.ui.FileUtils;
import com.atlassian.uwc.ui.UWCForm2;
import com.atlassian.uwc.ui.UWCUserSettings;

import org.apache.log4j.Logger;
import org.apache.xmlrpc.XmlRpcClient;
import org.apache.xmlrpc.XmlRpcException;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.Vector;

/**
 * The RemoteWikiBrokerOld will handle the the step of sending
 * or receiving remote objects to and from Confluence via XMLRPC.
 * <p/>
 * XMLRPC is proving to be the most stable means of communicating remotely
 * with Confluence at this time. 2006-04
 * <p/>
 * This class will also have some utility methods for managing the life cycle
 * of the remote objects.
 * <p/>
 * If we ever decide to explore an alternative way of moving remote objects to
 * and from Confluence we'll want to extract an interface from this...we probably
 * should anyway but one thing at a time :)
 */
public class RemoteWikiBrokerOld {

    Logger log = Logger.getLogger("RemoteWikiBrokerOld");

    /**
     * singleton stuff - generated by IDEA
     */
    private static RemoteWikiBrokerOld ourInstance = new RemoteWikiBrokerOld();
    private XmlRpcClient clientConnection = null;
    private String loginToken = null;
    private long lastLoginTimeStamp = 0;
    /**
     * tracks whether a new login is required
     */
    boolean reloginNeeded = false;

    public void needNewLogin() {
        reloginNeeded = true;
    }

    /**
     * singleton stuff  - generated by IDEA
     */
    public static RemoteWikiBrokerOld getInstance() {
        return ourInstance;
    }

    /**
     * singleton stuff  - generated by IDEA
     */
    private RemoteWikiBrokerOld() {
    }

    /**
     * Creates a local page metadata object which can then be sent
     * to Confluence with a 'store' method
     *
     * @param title
     * @param content
     * @return
     * @see PageForXmlRpcOld
     */
    public PageForXmlRpcOld createPage(String title, String content) {
        PageForXmlRpcOld page = new PageForXmlRpcOld();
        page.setTitle(title);
        page.setContent(content);
        return page;
    }

    /**
     * calls storeNewOrUpdatePage(PageForXmlRpcOld page, String space) using space
     * currently stored in the settings form
     *
     * @param page
     * @return
     */
    public PageForXmlRpcOld storeNewOrUpdatePage(PageForXmlRpcOld page) {
        ConfluenceSettingsForm confSettings = UWCForm2.getInstance().getConfluenceSettingsForm();
        String space = confSettings.getSpaceName();
        PageForXmlRpcOld resultPage;

        resultPage = storeNewOrUpdatePage(page, space);


        return resultPage;
    }

    /**
     * A convenience method to get the pageId from Confluence with only a space
     * name and a page title.
     *
     * @param space
     * @param pageTitle
     * @return
     */
    public String getPageIdFromConfluence(String space, String pageTitle) {
        PageForXmlRpcOld page = populatePageXmlRpcData(space, pageTitle);
        return page.getId();
    }

    /**
     * populate all the data in a PageForXmlRpcOld from the minimum parameters
     *
     * @param space     the space name
     * @param pageTitle the title of the page
     * @return a populated PageForXmlRpcOld object or an empty object if the page does not exist
     */
    public PageForXmlRpcOld populatePageXmlRpcData(String space, String pageTitle) {
        PageForXmlRpcOld page = new PageForXmlRpcOld();
        Vector<Serializable> paramsVector = new Vector<Serializable>();
        loginToken = getLoginToken();
        paramsVector.add(loginToken);
        paramsVector.add(space);
        paramsVector.add(pageTitle);
        XmlRpcClient client = getXMLRPCClient();
        try {
            page.setPageParams(client.execute("confluence1.getPage", paramsVector));
        } catch (XmlRpcException e) {
            // page may already exist
            log.info("could not retrieve infor for page space:title - " + space + ":" + pageTitle);
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        return page;
    }

    /**
     * @param page
     * @return
     */
    public PageForXmlRpcOld storeNewOrUpdatePage(PageForXmlRpcOld page, String space) {
        // get a handle to the XMLRPC client obj, this method should be called
        // to manage the connection and make sure it's live even though there
        // is a class var.  This is actually called here for coding clarity.
        XmlRpcClient client = getXMLRPCClient();

        // make sure loginToken is up to date
        String loginToken = getLoginToken();

        Vector<Serializable> paramsVector = new Vector<Serializable>();
        // add the login token
        paramsVector.add(loginToken);

        // add the page  ---------------------
        page.setSpace(space);
        paramsVector.add(page.getPageParams());

        PageForXmlRpcOld resultPage = new PageForXmlRpcOld();
        // write the page  ---------------------
        try {
            resultPage.setPageParams(client.execute("confluence1.storePage", paramsVector));
            return resultPage;
        } catch (XmlRpcException e) {
            if (e.getMessage().contains("already exists")) {
                log.info("This page already exists, now updating - space:title - " + space + ":" + page.getTitle());
            } else {
                // Some other error occurred
                log.error("Error while adding page " + page.getTitle(), e);
            }
        } catch (IOException e) {
            log.error("Error while adding page " + page.getTitle(), e);
        }
        // page may already exist, get more info on page  ---------------------
        resultPage = populatePageXmlRpcData(space, page.getTitle());

        if (page.getContent() == null || "".equals(page.getContent())) {
            // Do not overwrite existing pages with empty ones.
            return resultPage;
        }
        // write the page ---------------------
        loginToken = getLoginToken();

        paramsVector = new Vector<Serializable>();
        // add the login token
        paramsVector.add(loginToken);
        page.setSpace(space);
        resultPage.setContent(page.getContent());
        paramsVector.add(resultPage.getPageParams());

        // write the page
        try {
            resultPage.setPageParams(client.execute("confluence1.storePage", paramsVector));
        } catch (XmlRpcException e) {
            log.error("Failed to update page  " + page.getTitle(), e);
        } catch (IOException e) {
            log.error("Failed to update page " + page.getTitle(), e);
        }

        return resultPage;

    }

    /**
     * get the login token for the XmlRpc call.
     * Reuse the existing one or acquire a new one from the server if needed.
     * Confluence server login token is set as valid for 30 min. by default
     *
     * @return
     */
    private String getLoginToken() {
        // check login token
        long currentTimeStamp = (new Date()).getTime();
        // if 25 minutes has not passed since login
        // check last retrieval time
        if (Math.abs(currentTimeStamp - lastLoginTimeStamp) > 1500000) {
            reloginNeeded = true;
        }

        // return the token if it's still valid
        if (loginToken != null && !reloginNeeded)
            return loginToken;

        ConfluenceSettingsForm confSettings = UWCForm2.getInstance().getConfluenceSettingsForm();
        String login = confSettings.getLogin();
        String password = confSettings.getPassword();
        String spaceName = confSettings.getSpaceName();
        String url = confSettings.getUrl();

        Vector<String> loginParams = new Vector<String>(2);
        loginParams.add(login);
        loginParams.add(password);
        XmlRpcClient client = getXMLRPCClient();
        try {
            loginToken = (String) client.execute("confluence1.login", loginParams);
        } catch (XmlRpcException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        reloginNeeded = false;
        return loginToken;
    }

    
    /**
     * manage and hand back the XMLRPC client connection
     */
    protected XmlRpcClient getXMLRPCClient() {
    	//old way of doing it (with a form)
    	ConfluenceSettingsForm confSettings = UWCForm2.getInstance().getConfluenceSettingsForm();
    	return getXMLRPCClient(
	    			confSettings.getLogin(),
	    			confSettings.getPassword(),
	    			confSettings.getSpaceName(),
	    			confSettings.getUrl()
    			);
    }
    
    protected XmlRpcClient getXMLRPCClient(
    		UWCUserSettings settings) {
    	return getXMLRPCClient(
    			settings.getLogin(), 
    			settings.getPassword(), 
    			settings.getSpace(), 
    			settings.getUrl());
    }
    protected XmlRpcClient getXMLRPCClient(
    		String login, String password, String space, String url) {
        //
        if (clientConnection != null) {
            return clientConnection;
        }
        // retrieve the correct URL
//        String login = confSettings.getLogin();
//        String password = confSettings.getPassword();
//        String spaceName = confSettings.getSpaceName();
//        String url = confSettings.getUrl();
        String connectionURL;
        if (url.startsWith("http://")) {
            connectionURL = url + "/rpc/xmlrpc";
        } else {
            connectionURL = "http://" + url + "/rpc/xmlrpc";
        }
        try {
            clientConnection = new XmlRpcClient(connectionURL);
        } catch (MalformedURLException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        return clientConnection;

    }

    /**
     * Only write this new page if an existing one with the same name
     * doesn't already exist.
     *
     * @param page
     * @return
     */
    public PageForXmlRpcOld safeStoreNewPage(PageForXmlRpcOld page) {
        return null;
    }

    /**
     * @param pageId
     * @param attachment
     * @return
     * @todo -
     */
    public AttachmentForXmlRpcOld storeAttachment(String pageId, AttachmentForXmlRpcOld attachment) throws IOException, XmlRpcException {
        Vector<Serializable> paramsVector = new Vector<Serializable>();
        loginToken = getLoginToken();
        paramsVector.add(loginToken);
        paramsVector.add(pageId);
        paramsVector.add(attachment.getPageParams());

        File file = new File(attachment.getFileLocation());
        byte fileBytes[] = FileUtils.getBytesFromFile(file);
        paramsVector.add(fileBytes);

        XmlRpcClient client = getXMLRPCClient();
        attachment.setPageParams(client.execute("confluence1.addAttachment", paramsVector));
        log.info("attachment written " + attachment.getFileName());
        return attachment;
    }
}

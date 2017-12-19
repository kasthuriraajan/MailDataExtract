package com.wso2.org;

import com.google.api.client.util.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;


import java.io.*;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Read a mail using GmailAPI!
 * Extract some details and insert them into database
 */
public class MailDataExtract {

    private static Logger logger = Logger.getLogger(MailDataExtract.class.getName());

    public static void main(String[] args) {

        Properties properties = new Properties();

        try {

            /*load a properties file*/
            InputStream input  = new FileInputStream("config.properties");
            properties.load(input);

            /*Get Credential*/
            String tokenId = properties.getProperty("tokenId");
            String db = properties.getProperty("db");
            String user = properties.getProperty("user");
            String password = properties.getProperty("password");


            storeMessage(tokenId,db,user,password);

        } catch (IOException e) {
            logger.log(Level.SEVERE,e.getMessage());

        }
    }

    /*Method for get messageId list using Gmail API*/
    private static JSONArray getMailMessageList(String tokenId) {

        /*
        url for get message list of DevServiceUpdate using filter label from GmailAPI
        maxResults parameter value can be changed
        */
        String url = "https://www.googleapis.com/gmail/v1/users/me/messages?labelIds=Label_28&maxResults=10";

        JSONArray jsonArray = null;/* to store the data to return */
        try {
            CloseableHttpClient httpClient = HttpClientBuilder.create().build();

            HttpGet request = new HttpGet(url);/* make request for the url to get message list */
            request.addHeader("content-type", "application/json");
            request.addHeader("Authorization", "Bearer " + tokenId);/* give access to request the API */

            HttpResponse response = httpClient.execute(request);
            String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");
            JSONObject jsonObject = new JSONObject(responseBody);/* change the request as a json object */

            jsonArray = new JSONArray();
            int index = 0;
            jsonArray.put(index, jsonObject);/* store the response body as json object into the json array */

            logger.log(Level.INFO,response.getStatusLine().toString());
            logger.log(Level.INFO,response.toString());

            String nextPageToken = "";/*get nextPageTokenId from response body for pagination purpose*/
            if (!jsonObject.isNull("nextPageToken")) {
                nextPageToken = jsonObject.get("nextPageToken").toString();
            }

            /*pagination to get all results*/
            while (!jsonObject.isNull("nextPageToken")) {
                index++;
                String urlNext = "https://www.googleapis.com/gmail/v1/users/me/messages?labelIds=Label_28&" +
                        "maxResults=10&pageToken=" + nextPageToken;

                HttpGet requestNext = new HttpGet(urlNext);/* make request to get next page of message list */
                requestNext.addHeader("content-type", "application/json");
                requestNext.addHeader("Authorization", "Bearer " + tokenId);

                HttpResponse responseNext = httpClient.execute(requestNext);
                String responseBodyNext = EntityUtils.toString(responseNext.getEntity(), "UTF-8");
                jsonObject = new JSONObject(responseBodyNext);/* change the request as a json object */
                jsonArray.put(index, jsonObject);/* store the response body as json object into the json array */

                logger.log(Level.INFO,responseNext.getStatusLine().toString());
                logger.log(Level.INFO,responseNext.toString());

                if (!jsonObject.isNull("nextPageToken")) {
                    nextPageToken = jsonObject.get("nextPageToken").toString();
                }
            }

        } catch (IOException exception) {
            logger.log(Level.WARNING,exception.getMessage());
        }

        return jsonArray;
    }

    /*Method to store all mail message details to DB */
    private static void storeMessage(String tokenId,String db,String user,String password){
        JSONArray jsonArray = getMailMessageList(tokenId);
        for (int i=0; i<jsonArray.length();i++)
        {
            for (int j=0;j<jsonArray.getJSONObject(i).getJSONArray("messages").length();j++)
            {
                String messageId = jsonArray.getJSONObject(i).getJSONArray("messages").getJSONObject(j)
                        .getString("id");
                addIntoDB(getMailMessage(tokenId, messageId),db,user,password);/*insert each mail details into DB */
            }
        }
    }

    /*Method to extract details from mail*/
    private static  Map<String, Object>getMailMessage(String tokenId, String messageId) {

        /* url for get a message from GmailAPI */
        String url = "https://www.googleapis.com/gmail/v1/users/me/messages/" + messageId;
        Map<String, Object> mailDetails = null;

        try {
            CloseableHttpClient httpClient = HttpClientBuilder.create().build();

            HttpGet request = new HttpGet(url);
            request.addHeader("content-type", "application/json");
            request.addHeader("Authorization", "Bearer " + tokenId);

            HttpResponse response = httpClient.execute(request);
            String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");

            JSONObject jsonObject = new JSONObject(responseBody);
            mailDetails = new HashMap<>();

            logger.log(Level.INFO,response.getStatusLine().toString());
            logger.log(Level.INFO,response.toString());

            String data = jsonObject.getJSONObject("payload").getJSONArray("parts").getJSONObject(0).
                    getJSONObject("body").get("data").toString();

            for (int i = 0; i < jsonObject.getJSONObject("payload").getJSONArray("headers").length(); i++) {

                String name = jsonObject.getJSONObject("payload").getJSONArray("headers").getJSONObject(i).
                        getString("name");
                String value = jsonObject.getJSONObject("payload").getJSONArray("headers").getJSONObject(i).
                        getString("value");

                switch (name) {
                    case "Date":
                        mailDetails.put("Date", value);
                        break;
                    case "Subject":
                        mailDetails.put("Subject", value);
                        break;
                    case "From":
                        mailDetails.put("From", value);
                        break;
                    case "To":
                        mailDetails.put("To", value);
                        break;
                    case "Cc":
                        mailDetails.put("Cc", value);
                        break;
                    case "Mailing-list":
                        mailDetails.put("Mailing-list", value);
                        break;
                }
            }

            /* decode the mail body to String */
            String body = StringUtils.newStringUtf8(org.apache.commons.codec.binary.Base64.decodeBase64(data));

            mailDetails.put("Client Name", body.split("0: Client Name")[1].split("1: Start Date")[0].trim());
            mailDetails.put("Start Date", body.split("1: Start Date")[1].split("2: End Date")[0].trim());
            mailDetails.put("End Date", body.split("2: End Date")[1].split
                    ("3: Technical problems you encountered")[0].trim());
            mailDetails.put("Technical problems you encountered", body.split
                    ("3: Technical problems you encountered")[1].split
                    ("4: Problems solved and how they were solved")[0].trim().substring(20).trim());
            mailDetails.put("Problems solved and how they were solved",body.split
                    ("4: Problems solved and how they were solved")[1].split("5: List of tasks done")[0].trim());
            mailDetails.put("List of tasks done", body.split("5: List of tasks done")[1].split
                    ("6: List of your TODOs")[0].trim());
            mailDetails.put("List of your TODOs", body.split("6: List of your TODOs")[1].split
                    ("7: Action Items for LK team")[0].trim());
            mailDetails.put("Action Items for LK team",body.split("7: Action Items for LK team")[1].split
                    ("8: Other non-technical problems")[0].trim());
            mailDetails.put("Other non-technical problems", body.split("8: Other non-technical problems")[1].split
                    ("9: Learning points")[0].trim());
            mailDetails.put("Learning points", body.split("9: Learning points")[1].split
                    ("10: Details")[0].trim());
            mailDetails.put("Details", body.split("10: Details").length>1? body.split
                    ("10: Details")[1].trim():"None");

        } catch (IOException exception) {
            logger.log(Level.WARNING,exception.getMessage());
        }

        return mailDetails;
    }

    /*Method to insert the data into DB*/
    private static void addIntoDB(Map<String, Object> mailDetails,String db,String user,String password)  {

        try {
            Connection connection = DriverManager.getConnection(db, user, password);/*Connect DB*/

            String query = "INSERT INTO weeklyUpdate(`date`,`subject`,`assignee`,`from`,`to`,`client`,`startDate`,`endDate`," +
                    "`techProblems`,`problemSolved`,`tasksDone`,`toDoList`,`actionItems`,`nonTecProblems`," +
                    "`learningPoints`,`details`)VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?);";

            PreparedStatement statement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);

            statement.setString(1, mailDetails.get("Date").toString());
            statement.setString(2, mailDetails.get("Subject").toString());
            statement.setString(3, mailDetails.get("Subject").toString().split(" by: ")[1].trim());
            statement.setString(4, mailDetails.get("From").toString());
            statement.setString(5, mailDetails.get("To").toString());
            statement.setString(6, mailDetails.get("Client Name").toString());
            Date endDate = null;
            Date startDate = null;
            try {
                endDate = new SimpleDateFormat("MM/dd/yyyy").parse( mailDetails.get("End Date").toString());
                 startDate = new SimpleDateFormat("MM/dd/yyyy").parse(mailDetails.get("Start Date").toString());
            } catch (ParseException e) {
                logger.log(Level.WARNING,e.getMessage());
            }
            statement.setDate (7, new java.sql.Date(startDate != null ? startDate.getTime() : 0));
            statement.setDate(8,new java.sql.Date(endDate != null ? endDate.getTime() : 0));
            statement.setString(9, mailDetails.get("Technical problems you encountered").toString());
            statement.setString(10, mailDetails.get("Problems solved and how they were solved").toString());
            statement.setString(11, mailDetails.get("List of tasks done").toString());
            statement.setString(12, mailDetails.get("List of your TODOs").toString());
            statement.setString(13, mailDetails.get("Action Items for LK team").toString());
            statement.setString(14, mailDetails.get("Other non-technical problems").toString());
            statement.setString(15, mailDetails.get("Learning points").toString());
            statement.setString(16, mailDetails.get("Details").toString());
            statement.executeUpdate();
            logger.log(Level.INFO,mailDetails.get("Subject").toString()+" has been inserted into " +
                    "MailUpdate.weeklyUpdate");
        }
        catch (SQLException e) {
           logger.log(Level.WARNING,e.getMessage());
        }
    }

}

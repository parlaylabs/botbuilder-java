// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.sample.servlet;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.aad.adal4j.AuthenticationException;
import com.microsoft.bot.connector.authentication.ClaimsIdentity;
import com.microsoft.bot.connector.authentication.CredentialProvider;
import com.microsoft.bot.connector.authentication.CredentialProviderImpl;
import com.microsoft.bot.connector.authentication.JwtTokenValidation;
import com.microsoft.bot.connector.authentication.MicrosoftAppCredentials;
import com.microsoft.bot.connector.implementation.ConnectorClientImpl;
import com.microsoft.bot.schema.models.Activity;
import com.microsoft.bot.schema.models.ActivityTypes;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.WebServlet;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This is the Servlet that will receive incoming Channel Activity messages.
 */
@WebServlet(name = "EchoServlet", urlPatterns = "/api/messages")
public class EchoServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(EchoServlet.class.getName());

    private ObjectMapper objectMapper;
    private CredentialProvider credentialProvider;
    private MicrosoftAppCredentials credentials;

    @Override
    public void init() throws ServletException {
        try{
            this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .findAndRegisterModules();

            // Load the application.properties from the classpath
            Properties p = new Properties();
            p.load(Thread.currentThread().getContextClassLoader().getResourceAsStream("application.properties"));

            String appId = p.getProperty("MicrosoftAppId");
            String appPassword = p.getProperty("MicrosoftAppPassword");

            this.credentialProvider = new CredentialProviderImpl(appId, appPassword);
            this.credentials = new MicrosoftAppCredentials(appId, appPassword);
        }
        catch(IOException ioe){
            throw new ServletException(ioe);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            final Activity activity = getActivity(request);
            String authHeader = request.getHeader("Authorization");

            CompletableFuture<ClaimsIdentity> authenticateRequest = JwtTokenValidation.authenticateRequest(activity, authHeader, credentialProvider);
            authenticateRequest.thenRunAsync(() -> {
                if (activity.type().equals(ActivityTypes.MESSAGE)) {
                    // reply activity with the same text
                    ConnectorClientImpl connector = new ConnectorClientImpl(activity.serviceUrl(), this.credentials);
                    connector.conversations().sendToConversation(activity.conversation().id(),
                            new Activity()
                                    .withType(ActivityTypes.MESSAGE)
                                    .withText("Echo: " + activity.text())
                                    .withRecipient(activity.from())
                                    .withFrom(activity.recipient())
                    );
                }
            });
        } catch (AuthenticationException ex) {
            response.setStatus(401);
            LOGGER.log(Level.WARNING, "Auth failed!", ex);
        } catch (Exception ex) {
            response.setStatus(500);
            LOGGER.log(Level.WARNING, "Execution failed", ex);
        }
    }

    // Creates an Activity object from the request
    private Activity getActivity(HttpServletRequest request) throws IOException, JsonParseException, JsonMappingException {
        String body = getRequestBody(request);
        LOGGER.log(Level.INFO, body);
        return objectMapper.readValue(body, Activity.class);
    }

    private String getRequestBody(HttpServletRequest request) throws IOException {
        StringBuilder buffer = new StringBuilder();
        InputStream stream = request.getInputStream();
        int rByte;
        while ((rByte = stream.read()) != -1) {
            buffer.append((char)rByte);
        }
        stream.close();
        if (buffer.length() > 0) {
            return buffer.toString();
        }
        return "";
    }
}

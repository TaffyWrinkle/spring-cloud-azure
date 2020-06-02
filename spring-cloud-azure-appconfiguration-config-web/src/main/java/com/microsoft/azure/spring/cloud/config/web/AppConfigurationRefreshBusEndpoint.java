/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for
 * license information.
 */
package com.microsoft.azure.spring.cloud.config.web;

import static com.microsoft.azure.spring.cloud.config.web.Constants.VALIDATION_CODE_FORMAT_START;
import static com.microsoft.azure.spring.cloud.config.web.Constants.VALIDATION_CODE_KEY;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.endpoint.web.annotation.ControllerEndpoint;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.spring.cloud.config.properties.AppConfigurationProviderProperties;

@ControllerEndpoint(id = "appconfiguration-refresh-bus")
public class AppConfigurationRefreshBusEndpoint {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppConfigurationRefreshBusEndpoint.class);

    private ObjectMapper objectmapper = new ObjectMapper();

    private BusPublisher busPublisher;

    private AppConfigurationProviderProperties appConfiguration;

    public AppConfigurationRefreshBusEndpoint(BusPublisher busPublisher,
            AppConfigurationProviderProperties appConfiguration) {
        this.busPublisher = busPublisher;
        this.appConfiguration = appConfiguration;
    }

    @PostMapping(value = "/")
    @ResponseBody
    public String refresh(HttpServletRequest request, HttpServletResponse response,
            @RequestParam Map<String, String> allRequestParams) throws IOException {
        if (appConfiguration.getTokenName() == null || appConfiguration.getTokenSecret() == null
                || !allRequestParams.containsKey(appConfiguration.getTokenName())
                || !allRequestParams.get(appConfiguration.getTokenName()).equals(appConfiguration.getTokenSecret())) {
            return HttpStatus.UNAUTHORIZED.getReasonPhrase();
        }

        String reference = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));

        JsonNode kvReference = objectmapper.readTree(reference);

        JsonNode validationResponse = kvReference.findValue(VALIDATION_CODE_KEY);
        if (validationResponse != null) {
            // Validating Web Hook
            return VALIDATION_CODE_FORMAT_START + validationResponse.asText() + "\"}";
        } else {
            if (busPublisher != null) {
                // Spring Bus is in use, will publish a RefreshRemoteApplicationEvent
                busPublisher.publish();
                return HttpStatus.OK.getReasonPhrase();
            } else {
                LOGGER.error("BusPublisher Not Found. Unable to Refresh.");
                return HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase();
            }
        }
    }
}

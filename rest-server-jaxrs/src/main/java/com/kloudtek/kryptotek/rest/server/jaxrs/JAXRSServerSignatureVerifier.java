/*
 * Copyright (c) 2014 Kloudtek Ltd
 */

package com.kloudtek.kryptotek.rest.server.jaxrs;

import com.kloudtek.kryptotek.rest.RESTRequestSigner;
import com.kloudtek.util.io.BoundedOutputStream;
import com.kloudtek.util.io.IOUtils;
import com.kloudtek.util.validation.ValidationUtils;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.logging.Logger;

import static com.kloudtek.kryptotek.rest.RESTRequestSigner.*;

/**
 * Created by yannick on 28/10/2014.
 */
public abstract class JAXRSServerSignatureVerifier implements ContainerRequestFilter, ContainerResponseFilter {
    private static final Logger logger = Logger.getLogger(JAXRSServerSignatureVerifier.class.getName());
    private Long contentMaxSize;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String nounce = requestContext.getHeaderString(HEADER_NOUNCE);
        String identity = requestContext.getHeaderString(HEADER_IDENTITY);
        String timestamp = requestContext.getHeaderString(HEADER_TIMESTAMP);
        String signature = requestContext.getHeaderString(HEADER_SIGNATURE);
        if (!ValidationUtils.notEmpty(nounce, identity, timestamp, signature)) {
            logger.warning("Unauthorized request (missing any of nounce, identify, timestamp, signature)");
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        }
        URI requestUri = requestContext.getUriInfo().getRequestUri();
        StringBuilder path = new StringBuilder(requestUri.getPath());
        if (requestUri.getRawQuery() != null) {
            path.append('?').append(requestUri.getRawQuery());
        }
        RESTRequestSigner restRequestSigner = new RESTRequestSigner(requestContext.getMethod(), path.toString(), nounce, timestamp, identity);
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        InputStream is = requestContext.getEntityStream();
        IOUtils.copy(is, contentMaxSize != null ? new BoundedOutputStream(content, contentMaxSize, true) : content);
        byte[] contentData = content.toByteArray();
        restRequestSigner.setContent(contentData);
        // TODO verify timestamp expiry
        // TODO verify anti-replay store
        requestContext.setEntityStream(new ByteArrayInputStream(contentData));
        if (!verifySignature(identity, restRequestSigner.getDataToSign(), signature)) {
            logger.warning("Unauthorized request (invalid signature): " + restRequestSigner.toString());
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        System.out.println();
    }

    protected abstract boolean verifySignature(String identity, byte[] dataToSign, String signature);
}
package com.dgut.common.easemob.comm.invoker;

import com.dgut.common.easemob.api.RestAPIInvoker;
import com.dgut.common.easemob.comm.MessageTemplate;
import com.dgut.common.easemob.comm.constant.HTTPMethod;
import com.dgut.common.easemob.comm.utils.RestAPIUtils;
import com.dgut.common.easemob.comm.wrapper.BodyWrapper;
import com.dgut.common.easemob.comm.wrapper.HeaderWrapper;
import com.dgut.common.easemob.comm.wrapper.QueryWrapper;
import com.dgut.common.easemob.comm.wrapper.ResponseWrapper;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LocationAwareLogger;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Created by PUNK on 2017/1/22.
 */
public class HttpClientRestAPIInvoker implements RestAPIInvoker {

    private static final Logger log = LoggerFactory.getLogger(HttpClientRestAPIInvoker.class);


    @Override
    public ResponseWrapper sendRequest(String method, String url, HeaderWrapper header, BodyWrapper body, QueryWrapper query, boolean bodyNeed) {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        ObjectNode responseNode = JsonNodeFactory.instance.objectNode();

        responseWrapper.setResponseBody(responseNode);

        if( !HTTPMethod.METHOD_GET.equalsIgnoreCase(method) && !HTTPMethod.METHOD_POST.equalsIgnoreCase(method) && !HTTPMethod.METHOD_PUT.equalsIgnoreCase(method) && !HTTPMethod.METHOD_DELETE.equalsIgnoreCase(method) ) {
            String msg = MessageTemplate.print(MessageTemplate.UNKNOW_TYPE_MSG, new String[]{method, "HTTP methods"});
            responseWrapper.addError(msg);
        }
        if( StringUtils.isBlank(url) ) {
            String msg = MessageTemplate.print(MessageTemplate.BLANK_OBJ_MSG, new String[]{"Parameter url"});
            responseWrapper.addError(msg);
        }
        if( !RestAPIUtils.match("http(s)?://([\\w-]+\\.)+[\\w-]+(/[\\w- ./?%&=]*)?", url) ) {
            String msg = MessageTemplate.print(MessageTemplate.INVAILID_FORMAT_MSG, new String[]{"Parameter url"});
            responseWrapper.addError(msg);
        }
        if( null == header ) {
            String msg = MessageTemplate.print(MessageTemplate.BLANK_OBJ_MSG, new String[]{"Parameter header"});
            responseWrapper.addError(msg);
        }

        if(bodyNeed){

            if( null == body || !body.validate() ) {
                responseWrapper.addError(MessageTemplate.INVALID_BODY_MSG);
            }
        }

        if( responseWrapper.hasError() ) {
            return responseWrapper;
        }

        log.debug("=============Request=============");
        log.debug("Method: " + method);
        log.debug("URL: " + url);
        log.debug("Header: " + header);
        log.debug("Body: " + ((null == body) ? "" : body.getBody()));
        log.debug("Query: " + query);
        log.debug("===========Request End===========");

        HttpClient client = RestAPIUtils.getHttpClient( StringUtils.startsWithIgnoreCase(url, "HTTPS") );
        URL target = null;
        try {
            target = new URL(url);
        } catch (MalformedURLException e) {
            responseWrapper.addError(e.getMessage());
            return responseWrapper;
        }

        HttpUriRequest request = null;
        HttpResponse response = null;
        try{
            if (method.equals(HTTPMethod.METHOD_POST)) {
                request = new HttpPost(target.toURI());
            }
            else if (method.equals(HTTPMethod.METHOD_PUT)) {
                request = new HttpPut(target.toURI());
            }
            else if (method.equals(HTTPMethod.METHOD_GET)) {
                request = new HttpGet(target.toURI());
            }
            else if (method.equals(HTTPMethod.METHOD_DELETE)) {
                request = new HttpDelete(target.toURI());
            }
            else {
                String msg = MessageTemplate.print(MessageTemplate.UNKNOW_TYPE_MSG, new String[]{method, "Http Method"});
                log.error(msg);
                throw new RuntimeException(msg);
            }
        } catch(URISyntaxException e) {
            responseWrapper.addError(e.getMessage());
            return responseWrapper;
        }

        if( null != body && null != body.getBody() ){
            ((HttpEntityEnclosingRequestBase) request).setEntity(new StringEntity(body.getBody().toString(), "UTF-8"));
        }
        buildHeader(request, header);
        // TODO query

        try {
//            System.out.println(getClassResource(LocationAwareLogger.class));
            response = client.execute(request);
        } catch (IOException e) {
            responseWrapper.addError(e.getMessage());
            return responseWrapper;
        }

        responseWrapper = readResponse(responseWrapper, response, false);

        log.debug("=============Response=============");
        log.debug(responseWrapper.toString());
        log.debug("===========Response End===========");
        return responseWrapper;
    }



    private void buildHeader(HttpUriRequest request, HeaderWrapper header) {
        if ( null != header && !header.getHeaders().isEmpty() ) {
            for (NameValuePair nameValuePair : header.getHeaders()) {
                request.addHeader(nameValuePair.getName(), nameValuePair.getValue());
            }
        }
    }

    public static String getClassResource(Class<?> klass) {
        return klass.getClassLoader().getResource(
                klass.getName().replace('.', '/') + ".class").toString();
    }

    private ResponseWrapper readResponse(ResponseWrapper responseWrapper, HttpResponse response, boolean isFile) {
        ObjectNode responseNode;
        HttpEntity entity = response.getEntity();
        if( null != entity ) {
            responseWrapper.setResponseStatus(response.getStatusLine().getStatusCode());

            Object responseContent;
            try {
                if(isFile){
                    responseContent = entity.getContent();
                } else {
                    responseContent = EntityUtils.toString(entity, "UTF-8");
                    EntityUtils.consume(entity);
                }
            } catch (ParseException e){
                responseWrapper.addError(e.getMessage());
                return responseWrapper;
            } catch (IOException e) {
                responseWrapper.addError(e.getMessage());
                return responseWrapper;
            }

            if(!isFile) {
                ObjectMapper mapper = new ObjectMapper();
                JsonFactory factory = mapper.getFactory();
                JsonParser jp;
                try {
                    jp = factory.createParser(responseContent.toString());
                    responseNode = mapper.readTree(jp);
                    responseWrapper.setResponseBody(responseNode);
                } catch (IOException e) {
                    log.error(MessageTemplate.STR2JSON_ERROR_MSG, e);
                    responseWrapper.addError(MessageTemplate.STR2JSON_ERROR_MSG);
                }
            } else {
                responseWrapper.setResponseBody(responseContent);
            }
        }
        return responseWrapper;
    }
}

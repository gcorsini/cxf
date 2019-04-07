package org.apache.cxf.transport.http_tomcat;

import org.apache.catalina.connector.RequestFacade;
import org.apache.cxf.Bus;
import org.apache.cxf.transport.http.HttpUrlUtil;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class TomcatHTTPHandler {

    private static final String METHOD_TRACE = "TRACE";

    protected TomcatHTTPDestination tomcatHTTPDestination;
    protected ServletContext servletContext;
    private String urlName;
    private boolean contextMatchExact;
    private Bus bus;

    public TomcatHTTPHandler(TomcatHTTPDestination thd, boolean cmExact) {
        this.contextMatchExact = cmExact;
        this.tomcatHTTPDestination = thd;
    }

    public TomcatHTTPHandler(Bus bus) {
        this.bus = bus;
    }

    public ServletContext getServletContext() {
        return servletContext;
    }

    public void setServletContext(ServletContext sc) {
        servletContext = sc;
        if (tomcatHTTPDestination != null) {
            tomcatHTTPDestination.setServletContext(sc);
        }
    }

    public void setName(String name) {
        urlName = name;
    }

    public String getName() {
        return urlName;
    }

    public void handle(String target, RequestFacade facade, HttpServletRequest request,
                       HttpServletResponse response) throws IOException, ServletException {
        if (request.getMethod().equals(METHOD_TRACE)) {
            // todo implement me
//            baseRequest.setHandled(true);
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        } else {
            if (contextMatchExact) {
                if (target.equals(urlName)) {
                    tomcatHTTPDestination.doService(servletContext, request, response);
                }
            } else {
                if (target.equals(urlName) || HttpUrlUtil.checkContextPath(urlName, target)) {
                    tomcatHTTPDestination.doService(servletContext, request, response);
                }
            }
        }

    }

    public Bus getBus() {
        return tomcatHTTPDestination != null ? tomcatHTTPDestination.getBus() : bus;
    }

}

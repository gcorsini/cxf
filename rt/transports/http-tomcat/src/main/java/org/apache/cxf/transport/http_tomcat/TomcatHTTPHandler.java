package org.apache.cxf.transport.http_tomcat;

import org.apache.catalina.connector.RequestFacade;
import org.apache.cxf.Bus;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.transport.http.HttpUrlUtil;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TomcatHTTPHandler implements Filter {

    private static final String METHOD_TRACE = "TRACE";
    private static final Logger LOG =
            LogUtils.getL7dLogger(TomcatHTTPDestination.class);

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
        LOG.log(Level.FINE, "Handling incoming message");
        if (request.getMethod().equals(METHOD_TRACE)) {
            request.setAttribute("HTTP_HANDLER", this);
            request.setAttribute("TOMCAT_DESTINATION", tomcatHTTPDestination);

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

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        System.out.println("Im in init method");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        System.out.println("Im in filter method");
        // Might be needed (based on Jetty implementation)
        request.setAttribute("HTTP_HANDLER", this);
        request.setAttribute("TOMCAT_DESTINATION", tomcatHTTPDestination);

        HttpServletResponse httpResponse = (HttpServletResponse) response;
        httpResponse.addHeader("myHeader", "myHeaderValue");
        chain.doFilter(request, httpResponse);
    }

    @Override
    public void destroy() {
        System.out.println("Im in destroy method");
        this.tomcatHTTPDestination = null;
        this.servletContext = null;
        this.urlName = null;
    }
}

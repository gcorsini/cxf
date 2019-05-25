/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cxf.transport.http_tomcat;

import org.apache.catalina.*;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.loader.WebappLoader;
import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.AbstractProtocol;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.cxf.Bus;
import org.apache.cxf.common.i18n.Message;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.common.util.PropertyUtils;
import org.apache.cxf.common.util.SystemPropertyAction;
import org.apache.cxf.configuration.jsse.TLSServerParameters;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.jaxrs.servlet.CXFNonSpringJaxrsServlet;
import org.apache.cxf.transport.HttpUriMapper;
import org.springframework.util.ClassUtils;

import javax.annotation.PostConstruct;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.InetAddress;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TomcatHTTPServerEngine implements ServerEngine {

    public static final String DO_NOT_CHECK_URL_PROP = "org.apache.cxf.transports.http_tomcat.DontCheckUrl";

    private static final Logger LOG = LogUtils.getL7dLogger(TomcatHTTPServerEngine.class);


    /**
     * This is the network port for which this engine is allocated.
     */
    private int port;
//
//    /**
//     * This is the network address for which this engine is allocated.
//     */
//    private Host host;

    /**
     * This field holds the protocol for which this engine is
     * enabled, i.e. "http" or "https".
     */
    private String protocol = "http";

    private Boolean isSessionSupport = false;
    private int sessionTimeout = -1;
    private Boolean isReuseAddress = true;
    private Boolean continuationsEnabled = true;
    private int maxIdleTime = 200000;
    private Boolean sendServerVersion = true;
    private int servantCount;
    private Tomcat server;
    private Connector connector;
    private List<Handler> handlers;
    private List<String> registedPaths = new CopyOnWriteArrayList<>();
    private int backgroundProcessorDelay;
    private String host;

    /**
     * This field holds the TLS ServerParameters that are programatically
     * configured. The tlsServerParamers (due to JAXB) holds the struct
     * placed by SpringConfig.
     */
    private TLSServerParameters tlsServerParameters;

    /**
     * This field hold the threading parameters for this particular engine.
     */
    private ThreadingParameters threadingParameters;

    private boolean configFinalized;


    public TomcatHTTPServerEngine(int port) {
        this(null, port);
    }

    public TomcatHTTPServerEngine() {
    }

    public TomcatHTTPServerEngine(String host, int port) {
        this.host = host;
        this.port = port;
    }


    @Override
    public void removeServant(URL url) {

    }

    @Override
    public TomcatHTTPHandler getServant(URL url) {
        return null;
    }

    @Override
    public void addServant(URL url, TomcatHTTPHandler handler) {
        if (shouldCheckUrl(handler.getBus())) {
            checkRegistedContext(url);
        }

        if (server == null) {
            try {
                // create a new tomcat server instance if there is no server there
                //Tomcat tomcat = new Tomcat();
                server = new Tomcat();
                server.setHostname("localhost");
                String appBase = ".";
                server.getHost().setAppBase(appBase);

                Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
                connector.setPort(getPort());

//                int port = (getPort() >= 0) ? getPort() : 0;
//                connector.setPort(port);
                Http11NioProtocol protocol = (Http11NioProtocol) connector.getProtocolHandler();
                // Use HTTPS
                //connector.setScheme("https");
                //connector.setSecure(true);
                //protocol.setSSLEnabled(true);
                //protocol.setKeyAlias("tomcat?");
                //protocol.setKeystorePass("?");
                //protocol.setKeystoreFile();
                //protocol.setSslProtocol("TLS");
//                protocol.setAddress(InetAddress.getLocalHost());

                // add connector to server
                server.getService().addConnector(connector);

                /*
                 * The server may have no handler, it might have a collection handler,
                 * it might have a one-shot. We need to add one or more of ours.
                 *
                 */
                /*int numberOfHandlers = 1;
                if (handlers != null) {
                    numberOfHandlers += handlers.size();
                }
                Handler existingHandler = server.getHandler();

                HandlerCollection handlerCollection = null;
                boolean existingHandlerCollection = existingHandler instanceof HandlerCollection;
                if (existingHandlerCollection) {
                    handlerCollection = (HandlerCollection) existingHandler;
                }

                if (!existingHandlerCollection
                        &&
                        (existingHandler != null || numberOfHandlers > 1)) {
                    handlerCollection = new HandlerCollection();
                    if (existingHandler != null) {
                        handlerCollection.addHandler(existingHandler);
                    }
                    server.setHandler(handlerCollection);
                }

                *//*
                 * At this point, the server's handler is a collection. It was either
                 * one to start, or it is now one containing only the single handler
                 * that was there to begin with.
                 *//*
                if (handlers != null && !handlers.isEmpty()) {
                    for (Handler h : handlers) {
                        // Filtering out the jetty default handler
                        // which should not be added at this point.
                        if (h instanceof DefaultHandler) {
                            defaultHandler = (DefaultHandler) h;
                        } else {
                            if ((h instanceof SecurityHandler)
                                    && ((SecurityHandler)h).getHandler() == null) {
                                //if h is SecurityHandler(such as ConstraintSecurityHandler)
                                //then it need be on top of JettyHTTPHandler
                                //set JettyHTTPHandler as inner handler if
                                //inner handler is null
                                ((SecurityHandler)h).setHandler(handler);
                                securityHandler = (SecurityHandler)h;
                            } else {
                                handlerCollection.addHandler(h);
                            }
                        }
                    }
                }
                *//*
                 * handlerCollection may be null here if is only one handler to deal with.
                 * Which in turn implies that there can't be a 'defaultHander' to deal with.
                 *//*
                if (handlerCollection != null) {
                    handlerCollection.addHandler(contexts);
                    if (defaultHandler != null) {
                        handlerCollection.addHandler(defaultHandler);
                    }
                } else {
                    server.setHandler(contexts);
                }
*/
//
//                customizeConnector(connector);
                // todo resolves with error

//                ((AbstractProtocol<?>) connector.getProtocolHandler()).setAddress(InetAddress.getLocalHost());
//                tomcat.setConnector(connector);

//                tomcat.setPort(port);
//                tomcat.getHost().setAutoDeploy(false);
//                tomcat.getHost().setAutoDeploy(true);
//                tomcat.getServer().setPort(port);
//                Engine engine = tomcat.getEngine();
//                engine.setBackgroundProcessorDelay(this.backgroundProcessorDelay);
                // todo investigate what it is
//                for (Valve valve : this.engineValves) {
//                    engine.getPipeline().addValve(valve);
//                }

                // todo investigate what it is
                // Create default context
                //tomcat.initWebappDefaults("defaultContext");
                //server.initWebappDefaults("defaultContext");

                //prepareContext(tomcat.getHost(), tomcat);
                //prepareContext(server.getHost(), server);

                // context handling
                File docBase = new File(System.getProperty("java.io.tmpdir"));
                Context context = server.addContext("", docBase.getAbsolutePath());


                Class servletClass = HelloServlet.class;
                server.addServlet(context, servletClass.getSimpleName(), servletClass.getName());
                context.addServletMappingDecoded(
                        "/my-servlet/*", servletClass.getSimpleName());


                servletClass = HelloServlet2.class;
                server.addServlet(context, servletClass.getSimpleName(), servletClass.getName());
                context.addServletMappingDecoded(
                        "/hello/test/*", servletClass.getSimpleName());

                //server.addServlet("", "", "");
                //server.addServlet(handler.getServletContext().getContextPath(), handler.getName(), "");
                //context.addServletMappingDecoded(handler.getServletContext().getContextPath(), handler.getName());

                TomcatHTTPDestination destination = handler.tomcatHTTPDestination;
                //Class cxfTomcatServletClass = CxfTomcatServlet.class;
                //Tomcat.addServlet(context, cxfTomcatServletClass.getSimpleName(), cxfTomcatServletClass.getName());
                CxfTomcatServlet cxfTomcatServlet = new CxfTomcatServlet();
                cxfTomcatServlet.setDestination(destination);
                Tomcat.addServlet(context, "tomcatServlet", cxfTomcatServlet);

//                context.addServletMappingDecoded(
//                        "/tomcat-servlet/*", cxfTomcatServletClass.getSimpleName());
                context.addServletMappingDecoded(
                        "/tomcat-servlet/*", "tomcatServlet");
/*                Class<CxfTomcatServlet> cxfTomcatServletClass = CxfTomcatServlet.class;
                server.addWebapp()
                Tomcat.addServlet(
                        context, cxfTomcatServletClass.getSimpleName(), cxfTomcatServletClass.getName());
//        Tomcat.addServlet(
//                context, servletClass.getSimpleName()+ "1", servletClass.getName());
                context.addServletMappingDecoded(
                        "/hello/test/*", cxfTomcatServletClass.getSimpleName());
//        context.addServletMappingDecoded(
//                "/hello/test/*", servletClass.getSimpleName() + "1");*/

                // Start the server to trigger initialization listeners
//                tomcat.getConnector().setAsyncTimeout(300000);
                //server = tomcat;
                server.start();

                startDaemonAwaitThread();
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "START_UP_SERVER_FAILED_MSG", new Object[] {e.getMessage(), port});
                //problem starting server
                try {
                    server.stop();
                } catch (Exception ex) {
                    //ignore - probably wasn't fully started anyway
                }
                server = null;
                throw new Fault(new Message("START_UP_SERVER_FAILED_MSG", LOG, e.getMessage(), port), e);
            }

        } else {
//            String contextName = HttpUriMapper.getContextName(url.getPath());
//            try {
//                servletContext = buildServletContext(contextName);
//            } catch (ServletException e) {
//                throw new Fault(new Message("START_UP_SERVER_FAILED_MSG", LOG, e.getMessage(), port), e);
//            }
//            handler.setServletContext(servletContext);
//
//            if (handler.isContextMatchExact()) {
//                path.addExactPath(url.getPath(), handler);
//            } else {
//                path.addPrefixPath(url.getPath(), handler);
//            }

        }

        final String smap = HttpUriMapper.getResourceBase(url.getPath());
        handler.setName(smap);
        System.out.println("123123");
        registedPaths.add(url.getPath());
        servantCount = servantCount + 1;
    }

    private boolean shouldCheckUrl(Bus bus) {

        Object prop = null;
        if (bus != null) {
            prop = bus.getProperty(DO_NOT_CHECK_URL_PROP);
        }
        if (prop == null) {
            prop = SystemPropertyAction.getPropertyOrNull(DO_NOT_CHECK_URL_PROP);
        }
        return !PropertyUtils.isTrue(prop);
    }

    protected void checkRegistedContext(URL url) {

        String path = url.getPath();
        for (String registedPath : registedPaths) {
            if (path.equals(registedPath)) {
                throw new Fault(new Message("ADD_HANDLER_CONTEXT_IS_USED_MSG", LOG, url, registedPath));
            }
            // There are some context path conflicts which could cause the JettyHTTPServerEngine
            // doesn't route the message to the right JettyHTTPHandler
            if (path.equals(HttpUriMapper.getContextName(registedPath))) {
                throw new Fault(new Message("ADD_HANDLER_CONTEXT_IS_USED_MSG", LOG, url, registedPath));
            }
            if (registedPath.equals(HttpUriMapper.getContextName(path))) {
                throw new Fault(new Message("ADD_HANDLER_CONTEXT_CONFILICT_MSG", LOG, url, registedPath));
            }
        }

    }

/*
    private void prepareContext(Host host, Tomcat tomcat) {
        File docBase = new File(System.getProperty("java.io.tmpdir"));
        Context context = tomcat.addContext("", docBase.getAbsolutePath());

        tomcat.setHostname("localhost");
        String appBase = ".";
        tomcat.getHost().setAppBase(appBase);


        Class servletClass = HelloServlet.class;
        Tomcat.addServlet(
                context, servletClass.getSimpleName(), servletClass.getName());
        context.addServletMappingDecoded(
                "/my-servlet/*", servletClass.getSimpleName());


*/
/*
        Class<CxfTomcatServlet> cxfTomcatServletClass = CxfTomcatServlet.class;
        Tomcat.addServlet(
                context, cxfTomcatServletClass.getSimpleName(), cxfTomcatServletClass.getName());
//        Tomcat.addServlet(
//                context, servletClass.getSimpleName()+ "1", servletClass.getName());
        context.addServletMappingDecoded(
                "/hello/test/*", cxfTomcatServletClass.getSimpleName());
//        context.addServletMappingDecoded(
//                "/hello/test/*", servletClass.getSimpleName() + "1");
*//*



//        host.addChild(context);
        configureContext(context);
    }
*/

    private void startDaemonAwaitThread() {
        Thread awaitThread = new Thread("container-" + (1)) {
            @Override
            public void run() {
                server.getServer().await();
            }

        };
        awaitThread.setContextClassLoader(getClass().getClassLoader());
        awaitThread.setDaemon(false);
        awaitThread.start();
    }

    private void configureContext(Context context) {
//        this.contextLifecycleListeners.forEach(context::addLifecycleListener);
//        new DisableReferenceClearingContextCustomizer().customize(context);
//        this.tomcatContextCustomizers
//                .forEach((customizer) -> customizer.customize(context));
    }

    private void addDefaultServlet(Context context) {
        Wrapper defaultServlet = context.createWrapper();
        defaultServlet.setName("default");
        defaultServlet.setServletClass("org.apache.catalina.servlets.DefaultServlet");
        defaultServlet.addInitParameter("debug", "0");
        defaultServlet.addInitParameter("listings", "false");
        defaultServlet.setLoadOnStartup(1);
        // Otherwise the default location of a Spring DispatcherServlet cannot be set
        defaultServlet.setOverridable(true);
        context.addChild(defaultServlet);
        context.addServletMappingDecoded("/", "default");
    }

    /**
     * Return the absolute temp dir for given web server.
     * @param prefix server name
     * @return the temp dir for given server.
     */
    protected final File createTempDir(String prefix) {
        try {
            File tempDir = File.createTempFile(prefix + ".", "." + this.port);
            tempDir.delete();
            tempDir.mkdir();
            tempDir.deleteOnExit();
            return tempDir;
        }
        catch (IOException ex) {
            throw new IllegalArgumentException(
                    "Unable to create tempDir. java.io.tmpdir is set to "
                            + System.getProperty("java.io.tmpdir"),
                    ex);
        }
    }

    public int getMaxIdleTime() {
        return maxIdleTime;
    }

    public void setMaxIdleTime(int maxIdleTime) {
        this.maxIdleTime = maxIdleTime;
    }

    public void stop() {
        if (this.server != null) {
            try {
                this.server.stop();
                this.server.destroy();
            } catch (LifecycleException e) {
                System.out.println("Could not destroy the server");
                e.printStackTrace();
            }
        }
    }

    /**
     * This method will shut down the server engine and
     * remove it from the factory's cache.
     */
    public void shutdown() {
        registedPaths.clear();
        if (shouldDestroyPort()) {
            if (servantCount == 0) {
                TomcatHTTPServerEngineFactory.destroyForPort(port);
            } else {
                LOG.log(Level.WARNING, "FAILED_TO_SHUTDOWN_ENGINE_MSG", port);
            }
        }
    }

    private boolean shouldDestroyPort() {
        //if we shutdown the port, on SOME OS's/JVM's, if a client
        //in the same jvm had been talking to it at some point and keep alives
        //are on, then the port is held open for about 60 seconds
        //afterwards and if we restart, connections will then
        //get sent into the old stuff where there are
        //no longer any servant registered.   They pretty much just hang.

        //this is most often seen in our unit/system tests that
        //test things in the same VM.
        // todo investigate this property
        String s = SystemPropertyAction
                .getPropertyOrNull("org.apache.cxf.transports.http_undertow.DontClosePort." + port);
        if (s == null) {
            s = SystemPropertyAction
                    .getPropertyOrNull("org.apache.cxf.transports.http_undertow.DontClosePort");
        }
        return !Boolean.valueOf(s);
    }

    /**
     * This method is used to programmatically set the TLSServerParameters.
     * This method may only be called by the factory.
     * @throws IOException
     */
    public void setTlsServerParameters(TLSServerParameters params) {

        tlsServerParameters = params;
        if (this.configFinalized) {
            this.retrieveListenerFactory();
        }
    }

    private void retrieveListenerFactory() {
        if (tlsServerParameters != null) {
            protocol = "https";

        } else {
            protocol = "http";
        }
        LOG.fine("Configured port " + port + " for \"" + protocol + "\".");
    }

    /**
     * This method is called after configure on this object.
     */
    @PostConstruct
    public void finalizeConfig()
            throws GeneralSecurityException,
            IOException {
        retrieveListenerFactory();
        checkConnectorPort();
        this.configFinalized = true;
    }

    private void checkConnectorPort() throws IOException {
        try {
            if (null != connector) {
                int cp = ((Connector)connector).getPort();
                if (port != cp) {
                    throw new IOException("Error: Connector port " + cp + " does not match"
                            + " with the server engine port " + port);
                }
            }
        } catch (IOException ioe) {
            throw ioe;
        } catch (Throwable t) {
            //ignore...
        }
    }

    public void setHandlers(List<Handler> handlers) {
        this.handlers = handlers;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return this.port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public Boolean getContinuationsEnabled() {
        return continuationsEnabled;
    }

    public void setContinuationsEnabled(Boolean continuationsEnabled) {
        this.continuationsEnabled = continuationsEnabled;
    }

    public Connector getConnector() {
        return connector;
    }

    public Tomcat getServer() {
        return server;
    }

    /**
     * This method returns the programmatically set TLSServerParameters, not
     * the TLSServerParametersType, which is the JAXB generated type used
     * in SpringConfiguration.
     * @return
     */
    public TLSServerParameters getTlsServerParameters() {
        return tlsServerParameters;
    }

    /**
     * This method sets the threading parameters for this particular
     * server engine.
     * This method may only be called by the factory.
     */
    public void setThreadingParameters(ThreadingParameters params) {
        threadingParameters = params;
    }

    /**
     * This method returns whether the threading parameters are set.
     */
    public boolean isSetThreadingParameters() {
        return threadingParameters != null;
    }

    /**
     * This method returns the threading parameters that have been set.
     * This method may return null, if the threading parameters have not
     * been set.
     */
    public ThreadingParameters getThreadingParameters() {
        return threadingParameters;
    }

    public List<Handler> getHandlers() {
        return handlers;
    }
}

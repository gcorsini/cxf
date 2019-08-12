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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.servlet.Filter;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.cxf.Bus;
import org.apache.cxf.common.i18n.Message;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.common.util.PropertyUtils;
import org.apache.cxf.common.util.SystemPropertyAction;
import org.apache.cxf.configuration.jsse.TLSServerParameters;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.transport.HttpUriMapper;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.apache.tomcat.util.net.SSLHostConfig;

public class TomcatHTTPServerEngine implements ServerEngine {

    public static final String DO_NOT_CHECK_URL_PROP = "org.apache.cxf.transports.http_tomcat.DontCheckUrl";

    private static final Logger LOG = LogUtils.getL7dLogger(TomcatHTTPServerEngine.class);

    /**
     * This is the network port for which this engine is allocated.
     */
    private int port;

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
    private List<Filter> handlers;
    private ConcurrentMap<String, TomcatHTTPHandler> registeredPaths =
            new ConcurrentHashMap<>();
    private int backgroundProcessorDelay;
    private String host;

    /**
     * This field holds the TLS ServerParameters that are programatically
     * configured. The tlsServerParameters (due to JAXB) holds the struct
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
        LifecycleState state = server.getEngine().getState();
        if (server != null && state == LifecycleState.STARTED) {
            TomcatHTTPHandler tomcatHTTPHandler = registeredPaths.get(url.getPath());
            tomcatHTTPHandler.destroy();
            TomcatHTTPHandler handler = registeredPaths.remove(url.getPath());
            if (handler == null) {
                return;
            }
            --servantCount;
        }
    }

    @Override
    public TomcatHTTPHandler getServant(URL url) {
        return registeredPaths.getOrDefault(url.getPath(), null);
    }

    @Override
    public void addServant(URL url, TomcatHTTPHandler handler) {
        if (shouldCheckUrl(handler.getBus())) {
            checkRegistedContext(url);
        }

        handler.setName(url.getPath());
        if (server == null) {
            try {
                // create a new tomcat server instance if there is no server there
                server = new Tomcat();
                server.setHostname("localhost");
                String appBase = ".";
                server.getHost().setAppBase(appBase);

                // create a connector which will enable connections to the servlet
                connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
                connector.setPort(getPort());

                Http11NioProtocol nioProtocol = (Http11NioProtocol) connector.getProtocolHandler();
                if (tlsServerParameters != null) {
                    // TODO: use the tlsServerParameters instead of fake default values
/*                    // get desired protocol
                    String proto = (tlsServerParameters.getSecureSocketProtocol() == null) ?
                            tlsServerParameters.getSecureSocketProtocol() : "https";
                    // exclude specified protocols
                    for (String p : tlsServerParameters.getExcludeProtocols()) {
                        //protocol.setSslEnabledProtocols(p);
                        //scf.addExcludeProtocols(p);
                    }
                    // include specified protocols
                    for (String p : tlsServerParameters.getIncludeProtocols()) {
                        protocol.setSslEnabledProtocols(p);
                        //scf.addExcludeProtocols(p);
                    }
                    SSLContext context = tlsServerParameters.getJsseProvider() == null
                            ? SSLContext.getInstance(proto)
                            : SSLContext.getInstance(proto, tlsServerParameters.getJsseProvider());

                    KeyManager[] keyManagers = tlsServerParameters.getKeyManagers();
                    KeyManager[] configuredKeyManagers =
                            org.apache.cxf.transport.https.SSLUtils.configureKeyManagersWithCertAlias(
                            tlsServerParameters, keyManagers);

                    context.init(configuredKeyManagers,
                            tlsServerParameters.getTrustManagers(),
                            tlsServerParameters.getSecureRandom());

                    // Set the CipherSuites
                    final String[] supportedCipherSuites =
                            SSLUtils.getServerSupportedCipherSuites(context);

                    if (tlsServerParameters.getCipherSuitesFilter() != null
                            && tlsServerParameters.getCipherSuitesFilter().isSetExclude()) {
                        String[] excludedCipherSuites =
                                SSLUtils.getFilteredCiphersuites(tlsServerParameters.getCipherSuitesFilter(),
                                        supportedCipherSuites,
                                        LOG,
                                        true);
                        scf.setExcludeCipherSuites(excludedCipherSuites);
                    }

                    String[] includedCipherSuites =
                            SSLUtils.getCiphersuitesToInclude(tlsServerParameters.getCipherSuites(),
                                    tlsServerParameters.getCipherSuitesFilter(),
                                    context.getServerSocketFactory().getDefaultCipherSuites(),
                                    supportedCipherSuites,
                                    LOG);
                    scf.setIncludeCipherSuites(includedCipherSuites);*/

                    // Use HTTPS
                    SSLHostConfig sslHostConfig = new SSLHostConfig();
                    sslHostConfig.setCertificateKeyAlias("tomcat");

                    String keystorePath = System.getProperty("user.dir") + "/src/test/resources/keystore";
                    sslHostConfig.setCertificateKeystoreFile(keystorePath);
                    sslHostConfig.setCertificateKeystorePassword("changeit");

                    connector.setScheme("https");
                    connector.setSecure(true);
                    nioProtocol.setSSLEnabled(true);
                    connector.addSslHostConfig(sslHostConfig);
                }

                // add connector to server
                server.getService().addConnector(connector);

                createContext(url, handler);

                server.start();

                startDaemonAwaitThread();
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "START_UP_SERVER_FAILED_MSG", new Object[]{e.getMessage(), port});
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
            createContext(url, handler);
        }

        final String smap = HttpUriMapper.getResourceBase(url.getPath());
        handler.setName(smap);
        registeredPaths.put(url.getPath(), handler);
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
        for (String registedPath : registeredPaths.keySet()) {
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

    private void createContext(URL url, TomcatHTTPHandler handler) {
        //File docBase = new File(System.getProperty("java.io.tmpdir"));
        String contextName = HttpUriMapper.getContextName(url.getPath());
        // TODO: Ivan, should we just create a tmp dir for the server or for each context?
        File docBase = createTempDir(server.getHost().getName() + contextName);

        Context context = server.addContext(contextName, docBase.getAbsolutePath());

        // Get servlet name from handler by replacing "/" with "_" if they exist (as name is often link)
        String servletName = handler.getName().replaceFirst("/", "").replace("/", "_");

        Class filterClass = handler.getClass();
        String filterName = filterClass.getName();
        FilterDef def = new FilterDef();
        def.setFilterName(filterName);
        def.setFilter(handler);
        context.addFilterDef(def);
        FilterMap map = new FilterMap();
        map.setFilterName(filterName);
        //Get the URL Pattern from the input url string depending depending on exact or only context
        String urlPattern = url.getPath().replaceFirst(HttpUriMapper.getContextName(url.getPath()), "");
        // If the url doesn't have to match exactly, any character after its pattern should be accepted.
        urlPattern = handler.isContextMatchExact() ? urlPattern : urlPattern + "/*";
        map.addURLPattern(urlPattern);
        context.addFilterMap(map);

        server.addServlet(context, servletName, new CxfTomcatServlet());
        context.addServletMappingDecoded(urlPattern, servletName);

        handler.setServletContext(context.getServletContext());

    }

    private void startDaemonAwaitThread() {
        Thread awaitThread = new Thread("container-" + 1) {
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
        } catch (IOException ex) {
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
        registeredPaths.clear();
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
                .getPropertyOrNull("org.apache.cxf.transports.http_tomcat.DontClosePort." + port);
        if (s == null) {
            s = SystemPropertyAction
                    .getPropertyOrNull("org.apache.cxf.transports.http_tomcat.DontClosePort");
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
                int cp = connector.getPort();
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

    public void setHandlers(List<Filter> handlers) {
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

    public List<Filter> getHandlers() {
        return handlers;
    }
}

/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cxf.transport.http_tomcat;

import org.apache.cxf.Bus;
import org.apache.cxf.buslifecycle.BusLifeCycleListener;
import org.apache.cxf.buslifecycle.BusLifeCycleManager;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.configuration.jsse.TLSServerParameters;

import javax.annotation.Resource;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TomcatHTTPServerEngineFactory {

    private static final Logger LOG =
            LogUtils.getL7dLogger(TomcatHTTPServerEngineFactory.class);

    private Bus bus;
    private BusLifeCycleManager lifeCycleManager;
    private static final int FALLBACK_THREADING_PARAMS_KEY = 0;

    /**
     * This field holds the TLS ServerParameters that are programatically
     * configured. The tlsServerParamers (due to JAXB) holds the struct
     * placed by SpringConfig.
     */
    private TLSServerParameters tlsServerParameters;

    /**
     * This map holds references for allocated ports.
     */
    // Still use the static map to hold the port information
    // in the same JVM
    private static ConcurrentHashMap<Integer, TomcatHTTPServerEngine> portMap =
            new ConcurrentHashMap<>();

    /**
     * This map holds TLS Server Parameters that are to be used to
     * configure a subsequently created TomcatHTTPServerEngine.
     */
    private Map<String, TLSServerParameters> tlsParametersMap =
            new TreeMap<>();

    /**
     * This map holds the threading parameters that are to be applied
     * to new Engines when bound to the reference id.
     */
    private Map<String, ThreadingParameters> threadingParametersMap =
            new TreeMap<>();

    private ThreadingParameters fallbackThreadingParameters;

    /**
     * This boolean signfies that SpringConfig is over. finalizeConfig
     * has been called.
     */
    private boolean configFinalized;

    public TomcatHTTPServerEngineFactory() {
        // Empty
    }
    public TomcatHTTPServerEngineFactory(Bus b) {
        setBus(b);
    }
    public TomcatHTTPServerEngineFactory(Bus b,
                                        Map<String, TLSServerParameters> tls,
                                        Map<String, ThreadingParameters> threading) {
        tlsParametersMap.putAll(tls);
        threadingParametersMap.putAll(threading);
        setBus(b);
    }

    @Resource(name = "cxf")
    public final void setBus(Bus bus) {
        this.bus = bus;
        if (bus != null) {
            bus.setExtension(this, TomcatHTTPServerEngineFactory.class);
            lifeCycleManager = bus.getExtension(BusLifeCycleManager.class);
            if (null != lifeCycleManager) {
                lifeCycleManager.registerLifeCycleListener(new TomcatBusLifeCycleListener());
            }
        }
    }

    private class TomcatBusLifeCycleListener implements BusLifeCycleListener {
        public void initComplete() {
            TomcatHTTPServerEngineFactory.this.initComplete();
        }

        public void preShutdown() {
            TomcatHTTPServerEngineFactory.this.preShutdown();
        }

        public void postShutdown() {
            TomcatHTTPServerEngineFactory.this.postShutdown();
        }
    }

    private static TomcatHTTPServerEngine getOrCreate(TomcatHTTPServerEngineFactory factory,
                                                      String host,
                                                      int port,
                                                      TLSServerParameters tlsParams) throws IOException, GeneralSecurityException {

        TomcatHTTPServerEngine ref = portMap.get(port);
        if (ref == null) {
            ref = new TomcatHTTPServerEngine(host, port);
            if (tlsParams != null) {
                ref.setTlsServerParameters(tlsParams);
            }
            TomcatHTTPServerEngine tmpRef = portMap.putIfAbsent(port, ref);
            ref.finalizeConfig();
            if (tmpRef != null) {
                ref = tmpRef;
            }
        }
        return ref;
    }


    /**
     * This call sets TLSParametersMap for a TomcatHTTPServerEngine
     */
    public void setTlsServerParametersMap(
            Map<String, TLSServerParameters> tlsParamsMap) {

        tlsParametersMap = tlsParamsMap;
    }

    public Map<String, TLSServerParameters> getTlsServerParametersMap() {
        return tlsParametersMap;
    }

    public void setEnginesList(List<TomcatHTTPServerEngine> enginesList) {
        for (TomcatHTTPServerEngine engine : enginesList) {
            if (engine.getPort() == FALLBACK_THREADING_PARAMS_KEY) {
                // todo investigate what to write here
                fallbackThreadingParameters = engine.getThreadingParameters();
            }
            portMap.putIfAbsent(engine.getPort(), engine);
        }
    }

    public Bus getBus() {
        return bus;
    }

    /**
     * This call sets TLSServerParameters for a TomcatHTTPServerEngine
     * that will be subsequently created. It will not alter an engine
     * that has already been created for that network port.
     *
     * @param host      if not null, server will listen on this address/host,
     *                  otherwise, server will listen on all local addresses.
     * @param port      The network port number to bind to the engine.
     * @param tlsParams The tls server parameters. Not anymore: Cannot be null.
     * @throws IOException
     * @throws GeneralSecurityException
     */
    public void setTLSServerParametersForPort(
            String host,
            int port,
            TLSServerParameters tlsParams) throws GeneralSecurityException, IOException {
        if (tlsParams == null) {
            throw new IllegalArgumentException("tlsParams cannot be null");
        }
        TomcatHTTPServerEngine ref = retrieveTomcatHTTPServerEngine(port);
        if (null == ref) {
            getOrCreate(this, host, port, tlsParams);
        } else {
            ref.setTlsServerParameters(tlsParams);
        }
    }

    /**
     * calls thru to {{@link #createTomcatHTTPServerEngine(String, int, String)} with 'null' for host value
     */
    public void setTLSServerParametersForPort(
            int port,
            TLSServerParameters tlsParams) throws GeneralSecurityException, IOException {
        setTLSServerParametersForPort(null, port, tlsParams);
    }

    /**
     * This call retrieves a previously configured TomcatHTTPServerEngine for the
     * given port. If none exists, this call returns null.
     */
    public synchronized TomcatHTTPServerEngine retrieveTomcatHTTPServerEngine(int port) {
        return portMap.get(port);
    }

    /**
     * This call creates a new UndertowHTTPServerEngine initialized for "http"
     * or "https" on the given port. The determination of "http" or "https"
     * will depend on configuration of the engine's bean name.
     * <p>
     * If an UndertowHTTPEngine already exists, or the port
     * is already in use, a BindIOException will be thrown. If the
     * engine is being Spring configured for TLS a GeneralSecurityException
     * may be thrown.
     *
     * @param host     if not null, server will listen on this host/address, otherwise
     *                 server will listen on all local addresses.
     * @param port     listen port for server
     * @param protocol "http" or "https"
     * @return
     * @throws GeneralSecurityException
     * @throws IOException
     */
    public synchronized TomcatHTTPServerEngine createTomcatHTTPServerEngine(String host, int port,
                                                                            String protocol) throws GeneralSecurityException, IOException {
        LOG.fine("Creating Tomcat HTTP Server Engine for port " + port + ".");
        TomcatHTTPServerEngine ref = getOrCreate(this, host, port, null);
        // checking the protocol

        TLSServerParameters tlsParameters = null;
//        if (id != null && tlsParametersMap != null && tlsParametersMap.containsKey(id)) {
//            tlsParameters = tlsParametersMap.get(id);
//        }

        if (!protocol.equals(ref.getProtocol())) {
            throw new IOException("Protocol mismatch for port " + port + ": "
                    + "engine's protocol is " + ref.getProtocol()
                    + ", the url protocol is " + protocol);
        }

//         todo investigate threading parameters
        if (!(ref.isSetThreadingParameters()
                || null == fallbackThreadingParameters)) {
            if (LOG.isLoggable(Level.INFO)) {
                final int min = fallbackThreadingParameters.getMinThreads();
                final int max = fallbackThreadingParameters.getMaxThreads();
                LOG.log(Level.INFO,
                        "FALLBACK_THREADING_PARAMETERS_MSG",
                        new Object[]{port, min, max, ""});
            }
            ref.setThreadingParameters(fallbackThreadingParameters);
        }

        return ref;
    }

    /**
     * Calls thru to {{@link #createTomcatHTTPServerEngine(String, int, String)} with a 'null' host value
     */
    public synchronized TomcatHTTPServerEngine createTomcatHTTPServerEngine(int port, String protocol)
            throws GeneralSecurityException, IOException {
        return createTomcatHTTPServerEngine(null, port, protocol);
    }

    /**
     * This method removes the Server Engine from the port map and stops it.
     */
    public static synchronized void destroyForPort(int port) {
        TomcatHTTPServerEngine ref = portMap.remove(port);
        if (ref != null) {
            LOG.fine("Stopping Tomcat HTTP Server Engine on port " + port + ".");
            try {
                ref.stop();
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    public void initComplete() {
        // do nothing here
    }

    public void postShutdown() {
        // shut down the Undertow server in the portMap
        // To avoid the CurrentModificationException,
        // do not use portMap.values directly
        TomcatHTTPServerEngine[] engines =
                portMap.values().toArray(new TomcatHTTPServerEngine[portMap.values().size()]);
        for (TomcatHTTPServerEngine engine : engines) {
            engine.shutdown();
        }
        // clean up the collections
        // todo ingestigate threading parameters
//        threadingParametersMap.clear();
        tlsParametersMap.clear();
    }

    public void preShutdown() {
        // do nothing here
        // just let server registry to call the server stop first
    }

    public void setThreadingParametersMap(Map<String, ThreadingParameters> threadingParametersMap) {
        this.threadingParametersMap = threadingParametersMap;
    }
}

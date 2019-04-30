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

import java.io.IOException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.common.classloader.ClassLoaderUtils;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.configuration.jsse.TLSServerParameters;
import org.apache.cxf.configuration.security.CertificateConstraintsType;
import org.apache.cxf.message.Message;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.http.DestinationRegistry;
import org.apache.cxf.transport.https.CertConstraintsJaxBUtils;
import org.apache.cxf.transport.servlet.ServletDestination;
import org.apache.cxf.transports.http.configuration.HTTPServerPolicy;

public class TomcatHTTPDestination extends ServletDestination {

    private static final Logger LOG =
            LogUtils.getL7dLogger(TomcatHTTPDestination.class);

    protected TomcatHTTPServerEngine engine;
    protected TomcatHTTPServerEngineFactory serverEngineFactory;
    protected URL nurl;
    protected ClassLoader loader;
    protected ServletContext servletContext;
    protected TomcatHTTPHandler handler;
    /**
     * This variable signifies that finalizeConfig() has been called.
     * It gets called after this object has been spring configured.
     * It is used to automatically reinitialize things when resources
     * are reset, such as setTlsServerParameters().
     */
    private boolean configFinalized;


    public TomcatHTTPDestination(Bus bus,
                                 DestinationRegistry registry,
                                 EndpointInfo ei,
                                 TomcatHTTPServerEngineFactory factory) throws IOException {
        //Add the default port if the address is missing it
        super(bus, registry, ei, getAddressValue(ei, true).getAddress(), true);
        this.serverEngineFactory = factory;
        loader = bus.getExtension(ClassLoader.class);
    }

    protected Logger getLogger() {
        return LOG;
    }

    public void setServletContext(ServletContext sc) {
        servletContext = sc;
    }

    protected void retrieveEngine()
            throws GeneralSecurityException,
            IOException {
        if (serverEngineFactory == null) {
            return;
        }

        nurl = new URL(getAddress(endpointInfo));

        engine = serverEngineFactory.retrieveTomcatHTTPServerEngine(nurl.getPort());
        if (engine == null) {
            engine = serverEngineFactory.
                    createTomcatHTTPServerEngine(nurl.getHost(), nurl.getPort(), nurl.getProtocol());
        }

        assert engine != null;
        TLSServerParameters serverParameters = engine.getTlsServerParameters();
        if (serverParameters != null && serverParameters.getCertConstraints() != null) {
            CertificateConstraintsType constraints = serverParameters.getCertConstraints();
            if (constraints != null) {
                certConstraints = CertConstraintsJaxBUtils.createCertConstraints(constraints);
            }
        }

        // When configuring for "http", however, it is still possible that
        // Spring configuration has configured the port for https.
        if (!nurl.getProtocol().equals(engine.getProtocol())) {
            throw new IllegalStateException(
                    "Port " + engine.getPort()
                            + " is configured with wrong protocol \""
                            + engine.getProtocol()
                            + "\" for \"" + nurl + "\"");
        }
    }


    /**
     * This method is used to finalize the configuration
     * after the configuration items have been set.
     */
    public void finalizeConfig() {
        assert !configFinalized;

        try {
            retrieveEngine();
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        configFinalized = true;
    }

    /**
     * Activate receipt of incoming messages.
     */
    protected void activate() {
        super.activate();
        LOG.log(Level.FINE, "Activating receipt of incoming messages");
        // todo implement me
        if (engine != null) {
            TomcatHTTPHandler thd = createTomcatHTTPHandler(this, contextMatchOnExact());
            engine.addServant(nurl, thd);
        }
    }

    /**
     * Deactivate receipt of incoming messages.
     */
    protected void deactivate() {
        super.deactivate();
        LOG.log(Level.FINE, "Deactivating receipt of incoming messages");
        // todo implement me
        if (engine != null) {
            engine.removeServant(nurl);
        }
        handler = null;
    }

    protected TomcatHTTPHandler createTomcatHTTPHandler(TomcatHTTPDestination thd,
                                                      boolean cmExact) {
        return new TomcatHTTPHandler(thd, cmExact);
    }

    protected void doService(HttpServletRequest req,
                             HttpServletResponse resp) throws IOException {
        doService(servletContext, req, resp);
    }

    protected void doService(ServletContext context,
                             HttpServletRequest req,
                             HttpServletResponse resp) throws IOException {
        if (context == null) {
            context = servletContext;
        }
//        Request baseRequest = (req instanceof Request)
//                ? (Request)req : getCurrentRequest();

        HTTPServerPolicy sp = getServer();
        if (sp.isSetRedirectURL()) {
            resp.sendRedirect(sp.getRedirectURL());
            resp.flushBuffer();
            return;
        }

        // REVISIT: service on executor if associated with endpoint
        ClassLoaderUtils.ClassLoaderHolder origLoader = null;
        Bus origBus = BusFactory.getAndSetThreadDefaultBus(bus);
        try {
            if (loader != null) {
                origLoader = ClassLoaderUtils.setThreadContextClassloader(loader);
            }
            invoke(null, context, req, resp);
        } finally {
            if (origBus != bus) {
                BusFactory.setThreadDefaultBus(origBus);
            }
            if (origLoader != null) {
                origLoader.reset();
            }
        }

    }

    protected void invokeComplete(final ServletContext context,
                                  final HttpServletRequest req,
                                  final HttpServletResponse resp,
                                  Message m) throws IOException {
        resp.flushBuffer();
        super.invokeComplete(context, req, resp, m);
    }

//    protected OutputStream flushHeaders(Message outMessage, boolean getStream) throws IOException {
//        OutputStream out = super.flushHeaders(outMessage, getStream);
//        return wrapOutput(out);
//    }
//
//    private OutputStream wrapOutput(OutputStream out) {
//        try {
//            if (out instanceof CoyoteOutputStream) {
//                out = new TomcatOutputStream((CoyoteOutputStream)out);
//            }
//        } catch (Throwable t) {
//            //ignore
//        }
//        return out;
//    }




    protected Message retrieveFromContinuation(HttpServletRequest req) {
        return (Message) req.getAttribute(CXF_CONTINUATION_MESSAGE);
    }

    protected void setupContinuation(Message inMessage, final HttpServletRequest req,
                                     final HttpServletResponse resp) {
//        if (engine != null && engine.getContinuationsEnabled()) {
//            super.setupContinuation(inMessage, req, resp);
//        }

    }

    protected String getAddress(EndpointInfo endpointInfo) {
        return endpointInfo.getAddress();
    }

//    private Request getCurrentRequest() {
//        try {
//             con = HttpConnection.getCurrentConnection();
//
//            HttpChannel channel = con.getHttpChannel();
//            return channel.getRequest();
//        } catch (Throwable t) {
//            //
//        }
//        return null;
//    }

    public TomcatHTTPServerEngine getEngine() {
        return engine;
    }


//    static class TomcatOutputStream extends FilterOutputStream implements CopyingOutputStream {
//        final CoyoteOutputStream out;
//        boolean written;
//        TomcatOutputStream(CoyoteOutputStream o) {
//            super(o);
//            out = o;
//        }
//
//        private boolean sendContent(Class<?> type, InputStream c) throws IOException {
//            try {
//                out.write(c);
//            } catch (Exception e) {
//                return false;
//            }
//            return true;
//        }
//        @Override
//        public int copyFrom(InputStream in) throws IOException {
//            if (written) {
//                return IOUtils.copy(in, out);
//            }
//            CountingInputStream c = new CountingInputStream(in);
//            if (!sendContent(InputStream.class, c)
//                    && !sendContent(Object.class, c)) {
//                IOUtils.copy(c, out);
//            }
//            return c.getCount();
//        }
//        public void write(int b) throws IOException {
//            written = true;
//            out.write(b);
//        }
//        public void write(byte[] b, int off, int len) throws IOException {
//            written = true;
//            out.write(b, off, len);
//        }
//
//        @Override
//        public void close() throws IOException {
//            // Avoid calling flush() here. It interferes with
//            // content length calculation in the generator.
//            out.close();
//        }
//    }
//    static class CountingInputStream extends FilterInputStream {
//        int count;
//        CountingInputStream(InputStream in) {
//            super(in);
//        }
//        public int getCount() {
//            return count;
//        }
//
//        @Override
//        public int read() throws IOException {
//            int i = super.read();
//            if (i != -1) {
//                ++count;
//            }
//            return i;
//        }
//        @Override
//        public int read(byte[] b) throws IOException {
//            int i = super.read(b);
//            if (i != -1) {
//                count += i;
//            }
//            return i;
//        }
//        @Override
//        public int read(byte[] b, int off, int len) throws IOException {
//            int i = super.read(b, off, len);
//            if (i != -1) {
//                count += i;
//            }
//            return i;
//        }
//    }

}


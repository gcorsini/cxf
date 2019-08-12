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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.management.ObjectName;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import javax.servlet.Filter;

import org.apache.catalina.connector.Connector;
import org.apache.cxf.Bus;
import org.apache.cxf.configuration.Configurer;
import org.apache.cxf.configuration.jsse.TLSServerParameters;
import org.apache.cxf.configuration.spring.ConfigurerImpl;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.management.InstrumentationManager;
import org.apache.cxf.testutil.common.TestUtil;

import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;


public class TomcatHTTPServerEngineTest {

    private static final int PORT1
            = Integer.valueOf(TestUtil.getPortNumber(TomcatHTTPServerEngineTest.class, 1));
    private static final int PORT2
            = Integer.valueOf(TestUtil.getPortNumber(TomcatHTTPServerEngineTest.class, 2));
    private static final int PORT3
            = Integer.valueOf(TestUtil.getPortNumber(TomcatHTTPServerEngineTest.class, 3));
    private static final int PORT4
            = Integer.valueOf(TestUtil.getPortNumber(TomcatHTTPServerEngineTest.class, 4));


    private Bus bus;
    private IMocksControl control;
    private TomcatHTTPServerEngineFactory factory;


    @Before
    public void setUp() throws Exception {
        control = EasyMock.createNiceControl();
        bus = control.createMock(Bus.class);

        Configurer configurer = new ConfigurerImpl();
        bus.getExtension(Configurer.class);
        EasyMock.expectLastCall().andReturn(configurer).anyTimes();

        InstrumentationManager iManager = control.createMock(InstrumentationManager.class);
        iManager.getMBeanServer();
        EasyMock.expectLastCall().andReturn(ManagementFactory.getPlatformMBeanServer()).anyTimes();

        bus.getExtension(InstrumentationManager.class);
        EasyMock.expectLastCall().andReturn(iManager).anyTimes();

        control.replay();

        factory = new TomcatHTTPServerEngineFactory();
        factory.setBus(bus);

    }

    @Test
    public void testCreateServer() {
        TomcatHTTPServerEngine serverEngine = new TomcatHTTPServerEngine(8080);
        assertNotNull("Server engine constructor failed", serverEngine);
    }



    @Test
    public void testaddServants() throws Exception {
        String urlStr = "http://localhost:" + PORT1 + "/hello/test";
        String urlStr2 = "http://localhost:" + PORT1 + "/hello233/test";
        TomcatHTTPServerEngine serverEngine =
                factory.createTomcatHTTPServerEngine(PORT1, "http");
        serverEngine.setMaxIdleTime(30000);

        TomcatHTTPTestHandler testHandler1 = new TomcatHTTPTestHandler("Using test handler", true);
        TomcatHTTPTestHandler testHandler2 = new TomcatHTTPTestHandler("string2", true);
        serverEngine.addServant(new URL(urlStr), testHandler1);
        // TODO: check setting MaxIdleTime
        //assertEquals("Get the wrong maxIdleTime.", 30000, getMaxIdle(serverEngine.getConnector()));

        TomcatHTTPHandler handler = serverEngine.getServant(new URL(urlStr));
        assertEquals(testHandler1, handler);

        String response = null;
        response = getResponse(urlStr);
        assertEquals("The tomcat http handler did not take effect", response, "Using test handler");

        try {
            serverEngine.addServant(new URL(urlStr), testHandler2);
            fail("We don't support to publish the two service at the same context path");
        } catch (Exception ex) {
            assertTrue("Get a wrong exception message", ex.getMessage().indexOf("hello/test") > 0);
        }

        try {
            serverEngine.addServant(new URL(urlStr + "/test"), testHandler2);
            fail("We don't support to publish the two service at the same context path");
        } catch (Exception ex) {
            assertTrue("Get a wrong exception message", ex.getMessage().indexOf("hello/test/test") > 0);
        }

        try {
            serverEngine.addServant(new URL("http://localhost:" + PORT1 + "/hello"), testHandler2);
            fail("We don't support to publish the two service at the same context path");
        } catch (Exception ex) {
            assertTrue("Get a wrong exception message", ex.getMessage().indexOf("hello") > 0);
        }

        handler = serverEngine.getServant(new URL(urlStr2));
        assertEquals(null, handler);

        // ToDo: Removed the following lines. If multiple servlets are added these will have to work as well.
        // check if the system property change could work
/*        System.setProperty("org.apache.cxf.transports.http_tomcat.DontCheckUrl", "true");
        serverEngine.addServant(new URL(urlStr + "/test"), new TomcatHTTPTestHandler("string2", true));
        // clean up the System property setting
        System.clearProperty("org.apache.cxf.transports.http_tomcat.DontCheckUrl");*/

        serverEngine.addServant(new URL(urlStr2), testHandler2);

        handler = serverEngine.getServant(new URL(urlStr2));
        assertEquals(testHandler2, handler);

//        response = getResponse(urlStr2);
//        assertEquals("The tomcat http handler did not take effect", response, "string2");

        Set<ObjectName> s = CastUtils.cast(ManagementFactory.getPlatformMBeanServer().
                //queryNames(new ObjectName("org.eclipse.jetty.server:type=server,*"), null));
                queryNames(new ObjectName("Tomcat:type=Server"), null));
        assertEquals("Could not find 1 Tomcat Server: " + s, 1, s.size());

        response = getResponse(urlStr);
        assertEquals("The tomcat http handler failed after adding a second servlet", response,
                "Using test handler");

        serverEngine.removeServant(new URL(urlStr));

        handler = serverEngine.getServant(new URL(urlStr));
        assertEquals(null, handler);

        handler = serverEngine.getServant(new URL(urlStr2));
        assertEquals(testHandler2, handler);

        //serverEngine.shutdown();
//        response = getResponse(urlStr2);
//        assertEquals("The tomcat http handler did not take effect", response, "string2");

    }

    /**
     * Check that names of threads serving requests for instances of TomcatServerEngine
     * can be set with user specified name.
     */
    @Test
    public void testSettingThreadNames() throws Exception {
        fail("Test empty");
    }

    @Test
    public void testEngineRetrieval() throws Exception {
        TomcatHTTPServerEngine engine =
                factory.createTomcatHTTPServerEngine(PORT1, "http");

        assertTrue(
                "Engine references for the same port should point to the same instance",
                engine == factory.retrieveTomcatHTTPServerEngine(PORT1));
    }

    @Test
    public void testSetHandlers() throws Exception {
        //TODO Ivan, is this test needed?
        fail("Test not needed?!");
        URL url = new URL("http://localhost:" + PORT2 + "/hello/test");
        TomcatHTTPTestHandler handler1 = new TomcatHTTPTestHandler("string1", true);
        TomcatHTTPTestHandler handler2 = new TomcatHTTPTestHandler("string2", true);

        TomcatHTTPServerEngine engine = new TomcatHTTPServerEngine();
        engine.setPort(PORT2);

        List<Filter> handlers = new ArrayList<>();
        handlers.add(handler1);
        engine.setHandlers(handlers);
        engine.finalizeConfig();

        engine.addServant(url, handler2);
        String response = null;
        try {
            response = getResponse(url.toString());
            assertEquals("the tomcat http handler1 did not take effect", response, "string1string2");
        } catch (Exception ex) {
            fail("Can't get the reponse from the server " + ex);
        }
    }

    @Test
    public void testGetContextHandler() throws Exception {
        //TODO Ivan, is this test needed?
        fail("Test not needed?!");

        String urlStr = "http://localhost:" + PORT1 + "/hello/test";
        TomcatHTTPServerEngine engine =
                factory.createTomcatHTTPServerEngine(PORT1, "http");

        assertEquals("Engine should be empty", null, engine.getServant(new URL(urlStr)));
/*        ServletContext context = engine.getServant(new URL(urlStr)).getServletContext();
        ((Context) context).addServletMappingDecoded("/hello/test","contextHandlerTest");*/

//        ContextHandler contextHandler = engine.getContextHandler(new URL(urlStr));
//        // can't find the context handler here
//        assertNull(contextHandler);
        TomcatHTTPTestHandler handler1 = new TomcatHTTPTestHandler("string1", true);
        TomcatHTTPTestHandler handler2 = new TomcatHTTPTestHandler("string2", true);
        engine.addServant(new URL(urlStr), handler1);
        //engine.addServant(new URL(urlStr), handler2);
        //ServletContext context = engine.getServant(new URL(urlStr)).getServletContext();
        // TODO: remove handler1
        //context.getFilterRegistration(handler1.getName())
        //context.addFilter(handler2.getName(), handler2);
//        // Note: There appears to be an internal issue in Jetty that does not
//        // unregister the MBean for handler1 during this setHandler operation.
//        // This scenario may create a warning message in the logs
//        //     (javax.management.InstanceAlreadyExistsException: org.apache.cxf.
//        //         transport.http_jetty:type=jettyhttptesthandler,id=0)
//        // when running subsequent tests.
//        contextHandler = engine.getContextHandler(new URL(urlStr));
//        contextHandler.stop();
//        contextHandler.setHandler(handler2);
//        contextHandler.start();

        String response = null;
        try {
            response = getResponse(urlStr);
        } catch (Exception ex) {
            fail("Can't get the reponse from the server " + ex);
        }
        assertEquals("the tomcat http handler did not take effect", response, "string2");
    }

    @Test
    public void testTomcatHTTPHandlerContextMatchExactFalse() throws Exception {
        //fail("Test empty");

        String urlStr1 = "http://localhost:" + PORT3 + "/hello/test1";
        TomcatHTTPServerEngine engine =
                factory.createTomcatHTTPServerEngine(PORT3, "http");
        TomcatHTTPHandler handler = engine.getServant(new URL(urlStr1));
        // can't find the handler here
        assertNull(handler);
        TomcatHTTPHandler testHandler1 = new TomcatHTTPTestHandler("test", false);
        engine.addServant(new URL(urlStr1), testHandler1);

        handler = engine.getServant(new URL(urlStr1));
        assertEquals(testHandler1, handler);

        String response = null;
        try {
            response = getResponse(urlStr1 + "/test");
        } catch (Exception ex) {
            fail("Can't get the reponse from the server " + ex);
        }
        assertEquals("the tomcat http handler did not take effect", response, "test");
    }


    @Test
    public void testTomcatHTTPHandlerContextMatchExactTrue() throws Exception {
        String urlStr1 = "http://localhost:" + PORT3 + "/hello/test1";
        TomcatHTTPServerEngine engine =
                factory.createTomcatHTTPServerEngine(PORT3, "http");
        TomcatHTTPHandler handler = engine.getServant(new URL(urlStr1));
        // can't find the handler here
        assertNull(handler);
        TomcatHTTPHandler testHandler1 = new TomcatHTTPTestHandler("test", true);
        engine.addServant(new URL(urlStr1), testHandler1);

        handler = engine.getServant(new URL(urlStr1));
        assertEquals(testHandler1, handler);

        String response = null;
        try {
            response = getResponse(urlStr1);
        } catch (Exception ex) {
            fail("Can't get the reponse from the server " + ex);
        }
        assertEquals("the tomcat http handler did not take effect", response, "test");
    }

    @Test
    public void testSetConnector() throws Exception {
        fail("Test empty");

/*        URL url = new URL("http://localhost:" + PORT4 + "/hello/test");
        TomcatHTTPTestHandler handler1 = new TomcatHTTPTestHandler("string1", true);
        TomcatHTTPTestHandler handler2 = new TomcatHTTPTestHandler("string2", true);

        TomcatHTTPServerEngine engine = new TomcatHTTPServerEngine();
        engine.setPort(PORT4);
        Tomcat server = new Tomcat();
        Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
        connector.setPort(PORT4);
        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.addCustomizer(new org.eclipse.jetty.server.ForwardedRequestCustomizer());
        HttpConnectionFactory httpFactory = new HttpConnectionFactory(httpConfig);
        Collection<ConnectionFactory> connectionFactories = new ArrayList<>();
        connectionFactories.add(httpFactory);
        connector.setConnectionFactories(connectionFactories);
        engine.setConnector(connector);
        List<Handler> handlers = new ArrayList<>();
        handlers.add(handler1);
        engine.setHandlers(handlers);
        engine.finalizeConfig();

        engine.addServant(url, handler2);
        String response = null;
        try {
            response = getResponse(url.toString());
            assertEquals("the jetty http handler1 did not take effect", response, "string1string2");
        } catch (Exception ex) {
            fail("Can't get the reponse from the server " + ex);
        }
        engine.stop();
        TomcatHTTPServerEngineFactory.destroyForPort(PORT4);*/
    }

    /**
     * Test that multiple UndertowHTTPServerEngine instances can be used simultaneously
     * without having name collisions.
     */
    @Test
    public void testJmxSupport() throws Exception {
        String urlStr = "http://localhost:" + PORT1 + "/hello/test";
        String urlStr2 = "http://localhost:" + PORT2 + "/hello/test";
        TomcatHTTPServerEngine engine =
                factory.createTomcatHTTPServerEngine(PORT1, "http");
        TomcatHTTPServerEngine engine2 =
                factory.createTomcatHTTPServerEngine(PORT2, "http");
        TomcatHTTPTestHandler handler1 = new TomcatHTTPTestHandler("string1", true);
        TomcatHTTPTestHandler handler2 = new TomcatHTTPTestHandler("string2", true);

        engine.addServant(new URL(urlStr), handler1);

        Set<ObjectName> s = CastUtils.cast(ManagementFactory.getPlatformMBeanServer().
                //queryNames(new ObjectName("org.xnio:type=Xnio,provider=\"nio\""), null));
                queryNames(new ObjectName("Tomcat:type=Server,*"), null));
        assertEquals("Could not find 1 Tomcat Server: " + s, 1, s.size());

        engine2.addServant(new URL(urlStr2), handler2);

        s = CastUtils.cast(ManagementFactory.getPlatformMBeanServer().
                queryNames(new ObjectName("Tomcat:type=Server,*"), null));
        assertEquals("Could not find 2 Tomcat Server: " + s, 2, s.size());

        engine.removeServant(new URL(urlStr));
        engine2.removeServant(new URL(urlStr2));


        engine.shutdown();

        s = CastUtils.cast(ManagementFactory.getPlatformMBeanServer().
                queryNames(new ObjectName("Tomcat:type=Server"), null));
        assertEquals("Could not find 2 Tomcat Server: " + s, 1, s.size());

        engine2.shutdown();

        s = CastUtils.cast(ManagementFactory.getPlatformMBeanServer().
                queryNames(new ObjectName("Tomcat:type=Server"), null));
        assertEquals("Could not find 0 Tomcat Server: " + s, 0, s.size());
    }

    @Test
    public void testHttps() throws Exception {
        String urlStr = "https://localhost:" + PORT1 + "/hello/test";

        factory.setTLSServerParametersForPort(PORT1, new TLSServerParameters());
        TomcatHTTPServerEngine engine =
                factory.createTomcatHTTPServerEngine(PORT1, "https");

        assertTrue("Protocol must be http", "https".equals(engine.getProtocol()));
        TomcatHTTPTestHandler handler = new TomcatHTTPTestHandler("Testing https settings", false);
        engine.addServant(new URL(urlStr), handler);

        String response = null;
        response = getResponse(urlStr);
        assertEquals("The tomcat http handler failed to connect on https", response, "Testing https settings");
    }

    @Test
    public void testHttpAndHttps() throws Exception {
        fail("Test empty");

        TomcatHTTPServerEngine engine =
                factory.createTomcatHTTPServerEngine(PORT1, "http");

        assertTrue("Protocol must be http",
                "http".equals(engine.getProtocol()));

        engine = new TomcatHTTPServerEngine();
        engine.setPort(PORT2);
        engine.setMaxIdleTime(30000);
        engine.setTlsServerParameters(new TLSServerParameters());
        engine.finalizeConfig();

        List<TomcatHTTPServerEngine> list = new ArrayList<>();
        list.add(engine);
        factory.setEnginesList(list);
        engine = factory.createTomcatHTTPServerEngine(PORT2, "https");
        TomcatHTTPTestHandler handler1 = new TomcatHTTPTestHandler("string1", true);
        // need to create a servant to create the connector
        engine.addServant(new URL("https://localhost:" + PORT2 + "/test"), handler1);
        assertTrue("Protocol must be https",
                "https".equals(engine.getProtocol()));

//        assertEquals("Get the wrong maxIdleTime.", 30000, getMaxIdle(engine.getConnector()));

        factory.setTLSServerParametersForPort(PORT1, new TLSServerParameters());
        engine = factory.createTomcatHTTPServerEngine(PORT1, "https");
        assertTrue("Protocol must be https",
                "https".equals(engine.getProtocol()));

        factory.setTLSServerParametersForPort(PORT3, new TLSServerParameters());
        engine = factory.createTomcatHTTPServerEngine(PORT3, "https");
        assertTrue("Protocol must be https",
                "https".equals(engine.getProtocol()));

        String response = null;
        response = getResponse("https://localhost:" + PORT2 + "/test");
        assertEquals("The tomcat http handler failed to connect on https", response, "string1");
    }

    @After
    public void reset() throws Exception {
        TomcatHTTPServerEngineFactory.destroyForPort(PORT1);
        TomcatHTTPServerEngineFactory.destroyForPort(PORT2);
        TomcatHTTPServerEngineFactory.destroyForPort(PORT3);
        TomcatHTTPServerEngineFactory.destroyForPort(PORT4);

        TomcatHTTPServerEngine engine = factory.retrieveTomcatHTTPServerEngine(PORT1);
        assertNull("Engine should have been destroyed.", engine);
        engine = factory.retrieveTomcatHTTPServerEngine(PORT2);
        assertNull("Engine should have been destroyed.", engine);
        engine = factory.retrieveTomcatHTTPServerEngine(PORT3);
        assertNull("Engine should have been destroyed.", engine);
        engine = factory.retrieveTomcatHTTPServerEngine(PORT4);
        assertNull("Engine should have been destroyed.", engine);
    }

    private String getResponse(String target) throws Exception {
        URL url = new URL(target);
        if (url.getProtocol().equalsIgnoreCase("https")) {
            String keystorePath = System.getProperty("user.dir") + "/src/test/resources/keystore";
            System.setProperty("javax.net.ssl.trustStore", keystorePath);
            System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
        }

        URLConnection connection = url.openConnection();

        assertTrue(connection instanceof HttpURLConnection);
        if (connection instanceof HttpsURLConnection) {
            ((HttpsURLConnection) connection).setHostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String arg0, SSLSession arg1) {
                    return true;
                }
            });
        }

        connection.connect();
        InputStream in = connection.getInputStream();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        IOUtils.copy(in, buffer);
        return buffer.toString();
    }

    private int getMaxIdle(Connector connector) throws Exception {
        try {
            return (int)connector.getClass().getMethod("getAsyncTimeout").invoke(connector);
        } catch (NoSuchMethodException nex) {
            System.out.println("Tomcat server couldn't invoke the getAsyncTimeout method.");
        }
        return ((Long)connector.getClass().getMethod("getAsyncTimeout").invoke(connector)).intValue();
    }
}

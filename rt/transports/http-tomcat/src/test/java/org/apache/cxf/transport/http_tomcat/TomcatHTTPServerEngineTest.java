package org.apache.cxf.transport.http_tomcat;

import org.apache.catalina.connector.Connector;
import org.apache.cxf.Bus;
import org.apache.cxf.configuration.Configurer;
import org.apache.cxf.configuration.jsse.TLSServerParameters;
import org.apache.cxf.configuration.spring.ConfigurerImpl;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.management.InstrumentationManager;
import org.apache.cxf.testutil.common.TestUtil;
import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TomcatHTTPServerEngineTest {

    private static final int PORT1
            = Integer.valueOf(TestUtil.getPortNumber(TomcatHTTPServerEngineTest.class, 1));
    private static final int PORT2
            = Integer.valueOf(TestUtil.getPortNumber(TomcatHTTPServerEngineTest.class, 2));
    private static final int PORT3
            = Integer.valueOf(TestUtil.getPortNumber(TomcatHTTPServerEngineTest.class, 3));


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
    }

    @Test
    public void testHttpAndHttps() throws Exception {
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

        getResponse("https://localhost:" + PORT2 + "/test");

        TomcatHTTPServerEngineFactory.destroyForPort(PORT1);
        TomcatHTTPServerEngineFactory.destroyForPort(PORT2);
        TomcatHTTPServerEngineFactory.destroyForPort(PORT3);
    }

    @Test
    public void testaddServants() throws Exception {
        String urlStr = "http://localhost:" + PORT1 + "/hello/test";
        String urlStr2 = "http://localhost:" + PORT1 + "/hello233/test";
        String urlStr3 = "http://localhost:" + PORT1 + "/";

        TomcatHTTPServerEngine serverEngine = new TomcatHTTPServerEngine(8080);
        serverEngine.setMaxIdleTime(30000);
        urlStr3 = "http://localhost:" + "8080" + "/hello/test";
        urlStr2 = "http://localhost:8080/my-servlet/";
        urlStr = "http://localhost:8080/tomcat-servlet/";
        serverEngine.addServant(new URL(urlStr), new TomcatHTTPTestHandler("Using test handler", true));
//        serverEngine.addServant("http://localhost:8080");
        String response = null;
        response = getResponse(urlStr3);
        System.out.println(response);
        assertEquals("The tomcat failed to query tomcat-servlet", response, "inside hello second servlet");
        response = getResponse(urlStr2);
        System.out.println(response);
        assertEquals("The tomcat failed to query my-servlet", response, "inside hello servlet ");
        response = getResponse(urlStr);
        System.out.println(response);
        assertEquals("The tomcat http handler did not take effect", response, "Using test handler");

        // todo add more assertions
        Thread.sleep(1000);
        serverEngine.stop();


    }

    /**
     * Check that names of threads serving requests for instances of TomcatServerEngine
     * can be set with user specified name.
     */
    @Test
    public void testSettingThreadNames() throws Exception {

    }

    @Test
    public void testEngineRetrieval() throws Exception {
        TomcatHTTPServerEngine engine =
                factory.createTomcatHTTPServerEngine(PORT1, "http");

        assertTrue(
                "Engine references for the same port should point to the same instance",
                engine == factory.retrieveTomcatHTTPServerEngine(PORT1));

        TomcatHTTPServerEngineFactory.destroyForPort(PORT1);
    }

    @Test
    public void testSetHandlers() throws Exception {

    }

    @Test
    public void testGetContextHandler() throws Exception {


    }

    @Test
    public void testTomcatHTTPHandler() throws Exception {

    }

    @Test
    public void testSetConnector() throws Exception {


    }


    private String getResponse(String target) throws Exception {
        URL url = new URL(target);

        URLConnection connection = url.openConnection();

        assertTrue(connection instanceof HttpURLConnection);
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
            System.out.println("123123");
        }
        return ((Long)connector.getClass().getMethod("getAsyncTimeout").invoke(connector)).intValue();
    }


}

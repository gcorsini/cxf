package org.apache.cxf.transport.http_tomcat;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.bus.spring.SpringBusFactory;
import org.apache.cxf.transport.DestinationFactory;
import org.apache.cxf.transport.DestinationFactoryManager;
import org.apache.cxf.transport.http.HTTPTransportFactory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.URL;
import java.util.Collection;

import static org.junit.Assert.*;

public class TomcatHTTPServerEngineFactoryTest {

    Bus bus;

    @BeforeClass
    public static void classUp() {
        // Get rid of any notion of a default bus set by other
        // rogue tests.
        BusFactory.setDefaultBus(null);
    }

    @AfterClass
    public static void classDown() {
        // Clean up.
        BusFactory.setDefaultBus(null);
    }

    @After
    public void tearDown() {
        if (bus != null) {
            bus.shutdown(false);
            bus = null;
        }
    }

    /**
     * This test makes sure that a default Spring initialized bus will
     * have the TomcatHTTPServerEngineFactory (absent of <httpj:engine-factory>
     * configuration.
     */
    @Test
    public void testMakeSureTransportFactoryHasEngineFactory() throws Exception {
        bus = BusFactory.getDefaultBus(true);

        assertNotNull("Cannot get bus", bus);

        // Make sure we got the Transport Factory.
        DestinationFactoryManager destFM =
                bus.getExtension(DestinationFactoryManager.class);
        assertNotNull("Cannot get DestinationFactoryManager", destFM);
        DestinationFactory destF =
                destFM.getDestinationFactory(
                        "http://cxf.apache.org/transports/http");
        assertNotNull("No DestinationFactory", destF);
        assertTrue(HTTPTransportFactory.class.isInstance(destF));

        // And the TomcatHTTPServerEngineFactory should be there.
        TomcatHTTPServerEngineFactory factory =
                bus.getExtension(TomcatHTTPServerEngineFactory.class);

    }

    /**
     * This test makes sure that with a <httpj:engine-factory bus="cxf">
     * that the bus is configured with the rightly configured Tomcat
     * HTTP Server Engine Factory.  Port 1234 should have be configured
     * for TLS.
     */
    @Test
    public void testMakeSureTransportFactoryHasEngineFactoryConfigured() throws Exception {

        // This file configures the factory to configure
        // port 1234 with default TLS.

        URL config = getClass().getResource("server-engine-factory.xml");

        bus = new SpringBusFactory().createBus(config, true);

        TomcatHTTPServerEngineFactory factory =
                bus.getExtension(TomcatHTTPServerEngineFactory.class);

        assertNotNull("EngineFactory is not configured.", factory);

        // The Engine for port 1234 should be configured for TLS.
        // This will throw an error if it is not.
        TomcatHTTPServerEngine engine = null;
        engine = factory.createTomcatHTTPServerEngine(1234, "https");

        assertNotNull("Engine is not available.", engine);
        assertEquals(1234, engine.getPort());
        assertEquals("Not https", "https", engine.getProtocol());

        try {
            engine = factory.createTomcatHTTPServerEngine(1234, "http");
            fail("The engine's protocol should be https");
        } catch (Exception e) {
            // expect the exception
        }


    }

    // todo most likely not needed
    @Test
    public void testMakeSureTomcat9ConnectorConfigured() throws Exception {


        URL config = getClass().getResource("server-engine-factory-jetty9-connector.xml");

        bus = new SpringBusFactory().createBus(config, true);

        // todo implement me

    }

    @Test
    public void testAnInvalidConfiguresfile() {

        // This file configures the factory to configure
        // port 1234 with default TLS.

        URL config = getClass().getResource("invalid-engines.xml");

        bus = new SpringBusFactory().createBus(config);

        // todo implement me
        TomcatHTTPServerEngineFactory factory = new TomcatHTTPServerEngineFactory();

        assertNotNull("EngineFactory is not configured.", factory);
    }

}

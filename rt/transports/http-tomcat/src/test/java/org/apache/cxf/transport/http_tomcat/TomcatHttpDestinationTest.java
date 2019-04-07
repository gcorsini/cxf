package org.apache.cxf.transport.http_tomcat;

import org.apache.catalina.connector.Request;
import org.apache.coyote.Response;
import org.apache.cxf.Bus;
import org.apache.cxf.BusException;
import org.apache.cxf.BusFactory;
import org.apache.cxf.bus.extension.ExtensionManagerBus;
import org.apache.cxf.common.util.Base64Utility;
import org.apache.cxf.common.util.StringUtils;
import org.apache.cxf.endpoint.EndpointResolverRegistry;
import org.apache.cxf.message.Message;
import org.apache.cxf.policy.PolicyDataEngine;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.service.model.ServiceInfo;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.ConduitInitiator;
import org.apache.cxf.transport.ConduitInitiatorManager;
import org.apache.cxf.transport.MessageObserver;
import org.apache.cxf.transport.http.ContinuationProviderFactory;
import org.apache.cxf.transport.http.DestinationRegistry;
import org.apache.cxf.transport.http.HTTPTransportFactory;
import org.apache.cxf.transports.http.configuration.HTTPServerPolicy;
import org.apache.cxf.ws.addressing.EndpointReferenceType;
import org.apache.cxf.ws.addressing.EndpointReferenceUtils;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Test;

import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.xml.namespace.QName;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TomcatHttpDestinationTest {

    protected static final String AUTH_HEADER = "Authorization";
    protected static final String USER = "copernicus";
    protected static final String PASSWD = "epicycles";
    protected static final String BASIC_AUTH =
            "Basic " + Base64Utility.encode((USER + ":" + PASSWD).getBytes());

    private static final String NOWHERE = "http://nada.nothing.nowhere.null/";
    private static final String PAYLOAD = "message payload";
    private static final String CHALLENGE_HEADER = "WWW-Authenticate";
    private static final String BASIC_CHALLENGE = "Basic realm=terra";
    private static final String DIGEST_CHALLENGE = "Digest realm=luna";
    private static final String CUSTOM_CHALLENGE = "Custom realm=sol";
    private Bus bus;
    private Bus threadDefaultBus;
    private Conduit decoupledBackChannel;
    private EndpointInfo endpointInfo;
    private EndpointReferenceType address;
    private TomcatHTTPServerEngine engine;
    private HTTPServerPolicy policy;
    private TomcatHTTPDestination destination;
    private Request request;
    private Response response;
    private Message inMessage;
    private Message outMessage;
    private MessageObserver observer;
    private ServletInputStream is;
    private ServletOutputStream os;
    private HTTPTransportFactory transportFactory;

    /**
     * This class replaces the engine in the Tomcat Destination.
     */
    private class EasyMockTomcatHTTPDestination
            extends TomcatHTTPDestination {

        EasyMockTomcatHTTPDestination(Bus bus,
                                      DestinationRegistry registry,
                                      EndpointInfo endpointInfo,
                                      TomcatHTTPServerEngineFactory serverEngineFactory,
                                      TomcatHTTPServerEngine easyMockEngine) throws IOException {
            super(bus, registry, endpointInfo, serverEngineFactory);
            engine = easyMockEngine;
        }

        @Override
        public void retrieveEngine() {
            // Leave engine alone.
        }
    }

    @After
    public void tearDown() {
        if (bus != null) {
            bus.shutdown(true);
        }
        bus = null;
        transportFactory = null;
        decoupledBackChannel = null;
        address = null;
        engine = null;
        request = null;
        response = null;
        inMessage = null;
        outMessage = null;
        is = null;
        os = null;
        destination = null;
        BusFactory.setDefaultBus(null);
    }

    @Test
    public void testGetAddress() throws Exception {
        destination = setUpDestination();
        EndpointReferenceType ref = destination.getAddress();
        assertNotNull("unexpected null address", ref);
        assertEquals("unexpected address",
                EndpointReferenceUtils.getAddress(ref),
                StringUtils.addDefaultPortIfMissing(EndpointReferenceUtils.getAddress(address)));
        assertEquals("unexpected service name local part",
                EndpointReferenceUtils.getServiceName(ref, bus).getLocalPart(),
                "Service");
        assertEquals("unexpected portName",
                EndpointReferenceUtils.getPortName(ref),
                "Port");
    }

    private TomcatHTTPDestination setUpDestination(
            boolean contextMatchOnStem, boolean mockedBus)
            throws Exception {
        policy = new HTTPServerPolicy();
        address = getEPR("bar/foo");


        transportFactory = new HTTPTransportFactory();

        final ConduitInitiator ci = new ConduitInitiator() {
            public Conduit getConduit(EndpointInfo targetInfo, Bus b) throws IOException {
                return decoupledBackChannel;
            }

            public Conduit getConduit(EndpointInfo localInfo, EndpointReferenceType target, Bus b)
                    throws IOException {
                return decoupledBackChannel;
            }

            public List<String> getTransportIds() {
                return null;
            }

            public Set<String> getUriPrefixes() {
                return new HashSet<>(Collections.singletonList("http"));
            }

        };
        ConduitInitiatorManager mgr = new ConduitInitiatorManager() {
            public void deregisterConduitInitiator(String name) {
            }

            public ConduitInitiator getConduitInitiator(String name) throws BusException {
                return null;
            }

            public ConduitInitiator getConduitInitiatorForUri(String uri) {
                return ci;
            }

            public void registerConduitInitiator(String name, ConduitInitiator factory) {
            }
        };

        if (!mockedBus) {
            bus = new ExtensionManagerBus();
            bus.setExtension(mgr, ConduitInitiatorManager.class);
        } else {
            bus = EasyMock.createMock(Bus.class);
            bus.getExtension(EndpointResolverRegistry.class);
            EasyMock.expectLastCall().andReturn(null);
            bus.getExtension(ContinuationProviderFactory.class);
            EasyMock.expectLastCall().andReturn(null).anyTimes();
            bus.getExtension(PolicyDataEngine.class);
            EasyMock.expectLastCall().andReturn(null).anyTimes();
            bus.hasExtensionByName("org.apache.cxf.ws.policy.PolicyEngine");
            EasyMock.expectLastCall().andReturn(false);
            bus.getExtension(ClassLoader.class);
            EasyMock.expectLastCall().andReturn(this.getClass().getClassLoader());
            EasyMock.replay(bus);
        }


        engine = EasyMock.createNiceMock(TomcatHTTPServerEngine.class);
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setName(new QName("bla", "Service"));
        endpointInfo = new EndpointInfo(serviceInfo, "");
        endpointInfo.setName(new QName("bla", "Port"));
        endpointInfo.setAddress(NOWHERE + "bar/foo");

        endpointInfo.addExtensor(policy);
        engine.addServant(EasyMock.eq(new URL(NOWHERE + "bar/foo")),
                EasyMock.isA(TomcatHTTPHandler.class));
        EasyMock.expectLastCall();
        engine.getContinuationsEnabled();
        EasyMock.expectLastCall().andReturn(true);
        EasyMock.replay(engine);

        TomcatHTTPDestination dest = new EasyMockTomcatHTTPDestination(bus,
                transportFactory.getRegistry(),
                endpointInfo,
                null,
                engine);
        dest.retrieveEngine();
        policy = dest.getServer();
        observer = new MessageObserver() {
            public void onMessage(Message m) {
                inMessage = m;
                threadDefaultBus = BusFactory.getThreadDefaultBus();
            }
        };
        dest.setMessageObserver(observer);
        return dest;
    }

    private TomcatHTTPDestination setUpDestination()
            throws Exception {
        return setUpDestination(false, false);
    }

    static EndpointReferenceType getEPR(String s) {
        return EndpointReferenceUtils.getEndpointReference(NOWHERE + s);
    }


}

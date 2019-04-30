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
package org.apache.cxf.systest.http_tomcat;

import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.cxf.Bus;
import org.apache.cxf.bus.managers.DestinationFactoryManagerImpl;
import org.apache.cxf.endpoint.ServerImpl;
import org.apache.cxf.endpoint.ServerRegistry;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.testutil.common.TestUtil;
import org.apache.cxf.transport.http_tomcat.TomcatDestinationFactory;
import org.apache.cxf.transport.http_tomcat.TomcatHTTPDestination;
import org.apache.cxf.transport.http_tomcat.TomcatHTTPServerEngine;
import org.junit.Test;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Properties;

import static org.junit.Assert.*;


/**
 * This class tests starting up and shutting down the embedded server when there
 * is extra tomcat configuration.
 */
public class TomcatEngineLifecycleTest {
    private static final String PORT1 = TestUtil.getPortNumber(TomcatEngineLifecycleTest.class, 1);
    private static final String PORT2 = TestUtil.getPortNumber(TomcatEngineLifecycleTest.class, 2);
    private GenericApplicationContext applicationContext;

    @Test
    public void testUpDownWithServlets() throws Exception {
        setUpBus();

        Bus bus = (Bus)applicationContext.getBean("cxf");
        ServerRegistry sr = bus.getExtension(ServerRegistry.class);
        ServerImpl si = (ServerImpl) sr.getServers().get(0);
        TomcatHTTPDestination jhd = (TomcatHTTPDestination) si.getDestination();
        TomcatHTTPServerEngine e = (TomcatHTTPServerEngine) jhd.getEngine();
        Tomcat server = e.getServer();

        server.addServlet("/bloop", "defaultServlet", new DefaultServlet());
//        for (Handler h : server.getChildHandlersByClass(WebAppContext.class)) {
//            WebAppContext wac = (WebAppContext) h;
//            if ("/jsunit".equals(wac.getContextPath())) {
//                wac.addServlet("org.eclipse.jetty.servlet.DefaultServlet", "/bloop");
//                break;
//            }
//        }

        try {
            verifyStaticHtml();
            invokeService();
        } finally {
            shutdownService();
        }
    }

    @Test
    public void testServerUpDownUp() throws Exception {
        for (int i = 0; i < 2; ++i) { // twice
            setUpBus();
            try {
                verifyStaticHtml();
                invokeService();
                invokeService8801();
            } finally {
                shutdownService();
            }
        }
    }

    private void setUpBus() throws Exception {
        verifyNoServer(PORT2);
        verifyNoServer(PORT1);

        applicationContext = new GenericApplicationContext();

        XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(applicationContext);
        reader.loadBeanDefinitions(
                new ClassPathResource("META-INF/cxf/cxf.xml"),
                new ClassPathResource("cxf-tomcat.xml", getClass()),
                new ClassPathResource("tomcat-engine.xml", getClass()),
                new ClassPathResource("server-lifecycle-beans.xml", getClass()));

        // bring in some property values from a Properties file
        PropertyPlaceholderConfigurer cfg = new PropertyPlaceholderConfigurer();
        Properties properties = new Properties();
        properties.setProperty("staticResourceURL", getClass().getPackage().getName().replace('.', '/'));
        cfg.setProperties(properties);
        // now actually do the replacement
        cfg.postProcessBeanFactory(applicationContext.getBeanFactory());
        applicationContext.refresh();
    }

    private void invokeService() {
        DummyInterface client = (DummyInterface) applicationContext.getBean("dummy-client");
        String hello_world = client.echoTomcat("hello world");
        assertEquals("We should get out put from this client", "hello world", hello_world);
    }

    private void invokeService8801() {
        DummyInterface client = (DummyInterface) applicationContext.getBean("dummy-client-8801");
        String hello_world = client.echoTomcat("hello world");
        assertEquals("We should get out put from this client", "hello world", hello_world);
    }

    private static void verifyStaticHtml() throws Exception {
        String response = null;
        for (int i = 0; i < 50 && null == response; i++) {
            try (InputStream in = new URL("http://localhost:" + PORT2 + "/test.html").openConnection()
                    .getInputStream()) {
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                IOUtils.copy(in, os);
                response = new String(os.toByteArray());
            } catch (Exception ex) {
                Thread.sleep(100L);
            }
        }
        assertNotNull("Test doc can not be read", response);

        String html;
        try (InputStream htmlFile = TomcatEngineLifecycleTest.class.getResourceAsStream("test.html")) {
            byte[] buf = new byte[htmlFile.available()];
            htmlFile.read(buf);
            html = new String(buf);
        }
        assertEquals("Can't get the right test html", html, response);
    }

    private void shutdownService() throws Exception {
        applicationContext.close();
        applicationContext = null;
//        System.gc(); // make sure the port is cleaned up a bit

        verifyNoServer(PORT2);
        verifyNoServer(PORT1);
    }

    private static void verifyNoServer(String port) {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress().getHostName(), Integer.parseInt(port))) {
            fail("Server on port " + port + " accepted a connection.");
        } catch (UnknownHostException e) {
            fail("Unknown host for local address");
        } catch (IOException e) {
            // this is what we want.
        }
    }

}
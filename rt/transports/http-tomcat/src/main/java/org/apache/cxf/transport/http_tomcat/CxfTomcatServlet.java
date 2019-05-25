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

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.cxf.Bus;
import org.apache.cxf.transport.servlet.AbstractHTTPServlet;

public class CxfTomcatServlet extends AbstractHTTPServlet {

    private static final long serialVersionUID = 1L;
    private static TomcatHTTPDestination destination;


    public void setDestination(TomcatHTTPDestination tomcatHTTPDestination){
        destination = tomcatHTTPDestination;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

    }

    @Override
    protected Bus getBus() {
        return null;
    }

    @Override
    protected void invoke(HttpServletRequest request, HttpServletResponse response) throws ServletException {

        System.out.println("inside tomcat servlet ");
        TomcatHTTPDestination tomcatHTTPDestination = destination;
                //(TomcatHTTPDestination) request.getAttribute("TOMCAT_DESTINATION");
        try {
            tomcatHTTPDestination.doService(request.getServletContext(), request, response);
        } catch (IOException e) {
            e.printStackTrace();
            throw new ServletException(e);
        }
    }

}

//
//  ========================================================================
//  Copyright (c) Mort Bay Consulting Pty Ltd and others.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.jeudego.pairgoth.container;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.util.resource.PathResource;
import org.eclipse.jetty.webapp.WebAppContext;

public class ServerMain
{
    enum OperationalMode
    {
        DEV,
        PROD
    }

    private Path apiBasePath = null;
    private Path viewBasePath = null;

    public static void main(String[] args)
    {
        try
        {
            new ServerMain().run();
        }
        catch (Throwable t)
        {
            t.printStackTrace();
        }
    }

    private void run() throws Throwable
    {
        // create server and web context
        Server server = new Server(8080);

        WebAppContext apiContext = new WebAppContext() {
            @Override
            public boolean isServerResource(String name, URL url)
            {
                return super.isServerResource(name, url) || url.getFile().contains("/WEB-INF/jetty-server/");
            }
        };
        apiContext.setContextPath("/api");

        WebAppContext viewContext = new WebAppContext() {
            @Override
            public boolean isServerResource(String name, URL url)
            {
                return super.isServerResource(name, url) || url.getFile().contains("/WEB-INF/jetty-server/");
            }
        };
        viewContext.setContextPath("/");
        
        // pairgoth runtime properties
        File properties = new File("./pairgoth.properties");
        if (properties.exists()) {
            Properties props = new Properties();
            props.load(new FileReader(properties));
            for (Map.Entry<Object, Object> entry: props.entrySet()) {
                String property = (String)entry.getKey();
                String value = (String)entry.getValue();
                if (property.startsWith("logger.")) {
                    apiContext.setInitParameter("webapp-slf4j-logger." + property.substring(7), value);
                } else {
                    System.setProperty("pairgoth." + property, value);
                }
            }
        }

        switch (getOperationalMode())
        {
            case PROD:
                // Configure as WAR
                apiContext.setWar(apiBasePath.toString());
                viewContext.setWar(viewBasePath.toString());
                break;
            case DEV:
                // Configuring from Development Base

                apiContext.setBaseResource(new PathResource(apiBasePath.resolve("src/main/webapp")));
                // Add webapp compiled classes & resources (copied into place from src/main/resources)
                Path apiClassesPath = apiBasePath.resolve("target/webapp/WEB-INF/classes");
                apiContext.setExtraClasspath(apiClassesPath.toAbsolutePath().toString());

                viewContext.setBaseResource(new PathResource(viewBasePath.resolve("src/main/webapp")));
                // Add webapp compiled classes & resources (copied into place from src/main/resources)
                Path viewClassesPath = viewBasePath.resolve("target/webapp/WEB-INF/classes");
                viewContext.setExtraClasspath(viewClassesPath.toAbsolutePath().toString());
                
                server.setDumpAfterStart(true);
                break;
            default:
                throw new FileNotFoundException("Unable to configure WebAppContext base resource undefined");
        }
        
        server.setHandler(new ContextHandlerCollection(apiContext, viewContext));

        server.start();
        server.join();
    }

    private OperationalMode getOperationalMode() throws IOException
    {
        // Property set by jetty.bootstrap.JettyBootstrap
        String warLocation = System.getProperty("org.eclipse.jetty.livewar.LOCATION");
        if (warLocation != null)
        {
            Path warPath = new File(warLocation).toPath().toRealPath();
            if (Files.exists(warPath) && Files.isRegularFile(warPath))
            {
                this.apiBasePath = warPath;
                this.viewBasePath = warPath;
                return OperationalMode.PROD;
            }
        }

        // We are in development mode, likely building and testing from an IDE.
        Path apiDevPath = new File("../api-webapp").toPath().toRealPath();
        Path viewDevPath = new File("../view-webapp").toPath().toRealPath();
        if (Files.exists(apiDevPath) && Files.isDirectory(apiDevPath) && Files.exists(viewDevPath) && Files.isDirectory(viewDevPath))
        {
            this.apiBasePath = apiDevPath;
            this.viewBasePath = viewDevPath;
            return OperationalMode.DEV;
        }

        return null;
    }
}

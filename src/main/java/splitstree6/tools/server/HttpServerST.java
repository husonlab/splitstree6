/*
 * HttpServerST.java Copyright (C) 2024 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package splitstree6.tools.server;

import com.sun.net.httpserver.BasicAuthenticator;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpServer;
import jloda.fx.util.ProgramProperties;
import jloda.util.ProgramExecutorService;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * HTTP server for webserver
 * Daniel Huson, 8.2020
 */
public class HttpServerST {
	private final InetAddress address;
	private final HttpServer httpServer;
	private final String defaultPath;
	private long started = 0L;
	private final int readsPerPage;

	private final ArrayList<HttpContext> contexts = new ArrayList<>();

	public HttpServerST(String path, int port, int backlog, int readsPerPage, int pageTimeout) throws IOException {
		if (!path.startsWith("/"))
			path = "/" + path;

		this.defaultPath = path;
		this.readsPerPage = readsPerPage;

		address = InetAddress.getLocalHost();
		httpServer = HttpServer.create(new InetSocketAddress((InetAddress) null, port), backlog);


		final ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(ProgramExecutorService.getNumberOfCoresToUse());
		httpServer.setExecutor(threadPoolExecutor);

		// general info:
		createContext(path + "/help", new HttpHandlerST(RequestHandler.getHelp(this)), null);
		createContext(path + "/version", new HttpHandlerST(RequestHandler.getVersion()), null);
		createContext(path + "/about", new HttpHandlerST(RequestHandler.getAbout(this)), null);
		createContext(path + "/isReadOnly", new HttpHandlerST((c, p) -> "true".getBytes()), null);

		createContext(path + "/draw", new HttpHandlerST(RequestHandler.draw()), null);

	}

	private void createContext(String path, HttpHandlerST handler, BasicAuthenticator authenticator) {
		var context = httpServer.createContext(path, handler);
		if (authenticator != null)
			context.setAuthenticator(authenticator);
		contexts.add(context);
	}

	public ArrayList<HttpContext> getContexts() {
		return contexts;
	}


	public long getStarted() {
		return started;
	}

	public void start() {
		started = System.currentTimeMillis();
		httpServer.start();
	}

	public void stop() {
		httpServer.stop(1);
	}

	public InetAddress getAddress() {
		return address;
	}

	public InetSocketAddress getSocketAddress() {
		return httpServer.getAddress();
	}

	public HttpServer getHttpServer() {
		return httpServer;
	}

	public String getAbout() {
		return "Version: " + ProgramProperties.getProgramVersion() + "\n"
			   + "Hostname: " + getAddress().getHostName() + "\n"
			   + "IP address: " + getAddress().getHostAddress() + "\n"
			   + "Port: " + getSocketAddress().getPort() + "\n"
			   + "Total requests: " + (HttpHandlerST.getNumberOfRequests().get() + 1L) + "\n"
			   + "Server started: " + (new Date(getStarted())) + "\n";
	}
}

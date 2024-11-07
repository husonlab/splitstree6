/*
 * HttpHandlerST.java Copyright (C) 2024 Daniel H. Huson
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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import jloda.util.Basic;
import jloda.util.StringUtils;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * handles an HTTP request
 * Daniel Huson, 8.2020
 */
public class HttpHandlerST implements HttpHandler {
	private final RequestHandler requestHandler;
	private static final AtomicLong numberOfRequests = new AtomicLong(0L);

	public HttpHandlerST() {
		this(RequestHandler.getDefault());
	}

	public HttpHandlerST(RequestHandler requestHandler) {
		this.requestHandler = requestHandler;
	}

	@Override
	public void handle(HttpExchange httpExchange) throws IOException {
		try {
			final String[] parameters;
			if ("GET".equals(httpExchange.getRequestMethod())) {
				parameters = getGETParameters(httpExchange);
			} else if ("POST".equals(httpExchange.getRequestMethod())) {
				parameters = getPOSTParameters(httpExchange);
			} else
				parameters = null;
			respond(httpExchange, parameters);
			numberOfRequests.incrementAndGet();
		} catch (Exception ex) {
			Basic.caught(ex);
			throw ex;
		}
	}

	private String[] getGETParameters(HttpExchange httpExchange) {
		final var uri = httpExchange.getRequestURI().toString();
		final var posQuestionMark = uri.indexOf('?');
		if (posQuestionMark > 0 && posQuestionMark < uri.length() - 1) {
			final var parameters = uri.substring(posQuestionMark + 1);
			if (parameters.contains("&")) {
				return StringUtils.split(parameters, '&');
			} else
				return new String[]{parameters};
		}
		return new String[0];
	}

	private String[] getPOSTParameters(HttpExchange httpExchange) {
		return new String[0]; // not implemented
	}

	public void respond(HttpExchange httpExchange, String[] parameters) throws IOException {
		final var bytes = requestHandler.handle(httpExchange.getHttpContext().getPath(), parameters);
		try (var outputStream = httpExchange.getResponseBody()) {
			httpExchange.sendResponseHeaders(200, bytes.length);
			outputStream.write(bytes);
			outputStream.flush();
		}
	}

	public static AtomicLong getNumberOfRequests() {
		return numberOfRequests;
	}
}

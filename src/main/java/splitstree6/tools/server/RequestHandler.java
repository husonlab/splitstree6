/*
 * RequestHandler.java Copyright (C) 2024 Daniel H. Huson
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

import jloda.util.CollectionUtils;
import jloda.util.StringUtils;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * webserver request handler for data requests
 * Daniel Huson, 8.2020
 */
public interface RequestHandler {
	static RequestHandler getHelp(HttpServerST server) {
		return (c, p) -> {
			try {
				checkKnownParameters(p);
				try (var ins = RequestHandler.class.getResource("help.html").openStream()) {
					if (ins != null) {
						var baseURL = "http://" + server.getAddress().getHostAddress() + ":" + server.getSocketAddress().getPort() + "/splitstree";
						return StringUtils.toString(ins.readAllBytes()).replaceAll("ServiceURL", baseURL).getBytes();
					}
				}
				throw new IOException("Resource not found: help.html");
			} catch (IOException ex) {
				return reportError(c, p, ex.getMessage());
			}
		};
	}

	// example: http://127.0.0.1:8001/splitstree/draw?newick=((a,b),c);&width=600&height=600

	static RequestHandler draw() {
		return (c, p) -> {
			try {
				checkKnownParameters(p, "newick", "layout", "width", "height");

				var newick = Parameters.getValue(p, "newick");
				if (newick == null) return reportError(c, p, "newick argument is null");
				newick = URLDecoder.decode(newick, StandardCharsets.UTF_8);
				var layout = Parameters.getValue(p, "layout");
				if (layout == null)
					layout = "radial";
				else layout = layout.toLowerCase();
				var width = Parameters.getValue(p, "width", 800.0);
				var height = Parameters.getValue(p, "height", 800.0);

				System.err.println("draw request");
				System.err.println("newick=" + newick);
				System.err.println("layout=" + layout);

				System.err.println("width=" + width);
				System.err.println("height=" + height);

				return DrawNewick.apply(newick, layout, width, height).getBytes();
			} catch (IOException ex) {
				return reportError(c, p, ex.getMessage());
			}
		};
	}

	static RequestHandler drawDistances() {
		return (c, p) -> {
			try {
				checkKnownParameters(p, "matrix", "algorithm", "layout", "width", "height");

				var matrix = Parameters.getValue(p, "matrix");
				if (matrix == null) return reportError(c, p, "matrix argument is null");
				matrix = URLDecoder.decode(matrix, StandardCharsets.UTF_8);

				var algorithm = Parameters.getValue(p, "algorithm");
				if (algorithm == null)
					algorithm = "nj";
				else algorithm = algorithm.toLowerCase();

				var layout = Parameters.getValue(p, "layout");
				if (layout == null)
					layout = "radial";
				else layout = layout.toLowerCase();

				var width = Parameters.getValue(p, "width", 800.0);
				var height = Parameters.getValue(p, "height", 800.0);

				System.err.println("draw_distances request");
				System.err.println("matrix=\n" + matrix);
				System.err.println("algorithm=" + algorithm);
				System.err.println("layout=" + layout);
				System.err.println("width=" + width);
				System.err.println("height=" + height);

				return DrawDistances.apply(matrix, algorithm, layout, width, height).getBytes();
			} catch (IOException ex) {
				return reportError(c, p, ex.getMessage());
			}
		};
	}

	static RequestHandler drawSequences() {
		return (c, p) -> {
			try {
				checkKnownParameters(p, "sequences", "transform", "algorithm", "layout", "width", "height");

				var sequences = Parameters.getValue(p, "sequences");
				if (sequences == null) return reportError(c, p, "sequences argument is null");
				sequences = URLDecoder.decode(sequences, StandardCharsets.UTF_8);

				var transform = Parameters.getValue(p, "transform");
				if (transform == null)
					transform = "hamming";
				else transform = transform.toLowerCase();

				var algorithm = Parameters.getValue(p, "algorithm");
				if (algorithm == null)
					algorithm = "nj";
				else algorithm = algorithm.toLowerCase();

				var layout = Parameters.getValue(p, "layout");
				if (layout == null)
					layout = "radial";
				else layout = layout.toLowerCase();

				var width = Parameters.getValue(p, "width", 800.0);
				var height = Parameters.getValue(p, "height", 800.0);

				System.err.println("draw_distances request");
				System.err.println("sequences= (" + StringUtils.getLinesFromString(sequences).size() + " lines)");
				System.err.println("transform=" + transform);
				System.err.println("algorithm=" + algorithm);
				System.err.println("layout=" + layout);
				System.err.println("width=" + width);
				System.err.println("height=" + height);

				return DrawSequences.apply(sequences, transform, algorithm, layout, width, height).getBytes();
			} catch (IOException ex) {
				return reportError(c, p, ex.getMessage());
			}
		};
	}

	byte[] handle(String context, String[] parameters) throws IOException;

	static RequestHandler getDefault() {
		return (c, p) -> ("<html>\n" +
						  "<body>\n" +
						  "<b> Not implemented: " + c + " " + StringUtils.toString(p, ", ") +
						  "</b>\n" +
						  "</body>\n" +
						  "</html>\n").getBytes();
	}

	static RequestHandler getAbout(HttpServerST server) {
		return (c, p) -> {
			checkKnownParameters(p);
			return server.getAbout().getBytes();
		};
	}


	static RequestHandler getVersion() {
		return (c, p) -> {
			try {
				checkKnownParameters(p);
				return SplitsTreeServer.Version.getBytes();
			} catch (IOException ex) {
				return reportError(c, p, ex.getMessage());
			}
		};
	}


	static byte[] reportError(String content, String[] parameters, String message) {
		final String error = (Utilities.SERVER_ERROR + content + "?" + StringUtils.toString(parameters, "&") + ": " + message);
		System.err.println(error);
		return error.getBytes();
	}

	static void checkKnownParameters(String[] parameters, String... known) throws IOException {
		final List<String> parameterNames = Arrays.stream(parameters).sequential().map(parameter ->
		{
			if (parameter.contains("="))
				return parameter.substring(0, parameter.indexOf("="));
			else
				return parameter;
		}).toList();
		for (String name : parameterNames) {
			if (!CollectionUtils.contains(known, name))
				throw new IOException("Unknown parameter: '" + name + "'");
		}
	}

	static void checkRequiredParameters(String[] parameters, String... required) throws IOException {
		final List<String> parameterNames = Arrays.stream(parameters).sequential().map(parameter -> {
			if (parameter.contains("="))
				return parameter.substring(0, parameter.indexOf("="));
			else
				return parameter;
		}).toList();
		for (String name : required) {
			if (!parameterNames.contains(name))
				throw new IOException("Missing parameter: '" + name + "'");
		}
	}
}
